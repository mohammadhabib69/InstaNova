package com.insta.nova.util;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.widget.ImageView;
import androidx.annotation.Nullable;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.insta.nova.R;

/**
 * Standardized Glide loader using MediaUrl.normalize and logging under tag InstaGlide.
 */
public final class GlideUtils {

    private static final String TAG = "InstaGlide";

    /**
     * Buckets with {@code public = false}. Their objects are only reachable through the
     * authenticated endpoint, where the storage RLS policy on the owning table applies.
     *
     * @see MediaUrl#PRIVATE_BUCKETS
     */
    private GlideUtils() {}

    private static boolean isPrivate(String bucket) {
        return MediaUrl.isPrivate(bucket);
    }

    public static void load(ImageView imageView, String bucket, String rawUrl) {
        load(imageView, bucket, rawUrl, R.drawable.ic_image, R.drawable.ic_image);
    }

    /**
     * Same load, with a callback fired once the image has settled either way. Callers that
     * gate something on "the pixels are here" (the story timer) need the failure case too,
     * otherwise a broken image would freeze the flow instead of moving on.
     */
    public static void load(ImageView imageView, String bucket, String rawUrl,
                            @Nullable Runnable onSettled) {
        load(imageView, bucket, rawUrl, R.drawable.ic_image, R.drawable.ic_image, onSettled);
    }

    public static void load(ImageView imageView, String bucket, String rawUrl, int placeholderRes, int errorRes) {
        load(imageView, bucket, rawUrl, placeholderRes, errorRes, null);
    }

