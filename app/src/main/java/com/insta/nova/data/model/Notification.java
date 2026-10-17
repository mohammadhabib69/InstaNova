package com.insta.nova.data.model;

import java.io.Serializable;

/**
 * Domain model representing an activity feed notification (like, comment, follow).
 */
public class Notification implements Serializable {
    private String id;
    private String userId;
    private String actorId;
    private String actorUsername;
    private String actorAvatarUrl;
    private String type; // 'like', 'comment', 'follow'
    private String postId;
    private String postThumbnailUrl;
    private String commentText;
    private boolean read;
    private String createdAt;
    private boolean isActorFollowing;

    public Notification() {}

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

    public String getCommentText() { return commentText; }
    public void setCommentText(String commentText) { this.commentText = commentText; }

    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public boolean isActorFollowing() { return isActorFollowing; }
    public void setActorFollowing(boolean actorFollowing) { isActorFollowing = actorFollowing; }
}
