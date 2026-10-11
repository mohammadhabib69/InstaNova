package com.insta.clone.data.remote.dto;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/**
 * DTO representing notifications in the `notifications` table.
 */
public class NotificationDto implements Serializable {
    @SerializedName("id")
    private String id;

    @SerializedName("user_id")
    private String userId;

    @SerializedName("actor_id")
    private String actorId;

    @SerializedName("actor_username")
    private String actorUsername;

    @SerializedName("actor_avatar_url")
    private String actorAvatarUrl;

    @SerializedName("type")
    private String type; // "like", "comment", "follow"

    @SerializedName("post_id")
    private String postId;

    @SerializedName("post_thumbnail_url")
    private String postThumbnailUrl;

    @SerializedName("text")
    private String text;

    @SerializedName("is_read")
    private boolean read;

    @SerializedName("created_at")
    private String createdAt;

    public NotificationDto() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }

    public String getActorUsername() { return actorUsername; }
    public void setActorUsername(String actorUsername) { this.actorUsername = actorUsername; }

    public String getActorAvatarUrl() { return actorAvatarUrl; }
    public void setActorAvatarUrl(String actorAvatarUrl) { this.actorAvatarUrl = actorAvatarUrl; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getPostId() { return postId; }
    public void setPostId(String postId) { this.postId = postId; }

    public String getPostThumbnailUrl() { return postThumbnailUrl; }
    public void setPostThumbnailUrl(String postThumbnailUrl) { this.postThumbnailUrl = postThumbnailUrl; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
