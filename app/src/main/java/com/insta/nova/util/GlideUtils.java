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

    private GlideUtils() {}

    public static void load(ImageView imageView, String bucket, String rawUrl) {
        load(imageView, bucket, rawUrl, R.drawable.ic_image, R.drawable.ic_image);
    }

    public static void load(ImageView imageView, String bucket, String rawUrl, int placeholderRes, int errorRes) {
        if (imageView == null) return;
        Context context = imageView.getContext();
        String normalizedUrl = MediaUrl.normalize(bucket, rawUrl);

        if (normalizedUrl.isEmpty()) {
            imageView.setImageResource(errorRes);
            return;
        }

        Glide.with(context)
                .load(normalizedUrl)
                .placeholder(placeholderRes)
                .error(errorRes)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        Log.e(TAG, "Failed loading URL [" + normalizedUrl + "]: " + (e != null ? e.getMessage() : "unknown"), e);
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        Log.d(TAG, "Successfully loaded URL [" + normalizedUrl + "]");
                        return false;
                    }
                })
                .into(imageView);
    }

    public static void loadAvatar(ImageView imageView, String rawUrl) {
        if (imageView == null) return;
        Context context = imageView.getContext();
        String normalizedUrl = MediaUrl.normalize("avatars", rawUrl);

        if (normalizedUrl.isEmpty()) {
            imageView.setImageResource(R.drawable.ic_person_placeholder);
            return;
        }

        Glide.with(context)
                .load(normalizedUrl)
                .placeholder(R.drawable.ic_person_placeholder)
                .error(R.drawable.ic_person_placeholder)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        Log.e(TAG, "Failed loading avatar [" + normalizedUrl + "]: " + (e != null ? e.getMessage() : "unknown"), e);
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        Log.d(TAG, "Successfully loaded avatar [" + normalizedUrl + "]");
                        return false;
                    }
                })
                .into(imageView);
    }
}
