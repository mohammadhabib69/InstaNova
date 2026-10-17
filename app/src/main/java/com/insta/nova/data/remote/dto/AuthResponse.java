package com.insta.nova.data.remote.dto;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.util.Map;

/**
 * Supabase GoTrue signup response. access_token may be null when email confirmation is required.
 */
public class AuthResponse implements Serializable {

    @SerializedName("access_token")
    private String accessToken;

    @SerializedName("refresh_token")
    private String refreshToken;

    @SerializedName("user")
    private TokenResponse.AuthUser user;

    public AuthResponse() {}

    public AuthResponse(String accessToken, String refreshToken, String userId, String username, String email) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.user = new TokenResponse.AuthUser();
        this.user.id = userId;
        this.user.email = email;
        this.user.userMetadata = new java.util.HashMap<>();
        if (username != null) {
            this.user.userMetadata.put("username", username);
        }
    }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

    public TokenResponse.AuthUser getUser() { return user; }

    public String getUserId() {
        return user != null ? user.id : null;
    }

    public String getUsername() {
        if (user == null || user.userMetadata == null) return null;
        Object v = user.userMetadata.get("username");
        return v != null ? String.valueOf(v) : null;
    }

    public String getEmail() {
        return user != null ? user.email : null;
    }

    public boolean hasSession() {
        return accessToken != null && !accessToken.trim().isEmpty();
    }
}
