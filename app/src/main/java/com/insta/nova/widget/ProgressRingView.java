package com.insta.nova.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.insta.nova.R;

/** Circular progress with an 800 ms decelerating sweep and an optional centred label. */
public class ProgressRingView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint captionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();

    private float progress;
    private float target;
    private float max = 100f;
    private float strokeWidth;
    private String caption = "";
    private boolean gradient;
    private ValueAnimator animator;

    public ProgressRingView(Context context) {
        this(context, null);
    }

    public ProgressRingView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        strokeWidth = NovaUi.dpFloat(context, 10);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(strokeWidth);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setColor(ContextCompat.getColor(context, R.color.nova_stroke));
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(strokeWidth);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setColor(ContextCompat.getColor(context, R.color.nova_safe));
        valuePaint.setColor(ContextCompat.getColor(context, R.color.nova_text));
        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setTextSize(NovaUi.sp(context, 34));
        valuePaint.setFakeBoldText(true);
        captionPaint.setColor(ContextCompat.getColor(context, R.color.nova_text_2));
        captionPaint.setTextAlign(Paint.Align.CENTER);
        captionPaint.setTextSize(NovaUi.sp(context, 12));
    }

    public void setAccent(int color) {
        progressPaint.setColor(color);
        gradient = false;
        invalidate();
    }

    public void useGradient() {
        gradient = true;
        invalidate();
    }

    public void setMax(float max) {
        this.max = Math.max(1f, max);
        invalidate();
    }

    public void setCaption(String caption) {
        this.caption = caption == null ? "" : caption;
        invalidate();
    }

    /** Animates to a 0–100 percentage. */
    public void setProgress(float percent, boolean animate) {
        target = Math.max(0f, Math.min(max, percent));
        if (animator != null) animator.cancel();
        if (!animate || !NovaUi.animationsEnabled(getContext()) || getWindowToken() == null) {
            progress = target;
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(progress, target);
        animator.setDuration(800);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight());
        float inset = strokeWidth / 2f + NovaUi.dpFloat(getContext(), 2);
        arcRect.set((getWidth() - size) / 2f + inset, (getHeight() - size) / 2f + inset,
                (getWidth() + size) / 2f - inset, (getHeight() + size) / 2f - inset);

        canvas.drawArc(arcRect, -90f, 360f, false, trackPaint);
        if (gradient) {
            progressPaint.setShader(new LinearGradient(arcRect.left, arcRect.top, arcRect.right, arcRect.bottom,
                    ContextCompat.getColor(getContext(), R.color.nova_cyan),
                    ContextCompat.getColor(getContext(), R.color.nova_magenta), Shader.TileMode.CLAMP));
        }
        canvas.drawArc(arcRect, -90f, 360f * (progress / max), false, progressPaint);

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        String value = NovaUi.number(Math.round(progress));
        canvas.drawText(value, cx, cy + valuePaint.getTextSize() * 0.28f, valuePaint);
        if (!caption.isEmpty()) {
            canvas.drawText(caption, cx, cy + valuePaint.getTextSize() * 0.85f, captionPaint);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }
}
