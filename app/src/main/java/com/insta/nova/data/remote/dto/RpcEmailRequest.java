package com.insta.nova.data.remote.dto;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/**
 * Payload for RPC get_email_by_username(p_username text).
 */
public class RpcEmailRequest implements Serializable {

    @SerializedName("p_username")
    private String username;

    public RpcEmailRequest() {}

    public RpcEmailRequest(String username) {
        this.username = username;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
}
