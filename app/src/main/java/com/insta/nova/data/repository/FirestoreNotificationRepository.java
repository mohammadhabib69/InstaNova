package com.insta.nova.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.insta.nova.data.firebase.CrashReporter;
import com.insta.nova.data.firebase.FirebaseData;
import com.insta.nova.data.firebase.Timestamps;
import com.insta.nova.util.Resource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Notification feed (tasks P7-01..P7-03, P7-08).
 *
 * <p>Documents are written by a Cloud Function only (the rule denies client creates), so
 * this repository is read/update/delete only — which is exactly what a recipient should be
 * able to do.
 *
 * <p>Replaces the simulated {@code NotificationCenter}: that class seeded plausible-looking
 * notifications into SharedPreferences at runtime, which meant the feed was fiction. This
 * reads real documents and returns empty when there are none.
 */
public class FirestoreNotificationRepository {

    public static final int PAGE_SIZE = 20;

    private final FirebaseFirestore db;

    public FirestoreNotificationRepository() {
        this.db = FirebaseData.firestore();
    }

    /** Newest-first page for a recipient. Cursor-based, like the feed. */
    public LiveData<Resource<List<DocumentSnapshot>>> page(@NonNull String recipientUid,
                                                           @Nullable DocumentSnapshot cursor,
                                                           int limit) {
        MutableLiveData<Resource<List<DocumentSnapshot>>> out =
                new MutableLiveData<>(Resource.loading());
        int cap = limit <= 0 ? PAGE_SIZE : Math.min(limit, 50);

        Query query = db.collection("notifications")
                .whereEqualTo("recipientUid", recipientUid)
                .orderBy("createdAt", Query.Direction.DESCENDING);
        if (cursor != null) {
            query = query.startAfter(cursor.get("createdAt"));
        }
        query.limit(cap).get()
                .addOnSuccessListener(snap -> out.setValue(Resource.success(snap.getDocuments())))
                .addOnFailureListener(e -> fail(out, e, "notifications_page"));
        return out;
    }

    /** Unread badge. One query; Firestore has no count aggregate on this SDK version. */
    public LiveData<Resource<Integer>> unreadCount(@NonNull String recipientUid) {
        MutableLiveData<Resource<Integer>> out = new MutableLiveData<>(Resource.loading());
        db.collection("notifications")
                .whereEqualTo("recipientUid", recipientUid)
                .whereEqualTo("read", false)
                .limit(100)
                .get()
                .addOnSuccessListener(snap -> out.setValue(Resource.success(snap.size())))
                .addOnFailureListener(e -> fail(out, e, "notifications_unread"));
        return out;
    }

    public LiveData<Resource<Void>> markRead(@NonNull String notificationId) {
        MutableLiveData<Resource<Void>> out = new MutableLiveData<>(Resource.loading());
        Map<String, Object> patch = new HashMap<>();
        patch.put("read", true);
        patch.put("readAt", Timestamps.serverNow());
        db.collection("notifications").document(notificationId).update(patch)
                .addOnSuccessListener(v -> out.setValue(Resource.success(null)))
                .addOnFailureListener(e -> fail(out, e, "notification_mark_read"));
        return out;
    }

    public LiveData<Resource<Void>> delete(@NonNull String notificationId) {
        MutableLiveData<Resource<Void>> out = new MutableLiveData<>(Resource.loading());
        db.collection("notifications").document(notificationId).delete()
                .addOnSuccessListener(v -> out.setValue(Resource.success(null)))
                .addOnFailureListener(e -> fail(out, e, "notification_delete"));
        return out;
    }

    /**
     * Shape a Function will write. Kept here so the client and the future Function agree on
     * field names, and so tests can seed documents that exactly match production.
     */
    public static Map<String, Object> document(@NonNull String recipientUid, @NonNull String type,
                                               @NonNull String actorUid, @NonNull String targetId,
                                               @NonNull String text) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("recipientUid", recipientUid);
        doc.put("type", type);
        doc.put("actorUid", actorUid);
        doc.put("targetId", targetId);
        doc.put("text", text);
        doc.put("read", false);
        doc.put("createdAt", FieldValue.serverTimestamp());
        return doc;
    }

    private static <T> void fail(MutableLiveData<Resource<T>> out, Exception e, String context) {
        CrashReporter.record(e, context);
        out.setValue(Resource.error("Couldn't load notifications. Try again."));
    }
}
