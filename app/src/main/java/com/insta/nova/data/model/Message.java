package com.insta.nova.data.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

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

    /** Raw PostgREST timestamp, used verbatim as the keyset-pagination cursor. */
    private String createdAtRaw;

    /** PAGE 17: reactions embedded by {@code message_reactions(user_id,emoji)}. */
    private List<MessageReaction> reactions = new ArrayList<>();

    /** Optimistic send state: bubble drawn at 60% alpha until the server echoes it. */
    private boolean pending;
    /** Optimistic send failed: red ic_error + tap to retry. */
    private boolean failed;
    /** Local content URI shown while an image is compressed/uploaded. */
    private String localImageUri;

    private String messageType = "text";
    private String voicePath;
    private long voiceDurationMs;
    private String replyToId;
    private String replyToSender;
    private String replyToText;
    private long callDurationSec;
    /**
     * Prompt J: terminal status of the originating {@code calls} row
     * (connected / missed / no_answer / declined / ended). Null on entries
     * written before Prompt J, which render as the legacy "call ended".
     */
    private String callOutcome;

    public String getMessageType() { return messageType != null ? messageType : "text"; }
    public void setMessageType(String messageType) { this.messageType = messageType; }

    public String getVoicePath() { return voicePath; }
    public void setVoicePath(String voicePath) { this.voicePath = voicePath; }

    public long getVoiceDurationMs() { return voiceDurationMs; }
    public void setVoiceDurationMs(long voiceDurationMs) { this.voiceDurationMs = voiceDurationMs; }

    public String getReplyToId() { return replyToId; }
    public void setReplyToId(String replyToId) { this.replyToId = replyToId; }

    public String getReplyToSender() { return replyToSender; }
    public void setReplyToSender(String replyToSender) { this.replyToSender = replyToSender; }

    public String getReplyToText() { return replyToText; }
    public void setReplyToText(String replyToText) { this.replyToText = replyToText; }

    public long getCallDurationSec() { return callDurationSec; }
    public void setCallDurationSec(long callDurationSec) { this.callDurationSec = callDurationSec; }

    public String getCallOutcome() { return callOutcome; }
    public void setCallOutcome(String callOutcome) { this.callOutcome = callOutcome; }

    public boolean hasReply() {
        return replyToText != null && !replyToText.isEmpty();
    }

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
        this.createdAtRaw = createdAtStr;
        this.createdAt = com.insta.nova.util.TimeUtils.parseTimestamp(createdAtStr);
        if (this.createdAt <= 0L) this.createdAt = System.currentTimeMillis();
    }

    public String getCreatedAtRaw() { return createdAtRaw; }
    public void setCreatedAtRaw(String createdAtRaw) { this.createdAtRaw = createdAtRaw; }

    public List<MessageReaction> getReactions() {
        return reactions != null ? reactions : new ArrayList<>();
    }
    public void setReactions(List<MessageReaction> reactions) {
        this.reactions = reactions != null ? reactions : new ArrayList<>();
    }

    public boolean isPending() { return pending; }
    public void setPending(boolean pending) { this.pending = pending; }

    public boolean isFailed() { return failed; }
    public void setFailed(boolean failed) { this.failed = failed; }

    public String getLocalImageUri() { return localImageUri; }
    public void setLocalImageUri(String localImageUri) { this.localImageUri = localImageUri; }

    /** True when this message can carry an image (uploaded or still uploading). */
    public boolean hasImage() {
        String url = getImageUrl();
        return (url != null && !url.isEmpty()) || (localImageUri != null && !localImageUri.isEmpty());
    }
}
