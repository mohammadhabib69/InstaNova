package com.insta.nova.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.insta.nova.data.firebase.CrashReporter;
import com.insta.nova.data.firebase.FirebaseData;
import com.insta.nova.data.firebase.Validation;
import com.insta.nova.data.firebase.model.PublicProfileDoc;
import com.insta.nova.util.Resource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Read paths over {@code publicProfiles} (task P3-01).
 *
 * <p>Every lookup goes through {@code usernameLower}: Firestore has no case-insensitive
 * comparison, and the previous Postgres schema used an index on {@code lower(username)}.
 * The field is written by {@link PublicProfileDoc#toMap()} and validated by
 * {@link Validation#normalizeUsername(String)}.
 *
 * <p>Range queries on a single field need no composite index; anything adding a second
 * filter or a different sort order must ship an entry in {@code firestore.indexes.json}.
 */
public class PublicProfileRepository {

    /** Firestore caps `in`/range pages at 1000; the UI pages far below that. */
    public static final int DEFAULT_LIMIT = 20;

    private final FirebaseFirestore db;

    public PublicProfileRepository() {
        this.db = FirebaseData.firestore();
    }

    public LiveData<Resource<PublicProfileDoc>> get(@NonNull String uid) {
        MutableLiveData<Resource<PublicProfileDoc>> out = new MutableLiveData<>(Resource.loading());
        if (uid.isEmpty()) {
            out.setValue(Resource.error("Missing profile id"));
            return out;
        }
        db.collection("publicProfiles").document(uid).get()
                .addOnSuccessListener(doc -> out.setValue(doc.exists()
                        ? Resource.success(map(doc))
                        : Resource.error("Profile not found")))
                .addOnFailureListener(e -> fail(out, e, "profile_get"));
        return out;
    }

    /** Exact, case-insensitive username lookup. */
    public LiveData<Resource<PublicProfileDoc>> byUsername(@Nullable String username) {
        MutableLiveData<Resource<PublicProfileDoc>> out = new MutableLiveData<>(Resource.loading());
        String key = Validation.normalizeUsername(username);
        if (key.isEmpty()) {
            out.setValue(Resource.error("Missing username"));
            return out;
        }
        db.collection("publicProfiles")
                .whereEqualTo("usernameLower", key)
                .limit(1)
                .get()
                .addOnSuccessListener(snap -> out.setValue(
                        snap.isEmpty() ? Resource.error("Profile not found")
                                : Resource.success(map(snap.getDocuments().get(0)))))
                .addOnFailureListener(e -> fail(out, e, "profile_by_username"));
        return out;
    }

    /**
     * Prefix search used by the existing search screen. {@code \uf8ff} is the highest
     * code point Firestore will accept in a range bound, so this matches "hab" → "habib".
     */
    public LiveData<Resource<List<PublicProfileDoc>>> searchPrefix(@Nullable String prefix, int limit) {
        MutableLiveData<Resource<List<PublicProfileDoc>>> out =
                new MutableLiveData<>(Resource.loading());
        String key = Validation.normalizeUsername(prefix);
        if (key.isEmpty()) {
            out.setValue(Resource.success(new ArrayList<>()));
            return out;
        }
        int cap = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, 50);
        db.collection("publicProfiles")
                .orderBy("usernameLower")
                .whereGreaterThanOrEqualTo("usernameLower", key)
                .whereLessThanOrEqualTo("usernameLower", key + "\uf8ff")
                .limit(cap)
                .get()
                .addOnSuccessListener(snap -> out.setValue(Resource.success(mapAll(snap))))
                .addOnFailureListener(e -> fail(out, e, "profile_search"));
        return out;
    }

    /**
     * "Suggested for you" (task P3-05): highest-follower profiles the caller does not
     * already follow. Ranking is client-side over a bounded read because a server-side
     * recommendation job needs Cloud Functions (billing blocker).
     *
     * <p>Exclusion reads at most 500 edges; an account following more than that gets a
     * suggestion it may already follow, which is a cosmetic miss, not a correctness one.
     */
    public LiveData<Resource<List<PublicProfileDoc>>> suggested(@NonNull String me, int limit) {
        MutableLiveData<Resource<List<PublicProfileDoc>>> out = new MutableLiveData<>(Resource.loading());
        final int cap = limit <= 0 ? 5 : Math.min(limit, 20);

        db.collection("follows").whereEqualTo("followerUid", me).limit(500).get()
                .addOnSuccessListener(follows -> {
                    final Set<String> excluded = new HashSet<>();
                    excluded.add(me);
                    for (DocumentSnapshot edge : follows.getDocuments()) {
                        String following = edge.getString("followingUid");
                        if (following != null && !following.isEmpty()) {
                            excluded.add(following);
                        }
                    }
                    db.collection("publicProfiles")
                            .orderBy("followersCount", Query.Direction.DESCENDING)
                            .limit(cap + excluded.size())
                            .get()
                            .addOnSuccessListener(snap -> {
                                List<PublicProfileDoc> rows = new ArrayList<>(cap);
                                for (DocumentSnapshot doc : snap.getDocuments()) {
                                    PublicProfileDoc profile = map(doc);
                                    if (!excluded.contains(profile.uid)) {
                                        rows.add(profile);
                                    }
                                    if (rows.size() >= cap) {
                                        break;
                                    }
                                }
                                out.setValue(Resource.success(rows));
                            })
                            .addOnFailureListener(e -> fail(out, e, "suggested_profiles"));
                })
                .addOnFailureListener(e -> fail(out, e, "suggested_follows"));
        return out;
    }

    private static PublicProfileDoc map(@NonNull DocumentSnapshot doc) {
        PublicProfileDoc profile = PublicProfileDoc.fromMap(doc.getData());
        if (profile.uid == null || profile.uid.isEmpty()) {
            profile.uid = doc.getId();   // older documents predate the uid field
        }
        return profile;
    }

    private static List<PublicProfileDoc> mapAll(@NonNull QuerySnapshot snap) {
        List<PublicProfileDoc> rows = new ArrayList<>(snap.size());
        for (DocumentSnapshot doc : snap.getDocuments()) {
            rows.add(map(doc));
        }
        return rows;
    }

    private static <T> void fail(MutableLiveData<Resource<T>> out, Exception e, String context) {
        // Report once, return a user-safe message: the raw Firestore text can leak
        // project ids and rule paths into the UI.
        CrashReporter.record(e, context);
        out.setValue(Resource.error("Couldn't load profile. Check your connection."));
    }
}
