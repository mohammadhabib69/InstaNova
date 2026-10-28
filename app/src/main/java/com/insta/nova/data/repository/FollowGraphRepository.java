package com.insta.nova.data.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.insta.nova.data.firebase.CrashReporter;
import com.insta.nova.data.firebase.FirebaseData;
import com.insta.nova.data.firebase.Timestamps;
import com.insta.nova.util.Resource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Follow / unfollow / follow-request handling on Firestore (tasks P3-06, P3-07).
 *
 * <p>Named `FollowGraphRepository` on purpose: `FollowRepository` is the live Supabase
 * implementation and stays untouched until the Phase 3 cutover.
 *
 * <p>Model:
 * <ul>
 *   <li>Edge document {@code follows/{followerUid}_{followingUid}} — unique by id.</li>
 *   <li>Permanent counters on {@code publicProfiles} (`followersCount`, `followingCount`),
 *       adjusted with {@link FieldValue#increment(long)} so the write is atomic per field.</li>
 *   <li>Private targets get a {@code followRequests} document instead of an edge; accepting
 *       creates the edge and moves the counters.</li>
 * </ul>
 *
 * <p>Known limitation, accepted until Cloud Functions exist: the counterparty's
 * `followersCount` is incremented by the follower's device. Rules narrow that to exactly
 * one field by one step, and Phase 11 reconciles counters against the graph.
 */
public class FollowGraphRepository {

    private final FirebaseFirestore db;

    public FollowGraphRepository() {
        this.db = FirebaseData.firestore();
    }

    public static String edgeId(@NonNull String followerUid, @NonNull String followingUid) {
        return followerUid + "_" + followingUid;
    }

    public LiveData<Resource<Boolean>> follow(@NonNull String me, @NonNull String target, boolean targetIsPrivate) {
        MutableLiveData<Resource<Boolean>> out = new MutableLiveData<>(Resource.loading());
        if (me.equals(target)) {
            out.setValue(Resource.error("You can't follow yourself"));
            return out;
        }
        if (targetIsPrivate) {
            Map<String, Object> request = new HashMap<>();
            request.put("requesterUid", me);
            request.put("targetUid", target);
            request.put("status", "pending");
            request.put("createdAt", Timestamps.serverNow());
            db.collection("followRequests").document(edgeId(me, target)).set(request)
                    .addOnSuccessListener(v -> out.setValue(Resource.success(true)))
                    .addOnFailureListener(e -> fail(out, e, "follow_request"));
            return out;
        }

        String id = edgeId(me, target);
        Map<String, Object> edge = new HashMap<>();
        edge.put("edgeId", id);
        edge.put("followerUid", me);
        edge.put("followingUid", target);
        edge.put("createdAt", Timestamps.serverNow());

        db.runBatch(batch -> {
                    batch.set(db.collection("follows").document(id), edge);
                    batch.set(db.collection("publicProfiles").document(target),
                            java.util.Collections.singletonMap("followersCount", FieldValue.increment(1)),
                            SetOptions.merge());
                    batch.set(db.collection("publicProfiles").document(me),
                            java.util.Collections.singletonMap("followingCount", FieldValue.increment(1)),
                            SetOptions.merge());
                })
                .addOnSuccessListener(v -> out.setValue(Resource.success(true)))
                .addOnFailureListener(e -> fail(out, e, "follow"));
        return out;
    }

    public LiveData<Resource<Boolean>> unfollow(@NonNull String me, @NonNull String target) {
        MutableLiveData<Resource<Boolean>> out = new MutableLiveData<>(Resource.loading());
        String id = edgeId(me, target);
        db.runBatch(batch -> {
                    batch.delete(db.collection("follows").document(id));
                    batch.set(db.collection("publicProfiles").document(target),
                            java.util.Collections.singletonMap("followersCount", FieldValue.increment(-1)),
                            SetOptions.merge());
                    batch.set(db.collection("publicProfiles").document(me),
                            java.util.Collections.singletonMap("followingCount", FieldValue.increment(-1)),
                            SetOptions.merge());
                })
                .addOnSuccessListener(v -> out.setValue(Resource.success(true)))
                .addOnFailureListener(e -> fail(out, e, "unfollow"));
        return out;
    }

    /** Target accepts a pending request: status flips and the edge + counters are created. */
    public LiveData<Resource<Boolean>> acceptRequest(@NonNull String target, @NonNull String requester) {
        MutableLiveData<Resource<Boolean>> out = new MutableLiveData<>(Resource.loading());
        DocumentReference request = db.collection("followRequests").document(edgeId(requester, target));
        String id = edgeId(requester, target);

        db.runTransaction(tx -> {
            DocumentSnapshot snap = tx.get(request);
            if (!snap.exists() || !"pending".equals(snap.getString("status"))) {
                throw new IllegalStateException("request_not_pending");
            }
            Map<String, Object> edge = new HashMap<>();
            edge.put("edgeId", id);
            edge.put("followerUid", requester);
            edge.put("followingUid", target);
            edge.put("createdAt", Timestamps.serverNow());
            tx.set(db.collection("follows").document(id), edge);
            tx.update(request, "status", "accepted", "resolvedAt", Timestamps.serverNow());
            tx.set(db.collection("publicProfiles").document(target),
                    java.util.Collections.singletonMap("followersCount", FieldValue.increment(1)),
                    SetOptions.merge());
            tx.set(db.collection("publicProfiles").document(requester),
                    java.util.Collections.singletonMap("followingCount", FieldValue.increment(1)),
                    SetOptions.merge());
            return null;
        })
                .addOnSuccessListener(v -> out.setValue(Resource.success(true)))
                .addOnFailureListener(e -> fail(out, e, "accept_request"));
        return out;
    }

    public LiveData<Resource<Boolean>> rejectRequest(@NonNull String target, @NonNull String requester) {
        MutableLiveData<Resource<Boolean>> out = new MutableLiveData<>(Resource.loading());
        db.collection("followRequests").document(edgeId(requester, target))
                .update("status", "rejected", "resolvedAt", Timestamps.serverNow())
                .addOnSuccessListener(v -> out.setValue(Resource.success(true)))
                .addOnFailureListener(e -> fail(out, e, "reject_request"));
        return out;
    }

    public LiveData<Resource<Boolean>> isFollowing(@NonNull String me, @NonNull String target) {
        MutableLiveData<Resource<Boolean>> out = new MutableLiveData<>(Resource.loading());
        db.collection("follows").document(edgeId(me, target)).get()
                .addOnSuccessListener(doc -> out.setValue(Resource.success(doc.exists())))
                .addOnFailureListener(e -> fail(out, e, "is_following"));
        return out;
    }

    /** Follower uids of {@code uid}, newest first. */
    public LiveData<Resource<List<String>>> followers(@NonNull String uid, int limit) {
        return edgeQuery("followingUid", "followerUid", uid, limit);
    }

    /** Uids that {@code uid} follows, newest first. */
    public LiveData<Resource<List<String>>> following(@NonNull String uid, int limit) {
        return edgeQuery("followerUid", "followingUid", uid, limit);
    }

    private LiveData<Resource<List<String>>> edgeQuery(String matchField, String returnField,
                                                       String uid, int limit) {
        MutableLiveData<Resource<List<String>>> out = new MutableLiveData<>(Resource.loading());
        int cap = limit <= 0 ? PublicProfileRepository.DEFAULT_LIMIT : Math.min(limit, 100);
        db.collection("follows")
                .whereEqualTo(matchField, uid)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(cap)
                .get()
                .addOnSuccessListener(snap -> out.setValue(Resource.success(collect(snap, returnField))))
                .addOnFailureListener(e -> fail(out, e, "follow_edges"));
        return out;
    }

    private static List<String> collect(@NonNull QuerySnapshot snap, String field) {
        List<String> uids = new ArrayList<>(snap.size());
        for (DocumentSnapshot doc : snap.getDocuments()) {
            String value = doc.getString(field);
            if (value != null && !value.isEmpty()) {
                uids.add(value);
            }
        }
        return uids;
    }

    private static <T> void fail(MutableLiveData<Resource<T>> out, Exception e, String context) {
        CrashReporter.record(e, context);
        out.setValue(Resource.error("Couldn't update follow state. Try again."));
    }
}
