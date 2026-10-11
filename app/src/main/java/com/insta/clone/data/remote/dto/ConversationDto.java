package com.insta.clone.data.remote.dto;

import com.google.gson.annotations.SerializedName;
import com.insta.clone.data.model.Conversation;
import java.io.Serializable;

/**
 * DTO for RPC get_inbox returning direct message conversation threads.
 */
public class ConversationDto implements Serializable {
    @SerializedName("id")
    private String id;

    @SerializedName("other_user_id")
    private String otherUserId;

    @SerializedName("other_username")
    private String otherUsername;

    @SerializedName("other_full_name")
    private String fullName;

    @SerializedName("other_avatar_url")
    private String otherAvatarUrl;

    @SerializedName("last_message")
    private String lastMessage;

    @SerializedName("last_message_at")
    private String lastMessageAt;

    @SerializedName("unread_count")
    private int unread;

    public ConversationDto() {}

    public Conversation toConversation() {
        Conversation c = new Conversation(
                id != null ? id : "",
                otherUserId != null ? otherUserId : "",
                otherUsername != null ? otherUsername : "",
                otherAvatarUrl != null ? otherAvatarUrl : "",
                lastMessage != null ? lastMessage : "",
                0L,
                unread > 0
        );
        c.setOtherFullName(fullName != null ? fullName : "");
        c.setLastMessageAt(lastMessageAt);
        c.setUnreadCount(unread);
        return c;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOtherUserId() { return otherUserId; }
    public void setOtherUserId(String otherUserId) { this.otherUserId = otherUserId; }

    public String getOtherUsername() { return otherUsername; }
    public void setOtherUsername(String otherUsername) { this.otherUsername = otherUsername; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getOtherAvatarUrl() { return otherAvatarUrl; }
    public void setOtherAvatarUrl(String otherAvatarUrl) { this.otherAvatarUrl = otherAvatarUrl; }

    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }

    public String getLastMessageAt() { return lastMessageAt; }
    public void setLastMessageAt(String lastMessageAt) { this.lastMessageAt = lastMessageAt; }

    public int getUnread() { return unread; }
    public void setUnread(int unread) { this.unread = unread; }
}
