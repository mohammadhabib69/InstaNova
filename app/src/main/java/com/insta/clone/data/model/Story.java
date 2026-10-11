package com.insta.clone.data.model;

import java.io.Serializable;

/**
 * Domain model representing an individual temporary 24-hour story.
 */
public class Story implements Serializable {
    private String id;
    private String userId;
    private String username;
    private String userAvatar;
    private String mediaUrl;
    private String mediaType; // "image" or "video"
    private boolean isSeen;
    private long createdAt;
    private String expiresAt;

    public Story() {}

    public Story(String id, String userId, String username, String userAvatar, String mediaUrl,
                 String mediaType, boolean isSeen, long createdAt) {
        this.id = id;
        this.userId = userId;
        this.username = username;
        this.userAvatar = userAvatar;
        this.mediaUrl = mediaUrl;
        this.mediaType = mediaType;
        this.isSeen = isSeen;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getUserAvatar() { return userAvatar; }
    public void setUserAvatar(String userAvatar) { this.userAvatar = userAvatar; }

    public String getAvatarUrl() { return userAvatar; }
    public void setAvatarUrl(String avatarUrl) { this.userAvatar = avatarUrl; }

    public String getMediaUrl() { return mediaUrl; }
    public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }

    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }

    public boolean isSeen() { return isSeen; }
    public void setSeen(boolean seen) { isSeen = seen; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public void setCreatedAt(String createdAtStr) {
        try {
            this.createdAt = Long.parseLong(createdAtStr);
        } catch (Exception e) {
            this.createdAt = System.currentTimeMillis();
        }
    }

    public String getExpiresAt() { return expiresAt; }
    public void setExpiresAt(String expiresAt) { this.expiresAt = expiresAt; }
}
