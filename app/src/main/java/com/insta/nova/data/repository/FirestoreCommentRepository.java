package com.insta.nova.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import java.util.Map;

/**
 * Comments and comment likes on Firestore (task P4-05).
 *
 * <p>Named {@code FirestoreCommentRepository} to avoid shadowing the live Supabase
 * {@code CommentRepository}.
 *
 * <p>A new comment and the post's {@code commentsCount} move in the SAME batch, so a
 * comment can never exist without its counter having been bumped, and vice versa. The
 * delete path does the same in reverse, and is allowed for the post owner as well as the
 * comment author (moderation), which the rule checks server-side.
 */
public class FirestoreCommentRepository {

    public static final int PAGE_SIZE = 20;

    private final FirebaseFirestore db;

    public FirestoreCommentRepository() {
        this.db = FirebaseData.firestore();
    }

    public LiveData<Resource<String>> add(@NonNull String postId, @NonNull String commentId,
                                          @NonNull String authorUid, @NonNull String text,
                                          @Nullable String parentId, @NonNull String audience) {
        MutableLiveData<Resource<String>> out = new MutableLiveData<>(Resource.loading());
        Map<String, Object> doc = new HashMap<>();
        doc.put("commentId", commentId);
        doc.put("postId", postId);
        doc.put("authorUid", authorUid);
        doc.put("text", text);
        doc.put("parentId", parentId == null ? "" : parentId);
        doc.put("audience", audience);
        doc.put("likesCount", 0L);
        doc.put("createdAt", Timestamps.serverNow());

        db.runBatch(batch -> {
                    batch.set(db.collection("comments").document(commentId), doc);
                    batch.set(db.collection("posts").document(postId),
                            Collections.singletonMap("commentsCount", FieldValue.increment(1)),
                            SetOptions.merge());
                })
                .addOnSuccessListener(v -> out.setValue(Resource.success(commentId)))
                .addOnFailureListener(e -> fail(out, e, "comment_add"));
        return out;
    }

    /** Oldest-first page, matching how a comment thread reads. */
    public LiveData<Resource<java.util.List<DocumentSnapshot>>> page(@NonNull String postId,
                                                                    @Nullable DocumentSnapshot cursor,
                                                                    int limit) {
        MutableLiveData<Resource<java.util.List<DocumentSnapshot>>> out =
                new MutableLiveData<>(Resource.loading());
        int cap = limit <= 0 ? PAGE_SIZE : Math.min(limit, 50);

        Query query = db.collection("comments")
                .whereEqualTo("postId", postId)
                .whereEqualTo("audience", "public")
                .orderBy("createdAt", Query.Direction.ASCENDING);
        if (cursor != null) {
            query = query.startAfter(cursor.get("createdAt"));
        }
        query.limit(cap).get()
                .addOnSuccessListener(snap -> out.setValue(Resource.success(snap.getDocuments())))
                .addOnFailureListener(e -> fail(out, e, "comment_page"));
        return out;
    }

    public LiveData<Resource<Void>> delete(@NonNull String postId, @NonNull String commentId) {
        MutableLiveData<Resource<Void>> out = new MutableLiveData<>(Resource.loading());
        db.runBatch(batch -> {
                    batch.delete(db.collection("comments").document(commentId));
                    batch.set(db.collection("posts").document(postId),
                            Collections.singletonMap("commentsCount", FieldValue.increment(-1)),
                            SetOptions.merge());
                })
                .addOnSuccessListener(v -> out.setValue(Resource.success(null)))
                .addOnFailureListener(e -> fail(out, e, "comment_delete"));
        return out;
    }

