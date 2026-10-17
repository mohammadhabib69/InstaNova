package com.insta.nova.data.remote.dto;

import com.google.gson.annotations.SerializedName;
import com.insta.nova.data.model.Story;
import java.io.Serializable;

/**
 * DTO representing stories in the `stories` table.
 */
public class StoryDto implements Serializable {
    @SerializedName("id")
    private String id;

    @SerializedName("user_id")
    private String userId;

    @SerializedName("username")
    private String username;

    @SerializedName("avatar_url")
    private String avatarUrl;

    @SerializedName("media_url")
    private String mediaUrl;

    @SerializedName("media_type")
    private String mediaType;

    @SerializedName("is_seen")
    private boolean isSeen;

    @SerializedName("seen_by_me")
    private boolean seenByMe;

    @SerializedName("created_at")
    private String createdAt;

    @SerializedName("expires_at")
    private String expiresAt;

    public StoryDto() {}

    public Story toStory() {
        Story s = new Story(
                id != null ? id : "",
                userId != null ? userId : "",
                username != null ? username : "",
                avatarUrl != null ? avatarUrl : "",
                mediaUrl != null ? mediaUrl : "",
                mediaType != null ? mediaType : "image",
                isSeen || seenByMe,
                0L
        );
        s.setAvatarUrl(avatarUrl);
        s.setCreatedAt(createdAt);
        s.setExpiresAt(expiresAt);
        return s;
    }

    public Story toDomain() { return toStory(); }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public String getMediaUrl() { return mediaUrl; }
    public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }

    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }

    public boolean isSeen() { return isSeen; }
    public void setSeen(boolean seen) { isSeen = seen; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getExpiresAt() { return expiresAt; }
    public void setExpiresAt(String expiresAt) { this.expiresAt = expiresAt; }
}
