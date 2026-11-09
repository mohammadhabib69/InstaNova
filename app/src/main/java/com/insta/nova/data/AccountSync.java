package com.insta.nova.data;

import android.content.Context;
import android.util.Log;

import com.google.firebase.auth.FirebaseUser;
import com.insta.nova.data.local.SavedAccountsStore;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.model.SavedAccount;
import com.insta.nova.data.remote.RetrofitClient;
import com.insta.nova.data.remote.api.RestApi;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Keeps the two things that depend on "who is signed in" in step with the session.
 *
 * <p>1. The saved-account list is the only way back to an account without typing its
 * address, so the signed-in account is written there on every launch. Previously only
 * {@code LoginFragment} did it, which meant an account created through the sign-up
 * wizard never appeared in the switcher, and switching to it was impossible.
 *
 * <p>2. {@code get_email_by_username} can only answer if the address is stored against
 * the profile. Supabase holds no auth users (Firebase is the identity provider), so the
 * address is pushed once per launch from the Firebase session. Without this a username
 * login has nothing to resolve.
 *
 * <p>Both calls are fire-and-forget: a failure only costs the convenience of a later
 * username login, never the session in hand.
 */
public final class AccountSync {

    private static final String TAG = "InstaAccountSync";

    private AccountSync() { }

    public static void sync(Context context) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        SessionManager session = SessionManager.getInstance(app);
        String uid = session.getFirebaseUid();
        if (uid == null || uid.isEmpty()) return;

        rememberAccount(app, session, uid);
        pushEmail(app);
    }

    private static void rememberAccount(Context context, SessionManager session, String uid) {
        String username = session.getUsername();
        if (username == null || username.isEmpty()) return;
        try {
            SavedAccountsStore.getInstance(context).upsert(new SavedAccount(
                    uid, username, "", session.getAvatarUrl(), System.currentTimeMillis()));
        } catch (Exception e) {
            Log.w(TAG, "could not remember account: " + e.getMessage());
        }
    }

    private static void pushEmail(Context context) {
        FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        String email = user == null ? null : user.getEmail();
        if (email == null || email.trim().isEmpty()) return;

        Map<String, String> body = new HashMap<>();
        body.put("p_email", email.trim());
        RestApi api = RetrofitClient.getInstance(context).getRestApi();
        api.setMyEmail(body).enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> call, Response<Void> response) {
                if (!response.isSuccessful()) {
                    Log.w(TAG, "profile address rejected: HTTP " + response.code());
                }
            }

            @Override public void onFailure(Call<Void> call, Throwable t) {
                Log.w(TAG, "profile address failed: " + t.getMessage());
            }
        });
    }
}
