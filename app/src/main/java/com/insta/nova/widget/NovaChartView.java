package com.insta.nova.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.insta.nova.R;

/**
 * Dependency-free line/bar chart with a gradient fill, a 700 ms draw animation and
 * touch scrubbing. One instance per screen section; all paint objects are reused.
 */
public class NovaChartView extends View {

    public enum Mode {LINE, BAR}

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tooltipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tooltipTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint skeletonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path linePath = new Path();
    private final Path fillPath = new Path();
    private final RectF barRect = new RectF();
    private final RectF tooltipRect = new RectF();

    private float[] values = new float[0];
    private String[] labels = new String[0];
    private Mode mode = Mode.LINE;
    private int accent;
    private float progress = 1f;
    private boolean loading;
    private int selectedIndex = -1;
    private ValueAnimator animator;
    /** Shown instead of an empty plot when every value is zero. */
    private String emptyText;

    private float padLeft;
    private float padRight;
    private float padTop;
    private float padBottom;
    private float plotW;
    private float plotH;
    private float maxValue = 1f;

    public NovaChartView(Context context) {
        this(context, null);
    }

    public NovaChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        accent = ContextCompat.getColor(context, R.color.nova_cyan);
        float density = getResources().getDisplayMetrics().density;
        padLeft = 4f * density;
        padRight = 4f * density;
        padTop = 22f * density;
        padBottom = 22f * density;

