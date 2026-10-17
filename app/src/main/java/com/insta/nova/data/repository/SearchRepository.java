package com.insta.nova.data.repository;

import android.content.Context;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.model.Post;
import com.insta.nova.data.model.Profile;
import com.insta.nova.data.remote.ProfileColumns;
import com.insta.nova.data.remote.RetrofitClient;
import com.insta.nova.data.remote.api.RestApi;
import com.insta.nova.data.remote.dto.PostDto;
import com.insta.nova.data.remote.dto.ProfileDto;
import com.insta.nova.data.remote.dto.RpcSuggestedRequest;
import com.insta.nova.util.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Repository for Search (accounts, tags), Explore grid feed, and Suggested Users.
 */
public class SearchRepository {

    private final RestApi restApi;
    private final SessionManager sessionManager;

    public SearchRepository(Context context) {
        RetrofitClient client = RetrofitClient.getInstance(context);
        this.restApi = client.getRestApi();
        this.sessionManager = SessionManager.getInstance(context);
    }

    public LiveData<Resource<List<Profile>>> searchUsers(String query) {
        MutableLiveData<Resource<List<Profile>>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));

        String q = query.trim().replace("%", "");
        Map<String, String> params = new HashMap<>();
        params.put("or", "(username.ilike.*" + q + "*,full_name.ilike.*" + q + "*)");
        params.put("select", ProfileColumns.SEARCH);
        params.put("limit", "30");

        restApi.getProfiles(params).enqueue(new Callback<List<ProfileDto>>() {
            @Override
            public void onResponse(Call<List<ProfileDto>> call, Response<List<ProfileDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Profile> profiles = new ArrayList<>();
                    for (ProfileDto dto : response.body()) {
                        profiles.add(dto.toDomain());
                    }
                    result.postValue(Resource.success(profiles));
                } else {
                    result.postValue(Resource.error("Search failed", null));
                }
            }

            @Override
            public void onFailure(Call<List<ProfileDto>> call, Throwable t) {
                result.postValue(Resource.error(t.getMessage() != null ? t.getMessage() : "Network error", null));
            }
        });

        return result;
    }

    public LiveData<Resource<List<Post>>> searchTags(String tag) {
        MutableLiveData<Resource<List<Post>>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));

        String cleanTag = tag.trim().replace("#", "");
        Map<String, String> params = new HashMap<>();
        params.put("caption", "ilike.*#" + cleanTag + "*");
        params.put("order", "created_at.desc");
        params.put("limit", "30");

        restApi.getPosts(params).enqueue(new Callback<List<PostDto>>() {
            @Override
            public void onResponse(Call<List<PostDto>> call, Response<List<PostDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Post> posts = new ArrayList<>();
                    for (PostDto dto : response.body()) {
                        posts.add(dto.toDomain());
                    }
                    result.postValue(Resource.success(posts));
                } else {
                    result.postValue(Resource.error("No posts found for tag", null));
                }
            }

            @Override
            public void onFailure(Call<List<PostDto>> call, Throwable t) {
                result.postValue(Resource.error(t.getMessage() != null ? t.getMessage() : "Network error", null));
            }
        });

        return result;
    }

    /**
     * Caption search over {@code v_posts} (PostgREST {@code ilike}). The existing tag
     * search only matches {@code #hashtags}, so posts are filtered here instead.
     */
    public LiveData<Resource<List<Post>>> searchPostsByCaption(String query) {
        MutableLiveData<Resource<List<Post>>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));

        String needle = query == null ? "" : query.trim().replace("*", "");
        if (needle.isEmpty()) {
            result.postValue(Resource.success(new ArrayList<>()));
            return result;
        }

        Map<String, String> params = new HashMap<>();
        params.put("caption", "ilike.*" + needle + "*");
        params.put("order", "created_at.desc");
        params.put("limit", "30");

        restApi.getPosts(params).enqueue(new Callback<List<PostDto>>() {
            @Override
            public void onResponse(Call<List<PostDto>> call, Response<List<PostDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Post> posts = new ArrayList<>();
                    for (PostDto dto : response.body()) {
                        posts.add(dto.toDomain());
                    }
                    result.postValue(Resource.success(posts));
                } else {
                    result.postValue(Resource.success(new ArrayList<>()));
                }
            }

            @Override
            public void onFailure(Call<List<PostDto>> call, Throwable t) {
                // Offline: the local caption filter still runs, so this is not an error.
                result.postValue(Resource.success(new ArrayList<>()));
            }
        });

        return result;
    }

    public LiveData<Resource<List<Post>>> getExplorePosts(int limit, int offset) {
        MutableLiveData<Resource<List<Post>>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));

        String myId = sessionManager.getUserId();
        Map<String, String> params = new HashMap<>();
        params.put("is_reel", "eq.false");
        if (myId != null) {
            params.put("user_id", "neq." + myId);
        }
        params.put("order", "likes_count.desc,created_at.desc");
        params.put("limit", String.valueOf(limit));
        params.put("offset", String.valueOf(offset));

        restApi.getPosts(params).enqueue(new Callback<List<PostDto>>() {
            @Override
            public void onResponse(Call<List<PostDto>> call, Response<List<PostDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Post> posts = new ArrayList<>();
                    for (PostDto dto : response.body()) {
                        posts.add(dto.toDomain());
                    }
                    result.postValue(Resource.success(posts));
                } else {
                    result.postValue(Resource.error("Failed to load explore feed", null));
                }
            }

            @Override
            public void onFailure(Call<List<PostDto>> call, Throwable t) {
                result.postValue(Resource.error(t.getMessage() != null ? t.getMessage() : "Network error", null));
            }
        });

        return result;
    }

    public LiveData<Resource<List<Profile>>> getSuggestedUsers(int limit) {
        MutableLiveData<Resource<List<Profile>>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));

        Map<String, String> params = new HashMap<>();
        String myId = sessionManager.getUserId();
        if (myId != null) {
            params.put("id", "neq." + myId);
        }
        params.put("select", ProfileColumns.SEARCH);
        params.put("order", "followers_count.desc,created_at.desc");
        params.put("limit", String.valueOf(limit));

        restApi.getProfiles(params).enqueue(new Callback<List<ProfileDto>>() {
            @Override
            public void onResponse(Call<List<ProfileDto>> call, Response<List<ProfileDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Profile> profiles = new ArrayList<>();
                    for (ProfileDto dto : response.body()) {
                        profiles.add(dto.toDomain());
                    }
                    result.postValue(Resource.success(profiles));
                } else {
                    result.postValue(Resource.error("Failed to load suggestions", null));
                }
            }

            @Override
            public void onFailure(Call<List<ProfileDto>> call, Throwable t) {
                result.postValue(Resource.error(t.getMessage() != null ? t.getMessage() : "Network error", null));
            }
        });

        return result;
    }
}
