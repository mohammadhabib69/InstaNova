package com.insta.clone.data.remote.dto;

import com.google.gson.annotations.SerializedName;
import com.insta.clone.data.model.Comment;
import java.io.Serializable;

/**
 * DTO representing comments from the `comments` table.
 */
public class CommentDto implements Serializable {
    @SerializedName("id")
    private String id;

    @SerializedName("post_id")
    private String postId;

    @SerializedName("user_id")
    private String userId;

    @SerializedName("username")
    private String username;

    @SerializedName("avatar_url")
    private String avatarUrl;

    @SerializedName("is_verified")
    private boolean isVerified;

    @SerializedName("text")
    private String text;

    @SerializedName("body")
    private String body;

    @SerializedName("likes_count")
    private int likesCount;

    @SerializedName("liked_by_me")
    private boolean likedByMe;

    @SerializedName("created_at")
    private String createdAt;

    public CommentDto() {}

    public Comment toComment() {
        Comment comment = new Comment(
                id != null ? id : "",
                postId != null ? postId : "",
                userId != null ? userId : "",
                username != null ? username : "",
                avatarUrl != null ? avatarUrl : "",
                text != null ? text : (body != null ? body : ""),
                likesCount,
                likedByMe,
                0L
        );
        comment.setAvatarUrl(avatarUrl);
        comment.setVerified(isVerified);
        comment.setLikedByMe(likedByMe);
        comment.setCreatedAt(createdAt);
        return comment;
    }

    public Comment toDomain() { return toComment(); }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPostId() { return postId; }
    public void setPostId(String postId) { this.postId = postId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public boolean isVerified() { return isVerified; }
    public void setVerified(boolean verified) { isVerified = verified; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public int getLikesCount() { return likesCount; }
    public void setLikesCount(int likesCount) { this.likesCount = likesCount; }

    public boolean isLikedByMe() { return likedByMe; }
    public void setLikedByMe(boolean likedByMe) { this.likedByMe = likedByMe; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
