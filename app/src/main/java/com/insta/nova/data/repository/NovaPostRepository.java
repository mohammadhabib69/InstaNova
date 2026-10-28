package com.insta.nova.data.repository;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.gson.JsonElement;
import com.insta.nova.data.NovaApi;
import com.insta.nova.data.NovaDtos;
import com.insta.nova.data.NovaErrors;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.remote.RetrofitClient;
import com.insta.nova.util.Resource;
import com.insta.nova.util.TimeUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * The Nova feed's data source.
 *
 * <p>Everything here reads {@code nova_posts_public} / {@code nova_comments_public} and
 * writes through the Nova RPCs. The Instagram tables are never touched by this class:
 * the one exception is {@link #shareToInstagram}, which is the explicit,
 * opt-in "also share to Instagram" action.
 */
public class NovaPostRepository {

    private static final String TAG = "NovaPostRepository";

    private final NovaApi api;
    private final SessionManager session;

    public NovaPostRepository(Context context) {
        Context app = context.getApplicationContext();
        api = RetrofitClient.getInstance(app).getNovaApi();
        session = SessionManager.getInstance(app);
    }

    public String me() {
        return session.getUserId();
    }

    /* ------------------------------------------------------------------ */
    /* Feed                                                                 */
    /* ------------------------------------------------------------------ */

    /**
     * The Nova feed.
     *
     * @param contentType null for everything, otherwise a content_type value.
     * @param sort        "hot", "new" or "top".
     * @param savedOnly   true for the "Saved" chip.
     * @param mineOnly    true for the "Mine" chip.
     */
    public LiveData<Resource<List<NovaDtos.NovaPost>>> feed(String contentType,
                                                            String sort,
                                                            boolean savedOnly,
                                                            boolean mineOnly,
                                                            int limit,
                                                            int offset) {
        Map<String, String> query = new HashMap<>();
        query.put("select", "*");
        query.put("limit", String.valueOf(limit));
        if (offset > 0) {
            query.put("offset", String.valueOf(offset));
        }
        if (contentType != null && !contentType.trim().isEmpty()
                && !"all".equalsIgnoreCase(contentType)) {
            query.put("content_type", "eq." + contentType);
        }
        if (savedOnly) {
            query.put("is_saved", "eq.true");
        } else if (mineOnly) {
            query.put("is_mine", "eq.true");
        }
        // id.desc is appended to every order so the sort is total: without a
        // unique tiebreak, rows with the same rank or score can swap places
        // between pages and a paging client can miss or repeat them.
        if ("hot".equalsIgnoreCase(sort)) {
            // Every unvoted post has hot_rank 0, so falling back to recency
            // keeps a young feed readable instead of ordering by uuid.
            query.put("order", "hot_rank.desc,created_at.desc,id.desc");
        } else if ("top".equalsIgnoreCase(sort)) {
            query.put("order", "score.desc,created_at.desc,id.desc");
        } else {
            query.put("order", "created_at.desc,id.desc");
        }
        return call(api.getNovaPosts(query), "nova feed");
    }

    /* ------------------------------------------------------------------ */
    /* Comments                                                             */
    /* ------------------------------------------------------------------ */

    public LiveData<Resource<List<NovaDtos.NovaComment>>> comments(String postId) {
        Map<String, String> query = new HashMap<>();
        query.put("select", "*");
        query.put("post_id", "eq." + postId);
        query.put("order", "created_at.asc");
        query.put("limit", "300");
        return call(api.getNovaComments(query), "nova comments");
    }

    public LiveData<Resource<NovaDtos.CreatedRef>> createComment(String postId,
                                                                 String body,
                                                                 String parentId,
                                                                 boolean anonymous) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("p_post_id", postId);
        payload.put("p_body", body);
        payload.put("p_parent_id", parentId);
        payload.put("p_is_anonymous", anonymous);
        return call(api.createNovaComment(payload), "nova comment");
    }

    /**
     * Releases a comment that was held because its author is restricted by the post
     * owner. The server refuses the call for anyone but the owner, so the button is
     * only offered on a post this account wrote.
     */
    public LiveData<Resource<com.google.gson.JsonElement>> approveHeldComment(String commentId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("p_comment", commentId);
        return call(api.approveHeldNovaComment(payload), "approve comment");
    }

    /* ------------------------------------------------------------------ */
    /* Voting                                                               */
    /* ------------------------------------------------------------------ */

    /**
     * Casts a vote on a post.
     *
     * @param value 1 upvote, -1 downvote, 0 remove the vote. The server treats a
     *              repeat of the same value as a toggle-off, so the client can
     *              always send the intended value.
     */
    public LiveData<Resource<NovaDtos.VoteOutcome>> vote(String postId, int value) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("p_post_id", postId);
        payload.put("p_value", value);
        return call(api.castVote(payload), "nova vote");
    }

    /** Casts or changes a poll choice; the server returns the rebuilt counts. */
    public LiveData<Resource<NovaDtos.PollOutcome>> votePoll(String postId, int optionIndex) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("p_post", postId);
        payload.put("p_option", optionIndex);
        return call(api.votePoll(payload), "nova poll vote");
    }

    public LiveData<Resource<NovaDtos.VoteOutcome>> voteComment(String commentId, int value) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("p_comment_id", commentId);
        payload.put("p_value", value);
        return call(api.castCommentVote(payload), "nova comment vote");
    }

    /* ------------------------------------------------------------------ */
    /* Saving                                                               */
    /* ------------------------------------------------------------------ */

    public LiveData<Resource<Boolean>> setSaved(String postId, boolean saved) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("p_post_id", postId);
        payload.put("p_save", saved);
        return callForBoolean(api.toggleNovaSave(payload), "nova save");
    }

    /* ------------------------------------------------------------------ */
    /* Writing                                                              */
    /* ------------------------------------------------------------------ */

    /**
     * Creates a Nova post.
     *
     * <p>This never touches an Instagram table. The only way a Nova post reaches
     * Instagram is {@link #shareToInstagram}, which the composer only calls when
     * the author turned "Also share to Instagram" on.
     */
    public LiveData<Resource<NovaDtos.CreatedRef>> createPost(String body,
                                                               String mediaUrl,
                                                               String mediaType,
                                                               String contentType,
                                                               String flair,
                                                               boolean anonymous,
                                                               long expiresAtMillis,
                                                               Integer durationSeconds,
                                                               List<String> pollOptions) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("p_body", body);
        payload.put("p_media_url", mediaUrl);
        payload.put("p_media_type", mediaType);
        payload.put("p_content_type", contentType);
        payload.put("p_flair", flair);
        payload.put("p_is_anonymous", anonymous);
        if (expiresAtMillis > 0L) {
            payload.put("p_expires_at", TimeUtils.toSupabaseTimestamp(expiresAtMillis));
        }
        if (durationSeconds != null) {
            payload.put("p_duration_seconds", durationSeconds);
        }
        if (pollOptions != null && !pollOptions.isEmpty()) {
            payload.put("p_poll_options", toJsonArray(pollOptions));
        }
        return call(api.createNovaPost(payload), "nova post");
    }

    /**
     * The explicit "also share to Instagram" action.
     *
     * @param type "post", "story" or "reel".
     */
    public LiveData<Resource<Boolean>> shareToInstagram(String postId, String type) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("p_post_id", postId);
        payload.put("p_type", type);
        return callForBoolean(api.shareNovaToInstagram(payload), "share to instagram");
    }

    /* ------------------------------------------------------------------ */
    /* Helpers                                                              */
    /* ------------------------------------------------------------------ */

    /** Drops the rows whose expiry moment has passed. The server hides them too. */
    public static List<NovaDtos.NovaPost> withoutExpired(List<NovaDtos.NovaPost> posts) {
        if (posts == null) return new ArrayList<>();
        long now = System.currentTimeMillis();
        List<NovaDtos.NovaPost> alive = new ArrayList<>();
        for (NovaDtos.NovaPost post : posts) {
            if (post == null) continue;
            if (post.isExpired(now)) continue;
            alive.add(post);
        }
        return alive;
    }

    private static List<String> toJsonArray(List<String> values) {
        List<String> copy = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) copy.add(value.trim());
        }
        return copy;
    }

    private <T> LiveData<Resource<T>> call(Call<T> call, String label) {
        MutableLiveData<Resource<T>> out = new MutableLiveData<>(Resource.loading());
        call.enqueue(new Callback<T>() {
            @Override
            public void onResponse(Call<T> c, Response<T> response) {
                if (response.isSuccessful()) {
                    out.postValue(Resource.success(response.body()));
                } else {
                    String code = NovaErrors.code(response);
                    Log.w(TAG, label + " failed: " + code);
                    out.postValue(Resource.error(code));
                }
            }

            @Override
            public void onFailure(Call<T> c, Throwable t) {
                Log.w(TAG, label + " network failure: " + t.getMessage());
                out.postValue(Resource.error("network"));
            }
        });
        return out;
    }

    /**
     * The same call, for an RPC that answers with a json object instead of a
     * typed body: the caller only cares whether it worked.
     */
    private <T> LiveData<Resource<Boolean>> callForBoolean(Call<JsonElement> call, String label) {
        MutableLiveData<Resource<Boolean>> out = new MutableLiveData<>(Resource.loading());
        call.enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(Call<JsonElement> c, Response<JsonElement> response) {
                if (response.isSuccessful()) {
                    out.postValue(Resource.success(Boolean.TRUE));
                } else {
                    String code = NovaErrors.code(response);
                    Log.w(TAG, label + " failed: " + code);
                    out.postValue(Resource.error(code));
                }
            }

            @Override
            public void onFailure(Call<JsonElement> c, Throwable t) {
                Log.w(TAG, label + " network failure: " + t.getMessage());
                out.postValue(Resource.error("network"));
            }
        });
        return out;
    }

    /** Reads the id out of an RPC that answers with a json object. */
    public static String idOf(JsonElement element) {
        if (element == null || !element.isJsonObject()) return null;
        com.google.gson.JsonObject object = element.getAsJsonObject();
        return object.has("id") && !object.get("id").isJsonNull()
                ? object.get("id").getAsString() : null;
    }
}
