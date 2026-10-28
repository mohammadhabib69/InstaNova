package com.insta.nova.data.repository;

import android.content.Context;
import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.model.Post;
import com.insta.nova.data.remote.InteractionConflict;
import com.insta.nova.data.remote.RetrofitClient;
import com.insta.nova.data.remote.api.RestApi;
import com.insta.nova.data.remote.dto.PostDto;
import com.insta.nova.util.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Feed repository backed by v_feed/v_posts. It never seeds the UI with demo records. */
public class PostRepository {
    private static final String TAG = "PostRepository";
    private final RestApi restApi;
    private final SessionManager sessionManager;
    private final MutableLiveData<List<Post>> feedPostsLiveData = new MutableLiveData<>(new ArrayList<>());

    public PostRepository(Context context) {
        Context app = context.getApplicationContext();
        restApi = RetrofitClient.getInstance(app).getRestApi();
        sessionManager = SessionManager.getInstance(app);
    }

    public LiveData<Resource<List<Post>>> getFeed(String cursor, int pageSize) {
        MutableLiveData<Resource<List<Post>>> result = new MutableLiveData<>(); result.postValue(Resource.loading());
        Map<String, String> query = new HashMap<>();
        query.put("is_reel", "eq.false"); query.put("order", "created_at.desc"); query.put("limit", String.valueOf(pageSize));
        if (cursor != null && !cursor.isEmpty()) query.put("created_at", "lt." + cursor);
        restApi.getFeed(query).enqueue(new Callback<List<PostDto>>() {
            @Override public void onResponse(Call<List<PostDto>> call, Response<List<PostDto>> response) {
                if (!response.isSuccessful() || response.body() == null) { result.postValue(Resource.error(AuthRepository.errorBody(response))); return; }
                List<Post> posts = new ArrayList<>();
                int skipped = 0;
                for (PostDto dto : response.body()) {
                    if (dto == null) {
                        skipped++;
                        continue;
                    }
                    try {
                        posts.add(dto.toDomain());
                    } catch (RuntimeException e) {
                        skipped++;
                        Log.e(TAG, "Skipping malformed feed post", e);
                    }
                }
                if (!response.body().isEmpty() && posts.isEmpty()) {
                    result.postValue(Resource.error("The feed response did not contain valid posts."));
                    return;
                }
                if (skipped > 0) Log.w(TAG, "Skipped " + skipped + " malformed feed post(s)");
                feedPostsLiveData.postValue(cursor == null ? posts : append(posts)); result.postValue(Resource.success(posts));
            }
            @Override public void onFailure(Call<List<PostDto>> call, Throwable t) {
                result.postValue(Resource.error(com.insta.nova.util.NetworkErrors.friendly(t, "Network error")));
            }
        });
        return result;
    }

