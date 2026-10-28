package com.insta.nova.data;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;

/**
 * One image entry point for the whole app so the data saver applies everywhere.
 *
 * <p>Saver mode decodes at half the target size in RGB_565, which is the single
 * biggest bandwidth/memory win. Avatars use a fixed small target and are never
 * upscaled. Callers that want the full image (post detail, tap-to-load) use
 * {@link #loadFull}.
 */
public final class NovaGlide {

    private NovaGlide() {
    }

    public static void load(ImageView view, @Nullable String url) {
        load(view, url, false);
    }

    public static void loadFull(ImageView view, @Nullable String url) {
        load(view, url, true);
    }

    public static void loadAvatar(ImageView view, @Nullable String url) {
        if (view == null) return;
        if (url == null || url.trim().isEmpty()) {
            view.setImageDrawable(null);
            return;
        }
        RequestOptions options = new RequestOptions()
                .override(160, 160)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .format(NovaDataSaver.peekActive(view.getContext())
                        ? DecodeFormat.PREFER_RGB_565 : DecodeFormat.PREFER_ARGB_8888);
        Glide.with(view).load(url).apply(options).into(view);
    }

    private static void load(ImageView view, String url, boolean full) {
        if (view == null) return;
        if (url == null || url.trim().isEmpty()) {
            view.setImageDrawable((Drawable) null);
            return;
        }
        Context context = view.getContext();
        boolean saver = NovaDataSaver.peekActive(context) && !full;
        int targetW = view.getWidth() > 0 ? view.getWidth()
                : context.getResources().getDisplayMetrics().widthPixels / 2;
        int targetH = view.getHeight() > 0 ? view.getHeight()
                : context.getResources().getDisplayMetrics().heightPixels / 4;

        RequestOptions options = new RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .priority(Priority.NORMAL);
        if (saver) {
            options = options.override(Math.max(1, targetW / 2), Math.max(1, targetH / 2))
                    .format(DecodeFormat.PREFER_RGB_565);
        } else {
            options = options.format(DecodeFormat.PREFER_ARGB_8888);
        }
        Glide.with(view).load(url).apply(options).into(view);
    }
}
