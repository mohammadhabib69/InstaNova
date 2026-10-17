package com.insta.nova.data.remote;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;

import okhttp3.Authenticator;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

/**
 * Handles 401s for Firebase-backed Supabase requests.
 *
 * <p>Firebase ID tokens live ~1 hour. On a 401 the token is force-refreshed once and
 * the request is retried. If Supabase says it cannot decode the token at all
 * ({@code PGRST301}, i.e. the Firebase third-party integration is not enabled), the
 * token is dropped so the request is retried anonymously: public reads keep working
 * and the failure is logged once instead of on every call.
 */
public class TokenAuthenticator implements Authenticator {

    @Nullable
    @Override
    public Request authenticate(@Nullable Route route, @NonNull Response response) throws IOException {
        String authorization = response.request().header("Authorization");

        // No token was attached: nothing to refresh, and retrying would loop.
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }

        // Second attempt: do not loop.
        if (responseCount(response) >= 2) {
            return null;
        }

        String body = "";
        try {
            body = response.peekBody(4096).string();
        } catch (Exception ignored) {
        }
        boolean undecodable = body.contains("PGRST301")
                || body.contains("No suitable key")
                || body.contains("wrong key type");

        if (undecodable) {
            FirebaseTokens.markSupabaseRejected();
            return response.request().newBuilder()
                    .removeHeader("Authorization")
                    .build();
        }

        String refreshed = FirebaseTokens.current(true);
        if (refreshed == null || refreshed.isEmpty()) {
            return null;
        }
        if (authorization.endsWith(refreshed)) {
            // Same token Firebase would hand out again — no point retrying.
            return null;
        }
        return response.request().newBuilder()
                .header("Authorization", "Bearer " + refreshed)
                .build();
    }

    private static int responseCount(Response response) {
        int count = 1;
        Response r = response;
        while ((r = r.priorResponse()) != null) {
            count++;
        }
        return count;
    }
}
