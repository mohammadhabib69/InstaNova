package com.insta.nova.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.firestore.WriteBatch;
import com.insta.nova.data.firebase.CrashReporter;
import com.insta.nova.data.firebase.FirebaseData;
import com.insta.nova.data.firebase.model.PublicProfileDoc;
import com.insta.nova.data.firebase.model.UserDoc;
import com.insta.nova.util.Resource;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Firestore identity repository (tasks P3-01/P2-06).
 *
 * <p>Writes the paired documents described in ARCHITECTURE §3:
 * <ul>
 *   <li>{@code users/{uid}} — private, owner-only (rules enforce it)</li>
 *   <li>{@code publicProfiles/{uid}} — signed-in-readable projection</li>
 *   <li>{@code usernames/{lowercase}} — uniqueness reservation</li>
 * </ul>
 *
 * <p>Username reservation runs in a transaction (read-then-write on one document), then
 * the two profile documents are written in a single batch. The reservation and the batch
 * are not one atomic unit — Firestore transactions cannot span a batch — so a crash
 * between them can leave a reserved name without a profile. That is recoverable
 * (the reservation carries {@code uid} and a re-run overwrites it) and is why the
 * operation is idempotent: calling it twice with the same input is safe.
 */
public class UserRepository {

    private static final String COLLECTION_USERS = "users";
    private static final String COLLECTION_PUBLIC = "publicProfiles";
    private static final String COLLECTION_USERNAMES = "usernames";

    private static final String ERROR_TAKEN = "That username is already taken. Please choose another.";
    private static final String ERROR_OFFLINE = "Couldn't reach Firebase. Check your connection and try again.";

    private final FirebaseFirestore db;

    public UserRepository() {
        this.db = FirebaseData.firestore();
    }

