package com.insta.nova.data.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;
import com.insta.nova.data.firebase.CrashReporter;
import com.insta.nova.data.firebase.FirebaseData;
import com.insta.nova.data.firebase.Timestamps;
import com.insta.nova.util.Resource;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Confessions (tasks P9-04, P9-08): anonymous, university-scoped.
 *
 * <p><b>Anonymity is a server property, not a promise.</b> The document carries no author
 * field at all, and `firestore.rules` rejects any write containing `authorUid`, `authorId`,
 * `uid` or `senderUid`. That means anonymity cannot be lost by a client bug.
 *
 * <p>The trade-off: without an author field nobody — including the author — can delete a
 * confession from the client, and moderation cannot attribute one. Both need a Function
 * holding the author mapping server-side (P9-05, billing-blocked). That is deliberate: an
 * author field visible to the client would silently defeat the whole feature.
 */
public class FirestoreConfessionRepository {

    public static final int PAGE = 20;

    private final FirebaseFirestore db;

    public FirestoreConfessionRepository() {
        this.db = FirebaseData.firestore();
    }

    public LiveData<Resource<String>> post(@NonNull String confessionId, @NonNull String universityId,
                                           @NonNull String body) {
        MutableLiveData<Resource<String>> out = new MutableLiveData<>(Resource.loading());
        Map<String, Object> doc = new HashMap<>();
        doc.put("confessionId", confessionId);
        doc.put("universityId", universityId);
        doc.put("body", body);
        doc.put("hidden", false);
        doc.put("likesCount", 0L);
        doc.put("createdAt", Timestamps.serverNow());
        // Intentionally no author field — see class comment.

        db.collection("novaConfessions").document(confessionId).set(doc)
                .addOnSuccessListener(v -> out.setValue(Resource.success(confessionId)))
                .addOnFailureListener(e -> fail(out, e, "confession_post"));
        return out;
    }

    public LiveData<Resource<List<DocumentSnapshot>>> feed(@NonNull String universityId,
                                                           DocumentSnapshot cursor, int limit) {
        MutableLiveData<Resource<List<DocumentSnapshot>>> out = new MutableLiveData<>(Resource.loading());
        int cap = limit <= 0 ? PAGE : Math.min(limit, 50);
        Query query = db.collection("novaConfessions")
                .whereEqualTo("universityId", universityId)
                .whereEqualTo("hidden", false)
                .orderBy("createdAt", Query.Direction.DESCENDING);
        if (cursor != null) {
            query = query.startAfter(cursor.get("createdAt"));
        }
        query.limit(cap).get()
                .addOnSuccessListener(snap -> out.setValue(Resource.success(snap.getDocuments())))
                .addOnFailureListener(e -> fail(out, e, "confession_feed"));
        return out;
    }

    public LiveData<Resource<Boolean>> setLiked(@NonNull String confessionId, @NonNull String uid,
                                                boolean liked) {
        MutableLiveData<Resource<Boolean>> out = new MutableLiveData<>(Resource.loading());
        String id = confessionId + "_" + uid;

        db.runBatch(batch -> {
                    if (liked) {
                        Map<String, Object> like = new HashMap<>();
                        like.put("likeId", id);
                        like.put("confessionId", confessionId);
                        like.put("uid", uid);
                        like.put("createdAt", Timestamps.serverNow());
                        batch.set(db.collection("novaConfessionLikes").document(id), like);
                    } else {
                        batch.delete(db.collection("novaConfessionLikes").document(id));
                    }
                    batch.set(db.collection("novaConfessions").document(confessionId),
                            Collections.singletonMap("likesCount", FieldValue.increment(liked ? 1 : -1)),
                            SetOptions.merge());
                })
                .addOnSuccessListener(v -> out.setValue(Resource.success(liked)))
                .addOnFailureListener(e -> fail(out, e, "confession_like"));
        return out;
    }

    private static <T> void fail(MutableLiveData<Resource<T>> out, Exception e, String context) {
        CrashReporter.record(e, context);
        out.setValue(Resource.error("Couldn't load confessions. Try again."));
    }
}
