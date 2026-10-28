package com.insta.nova.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/**
 * One row of {@code post_tags} as the feed reads it.
 *
 * <p>{@code x} and {@code y} are the 0..1 position the composer stored, so the marker lands on
 * the same place of the photo for every viewer regardless of screen size. The tagged account's
 * name comes from the embedded profiles row instead of a second request.
 */
public class PostTagDto {

    @SerializedName("user_id")
    public String userId;

    public float x;
    public float y;

    @SerializedName("profiles")
    public Profile profile;

    public static class Profile {
        public String username;
    }

    public String username() {
        return profile == null || profile.username == null ? "" : profile.username;
    }
}