    /** Reads the caller's own private document. */
    @NonNull
    public LiveData<Resource<UserDoc>> fetchSelf(@NonNull String uid) {
        MutableLiveData<Resource<UserDoc>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));
        db.collection(COLLECTION_USERS).document(uid).get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        result.postValue(Resource.error("No profile document for this account.", null));
                        return;
                    }
                    result.postValue(Resource.success(UserDoc.fromMap(snapshot.getData())));
                })
                .addOnFailureListener(e -> {
                    CrashReporter.record(e, "fetchSelf");
                    result.postValue(Resource.error(messageFor(e), null));
                });
        return result;
    }

    /** Live view of the caller's own document; emits on every server/local change. */
    @NonNull
    public LiveData<Resource<UserDoc>> observeSelf(@NonNull String uid) {
        MutableLiveData<Resource<UserDoc>> result = new MutableLiveData<>();
        db.collection(COLLECTION_USERS).document(uid).addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                CrashReporter.record(error, "observeSelf");
                result.postValue(Resource.error(messageFor(error), null));
                return;
            }
            if (snapshot == null || !snapshot.exists()) return;
            result.postValue(Resource.success(UserDoc.fromMap(snapshot.getData())));
        });
        return result;
    }

    /** True when no one holds {@code username}. */
    @NonNull
    public LiveData<Resource<Boolean>> isUsernameAvailable(@NonNull String username) {
        MutableLiveData<Resource<Boolean>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));
        String key = normalize(username);
        if (key.isEmpty()) {
            result.setValue(Resource.success(false));
            return result;
        }
        db.collection(COLLECTION_USERNAMES).document(key).get()
                .addOnSuccessListener(snapshot -> result.postValue(Resource.success(!snapshot.exists())))
                .addOnFailureListener(e -> {
                    CrashReporter.record(e, "isUsernameAvailable");
                    result.postValue(Resource.error(messageFor(e), null));
                });
        return result;
    }

    /**
     * Writes/updates the caller's profile documents and reserves the username.
     *
     * @param user             private document fields (uid required)
     * @param previousUsername the name currently reserved for this uid, or null on first write
     */
    @NonNull
    public LiveData<Resource<Void>> writeSelf(@NonNull UserDoc user, @Nullable String previousUsername) {
        MutableLiveData<Resource<Void>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));
        if (user.uid == null || user.uid.isEmpty()) {
            result.setValue(Resource.error("Missing account id.", null));
            return result;
        }
        final String newKey = normalize(user.username);
        if (newKey.isEmpty()) {
            result.setValue(Resource.error("Please choose a username.", null));
            return result;
        }

        final DocumentReference usernameRef = db.collection(COLLECTION_USERNAMES).document(newKey);
        db.runTransaction((Transaction.Function<String>) transaction -> {
            DocumentSnapshot existing = transaction.get(usernameRef);
            if (existing.exists()) {
                String owner = existing.getString("uid");
                if (owner != null && !owner.equals(user.uid)) {
                    return ERROR_TAKEN;
                }
            }
            Map<String, Object> reservation = new HashMap<>();
            reservation.put("uid", user.uid);
            reservation.put("username", user.username);
            reservation.put("createdAt", existing.exists()
                    ? existing.get("createdAt")
                    : FieldValue.serverTimestamp());
            transaction.set(usernameRef, reservation, SetOptions.merge());
            return null;
        }).addOnSuccessListener(error -> {
            if (error != null) {
                result.postValue(Resource.error(error, null));
                return;
            }
            commitProfile(user, previousUsername, newKey, result);
        }).addOnFailureListener(e -> {
            CrashReporter.record(e, "reserveUsername");
            result.postValue(Resource.error(messageFor(e), null));
        });
        return result;
    }

    /**
     * Profile documents only: {@code users/{uid}} + {@code publicProfiles/{uid}}.
     *
     * <p>A username reservation is a namespace claim, and that namespace still belongs to
     * Supabase until the migration moves it. Reserving it here would also need a Firestore rule
     * for {@code usernames}, which does not exist, so the whole transaction died with
     * PERMISSION_DENIED — the exact failure the device logged on 2026-10-30. The identity mirror
     * therefore writes only the two profile documents.
     */
    public LiveData<Resource<Void>> writeSelfProfileOnly(@NonNull UserDoc user) {
        MutableLiveData<Resource<Void>> result = new MutableLiveData<>(Resource.loading(null));
        if (user.uid == null || user.uid.isEmpty()) {
            result.setValue(Resource.error("Missing account id.", null));
            return result;
        }

        WriteBatch batch = db.batch();
        Map<String, Object> privateData = user.toMap();
        privateData.put("updatedAt", FieldValue.serverTimestamp());
        batch.set(db.collection(COLLECTION_USERS).document(user.uid), privateData, SetOptions.merge());
        batch.set(db.collection(COLLECTION_PUBLIC).document(user.uid),
                PublicProfileDoc.from(user).toMap(), SetOptions.merge());

        batch.commit()
                .addOnSuccessListener(unused -> result.postValue(Resource.success(null)))
                .addOnFailureListener(e -> {
                    CrashReporter.record(e, "writeSelfProfileOnly");
                    result.postValue(Resource.error(messageFor(e), null));
                });
        return result;
    }

    private void commitProfile(@NonNull UserDoc user, @Nullable String previousUsername,
                               @NonNull String newKey, MutableLiveData<Resource<Void>> result) {
        WriteBatch batch = db.batch();

        Map<String, Object> privateData = user.toMap();
        privateData.put("updatedAt", FieldValue.serverTimestamp());
        batch.set(db.collection(COLLECTION_USERS).document(user.uid), privateData, SetOptions.merge());

        batch.set(db.collection(COLLECTION_PUBLIC).document(user.uid),
                PublicProfileDoc.from(user).toMap(), SetOptions.merge());

        String oldKey = normalize(previousUsername == null ? "" : previousUsername);
        if (!oldKey.isEmpty() && !oldKey.equals(newKey)) {
            batch.delete(db.collection(COLLECTION_USERNAMES).document(oldKey));
        }

        batch.commit()
                .addOnSuccessListener(unused -> result.postValue(Resource.success(null)))
                .addOnFailureListener(e -> {
                    CrashReporter.record(e, "commitProfile");
                    result.postValue(Resource.error(messageFor(e), null));
                });
    }

    static String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.US);
    }

    private static String messageFor(@NonNull Exception e) {
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.US);
        if (message.contains("permission") || message.contains("offline")) return ERROR_OFFLINE;
        return "Profile sync failed. Please try again.";
    }
}
