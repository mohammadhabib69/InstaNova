package com.insta.nova.data.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;
import com.insta.nova.data.firebase.CrashReporter;
import com.insta.nova.data.firebase.FirebaseData;
import com.insta.nova.data.firebase.NotificationPrefs;
import com.insta.nova.util.Resource;

import java.util.HashMap;
import java.util.Map;

/**
 * User settings (tasks P10-01..P10-03, P10-11).
 *
 * <p>Settings live on the owner-only {@code users/{uid}} document under a single `settings`
 * map rather than in a separate collection. Reasons:
 * <ul>
 *   <li>Reading them costs nothing extra — the signed-in user's document is already loaded.</li>
 *   <li>They can never leak: `users/{uid}` is readable only by its owner, so notification
 *       preferences, privacy flags and call settings inherit that boundary.</li>
 *   <li>One write covers a whole settings screen instead of N documents.</li>
 * </ul>
 *
 * <p>Named `FirestoreSettingsRepository` because `SettingsRepository` is the live Supabase
 * class (password change, private toggle, archive) and stays untouched until cutover.
 *
 * <p>This replaces the `nova_settings` table (Postgres) and consolidates the two local
 * stores (`AppSettingsStore`, `ChatSettingsStore`) that duplicated it on-device.
 */
public class FirestoreSettingsRepository {

    public static final String KEY_NOTIFY_LIKES = "notifyLikes";
    public static final String KEY_NOTIFY_COMMENTS = "notifyComments";
    public static final String KEY_NOTIFY_FOLLOWS = "notifyFollows";
    public static final String KEY_NOTIFY_MESSAGES = "notifyMessages";
    public static final String KEY_NOTIFY_CALLS = "notifyCalls";
    public static final String KEY_PRIVATE_ACCOUNT = "privateAccount";
    public static final String KEY_SHOW_ACTIVITY = "showActivity";
    public static final String KEY_WHO_CAN_CALL = "whoCanCall";
    public static final String KEY_WHO_CAN_DM = "whoCanDm";
    public static final String KEY_LANGUAGE = "language";

    private static final String COLLECTION_USERS = "users";
    private static final String COLLECTION_PUBLIC = "publicProfiles";

    private final FirebaseFirestore db;

    public FirestoreSettingsRepository() {
        this.db = FirebaseData.firestore();
    }

    public LiveData<Resource<Map<String, Object>>> load(@NonNull String uid) {
        MutableLiveData<Resource<Map<String, Object>>> out = new MutableLiveData<>(Resource.loading());
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> out.setValue(Resource.success(settingsOf(doc))))
                .addOnFailureListener(e -> fail(out, e, "settings_load"));
        return out;
    }

    /**
     * Partial update: only the changed keys are written, so two screens cannot clobber.
     *
     * <p>One key needs a second document: {@link #KEY_PRIVATE_ACCOUNT}. The
     * `followRequests` rule reads `publicProfiles/{uid}.isPrivate` to decide whether a
     * follow must be a request, so a privacy toggle that only touched `users/{uid}` would
     * leave the projection stale — the account would look public to everyone and every
     * follow request would be rejected as a follow of a public account. Both writes go in
     * one batch so the two documents can never disagree, not even for a moment.
     */
    public LiveData<Resource<Void>> update(@NonNull String uid, @NonNull Map<String, Object> patch) {
        MutableLiveData<Resource<Void>> out = new MutableLiveData<>(Resource.loading());
        Map<String, Object> settings = new HashMap<>(patch);
        Map<String, Object> wrapper = new HashMap<>();
        wrapper.put("settings", settings);
        wrapper.put("updatedAt", FieldValue.serverTimestamp());

        Object isPrivate = patch.get(KEY_PRIVATE_ACCOUNT);
        if (!(isPrivate instanceof Boolean)) {
            db.collection(COLLECTION_USERS).document(uid).set(wrapper, SetOptions.merge())
                    .addOnSuccessListener(v -> out.setValue(Resource.success(null)))
                    .addOnFailureListener(e -> fail(out, e, "settings_update"));
            return out;
        }

        WriteBatch batch = db.batch();
        Map<String, Object> userDoc = new HashMap<>(wrapper);
        // Mirror to the top-level field too: UserDoc reads it there, the settings screen
        // reads the nested one, and the projection must not be the only place that agrees.
        userDoc.put("isPrivate", isPrivate);
        batch.set(db.collection(COLLECTION_USERS).document(uid), userDoc, SetOptions.merge());

        Map<String, Object> projection = new HashMap<>();
        projection.put("isPrivate", isPrivate);
        batch.set(db.collection(COLLECTION_PUBLIC).document(uid), projection, SetOptions.merge());

        batch.commit()
                .addOnSuccessListener(v -> out.setValue(Resource.success(null)))
                .addOnFailureListener(e -> fail(out, e, "settings_update"));
        return out;
    }

    /** Defaults match the current app behaviour, so a missing document is not a surprise. */
    @NonNull
    public static Map<String, Object> defaults() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put(KEY_NOTIFY_LIKES, true);
        defaults.put(KEY_NOTIFY_COMMENTS, true);
        defaults.put(KEY_NOTIFY_FOLLOWS, true);
        defaults.put(KEY_NOTIFY_MESSAGES, true);
        defaults.put(KEY_NOTIFY_CALLS, true);
        // P7-05: keys the dispatcher consults via NotificationPrefs. Quiet hours are
        // deliberately absent by default — absent means "no quiet window", so a missing
        // pair never silently mutes overnight notifications.
        defaults.put(NotificationPrefs.MENTIONS, true);
        defaults.put(NotificationPrefs.STORIES, true);
        defaults.put(NotificationPrefs.LIVE, true);
        defaults.put(KEY_PRIVATE_ACCOUNT, false);
        defaults.put(KEY_SHOW_ACTIVITY, true);
        defaults.put(KEY_WHO_CAN_CALL, "everyone");
        defaults.put(KEY_WHO_CAN_DM, "everyone");
        defaults.put(KEY_LANGUAGE, "en");
        return defaults;
    }

    /** Stored settings layered over defaults, so new keys appear without a migration. */
    @NonNull
    @SuppressWarnings("unchecked")
    public static Map<String, Object> settingsOf(DocumentSnapshot doc) {
        Map<String, Object> merged = defaults();
        if (doc != null && doc.exists()) {
            Object raw = doc.get("settings");
            if (raw instanceof Map) {
                merged.putAll((Map<String, Object>) raw);
            }
        }
        return merged;
    }

    private static <T> void fail(MutableLiveData<Resource<T>> out, Exception e, String context) {
        CrashReporter.record(e, context);
        out.setValue(Resource.error("Couldn't save settings. Try again."));
    }
}
