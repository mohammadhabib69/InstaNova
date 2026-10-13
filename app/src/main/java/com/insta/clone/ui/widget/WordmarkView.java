package com.insta.clone.ui.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.insta.clone.R;
import com.insta.clone.util.Constants;

/**
 * Instagram script wordmark, tinted to textPrimary.
 *
 * Prefers the {@code ic_wordmark} drawable when it is present; otherwise it
 * renders {@link Constants#WORDMARK} with a cursive typeface. The text path
 * never crashes if a custom font is absent — it degrades to the platform
 * cursive/serif family.
 */
public class WordmarkView extends LinearLayout {

    public WordmarkView(@NonNull Context context) {
        super(context);
        init(context, null);
    }

    public WordmarkView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public WordmarkView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        setOrientation(HORIZONTAL);

        float sizeSp = resolveTextSize(context);

        int tint = resolveThemeColor(context, R.attr.themeTextPrimary, 0xFF000000);

        boolean hasAsset = false;
        try {
            hasAsset = ContextCompat.getDrawable(context, R.drawable.ic_wordmark) != null;
        } catch (Exception ignored) {
            // Missing asset -> fall back to text.
        }

        if (hasAsset) {
            ImageView image = new ImageView(context);
            image.setImageResource(R.drawable.ic_wordmark);
            image.setColorFilter(tint);
            image.setContentDescription(Constants.WORDMARK);
            LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            addView(image, lp);
        } else {
            TextView text = new TextView(context);
            text.setText(Constants.WORDMARK);
            text.setTextColor(tint);
            text.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
            // Cursive family is guaranteed to resolve to a real font on-device.
            try {
                text.setTypeface(Typeface.create("cursive", Typeface.NORMAL));
            } catch (Exception e) {
                text.setTypeface(Typeface.SERIF);
            }
            addView(text, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        }
    }

    private float resolveTextSize(Context context) {
        try {
            return context.getResources().getDimensionPixelSize(R.dimen.text_wordmark_home)
                    / context.getResources().getDisplayMetrics().scaledDensity;
        } catch (Exception e) {
            return 26f;
        }
    }

    private int resolveThemeColor(Context context, int attr, int fallback) {
        try {
            TypedArray a = context.getTheme().obtainStyledAttributes(new int[]{attr});
            int color = a.getColor(0, fallback);
            a.recycle();
            if (color != 0) return color;
        } catch (Exception ignored) {
        }
        return fallback;
    }
}