    /**
     * The same feed request as {@link #getFeed(String, int)}, delivered straight to a
     * callback instead of through a LiveData.
     *
     * <p>The LiveData round trip was not delivering its success value to the Home
     * observer while the request itself returned 200, so the screen could only ever
     * show the cached copy. A direct callback removes that whole layer from the path.
     * Callbacks run on the main thread.
     */
    public void loadFeed(int pageSize, java.util.function.Consumer<List<Post>> onPosts,
                         java.util.function.Consumer<String> onError) {
        Map<String, String> query = new HashMap<>();
        query.put("is_reel", "eq.false");
        query.put("order", "created_at.desc");
        query.put("limit", String.valueOf(pageSize));
        restApi.getFeed(query).enqueue(new Callback<List<PostDto>>() {
            @Override public void onResponse(Call<List<PostDto>> call, Response<List<PostDto>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    String message = AuthRepository.errorBody(response);
                    new android.os.Handler(android.os.Looper.getMainLooper())
                            .post(() -> onError.accept(message));
                    return;
                }
                List<Post> posts = new ArrayList<>();
                for (PostDto dto : response.body()) {
                    if (dto == null) continue;
                    try {
                        posts.add(dto.toDomain());
                    } catch (RuntimeException e) {
                        Log.e(TAG, "Skipping malformed feed post", e);
                    }
                }
                feedPostsLiveData.postValue(posts);
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .post(() -> onPosts.accept(posts));
            }

            @Override public void onFailure(Call<List<PostDto>> call, Throwable t) {
                String message = com.insta.nova.util.NetworkErrors.friendly(t, "Network error");
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .post(() -> onError.accept(message));
            }
        });
    }

    /**
     * Public photo posts from every author, newest first, paged by offset.
     *
     * <p>Home used to read {@code v_feed}, which only exposes the viewer's own rows plus the
     * accounts they follow. An account that follows one quiet poster therefore saw nothing but
     * its own timeline. The discovery pool is the same view the profile and search already
     * read, so nothing new becomes visible: {@code v_posts} still applies the private-account
     * and block rules.
     */
    public void loadDiscoveryFeed(int pageSize, int offset,
                                  java.util.function.Consumer<List<Post>> onPosts,
                                  java.util.function.Consumer<String> onError) {
        Map<String, String> query = new HashMap<>();
        query.put("is_reel", "eq.false");
        query.put("space", "eq.insta");
        query.put("is_archived", "eq.false");
        query.put("order", "created_at.desc");
        query.put("limit", String.valueOf(pageSize));
        query.put("offset", String.valueOf(Math.max(0, offset)));

        try {
            restApi.getPosts(query).enqueue(new Callback<List<PostDto>>() {
                @Override
                public void onResponse(Call<List<PostDto>> call, Response<List<PostDto>> response) {
                    if (!response.isSuccessful() || response.body() == null) {
                        String message = AuthRepository.errorBody(response);
                        new android.os.Handler(android.os.Looper.getMainLooper())
                                .post(() -> onError.accept(message));
                        return;
                    }
                    List<Post> posts = new ArrayList<>();
                    for (PostDto dto : response.body()) {
                        if (dto == null) continue;
                        try {
                            posts.add(dto.toDomain());
                        } catch (RuntimeException e) {
                            Log.e(TAG, "Skipping malformed discovery post", e);
                        }
                    }
                    new android.os.Handler(android.os.Looper.getMainLooper())
                            .post(() -> onPosts.accept(posts));
                }

                @Override
                public void onFailure(Call<List<PostDto>> call, Throwable t) {
                    String message = com.insta.nova.util.NetworkErrors.friendly(t, "Network error");
                    new android.os.Handler(android.os.Looper.getMainLooper())
                            .post(() -> onError.accept(message));
                }
            });
        } catch (RuntimeException e) {
            Log.e(TAG, "Unable to request the discovery feed", e);
            onError.accept("Unable to load your feed.");
        }
    }

    /**
     * Every post in the network, newest first, keyset paginated.
     *
     * <p>This is the Nova hub's source: unlike {@link #getFeed(String, int)} it does not
     * force {@code is_reel=eq.false}, so photos and reels share one list. The cursor is the
     * newest row's {@code created_at}, which keeps paging stable regardless of the sort the
     * caller applies afterwards.
     *
     * @param reelsOnly {@code TRUE} reels only, {@code FALSE} photos/carousels only,
     *                  {@code null} everything.
     * @param authorId  when set, only that author's posts (the "Mine" filter).
     */
    public LiveData<Resource<List<Post>>> getAllPosts(String cursor, int pageSize,
                                                      Boolean reelsOnly, String authorId,
                                                      String space) {
        MutableLiveData<Resource<List<Post>>> result = new MutableLiveData<>();
        result.postValue(Resource.loading());

        Map<String, String> query = new HashMap<>();
        query.put("order", "created_at.desc");
        query.put("limit", String.valueOf(pageSize));
        if (reelsOnly != null) query.put("is_reel", reelsOnly ? "eq.true" : "eq.false");
        // Never let the two spaces mix: Nova reads only Nova rows, Home only Insta rows.
        if (space != null && !space.trim().isEmpty()) query.put("space", "eq." + space);
        if (authorId != null && !authorId.trim().isEmpty()) query.put("user_id", "eq." + authorId);
        // Archived rows belong to Settings → Archive only; no feed or grid lists them.
        query.put("is_archived", "eq.false");
        if (cursor != null && !cursor.isEmpty()) query.put("created_at", "lt." + cursor);

        try {
            restApi.getPosts(query).enqueue(new Callback<List<PostDto>>() {
                @Override
                public void onResponse(Call<List<PostDto>> call, Response<List<PostDto>> response) {
                    if (!response.isSuccessful() || response.body() == null) {
                        result.postValue(Resource.error(AuthRepository.errorBody(response)));
                        return;
                    }
                    List<Post> posts = new ArrayList<>();
                    int skipped = 0;
                    for (PostDto dto : response.body()) {
                        if (dto == null) {
                            skipped++;
                            continue;
                        }
                        try {
                            posts.add(dto.toDomain());
                        } catch (RuntimeException e) {
                            skipped++;
                            Log.e(TAG, "Skipping malformed Nova post", e);
                        }
                    }
                    if (skipped > 0) Log.w(TAG, "Skipped " + skipped + " malformed Nova post(s)");
                    result.postValue(Resource.success(posts));
                }

                @Override
                public void onFailure(Call<List<PostDto>> call, Throwable t) {
                    result.postValue(Resource.error(
                            com.insta.nova.util.NetworkErrors.friendly(t, "Network error")));
                }
            });
        } catch (RuntimeException e) {
            Log.e(TAG, "Unable to request Nova posts", e);
            result.postValue(Resource.error("Unable to load posts."));
        }
        return result;
    }

    private List<Post> append(List<Post> page) {
        List<Post> all = new ArrayList<>(feedPostsLiveData.getValue() == null ? new ArrayList<>() : feedPostsLiveData.getValue());
        all.addAll(page); return all;
    }

    public LiveData<List<Post>> getFeedPosts() { return feedPostsLiveData; }

    public LiveData<Resource<List<Post>>> getSavedPosts() {
        MutableLiveData<Resource<List<Post>>> result = new MutableLiveData<>(); result.postValue(Resource.loading());
        Map<String, String> query = new HashMap<>(); query.put("order", "saved_at.desc"); query.put("limit", "100");
        // The saved list belongs to Instagram: a Nova row never appears here.
        query.put("space", "eq.insta");
        restApi.getSavedPosts(query).enqueue(new Callback<List<PostDto>>() {
            @Override public void onResponse(Call<List<PostDto>> call, Response<List<PostDto>> response) {
                if (!response.isSuccessful() || response.body() == null) { result.postValue(Resource.error(AuthRepository.errorBody(response))); return; }
                List<Post> posts = new ArrayList<>(); for (PostDto dto : response.body()) posts.add(dto.toDomain()); result.postValue(Resource.success(posts));
            }
            @Override public void onFailure(Call<List<PostDto>> call, Throwable t) {
                result.postValue(Resource.error(com.insta.nova.util.NetworkErrors.friendly(t, "Network error")));
            }
        }); return result;
    }
    public void setFeedPosts(List<Post> posts) { feedPostsLiveData.postValue(posts == null ? new ArrayList<>() : posts); }
    public void addPost(Post post) { List<Post> all = new ArrayList<>(); all.add(post); if (feedPostsLiveData.getValue() != null) all.addAll(feedPostsLiveData.getValue()); feedPostsLiveData.postValue(all); }

    public LiveData<Resource<Boolean>> toggleLike(String postId, boolean shouldLike) {
        MutableLiveData<Resource<Boolean>> result = new MutableLiveData<>(); result.postValue(Resource.loading());
        String uid = sessionManager.getUserId();
        // getUserId() returns "" when the identity gate never resolved, not null: an empty
        // id must fail loudly here instead of being sent as user_id=eq.
        if (uid == null || uid.trim().isEmpty()) { result.postValue(Resource.error("Not authenticated")); return result; }
        if (shouldLike) {
            Map<String, Object> body = new HashMap<>(); body.put("post_id", postId); body.put("user_id", uid);
            restApi.likePost(InteractionConflict.LIKE, body).enqueue(voidCallback(result, true));
        } else restApi.unlikePost("eq." + postId, "eq." + uid).enqueue(voidCallback(result, false));
        return result;
    }

    public LiveData<Resource<Boolean>> toggleSave(String postId, boolean shouldSave) {
        MutableLiveData<Resource<Boolean>> result = new MutableLiveData<>(); result.postValue(Resource.loading());
        String uid = sessionManager.getUserId(); if (uid == null) { result.postValue(Resource.error("Not authenticated")); return result; }
        if (shouldSave) {
            Map<String, Object> body = new HashMap<>(); body.put("post_id", postId); body.put("user_id", uid);
            restApi.savePost(InteractionConflict.SAVE, body).enqueue(voidCallback(result, true));
        } else restApi.unsavePost("eq." + postId, "eq." + uid).enqueue(voidCallback(result, false));
        return result;
    }

    private Callback<Void> voidCallback(MutableLiveData<Resource<Boolean>> result, boolean value) {
        return new Callback<Void>() {
            @Override public void onResponse(Call<Void> call, Response<Void> response) { result.postValue(response.isSuccessful() ? Resource.success(value) : Resource.error(AuthRepository.errorBody(response))); }
            @Override public void onFailure(Call<Void> call, Throwable t) { result.postValue(Resource.error(t.getMessage())); }
        };
    }

    public LiveData<Resource<Boolean>> deletePost(String postId) {
        MutableLiveData<Resource<Boolean>> result = new MutableLiveData<>();
        restApi.deletePost("eq." + postId).enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> call, Response<Void> response) { result.postValue(response.isSuccessful() ? Resource.success(true) : Resource.error(AuthRepository.errorBody(response))); }
            @Override public void onFailure(Call<Void> call, Throwable t) { result.postValue(Resource.error(t.getMessage())); }
        }); return result;
    }

    /** Prompt N — archived posts of one user (v_posts now exposes is_archived). */
    public LiveData<Resource<List<Post>>> getArchivedPosts(String userId) {
        MutableLiveData<Resource<List<Post>>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));
        Map<String, String> query = new HashMap<>();
        query.put("user_id", "eq." + userId);
        query.put("is_archived", "eq.true");
        query.put("space", "eq.insta");
        query.put("order", "created_at.desc");
        query.put("limit", "200");
        restApi.getPosts(query).enqueue(new Callback<List<PostDto>>() {
            @Override
            public void onResponse(Call<List<PostDto>> call, Response<List<PostDto>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    result.postValue(Resource.error(AuthRepository.errorBody(response), null));
                    return;
                }
                List<Post> posts = new ArrayList<>();
                for (PostDto dto : response.body()) if (dto != null) posts.add(dto.toDomain());
                result.postValue(Resource.success(posts));
            }

            @Override
            public void onFailure(Call<List<PostDto>> call, Throwable t) {
                result.postValue(Resource.error(t.getMessage() == null ? "Network error" : t.getMessage(), null));
            }
        });
        return result;
    }

    public LiveData<Resource<Boolean>> setArchived(String postId, boolean archived) {
        MutableLiveData<Resource<Boolean>> result = new MutableLiveData<>();
        Map<String, Object> body = new HashMap<>();
        body.put("is_archived", archived);
        restApi.updatePost("eq." + postId, body).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                result.postValue(response.isSuccessful()
                        ? Resource.success(true) : Resource.error(AuthRepository.errorBody(response), false));
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                result.postValue(Resource.error(t.getMessage() == null ? "Network error" : t.getMessage(), false));
            }
        });
        return result;
    }

    public LiveData<Resource<Boolean>> updateCaption(String postId, String caption) {
        MutableLiveData<Resource<Boolean>> result = new MutableLiveData<>();
        Map<String, Object> body = new HashMap<>();
        body.put("caption", caption);
        restApi.updatePost("eq." + postId, body).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                result.postValue(response.isSuccessful()
                        ? Resource.success(true) : Resource.error(AuthRepository.errorBody(response), false));
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                result.postValue(Resource.error(t.getMessage() == null ? "Network error" : t.getMessage(), false));
            }
        });
        return result;
    }

    /**
     * Re-reads one post from v_posts so a detail screen opened from a grid, a search
     * result or a notification shows the real media, caption and counters instead of
     * whatever partial object the caller happened to serialise.
     */
    public LiveData<Resource<Post>> getPostById(String postId) {
        final MutableLiveData<Resource<Post>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));
        if (postId == null || postId.trim().isEmpty()) {
            result.setValue(Resource.error("Missing post", null));
            return result;
        }
        Map<String, String> query = new HashMap<>();
        query.put("id", "eq." + postId);
        query.put("limit", "1");
        restApi.getPosts(query).enqueue(new Callback<List<PostDto>>() {
            @Override
            public void onResponse(Call<List<PostDto>> call, Response<List<PostDto>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    result.postValue(Resource.error(AuthRepository.errorBody(response), null));
                    return;
                }
                if (response.body().isEmpty() || response.body().get(0) == null) {
                    result.postValue(Resource.error("Post not found", null));
                    return;
                }
                result.postValue(Resource.success(response.body().get(0).toDomain()));
            }

            @Override
            public void onFailure(Call<List<PostDto>> call, Throwable t) {
                result.postValue(Resource.error(t.getMessage() == null ? "Network error" : t.getMessage(), null));
            }
        });
        return result;
    }

    /**
     * The real likers of a post, newest first. The likes table is readable by every
     * signed-in user, and PostgREST embeds the liker's profile row, so the sheet can
     * show the same people the counter counts instead of placeholder names.
     */
    public LiveData<Resource<List<com.insta.nova.data.model.Profile>>> getLikers(String postId) {
        final MutableLiveData<Resource<List<com.insta.nova.data.model.Profile>>> result =
                new MutableLiveData<>();
        result.setValue(Resource.loading(null));
        if (postId == null || postId.trim().isEmpty()) {
            result.setValue(Resource.success(new ArrayList<>()));
            return result;
        }
        Map<String, String> query = new HashMap<>();
        query.put("select", "user_id,created_at,profiles(username,full_name,avatar_url)");
        query.put("post_id", "eq." + postId);
        query.put("order", "created_at.desc");
        query.put("limit", "200");
        restApi.getLikes(query).enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> call,
                                   Response<List<Map<String, Object>>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    result.postValue(Resource.error(AuthRepository.errorBody(response), null));
                    return;
                }
                List<com.insta.nova.data.model.Profile> people = new ArrayList<>();
                for (Map<String, Object> row : response.body()) {
                    if (row == null) continue;
                    Object userId = row.get("user_id");
                    if (!(userId instanceof String)) continue;
                    com.insta.nova.data.model.Profile profile =
                            new com.insta.nova.data.model.Profile();
                    profile.setId((String) userId);
                    Object embedded = row.get("profiles");
                    if (embedded instanceof Map) {
                        Map<?, ?> p = (Map<?, ?>) embedded;
                        Object username = p.get("username");
                        Object fullName = p.get("full_name");
                        Object avatar = p.get("avatar_url");
                        profile.setUsername(username instanceof String ? (String) username : null);
                        profile.setFullName(fullName instanceof String ? (String) fullName : null);
                        profile.setAvatarUrl(avatar instanceof String ? (String) avatar : null);
                    }
                    people.add(profile);
                }
                result.postValue(Resource.success(people));
            }

            @Override
            public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                result.postValue(Resource.error(t.getMessage() == null ? "Network error" : t.getMessage(), null));
            }
        });
        return result;
    }

    public LiveData<Resource<List<Post>>> getPostsByIds(List<String> ids) {
        MutableLiveData<Resource<List<Post>>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));
        if (ids == null || ids.isEmpty()) {
            result.setValue(Resource.success(new ArrayList<>()));
            return result;
        }
        Map<String, String> query = new HashMap<>();
        query.put("id", "in.(" + android.text.TextUtils.join(",", ids) + ")");
        query.put("space", "eq.insta");
        query.put("limit", "200");
        restApi.getPosts(query).enqueue(new Callback<List<PostDto>>() {
            @Override
            public void onResponse(Call<List<PostDto>> call, Response<List<PostDto>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    result.postValue(Resource.error(AuthRepository.errorBody(response), null));
                    return;
                }
                List<Post> posts = new ArrayList<>();
                for (PostDto dto : response.body()) if (dto != null) posts.add(dto.toDomain());
                result.postValue(Resource.success(posts));
            }

            @Override
            public void onFailure(Call<List<PostDto>> call, Throwable t) {
                result.postValue(Resource.error(t.getMessage() == null ? "Network error" : t.getMessage(), null));
            }
        });
        return result;
    }

    public LiveData<Resource<List<Post>>> getUserPosts(String userId) {
        MutableLiveData<Resource<List<Post>>> result = new MutableLiveData<>();
        result.postValue(Resource.loading());
        Map<String, String> query = new HashMap<>();
        query.put("user_id", "eq." + (userId != null ? userId : sessionManager.getUserId()));
        query.put("is_reel", "eq.false");
        // A profile grid is an Instagram surface: Nova rows never show on it.
        query.put("space", "eq.insta");
        // Archived posts leave the grid. v_posts exposes the flag but does not filter on
        // it, so the three-dot Archive action used to write the row and leave the tile.
        query.put("is_archived", "eq.false");
        query.put("order", "created_at.desc");
        restApi.getPosts(query).enqueue(new Callback<List<PostDto>>() {
            @Override
            public void onResponse(Call<List<PostDto>> call, Response<List<PostDto>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    result.postValue(Resource.error(AuthRepository.errorBody(response)));
                    return;
                }
                List<Post> posts = new ArrayList<>();
                for (PostDto dto : response.body()) {
                    if (dto != null) posts.add(dto.toDomain());
                }
                result.postValue(Resource.success(posts));
            }

            @Override
            public void onFailure(Call<List<PostDto>> call, Throwable t) {
                result.postValue(Resource.error(t.getMessage()));
            }
        });
        return result;
    }
}
