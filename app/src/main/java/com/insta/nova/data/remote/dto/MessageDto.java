package com.insta.nova.data.remote.dto;

import com.google.gson.annotations.SerializedName;
import com.insta.nova.data.model.Message;
import com.insta.nova.data.model.MessageReaction;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO representing messages in the `messages` table.
 *
 * <p>PAGE 17 asks for {@code select=*,message_reactions(user_id,emoji)}, so the
 * embedded reaction rows arrive in the same payload — no second round trip.
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

    @SerializedName("body")
    private String body;

    @SerializedName("image_url")
    private String imageUrl;

    @SerializedName("media_url")
    private String mediaUrl;

    @SerializedName("post_id")
    private String postId;

    @SerializedName("is_read")
    private boolean read;

    /** Some deployments expose the column as {@code read}; both map to isRead. */
    @SerializedName("read")
    private Boolean readAlt;

    @SerializedName("created_at")
    private String createdAt;

    @SerializedName("message_reactions")
    private List<ReactionDto> reactionDtos;

    public MessageDto() {}

    /** Embedded {@code message_reactions(user_id,emoji)} row. */
    public static class ReactionDto implements Serializable {
        @SerializedName("user_id")
        private String userId;
        @SerializedName("emoji")
        private String emoji;

        public String getUserId() { return userId; }
        public String getEmoji() { return emoji; }
    }

    public Message toMessage() {
        String msgText = body != null ? body : (text != null ? text : "");
        String media = mediaUrl != null ? mediaUrl : (imageUrl != null ? imageUrl : "");
        Message m = new Message(
                id != null ? id : "",
                conversationId != null ? conversationId : "",
                senderId != null ? senderId : "",
                msgText,
                media,
                false,
                0L
        );
        m.setImageUrl(media);
        m.setMediaUrl(media);
        m.setPostId(postId);
        m.setRead(readAlt != null ? readAlt : read);
        m.setCreatedAt(createdAt);

        List<MessageReaction> reactions = new ArrayList<>();
        if (reactionDtos != null) {
            for (ReactionDto r : reactionDtos) {
                if (r == null || r.getEmoji() == null) continue;
                reactions.add(new MessageReaction(id, r.getUserId(), r.getEmoji()));
            }
        }
        m.setReactions(reactions);
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

    public boolean isRead() { return readAlt != null ? readAlt : read; }
    public void setRead(boolean read) { this.read = read; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
