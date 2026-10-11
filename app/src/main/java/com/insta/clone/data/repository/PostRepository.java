package com.insta.clone.data.repository;

import android.content.Context;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.insta.clone.data.local.SessionManager;
import com.insta.clone.data.model.Post;
import com.insta.clone.data.remote.RetrofitClient;
import com.insta.clone.data.remote.api.RestApi;
import com.insta.clone.data.remote.dto.PostDto;
import com.insta.clone.util.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Feed repository backed by v_feed/v_posts. It never seeds the UI with demo records. */
public class PostRepository {
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
                List<Post> posts = new ArrayList<>(); for (PostDto dto : response.body()) posts.add(dto.toDomain());
                feedPostsLiveData.postValue(cursor == null ? posts : append(posts)); result.postValue(Resource.success(posts));
            }
            @Override public void onFailure(Call<List<PostDto>> call, Throwable t) { result.postValue(Resource.error(t.getMessage())); }
        });
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
        restApi.getSavedPosts(query).enqueue(new Callback<List<PostDto>>() {
            @Override public void onResponse(Call<List<PostDto>> call, Response<List<PostDto>> response) {
                if (!response.isSuccessful() || response.body() == null) { result.postValue(Resource.error(AuthRepository.errorBody(response))); return; }
                List<Post> posts = new ArrayList<>(); for (PostDto dto : response.body()) posts.add(dto.toDomain()); result.postValue(Resource.success(posts));
            }
            @Override public void onFailure(Call<List<PostDto>> call, Throwable t) { result.postValue(Resource.error(t.getMessage())); }
        }); return result;
    }
    public void setFeedPosts(List<Post> posts) { feedPostsLiveData.postValue(posts == null ? new ArrayList<>() : posts); }
    public void addPost(Post post) { List<Post> all = new ArrayList<>(); all.add(post); if (feedPostsLiveData.getValue() != null) all.addAll(feedPostsLiveData.getValue()); feedPostsLiveData.postValue(all); }

    public LiveData<Resource<Boolean>> toggleLike(String postId, boolean shouldLike) {
        MutableLiveData<Resource<Boolean>> result = new MutableLiveData<>(); result.postValue(Resource.loading());
        String uid = sessionManager.getUserId(); if (uid == null) { result.postValue(Resource.error("Not authenticated")); return result; }
        if (shouldLike) {
            Map<String, Object> body = new HashMap<>(); body.put("post_id", postId); body.put("user_id", uid);
            restApi.likePost(body).enqueue(voidCallback(result, true));
        } else restApi.unlikePost("eq." + postId, "eq." + uid).enqueue(voidCallback(result, false));
        return result;
    }

    public LiveData<Resource<Boolean>> toggleSave(String postId, boolean shouldSave) {
        MutableLiveData<Resource<Boolean>> result = new MutableLiveData<>(); result.postValue(Resource.loading());
        String uid = sessionManager.getUserId(); if (uid == null) { result.postValue(Resource.error("Not authenticated")); return result; }
        if (shouldSave) {
            Map<String, Object> body = new HashMap<>(); body.put("post_id", postId); body.put("user_id", uid);
            restApi.savePost(body).enqueue(voidCallback(result, true));
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
}
