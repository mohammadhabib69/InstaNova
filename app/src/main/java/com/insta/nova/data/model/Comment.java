package com.insta.nova.data.model;

import java.io.Serializable;
import java.util.Objects;
import com.insta.nova.util.TimeUtils;

/**
 * Domain model representing a comment on a post.
 */
public class Comment implements Serializable {
    private String id;
    private String postId;
    private String userId;
    private String username;
    private String userAvatar;
    private String text;
    private int likesCount;
    private boolean isLiked;
    private boolean isVerified;
    private long createdAt;

    public Comment() {}

    public Comment(String id, String postId, String userId, String username, String userAvatar,
                   String text, int likesCount, boolean isLiked, long createdAt) {
        this.id = id;
        this.postId = postId;
        this.userId = userId;
        this.username = username;
        this.userAvatar = userAvatar;
        this.text = text;
        this.likesCount = likesCount;
        this.isLiked = isLiked;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPostId() { return postId; }
    public void setPostId(String postId) { this.postId = postId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getUserAvatar() { return userAvatar; }
    public void setUserAvatar(String userAvatar) { this.userAvatar = userAvatar; }

    public String getAvatarUrl() { return userAvatar; }
    public void setAvatarUrl(String avatarUrl) { this.userAvatar = avatarUrl; }

    public boolean isVerified() { return isVerified; }
    public void setVerified(boolean verified) { isVerified = verified; }

    public boolean isLikedByMe() { return isLiked; }
    public void setLikedByMe(boolean likedByMe) { this.isLiked = likedByMe; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public int getLikesCount() { return likesCount; }
    public void setLikesCount(int likesCount) { this.likesCount = likesCount; }

    public boolean isLiked() { return isLiked; }
    public void setLiked(boolean liked) { isLiked = liked; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public void setCreatedAt(String createdAtStr) {
        this.createdAt = TimeUtils.parseTimestamp(createdAtStr);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Comment comment = (Comment) o;
        return Objects.equals(id, comment.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
