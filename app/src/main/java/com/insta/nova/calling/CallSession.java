package com.insta.nova.calling;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.Serializable;

public class CallSession implements Serializable {

    public static final String TYPE_AUDIO = "audio";
    public static final String TYPE_VIDEO = "video";

    public static final String STATUS_RINGING = "ringing";
    public static final String STATUS_ACCEPTED = "accepted";
    public static final String STATUS_REJECTED = "rejected";
    public static final String STATUS_ENDED = "ended";
    public static final String STATUS_MISSED = "missed";
    public static final String STATUS_CANCELLED = "cancelled";
    public static final String STATUS_FAILED = "failed";

    public String callId;
    public String conversationId;
    public String callerId;
    public String callerUsername;
    public String callerAvatar;
    public String recipientId;
    public String recipientUsername;
    public String recipientAvatar;
    public String callType; // "audio" or "video"
    public String status;
    public boolean isOutgoing;
    public long startedAtMs;
    public int durationSeconds;

    public CallSession() {
        this.status = STATUS_RINGING;
        this.startedAtMs = System.currentTimeMillis();
    }

    public CallSession(String callId, String conversationId, String callerId, String recipientId, String callType, boolean isOutgoing) {
        this.callId = callId;
        this.conversationId = conversationId;
        this.callerId = callerId;
        this.recipientId = recipientId;
        this.callType = callType;
        this.isOutgoing = isOutgoing;
        this.status = STATUS_RINGING;
        this.startedAtMs = System.currentTimeMillis();
    }

    public boolean isVideo() {
        return TYPE_VIDEO.equalsIgnoreCase(callType);
    }
}
