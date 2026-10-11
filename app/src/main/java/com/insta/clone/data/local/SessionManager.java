package com.insta.clone.data.local;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Manages user session tokens, user IDs, and auth persistence.
 */
public class SessionManager {
    private static final String PREF_NAME = "insta_session_prefs";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_AVATAR_URL = "avatar_url";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";

    private static SessionManager instance;
    private final SharedPreferences prefs;

    public static synchronized SessionManager getInstance(Context context) {
        if (instance == null) {
            instance = new SessionManager(context.getApplicationContext());
        }
        return instance;
    }

    public SessionManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveSession(String accessToken, String refreshToken, String userId, String username, String avatarUrl) {
        prefs.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .putString(KEY_USER_ID, userId)
                .putString(KEY_USERNAME, username)
                .putString(KEY_AVATAR_URL, avatarUrl)
                .putBoolean(KEY_IS_LOGGED_IN, true)
                .apply();
    }

    public void saveTokens(String accessToken, String refreshToken) {
        String uid = extractSub(accessToken);
        SharedPreferences.Editor editor = prefs.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken);
        if (uid != null && !uid.isEmpty()) {
            editor.putString(KEY_USER_ID, uid);
        }
        editor.apply();
    }

    public boolean isLoggedIn() {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    public String getAccessToken() {
        return prefs.getString(KEY_ACCESS_TOKEN, null);
    }

    public String getRefreshToken() {
        return prefs.getString(KEY_REFRESH_TOKEN, null);
    }

    public String getUserId() {
        String tokenSub = extractSub(getAccessToken());
        if (tokenSub != null && !tokenSub.isEmpty()) {
            return tokenSub;
        }
        return prefs.getString(KEY_USER_ID, null);
    }

    private static String extractSub(String token) {
        if (token == null || !token.contains(".")) return null;
        try {
            String[] parts = token.split("\\.");
            if (parts.length >= 2) {
                byte[] decoded = android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE);
                org.json.JSONObject json = new org.json.JSONObject(new String(decoded, java.nio.charset.StandardCharsets.UTF_8));
                if (json.has("sub")) {
                    return json.getString("sub");
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public String getUsername() {
        return prefs.getString(KEY_USERNAME, "user");
    }

    public String getAvatarUrl() {
        return prefs.getString(KEY_AVATAR_URL, null);
    }

    public void clearSession() {
        prefs.edit().clear().apply();
    }
}
