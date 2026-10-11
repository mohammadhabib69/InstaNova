package com.insta.clone.data.model;

import java.io.Serializable;

/**
 * Domain model representing a chat message in 1-on-1 Direct Messaging.
 */
public class Message implements Serializable {
    private String id;
    private String conversationId;
    private String senderId;
    private String text;
    private String mediaUrl;
    private String imageUrl;
    private String postId;
    private boolean isLiked;
    private boolean isRead;
    private long createdAt;

    public Message() {}

    public Message(String id, String conversationId, String senderId, String text,
                   String mediaUrl, boolean isLiked, long createdAt) {
        this.id = id;
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.text = text;
        this.mediaUrl = mediaUrl;
        this.isLiked = isLiked;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getMediaUrl() { return mediaUrl; }
    public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }

    public String getImageUrl() { return imageUrl != null ? imageUrl : mediaUrl; }
    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
        if (this.mediaUrl == null) this.mediaUrl = imageUrl;
    }

    public String getPostId() { return postId; }
    public void setPostId(String postId) { this.postId = postId; }

    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { this.isRead = read; }

    public boolean isLiked() { return isLiked; }
    public void setLiked(boolean liked) { isLiked = liked; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public void setCreatedAt(String createdAtStr) {
        try {
            this.createdAt = Long.parseLong(createdAtStr);
        } catch (Exception e) {
            this.createdAt = System.currentTimeMillis();
        }
    }
}
