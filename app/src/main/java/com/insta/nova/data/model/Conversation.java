package com.insta.nova.data.model;

import java.io.Serializable;

/**
 * Domain model representing a direct message conversation thread.
 */
public class Conversation implements Serializable {
    private String id;
    private String otherUserId;
    private String otherUsername;
    private String otherFullName;
    private String otherAvatarUrl;
    private String lastMessage;
    private String lastMessageAt;
    private long lastMessageTimestamp;
    private boolean isUnread;
    private int unreadCount;

    public Conversation() {}

    public Conversation(String id, String otherUserId, String otherUsername,
                        String otherAvatarUrl, String lastMessage,
                        long lastMessageTimestamp, boolean isUnread) {
        this.id = id;
        this.otherUserId = otherUserId;
        this.otherUsername = otherUsername;
        this.otherAvatarUrl = otherAvatarUrl;
        this.lastMessage = lastMessage;
        this.lastMessageTimestamp = lastMessageTimestamp;
        this.isUnread = isUnread;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOtherUserId() { return otherUserId; }
    public void setOtherUserId(String otherUserId) { this.otherUserId = otherUserId; }

    public String getOtherUsername() { return otherUsername; }
    public void setOtherUsername(String otherUsername) { this.otherUsername = otherUsername; }

    public String getOtherFullName() { return otherFullName; }
    public void setOtherFullName(String otherFullName) { this.otherFullName = otherFullName; }

    public String getOtherAvatarUrl() { return otherAvatarUrl; }
    public void setOtherAvatarUrl(String otherAvatarUrl) { this.otherAvatarUrl = otherAvatarUrl; }

    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }

    public String getLastMessageAt() { return lastMessageAt; }
    public void setLastMessageAt(String lastMessageAt) { this.lastMessageAt = lastMessageAt; }

    public long getLastMessageTimestamp() { return lastMessageTimestamp; }
    public void setLastMessageTimestamp(long lastMessageTimestamp) { this.lastMessageTimestamp = lastMessageTimestamp; }

    public boolean isUnread() { return isUnread; }
    public void setUnread(boolean unread) { isUnread = unread; }

    public int getUnreadCount() { return unreadCount; }
    public void setUnreadCount(int unreadCount) { this.unreadCount = unreadCount; }
}
