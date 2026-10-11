package com.insta.clone.data.remote;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.gson.Gson;
import com.insta.clone.BuildConfig;
import com.insta.clone.data.local.SessionManager;
import com.insta.clone.data.remote.dto.TokenResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import okhttp3.Authenticator;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.Route;

/**
 * OkHttp Authenticator that automatically executes token refresh on 401 responses.
 */
public class TokenAuthenticator implements Authenticator {

    private static final String TAG = "InstaApi";
    private final Context context;
    private final Gson gson = new Gson();

    public TokenAuthenticator(Context context) {
        this.context = context.getApplicationContext();
    }

    @Nullable
    @Override
    public Request authenticate(@Nullable Route route, @NonNull Response response) throws IOException {
        // Prevent infinite refresh loop if refresh request itself failed
        if (response.request().url().toString().contains("/auth/v1/token")) {
            return null;
        }

        SessionManager sessionManager = SessionManager.getInstance(context);
        String refreshToken = sessionManager.getRefreshToken();
        if (refreshToken == null || refreshToken.isEmpty()) {
            return null;
        }

        synchronized (this) {
            // Check if another thread already refreshed
            String currentToken = sessionManager.getAccessToken();
            String authHeader = response.request().header("Authorization");
            if (authHeader != null && !authHeader.endsWith(currentToken)) {
                return response.request().newBuilder()
                        .header("Authorization", "Bearer " + currentToken)
                        .build();
            }

            OkHttpClient client = new OkHttpClient();
            Map<String, String> bodyMap = new HashMap<>();
            bodyMap.put("refresh_token", refreshToken);
            RequestBody body = RequestBody.create(
                    MediaType.parse("application/json"),
                    gson.toJson(bodyMap)
            );

            String refreshUrl = BuildConfig.SUPABASE_URL.replaceAll("/+$", "") + "/auth/v1/token?grant_type=refresh_token";
            Request refreshRequest = new Request.Builder()
                    .url(refreshUrl)
                    .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            try (Response refreshResponse = client.newCall(refreshRequest).execute()) {
                if (refreshResponse.isSuccessful() && refreshResponse.body() != null) {
                    String responseBody = refreshResponse.body().string();
                    TokenResponse tokenData = gson.fromJson(responseBody, TokenResponse.class);
                    if (tokenData != null && tokenData.getAccessToken() != null) {
                        sessionManager.saveTokens(tokenData.getAccessToken(), tokenData.getRefreshToken());
                        return response.request().newBuilder()
                                .header("Authorization", "Bearer " + tokenData.getAccessToken())
                                .build();
                    }
                } else {
                    Log.w(TAG, "Refresh token expired or invalid (HTTP " + refreshResponse.code() + "). Logging out.");
                    sessionManager.clearSession();
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception during token refresh", e);
            }
        }

        return null;
    }
}
