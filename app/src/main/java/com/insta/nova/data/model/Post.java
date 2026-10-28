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
    /** Up to three usernames of the newest likers, for the "Liked by …" line. */
    private List<String> likerNames;
    /** Advanced settings from the composer: hide the tally, or close the thread. */
    private boolean hideLikeCount;
    private boolean commentsDisabled;
    /** The attached track, null when the post has no music. */
    private String musicUrl;
    private String musicTitle;
    private boolean isLiked;
    private boolean isSaved;
    private String videoUrl;
    private String thumbnailUrl;
    private long createdAt;
    /** "insta" (Home/Explore/Profile) or "nova" (Nova tab). */
    private String space = SPACE_INSTA;

    public static final String SPACE_INSTA = "insta";
    public static final String SPACE_NOVA = "nova";

    public Post() {
        this.mediaUrls = new ArrayList<>();
        this.space = SPACE_INSTA;
    }

    public Post(String id, String userId, String username, String userAvatar, boolean isUserVerified,
                String caption, String location, List<String> mediaUrls, String mediaType,
                int likesCount, int commentsCount, boolean isLiked, boolean isSaved, long createdAt) {
        this.id = id;
        this.userId = userId;
        this.username = username;
        this.userAvatar = userAvatar;
        this.isUserVerified = isUserVerified;
        // Reels carry their editor block at the end of the caption; strip it here so every
        // construction path (RPC mapping, local store, copy) shows only the author's text.
        setCaption(caption);
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
        // Anything the composer can attach has to survive an optimistic like/save copy,
        // otherwise the row is rebuilt without it (the music chip used to vanish on tap).
        this.musicUrl = other.musicUrl;
        this.musicTitle = other.musicTitle;
        this.hideLikeCount = other.hideLikeCount;
        this.commentsDisabled = other.commentsDisabled;
        this.likesCount = other.likesCount;
        this.commentsCount = other.commentsCount;
        this.likerNames = other.likerNames == null
                ? new ArrayList<>() : new ArrayList<>(other.likerNames);
        this.isLiked = other.isLiked;
        this.isSaved = other.isSaved;
        this.videoUrl = other.videoUrl;
        this.thumbnailUrl = other.thumbnailUrl;
        this.space = other.space;
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

    public String getCaption() {
        // Gson-built copies bypass setCaption, so the strip also happens on read.
        return com.insta.nova.util.ReelMetaCodec.strip(caption);
    }
    public void setCaption(String caption) {
        this.caption = com.insta.nova.util.ReelMetaCodec.strip(caption);
        java.util.Map<String, Object> parsed =
                com.insta.nova.util.ReelMetaCodec.parse(caption);
        if (!parsed.isEmpty()) this.reelMeta = parsed;
    }

    /** Reel editor block (timed text, volume levels); null for a photo post. */
    private java.util.Map<String, Object> reelMeta;

    public java.util.Map<String, Object> getReelMeta() {
        if (reelMeta == null && com.insta.nova.util.ReelMetaCodec.hasMeta(caption)) {
            reelMeta = com.insta.nova.util.ReelMetaCodec.parse(caption);
        }
        return reelMeta;
    }

    public void setReelMeta(java.util.Map<String, Object> reelMeta) { this.reelMeta = reelMeta; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public List<String> getMediaUrls() { return mediaUrls; }
    public void setMediaUrls(List<String> mediaUrls) { this.mediaUrls = mediaUrls; }

    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }

    public String getMusicUrl() { return musicUrl; }
    public void setMusicUrl(String musicUrl) { this.musicUrl = musicUrl; }
    public String getMusicTitle() { return musicTitle; }
    public void setMusicTitle(String musicTitle) { this.musicTitle = musicTitle; }

    public boolean isHideLikeCount() { return hideLikeCount; }
    public void setHideLikeCount(boolean hideLikeCount) { this.hideLikeCount = hideLikeCount; }

    public boolean isCommentsDisabled() { return commentsDisabled; }
    public void setCommentsDisabled(boolean commentsDisabled) { this.commentsDisabled = commentsDisabled; }

    public int getLikesCount() { return likesCount; }
    public void setLikesCount(int likesCount) { this.likesCount = likesCount; }

    public List<String> getLikerNames() { return likerNames; }
    public void setLikerNames(List<String> likerNames) { this.likerNames = likerNames; }

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

    public String getSpace() {
        return space == null || space.trim().isEmpty() ? SPACE_INSTA : space;
    }

    public void setSpace(String space) {
        this.space = space;
    }

    public boolean isNova() {
        return SPACE_NOVA.equalsIgnoreCase(getSpace());
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
