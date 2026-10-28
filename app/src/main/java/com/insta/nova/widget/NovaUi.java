package com.insta.nova.widget;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.insta.nova.R;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Small, allocation-conscious helpers shared by every Nova screen.
 *
 * <p>Every animation helper checks the system animator duration scale first: when a
 * user (or an automated test) sets it to 0, Nova renders statically instead of
 * animating. Haptics degrade gracefully on API &lt; 30.
 */
public final class NovaUi {

    private static final DecimalFormat NUMBER = new DecimalFormat("#,###");

    private NovaUi() {
    }

    /* ------------------------------------------------------------------ */
    /* Metrics                                                             */
    /* ------------------------------------------------------------------ */

    public static int dp(Context context, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics()));
    }

    public static float dpFloat(Context context, float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics());
    }

    public static int sp(Context context, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value,
                context.getResources().getDisplayMetrics()));
    }

    /* ------------------------------------------------------------------ */
    /* Motion                                                              */
    /* ------------------------------------------------------------------ */

    public static boolean animationsEnabled(Context context) {
        if (context == null) return true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return ValueAnimator.areAnimatorsEnabled();
        }
        try {
            float scale = Settings.Global.getFloat(context.getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
            return scale > 0f;
        } catch (RuntimeException e) {
            return true;
        }
    }

    /** Spring-ish press: 0.97 scale down in 120 ms, back on release. */
    public static void pressEffect(View view) {
        if (view == null) return;
        final boolean animate = animationsEnabled(view.getContext());
        view.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (animate) v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(120).start();
                    break;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    if (animate) v.animate().scaleX(1f).scaleY(1f).setDuration(160).start();
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    /** Staggered entrance: 30 ms steps, capped at 8 items. */
    public static void staggerIn(ViewGroup parent) {
        if (parent == null) return;
        boolean animate = animationsEnabled(parent.getContext());
        int count = Math.min(parent.getChildCount(), 8);
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            child.setAlpha(animate ? 0f : 1f);
            child.setTranslationY(animate ? dp(parent.getContext(), 14) : 0f);
            if (!animate) continue;
            child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(i < count ? i * 30L : 240L)
                    .setDuration(260)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }

    public static void countUp(final TextView target, long from, long to) {
        if (target == null) return;
        if (!animationsEnabled(target.getContext())) {
            target.setText(NUMBER.format(to));
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(600);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            target.setText(NUMBER.format(from + Math.round((to - from) * f)));
        });
        animator.start();
    }

    /** 1.2 s shimmer pulse for skeleton placeholders. */
    public static void pulse(View view) {
        if (view == null) return;
        if (!animationsEnabled(view.getContext())) {
            view.setAlpha(0.7f);
            return;
        }
        android.animation.ObjectAnimator animator = android.animation.ObjectAnimator
                .ofFloat(view, "alpha", 0.45f, 1f);
        animator.setDuration(1200);
        animator.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        animator.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        animator.start();
    }

    public static void haptic(View view, boolean confirm) {
        if (view == null) return;
        int type;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            type = confirm ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.REJECT;
        } else {
            type = confirm ? HapticFeedbackConstants.LONG_PRESS : HapticFeedbackConstants.KEYBOARD_TAP;
        }
        view.performHapticFeedback(type);
    }

    /* ------------------------------------------------------------------ */
    /* Colour                                                              */
    /* ------------------------------------------------------------------ */

    public static boolean isNight(Context context) {
        int mode = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    /** Circle behind a module icon: accent at 18% fill + 22% stroke. */
    public static GradientDrawable accentCircle(Context context, int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(withAlpha(color, 0.18f));
        d.setStroke(dp(context, 1), withAlpha(color, 0.34f));
        return d;
    }

    public static GradientDrawable accentPill(Context context, int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(dpFloat(context, 16));
        d.setColor(withAlpha(color, 0.14f));
        return d;
    }

    public static int withAlpha(int color, float alpha) {
        return Color.argb(Math.round(255 * alpha), Color.red(color), Color.green(color), Color.blue(color));
    }

    public static int darken(int color, float factor) {
        return Color.rgb((int) (Color.red(color) * factor),
                (int) (Color.green(color) * factor),
                (int) (Color.blue(color) * factor));
    }

    public static int accent(Context context, int module) {
        switch (module) {
            case NovaModules.SAFE:
                return ContextCompat.getColor(context, R.color.nova_safe);
            case NovaModules.SOS:
                return ContextCompat.getColor(context, R.color.nova_sos);
            case NovaModules.CAMPUS:
                return ContextCompat.getColor(context, R.color.nova_campus);
            case NovaModules.LITE:
                return ContextCompat.getColor(context, R.color.nova_lite);
            case NovaModules.STUDY:
                return ContextCompat.getColor(context, R.color.nova_study);
            case NovaModules.INSIGHTS:
                return ContextCompat.getColor(context, R.color.nova_insights);
            default:
                return ContextCompat.getColor(context, R.color.nova_violet);
        }
    }

    /** Makes the status-bar icons readable over the current Nova surface. */
    public static void styleStatusBar(Activity activity, boolean lightIcons) {
        if (activity == null || activity.getWindow() == null) return;
        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(activity.getWindow(), activity.getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(!lightIcons);
        controller.setAppearanceLightNavigationBars(!lightIcons);
    }

    /* ------------------------------------------------------------------ */
    /* Text                                                                */
    /* ------------------------------------------------------------------ */

    public static String number(long value) {
        return NUMBER.format(value);
    }

    public static String minutes(Context context, int minutes) {
        return context.getString(R.string.nova_study_minutes, minutes);
    }

    public static String formatHour(Context context, int hour24) {
        int h = hour24 % 12;
        if (h == 0) h = 12;
        String suffix = hour24 < 12 ? "AM" : "PM";
        if ("bn".equals(Locale.getDefault().getLanguage())) {
            suffix = hour24 < 12 ? "AM" : "PM";
        }
        return h + " " + suffix;
    }

    public static NumberFormat integerFormat() {
        return NumberFormat.getIntegerInstance(Locale.US);
    }
}
