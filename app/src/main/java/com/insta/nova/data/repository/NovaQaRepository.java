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
import java.util.List;
import java.util.Map;

/**
 * NOVA Hub Q&amp;A (tasks P9-04, P9-06, P9-07).
 *
 * <p>Replaces the Postgres pair `nova_questions` / `nova_answers` plus the
 * `nova_qa_votes` table. Votes are one document per (target, user) so a double-tap cannot
 * count twice, and the counter rides in the same batch — the same pattern used for likes.
 *
 * <p>`solved`/`accepted` are separate: `accepted` lives on the answer and is writable only
 * by the question's author (rule-enforced via a `get()` on the parent question).
 */
public class NovaQaRepository {

    public static final int PAGE = 20;

    private final FirebaseFirestore db;

    public NovaQaRepository() {
        this.db = FirebaseData.firestore();
    }

    public LiveData<Resource<String>> ask(@NonNull String questionId, @NonNull String authorUid,
                                          @NonNull String title, @NonNull String body,
                                          @Nullable String subject, @NonNull String universityId) {
        MutableLiveData<Resource<String>> out = new MutableLiveData<>(Resource.loading());
        Map<String, Object> doc = new HashMap<>();
        doc.put("questionId", questionId);
        doc.put("authorUid", authorUid);
        doc.put("universityId", universityId);
        doc.put("title", title);
        doc.put("body", body);
        doc.put("subject", subject == null ? "" : subject);
        doc.put("audience", "public");
        doc.put("solved", false);
        doc.put("votes", 0L);
        doc.put("answersCount", 0L);
        doc.put("createdAt", Timestamps.serverNow());

        db.collection("novaQuestions").document(questionId).set(doc)
                .addOnSuccessListener(v -> out.setValue(Resource.success(questionId)))
                .addOnFailureListener(e -> fail(out, e, "nova_ask"));
        return out;
    }

    /**
     * Q&amp;A feed. {@code universityId} is nullable ON PURPOSE: the Postgres source has no
     * university column on {@code nova_questions} (nor on {@code profiles}), so scoping by
     * university would silently return an empty feed for every migrated row. Callers pass a
     * university only when they have one from campus membership.
     */
    public LiveData<Resource<List<DocumentSnapshot>>> questions(@Nullable String universityId,
                                                                @Nullable DocumentSnapshot cursor,
                                                                int limit) {
        MutableLiveData<Resource<List<DocumentSnapshot>>> out = new MutableLiveData<>(Resource.loading());
        int cap = limit <= 0 ? PAGE : Math.min(limit, 50);
        Query query = db.collection("novaQuestions")
                .whereEqualTo("audience", "public")
                .orderBy("createdAt", Query.Direction.DESCENDING);
        if (universityId != null && !universityId.isEmpty()) {
            query = db.collection("novaQuestions")
                    .whereEqualTo("universityId", universityId)
                    .whereEqualTo("audience", "public")
                    .orderBy("createdAt", Query.Direction.DESCENDING);
        }
        if (cursor != null) {
            query = query.startAfter(cursor.get("createdAt"));
        }
        query.limit(cap).get()
                .addOnSuccessListener(snap -> out.setValue(Resource.success(snap.getDocuments())))
                .addOnFailureListener(e -> fail(out, e, "nova_questions"));
        return out;
    }

    public LiveData<Resource<String>> answer(@NonNull String answerId, @NonNull String questionId,
                                             @NonNull String authorUid, @NonNull String body) {
        MutableLiveData<Resource<String>> out = new MutableLiveData<>(Resource.loading());
        Map<String, Object> doc = new HashMap<>();
        doc.put("answerId", answerId);
        doc.put("questionId", questionId);
        doc.put("authorUid", authorUid);
        doc.put("body", body);
        doc.put("audience", "public");
        doc.put("accepted", false);
        doc.put("votes", 0L);
        doc.put("createdAt", Timestamps.serverNow());

        db.runBatch(batch -> {
                    batch.set(db.collection("novaAnswers").document(answerId), doc);
                    batch.set(db.collection("novaQuestions").document(questionId),
                            Collections.singletonMap("answersCount", FieldValue.increment(1)),
                            SetOptions.merge());
                })
                .addOnSuccessListener(v -> out.setValue(Resource.success(answerId)))
                .addOnFailureListener(e -> fail(out, e, "nova_answer"));
        return out;
    }

    /** Question author marks one answer accepted. Rule checks ownership of the question. */
    public LiveData<Resource<Void>> acceptAnswer(@NonNull String answerId, @NonNull String questionId) {
        MutableLiveData<Resource<Void>> out = new MutableLiveData<>(Resource.loading());
        db.runBatch(batch -> {
                    batch.update(db.collection("novaAnswers").document(answerId), "accepted", true);
                    batch.update(db.collection("novaQuestions").document(questionId), "solved", true);
                })
                .addOnSuccessListener(v -> out.setValue(Resource.success(null)))
                .addOnFailureListener(e -> fail(out, e, "nova_accept"));
        return out;
    }

    public static String voteId(@NonNull String targetType, @NonNull String targetId, @NonNull String uid) {
        return targetType + "_" + targetId + "_" + uid;
    }

    /** Casts (+1) or removes (-1) a vote. Idempotent by document id. */
    public LiveData<Resource<Boolean>> vote(@NonNull String targetType, @NonNull String targetId,
                                            @NonNull String uid, boolean up) {
        MutableLiveData<Resource<Boolean>> out = new MutableLiveData<>(Resource.loading());
        String id = voteId(targetType, targetId, uid);
        String collection = "question".equals(targetType) ? "novaQuestions" : "novaAnswers";
        Map<String, Object> counter = Collections.singletonMap("votes", FieldValue.increment(up ? 1 : -1));

        db.runBatch(batch -> {
                    if (up) {
                        Map<String, Object> vote = new HashMap<>();
                        vote.put("voteId", id);
                        vote.put("targetType", targetType);
                        vote.put("targetId", targetId);
                        vote.put("uid", uid);
                        vote.put("createdAt", Timestamps.serverNow());
                        batch.set(db.collection("novaVotes").document(id), vote);
                    } else {
                        batch.delete(db.collection("novaVotes").document(id));
                    }
                    batch.set(db.collection(collection).document(targetId), counter, SetOptions.merge());
                })
                .addOnSuccessListener(v -> out.setValue(Resource.success(up)))
                .addOnFailureListener(e -> fail(out, e, "nova_vote"));
        return out;
    }

    private static <T> void fail(MutableLiveData<Resource<T>> out, Exception e, String context) {
        CrashReporter.record(e, context);
        out.setValue(Resource.error("Couldn't reach NOVA Hub. Try again."));
    }
}
