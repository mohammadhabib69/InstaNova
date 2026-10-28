package com.insta.nova.data.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.firestore.FirebaseFirestore;
import com.insta.nova.data.firebase.CrashReporter;
import com.insta.nova.data.firebase.FirebaseData;
import com.insta.nova.data.firebase.Timestamps;
import com.insta.nova.util.Resource;

import java.util.HashMap;
import java.util.Map;

/**
 * Saved posts (task P4-04).
 *
 * <p>Private to the saver: the document is readable only by its owner, unlike likes which
 * are public so "who liked this" can work. No counter on the post — a save count nobody
 * displays would be a write on every save for nothing.
 */
public class SavedPostRepository {

    private final FirebaseFirestore db;

    public SavedPostRepository() {
        this.db = FirebaseData.firestore();
    }

    public static String saveId(@NonNull String uid, @NonNull String postId) {
        return uid + "_" + postId;
    }

    public LiveData<Resource<Boolean>> setSaved(@NonNull String uid, @NonNull String postId, boolean saved) {
        MutableLiveData<Resource<Boolean>> out = new MutableLiveData<>(Resource.loading());
        String id = saveId(uid, postId);

        if (saved) {
            Map<String, Object> doc = new HashMap<>();
            doc.put("saveId", id);
            doc.put("uid", uid);
            doc.put("postId", postId);
            doc.put("createdAt", Timestamps.serverNow());
            db.collection("savedPosts").document(id).set(doc)
                    .addOnSuccessListener(v -> out.setValue(Resource.success(true)))
                    .addOnFailureListener(e -> fail(out, e, "save_set"));
        } else {
            db.collection("savedPosts").document(id).delete()
                    .addOnSuccessListener(v -> out.setValue(Resource.success(false)))
                    .addOnFailureListener(e -> fail(out, e, "save_clear"));
        }
        return out;
    }

    public LiveData<Resource<Boolean>> isSaved(@NonNull String uid, @NonNull String postId) {
        MutableLiveData<Resource<Boolean>> out = new MutableLiveData<>(Resource.loading());
        db.collection("savedPosts").document(saveId(uid, postId)).get()
                .addOnSuccessListener(doc -> out.setValue(Resource.success(doc.exists())))
                .addOnFailureListener(e -> fail(out, e, "save_state"));
        return out;
    }

    private static <T> void fail(MutableLiveData<Resource<T>> out, Exception e, String context) {
        CrashReporter.record(e, context);
        out.setValue(Resource.error("Couldn't update saved posts. Try again."));
    }
}
