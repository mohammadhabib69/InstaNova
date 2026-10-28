package com.insta.nova.data.repository;

import android.content.Context;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.remote.InteractionConflict;
import com.insta.nova.data.remote.RetrofitClient;
import com.insta.nova.data.remote.api.RestApi;
import com.google.gson.JsonElement;
import com.insta.nova.util.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Repository for follow/unfollow operations and relationship status tracking backed by Supabase.
 */
public class FollowRepository {

    private final RestApi restApi;
    private final SessionManager sessionManager;

    public FollowRepository(Context context) {
        RetrofitClient client = RetrofitClient.getInstance(context);
        this.restApi = client.getRestApi();
        this.sessionManager = SessionManager.getInstance(context);
    }

    public LiveData<Resource<Boolean>> toggleFollow(String targetUserId, boolean follow) {
        MutableLiveData<Resource<Boolean>> liveData = new MutableLiveData<>();
        liveData.setValue(Resource.loading(null));

        String myId = sessionManager.getUserId();
        // "" means the identity gate never resolved a profiles.id; sending it would
        // produce follower_id=eq. and a malformed relationship request.
        if (myId == null || myId.trim().isEmpty()) {
            liveData.setValue(Resource.error("Not authenticated", null));
            return liveData;
        }

        if (follow) {
            Map<String, Object> body = new HashMap<>();
            body.put("follower_id", myId);
            body.put("following_id", targetUserId);

            restApi.followUser(InteractionConflict.FOLLOW, body).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    if (response.isSuccessful()) {
                        liveData.postValue(Resource.success(true));
                    } else {
                        liveData.postValue(Resource.error("Failed to follow user", false));
                    }
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    liveData.postValue(Resource.error(t.getMessage() != null ? t.getMessage() : "Network error", false));
                }
            });
        } else {
            restApi.unfollowUser("eq." + myId, "eq." + targetUserId).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    if (response.isSuccessful()) {
                        liveData.postValue(Resource.success(false));
                    } else {
                        liveData.postValue(Resource.error("Failed to unfollow user", true));
                    }
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    liveData.postValue(Resource.error(t.getMessage() != null ? t.getMessage() : "Network error", true));
                }
            });
        }

        return liveData;
    }

    /**
     * Private-aware follow. Returns "following", "requested" (private target) or "none".
     *
     * <p>Restored after an accidental overwrite: the signature and return contract were
     * recovered from the last compiled class plus `RestApi.followUserRpc`'s documented
     * behaviour ("following" / "requested" / "self"), and from the call site in
     * {@code PeopleListActivity}. The failure states mirror the compiled bytecode:
     * a failed unfollow reports the previous state "following", a failed follow "none".
     */
    public LiveData<Resource<String>> followWithState(String targetUserId, boolean follow) {
        MutableLiveData<Resource<String>> liveData = new MutableLiveData<>();
        liveData.setValue(Resource.loading(null));

        String myId = sessionManager.getUserId();
        if (myId == null || myId.trim().isEmpty()) {
            liveData.setValue(Resource.error("Not authenticated", "none"));
            return liveData;
        }

        if (!follow) {
            restApi.unfollowUser("eq." + myId, "eq." + targetUserId).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    if (response.isSuccessful()) {
                        liveData.postValue(Resource.success("none"));
                    } else {
                        liveData.postValue(Resource.error("Failed to unfollow user", "following"));
                    }
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    liveData.postValue(Resource.error("Failed to unfollow user", "following"));
                }
            });
            return liveData;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("p_target", targetUserId);
        restApi.followUserRpc(body).enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    liveData.postValue(Resource.error("Failed to follow user", "none"));
                    return;
                }
                JsonElement element = response.body();
                String state = element.isJsonNull() ? "none" : element.getAsString();
                liveData.postValue(Resource.success(state));
            }

            @Override
            public void onFailure(Call<JsonElement> call, Throwable t) {
                liveData.postValue(Resource.error("Failed to follow user", "none"));
            }
        });
        return liveData;
    }

    public LiveData<Resource<Boolean>> isFollowing(String targetUserId) {
        MutableLiveData<Resource<Boolean>> liveData = new MutableLiveData<>();
        liveData.setValue(Resource.loading(null));

        String myId = sessionManager.getUserId();
        if (myId == null) {
            liveData.setValue(Resource.success(false));
            return liveData;
        }

        Map<String, String> query = new HashMap<>();
        query.put("follower_id", "eq." + myId);
        query.put("following_id", "eq." + targetUserId);
        query.put("select", "following_id");

        restApi.getFollows(query).enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> call, Response<List<Map<String, Object>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    liveData.postValue(Resource.success(!response.body().isEmpty()));
                } else {
                    liveData.postValue(Resource.success(false));
                }
            }

            @Override
            public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                liveData.postValue(Resource.error(t.getMessage() != null ? t.getMessage() : "Network error", false));
            }
        });

        return liveData;
    }
}
