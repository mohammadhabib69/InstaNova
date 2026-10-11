package com.insta.clone.data.remote.dto;

import com.google.gson.annotations.SerializedName;
import com.insta.clone.data.model.Message;
import java.io.Serializable;

/**
 * DTO representing messages in the `messages` table.
 */
public class MessageDto implements Serializable {
    @SerializedName("id")
    private String id;

    @SerializedName("conversation_id")
    private String conversationId;

    @SerializedName("sender_id")
    private String senderId;

    @SerializedName("text")
    private String text;

    @SerializedName("image_url")
    private String imageUrl;

    @SerializedName("post_id")
    private String postId;

    @SerializedName("is_read")
    private boolean read;

    @SerializedName("created_at")
    private String createdAt;

    public MessageDto() {}

    public Message toMessage() {
        Message m = new Message(
                id != null ? id : "",
                conversationId != null ? conversationId : "",
                senderId != null ? senderId : "",
                text != null ? text : "",
                imageUrl != null ? imageUrl : "",
                false,
                0L
        );
        m.setImageUrl(imageUrl);
        m.setPostId(postId);
        m.setRead(read);
        m.setCreatedAt(createdAt);
        return m;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getPostId() { return postId; }
    public void setPostId(String postId) { this.postId = postId; }

    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
