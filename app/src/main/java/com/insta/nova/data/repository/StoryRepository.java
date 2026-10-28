package com.insta.nova.data.repository;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.model.Story;
import com.insta.nova.data.model.StoryGroup;
import com.insta.nova.data.remote.RetrofitClient;
import com.insta.nova.data.remote.api.RestApi;
import com.insta.nova.data.remote.dto.StoryDto;
import com.insta.nova.data.remote.dto.StoryGroupDto;
import com.insta.nova.util.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Active story tray repository backed by the RPC and v_stories view. */
public class StoryRepository {
    private static final String TAG = "StoryRepository";
    private final RestApi restApi;
    private final Supplier<String> viewerIdSupplier;
    private final MutableLiveData<List<StoryGroup>> rawGroups = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Resource<List<StoryGroup>>> groups = new MutableLiveData<>();

    /** Result of the most recent markStorySeen call. */
    private final MutableLiveData<Resource<Boolean>> seenState = new MutableLiveData<>();

    public StoryRepository(Context context) {
        this(RetrofitClient.getInstance(context.getApplicationContext()).getRestApi(),
                () -> SessionManager.getInstance(context.getApplicationContext()).getUserId());
    }

    /** Test seam: a fake API and a viewer-id resolver, so no Android runtime is needed. */
    @VisibleForTesting
    public StoryRepository(RestApi restApi, Supplier<String> viewerIdSupplier) {
        this.restApi = restApi;
        this.viewerIdSupplier = viewerIdSupplier;
    }

    public LiveData<Resource<List<StoryGroup>>> getStoryGroups() {
        groups.postValue(Resource.loading());
        restApi.getStoryGroups(Collections.emptyMap()).enqueue(new Callback<List<StoryGroupDto>>() {
            @Override public void onResponse(Call<List<StoryGroupDto>> call, Response<List<StoryGroupDto>> response) {
                if (!response.isSuccessful() || response.body() == null) { groups.postValue(Resource.error(AuthRepository.errorBody(response))); return; }
                List<StoryGroup> result = new ArrayList<>(); String uid = viewerIdSupplier.get();
                boolean hasOwnerGroup = false;
                int skipped = 0;
                for (StoryGroupDto dto : response.body()) {
                    if (dto == null) {
                        skipped++;
                        continue;
                    }
                    try {
                        StoryGroup group = dto.toStoryGroup();
                        group.setOwner(uid != null && uid.equals(dto.getUserId()));
                        if (group.isOwner()) hasOwnerGroup = true;
                        group.setHasActiveStory(true);
                        result.add(group);
                    } catch (RuntimeException e) {
                        skipped++;
                        Log.e(TAG, "Skipping malformed story group", e);
                    }
                }
                if (skipped > 0) Log.w(TAG, "Skipped " + skipped + " malformed story group(s)");
                // The owner must see their own story whatever its audience is.
                // v_story_groups hides non-public rows for everyone, owner included, so a
                // story posted as close_friends produced no ring in the tray at all. Read
                // the owner's own active stories directly and prepend them.
                // The view can also return an owner row with an empty story list; that
                // still yields no ring, so treat "owner row without stories" the same as
                // "no owner row" and replace it with the authoritative one.
                if (uid != null) {
                    StoryGroup emptyOwner = null;
                    for (StoryGroup g : result) {
                        if (g.isOwner()) {
                            if (g.getStories() == null || g.getStories().isEmpty()) emptyOwner = g;
                            break;
                        }
                    }
                    if (emptyOwner != null) { result.remove(emptyOwner); hasOwnerGroup = false; }
                }
                if (uid != null && !hasOwnerGroup) {
                    fetchOwnStories(uid, result);
                } else if (uid != null && result.isEmpty()) {
                    fetchOwnStories(uid, result);
                } else {
                    rawGroups.postValue(result); groups.postValue(Resource.success(result));
                }
            }
            @Override public void onFailure(Call<List<StoryGroupDto>> call, Throwable t) { groups.postValue(Resource.error(t.getMessage())); }
        });
        return groups;
    }

