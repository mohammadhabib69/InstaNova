package com.insta.nova.data.remote.dto;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/**
 * Payload for RPC get_suggested_users.
 */
public class RpcSuggestedRequest implements Serializable {

    @SerializedName("p_limit")
    private int limit;

    @SerializedName("p_exclude_user_id")
    private String exclude_user_id;

    public RpcSuggestedRequest() {}

    public RpcSuggestedRequest(int limit, String exclude_user_id) {
        this.limit = limit;
        this.exclude_user_id = exclude_user_id;
    }

    public RpcSuggestedRequest(int limit) {
        this(limit, null);
    }

    public int getLimit() { return limit; }
    public void setLimit(int limit) { this.limit = limit; }

    public String getExclude_user_id() { return exclude_user_id; }
    public void setExclude_user_id(String exclude_user_id) { this.exclude_user_id = exclude_user_id; }
}
