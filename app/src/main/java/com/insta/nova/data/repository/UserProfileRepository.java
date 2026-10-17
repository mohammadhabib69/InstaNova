package com.insta.nova.data.repository;

import android.content.Context;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.remote.RetrofitClient;
import com.insta.nova.data.remote.api.RestApi;
import com.insta.nova.data.remote.dto.PostDto;
import com.insta.nova.data.remote.dto.ProfileDto;
import com.insta.nova.data.remote.dto.StoryDto;
import com.insta.nova.util.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** PAGE 18 — data access for another user's profile. */
public class UserProfileRepository {

    public static final int PAGE_SIZE = 30;

    /** {@code followed_by_preview} result. */
    public static class FollowedBy {
        public int total;
        public final List<String> usernames = new ArrayList<>();
        public final List<String> avatars = new ArrayList<>();
    }

    private final RestApi api;
    private final SessionManager session;

    public UserProfileRepository(Context context) {
        api = RetrofitClient.getInstance(context).getRestApi();
        session = SessionManager.getInstance(context);
    }

    public String me() { return session.getUserId(); }

    public LiveData<Resource<ProfileDto>> getProfile(String userId) {
        MutableLiveData<Resource<ProfileDto>> result = new MutableLiveData<>();
        Map<String, String> q = new HashMap<>();
        q.put("id", "eq." + userId);
        q.put("select", "id,username,full_name,bio,avatar_url,website,is_verified,"
                + "posts_count,followers_count,following_count");
        q.put("limit", "1");
        api.getProfiles(q).enqueue(new Callback<List<ProfileDto>>() {
            @Override public void onResponse(Call<List<ProfileDto>> c, Response<List<ProfileDto>> r) {
                if (r.isSuccessful() && r.body() != null && !r.body().isEmpty()) {
                    result.postValue(Resource.success(r.body().get(0)));
                } else if (r.isSuccessful()) {
                    result.postValue(Resource.success(null));
                } else {
                    result.postValue(Resource.error("HTTP " + r.code()));
                }
            }
            @Override public void onFailure(Call<List<ProfileDto>> c, Throwable t) {
                result.postValue(Resource.error(t.getMessage() == null ? "Network error" : t.getMessage()));
            }
        });
        return result;
    }

    public LiveData<Resource<ProfileDto>> getProfileByUsername(String username) {
        MutableLiveData<Resource<ProfileDto>> result = new MutableLiveData<>();
        Map<String, String> q = new HashMap<>();
        q.put("username", "eq." + username);
        q.put("select", "id,username,full_name,avatar_url,is_verified,posts_count,followers_count,following_count");
        q.put("limit", "1");
        api.getProfiles(q).enqueue(new Callback<List<ProfileDto>>() {
            @Override public void onResponse(Call<List<ProfileDto>> c, Response<List<ProfileDto>> r) {
                if (r.isSuccessful() && r.body() != null && !r.body().isEmpty()) {
                    result.postValue(Resource.success(r.body().get(0)));
                } else if (r.isSuccessful()) {
                    result.postValue(Resource.success(null));
                } else {
                    result.postValue(Resource.error("HTTP " + r.code()));
                }
            }
            @Override public void onFailure(Call<List<ProfileDto>> c, Throwable t) {
                result.postValue(Resource.error("Network error"));
            }
        });
        return result;
    }

    /** [0] = I follow them, [1] = they follow me. */
    public LiveData<Resource<boolean[]>> getRelationship(String userId) {
        MutableLiveData<Resource<boolean[]>> result = new MutableLiveData<>();
        String me = me();
        if (me == null || userId == null) {
            result.setValue(Resource.error("Not authenticated"));
            return result;
        }
        Map<String, String> q = new HashMap<>();
        q.put("or", "(and(follower_id.eq." + me + ",following_id.eq." + userId + "),"
                + "and(follower_id.eq." + userId + ",following_id.eq." + me + "))");
        q.put("select", "follower_id,following_id");
        api.getFollows(q).enqueue(new Callback<List<Map<String, Object>>>() {
            @Override public void onResponse(Call<List<Map<String, Object>>> c,
                                             Response<List<Map<String, Object>>> r) {
                if (!r.isSuccessful() || r.body() == null) {
                    result.postValue(Resource.error("HTTP " + r.code()));
                    return;
                }
                boolean[] state = new boolean[2];
                for (Map<String, Object> row : r.body()) {
                    Object f = row.get("follower_id");
                    Object t = row.get("following_id");
                    if (me.equals(f) && userId.equals(t)) state[0] = true;
                    if (userId.equals(f) && me.equals(t)) state[1] = true;
                }
                result.postValue(Resource.success(state));
            }
            @Override public void onFailure(Call<List<Map<String, Object>>> c, Throwable t) {
                result.postValue(Resource.error("Network error"));
            }
        });
        return result;
    }

