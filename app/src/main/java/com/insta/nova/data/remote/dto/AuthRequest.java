package com.insta.nova.data.remote.dto;

import java.io.Serializable;
import java.util.Map;

/**
 * Payload for Supabase GoTrue signup and login requests.
 */
public class AuthRequest implements Serializable {
    private String email;
    private String password;
    private Map<String, Object> data;

    public AuthRequest() {}

    public AuthRequest(String email, String password) {
        this.email = email;
        this.password = password;
    }

    public AuthRequest(String email, String password, Map<String, Object> data) {
        this.email = email;
        this.password = password;
        this.data = data;
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { this.data = data; }
}
