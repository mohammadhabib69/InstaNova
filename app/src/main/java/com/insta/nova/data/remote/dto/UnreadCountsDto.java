package com.insta.nova.data.remote.dto;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/**
 * DTO for RPC get_unread_counts.
 */
public class UnreadCountsDto implements Serializable {
    @SerializedName("notifications")
    private int notifications;

    @SerializedName("direct_messages")
    private int directMessages;

    @SerializedName("messages")
    private Integer messages;

    public UnreadCountsDto() {}

    public UnreadCountsDto(int notifications, int directMessages) {
        this.notifications = notifications;
        this.directMessages = directMessages;
    }

    public int getNotifications() { return notifications; }
    public void setNotifications(int notifications) { this.notifications = notifications; }

    public int getDirectMessages() { return messages != null ? messages : directMessages; }
    public void setDirectMessages(int directMessages) { this.directMessages = directMessages; }
}
