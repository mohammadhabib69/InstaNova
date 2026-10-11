package com.insta.clone.util;

import android.util.Log;
import com.insta.clone.BuildConfig;

/**
 * Central helper for normalizing media URLs stored in Supabase.
 * Ensures relative paths, missing '/public/' URLs, and unsafe characters are corrected.
 */
public final class MediaUrl {

    private static final String TAG = "InstaGlide";

    private MediaUrl() {}

    /**
     * Normalizes a media URL or path from the database.
     *
     * @param bucket The storage bucket name (e.g. "posts", "avatars", "stories", "reels", "chat").
     * @param valueFromDb The string stored in the database (could be relative, absolute, or legacy).
     * @return The fully qualified public Supabase storage URL.
     */
    public static String normalize(String bucket, String valueFromDb) {
        if (valueFromDb == null) return "";
        String val = valueFromDb.trim();
        if (val.isEmpty()) return "";

        String supabaseBase = BuildConfig.SUPABASE_URL.replaceAll("/+$", "");
        String b = (bucket != null && !bucket.isEmpty()) ? bucket : "posts";
        String normalized;

        if (val.startsWith("content://") || val.startsWith("file://") || val.startsWith("android.resource://")) {
            return val;
        }

        if (val.startsWith("http://") || val.startsWith("https://")) {
            // Check if it's a Supabase storage URL that lacks /public/
            if (val.contains("/storage/v1/object/")) {
                if (!val.contains("/storage/v1/object/public/")) {
                    val = val.replace("/storage/v1/object/authenticated/", "/storage/v1/object/public/");
                    val = val.replace("/storage/v1/object/", "/storage/v1/object/public/");
                }
            } else if (val.contains("/object/authenticated/")) {
                val = val.replace("/object/authenticated/", "/storage/v1/object/public/");
            } else if (val.contains("/object/") && !val.contains("/object/public/")) {
                val = val.replace("/object/", "/storage/v1/object/public/");
            }
            normalized = val;
        } else {
            // It's a relative path -> prepend public bucket URL
            String path = val.replaceAll("^/+", "");
            if (path.startsWith(b + "/")) {
                path = path.substring(b.length() + 1);
            }
            normalized = supabaseBase + "/storage/v1/object/public/" + b + "/" + path;
        }

        // Clean up any double /public/public/
        normalized = normalized.replace("/public/public/", "/public/");

        return safeEncodeUrl(normalized);
    }

    private static String safeEncodeUrl(String url) {
        if (url == null) return "";
        try {
            // Encode spaces and unsafe characters without re-encoding percent sequences
            return url.replace(" ", "%20");
        } catch (Exception e) {
            Log.w(TAG, "Error encoding URL: " + url, e);
            return url;
        }
    }
}
