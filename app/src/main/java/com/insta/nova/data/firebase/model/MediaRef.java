package com.insta.nova.data.firebase.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Reference to a file that lives in Supabase Storage (tasks P4-07, P4-08, P4-13).
 *
 * <p>Firestore stores the *reference*, never the bytes. The {@code url} is kept verbatim
 * for existing rows (so old posts keep rendering) and {@link #publicUrl(String)} can
 * rebuild it from {@code bucket} + {@code path} for new ones.
 *
 * <p>Ownership is derived from the path, not from a field: Storage paths are
 * {@code {legacyProfileId}/{file}}, so an object belongs to whoever owns that first
 * segment. That is what lets the migration verify every reference without trusting the
 * client (P4-13).
 */
public class MediaRef {

    public String mediaId = "";
    public String ownerUid = "";
    /** Supabase {@code profiles.id} — the first path segment, not the Firebase uid. */
    public String ownerLegacyId = "";
    public String bucket = "posts";
    public String path = "";
    public String url = "";
    public String mediaType = "image";
    public String mimeType = "";
    public long size;
    public long createdAt;

    public MediaRef() {
    }

    /** True when {@code path} sits under the given legacy profile id. */
    public boolean isOwnedByLegacyId(@Nullable String legacyProfileId) {
        if (legacyProfileId == null || legacyProfileId.isEmpty() || path == null || path.isEmpty()) {
            return false;
        }
        // Prefix + separator, so "abc" cannot claim "abcd/file.jpg".
        return path.startsWith(legacyProfileId + "/");
    }

    /** Public Storage URL. Existing {@link #url} wins when present. */
    @NonNull
    public String publicUrl(@NonNull String supabaseBaseUrl) {
        if (url != null && !url.isEmpty()) {
            return url;
        }
        String base = supabaseBaseUrl.replaceAll("/+$", "");
        return base + "/storage/v1/object/public/" + bucket + "/" + path;
    }

    /**
     * Splits a public Storage URL into bucket + path. Returns null for anything that is not
     * a Storage URL (content://, file://, third-party hosts) so callers can skip it instead
     * of storing a broken reference.
     */
    @Nullable
    public static MediaRef parse(@Nullable String url, @NonNull String fallbackBucket) {
        if (url == null) {
            return null;
        }
        String trimmed = url.trim();
        int marker = trimmed.indexOf("/storage/v1/object/");
        if (marker < 0) {
            return null;
        }
        String rest = trimmed.substring(marker + "/storage/v1/object/".length());
        if (rest.startsWith("public/")) {
            rest = rest.substring("public/".length());
        } else if (rest.startsWith("authenticated/")) {
            rest = rest.substring("authenticated/".length());
        }
        int slash = rest.indexOf('/');
        if (slash <= 0 || slash == rest.length() - 1) {
            return null;
        }
        MediaRef ref = new MediaRef();
        ref.bucket = rest.substring(0, slash);
        ref.path = rest.substring(slash + 1);
        ref.url = trimmed;
        ref.ownerLegacyId = ref.path.contains("/") ? ref.path.substring(0, ref.path.indexOf('/')) : "";
        String lower = ref.path.toLowerCase();
        ref.mediaType = lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".webm")
                ? "video" : "image";
        ref.mediaId = ref.bucket + ":" + ref.path;
        return ref;
    }

    @NonNull
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("mediaId", mediaId == null ? "" : mediaId);
        map.put("ownerUid", ownerUid == null ? "" : ownerUid);
        map.put("ownerLegacyId", ownerLegacyId == null ? "" : ownerLegacyId);
        map.put("bucket", bucket == null ? "posts" : bucket);
        map.put("path", path == null ? "" : path);
        map.put("url", url == null ? "" : url);
        map.put("mediaType", mediaType == null ? "image" : mediaType);
        map.put("mimeType", mimeType == null ? "" : mimeType);
        map.put("size", size);
        map.put("createdAt", createdAt);
        return map;
    }

    @NonNull
    @SuppressWarnings("unchecked")
    public static MediaRef fromMap(@Nullable Map<String, Object> map) {
        MediaRef ref = new MediaRef();
        if (map == null) {
            return ref;
        }
        ref.mediaId = UserDoc.str(map.get("mediaId"));
        ref.ownerUid = UserDoc.str(map.get("ownerUid"));
        ref.ownerLegacyId = UserDoc.str(map.get("ownerLegacyId"));
        String bucket = UserDoc.str(map.get("bucket"));
        ref.bucket = bucket.isEmpty() ? "posts" : bucket;
        ref.path = UserDoc.str(map.get("path"));
        ref.url = UserDoc.str(map.get("url"));
        String type = UserDoc.str(map.get("mediaType"));
        ref.mediaType = type.isEmpty() ? "image" : type;
        ref.mimeType = UserDoc.str(map.get("mimeType"));
        ref.size = UserDoc.num(map.get("size"));
        ref.createdAt = UserDoc.num(map.get("createdAt"));
        if (ref.ownerLegacyId.isEmpty() && ref.path.contains("/")) {
            ref.ownerLegacyId = ref.path.substring(0, ref.path.indexOf('/'));
        }
        return ref;
    }
}
