package com.insta.nova.util;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.util.Log;

import java.util.Map;

/**
 * One track at a time for post music.
 *
 * <p>The posts bucket is private, so the bytes are fetched from the authenticated
 * storage endpoint with the signed-in user's token — the same headers the video
 * player uses. A single shared instance means tapping a second post stops the
 * first, which is what a feed needs: two tracks playing at once is never right.
 */
public final class PostMusicPlayer {

    private static final String TAG = "PostMusic";

    private static MediaPlayer player;
    /** The URL currently loaded, null when nothing is playing. */
    private static String currentUrl;
    /** True while the loaded track is deliberately paused. */
    private static boolean paused;
    /** Kept only so a failed download can say so on screen. */
    private static Context appContext;

    private PostMusicPlayer() {
    }

    /**
     * True when this URL is the loaded track and not paused. It is deliberately not
     * {@code isPlaying()}: the player prepares asynchronously, so a row that re-binds
     * right after a tap would still read "not playing" and flip the icon back.
     */
    public static boolean isActive(String url) {
        return url != null && url.equals(currentUrl) && player != null && !paused;
    }

    /** True when this URL is the one loaded and running. */
    public static boolean isPlaying(String url) {
        return url != null && url.equals(currentUrl) && player != null && player.isPlaying();
    }

    /** True when this URL is loaded but paused. */
    public static boolean isLoaded(String url) {
        return url != null && url.equals(currentUrl) && player != null;
    }

    /**
     * Plays {@code url}, or pauses/resumes it when it is already the loaded track.
     *
     * @return true when the track ends up playing
     */
    public static boolean toggle(Context context, String url) {
        if (url == null || url.trim().isEmpty()) return false;
        appContext = context.getApplicationContext();
        // Identity is the URL the row carries, not the rewritten one: the chip compares its
        // own raw musicUrl against this, and posts is a private bucket, so the authenticated
        // endpoint differs from the stored string and would never match.
        String key = url.trim();
        String resolved = MediaUrl.toAuthenticated(MediaUrl.normalize("posts", key));

        if (player != null && key.equals(currentUrl)) {
            if (player.isPlaying()) {
                player.pause();
                paused = true;
                return false;
            }
            paused = false;
            player.start();
            return true;
        }

        stop();
        final String trackUrl = resolved;
        currentUrl = key;
        paused = false;

        // The String overload that takes headers does not exist on this SDK, and the Uri
        // overload goes through ContentResolver ("No content provider" for an https URL).
        // So the file is fetched once with the auth headers and played from the cache.
        new Thread(() -> {
            java.io.File cached = cachedFile(context, trackUrl);
            if (!cached.exists() || cached.length() == 0) {
                try {
                    okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
                    okhttp3.Request.Builder request = new okhttp3.Request.Builder().url(trackUrl);
                    for (Map.Entry<String, String> header : MediaUrl.streamHeaders().entrySet()) {
                        request.addHeader(header.getKey(), header.getValue());
                    }
                    okhttp3.Response response = client.newCall(request.build()).execute();
                    if (!response.isSuccessful() || response.body() == null) {
                        Log.e(TAG, "download failed HTTP " + response.code() + " for " + trackUrl);
                        stopOnMain();
                        return;
                    }
                    java.io.File tmp = new java.io.File(cached.getAbsolutePath() + ".part");
                    try (java.io.InputStream in = response.body().byteStream();
                         java.io.OutputStream out = new java.io.FileOutputStream(tmp)) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                    }
                    if (!tmp.renameTo(cached)) {
                        Log.e(TAG, "could not store the downloaded track");
                        stopOnMain();
                        return;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "download error for " + trackUrl + ": " + e.getMessage(), e);
                    stopOnMain();
                    return;
                }
            }
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> startFile(cached));
        }).start();
        return true;
    }

    /** The cached copy of a track, one file per URL. */
    private static java.io.File cachedFile(Context context, String url) {
        java.io.File dir = new java.io.File(context.getApplicationContext().getCacheDir(), "post_music");
        if (!dir.exists()) dir.mkdirs();
        String name = Integer.toHexString(url.hashCode()) + ".audio";
        return new java.io.File(dir, name);
    }

    /** Starts a downloaded track; called on the main thread. */
    private static void startFile(java.io.File file) {
        if (currentUrl == null) return;
        // Only the player is replaced here: stop() would also clear currentUrl, which is
        // exactly what the chip reads to decide between "♪" and "⏸".
        releasePlayer();
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build());
            player.setDataSource(file.getAbsolutePath());
            player.setOnCompletionListener(mp -> stop());
            player.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "playback error " + what + "/" + extra);
                stop();
                return true;
            });
            player.setOnPreparedListener(mp -> {
                paused = false;
                mp.start();
            });
            paused = false;
            player.prepareAsync();
        } catch (Exception e) {
            Log.e(TAG, "could not open " + file + ": " + e.getMessage(), e);
            stop();
        }
    }

    /** Releases on the main thread so the chip is not left reading "playing". */
    private static void stopOnMain() {
        final Context context = appContext;
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            stop();
            if (context != null) {
                android.widget.Toast.makeText(context, com.insta.nova.R.string.post_music_failed,
                        android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Releases the player; safe to call when nothing is playing. */
    public static void stop() {
        releasePlayer();
        currentUrl = null;
        paused = false;
    }

    /** Drops the MediaPlayer but keeps the loaded URL, so a rebind keeps showing "⏸". */
    private static void releasePlayer() {
        if (player != null) {
            try {
                player.reset();
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
        }
    }
}
