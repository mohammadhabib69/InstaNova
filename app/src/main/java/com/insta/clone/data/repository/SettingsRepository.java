package com.insta.clone.data.repository;

import android.content.Context;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.insta.clone.data.local.SessionManager;
import com.insta.clone.data.model.Story;
import com.insta.clone.data.remote.RetrofitClient;
import com.insta.clone.data.remote.api.AuthApi;
import com.insta.clone.data.remote.api.RestApi;
import com.insta.clone.data.remote.dto.StoryDto;
import com.insta.clone.util.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * PAGE 13/14 data access: account privacy through the set_account_privacy RPC,
 * the full story archive (expired rows included), the signed-in user's account
 * details, the password change and the token revocation used by Log out.
 */
public class SettingsRepository {

    private final RestApi restApi;
    private final AuthApi authApi;
    private final SessionManager sessionManager;

    public SettingsRepository(Context context) {
        RetrofitClient client = RetrofitClient.getInstance(context.getApplicationContext());
        this.restApi = client.getRestApi();
        this.authApi = client.getAuthApi();
        this.sessionManager = SessionManager.getInstance(context.getApplicationContext());
    }

    public String getUserId() {
        return sessionManager.getUserId();
    }

    public String getUsername() {
        return sessionManager.getUsername();
    }

    /** Every story I ever posted, expired ones included (Settings > Archive). */
    public LiveData<Resource<List<Story>>> getArchive() {
        MutableLiveData<Resource<List<Story>>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));
        Map<String, String> query = new HashMap<>();
        query.put("user_id", "eq." + sessionManager.getUserId());
        query.put("order", "created_at.desc");
        query.put("limit", "200");
        restApi.getStories(query).enqueue(new Callback<List<StoryDto>>() {
            @Override
            public void onResponse(Call<List<StoryDto>> call, Response<List<StoryDto>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    result.postValue(Resource.error(AuthRepository.errorBody(response), null));
                    return;
                }
                List<Story> stories = new ArrayList<>();
                for (StoryDto dto : response.body()) {
                    if (dto != null) stories.add(dto.toDomain());
                }
                result.postValue(Resource.success(stories));
            }

            @Override
            public void onFailure(Call<List<StoryDto>> call, Throwable t) {
                result.postValue(Resource.error(t.getMessage() != null ? t.getMessage() : "Network error", null));
            }
        });
        return result;
    }

    /** Raw row for the signed-in user: username, email, created_at, is_private. */
    public LiveData<Resource<Map<String, Object>>> getMyProfileRow() {
        MutableLiveData<Resource<Map<String, Object>>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));
        Map<String, String> query = new HashMap<>();
        query.put("id", "eq." + sessionManager.getUserId());
        query.put("select", "username,email,created_at,is_private");
        query.put("limit", "1");
        restApi.getProfilesRaw(query).enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> call, Response<List<Map<String, Object>>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    result.postValue(Resource.success(response.body().get(0)));
                } else {
                    result.postValue(Resource.error(AuthRepository.errorBody(response), null));
                }
            }

            @Override
            public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                result.postValue(Resource.error(t.getMessage() != null ? t.getMessage() : "Network error", null));
            }
        });
        return result;
    }

    /** profiles.is_private for the signed-in user. */
    public LiveData<Resource<Boolean>> isPrivate() {
        MutableLiveData<Resource<Boolean>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));
        Map<String, String> query = new HashMap<>();
        query.put("id", "eq." + sessionManager.getUserId());
        query.put("select", "is_private");
        query.put("limit", "1");
        restApi.getProfilesRaw(query).enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> call, Response<List<Map<String, Object>>> response) {
                boolean value = false;
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    Object raw = response.body().get(0).get("is_private");
                    value = raw instanceof Boolean ? (Boolean) raw : Boolean.parseBoolean(String.valueOf(raw));
                }
                result.postValue(Resource.success(value));
            }

            @Override
            public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                result.postValue(Resource.error(t.getMessage() != null ? t.getMessage() : "Network error", null));
            }
        });
        return result;
    }

    /** POST /rest/v1/rpc/set_account_privacy */
    public LiveData<Resource<Void>> setPrivate(boolean isPrivate) {
        MutableLiveData<Resource<Void>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));
        Map<String, Object> body = new HashMap<>();
        body.put("p_private", isPrivate);
        restApi.setAccountPrivacy(body).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                result.postValue(response.isSuccessful()
                        ? Resource.success(null)
                        : Resource.error(AuthRepository.errorBody(response), null));
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                result.postValue(Resource.error(t.getMessage() != null ? t.getMessage() : "Network error", null));
            }
        });
        return result;
    }

    /** PUT /auth/v1/user with the new password. */
    public LiveData<Resource<Void>> updatePassword(String newPassword) {
        MutableLiveData<Resource<Void>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));
        Map<String, Object> body = new HashMap<>();
        body.put("password", newPassword);
        authApi.updateUser(body).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                result.postValue(response.isSuccessful()
                        ? Resource.success(null)
                        : Resource.error(AuthRepository.errorBody(response), null));
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                result.postValue(Resource.error(t.getMessage() != null ? t.getMessage() : "Network error", null));
            }
        });
        return result;
    }

    /** Revokes the refresh token server-side; the caller clears the local session. */
    public LiveData<Resource<Void>> revokeSession() {
        MutableLiveData<Resource<Void>> result = new MutableLiveData<>();
        authApi.logout().enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                result.postValue(Resource.success(null));
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                result.postValue(Resource.error(t.getMessage() != null ? t.getMessage() : "Network error", null));
            }
        });
        return result;
    }
}
