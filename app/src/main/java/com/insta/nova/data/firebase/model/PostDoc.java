package com.insta.nova.data.firebase.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Post document ({@code posts/{postId}}), Phase 4.
 *
 * <p>Media files stay in Supabase Storage; this document carries only the metadata the
 * feed needs, so a feed page costs one read per post and never touches Storage.
 * Counters start at zero and are only ever moved by one step (rule-enforced).
 */
public class PostDoc {

    public String postId = "";
    public String authorUid = "";
    public String caption = "";
    public String visibility = "public";
    /** Denormalised read gate: "public" or "followers" (see rules comment). */
    public String audience = "public";
    public String location = "";
    public List<Map<String, Object>> media = new ArrayList<>();
    public long likesCount;
    public long commentsCount;
    public long createdAt;
    public long updatedAt;

    public PostDoc() {
    }

    /**
     * Adds a media reference through {@link MediaRef} so posts and profiles share one
     * metadata contract (mediaId, bucket, path, url, type, owner, legacy id).
     */
    public void addMedia(@NonNull String mediaId, @NonNull String bucket, @NonNull String path,
                         @NonNull String url, @NonNull String mediaType) {
        MediaRef ref = new MediaRef();
        ref.mediaId = mediaId;
        ref.bucket = bucket;
        ref.path = path;
        ref.url = url;
        ref.mediaType = mediaType;
        ref.ownerUid = authorUid;
        ref.ownerLegacyId = path.contains("/") ? path.substring(0, path.indexOf('/')) : "";
        media.add(ref.toMap());
    }

    @NonNull
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("postId", postId);
        map.put("authorUid", authorUid);
        map.put("caption", caption == null ? "" : caption);
        map.put("visibility", visibility == null ? "public" : visibility);
        map.put("audience", audience == null ? "public" : audience);
        map.put("location", location == null ? "" : location);
        map.put("media", media);
        map.put("likesCount", likesCount);
        map.put("commentsCount", commentsCount);
        map.put("createdAt", createdAt);
        map.put("updatedAt", updatedAt);
        return map;
    }

    @NonNull
    @SuppressWarnings("unchecked")
    public static PostDoc fromMap(@Nullable Map<String, Object> map) {
        PostDoc post = new PostDoc();
        if (map == null) {
            return post;
        }
        post.postId = UserDoc.str(map.get("postId"));
        post.authorUid = UserDoc.str(map.get("authorUid"));
        post.caption = UserDoc.str(map.get("caption"));
        post.visibility = map.get("visibility") == null ? "public" : UserDoc.str(map.get("visibility"));
        post.audience = map.get("audience") == null ? "public" : UserDoc.str(map.get("audience"));
        post.location = UserDoc.str(map.get("location"));
        post.likesCount = UserDoc.num(map.get("likesCount"));
        post.commentsCount = UserDoc.num(map.get("commentsCount"));
        post.createdAt = UserDoc.num(map.get("createdAt"));
        post.updatedAt = UserDoc.num(map.get("updatedAt"));
        Object rawMedia = map.get("media");
        if (rawMedia instanceof List) {
            post.media = (List<Map<String, Object>>) rawMedia;
        }
        return post;
    }
}
