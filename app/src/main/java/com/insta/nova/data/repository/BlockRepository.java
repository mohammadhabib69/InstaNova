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
 * Block / unblock (task P3-08).
 *
 * <p>A block must also tear down the follow edges in both directions, otherwise the
 * blocked account keeps seeing posts through an existing edge. Both directions are
 * removed in the same batch as the block document, and either side is allowed to delete
 * an edge by rule, so no privileged credential is needed.
 */
public class BlockRepository {

    private final FirebaseFirestore db;

    public BlockRepository() {
        this.db = FirebaseData.firestore();
    }

    public LiveData<Resource<Boolean>> block(@NonNull String me, @NonNull String target) {
        MutableLiveData<Resource<Boolean>> out = new MutableLiveData<>(Resource.loading());
        String id = FollowGraphRepository.edgeId(me, target);

        Map<String, Object> doc = new HashMap<>();
        doc.put("edgeId", id);
        doc.put("blockerUid", me);
        doc.put("blockedUid", target);
        doc.put("createdAt", Timestamps.serverNow());

        db.runBatch(batch -> {
                    batch.set(db.collection("blocks").document(id), doc);
                    batch.delete(db.collection("follows").document(id));
                    batch.delete(db.collection("follows").document(FollowGraphRepository.edgeId(target, me)));
                    // A pending request in either direction is meaningless once blocked.
                    batch.delete(db.collection("followRequests").document(id));
                    batch.delete(db.collection("followRequests").document(FollowGraphRepository.edgeId(target, me)));
                })
                .addOnSuccessListener(v -> out.setValue(Resource.success(true)))
                .addOnFailureListener(e -> fail(out, e, "block"));
        return out;
    }

    public LiveData<Resource<Boolean>> unblock(@NonNull String me, @NonNull String target) {
        MutableLiveData<Resource<Boolean>> out = new MutableLiveData<>(Resource.loading());
        db.collection("blocks").document(FollowGraphRepository.edgeId(me, target)).delete()
                .addOnSuccessListener(v -> out.setValue(Resource.success(true)))
                .addOnFailureListener(e -> fail(out, e, "unblock"));
        return out;
    }

    public LiveData<Resource<Boolean>> isBlocked(@NonNull String me, @NonNull String target) {
        MutableLiveData<Resource<Boolean>> out = new MutableLiveData<>(Resource.loading());
        db.collection("blocks").document(FollowGraphRepository.edgeId(me, target)).get()
                .addOnSuccessListener(doc -> out.setValue(Resource.success(doc.exists())))
                .addOnFailureListener(e -> fail(out, e, "is_blocked"));
        return out;
    }

    private static <T> void fail(MutableLiveData<Resource<T>> out, Exception e, String context) {
        CrashReporter.record(e, context);
        out.setValue(Resource.error("Couldn't update block state. Try again."));
    }
}
