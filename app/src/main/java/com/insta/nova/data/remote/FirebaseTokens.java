package com.insta.nova.data.remote;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GetTokenResult;

import java.util.concurrent.TimeUnit;

/**
 * Single place that turns the signed-in Firebase user into a Firebase ID token.
 *
 * <p>Supabase (with Third-Party Auth → Firebase enabled) verifies this token
 * itself and derives {@code auth.jwt()->>'sub'} from it. The token is never
 * persisted: it is fetched from FirebaseAuth on demand and lives only for the
 * duration of a request. Firebase caches and refreshes it internally.
 */
public final class FirebaseTokens {

    private static final String TAG = "InstaApi";

    /**
     * Set when Supabase answers "no suitable key" for our token, which means the
     * project has not enabled the Firebase third-party integration yet. While it is
     * set we stop sending the token so public (anon) reads keep working, instead of
     * failing every request with 401. Reset on next process start, so enabling the
     * integration needs only an app restart.
     */
    private static volatile boolean supabaseRejectsFirebaseToken = false;

    private FirebaseTokens() {
    }

    /** Blocking fetch. Returns null when nobody is signed in or Firebase fails. */
    public static String current(boolean forceRefresh) {
        if (supabaseRejectsFirebaseToken) return null;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return null;
        try {
            GetTokenResult result = Tasks.await(user.getIdToken(forceRefresh), 15, TimeUnit.SECONDS);
            return result == null ? null : result.getToken();
        } catch (Exception e) {
            Log.w(TAG, "Could not obtain a Firebase ID token: " + e.getMessage());
            return null;
        }
    }

    /** Non-blocking read of the already-cached token, used by tests/diagnostics. */
    public static String cached() {
        return supabaseRejectsFirebaseToken ? null : current(false);
    }

    public interface TokenCallback {
        void onToken(String token);
    }

    /**
     * Fetches the ID token without blocking, safe to call from the main thread.
     *
     * <p>{@link #current} uses {@code Tasks.await}, which throws
     * "Must not be called on the main application thread". Any caller on the UI
     * thread therefore silently received null — which for Supabase Realtime
     * means an anonymous join: the channel reports "joined" while RLS filters
     * out every row, so no call event is ever delivered.
     */
    public static void currentAsync(@NonNull TokenCallback callback) {
        if (supabaseRejectsFirebaseToken) {
            callback.onToken(null);
            return;
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onToken(null);
            return;
        }
        try {
            user.getIdToken(false).addOnCompleteListener(task -> {
                if (!task.isSuccessful() || task.getResult() == null) {
                    callback.onToken(null);
                } else {
                    callback.onToken(task.getResult().getToken());
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "Async token fetch failed: " + e.getMessage());
            callback.onToken(null);
        }
    }

    public static void markSupabaseRejected() {
        if (!supabaseRejectsFirebaseToken) {
            supabaseRejectsFirebaseToken = true;
            Log.e(TAG, "Supabase rejected the Firebase ID token (PGRST301). "
                    + "Enable Authentication -> Third-party auth -> Firebase for this project. "
                    + "Falling back to anonymous access for this process.");
        }
    }

    public static boolean isSupabaseRejecting() {
        return supabaseRejectsFirebaseToken;
    }
}
