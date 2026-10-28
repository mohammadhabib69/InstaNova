package com.insta.nova.data.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.insta.nova.data.firebase.CrashReporter;
import com.insta.nova.data.firebase.FirebaseData;
import com.insta.nova.data.firebase.Timestamps;
import com.insta.nova.util.Resource;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Likes (task P4-03).
 *
 * <p>The like document id is {@code "{postId}_{uid}"}, so double-tapping cannot create a
 * duplicate and unlike is a delete, not a scan. The counter rides in the same batch.
 */
public class LikeRepository {

    private final FirebaseFirestore db;

    public LikeRepository() {
        this.db = FirebaseData.firestore();
    }

    public static String likeId(@NonNull String postId, @NonNull String uid) {
        return postId + "_" + uid;
    }

    public LiveData<Resource<Boolean>> setLiked(@NonNull String postId, @NonNull String uid, boolean liked) {
        MutableLiveData<Resource<Boolean>> out = new MutableLiveData<>(Resource.loading());
        String id = likeId(postId, uid);
        Map<String, Object> counter = Collections.singletonMap(
                "likesCount", FieldValue.increment(liked ? 1 : -1));

        db.runBatch(batch -> {
                    if (liked) {
                        Map<String, Object> like = new HashMap<>();
                        like.put("likeId", id);
                        like.put("postId", postId);
                        like.put("uid", uid);
                        like.put("createdAt", Timestamps.serverNow());
                        batch.set(db.collection("likes").document(id), like);
                    } else {
                        batch.delete(db.collection("likes").document(id));
                    }
                    batch.set(db.collection("posts").document(postId), counter, SetOptions.merge());
                })
                .addOnSuccessListener(v -> out.setValue(Resource.success(liked)))
                .addOnFailureListener(e -> fail(out, e, "like_set"));
        return out;
    }

    public LiveData<Resource<Boolean>> isLiked(@NonNull String postId, @NonNull String uid) {
        MutableLiveData<Resource<Boolean>> out = new MutableLiveData<>(Resource.loading());
        db.collection("likes").document(likeId(postId, uid)).get()
                .addOnSuccessListener(doc -> out.setValue(Resource.success(doc.exists())))
                .addOnFailureListener(e -> fail(out, e, "like_state"));
        return out;
    }

    public LiveData<Resource<Long>> count(@NonNull String postId) {
        MutableLiveData<Resource<Long>> out = new MutableLiveData<>(Resource.loading());
        db.collection("likes").whereEqualTo("postId", postId).get()
                .addOnSuccessListener(snap -> out.setValue(Resource.success((long) snap.size())))
                .addOnFailureListener(e -> fail(out, e, "like_count"));
        return out;
    }

    private static <T> void fail(MutableLiveData<Resource<T>> out, Exception e, String context) {
        CrashReporter.record(e, context);
        out.setValue(Resource.error("Couldn't update like. Try again."));
    }
}
