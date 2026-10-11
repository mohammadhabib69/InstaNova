package com.insta.clone.data.remote;

import androidx.annotation.NonNull;
import com.insta.clone.BuildConfig;
import com.insta.clone.data.local.SessionManager;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Injects required Supabase headers (`apikey` and `Authorization`).
 * Reads the CURRENT access token on every request (never caches at startup).
 * Publishable keys (sb_publishable_...) are sent ONLY as apikey, never as Bearer.
 */
public class AuthInterceptor implements Interceptor {

    private final SessionManager sessionManager;

    public AuthInterceptor(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request original = chain.request();
        Request.Builder builder = original.newBuilder();

        String apiKey = BuildConfig.SUPABASE_ANON_KEY != null ? BuildConfig.SUPABASE_ANON_KEY.trim() : "";
        builder.header("apikey", apiKey);
        builder.header("Accept", "application/json");

        String token = sessionManager.getAccessToken();
        boolean hasUserToken = token != null && !token.trim().isEmpty();
        boolean publishableKey = apiKey.startsWith("sb_publishable_");

        if (hasUserToken) {
            builder.header("Authorization", "Bearer " + token.trim());
        } else if (!publishableKey && !apiKey.isEmpty()) {
            // Legacy anon JWT may be used as Bearer when logged out.
            builder.header("Authorization", "Bearer " + apiKey);
        }
        // If publishable key and logged out: apikey only (never Bearer sb_publishable_...).

        return chain.proceed(builder.build());
    }
}
