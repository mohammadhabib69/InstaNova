package com.insta.nova.data.remote.dto;

import com.google.gson.annotations.SerializedName;
import com.insta.nova.data.model.Post;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO representing post items from `v_feed`, `v_posts`, and `v_saved`.
 */
public class PostDto implements Serializable {
    @SerializedName("id")
    private String id;

    @SerializedName("user_id")
    private String userId;

    @SerializedName("username")
    private String username;

    @SerializedName("avatar_url")
    private String avatarUrl;

    @SerializedName("is_verified")
    private boolean isVerified;

    @SerializedName("caption")
    private String caption;

    @SerializedName("location")
    private String location;

    @SerializedName("media")
    private List<String> mediaUrls;

    @SerializedName("media_urls")
    private List<String> legacyMediaUrls;

    @SerializedName("top_likers")
    private List<TopLikerDto> topLikers;

    @SerializedName("media_type")
    private String mediaType;

    @SerializedName("is_reel")
    private boolean isReel;

    @SerializedName("video_url")
    private String videoUrl;

    @SerializedName("thumbnail_url")
    private String thumbnailUrl;

    @SerializedName("likes_count")
    private int likesCount;

    @SerializedName("comments_count")
    private int commentsCount;

    @SerializedName("liked_by_me")
    private boolean isLiked;

    @SerializedName("saved_by_me")
    private boolean isSaved;

    @SerializedName("created_at")
    private String createdAt;

    public PostDto() {}

    public Post toPost() {
        Post post = new Post(
                id != null ? id : "",
                userId != null ? userId : "",
                username != null ? username : "",
                avatarUrl != null ? avatarUrl : "",
                isVerified,
                caption != null ? caption : "",
                location != null ? location : "",
                mediaUrls != null ? mediaUrls : (legacyMediaUrls != null ? legacyMediaUrls : new ArrayList<>()),
                mediaType != null ? mediaType : "image",
                likesCount,
                commentsCount,
                isLiked,
                isSaved,
                0L
        );
        post.setAvatarUrl(avatarUrl);
        post.setVerified(isVerified);
        post.setReel(isReel || "reel".equalsIgnoreCase(mediaType));
        post.setVideoUrl(videoUrl);
        post.setThumbnailUrl(thumbnailUrl);
        post.setCreatedAt(createdAt);
        return post;
    }

    public Post toDomain() { return toPost(); }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public boolean isVerified() { return isVerified; }
    public void setVerified(boolean verified) { isVerified = verified; }

    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public List<String> getMediaUrls() { return mediaUrls != null ? mediaUrls : legacyMediaUrls; }
    public void setMediaUrls(List<String> mediaUrls) { this.mediaUrls = mediaUrls; }

    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }

    public boolean isReel() { return isReel; }
    public void setReel(boolean reel) { isReel = reel; }

    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }

    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }

    public int getLikesCount() { return likesCount; }
    public void setLikesCount(int likesCount) { this.likesCount = likesCount; }

    public int getCommentsCount() { return commentsCount; }
    public void setCommentsCount(int commentsCount) { this.commentsCount = commentsCount; }

    public boolean isLiked() { return isLiked; }
    public void setLiked(boolean liked) { isLiked = liked; }

    public boolean isSaved() { return isSaved; }
    public void setSaved(boolean saved) { isSaved = saved; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public List<TopLikerDto> getTopLikers() { return topLikers; }

    public static class TopLikerDto implements Serializable {
        @SerializedName("username") private String username;
        @SerializedName("avatar_url") private String avatarUrl;
        public String getUsername() { return username; }
        public String getAvatarUrl() { return avatarUrl; }
    }
}
