package com.insta.clone.data.repository;

import android.content.Context;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.insta.clone.data.local.SessionManager;
import com.insta.clone.data.remote.RetrofitClient;
import com.insta.clone.BuildConfig;
import com.insta.clone.R;
import com.insta.clone.data.remote.api.AuthApi;
import com.insta.clone.data.remote.api.RestApi;
import com.insta.clone.data.remote.api.StorageApi;
import com.insta.clone.data.remote.dto.AuthRequest;
import com.insta.clone.data.remote.dto.AuthResponse;
import com.insta.clone.data.remote.dto.ProfileDto;
import com.insta.clone.data.remote.dto.RpcEmailRequest;
import com.insta.clone.data.remote.dto.TokenResponse;
import com.insta.clone.util.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Real Supabase GoTrue authentication. No local or generated credentials are accepted. */
public class AuthRepository {
    private final AuthApi authApi;
    private final RestApi restApi;
    private final StorageApi storageApi;
    private final SessionManager sessionManager;
    private final Context appContext;

    public AuthRepository(Context context) {
        Context app = context.getApplicationContext();
        this.appContext = app;
        RetrofitClient client = RetrofitClient.getInstance(app);
        authApi = client.getAuthApi();
        restApi = client.getRestApi();
        storageApi = client.getStorageApi();
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

    /* ================================================================== */
    /* Registration wizard — email + 6-digit code                          */
    /* ================================================================== */

    /**
     * POST /auth/v1/otp {email, create_user:true}. Emails the 6-digit code and,
     * with create_user, creates the auth user on first use so the account exists
     * before the Username step runs (the DB trigger assigns a temporary username).
     */
    public LiveData<Resource<Void>> requestOtp(String email) {
        MutableLiveData<Resource<Void>> result = new MutableLiveData<>();
        result.postValue(Resource.loading());
        String value = email == null ? "" : email.trim();
        if (value.isEmpty()) {
            result.postValue(Resource.error(appContext.getString(R.string.auth_email_invalid), null));
            return result;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("email", value);
        body.put("create_user", true);
        authApi.requestOtp(body).enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    result.postValue(Resource.success(null));
                    return;
                }
                result.postValue(Resource.error(mapAuthError(response), null));
            }
            @Override public void onFailure(Call<Void> call, Throwable t) {
                result.postValue(Resource.error("Unable to reach Supabase: " + safe(t), null));
            }
        });
        return result;
    }

    /**
     * POST /auth/v1/verify {type:email, email, token}. On success the session is
     * persisted straight away so a process death mid-wizard can resume at the
     * Password step; the Password step may drop the persisted copy if the user
     * unchecks "Remember login info".
     */
    public LiveData<Resource<TokenResponse>> verifyOtp(String email, String token) {
        MutableLiveData<Resource<TokenResponse>> result = new MutableLiveData<>();
        result.postValue(Resource.loading());
        Map<String, Object> body = new HashMap<>();
        body.put("type", "email");
        body.put("email", email == null ? "" : email.trim());
        body.put("token", token == null ? "" : token.trim());
        authApi.verifyOtp(body).enqueue(new Callback<TokenResponse>() {
            @Override public void onResponse(Call<TokenResponse> call, Response<TokenResponse> response) {
                if (!response.isSuccessful() || response.body() == null
                        || response.body().getAccessToken() == null) {
                    result.postValue(Resource.error(mapAuthError(response)));
                    return;
                }
                TokenResponse t = response.body();
                saveSession(t.getAccessToken(), t.getRefreshToken(), t.getUserId(), t.getUsername(), t.getAvatarUrl());
                result.postValue(Resource.success(t));
            }
            @Override public void onFailure(Call<TokenResponse> call, Throwable t) {
                result.postValue(Resource.error("Unable to reach Supabase: " + safe(t)));
            }
        });
        return result;
    }

    /** GET /rest/v1/profiles?id=eq.{uid} — one raw row, used to detect a finished account. */
    public LiveData<Resource<Map<String, Object>>> fetchProfileRow(String uid) {
        MutableLiveData<Resource<Map<String, Object>>> result = new MutableLiveData<>();
        Map<String, String> q = new HashMap<>();
        q.put("id", "eq." + uid);
        q.put("select", "id,username,full_name,email,is_private,created_at");
        q.put("limit", "1");
        restApi.getProfilesRaw(q).enqueue(new Callback<List<Map<String, Object>>>() {
            @Override public void onResponse(Call<List<Map<String, Object>>> call, Response<List<Map<String, Object>>> response) {
                if (!response.isSuccessful() || response.body() == null || response.body().isEmpty()) {
                    result.postValue(Resource.error(mapAuthError(response), null));
                    return;
                }
                result.postValue(Resource.success(response.body().get(0)));
            }
            @Override public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                result.postValue(Resource.error("Unable to reach Supabase: " + safe(t), null));
            }
        });
        return result;
    }

    /** PUT /auth/v1/user {password}. */
    public LiveData<Resource<Void>> updatePassword(String password) {
        MutableLiveData<Resource<Void>> result = new MutableLiveData<>();
        result.postValue(Resource.loading());
        Map<String, Object> body = new HashMap<>();
        body.put("password", password == null ? "" : password);
        authApi.updateUser(body).enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> call, Response<Void> response) {
                result.postValue(response.isSuccessful()
                        ? Resource.success(null)
                        : Resource.error(mapAuthError(response), null));
            }
            @Override public void onFailure(Call<Void> call, Throwable t) {
                result.postValue(Resource.error("Unable to reach Supabase: " + safe(t), null));
            }
        });
        return result;
    }

    /** PATCH /rest/v1/profiles?id=eq.{uid}. A 23505 unique violation is the username race. */
    public LiveData<Resource<Void>> patchProfile(String uid, Map<String, Object> fields) {
        MutableLiveData<Resource<Void>> result = new MutableLiveData<>();
        result.postValue(Resource.loading());
        restApi.updateProfile("eq." + uid, fields).enqueue(new Callback<List<ProfileDto>>() {
            @Override public void onResponse(Call<List<ProfileDto>> call, Response<List<ProfileDto>> response) {
                if (response.isSuccessful()) {
                    result.postValue(Resource.success(null));
                    return;
                }
                String raw = errorBody(response);
                if (raw.contains("23505") || raw.toLowerCase().contains("duplicate key")) {
                    raw = appContext.getString(R.string.auth_username_just_taken);
                }
                result.postValue(Resource.error(raw, null));
            }
            @Override public void onFailure(Call<List<ProfileDto>> call, Throwable t) {
                result.postValue(Resource.error("Unable to reach Supabase: " + safe(t), null));
            }
        });
        return result;
    }

    /**
     * Uploads raw JPEG bytes and returns the public URL. The caller must HEAD-check
     * the URL before writing it to the database.
     */
    public LiveData<Resource<String>> uploadImage(String bucket, byte[] bytes, String uid, String ext) {
        MutableLiveData<Resource<String>> result = new MutableLiveData<>();
        result.postValue(Resource.loading());
        final String path = uid + "/" + UUID.randomUUID().toString().replace("-", "")
                + "." + (ext == null || ext.isEmpty() ? "jpg" : ext);
        RequestBody body = RequestBody.create(MediaType.parse("image/jpeg"), bytes);
        storageApi.uploadFile(bucket, path, body).enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> call, Response<Void> response) {
                if (!response.isSuccessful()) {
                    result.postValue(Resource.error(mapAuthError(response)));
                    return;
                }
                result.postValue(Resource.success(publicUrl(bucket, path)));
            }
            @Override public void onFailure(Call<Void> call, Throwable t) {
                result.postValue(Resource.error("Unable to reach Supabase: " + safe(t)));
            }
        });
        return result;
    }

    /** Public object URL for a stored object (bucket is public per the storage patch). */
    public String publicUrl(String bucket, String path) {
        String base = BuildConfig.SUPABASE_URL == null ? "" : BuildConfig.SUPABASE_URL.trim();
        if (!base.endsWith("/")) base += "/";
        return base + "storage/v1/object/public/" + bucket + "/" + path;
    }

    /** Clears the stored session (used by "I already have an account" and Log out). */
    public void clearSession() {
        sessionManager.clearSession();
    }

    /**
     * Applies the "Remember login info" choice. When unchecked the session is moved
     * off disk into memory, so the wizard keeps working but the next cold start
     * opens Login.
     */
    public void setRememberLogin(boolean remember) {
        if (!remember) sessionManager.keepInMemoryOnly();
    }

    /**
     * Turns a failing response into friendly copy. Known Supabase error codes are
     * mapped; anything else falls through with the real message so nothing is hidden.
     */
    private String mapAuthError(Response<?> response) {
        String raw = errorBody(response);
        String lower = raw.toLowerCase();
        if (response.code() == 429
                || lower.contains("over_email_send_rate_limit")
                || lower.contains("email rate limit")) {
            return appContext.getString(R.string.auth_email_rate_limited);
        }
        if (lower.contains("email_address_invalid")
                || (lower.contains("invalid") && lower.contains("email"))) {
            return appContext.getString(R.string.auth_email_invalid);
        }
        if (lower.contains("user_already_exists") || lower.contains("already registered")) {
            return appContext.getString(R.string.auth_already_registered_title);
        }
        if (lower.contains("otp_expired") || lower.contains("expired")) {
            return appContext.getString(R.string.auth_code_wrong);
        }
        if (response.code() == 401 || lower.contains("invalid token")) {
            return appContext.getString(R.string.auth_verify_failed);
        }
        return raw;
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
