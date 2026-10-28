package com.insta.nova.data.firebase.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Private identity document: {@code users/{uid}} (ARCHITECTURE §3).
 *
 * <p>Owner-only by rule. It deliberately holds everything that must never be public —
 * FCM tokens, birthday, legacy UUID, settings — because Firestore has no column-level
 * security: the split from {@link PublicProfileDoc} is what keeps those fields private
 * (closes audit finding S4).
 *
 * <p>Timestamps are epoch millis here so the mapping is unit-testable without a Firebase
 * runtime; {@code UserRepository} replaces {@code updatedAt} with a server timestamp on
 * write.
 */
public final class UserDoc {

    public String uid = "";
    /** Supabase {@code profiles.id} — required while Storage paths still use it. */
    public String legacyProfileId = "";
    public String username = "";
    public String fullName = "";
    public String bio = "";
    public String website = "";
    public String avatarUrl = "";
    public boolean verified;
    public boolean isPrivate;
    public String accountType = "personal";
    public String birthday = "";
    public boolean showActivity = true;
    public String whoCanCall = "everyone";
    public long createdAt;
    public long updatedAt;

    public int postsCount;
    public int followersCount;
    public int followingCount;

    public UserDoc() {
    }

    @NonNull
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("uid", str(uid));
        map.put("legacyProfileId", str(legacyProfileId));
        map.put("username", str(username));
        map.put("fullName", str(fullName));
        map.put("bio", bio);
        map.put("website", website);
        map.put("avatarUrl", str(avatarUrl));
        map.put("isVerified", verified);
        map.put("isPrivate", isPrivate);
        map.put("accountType", accountType);
        map.put("birthday", str(birthday));
        map.put("showActivity", showActivity);
        map.put("whoCanCall", whoCanCall);
        map.put("createdAt", createdAt);
        map.put("updatedAt", updatedAt);
        Map<String, Object> counters = new HashMap<>();
        counters.put("posts", postsCount);
        counters.put("followers", followersCount);
        counters.put("following", followingCount);
        map.put("counters", counters);
        return map;
    }

    @NonNull
    public static UserDoc fromMap(@Nullable Map<String, Object> map) {
        UserDoc doc = new UserDoc();
        if (map == null) return doc;
        doc.uid = str(map.get("uid"));
        doc.legacyProfileId = str(map.get("legacyProfileId"));
        doc.username = str(map.get("username"));
        doc.fullName = str(map.get("fullName"));
        doc.bio = str(map.get("bio"));
        doc.website = str(map.get("website"));
        doc.avatarUrl = str(map.get("avatarUrl"));
        doc.verified = bool(map.get("isVerified"));
        doc.isPrivate = bool(map.get("isPrivate"));
        doc.accountType = str(map.get("accountType"));
        doc.birthday = str(map.get("birthday"));
        doc.showActivity = bool(map.get("showActivity"));
        doc.whoCanCall = str(map.get("whoCanCall"));
        doc.createdAt = num(map.get("createdAt"));
        doc.updatedAt = num(map.get("updatedAt"));
        Object counters = map.get("counters");
        if (counters instanceof Map) {
            Map<?, ?> c = (Map<?, ?>) counters;
            doc.postsCount = (int) num(c.get("posts"));
            doc.followersCount = (int) num(c.get("followers"));
            doc.followingCount = (int) num(c.get("following"));
        }
        return doc;
    }

    @NonNull
    static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    static boolean bool(Object value) {
        return value instanceof Boolean && (Boolean) value;
    }

    static long num(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }
}
