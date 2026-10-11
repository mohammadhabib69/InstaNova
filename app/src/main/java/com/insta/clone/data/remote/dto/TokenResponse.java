package com.insta.clone.data.remote.dto;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.util.Map;

/**
 * Supabase GoTrue token response (password / refresh grant).
 */
public class TokenResponse implements Serializable {

    @SerializedName("access_token")
    private String accessToken;

    @SerializedName("refresh_token")
    private String refreshToken;

    @SerializedName("expires_in")
    private Integer expiresIn;

    @SerializedName("token_type")
    private String tokenType;

    @SerializedName("user")
    private AuthUser user;

    public TokenResponse() {}

    public TokenResponse(String accessToken, String refreshToken, String userId, String username, String avatarUrl) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.user = new AuthUser();
        this.user.id = userId;
        this.user.userMetadata = new java.util.HashMap<>();
        if (username != null) {
            this.user.userMetadata.put("username", username);
        }
        if (avatarUrl != null) {
            this.user.userMetadata.put("avatar_url", avatarUrl);
        }
    }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

    public Integer getExpiresIn() { return expiresIn; }
    public AuthUser getUser() { return user; }

    public String getUserId() {
        return user != null ? user.id : null;
    }

    public String getUsername() {
        if (user == null || user.userMetadata == null) return null;
        Object v = user.userMetadata.get("username");
        return v != null ? String.valueOf(v) : null;
    }

    public String getAvatarUrl() {
        if (user == null || user.userMetadata == null) return null;
        Object v = user.userMetadata.get("avatar_url");
        return v != null ? String.valueOf(v) : null;
    }

    public String getEmail() {
        return user != null ? user.email : null;
    }

    public static class AuthUser implements Serializable {
        @SerializedName("id")
        public String id;

        @SerializedName("email")
        public String email;

        @SerializedName("user_metadata")
        public Map<String, Object> userMetadata;
    }
}
