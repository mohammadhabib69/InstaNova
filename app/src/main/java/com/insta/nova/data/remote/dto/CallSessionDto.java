package com.insta.nova.data.remote.dto;

import com.google.gson.annotations.SerializedName;

public class CallSessionDto {

    @SerializedName("id")
    public String id;

    @SerializedName("conversation_id")
    public String conversationId;

    @SerializedName("caller_id")
    public String callerId;

    @SerializedName("recipient_id")
    public String recipientId;

    @SerializedName("call_type")
    public String callType;

    @SerializedName("status")
    public String status;

    @SerializedName("started_at")
    public String startedAt;

    @SerializedName("answered_at")
    public String answeredAt;

    @SerializedName("ended_at")
    public String endedAt;

    @SerializedName("duration_seconds")
    public int durationSeconds;

    @SerializedName("ended_by")
    public String endedBy;

    @SerializedName("created_at")
    public String createdAt;

    @SerializedName("updated_at")
    public String updatedAt;

    // Joined caller profile if queried with PostgREST resource embedding
    @SerializedName("caller")
    public ProfileDto caller;

    // Joined recipient profile if queried with PostgREST resource embedding
    @SerializedName("recipient")
    public ProfileDto recipient;
}