    /**
     * Loads the signed-in user's own active stories and puts them at the head of the tray.
     *
     * <p>Only reached when {@code v_story_groups} returned no owner group, which is what
     * happens for any story whose audience is not public. A published story that the
     * author cannot see in their own tray reads as a lost upload, so the owner row is
     * rebuilt from the authoritative {@code v_stories} rows instead of from the view.
     */
    private void fetchOwnStories(@NonNull String uid, @NonNull List<StoryGroup> result) {
        java.util.Map<String, String> query = new java.util.HashMap<>();
        query.put("user_id", "eq." + uid);
        query.put("order", "created_at.desc");

        restApi.getStories(query).enqueue(new Callback<List<StoryDto>>() {
            @Override
            public void onResponse(@NonNull Call<List<StoryDto>> call, @NonNull Response<List<StoryDto>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    StoryDto first = response.body().get(0);
                    StoryGroup group = new StoryGroup();
                    group.setUserId(uid);
                    group.setUsername(first.getUsername());
                    group.setAvatarUrl(first.getAvatarUrl());
                    group.setOwner(true);
                    group.setHasActiveStory(true);
                    group.setLatestStoryId(first.getId());
                    List<Story> stories = new ArrayList<>();
                    for (StoryDto dto : response.body()) {
                        try {
                            stories.add(dto.toStory());
                        } catch (RuntimeException e) {
                            Log.w(TAG, "Skipping malformed own story " + dto.getId(), e);
                        }
                    }
                    // The ring is drawn from hasUnseen, not from the story list. Rebuilding the
                    // owner group with the default (false) made a fresh own story render as a
                    // 1.5dp grey "seen" ring, which on a dark tray is indistinguishable from no
                    // ring at all — the tray read as "my story never published".
                    boolean unseen = false;
                    for (Story own : stories) {
                        if (own != null && !own.isSeen()) { unseen = true; break; }
                    }
                    group.setHasUnseen(unseen);
                    if (!stories.isEmpty()) {
                        group.setStories(stories);
                        result.add(0, group);
                    }
                    Log.i(TAG, "own-story fallback: rows=" + response.body().size()
                            + " mapped=" + stories.size()
                            + " tray=" + result.size()
                            + " ownerActive=" + (!stories.isEmpty()));
                } else {
                    Log.i(TAG, "own-story fallback returned nothing: code=" + response.code()
                            + " bodyEmpty=" + (response.body() == null || response.body().isEmpty()));
                }
                rawGroups.postValue(result);
                groups.postValue(Resource.success(result));
            }

            @Override
            public void onFailure(@NonNull Call<List<StoryDto>> call, @NonNull Throwable t) {
                // The tray must still render for everyone else; the owner row is the only
                // thing missing at this point.
                Log.w(TAG, "Own-story fallback failed", t);
                rawGroups.postValue(result);
                groups.postValue(Resource.success(result));
            }
        });
    }

    public LiveData<List<StoryGroup>> getRawStoryGroups() { return rawGroups; }
    public void setStoryGroups(List<StoryGroup> value) { rawGroups.postValue(value); groups.postValue(Resource.success(value)); }

    /**
     * Records a story view as the signed-in user.
     *
     * <p>The POST is treated as plain insert: the only error we accept as
     * "the view was already recorded" is the {@code 23505 duplicate key} reply
     * from the {@code story_views_pkey} constraint. Any other failure
     * (auth, network, invalid uuid, …) is propagated and the tray is NOT
     * refreshed optimistically.
     *
     * <p>The {@code userId} argument is preserved for callers that still pass
     * the story owner id; the viewer is always taken from
     * {@link SessionManager#getUserId()} (the resolved Supabase profile UUID).
     */
    public void markStorySeen(String userId, String storyId) {
        markStorySeen(storyId);
    }

    /** Observable variant of {@link #markStorySeen(String, String)}. */
    public LiveData<Resource<Boolean>> markStorySeen(String storyId) {
        final String viewerId = viewerIdSupplier.get();
        if (viewerId == null || viewerId.trim().isEmpty()) {
            // Identity gate never resolved a profiles.id; sending "" would
            // hit 22P02 on the uuid column. Surface the error rather than
            // silently leaving the seen ring stale.
            Log.w(TAG, "markStorySeen: missing viewer id, not calling API");
            seenState.postValue(Resource.error("Not authenticated", null));
            return seenState;
        }
        if (storyId == null || storyId.trim().isEmpty()) {
            seenState.postValue(Resource.error("Missing story id", null));
            return seenState;
        }
        seenState.postValue(Resource.loading(null));

        Map<String, Object> body = new HashMap<>();
        body.put("story_id", storyId);
        body.put("viewer_id", viewerId);

        restApi.recordStoryView(body).enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Log.d(TAG, "Story view recorded for " + storyId);
                    seenState.postValue(Resource.success(true));
                    getStoryGroups();
                    return;
                }
                ErrorBody err = parseErrorBody(response);
                if ("23505".equals(err.code)) {
                    // The expected duplicate-key reply: the (story_id, viewer_id)
                    // row already exists, so the view is already on record.
                    Log.d(TAG, "Story view already on record (23505) for " + storyId);
                    seenState.postValue(Resource.success(true));
                    getStoryGroups();
                    return;
                }
                Log.w(TAG, "Story view insert rejected code=" + err.code
                        + " http=" + response.code() + " message=" + err.message);
                seenState.postValue(Resource.error(
                        "Story view insert failed (HTTP " + response.code() + ")", null));
            }

            @Override public void onFailure(Call<Void> call, Throwable t) {
                Log.w(TAG, "Story view insert network error: " + t.getMessage());
                seenState.postValue(Resource.error(
                        t.getMessage() != null ? t.getMessage() : "Network error", null));
            }
        });
        return seenState;
    }

    /** Stream of markStorySeen outcomes. */
    public LiveData<Resource<Boolean>> getSeenState() { return seenState; }

    /**
     * Decide whether a viewer should record a view for a given story. The
     * owner of a story is never recorded as a viewer of their own story —
     * a view is an audit event of someone else watching the story, and the
     * tray would otherwise mark the owner's own stories as "seen" the first
     * time they opened the preview.
     */
    public static boolean shouldRecordView(@NonNull String viewerId, @NonNull String ownerId) {
        if (viewerId.isEmpty() || ownerId.isEmpty()) return false;
        return !viewerId.equals(ownerId);
    }

    private static ErrorBody parseErrorBody(Response<?> response) {
        try {
            if (response.errorBody() == null) return new ErrorBody("", "");
            String raw = response.errorBody().string();
            if (raw.isEmpty()) return new ErrorBody("", "");
            JsonObject obj = JsonParser.parseString(raw).getAsJsonObject();
            String code = obj.has("code") ? obj.get("code").getAsString() : "";
            String message = obj.has("message") ? obj.get("message").getAsString() : raw;
            return new ErrorBody(code, message);
        } catch (Exception e) {
            return new ErrorBody("", "");
        }
    }

    private static final class ErrorBody {
        final String code;
        final String message;
        ErrorBody(String code, String message) {
            this.code = code == null ? "" : code;
            this.message = message == null ? "" : message;
        }
    }
}