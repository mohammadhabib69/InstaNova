package com.insta.nova.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.HashMap;
import java.util.Map;

/**
 * Carries a reel's editor state (timed text, volume levels, track) inside the caption.
 *
 * <p>The Supabase {@code posts} table has no jsonb column and {@code create_instagram_post}
 * takes no argument for this, so the block rides at the end of the caption behind a marker.
 * It is stripped by {@code PostDto.toPost()} before anything is displayed, which means every
 * surface sees the author's caption only — the marker never reaches the UI.
 *
 * <p>This is a bridge: once a {@code posts.editor_meta} column exists, the block moves there
 * and this class goes away.
 */
public final class ReelMetaCodec {

    /** Marker starts on its own line so a caption never ends up glued to it. */
    private static final String MARKER = "\n<<reelmeta>>";

    private ReelMetaCodec() {
    }

    /** Appends the meta block to a caption; returns the caption unchanged when empty. */
    @NonNull
    public static String encode(@Nullable String caption, @Nullable Map<String, Object> meta) {
        String base = caption == null ? "" : caption;
        if (meta == null || meta.isEmpty()) return base;
        try {
            return base + MARKER + new Gson().toJson(meta);
        } catch (Exception e) {
            return base;
        }
    }

    /** True when the caption carries an editor block. */
    public static boolean hasMeta(@Nullable String caption) {
        return caption != null && caption.contains(MARKER);
    }

    /** The caption as the author typed it. */
    @NonNull
    public static String strip(@Nullable String caption) {
        if (caption == null) return "";
        int at = caption.indexOf(MARKER);
        return at < 0 ? caption : caption.substring(0, at);
    }

    /** The editor block, or an empty map when there is none. */
    @NonNull
    public static Map<String, Object> parse(@Nullable String caption) {
        if (caption == null) return new HashMap<>();
        int at = caption.indexOf(MARKER);
        if (at < 0) return new HashMap<>();
        String json = caption.substring(at + MARKER.length()).trim();
        try {
            Map<String, Object> parsed = new Gson().fromJson(json,
                    new TypeToken<Map<String, Object>>() { }.getType());
            return parsed == null ? new HashMap<>() : parsed;
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
}
