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
 * Mute, restricted and close-friend edges (task P3-09).
 *
 * <p>All three are the same shape — a private flag on a relationship — so they share one
 * collection with a {@code kind} discriminator instead of three near-identical ones.
 * The muted account is never notified and never sees this document.
 */
public class MuteRepository {

    public static final String KIND_MUTE = "mute";
    public static final String KIND_RESTRICTED = "restricted";
    public static final String KIND_CLOSE_FRIEND = "close_friend";

    private final FirebaseFirestore db;

    public MuteRepository() {
        this.db = FirebaseData.firestore();
    }

    public LiveData<Resource<Boolean>> set(@NonNull String me, @NonNull String target,
                                           @NonNull String kind, boolean on) {
        MutableLiveData<Resource<Boolean>> out = new MutableLiveData<>(Resource.loading());
        String id = FollowGraphRepository.edgeId(me, target);
        if (on) {
            Map<String, Object> doc = new HashMap<>();
            doc.put("edgeId", id);
            doc.put("muterUid", me);
            doc.put("mutedUid", target);
            doc.put("kind", kind);
            doc.put("createdAt", Timestamps.serverNow());
            db.collection("mutes").document(id).set(doc)
                    .addOnSuccessListener(v -> out.setValue(Resource.success(true)))
                    .addOnFailureListener(e -> fail(out, e, "mute_set"));
        } else {
            db.collection("mutes").document(id).delete()
                    .addOnSuccessListener(v -> out.setValue(Resource.success(true)))
                    .addOnFailureListener(e -> fail(out, e, "mute_clear"));
        }
        return out;
    }

    public LiveData<Resource<String>> kindOf(@NonNull String me, @NonNull String target) {
        MutableLiveData<Resource<String>> out = new MutableLiveData<>(Resource.loading());
        db.collection("mutes").document(FollowGraphRepository.edgeId(me, target)).get()
                .addOnSuccessListener(doc ->
                        out.setValue(Resource.success(doc.exists() ? doc.getString("kind") : null)))
                .addOnFailureListener(e -> {
                    CrashReporter.record(e, "mute_get");
                    out.setValue(Resource.error("Couldn't load mute state."));
                });
        return out;
    }

    private static <T> void fail(MutableLiveData<Resource<T>> out, Exception e, String context) {
        CrashReporter.record(e, context);
        out.setValue(Resource.error("Couldn't update mute state. Try again."));
    }
}