    public static void load(ImageView imageView, String bucket, String rawUrl,
                            int placeholderRes, int errorRes, @Nullable Runnable onSettled) {
        if (imageView == null) return;
        Context context = imageView.getContext();
        String normalizedUrl = MediaUrl.normalize(bucket, rawUrl);

        if (normalizedUrl.isEmpty()) {
            imageView.setImageResource(errorRes);
            if (onSettled != null) imageView.post(onSettled);
            return;
        }

        // Buckets that are no longer public are fetched through the authenticated
        // endpoint. Glide does not share this app's Retrofit interceptors, so the same
        // headers are attached here — lazily, because resolving a Firebase token is a
        // blocking call and Glide evaluates these on its own loading thread.
        // A local preview (a just-captured photo or template render) must keep the file/
        // content loader; the OkHttp loader rejects anything that is not http(s).
        boolean remote = normalizedUrl.startsWith("http://") || normalizedUrl.startsWith("https://");
        boolean needsAuth = remote && isPrivate(bucket);
        Object model = normalizedUrl;
        if (needsAuth) {
            com.bumptech.glide.load.model.LazyHeaders headers =
                    new com.bumptech.glide.load.model.LazyHeaders.Builder()
                            .addHeader("apikey", () -> {
                                String key = com.insta.nova.BuildConfig.SUPABASE_ANON_KEY;
                                return key == null ? "" : key.trim();
                            })
                            .addHeader("Authorization", () -> {
                                String token = com.insta.nova.data.remote.FirebaseTokens.current(false);
                                return token == null || token.isEmpty() ? "" : "Bearer " + token;
                            })
                            .build();
            model = new com.bumptech.glide.load.model.GlideUrl(
                    MediaUrl.authenticated(bucket, rawUrl), headers);
        }

        com.bumptech.glide.RequestBuilder<Drawable> request = Glide.with(context)
                .load(model)
                .placeholder(placeholderRes)
                .error(errorRes);
        // Data Saver: decode a smaller bitmap and let the GPU upscale it, which
        // lowers both bytes over the wire cache and decode quality.
        if (new com.insta.nova.data.local.AppSettingsStore(context).dataSaver()) {
            request = request.override(480, 480);
        }
        request
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        StringBuilder detail = new StringBuilder();
                        if (e != null) {
                            for (Throwable cause : e.getRootCauses()) {
                                detail.append(" | ").append(cause.getClass().getSimpleName())
                                        .append(": ").append(cause.getMessage());
                            }
                        }
                        Log.e(TAG, "Failed loading URL [" + normalizedUrl + "]: "
                                + (e != null ? e.getMessage() : "unknown") + detail, e);
                        if (onSettled != null) imageView.post(onSettled);
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        Log.d(TAG, "Successfully loaded URL [" + normalizedUrl + "]");
                        if (onSettled != null) imageView.post(onSettled);
                        return false;
                    }
                })
                .into(imageView);
    }

    /**
     * Detail-view load: paints a quarter-size copy first, then the full image.
     *
     * <p>A private-bucket object arrives in one piece, so a large photo showed nothing at all
     * until the last byte landed. The thumbnail request puts pixels on screen as soon as a
     * small version is decoded, which is what makes a slow connection feel responsive.
     *
     * @param onSettled receives {@code true} when the full image was decoded, {@code false}
     *                  when it failed (so the caller can offer a retry).
     */
    public static void loadProgressive(ImageView imageView, String bucket, String rawUrl,
                                       @Nullable java.util.function.Consumer<Boolean> onSettled) {
        if (imageView == null) return;
        Context context = imageView.getContext();
        String normalizedUrl = MediaUrl.normalize(bucket, rawUrl);
        if (normalizedUrl.isEmpty()) {
            imageView.setImageResource(R.drawable.ic_image);
            if (onSettled != null) imageView.post(() -> onSettled.accept(false));
            return;
        }

        Object model = modelFor(bucket, normalizedUrl);
        Glide.with(context)
                .load(model)
                .thumbnail(0.25f)
                .placeholder(R.drawable.ic_image)
                .error(R.drawable.ic_image)
                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.AUTOMATIC)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object m,
                                                Target<Drawable> target, boolean isFirst) {
                        Log.e(TAG, "Failed loading URL [" + normalizedUrl + "]: "
                                + (e != null ? e.getMessage() : "unknown"), e);
                        if (onSettled != null) imageView.post(() -> onSettled.accept(false));
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object m,
                                                   Target<Drawable> target,
                                                   DataSource dataSource, boolean isFirst) {
                        if (onSettled != null) imageView.post(() -> onSettled.accept(true));
                        return false;
                    }
                })
                .into(imageView);
    }

    /**
     * Same request as {@link #load}, but reports whether pixels arrived.
     *
     * <p>A DM media bubble takes its height from the bitmap. When a fetch is refused (an
     * expired story share, a revoked object) nothing is drawn and the bubble collapses to
     * zero height, which reads as a blank gap in the thread. Callers use the {@code false}
     * case to reserve a visible box instead.
     */
    public static void loadTracked(ImageView imageView, String bucket, String rawUrl,
                                   java.util.function.Consumer<Boolean> result) {
        if (imageView == null) return;
        Context context = imageView.getContext();
        String normalizedUrl = MediaUrl.normalize(bucket, rawUrl);
        if (normalizedUrl.isEmpty()) {
            imageView.setImageResource(R.drawable.ic_image);
            if (result != null) imageView.post(() -> result.accept(false));
            return;
        }
        Object model = modelFor(bucket, normalizedUrl);
        Glide.with(context)
                .load(model)
                .placeholder(R.drawable.ic_image)
                .error(R.drawable.ic_image)
                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.AUTOMATIC)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object m,
                                                Target<Drawable> target, boolean isFirst) {
                        Log.e(TAG, "Failed loading URL [" + normalizedUrl + "]: "
                                + (e != null ? e.getMessage() : "unknown"), e);
                        if (result != null) imageView.post(() -> result.accept(false));
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object m,
                                                   Target<Drawable> target,
                                                   DataSource dataSource, boolean isFirst) {
                        if (result != null) imageView.post(() -> result.accept(true));
                        return false;
                    }
                })
                .into(imageView);
    }

    /**
     * Grid-cell load: decodes at the cell's own size instead of the stored resolution.
     *
     * <p>Feeding a 360 px cell a 1440 px original costs memory and decode time per tile, and a
     * full screen of them is what made the profile grid sit on its placeholders. The override
     * caps the decode, and the explicit cache key keeps those small bitmaps reusable.
     */
    public static void loadSized(ImageView imageView, String bucket, String rawUrl,
                                 int targetPx, @Nullable Runnable onSettled) {
        if (imageView == null) return;
        Context context = imageView.getContext();
        String normalizedUrl = MediaUrl.normalize(bucket, rawUrl);
        if (normalizedUrl.isEmpty()) {
            imageView.setImageResource(R.drawable.ic_image);
            if (onSettled != null) imageView.post(onSettled);
            return;
        }

        int side = Math.max(64, targetPx);
        Object model = modelFor(bucket, normalizedUrl);
        Glide.with(context)
                .load(model)
                .override(side, side)
                .placeholder(R.drawable.ic_image)
                .error(R.drawable.ic_image)
                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.AUTOMATIC)
                .priority(com.bumptech.glide.Priority.NORMAL)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object m,
                                                Target<Drawable> target, boolean isFirst) {
                        Log.e(TAG, "Failed loading URL [" + normalizedUrl + "]: "
                                + (e != null ? e.getMessage() : "unknown"), e);
                        if (onSettled != null) imageView.post(onSettled);
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object m,
                                                   Target<Drawable> target,
                                                   DataSource dataSource, boolean isFirst) {
                        if (onSettled != null) imageView.post(onSettled);
                        return false;
                    }
                })
                .into(imageView);
    }

    /**
     * The Glide model for a normalized URL: private buckets need the authenticated endpoint and
     * a bearer token, everything else is a plain URL.
     */
    private static Object modelFor(String bucket, String normalizedUrl) {
        boolean remote = normalizedUrl.startsWith("http://") || normalizedUrl.startsWith("https://");
        if (!remote || !isPrivate(bucket)) return normalizedUrl;
        com.bumptech.glide.load.model.LazyHeaders headers =
                new com.bumptech.glide.load.model.LazyHeaders.Builder()
                        .addHeader("apikey", () -> {
                            String key = com.insta.nova.BuildConfig.SUPABASE_ANON_KEY;
                            return key == null ? "" : key.trim();
                        })
                        .addHeader("Authorization", () -> {
                            String token = com.insta.nova.data.remote.FirebaseTokens.current(false);
                            return token == null || token.isEmpty() ? "" : "Bearer " + token;
                        })
                        .build();
        return new com.bumptech.glide.load.model.GlideUrl(
                MediaUrl.toAuthenticated(normalizedUrl), headers);
    }

    /**
     * Loads into a view whose corners must be rounded in the pixels themselves. ShapeableImageView
     * outline clipping is not applied on every OEM theme, so the rounding is baked at decode time.
     */
    public static void loadRounded(ImageView imageView, String bucket, String rawUrl, int radiusPx) {
        loadRounded(imageView, bucket, rawUrl, radiusPx, 0, 0);
    }

    public static void loadRounded(ImageView imageView, String bucket, String rawUrl, int radiusPx,
                                   int placeholderRes, int errorRes) {
        android.content.Context context = imageView.getContext();
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            if (errorRes != 0) imageView.setImageResource(errorRes);
            return;
        }
        boolean remote = rawUrl.startsWith("http");
        // Private buckets need the bearer token, exactly like load(). Building the plain
        // authenticated URL here worked only while the bitmap was in Glide's disk cache.
        Object model = remote ? modelFor(bucket, MediaUrl.normalize(bucket, rawUrl)) : rawUrl;
        com.bumptech.glide.RequestBuilder<Drawable> request = Glide.with(context)
                .load(model)
                .transform(new com.bumptech.glide.load.resource.bitmap.RoundedCorners(Math.max(1, radiusPx)));
        if (placeholderRes != 0) request = request.placeholder(placeholderRes);
        if (errorRes != 0) request = request.error(errorRes);
        request.into(imageView);
    }

    /**
     * Avatars now live in a private bucket too, so they go through the same path as any other
     * object: {@code avatars} is on the private list, which means the authenticated endpoint
     * and the token headers are attached rather than left off.
     */
    public static void loadAvatar(ImageView imageView, String rawUrl) {
        load(imageView, "avatars", rawUrl,
                R.drawable.ic_person_placeholder, R.drawable.ic_person_placeholder);
    }

    /**
     * Best-effort avatar bitmap for a notification's large icon.
     *
     * <p>Only an already-decoded image is returned: {@code Notification.Builder} wants a
     * Bitmap synchronously, and blocking the thread that posts an incoming-call
     * notification on a network fetch is exactly the wrong trade — the notification is the
     * only thing that rings a backgrounded phone. Callers fall back to the placeholder.
     */
    @Nullable
    public static android.graphics.Bitmap notificationBanner(Context context, @Nullable String rawUrl) {
        if (context == null || rawUrl == null || rawUrl.trim().isEmpty()) return null;
        try {
            String normalizedUrl = MediaUrl.normalize("avatars", rawUrl);
            if (normalizedUrl.isEmpty()) return null;
            return Glide.with(context.getApplicationContext())
                    .asBitmap()
                    .load(normalizedUrl)
                    .submit(256, 256)
                    .get(120, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            return null;
        }
    }
}
