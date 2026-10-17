package com.insta.nova.data.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Domain model representing a user's grouped stories for Story tray.
 */
public class StoryGroup implements Serializable {
    private String userId;
    private String username;
    private String userAvatar;
    private boolean isOwner;
    private boolean hasUnseen;
    private boolean hasActiveStory;
    private String latestStoryId;
    private List<Story> stories;

    public StoryGroup() {
        this.stories = new ArrayList<>();
    }

    public StoryGroup(String userId, String username, String userAvatar, boolean hasUnseen, String latestStoryId) {
        this.userId = userId;
        this.username = username;
        this.userAvatar = userAvatar;
        this.hasUnseen = hasUnseen;
        this.latestStoryId = latestStoryId;
        this.isOwner = false;
        this.hasActiveStory = true;
        this.stories = new ArrayList<>();
    }

    public StoryGroup(String userId, String username, String userAvatar, boolean isOwner,
                      boolean hasUnseen, List<Story> stories) {
        this.userId = userId;
        this.username = username;
        this.userAvatar = userAvatar;
        this.isOwner = isOwner;
        this.hasUnseen = hasUnseen;
        this.hasActiveStory = stories != null && !stories.isEmpty();
        this.stories = stories != null ? stories : new ArrayList<>();
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getUserAvatar() { return userAvatar; }
    public void setUserAvatar(String userAvatar) { this.userAvatar = userAvatar; }

    public String getAvatarUrl() { return userAvatar; }
    public void setAvatarUrl(String avatarUrl) { this.userAvatar = avatarUrl; }

    public boolean isOwner() { return isOwner; }
    public void setOwner(boolean owner) { isOwner = owner; }

    public boolean hasUnseen() { return hasUnseen; }
    public void setHasUnseen(boolean hasUnseen) { this.hasUnseen = hasUnseen; }

    /** True only for a group backed by an unexpired active story. */
    public boolean hasActiveStory() { return hasActiveStory; }
    public void setHasActiveStory(boolean hasActiveStory) { this.hasActiveStory = hasActiveStory; }

    public String getLatestStoryId() { return latestStoryId; }
    public void setLatestStoryId(String latestStoryId) { this.latestStoryId = latestStoryId; }

    public List<Story> getStories() { return stories; }
    public void setStories(List<Story> stories) { this.stories = stories; }
}
