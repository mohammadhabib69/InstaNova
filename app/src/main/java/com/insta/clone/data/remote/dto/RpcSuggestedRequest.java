package com.insta.clone.data.remote.dto;

import java.io.Serializable;

/**
 * Payload for RPC get_suggested_users.
 */
public class RpcSuggestedRequest implements Serializable {
    private int limit;
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
