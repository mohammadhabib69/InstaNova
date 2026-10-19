package com.insta.nova.data.local;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.insta.nova.data.model.Comment;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Local, per-account persistence for post comments.
 *
 * <p>Mirrors the SharedPreferences + Gson pattern used by {@link RecentSearchStore}.
 * Comments are keyed by {@code <account>_<postId>} so switching accounts never leaks
 * another user's thread, and the first successful server read is imported once
 * ("hydrated") so existing comments and their counts survive offline sessions.
 */
public class CommentStore {

    private static final String PREF_NAME = "insta_comments_prefs";
    private static final String KEY_COMMENTS = "comments_";
    private static final String KEY_HYDRATED = "hydrated_";

    /** Bumped whenever any thread changes, so the feed can refresh its counts. */
    private static final MutableLiveData<Integer> REVISION = new MutableLiveData<>(0);

    private final SharedPreferences prefs;
    private final Gson gson;
    private final String accountKey;
    private final String myUsername;
    private final String myAvatarUrl;

    public CommentStore(Context context) {
        Context app = context.getApplicationContext();
        this.prefs = app.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
        SessionManager session = SessionManager.getInstance(app);
        String userId = session.getUserId();
        this.accountKey = userId == null || userId.trim().isEmpty() ? "local" : userId;
        this.myUsername = session.getUsername();
        this.myAvatarUrl = session.getAvatarUrl();
    }

    /** Process-wide change signal: value increases by one on every mutation. */
    public static LiveData<Integer> revision() {
        return REVISION;
    }

    private static void notifyChanged() {
        Integer current = REVISION.getValue();
        REVISION.setValue(current == null ? 1 : current + 1);
    }

    private String commentsKey(String postId) {
        return KEY_COMMENTS + accountKey + "_" + postId;
    }

    private String hydratedKey(String postId) {
        return KEY_HYDRATED + accountKey + "_" + postId;
    }

    public List<Comment> getComments(String postId) {
        if (postId == null || postId.isEmpty()) return new ArrayList<>();
        String json = prefs.getString(commentsKey(postId), null);
        if (json == null) return new ArrayList<>();
        try {
            Type type = new TypeToken<List<Comment>>() {}.getType();
            List<Comment> parsed = gson.fromJson(json, type);
            return parsed == null ? new ArrayList<>() : parsed;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public int count(String postId) {
        return getComments(postId).size();
    }

    /** True once this post's thread has been imported or written locally. */
    public boolean isHydrated(String postId) {
        return postId != null && prefs.getBoolean(hydratedKey(postId), false);
    }

    /**
     * Imports the server thread the first time the sheet is opened. Local rows that
     * are not in the response (e.g. posted offline) are preserved.
     */
    public void hydrate(String postId, List<Comment> serverComments) {
        List<Comment> merged = new ArrayList<>();
        if (serverComments != null) merged.addAll(serverComments);
        for (Comment local : getComments(postId)) {
            boolean alreadyThere = false;
            for (Comment candidate : merged) {
                if (candidate.getId() != null && candidate.getId().equals(local.getId())) {
                    alreadyThere = true;
                    break;
                }
            }
            if (!alreadyThere) merged.add(local);
        }
        save(postId, merged);
        markHydrated(postId);
    }

    public void markHydrated(String postId) {
        if (postId == null || postId.isEmpty()) return;
        prefs.edit().putBoolean(hydratedKey(postId), true).apply();
        notifyChanged();
    }

    public void save(String postId, List<Comment> comments) {
        if (postId == null || postId.isEmpty()) return;
        List<Comment> safe = comments == null ? new ArrayList<>() : comments;
        prefs.edit().putString(commentsKey(postId), gson.toJson(safe)).apply();
    }

    /** Appends the signed-in user's comment and returns the stored row. */
    public Comment add(String postId, String text) {
        Comment comment = new Comment(
                UUID.randomUUID().toString(),
                postId,
                accountKey,
                myUsername == null || myUsername.trim().isEmpty() ? "you" : myUsername,
                myAvatarUrl,
                text == null ? "" : text.trim(),
                0,
                false,
                System.currentTimeMillis());

        List<Comment> comments = getComments(postId);
        comments.add(comment);
        save(postId, comments);
        markHydrated(postId);
        return comment;
    }

    public void toggleLike(String postId, String commentId) {
        List<Comment> comments = getComments(postId);
        for (Comment comment : comments) {
            if (comment.getId() != null && comment.getId().equals(commentId)) {
                boolean liked = !comment.isLikedByMe();
                comment.setLikedByMe(liked);
                comment.setLikesCount(Math.max(0, comment.getLikesCount() + (liked ? 1 : -1)));
                break;
            }
        }
        save(postId, comments);
        notifyChanged();
    }

    public void delete(String postId, String commentId) {
        List<Comment> comments = getComments(postId);
        for (int i = comments.size() - 1; i >= 0; i--) {
            if (commentId != null && commentId.equals(comments.get(i).getId())) {
                comments.remove(i);
            }
        }
        save(postId, comments);
        notifyChanged();
    }
}
