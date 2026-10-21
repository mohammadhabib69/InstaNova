package com.insta.nova.data.model;

import java.io.Serializable;

/**
 * One emoji reaction on a chat message (PAGE 17).
 *
 * <p>Backed by {@code public.message_reactions} — one row per
 * {@code (message_id, user_id)}, so a user has at most one reaction per message.
 */
public class MessageReaction implements Serializable {

    private String messageId;
    private String userId;
    private String emoji;

    public MessageReaction() {}

    public MessageReaction(String messageId, String userId, String emoji) {
        this.messageId = messageId;
        this.userId = userId;
        this.emoji = emoji;
    }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getEmoji() { return emoji; }
    public void setEmoji(String emoji) { this.emoji = emoji; }
}
