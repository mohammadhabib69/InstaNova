package com.insta.nova.util;

import android.util.Log;
import com.insta.nova.BuildConfig;

/**
 * Central helper for normalizing media URLs stored in Supabase.
 * Ensures relative paths, missing '/public/' URLs, and unsafe characters are corrected.
 */
public final class MediaUrl {

    private static final String TAG = "InstaGlide";

    /** URL marker for the RLS-checked object endpoint. */
    private static final String AUTH_MARKER = "/storage/v1/object/authenticated/";
    private static final String PUBLIC_MARKER = "/storage/v1/object/public/";

    /**
     * Buckets with {@code public = false}. Their objects are only reachable through the
     * authenticated endpoint, where the storage RLS policy on the owning table decides who
     * may fetch the bytes.
     *
     * <p>Every bucket is on this list: media is readable by a signed-in user under a policy,
     * never by anyone who happens to hold the URL. A URL that still says {@code /public/}
     * (rows written before the flip, or an upload helper that builds one) is rewritten by
     * {@link com.insta.nova.data.remote.StoragePrivacyInterceptor} on the way out, so the
     * stored strings did not have to be migrated.
     */
    public static final java.util.Set<String> PRIVATE_BUCKETS =
            java.util.Collections.unmodifiableSet(new java.util.LinkedHashSet<>(java.util.Arrays.asList(
                    "stories", "posts", "reels", "nova", "chat", "avatars")));

    private MediaUrl() {}

    public static boolean isPrivate(String bucket) {
        return bucket != null && PRIVATE_BUCKETS.contains(bucket.toLowerCase(java.util.Locale.US));
    }

    /**
     * Rewrites a storage URL from the public endpoint to the authenticated one when its
     * bucket is private. Anything else — another host, an already-authenticated URL, a
     * local content:// URI — is returned unchanged.
     */
    public static String toAuthenticated(String url) {
        if (url == null) return null;
        int marker = url.indexOf(PUBLIC_MARKER);
        if (marker < 0) return url;
        String rest = url.substring(marker + PUBLIC_MARKER.length());
        int slash = rest.indexOf('/');
        if (slash <= 0) return url;
        if (!isPrivate(rest.substring(0, slash))) return url;
        return url.substring(0, marker) + AUTH_MARKER + rest;
    }

    /**
     * The headers Supabase Storage needs on a private object: the project key identifies the
     * project, the bearer token is what the RLS policy reads. Used where the request does not
     * go through the shared OkHttp client (ExoPlayer's own data source).
     */
    /**
     * Data source factory for a private-bucket video.
     *
     * <p>The bearer token is resolved inside {@code createDataSource()}, which ExoPlayer calls
     * on its own loading thread. Doing it eagerly at the call site would run
     * {@code Tasks.await} on the main thread — that throws, the token silently comes back
     * null, and the request goes out with the anon key as the bearer, which the storage API
     * answers with HTTP 400.
     */
    public static androidx.media3.datasource.DataSource.Factory streamFactory() {
        return () -> new androidx.media3.datasource.DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(streamHeaders())
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(20000)
                .createDataSource();
    }

    public static java.util.Map<String, String> streamHeaders() {
        java.util.Map<String, String> headers = new java.util.HashMap<>();
        String key = com.insta.nova.BuildConfig.SUPABASE_ANON_KEY;
        key = key == null ? "" : key.trim();
        headers.put("apikey", key);
        String token = com.insta.nova.data.remote.FirebaseTokens.current(false);
        headers.put("Authorization", "Bearer " + (token == null || token.isEmpty() ? key : token));
        return headers;
    }

    /**
     * The same object addressed through the authenticated endpoint instead of the public
     * one, for buckets that are not public.
     *
     * <p>A private bucket rejects {@code /object/public/...} outright, and the
     * authenticated path is subject to the storage RLS policy — so the row-level rule on
     * {@code stories} decides who may fetch the bytes, not just who may see the row.
     */
    public static String authenticated(String bucket, String valueFromDb) {
        String normalized = normalize(bucket, valueFromDb);
        if (normalized.isEmpty()) return normalized;
        return toAuthenticated(normalized);
    }

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
