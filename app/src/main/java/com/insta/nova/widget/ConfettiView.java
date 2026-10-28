package com.insta.nova.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.insta.nova.R;

import java.util.Random;

/**
 * 40-particle confetti burst used only for genuine success moments (session saved,
 * donation confirmed). Runs 1.2 s, then hides itself. Never blocks touch.
 */
public class ConfettiView extends View {

    private static final int COUNT = 40;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] x = new float[COUNT];
    private final float[] y = new float[COUNT];
    private final float[] vx = new float[COUNT];
    private final float[] vy = new float[COUNT];
    private final float[] rotation = new float[COUNT];
    private final float[] spin = new float[COUNT];
    private final float[] size = new float[COUNT];
    private final int[] colors = new int[COUNT];

    private float progress;
    private ValueAnimator animator;
    private final Random random = new Random();

    public ConfettiView(Context context) {
        this(context, null);
    }

    public ConfettiView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setVisibility(GONE);
    }

    /** Plays the burst from the centre of this view. */
    public void burst() {
        if (!NovaUi.animationsEnabled(getContext())) return;
        int[] palette = new int[]{
                ContextCompat.getColor(getContext(), R.color.nova_cyan),
                ContextCompat.getColor(getContext(), R.color.nova_violet),
                ContextCompat.getColor(getContext(), R.color.nova_magenta),
                ContextCompat.getColor(getContext(), R.color.nova_success),
                ContextCompat.getColor(getContext(), R.color.nova_warning)
        };
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        for (int i = 0; i < COUNT; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            float speed = NovaUi.dpFloat(getContext(), 4) + random.nextFloat() * NovaUi.dpFloat(getContext(), 7);
            x[i] = cx;
            y[i] = cy;
            vx[i] = (float) Math.cos(angle) * speed;
            vy[i] = (float) Math.sin(angle) * speed - NovaUi.dpFloat(getContext(), 3);
            rotation[i] = random.nextFloat() * 360f;
            spin[i] = -220f + random.nextFloat() * 440f;
            size[i] = NovaUi.dpFloat(getContext(), 3) + random.nextFloat() * NovaUi.dpFloat(getContext(), 4);
            colors[i] = palette[i % palette.length];
        }
        setVisibility(VISIBLE);
        if (animator != null) animator.cancel();
        progress = 0f;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1200);
        animator.setInterpolator(new DecelerateInterpolator(0.7f));
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                setVisibility(GONE);
            }
        });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float t = progress * 1.2f;
        float gravity = NovaUi.dpFloat(getContext(), 26);
        for (int i = 0; i < COUNT; i++) {
            float px = x[i] + vx[i] * t * 12f;
            float py = y[i] + vy[i] * t * 12f + gravity * t * t;
            paint.setColor(colors[i]);
            paint.setAlpha((int) (255 * Math.max(0f, 1f - progress * progress)));
            canvas.save();
            canvas.translate(px, py);
            canvas.rotate(rotation[i] + spin[i] * t);
            canvas.drawRect(-size[i] / 2f, -size[i] / 2f, size[i] / 2f, size[i] / 2f, paint);
            canvas.restore();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }
}
