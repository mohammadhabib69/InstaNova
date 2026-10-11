package com.insta.clone.data.remote.dto;

import com.google.gson.annotations.SerializedName;
import com.insta.clone.data.model.Profile;
import java.io.Serializable;

/**
 * DTO representing profile rows in the `profiles` table.
 */
public class ProfileDto implements Serializable {
    @SerializedName("id")
    private String id;

    @SerializedName("username")
    private String username;

    @SerializedName("full_name")
    private String fullName;

    @SerializedName("bio")
    private String bio;

    @SerializedName("avatar_url")
    private String avatarUrl;

    @SerializedName("website")
    private String website;

    @SerializedName("is_verified")
    private boolean isVerified;

    @SerializedName("posts_count")
    private int postsCount;

    @SerializedName("followers_count")
    private int followersCount;

    @SerializedName("following_count")
    private int followingCount;

    @SerializedName("is_following")
    private boolean isFollowing;

    @SerializedName("birthday")
    private String birthday;

    public ProfileDto() {}

    public Profile toProfile() {
        Profile p = new Profile(
                id != null ? id : "",
                username != null ? username : "",
                fullName != null ? fullName : "",
                bio != null ? bio : "",
                avatarUrl != null ? avatarUrl : "",
                website != null ? website : "",
                isVerified,
                postsCount,
                followersCount,
                followingCount,
                isFollowing
        );
        p.setBirthday(birthday);
        return p;
    }

    public Profile toDomain() { return toProfile(); }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }

    public boolean isVerified() { return isVerified; }
    public void setVerified(boolean verified) { isVerified = verified; }

    public int getPostsCount() { return postsCount; }
    public void setPostsCount(int postsCount) { this.postsCount = postsCount; }

    public int getFollowersCount() { return followersCount; }
    public void setFollowersCount(int followersCount) { this.followersCount = followersCount; }

    public int getFollowingCount() { return followingCount; }
    public void setFollowingCount(int followingCount) { this.followingCount = followingCount; }

    public boolean isFollowing() { return isFollowing; }
    public void setFollowing(boolean following) { isFollowing = following; }

    public String getBirthday() { return birthday; }
    public void setBirthday(String birthday) { this.birthday = birthday; }
}
