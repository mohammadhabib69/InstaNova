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
import com.insta.nova.data.firebase.model.PostDoc;
import com.insta.nova.util.Resource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Post writes and feed reads on Firestore (tasks P4-01, P4-02).
 *
 * <p>Named {@code FirestorePostRepository} because {@code PostRepository} is the live
 * Supabase implementation and must stay untouched until the Phase 4 cutover.
 *
 * <p>Pagination is cursor-based ({@code startAfter(lastCreatedAt, lastId)}), not offset:
 * Firestore charges by document read, and offsets re-read what the user already saw.
 */
public class FirestorePostRepository {

    public static final int PAGE_SIZE = 10;

    private final FirebaseFirestore db;

    public FirestorePostRepository() {
        this.db = FirebaseData.firestore();
    }

    public LiveData<Resource<String>> create(@NonNull PostDoc post) {
        MutableLiveData<Resource<String>> out = new MutableLiveData<>(Resource.loading());
        if (post.postId == null || post.postId.isEmpty()) {
            out.setValue(Resource.error("Missing post id"));
            return out;
        }
        Map<String, Object> data = post.toMap();
        // Rules require these to start at zero: counters are only ever moved by one step.
        data.put("likesCount", 0L);
        data.put("commentsCount", 0L);
        data.put("createdAt", Timestamps.serverNow());
        data.put("updatedAt", Timestamps.serverNow());

        db.collection("posts").document(post.postId).set(data)
                .addOnSuccessListener(v -> out.setValue(Resource.success(post.postId)))
                .addOnFailureListener(e -> fail(out, e, "post_create"));
        return out;
    }

    /** Newest-first feed page. Pass the previous page's last document to continue. */
    // P10-07: named trace so feed latency is attributable in the Perf dashboard instead of
    // being one anonymous network span among many.
    @com.google.firebase.perf.metrics.AddTrace(name = "feed_load")
    public LiveData<Resource<List<PostDoc>>> feed(@Nullable DocumentSnapshot cursor, int limit) {
        MutableLiveData<Resource<List<PostDoc>>> out = new MutableLiveData<>(Resource.loading());
        int cap = limit <= 0 ? PAGE_SIZE : Math.min(limit, 50);

        // The rule allows `audience == "public"` OR the caller's own posts. Firestore
        // rejects a list query whose rules are field-based unless the query constrains
        // the same field, so the public feed is its own query and own posts are fetched
        // separately (byAuthor) — see MIGRATION_LOG limitation L3.
        Query query = db.collection("posts")
                .whereEqualTo("audience", "public")
                .orderBy("createdAt", Query.Direction.DESCENDING);
        if (cursor != null) {
            query = query.startAfter(cursor.get("createdAt"));
        }
        query.limit(cap).get()
                .addOnSuccessListener(snap -> out.setValue(Resource.success(mapAll(snap.getDocuments()))))
                .addOnFailureListener(e -> fail(out, e, "post_feed"));
        return out;
    }

    public LiveData<Resource<List<PostDoc>>> byAuthor(@NonNull String authorUid, int limit) {
        MutableLiveData<Resource<List<PostDoc>>> out = new MutableLiveData<>(Resource.loading());
        int cap = limit <= 0 ? PAGE_SIZE : Math.min(limit, 50);
        db.collection("posts")
                .whereEqualTo("authorUid", authorUid)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(cap)
                .get()
                .addOnSuccessListener(snap -> out.setValue(Resource.success(mapAll(snap.getDocuments()))))
                .addOnFailureListener(e -> fail(out, e, "post_by_author"));
        return out;
    }

    public LiveData<Resource<Void>> updateCaption(@NonNull String postId, @NonNull String caption) {
        MutableLiveData<Resource<Void>> out = new MutableLiveData<>(Resource.loading());
        Map<String, Object> patch = new HashMap<>();
        patch.put("caption", caption);
        patch.put("updatedAt", FieldValue.serverTimestamp());
        db.collection("posts").document(postId).update(patch)
                .addOnSuccessListener(v -> out.setValue(Resource.success(null)))
                .addOnFailureListener(e -> fail(out, e, "post_caption"));
        return out;
    }

    public LiveData<Resource<Void>> delete(@NonNull String postId) {
        MutableLiveData<Resource<Void>> out = new MutableLiveData<>(Resource.loading());
        // Comment/like sub-documents are removed by the Phase 4 cleanup task together with
        // the Storage objects; deleting the post first keeps the UI consistent immediately.
        db.collection("posts").document(postId).delete()
                .addOnSuccessListener(v -> out.setValue(Resource.success(null)))
                .addOnFailureListener(e -> fail(out, e, "post_delete"));
        return out;
    }

    /**
     * Deletes a post and everything hanging off it (task P4-10).
     *
     * <p>Order matters: child documents first, post last. If the process dies halfway the
     * post is still there and the operation can be retried; the opposite order would leave
     * orphan likes/comments that no screen can reach.
     *
     * <p><b>Media is NOT deleted here.</b> Supabase Storage has read-only policies today
     * (no DELETE policy on {@code storage.objects}), so a client delete would silently fail;
     * object removal must run server-side once Cloud Functions exist. The caller must keep
     * the post's media paths so the cleanup job can find them.
     */
    public LiveData<Resource<Void>> deleteWithCascade(@NonNull String postId) {
        MutableLiveData<Resource<Void>> out = new MutableLiveData<>(Resource.loading());
        db.collection("likes").whereEqualTo("postId", postId).get()
                .addOnSuccessListener(likes -> {
                    com.google.firebase.firestore.WriteBatch batch = db.batch();
                    for (DocumentSnapshot like : likes.getDocuments()) {
                        batch.delete(like.getReference());
                    }
                    batch.commit()
                            .addOnSuccessListener(v -> deleteCommentsThenPost(postId, out))
                            .addOnFailureListener(e -> fail(out, e, "post_delete_likes"));
                })
                .addOnFailureListener(e -> fail(out, e, "post_delete_likes_query"));
        return out;
    }

    private void deleteCommentsThenPost(@NonNull String postId,
                                        MutableLiveData<Resource<Void>> out) {
        db.collection("comments").whereEqualTo("postId", postId).get()
                .addOnSuccessListener(comments -> {
                    com.google.firebase.firestore.WriteBatch batch = db.batch();
                    for (DocumentSnapshot comment : comments.getDocuments()) {
                        batch.delete(comment.getReference());
                    }
                    batch.commit()
                            .addOnSuccessListener(v -> db.collection("posts").document(postId).delete()
                                    .addOnSuccessListener(done -> out.setValue(Resource.success(null)))
                                    .addOnFailureListener(e -> fail(out, e, "post_delete_doc")))
                            .addOnFailureListener(e -> fail(out, e, "post_delete_comments"));
                })
                .addOnFailureListener(e -> fail(out, e, "post_delete_comments_query"));
    }

    private static List<PostDoc> mapAll(@NonNull List<DocumentSnapshot> docs) {
        List<PostDoc> posts = new ArrayList<>(docs.size());
        for (DocumentSnapshot doc : docs) {
            PostDoc post = PostDoc.fromMap(doc.getData());
            if (post.postId.isEmpty()) {
                post.postId = doc.getId();
            }
            posts.add(post);
        }
        return posts;
    }

    private static <T> void fail(MutableLiveData<Resource<T>> out, Exception e, String context) {
        CrashReporter.record(e, context);
        out.setValue(Resource.error("Couldn't load posts. Try again."));
    }
}
