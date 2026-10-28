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

    /**
     * Creation time in epoch millis.
     *
     * <p>Rows simulated locally carry millis as a string; rows written by the backend
     * carry an ISO-8601 timestamp. Parsing only the first form silently dated every
     * server row to 0, which pushed real notifications to the bottom of the list —
     * a story reaction looked missing when it had in fact been stored.
     */
    public long createdAtMillis() {
        if (createdAt == null || createdAt.isEmpty()) return 0L;
        try {
            return Long.parseLong(createdAt);
        } catch (NumberFormatException ignored) {
            // Fall through to ISO-8601.
        }
        String iso = createdAt.trim().replace(' ', 'T');
        // Trim sub-second precision and a trailing Z so one pattern covers both.
        int dot = iso.indexOf('.');
        if (dot > 0) {
            int end = dot + 1;
            while (end < iso.length() && Character.isDigit(iso.charAt(end))) end++;
            iso = iso.substring(0, dot) + iso.substring(end);
        }
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ssZ",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd"
        };
        for (String pattern : patterns) {
            try {
                java.text.SimpleDateFormat format =
                        new java.text.SimpleDateFormat(pattern, java.util.Locale.US);
                format.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                java.util.Date parsed = format.parse(iso);
                if (parsed != null) return parsed.getTime();
            } catch (java.text.ParseException ignored) {
                // Try the next shape.
            }
        }
        return 0L;
    }
}