        gridPaint.setColor(ContextCompat.getColor(context, R.color.nova_stroke));
        gridPaint.setStrokeWidth(1f);
        axisPaint.setColor(ContextCompat.getColor(context, R.color.nova_text_3));
        axisPaint.setTextSize(10f * getResources().getDisplayMetrics().scaledDensity);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(2.2f * density);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        fillPaint.setStyle(Paint.Style.FILL);
        barPaint.setStyle(Paint.Style.FILL);
        tooltipPaint.setColor(ContextCompat.getColor(context, R.color.nova_card_high));
        tooltipTextPaint.setColor(ContextCompat.getColor(context, R.color.nova_text));
        tooltipTextPaint.setTextSize(11f * getResources().getDisplayMetrics().scaledDensity);
        tooltipTextPaint.setAntiAlias(true);
        skeletonPaint.setColor(ContextCompat.getColor(context, R.color.nova_skeleton));
    }

    /* ------------------------------------------------------------------ */
    /* Data                                                                */
    /* ------------------------------------------------------------------ */

    public void setMode(Mode mode) {
        this.mode = mode;
        rebuildPaths();
        invalidate();
    }

    public void setAccent(int color) {
        this.accent = color;
        rebuildPaths();
        invalidate();
    }

    public void setLoading(boolean loading) {
        this.loading = loading;
        invalidate();
    }

    public void setEmptyText(String text) {
        this.emptyText = text;
        invalidate();
    }

    public void setData(float[] values, String[] labels) {
        this.values = values == null ? new float[0] : values;
        this.labels = labels == null ? new String[0] : labels;
        this.selectedIndex = -1;
        this.loading = false;
        maxValue = 1f;
        for (float v : this.values) maxValue = Math.max(maxValue, v);
        maxValue *= 1.15f;
        rebuildPaths();
        animateDraw();
    }

    private void animateDraw() {
        if (animator != null) animator.cancel();
        if (!NovaUi.animationsEnabled(getContext())) {
            progress = 1f;
            invalidate();
            return;
        }
        progress = 0f;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(700);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        plotW = Math.max(1, w - padLeft - padRight);
        plotH = Math.max(1, h - padTop - padBottom);
        rebuildPaths();
    }

    private void rebuildPaths() {
        linePath.reset();
        fillPath.reset();
        if (values.length == 0 || plotW <= 0) return;

        if (mode == Mode.LINE) {
            float step = values.length == 1 ? 0 : plotW / (values.length - 1);
            float prevX = 0;
            float prevY = 0;
            for (int i = 0; i < values.length; i++) {
                float x = padLeft + (values.length == 1 ? plotW / 2 : i * step);
                float y = padTop + plotH - (values[i] / maxValue) * plotH;
                if (i == 0) {
                    linePath.moveTo(x, y);
                    prevX = x;
                    prevY = y;
                } else {
                    // Cubic through the midpoint keeps the curve smooth without overshoot.
                    float midX = (prevX + x) / 2f;
                    linePath.cubicTo(midX, prevY, midX, y, x, y);
                    prevX = x;
                    prevY = y;
                }
            }
            fillPath.addPath(linePath);
            fillPath.lineTo(padLeft + plotW, padTop + plotH);
            fillPath.lineTo(padLeft, padTop + plotH);
            fillPath.close();
            fillPaint.setShader(new LinearGradient(0, padTop, 0, padTop + plotH,
                    NovaUi.withAlpha(accent, 0.34f), NovaUi.withAlpha(accent, 0f),
                    Shader.TileMode.CLAMP));
            linePaint.setShader(new LinearGradient(padLeft, 0, padLeft + plotW, 0,
                    accent, ContextCompat.getColor(getContext(), R.color.nova_cyan), Shader.TileMode.CLAMP));
            barPaint.setShader(null);
        } else {
            barPaint.setShader(new LinearGradient(0, padTop, 0, padTop + plotH,
                    accent, NovaUi.withAlpha(accent, 0.28f), Shader.TileMode.CLAMP));
        }
    }

    /* ------------------------------------------------------------------ */
    /* Draw                                                                */
    /* ------------------------------------------------------------------ */

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float bottom = padTop + plotH;
        for (int i = 0; i <= 3; i++) {
            float y = padTop + plotH * i / 3f;
            canvas.drawLine(padLeft, y, padLeft + plotW, y, gridPaint);
        }

        if (loading) {
            float bw = plotW / 12f;
            for (int i = 0; i < 12; i++) {
                float h = plotH * (0.25f + 0.5f * Math.abs((float) Math.sin(i * 1.7f)));
                barRect.set(padLeft + i * bw, bottom - h, padLeft + i * bw + bw * 0.6f, bottom);
                canvas.drawRoundRect(barRect, 6, 6, skeletonPaint);
            }
            return;
        }
        if (values.length == 0) {
            return;
        }

        float total = 0f;
        for (float v : values) total += Math.max(0f, v);
        if (total <= 0f && emptyText != null && !emptyText.isEmpty()) {
            // Nothing happened yet: say so instead of drawing an empty plot.
            tooltipTextPaint.setTextAlign(Paint.Align.CENTER);
            tooltipTextPaint.setColor(ContextCompat.getColor(getContext(), R.color.nova_text_3));
            canvas.drawText(emptyText, getWidth() / 2f, padTop + plotH / 2f, tooltipTextPaint);
            tooltipTextPaint.setTextAlign(Paint.Align.LEFT);
            tooltipTextPaint.setColor(ContextCompat.getColor(getContext(), R.color.nova_text));
            drawAxisLabels(canvas);
            return;
        }

        if (mode == Mode.BAR) {
            float slot = plotW / values.length;
            float barW = Math.min(slot * 0.58f, NovaUi.dpFloat(getContext(), 26));
            for (int i = 0; i < values.length; i++) {
                float h = (values[i] / maxValue) * plotH * progress;
                float left = padLeft + i * slot + (slot - barW) / 2f;
                barRect.set(left, bottom - h, left + barW, bottom);
                canvas.drawRoundRect(barRect, barW / 2f, barW / 2f, barPaint);
            }
        } else {
            canvas.save();
            canvas.clipRect(padLeft, padTop - 4, padLeft + plotW * progress, bottom + 1);
            canvas.drawPath(fillPath, fillPaint);
            canvas.drawPath(linePath, linePaint);
            canvas.restore();
        }

        drawAxisLabels(canvas);
        drawTooltip(canvas);
    }

    private void drawAxisLabels(Canvas canvas) {
        if (labels.length == 0) return;
        axisPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(labels[0], padLeft, getHeight() - 6, axisPaint);
        if (labels.length > 2) {
            axisPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(labels[labels.length / 2], padLeft + plotW / 2f, getHeight() - 6, axisPaint);
        }
        axisPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(labels[labels.length - 1], padLeft + plotW, getHeight() - 6, axisPaint);
    }

    private void drawTooltip(Canvas canvas) {
        if (selectedIndex < 0 || selectedIndex >= values.length) return;
        float step = values.length == 1 ? 0 : plotW / (values.length - 1);
        float x = padLeft + (values.length == 1 ? plotW / 2 : selectedIndex * step);
        float y = padTop + plotH - (values[selectedIndex] / maxValue) * plotH;
        String label = (selectedIndex < labels.length ? labels[selectedIndex] + " · " : "")
                + NovaUi.number(Math.round(values[selectedIndex]));
        float textW = tooltipTextPaint.measureText(label);
        float w = textW + NovaUi.dpFloat(getContext(), 18);
        float h = NovaUi.dpFloat(getContext(), 28);
        float left = Math.max(2, Math.min(getWidth() - w - 2, x - w / 2));
        float top = Math.max(2, y - h - NovaUi.dpFloat(getContext(), 8));
        tooltipRect.set(left, top, left + w, top + h);
        canvas.drawRoundRect(tooltipRect, h / 2, h / 2, tooltipPaint);
        canvas.drawText(label, left + w / 2, top + h * 0.68f, centeredTooltipPaint());
        canvas.drawCircle(x, y, NovaUi.dpFloat(getContext(), 4), dotPaint());
    }

    private final Paint centered = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Paint centeredTooltipPaint() {
        centered.set(tooltipTextPaint);
        centered.setTextAlign(Paint.Align.CENTER);
        return centered;
    }

    private Paint dotPaint() {
        dot.setColor(accent);
        dot.setStyle(Paint.Style.FILL);
        return dot;
    }

    /* ------------------------------------------------------------------ */
    /* Touch scrub                                                         */
    /* ------------------------------------------------------------------ */

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (values.length == 0) return super.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                float step = values.length == 1 ? plotW : plotW / Math.max(1, values.length - 1);
                int index = Math.round((event.getX() - padLeft) / step);
                selectedIndex = Math.max(0, Math.min(values.length - 1, index));
                getParent().requestDisallowInterceptTouchEvent(true);
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                selectedIndex = -1;
                invalidate();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }
}
