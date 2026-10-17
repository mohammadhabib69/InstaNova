package com.insta.nova.data.repository;

import android.content.Context;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.model.Post;
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

/**
 * Repository handling Reels feed pagination, playback metadata, and Reel uploads.
 */
public class ReelRepository {

    private final RestApi restApi;
    private final SessionManager sessionManager;

    public ReelRepository(Context context) {
        RetrofitClient client = RetrofitClient.getInstance(context);
        this.restApi = client.getRestApi();
        this.sessionManager = SessionManager.getInstance(context);
    }

    public LiveData<Resource<List<Post>>> getReels(int limit, int offset) {
        MutableLiveData<Resource<List<Post>>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));

        Map<String, String> query = new HashMap<>();
        query.put("is_reel", "eq.true");
        query.put("order", "created_at.desc");
        query.put("limit", String.valueOf(limit));
        query.put("offset", String.valueOf(offset));

        restApi.getPosts(query).enqueue(new Callback<List<PostDto>>() {
            @Override
            public void onResponse(Call<List<PostDto>> call, Response<List<PostDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Post> reels = new ArrayList<>();
                    for (PostDto dto : response.body()) {
                        reels.add(dto.toDomain());
                    }
                    result.postValue(Resource.success(reels));
                } else {
                    result.postValue(Resource.error("Failed to load reels", null));
                }
            }

            @Override
            public void onFailure(Call<List<PostDto>> call, Throwable t) {
                result.postValue(Resource.error(t.getMessage() != null ? t.getMessage() : "Network error", null));
            }
        });

        return result;
    }

    public LiveData<Resource<List<Post>>> getUserReels(String userId) {
        MutableLiveData<Resource<List<Post>>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));

        Map<String, String> query = new HashMap<>();
        query.put("user_id", "eq." + userId);
        query.put("is_reel", "eq.true");
        query.put("order", "created_at.desc");

        restApi.getPosts(query).enqueue(new Callback<List<PostDto>>() {
            @Override
            public void onResponse(Call<List<PostDto>> call, Response<List<PostDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Post> reels = new ArrayList<>();
                    for (PostDto dto : response.body()) {
                        reels.add(dto.toDomain());
                    }
                    result.postValue(Resource.success(reels));
                } else {
                    result.postValue(Resource.error("Failed to load user reels", null));
                }
            }

            @Override
            public void onFailure(Call<List<PostDto>> call, Throwable t) {
                result.postValue(Resource.error(t.getMessage() != null ? t.getMessage() : "Network error", null));
            }
        });

        return result;
    }

    public LiveData<Resource<Post>> createReel(String caption, String videoUrl, String thumbnailUrl) {
        MutableLiveData<Resource<Post>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));

        String myId = sessionManager.getUserId();
        if (myId == null) {
            result.setValue(Resource.error("Not authenticated", null));
            return result;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("user_id", myId);
        body.put("caption", caption != null ? caption.trim() : "");
        body.put("is_reel", true);
        body.put("video_url", videoUrl);
        body.put("thumbnail_url", thumbnailUrl);

        restApi.createPost(body).enqueue(new Callback<List<PostDto>>() {
            @Override
            public void onResponse(Call<List<PostDto>> call, Response<List<PostDto>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    result.postValue(Resource.success(response.body().get(0).toDomain()));
                } else {
                    result.postValue(Resource.error("Failed to share reel", null));
                }
            }

            @Override
            public void onFailure(Call<List<PostDto>> call, Throwable t) {
                result.postValue(Resource.error(t.getMessage() != null ? t.getMessage() : "Network error", null));
            }
        });

        return result;
    }
}
