package com.insta.clone.data.repository;

import android.content.Context;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.insta.clone.data.local.SessionManager;
import com.insta.clone.data.remote.RetrofitClient;
import com.insta.clone.data.remote.api.AuthApi;
import com.insta.clone.data.remote.api.RestApi;
import com.insta.clone.data.remote.dto.AuthRequest;
import com.insta.clone.data.remote.dto.AuthResponse;
import com.insta.clone.data.remote.dto.RpcEmailRequest;
import com.insta.clone.data.remote.dto.TokenResponse;
import com.insta.clone.util.Resource;
import java.util.HashMap;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Real Supabase GoTrue authentication. No local or generated credentials are accepted. */
public class AuthRepository {
    private final AuthApi authApi;
    private final RestApi restApi;
    private final SessionManager sessionManager;

    public AuthRepository(Context context) {
        Context app = context.getApplicationContext();
        RetrofitClient client = RetrofitClient.getInstance(app);
        authApi = client.getAuthApi();
        restApi = client.getRestApi();
        sessionManager = SessionManager.getInstance(app);
    }

    public LiveData<Resource<TokenResponse>> login(String identifier, String password) {
        MutableLiveData<Resource<TokenResponse>> result = new MutableLiveData<>();
        result.postValue(Resource.loading());
        String value = identifier == null ? "" : identifier.trim();
        if (value.isEmpty() || password == null || password.isEmpty()) {
            result.postValue(Resource.error("Please enter your username or email and password."));
            return result;
        }
        if (value.contains("@")) signInEmail(value, password, result);
        else restApi.getEmailByUsername(new RpcEmailRequest(value)).enqueue(new Callback<JsonElement>() {
            @Override public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                String email = response.body() == null || response.body().isJsonNull() ? "" : response.body().getAsString();
                if (!response.isSuccessful() || email.trim().isEmpty()) {
                    result.postValue(Resource.error("The username you entered doesn't belong to an account. Please check your username and try again."));
                    return;
                }
                signInEmail(email.trim(), password, result);
            }
            @Override public void onFailure(Call<JsonElement> call, Throwable t) { result.postValue(Resource.error("Unable to reach Supabase: " + safe(t))); }
        });
        return result;
    }

    private void signInEmail(String email, String password, MutableLiveData<Resource<TokenResponse>> result) {
        authApi.signInWithPassword(new AuthRequest(email, password)).enqueue(new Callback<TokenResponse>() {
            @Override public void onResponse(Call<TokenResponse> call, Response<TokenResponse> response) {
                if (!response.isSuccessful() || response.body() == null || response.body().getAccessToken() == null) {
                    String body = errorBody(response).toLowerCase();
                    result.postValue(Resource.error(body.contains("invalid") ? "Sorry, your password was incorrect. Please double-check your password." : errorBody(response)));
                    return;
                }
                TokenResponse token = response.body();
                saveSession(token.getAccessToken(), token.getRefreshToken(), token.getUserId(), token.getUsername(), token.getAvatarUrl());
                result.postValue(Resource.success(token));
            }
            @Override public void onFailure(Call<TokenResponse> call, Throwable t) { result.postValue(Resource.error("Unable to reach Supabase: " + safe(t))); }
        });
    }

    public LiveData<Resource<AuthResponse>> signUp(String email, String password, String username, String fullName, String birthday) {
        MutableLiveData<Resource<AuthResponse>> result = new MutableLiveData<>();
        result.postValue(Resource.loading());
        Map<String, Object> data = new HashMap<>();
        data.put("username", username == null ? "" : username.trim().toLowerCase());
        data.put("full_name", fullName == null ? "" : fullName.trim());
        authApi.signUp(new AuthRequest(email == null ? "" : email.trim(), password, data)).enqueue(new Callback<AuthResponse>() {
            @Override public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    String raw = errorBody(response);
                    String normalized = raw.toLowerCase();
                    if (normalized.contains("over_email_send_rate_limit") || normalized.contains("email rate limit") || response.code() == 429) {
                        raw = "Supabase is temporarily limiting new email sign-ups. Please wait a little while and try again.";
                    } else if (normalized.contains("user_already_exists") || normalized.contains("already registered") || normalized.contains("already exists")) {
                        raw = "That email is already registered. Please log in instead.";
                    }
                    result.postValue(Resource.error(raw));
                    return;
                }
                AuthResponse auth = response.body();
                if (auth.hasSession()) saveSession(auth.getAccessToken(), auth.getRefreshToken(), auth.getUserId(), auth.getUsername(), null);
                result.postValue(Resource.success(auth));
            }
            @Override public void onFailure(Call<AuthResponse> call, Throwable t) { result.postValue(Resource.error("Unable to reach Supabase: " + safe(t))); }
        });
        return result;
    }

    public LiveData<Resource<Void>> recoverPassword(String email) {
        MutableLiveData<Resource<Void>> result = new MutableLiveData<>();
        result.postValue(Resource.loading());
        Map<String, String> body = new HashMap<>(); body.put("email", email == null ? "" : email.trim());
        authApi.recoverPassword(body).enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> call, Response<Void> response) { result.postValue(response.isSuccessful() ? Resource.success(null) : Resource.error(errorBody(response), null)); }
            @Override public void onFailure(Call<Void> call, Throwable t) { result.postValue(Resource.error(safe(t), null)); }
        });
        return result;
    }

    private void saveSession(String access, String refresh, String id, String username, String avatar) {
        if (access != null && !access.isEmpty() && id != null && !id.isEmpty()) sessionManager.saveSession(access, refresh, id, username, avatar);
    }

    static String errorBody(Response<?> response) {
        try {
            String raw = response.errorBody() == null ? "" : response.errorBody().string();
            if (raw.isEmpty()) return "Supabase request failed (HTTP " + response.code() + ").";
            JsonObject object = JsonParser.parseString(raw).getAsJsonObject();
            if (object.has("message")) return object.get("message").getAsString();
            if (object.has("error_description")) return object.get("error_description").getAsString();
            return raw;
        } catch (Exception e) { return "Supabase request failed (HTTP " + response.code() + ")."; }
    }

    private static String safe(Throwable t) { return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage(); }
}
