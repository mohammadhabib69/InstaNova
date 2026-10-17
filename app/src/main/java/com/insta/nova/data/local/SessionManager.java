package com.insta.nova.data.local;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Local, non-sensitive application state.
 *
 * <p>FirebaseAuth is the single source of truth for authentication. Nothing in this
 * class is a credential: no passwords, and no Firebase ID tokens are ever written
 * here (Firebase persists and refreshes its own session). Only the identifiers the
 * UI needs to render offline are stored:
 *
 * <ul>
 *   <li>{@code firebase_uid} — Firebase's own user id (needed to look the profile up)</li>
 *   <li>{@code user_id} — the Supabase {@code profiles.id} UUID used by every table</li>
 *   <li>username / avatar url — cached for instant UI</li>
 *   <li>{@code signup_in_progress} — lets Splash resume an unfinished wizard</li>
 * </ul>
 *
 * <p>Every getter tolerates missing or corrupt data and returns a safe default.
 */
public class SessionManager {

    private static final String TAG = "InstaSession";
    private static final String PREF_NAME = "insta_session_prefs";

    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_FIREBASE_UID = "firebase_uid";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_AVATAR_URL = "avatar_url";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static final String KEY_REMEMBER_LOGIN = "remember_login";
    private static final String KEY_SIGNUP_IN_PROGRESS = "signup_in_progress";

    private static SessionManager instance;
    private final SharedPreferences prefs;

    public static synchronized SessionManager getInstance(Context context) {
        if (instance == null) {
            instance = new SessionManager(context.getApplicationContext());
        }
        return instance;
    }

    public SessionManager(Context context) {
        SharedPreferences p;
        try {
            p = context.getApplicationContext()
                    .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        } catch (RuntimeException e) {
            // Corrupt prefs file: fall back to an empty, in-memory-equivalent store.
            Log.e(TAG, "Unable to open session prefs, starting clean", e);
            p = context.getApplicationContext()
                    .getSharedPreferences(PREF_NAME + "_recovery", Context.MODE_PRIVATE);
        }
        this.prefs = p;
    }

    /* ------------------------------------------------------------------ */
    /* Identity                                                            */
    /* ------------------------------------------------------------------ */

    /**
     * Stores the authenticated identity.
     *
     * @param firebaseUid FirebaseAuth user id — the identity anchor
     * @param profileId   Supabase {@code profiles.id} (may be null until the profile exists)
     */
    public void saveIdentity(String firebaseUid, String profileId, String username, String avatarUrl) {
        SharedPreferences.Editor editor = prefs.edit()
                .putString(KEY_FIREBASE_UID, firebaseUid)
                .putString(KEY_USERNAME, username)
                .putString(KEY_AVATAR_URL, avatarUrl)
                .putBoolean(KEY_IS_LOGGED_IN, true);
        if (profileId != null && !profileId.isEmpty()) {
            editor.putString(KEY_USER_ID, profileId);
        }
        editor.apply();
    }

    /** Updates just the Supabase profile id once the row has been created/resolved. */
    public void setProfileId(String profileId) {
        if (profileId == null || profileId.isEmpty()) return;
        prefs.edit().putString(KEY_USER_ID, profileId).apply();
    }

    public void updateAvatar(String avatarUrl) {
        prefs.edit().putString(KEY_AVATAR_URL, avatarUrl).apply();
    }

    /** Lets the shell refresh the nav avatar when Edit Profile changes the cached URL. */
    public void registerAvatarListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        if (listener != null) prefs.registerOnSharedPreferenceChangeListener(listener);
    }

    public void unregisterAvatarListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        if (listener != null) prefs.unregisterOnSharedPreferenceChangeListener(listener);
    }

    public void updateUsername(String username) {
        prefs.edit().putString(KEY_USERNAME, username).apply();
    }

    public String getFirebaseUid() {
        return prefs.getString(KEY_FIREBASE_UID, null);
    }

    /** Supabase {@code profiles.id}; empty string when the profile has not been created yet. */
    public String getUserId() {
        String id = prefs.getString(KEY_USER_ID, null);
        return id == null ? "" : id;
    }

    public String getUsername() {
        String name = prefs.getString(KEY_USERNAME, null);
        return name == null || name.isEmpty() ? "user" : name;
    }

    public String getAvatarUrl() {
        return prefs.getString(KEY_AVATAR_URL, null);
    }

    public boolean isLoggedIn() {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false) && getFirebaseUid() != null;
    }

    /* ------------------------------------------------------------------ */
    /* Signup-in-progress (Splash resume)                                  */
    /* ------------------------------------------------------------------ */

    /**
     * True from the moment the Firebase account exists until the profile is
     * finalised on the Terms step. Splash uses this to reopen the wizard at the
     * Password step instead of dropping the user into Home.
     */
    public void setSignupInProgress(boolean inProgress) {
        prefs.edit().putBoolean(KEY_SIGNUP_IN_PROGRESS, inProgress).apply();
    }

    public boolean isSignupInProgress() {
        return prefs.getBoolean(KEY_SIGNUP_IN_PROGRESS, false);
    }

    /* ------------------------------------------------------------------ */
    /* "Remember login info"                                               */
    /* ------------------------------------------------------------------ */

    /**
     * Persisted choice. When false, Splash signs the Firebase user out on the next
     * cold start so nothing survives on the device.
     */
    public void setRememberLogin(boolean remember) {
        prefs.edit().putBoolean(KEY_REMEMBER_LOGIN, remember).apply();
    }

    public boolean isRememberLogin() {
        return prefs.getBoolean(KEY_REMEMBER_LOGIN, true);
    }

    /* ------------------------------------------------------------------ */
    /* Cleanup                                                             */
    /* ------------------------------------------------------------------ */

    public void clearSession() {
        prefs.edit().clear().apply();
    }
}
