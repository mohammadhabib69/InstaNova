package com.insta.clone.data.repository;

import android.content.Context;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.insta.clone.data.local.SessionManager;
import com.insta.clone.data.model.Profile;
import com.insta.clone.data.remote.RetrofitClient;
import com.insta.clone.data.remote.api.RestApi;
import com.insta.clone.data.remote.dto.ProfileDto;
import com.insta.clone.util.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Profile data always comes from PostgREST; there are no placeholder profiles. */
public class ProfileRepository {
    private final RestApi restApi;
    private final SessionManager sessionManager;

    public ProfileRepository(Context context) {
        Context app = context.getApplicationContext();
        restApi = RetrofitClient.getInstance(app).getRestApi();
        sessionManager = SessionManager.getInstance(app);
    }

    public LiveData<Resource<Boolean>> checkUsernameAvailability(String username) {
        MutableLiveData<Resource<Boolean>> result = new MutableLiveData<>();
        String value = username == null ? "" : username.trim().toLowerCase();
        if (value.length() < 3 || value.length() > 30 || !value.matches("[a-z0-9._]+")) { result.postValue(Resource.success(false)); return result; }
        restApi.isUsernameAvailable(new com.insta.clone.data.remote.dto.RpcEmailRequest(value)).enqueue(new Callback<Boolean>() {
            @Override public void onResponse(Call<Boolean> call, Response<Boolean> response) {
                result.postValue(response.isSuccessful() && response.body() != null ? Resource.success(response.body()) : Resource.error(AuthRepository.errorBody(response)));
            }
            @Override public void onFailure(Call<Boolean> call, Throwable t) { result.postValue(Resource.error(t.getMessage())); }
        });
        return result;
    }

    public LiveData<Resource<Profile>> getProfile(String userId) {
        MutableLiveData<Resource<Profile>> result = new MutableLiveData<>(); result.postValue(Resource.loading());
        String id = userId != null ? userId : sessionManager.getUserId();
        if (id == null || id.isEmpty()) { result.postValue(Resource.error("No authenticated user.")); return result; }
        Map<String, String> query = new HashMap<>(); query.put("id", "eq." + id);
        loadProfile(id, result, query, false);
        return result;
    }

    private void loadProfile(String id, MutableLiveData<Resource<Profile>> result, Map<String, String> query, boolean retried) {
        restApi.getProfiles(query).enqueue(new Callback<List<ProfileDto>>() {
            @Override public void onResponse(Call<List<ProfileDto>> call, Response<List<ProfileDto>> response) {
                if (!response.isSuccessful() || response.body() == null) { result.postValue(Resource.error(AuthRepository.errorBody(response))); return; }
                if (!response.body().isEmpty()) { result.postValue(Resource.success(response.body().get(0).toDomain())); return; }
                if (retried) { result.postValue(Resource.error("Profile row was not returned by Supabase.")); return; }
                Map<String, Object> body = new HashMap<>(); body.put("id", id); body.put("username", sessionManager.getUsername()); body.put("full_name", "");
                restApi.createProfile(body).enqueue(new Callback<List<ProfileDto>>() {
                    @Override public void onResponse(Call<List<ProfileDto>> call, Response<List<ProfileDto>> response) {
                        if (!response.isSuccessful()) { result.postValue(Resource.error(AuthRepository.errorBody(response))); return; }
                        loadProfile(id, result, query, true);
                    }
                    @Override public void onFailure(Call<List<ProfileDto>> call, Throwable t) { result.postValue(Resource.error(t.getMessage())); }
                });
            }
            @Override public void onFailure(Call<List<ProfileDto>> call, Throwable t) { result.postValue(Resource.error(t.getMessage())); }
        });
    }
}
