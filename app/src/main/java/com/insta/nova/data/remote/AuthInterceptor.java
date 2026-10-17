package com.insta.nova.data.remote;

import androidx.annotation.NonNull;

import com.insta.nova.BuildConfig;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Adds the Supabase {@code apikey} and, when a Firebase user is signed in, the
 * Firebase ID token as {@code Authorization: Bearer ...}.
 *
 * <p>The token is read fresh on every request (never cached at startup) so a
 * refresh or sign-out takes effect immediately. When nobody is signed in, only
 * the apikey is sent, which is the anonymous/public-read path.
 */
public class AuthInterceptor implements Interceptor {

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request.Builder builder = chain.request().newBuilder();

        String apiKey = BuildConfig.SUPABASE_ANON_KEY != null ? BuildConfig.SUPABASE_ANON_KEY.trim() : "";
        builder.header("apikey", apiKey);
        builder.header("Accept", "application/json");

        String token = FirebaseTokens.current(false);
        if (token != null && !token.isEmpty()) {
            builder.header("Authorization", "Bearer " + token);
        } else if (!apiKey.isEmpty() && !apiKey.startsWith("sb_publishable_")) {
            // Legacy anon JWT doubles as the bearer when logged out.
            builder.header("Authorization", "Bearer " + apiKey);
        }

        return chain.proceed(builder.build());
    }
}
