package com.insta.nova.data.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import com.insta.nova.util.TimeUtils;

/**
 * Domain model representing an Instagram post (image, carousel, or reel).
 */
public class Post implements Serializable {
    private String id;
    private String userId;
    private String username;
    private String userAvatar;
    private boolean isUserVerified;
    private String caption;
    private String location;
    private List<String> mediaUrls;
    private String mediaType; // "image", "carousel", "reel"
    private int likesCount;
    private int commentsCount;
    private boolean isLiked;
    private boolean isSaved;
    private String videoUrl;
    private String thumbnailUrl;
    private long createdAt;

    public Post() {
        this.mediaUrls = new ArrayList<>();
    }

    public Post(String id, String userId, String username, String userAvatar, boolean isUserVerified,
                String caption, String location, List<String> mediaUrls, String mediaType,
                int likesCount, int commentsCount, boolean isLiked, boolean isSaved, long createdAt) {
        this.id = id;
        this.userId = userId;
        this.username = username;
        this.userAvatar = userAvatar;
        this.isUserVerified = isUserVerified;
        this.caption = caption;
        this.location = location;
        this.mediaUrls = mediaUrls != null ? mediaUrls : new ArrayList<>();
        this.mediaType = mediaType != null ? mediaType : "image";
        this.likesCount = likesCount;
        this.commentsCount = commentsCount;
        this.isLiked = isLiked;
        this.isSaved = isSaved;
        this.createdAt = createdAt;
    }

    /**
     * Copies every field, including the mutable media list.
     *
     * <p>Feed interactions need this: the RecyclerView's DiffUtil compares the old and
     * new list entries, so an optimistic like/save has to reach the list as a
     * <em>different</em> instance. Mutating the item the adapter already holds makes
     * both sides of the comparison the same object, and DiffUtil then reports no change.
     */
    public Post(Post other) {
        if (other == null) {
            this.mediaUrls = new ArrayList<>();
            return;
        }
        this.id = other.id;
        this.userId = other.userId;
        this.username = other.username;
        this.userAvatar = other.userAvatar;
        this.isUserVerified = other.isUserVerified;
        this.caption = other.caption;
        this.location = other.location;
        this.mediaUrls = other.mediaUrls == null ? new ArrayList<>() : new ArrayList<>(other.mediaUrls);
        this.mediaType = other.mediaType;
        this.likesCount = other.likesCount;
        this.commentsCount = other.commentsCount;
        this.isLiked = other.isLiked;
        this.isSaved = other.isSaved;
        this.videoUrl = other.videoUrl;
        this.thumbnailUrl = other.thumbnailUrl;
        this.createdAt = other.createdAt;
    }

    public Post copy() {
        return new Post(this);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getUserAvatar() { return userAvatar; }
    public void setUserAvatar(String userAvatar) { this.userAvatar = userAvatar; }

    public boolean isUserVerified() { return isUserVerified; }
    public void setUserVerified(boolean userVerified) { isUserVerified = userVerified; }

    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public List<String> getMediaUrls() { return mediaUrls; }
    public void setMediaUrls(List<String> mediaUrls) { this.mediaUrls = mediaUrls; }

    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }

    public int getLikesCount() { return likesCount; }
    public void setLikesCount(int likesCount) { this.likesCount = likesCount; }

    public int getCommentsCount() { return commentsCount; }
    public void setCommentsCount(int commentsCount) { this.commentsCount = commentsCount; }

    public boolean isLiked() { return isLiked; }
    public void setLiked(boolean liked) { isLiked = liked; }

    public boolean isSaved() { return isSaved; }
    public void setSaved(boolean saved) { isSaved = saved; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public boolean isLikedByMe() { return isLiked; }
    public void setLikedByMe(boolean likedByMe) { this.isLiked = likedByMe; }

    public boolean isSavedByMe() { return isSaved; }
    public void setSavedByMe(boolean savedByMe) { this.isSaved = savedByMe; }

    public boolean isVerified() { return isUserVerified; }
    public void setVerified(boolean verified) { this.isUserVerified = verified; }

    public String getAvatarUrl() { return userAvatar; }
    public void setAvatarUrl(String avatarUrl) { this.userAvatar = avatarUrl; }

    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }

    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }

    public String getFirstMediaUrl() {
        return (mediaUrls != null && !mediaUrls.isEmpty()) ? mediaUrls.get(0) : "";
    }

    public boolean isReel() {
        return "reel".equalsIgnoreCase(mediaType);
    }

    public void setReel(boolean reel) {
        this.mediaType = reel ? "reel" : "image";
    }

    public void setCreatedAt(String createdAtStr) {
        this.createdAt = TimeUtils.parseTimestamp(createdAtStr);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Post post = (Post) o;
        return Objects.equals(id, post.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
