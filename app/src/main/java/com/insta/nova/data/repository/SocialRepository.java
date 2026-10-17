package com.insta.nova.data.repository;

import android.content.Context;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.model.Profile;
import com.insta.nova.data.remote.RetrofitClient;
import com.insta.nova.data.remote.api.RestApi;
import com.insta.nova.util.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Read-only social graph queries that are already expressible with PostgREST
 * resource embedding, so no schema change is required:
 *
 *   likes   -> profiles via the likes.user_id foreign key
 *   follows -> profiles via follows.follower_id / follows.following_id
 *
 * Every method returns Profile objects so the existing user-row UI can render
 * them. Usernames, avatar urls and full names all come from the embedded
 * profiles resource.
 */
public class SocialRepository {

    private final RestApi restApi;
    private final SessionManager sessionManager;

    public SocialRepository(Context context) {
        RetrofitClient client = RetrofitClient.getInstance(context.getApplicationContext());
        this.restApi = client.getRestApi();
        this.sessionManager = SessionManager.getInstance(context.getApplicationContext());
    }

    public String getCurrentUserId() {
        return sessionManager.getUserId();
    }

    /** Users who liked the given post, newest first. */
    public LiveData<Resource<List<Profile>>> getLikers(String postId) {
        Map<String, String> query = new HashMap<>();
        query.put("post_id", "eq." + postId);
        query.put("select", "user_id,profiles(username,avatar_url,full_name,is_verified)");
        query.put("order", "created_at.desc");
        query.put("limit", "200");
        return fetchProfiles(restApi.getLikes(query));
    }

    /** Users who follow the given user. */
    public LiveData<Resource<List<Profile>>> getFollowers(String userId) {
        Map<String, String> query = new HashMap<>();
        query.put("following_id", "eq." + userId);
        query.put("select", "follower_id,profiles!follows_follower_id_fkey(username,avatar_url,full_name,is_verified)");
        query.put("limit", "200");
        return fetchProfiles(restApi.getFollows(query));
    }

    /** Users the given user follows. */
    public LiveData<Resource<List<Profile>>> getFollowing(String userId) {
        Map<String, String> query = new HashMap<>();
        query.put("follower_id", "eq." + userId);
        query.put("select", "following_id,profiles!follows_following_id_fkey(username,avatar_url,full_name,is_verified)");
        query.put("limit", "200");
        return fetchProfiles(restApi.getFollows(query));
    }

    /** Users who watched the given story (story_views joined to profiles). */
    public LiveData<Resource<List<Profile>>> getStoryViewers(String storyId) {
        Map<String, String> query = new HashMap<>();
        query.put("story_id", "eq." + storyId);
        query.put("select", "viewer_id,profiles(username,avatar_url,full_name,is_verified)");
        query.put("order", "created_at.desc");
        query.put("limit", "200");
        return fetchProfiles(restApi.getStoryViews(query));
    }

    /**
     * Parses PostgREST resource-embedding payloads of the form
     * [ { "user_id": "...", "profiles": { ... } }, ... ]
     */
    private LiveData<Resource<List<Profile>>> fetchProfiles(Call<List<Map<String, Object>>> call) {
        MutableLiveData<Resource<List<Profile>>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));
        call.enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> c, Response<List<Map<String, Object>>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    result.postValue(Resource.error(AuthRepository.errorBody(response), null));
                    return;
                }
                List<Profile> profiles = new ArrayList<>();
                for (Map<String, Object> row : response.body()) {
                    if (row == null) continue;
                    Object embedded = row.get("profiles");
                    if (!(embedded instanceof Map)) continue;
                    @SuppressWarnings("unchecked")
                    Map<String, Object> p = (Map<String, Object>) embedded;
                    String id = firstNonNull(asString(row.get("user_id")), asString(row.get("follower_id")),
                            asString(row.get("following_id")));
                    String username = asString(p.get("username"));
                    if (username == null || username.isEmpty()) continue;
                    String avatar = asString(p.get("avatar_url"));
                    String fullName = asString(p.get("full_name"));
                    boolean verified = Boolean.TRUE.equals(p.get("is_verified"));
                    Profile profile = new Profile();
                    profile.setId(id);
                    profile.setUsername(username);
                    profile.setFullName(fullName);
                    profile.setAvatarUrl(avatar);
                    profile.setVerified(verified);
                    profiles.add(profile);
                }
                result.postValue(Resource.success(profiles));
            }

            @Override
            public void onFailure(Call<List<Map<String, Object>>> c, Throwable t) {
                result.postValue(Resource.error(t.getMessage() != null ? t.getMessage() : "Network error", null));
            }
        });
        return result;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String firstNonNull(String... values) {
        for (String v : values) {
            if (v != null && !v.isEmpty() && !"null".equals(v)) return v;
        }
        return null;
    }
}
