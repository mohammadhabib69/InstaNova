package com.insta.nova.data.firebase.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Story document ({@code stories/{storyId}}), Phase 5.
 *
 * <p>Expiry is a stored field ({@code expiresAt}, epoch millis) rather than a computed
 * rule: queries can filter on it, whereas `request.time + 24h` inside a rule would make
 * every list query un-evaluable. Deleting expired documents needs a scheduled Function.
 *
 * <p>`audience` is denormalised for the same reason as posts: the read rule may only look
 * at the candidate document, so "followers only" is a field, not a join.
 */
public class StoryDoc {

    public static final String AUDIENCE_PUBLIC = "public";
    public static final String AUDIENCE_FOLLOWERS = "followers";
    public static final String AUDIENCE_CLOSE_FRIENDS = "close_friends";

    public String storyId = "";
    public String authorUid = "";
    public String audience = AUDIENCE_PUBLIC;
    public String caption = "";
    public Map<String, Object> media = new HashMap<>();
    public long createdAt;
    public long expiresAt;

    public StoryDoc() {
    }

    @NonNull
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("storyId", storyId);
        map.put("authorUid", authorUid);
        map.put("audience", audience == null ? AUDIENCE_PUBLIC : audience);
        map.put("caption", caption == null ? "" : caption);
        map.put("media", media == null ? new HashMap<>() : media);
        map.put("createdAt", createdAt);
        map.put("expiresAt", expiresAt);
        return map;
    }

    @NonNull
    @SuppressWarnings("unchecked")
    public static StoryDoc fromMap(@Nullable Map<String, Object> map) {
        StoryDoc story = new StoryDoc();
        if (map == null) {
            return story;
        }
        story.storyId = UserDoc.str(map.get("storyId"));
        story.authorUid = UserDoc.str(map.get("authorUid"));
        String audience = UserDoc.str(map.get("audience"));
        story.audience = audience.isEmpty() ? AUDIENCE_PUBLIC : audience;
        story.caption = UserDoc.str(map.get("caption"));
        Object raw = map.get("media");
        if (raw instanceof Map) {
            story.media = (Map<String, Object>) raw;
        }
        story.createdAt = UserDoc.num(map.get("createdAt"));
        story.expiresAt = UserDoc.num(map.get("expiresAt"));
        return story;
    }
}
