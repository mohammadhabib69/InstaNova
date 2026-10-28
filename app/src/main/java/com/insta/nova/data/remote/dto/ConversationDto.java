package com.insta.nova.data.remote.dto;

import com.google.gson.annotations.SerializedName;
import com.insta.nova.data.model.Conversation;
import java.io.Serializable;

/**
 * DTO for RPC get_inbox returning direct message conversation threads.
 */
public class ConversationDto implements Serializable {
    @SerializedName("id")
    private String id;

    @SerializedName("conversation_id")
    private String conversationId;

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

    @SerializedName("photo_count")
    private int photoCount;

    /**
     * True when this thread is a request for the viewer: the last sender does not
     * follow them and they have not accepted it yet. Decided server-side from the
     * viewer's own membership row, so it is already per-viewer and must not be
     * re-derived on the device.
     */
    @SerializedName("is_request")
    private Boolean isRequest;

    public ConversationDto() {}

    public Conversation toConversation() {
        String cid = conversationId != null ? conversationId : (id != null ? id : "");
        Conversation c = new Conversation(
                cid,
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
        c.setPhotoCount(photoCount);
        c.setRequest(Boolean.TRUE.equals(isRequest));
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
