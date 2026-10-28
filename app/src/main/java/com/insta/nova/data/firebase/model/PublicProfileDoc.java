package com.insta.nova.data.firebase.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Signed-in-readable projection: {@code publicProfiles/{uid}} (ARCHITECTURE §3).
 *
 * <p>Contains only fields another user is allowed to see. No FCM tokens, no birthday, no
 * legacy UUID, no settings — that is the whole point of the split. Anything added here is
 * effectively public to every signed-in user, so add fields deliberately.
 */
public final class PublicProfileDoc {

    public String uid;
    public String username;
    /** Lowercased mirror of {@link #username}: Firestore has no case-insensitive match. */
    public String usernameLower;
    public String fullName;
    public String avatarUrl;
    public String bio = "";
    public String website = "";
    public boolean verified;
    public boolean isPrivate;
    public int followersCount;
    public int followingCount;
    public int postsCount;

    public PublicProfileDoc() {
    }

    /** Builds the public projection from the private document. */
    @NonNull
    public static PublicProfileDoc from(UserDoc user) {
        PublicProfileDoc pub = new PublicProfileDoc();
        if (user == null) return pub;
        pub.uid = user.uid;
        pub.username = user.username;
        pub.usernameLower = com.insta.nova.data.firebase.Validation.normalizeUsername(user.username);
        pub.fullName = user.fullName;
        pub.avatarUrl = user.avatarUrl;
        pub.bio = user.bio;
        pub.website = user.website;
        pub.verified = user.verified;
        pub.isPrivate = user.isPrivate;
        pub.followersCount = user.followersCount;
        pub.followingCount = user.followingCount;
        pub.postsCount = user.postsCount;
        return pub;
    }

    @NonNull
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("uid", uid);
        map.put("username", username);
        map.put("usernameLower", usernameLower == null || usernameLower.isEmpty()
                ? com.insta.nova.data.firebase.Validation.normalizeUsername(username)
                : usernameLower);
        map.put("fullName", fullName);
        map.put("avatarUrl", avatarUrl);
        map.put("bio", bio);
        map.put("website", website);
        map.put("isVerified", verified);
        map.put("isPrivate", isPrivate);
        map.put("followersCount", followersCount);
        map.put("followingCount", followingCount);
        map.put("postsCount", postsCount);
        return map;
    }

    @NonNull
    public static PublicProfileDoc fromMap(@Nullable Map<String, Object> map) {
        PublicProfileDoc pub = new PublicProfileDoc();
        if (map == null) return pub;
        pub.uid = UserDoc.str(map.get("uid"));
        pub.username = UserDoc.str(map.get("username"));
        pub.usernameLower = UserDoc.str(map.get("usernameLower")).isEmpty()
                ? com.insta.nova.data.firebase.Validation.normalizeUsername(pub.username)
                : UserDoc.str(map.get("usernameLower"));
        pub.fullName = UserDoc.str(map.get("fullName"));
        pub.avatarUrl = UserDoc.str(map.get("avatarUrl"));
        pub.bio = UserDoc.str(map.get("bio"));
        pub.website = UserDoc.str(map.get("website"));
        pub.verified = UserDoc.bool(map.get("isVerified"));
        pub.isPrivate = UserDoc.bool(map.get("isPrivate"));
        pub.followersCount = (int) UserDoc.num(map.get("followersCount"));
        pub.followingCount = (int) UserDoc.num(map.get("followingCount"));
        pub.postsCount = (int) UserDoc.num(map.get("postsCount"));
        return pub;
    }
}