    public LiveData<Resource<Boolean>> follow(String userId) {
        MutableLiveData<Resource<Boolean>> result = new MutableLiveData<>();
        String me = me();
        if (me == null) { result.setValue(Resource.error("Not authenticated")); return result; }
        Map<String, Object> body = new HashMap<>();
        body.put("follower_id", me);
        body.put("following_id", userId);
        api.followUser(com.insta.nova.data.remote.InteractionConflict.FOLLOW, body).enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> c, Response<Void> r) {
                result.postValue(r.isSuccessful() ? Resource.success(true) : Resource.error("follow failed"));
            }
            @Override public void onFailure(Call<Void> c, Throwable t) {
                result.postValue(Resource.error("Network error"));
            }
        });
        return result;
    }

    public LiveData<Resource<Boolean>> unfollow(String userId) {
        MutableLiveData<Resource<Boolean>> result = new MutableLiveData<>();
        String me = me();
        if (me == null) { result.setValue(Resource.error("Not authenticated")); return result; }
        api.unfollowUser("eq." + me, "eq." + userId).enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> c, Response<Void> r) {
                result.postValue(r.isSuccessful() ? Resource.success(true) : Resource.error("unfollow failed"));
            }
            @Override public void onFailure(Call<Void> c, Throwable t) {
                result.postValue(Resource.error("Network error"));
            }
        });
        return result;
    }

    public LiveData<Resource<List<StoryDto>>> getStories(String userId) {
        MutableLiveData<Resource<List<StoryDto>>> result = new MutableLiveData<>();
        Map<String, String> q = new HashMap<>();
        q.put("user_id", "eq." + userId);
        q.put("expires_at", "gt." + com.insta.nova.util.TimeUtils.toSupabaseTimestamp(System.currentTimeMillis()));
        q.put("order", "created_at.asc");
        api.getStories(q).enqueue(new Callback<List<StoryDto>>() {
            @Override public void onResponse(Call<List<StoryDto>> c, Response<List<StoryDto>> r) {
                result.postValue(r.isSuccessful() && r.body() != null
                        ? Resource.success(r.body()) : Resource.error("stories failed"));
            }
            @Override public void onFailure(Call<List<StoryDto>> c, Throwable t) {
                result.postValue(Resource.error("Network error"));
            }
        });
        return result;
    }

    public LiveData<Resource<List<PostDto>>> getPosts(String userId, int offset) {
        MutableLiveData<Resource<List<PostDto>>> result = new MutableLiveData<>();
        Map<String, String> q = new HashMap<>();
        q.put("user_id", "eq." + userId);
        q.put("order", "created_at.desc");
        q.put("limit", String.valueOf(PAGE_SIZE));
        q.put("offset", String.valueOf(offset));
        api.getPosts(q).enqueue(new Callback<List<PostDto>>() {
            @Override public void onResponse(Call<List<PostDto>> c, Response<List<PostDto>> r) {
                result.postValue(r.isSuccessful() && r.body() != null
                        ? Resource.success(r.body()) : Resource.error("posts failed"));
            }
            @Override public void onFailure(Call<List<PostDto>> c, Throwable t) {
                result.postValue(Resource.error("Network error"));
            }
        });
        return result;
    }

    public LiveData<Resource<FollowedBy>> getFollowedBy(String userId) {
        MutableLiveData<Resource<FollowedBy>> result = new MutableLiveData<>();
        Map<String, Object> body = new HashMap<>();
        body.put("p_user", userId);
        api.followedByPreview(body).enqueue(new Callback<JsonElement>() {
            @Override public void onResponse(Call<JsonElement> c, Response<JsonElement> r) {
                if (!r.isSuccessful() || r.body() == null) {
                    result.postValue(Resource.error("preview failed"));
                    return;
                }
                result.postValue(Resource.success(parseFollowedBy(r.body())));
            }
            @Override public void onFailure(Call<JsonElement> c, Throwable t) {
                result.postValue(Resource.error("Network error"));
            }
        });
        return result;
    }

    /** Accepts either a single object or a one-element array. */
    static FollowedBy parseFollowedBy(JsonElement element) {
        FollowedBy fb = new FollowedBy();
        JsonElement row = element;
        if (row.isJsonArray()) {
            JsonArray arr = row.getAsJsonArray();
            if (arr.size() == 0) return fb;
            row = arr.get(0);
        }
        if (!row.isJsonObject()) return fb;
        JsonObject obj = row.getAsJsonObject();
        if (obj.has("total") && !obj.get("total").isJsonNull()) {
            try { fb.total = obj.get("total").getAsInt(); } catch (Exception ignored) {}
        }
        if (obj.has("users") && obj.get("users").isJsonArray()) {
            for (JsonElement u : obj.getAsJsonArray("users")) {
                if (!u.isJsonObject()) continue;
                JsonObject o = u.getAsJsonObject();
                fb.usernames.add(o.has("username") && !o.get("username").isJsonNull()
                        ? o.get("username").getAsString() : "");
                fb.avatars.add(o.has("avatar_url") && !o.get("avatar_url").isJsonNull()
                        ? o.get("avatar_url").getAsString() : "");
            }
        }
        if (fb.total == 0) fb.total = fb.usernames.size();
        return fb;
    }

    public LiveData<Resource<List<ProfileDto>>> getSuggestions(int limit) {
        MutableLiveData<Resource<List<ProfileDto>>> result = new MutableLiveData<>();
        Map<String, Object> body = new HashMap<>();
        body.put("p_limit", limit);
        api.getSuggestedUsersRaw(body).enqueue(new Callback<List<ProfileDto>>() {
            @Override public void onResponse(Call<List<ProfileDto>> c, Response<List<ProfileDto>> r) {
                result.postValue(r.isSuccessful() && r.body() != null
                        ? Resource.success(r.body()) : Resource.error("suggestions failed"));
            }
            @Override public void onFailure(Call<List<ProfileDto>> c, Throwable t) {
                result.postValue(Resource.error("Network error"));
            }
        });
        return result;
    }
}
