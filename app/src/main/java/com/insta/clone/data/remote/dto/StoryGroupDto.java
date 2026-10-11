package com.insta.clone.data.remote.dto;

import com.google.gson.annotations.SerializedName;
import com.insta.clone.data.model.StoryGroup;
import java.io.Serializable;

/**
 * DTO for RPC get_story_groups representing grouped active stories.
 */
public class StoryGroupDto implements Serializable {
    @SerializedName("user_id")
    private String userId;

    @SerializedName("username")
    private String username;

    @SerializedName("avatar_url")
    private String avatarUrl;

    @SerializedName("has_unseen")
    private boolean hasUnseen;

    @SerializedName("latest_story_id")
    private String latest;

    public StoryGroupDto() {}

    public StoryGroup toStoryGroup() {
        return new StoryGroup(
                userId == null ? "" : userId,
                username == null ? "" : username,
                avatarUrl == null ? "" : avatarUrl,
                hasUnseen,
                latest == null ? "" : latest
        );
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public boolean isHasUnseen() { return hasUnseen; }
    public void setHasUnseen(boolean hasUnseen) { this.hasUnseen = hasUnseen; }

    public String getLatest() { return latest; }
    public void setLatest(String latest) { this.latest = latest; }
}
