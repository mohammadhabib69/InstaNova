package com.insta.nova.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.insta.nova.data.firebase.CrashReporter;
import com.insta.nova.data.firebase.FirebaseData;
import com.insta.nova.data.firebase.Timestamps;
import com.insta.nova.data.firebase.model.StoryDoc;
import com.insta.nova.util.Resource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stories on Firestore (tasks P5-01..P5-04, P5-07).
 *
 * <p>Named {@code FirestoreStoryRepository}: {@code StoryRepository} is the live Supabase
 * implementation.
 *
 * <p>Expiry model: the client writes `expiresAt`, queries filter `expiresAt > now`, and a
 * scheduled Function (P5-04, blocked on billing) performs the actual delete. Until then an
 * expired story is invisible but still stored — acceptable because the rule refuses reads
 * of anything past expiry only via the query, so the UI never shows it.
 */
public class FirestoreStoryRepository {

    public static final int FEED_LIMIT = 50;

    private final FirebaseFirestore db;

    public FirestoreStoryRepository() {
        this.db = FirebaseData.firestore();
    }

    public LiveData<Resource<String>> create(@NonNull StoryDoc story) {
        MutableLiveData<Resource<String>> out = new MutableLiveData<>(Resource.loading());
        if (story.storyId == null || story.storyId.isEmpty()) {
            out.setValue(Resource.error("Missing story id"));
            return out;
        }
        if (story.expiresAt <= System.currentTimeMillis()) {
            out.setValue(Resource.error("Story expiry must be in the future"));
            return out;
        }
        Map<String, Object> data = story.toMap();
        data.put("createdAt", Timestamps.serverNow());
        db.collection("stories").document(story.storyId).set(data)
                .addOnSuccessListener(v -> out.setValue(Resource.success(story.storyId)))
                .addOnFailureListener(e -> fail(out, e, "story_create"));
        return out;
    }

    /**
     * Active public stories. The query constrains `audience` because the rule is
     * field-based — see SECURITY_PLAN §6 (rules are not filters).
     */
    public LiveData<Resource<List<StoryDoc>>> activePublic(int limit) {
        MutableLiveData<Resource<List<StoryDoc>>> out = new MutableLiveData<>(Resource.loading());
        int cap = limit <= 0 ? FEED_LIMIT : Math.min(limit, 100);
        db.collection("stories")
                .whereEqualTo("audience", StoryDoc.AUDIENCE_PUBLIC)
                .whereGreaterThan("expiresAt", System.currentTimeMillis())
                .orderBy("expiresAt", Query.Direction.ASCENDING)
                .limit(cap)
                .get()
                .addOnSuccessListener(snap -> out.setValue(Resource.success(mapAll(snap.getDocuments()))))
                .addOnFailureListener(e -> fail(out, e, "story_feed"));
        return out;
    }

    /** The caller's own stories, including expired ones for the archive view. */
    public LiveData<Resource<List<StoryDoc>>> byAuthor(@NonNull String uid, int limit) {
        MutableLiveData<Resource<List<StoryDoc>>> out = new MutableLiveData<>(Resource.loading());
        int cap = limit <= 0 ? FEED_LIMIT : Math.min(limit, 100);
        db.collection("stories")
                .whereEqualTo("authorUid", uid)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(cap)
                .get()
                .addOnSuccessListener(snap -> out.setValue(Resource.success(mapAll(snap.getDocuments()))))
                .addOnFailureListener(e -> fail(out, e, "story_by_author"));
        return out;
    }

    /**
     * Records a view. Deliberately does NOT bump a counter on the story document: the story
     * rule allows updates only by the author, and a viewer-driven counter would need the
     * same wide-open guard that likes use. The viewer list is the source of truth.
     */
    public LiveData<Resource<Void>> markViewed(@NonNull String storyId, @NonNull String viewerUid) {
        MutableLiveData<Resource<Void>> out = new MutableLiveData<>(Resource.loading());
        String id = storyId + "_" + viewerUid;
        Map<String, Object> doc = new HashMap<>();
        doc.put("viewId", id);
        doc.put("storyId", storyId);
        doc.put("viewerUid", viewerUid);
        doc.put("viewedAt", Timestamps.serverNow());
        db.collection("storyViews").document(id).set(doc)
                .addOnSuccessListener(v -> out.setValue(Resource.success(null)))
                .addOnFailureListener(e -> fail(out, e, "story_view"));
        return out;
    }

    /** Who saw this story. Only the author passes the rule. */
    public LiveData<Resource<List<String>>> viewers(@NonNull String storyId, int limit) {
        MutableLiveData<Resource<List<String>>> out = new MutableLiveData<>(Resource.loading());
        db.collection("storyViews")
                .whereEqualTo("storyId", storyId)
                .limit(limit <= 0 ? 100 : Math.min(limit, 200))
                .get()
                .addOnSuccessListener(snap -> {
                    List<String> uids = new ArrayList<>(snap.size());
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        String viewer = doc.getString("viewerUid");
                        if (viewer != null) {
                            uids.add(viewer);
                        }
                    }
                    out.setValue(Resource.success(uids));
                })
                .addOnFailureListener(e -> fail(out, e, "story_viewers"));
        return out;
    }

    public LiveData<Resource<Void>> delete(@NonNull String storyId) {
        MutableLiveData<Resource<Void>> out = new MutableLiveData<>(Resource.loading());
        db.collection("stories").document(storyId).delete()
                .addOnSuccessListener(v -> out.setValue(Resource.success(null)))
                .addOnFailureListener(e -> fail(out, e, "story_delete"));
        return out;
    }

    private static List<StoryDoc> mapAll(@NonNull List<DocumentSnapshot> docs) {
        List<StoryDoc> stories = new ArrayList<>(docs.size());
        for (DocumentSnapshot doc : docs) {
            StoryDoc story = StoryDoc.fromMap(doc.getData());
            if (story.storyId.isEmpty()) {
                story.storyId = doc.getId();
            }
            stories.add(story);
        }
        return stories;
    }

    private static <T> void fail(MutableLiveData<Resource<T>> out, Exception e, String context) {
        CrashReporter.record(e, context);
        out.setValue(Resource.error("Couldn't load stories. Try again."));
    }
}