    public LiveData<Resource<Boolean>> setLiked(@NonNull String commentId, @NonNull String uid,
                                                boolean liked) {
        MutableLiveData<Resource<Boolean>> out = new MutableLiveData<>(Resource.loading());
        String id = commentId + "_" + uid;

        db.runBatch(batch -> {
                    if (liked) {
                        Map<String, Object> like = new HashMap<>();
                        like.put("likeId", id);
                        like.put("commentId", commentId);
                        like.put("uid", uid);
                        like.put("createdAt", Timestamps.serverNow());
                        batch.set(db.collection("commentLikes").document(id), like);
                    } else {
                        batch.delete(db.collection("commentLikes").document(id));
                    }
                    batch.set(db.collection("comments").document(commentId),
                            Collections.singletonMap("likesCount", FieldValue.increment(liked ? 1 : -1)),
                            SetOptions.merge());
                })
                .addOnSuccessListener(v -> out.setValue(Resource.success(liked)))
                .addOnFailureListener(e -> fail(out, e, "comment_like"));
        return out;
    }

    /**
     * Comment-only write.
     *
     * <p>{@link #add} bumps {@code posts/{postId}.commentsCount} in the same batch, which is
     * correct only when the post document itself lives in Firestore. The live app still reads
     * posts from Supabase, so that batch would try to create a post document and be refused by
     * the posts rule; the comment would then never be stored. This variant writes just the
     * comment, which the comments rule allows on its own.
     */
    public LiveData<Resource<String>> addCommentOnly(@NonNull String postId, @NonNull String commentId,
                                                     @NonNull String authorUid, @NonNull String text,
                                                     @NonNull String audience) {
        return addCommentOnly(postId, commentId, authorUid, text, audience, false);
    }

    /**
     * @param held true when the post owner has restricted this author. The document is
     *             written either way — the point is that {@code firestore.rules} then
     *             only returns it to its author and to the post owner, so the thread
     *             is held for review rather than published.
     */
    public LiveData<Resource<String>> addCommentOnly(@NonNull String postId, @NonNull String commentId,
                                                     @NonNull String authorUid, @NonNull String text,
                                                     @NonNull String audience, boolean held) {
        MutableLiveData<Resource<String>> out = new MutableLiveData<>(Resource.loading());
        Map<String, Object> doc = new HashMap<>();
        doc.put("commentId", commentId);
        doc.put("postId", postId);
        doc.put("authorUid", authorUid);
        doc.put("text", text);
        doc.put("parentId", "");
        doc.put("audience", audience);
        doc.put("likesCount", 0L);
        doc.put("held", held);
        doc.put("createdAt", Timestamps.serverNow());

        db.collection("comments").document(commentId).set(doc)
                .addOnSuccessListener(v -> out.setValue(Resource.success(commentId)))
                .addOnFailureListener(e -> fail(out, e, "comment_add"));
        return out;
    }

    /**
     * Releases a held comment. Only the post owner passes {@code firestore.rules}; the
     * author cannot clear their own flag.
     */
    public LiveData<Resource<Void>> releaseHeldComment(@NonNull String commentId) {
        MutableLiveData<Resource<Void>> out = new MutableLiveData<>(Resource.loading());
        db.collection("comments").document(commentId)
                .update("held", false)
                .addOnSuccessListener(v -> out.setValue(Resource.success(null)))
                .addOnFailureListener(e -> fail(out, e, "comment_release"));
        return out;
    }

    /** Comment delete without the post counter, for the same reason as {@link #addCommentOnly}. */
    public LiveData<Resource<Void>> deleteCommentOnly(@NonNull String commentId) {
        MutableLiveData<Resource<Void>> out = new MutableLiveData<>(Resource.loading());
        db.collection("comments").document(commentId).delete()
                .addOnSuccessListener(v -> out.setValue(Resource.success(null)))
                .addOnFailureListener(e -> fail(out, e, "comment_delete"));
        return out;
    }

    private static <T> void fail(MutableLiveData<Resource<T>> out, Exception e, String context) {
        CrashReporter.record(e, context);
        out.setValue(Resource.error("Couldn't update comments. Try again."));
    }
}
