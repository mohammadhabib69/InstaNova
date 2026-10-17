package com.insta.nova.data.remote.dto;

import com.google.gson.annotations.SerializedName;

public class CallSignalDto {

    @SerializedName("id")
    public String id;

    @SerializedName("call_id")
    public String callId;

    @SerializedName("sender_id")
    public String senderId;

    @SerializedName("recipient_id")
    public String recipientId;

    @SerializedName("signal_type")
    public String signalType; // 'offer', 'answer', 'ice_candidate', 'reject', 'end'

    @SerializedName("payload")
    public String payload;

    @SerializedName("created_at")
    public String createdAt;
}
