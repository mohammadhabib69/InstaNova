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

    /**
     * Photos in the trailing burst when the newest message is media. 0 for a text tail;
     * 1 for a single photo. Lets the inbox say "4 photos" instead of "Sent a photo".
     */
    private int photoCount;

    /** True when the other person is not an approved follower and this is unaccepted. */
    private boolean isRequest;

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

    public boolean isRequest() { return isRequest; }
    public void setRequest(boolean request) { this.isRequest = request; }

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

    public int getPhotoCount() { return photoCount; }
    public void setPhotoCount(int photoCount) { this.photoCount = photoCount; }

    public int getUnreadCount() { return unreadCount; }
    public void setUnreadCount(int unreadCount) { this.unreadCount = unreadCount; }
}
