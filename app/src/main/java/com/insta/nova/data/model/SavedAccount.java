package com.insta.nova.data.model;

import org.json.JSONException;
import org.json.JSONObject;

/** One remembered login on the PAGE 16 saved-accounts screen. */
public class SavedAccount {

    public final String userId;
    public final String username;
    public final String fullName;
    public final String avatarUrl;
    public final long lastLoginAt;

    public SavedAccount(String userId, String username, String fullName, String avatarUrl, long lastLoginAt) {
        this.userId = userId == null ? "" : userId;
        this.username = username == null ? "" : username;
        this.fullName = fullName == null ? "" : fullName;
        this.avatarUrl = avatarUrl == null ? "" : avatarUrl;
        this.lastLoginAt = lastLoginAt;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("userId", userId);
        o.put("username", username);
        o.put("fullName", fullName);
        o.put("avatarUrl", avatarUrl);
        o.put("lastLoginAt", lastLoginAt);
        return o;
    }

    public static SavedAccount fromJson(JSONObject o) {
        return new SavedAccount(
                o.optString("userId", ""),
                o.optString("username", ""),
                o.optString("fullName", ""),
                o.optString("avatarUrl", ""),
                o.optLong("lastLoginAt", 0L));
    }
}
