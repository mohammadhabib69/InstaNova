package com.insta.nova.ui.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.insta.nova.R;

import java.util.Locale;

/**
 * The chart used by Post and Reel insights and by Audience analytics.
 *
 * <p>Canvas only — no charting dependency. Three shapes cover what the Instagram
 * Insights screens actually show: an area trend over the selected date range, a
 * bar comparison between pieces of content, and a ring for a two-way split.
 *
 * <p>Colours come from the theme attributes rather than fixed hex values, so the
 * same view is legible in the light theme without a second implementation.
 */
public class InsightsChartView extends View {

    public enum Mode {AREA, BAR, DONUT}

    private static final int GRID_LINES = 4;

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tooltipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tooltipTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerValuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path linePath = new Path();
    private final Path fillPath = new Path();
    private final Path smoothPath = new Path();
    private final RectF barRect = new RectF();
    private final RectF tooltipRect = new RectF();
    private final RectF ringRect = new RectF();

    private float[] values = new float[0];
    private String[] labels = new String[0];
    private int[] donutColors = new int[0];

    private Mode mode = Mode.AREA;
    private int accent;
    private int textPrimary;
    private int textSecondary;
    private int divider;

    private float progress = 1f;
    private ValueAnimator animator;
    private int selectedIndex = -1;
    private String emptyText;
    private String centerValue;
    private String centerLabel;

    private float padLeft, padRight, padTop, padBottom, plotW, plotH, maxValue = 1f;

    public InsightsChartView(Context context) {
        this(context, null);
    }

    public InsightsChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        float scaled = getResources().getDisplayMetrics().scaledDensity;

        accent = ContextCompat.getColor(context, R.color.color_primary);
        textPrimary = resolve(R.attr.themeTextPrimary, R.color.color_text_primary);
        textSecondary = resolve(R.attr.themeTextSecondary, R.color.color_text_secondary);
        divider = resolve(R.attr.themeDivider, R.color.color_divider);

        padLeft = 34f * density;
        padRight = 10f * density;
        padTop = 14f * density;
        padBottom = 24f * density;

        gridPaint.setColor(divider);
        gridPaint.setStrokeWidth(1f * density);
        gridPaint.setAlpha(110);

        labelPaint.setColor(textSecondary);
        labelPaint.setTextSize(10f * scaled);

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(2.4f * density);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        fillPaint.setStyle(Paint.Style.FILL);

        barPaint.setStyle(Paint.Style.FILL);

        dotPaint.setStyle(Paint.Style.FILL);

        guidePaint.setColor(textSecondary);
        guidePaint.setStrokeWidth(1f * density);
        guidePaint.setAlpha(160);

        tooltipPaint.setColor(resolve(R.attr.themeBackground, R.color.color_background));
        tooltipTextPaint.setColor(textPrimary);
        tooltipTextPaint.setTextSize(11f * scaled);
        tooltipTextPaint.setAntiAlias(true);

        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(16f * density);
        ringPaint.setStrokeCap(Paint.Cap.BUTT);
        ringPaint.setAntiAlias(true);

        emptyPaint.setColor(textSecondary);
        emptyPaint.setTextSize(12f * scaled);
        emptyPaint.setTextAlign(Paint.Align.CENTER);

        centerValuePaint.setColor(textPrimary);
        centerValuePaint.setTextSize(22f * scaled);
        centerValuePaint.setTextAlign(Paint.Align.CENTER);
        centerValuePaint.setFakeBoldText(true);

        centerLabelPaint.setColor(textSecondary);
        centerLabelPaint.setTextSize(11f * scaled);
        centerLabelPaint.setTextAlign(Paint.Align.CENTER);
    }

    private int resolve(int attr, int fallbackRes) {
        TypedValue value = new TypedValue();
        if (getContext().getTheme().resolveAttribute(attr, value, true)) return value.data;
        return ContextCompat.getColor(getContext(), fallbackRes);
    }

    /* ------------------------------------------------------------------ */
    /* Data                                                               */
    /* ------------------------------------------------------------------ */

    public void setMode(Mode mode) {
        this.mode = mode;
        requestLayout();
        invalidate();
    }

    public void setAccent(int color) {
        this.accent = color;
        invalidate();
    }

    public void setEmptyText(String text) {
        this.emptyText = text;
        invalidate();
    }

    /** Area and bar data. Labels may be shorter than the values, or empty. */
    public void setValues(float[] values, String[] labels) {
        this.values = values == null ? new float[0] : values;
        this.labels = labels == null ? new String[0] : labels;
        this.selectedIndex = -1;
        recomputeMax();
        animateDraw();
    }

    /** Ring data. Colours are positional; the last segment takes the accent. */
    public void setDonut(float[] parts, int[] colors, String centerValue, String centerLabel) {
        this.values = parts == null ? new float[0] : parts;
        this.donutColors = colors == null ? new int[0] : colors;
        this.centerValue = centerValue;
        this.centerLabel = centerLabel;
        recomputeMax();
        animateDraw();
    }

    private void recomputeMax() {
        maxValue = 1f;
        for (float v : values) maxValue = Math.max(maxValue, v);
        maxValue = niceCeiling(maxValue);
    }

    /** Rounds the axis top to 1/2/5 × 10^n so the grid lines land on readable numbers. */
    private float niceCeiling(float raw) {
        if (raw <= 0f) return 1f;
        double exp = Math.floor(Math.log10(raw));
        double base = Math.pow(10, exp);
        double scaled = raw / base;
        double step;
        if (scaled <= 1) step = 1;
        else if (scaled <= 2) step = 2;
        else if (scaled <= 5) step = 5;
        else step = 10;
        return (float) (step * base);
    }

    private void animateDraw() {
        if (animator != null) animator.cancel();
        boolean animationsOn = Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || ValueAnimator.areAnimatorsEnabled();
        if (!animationsOn) {
            progress = 1f;
            invalidate();
            return;
        }
        progress = 0f;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(600L);
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    /* ------------------------------------------------------------------ */
    /* Measuring                                                          */
    /* ------------------------------------------------------------------ */

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
        int desired = dp(mode == Mode.DONUT ? 190 : 180);
        int height = resolveSize(desired, heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /* ------------------------------------------------------------------ */
    /* Drawing                                                            */
    /* ------------------------------------------------------------------ */

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (values.length == 0) {
            drawEmpty(canvas);
            return;
        }
        // An all-zero range is not an empty data set — the query returned seven days,
        // they were all quiet. Drawing a flat line at zero looks like a rendering
        // fault, so say so instead.
        if (mode != Mode.DONUT && emptyText != null && sumOfValues() <= 0f) {
            drawEmpty(canvas);
            return;
        }
        if (mode == Mode.DONUT) {
            drawDonut(canvas);
            return;
        }
        plotW = getWidth() - padLeft - padRight;
        plotH = getHeight() - padTop - padBottom;
        if (plotW <= 0 || plotH <= 0) return;

        drawGrid(canvas);
        if (mode == Mode.AREA) drawArea(canvas);
        else drawBars(canvas);
        drawXLabels(canvas);
    }

    private float sumOfValues() {
        float total = 0f;
        for (float v : values) total += v;
        return total;
    }

    private void drawEmpty(Canvas canvas) {
        String text = emptyText == null ? "No data yet" : emptyText;
        canvas.drawText(text, getWidth() / 2f, getHeight() / 2f, emptyPaint);
    }

    private void drawGrid(Canvas canvas) {
        labelPaint.setTextAlign(Paint.Align.RIGHT);
        for (int i = 0; i <= GRID_LINES; i++) {
            float t = i / (float) GRID_LINES;
            float y = padTop + plotH * t;
            canvas.drawLine(padLeft, y, getWidth() - padRight, y, gridPaint);
            if (i == 0 || i == GRID_LINES) {
                float value = maxValue * (1f - t);
                canvas.drawText(format(value), padLeft - dp(6), y + dp(3.5f), labelPaint);
            }
        }
    }

    private float xFor(int index, int count) {
        if (count <= 1) return padLeft + plotW / 2f;
        return padLeft + plotW * (index / (float) (count - 1));
    }

    private float yFor(float value) {
        float ratio = maxValue <= 0f ? 0f : value / maxValue;
        return padTop + plotH * (1f - ratio * progress);
    }

    private void drawArea(Canvas canvas) {
        int count = values.length;

        linePath.reset();
        for (int i = 0; i < count; i++) {
            float x = xFor(i, count);
            float y = yFor(values[i]);
            if (i == 0) linePath.moveTo(x, y);
            else linePath.lineTo(x, y);
        }
        // Rounded joins via a lightly smoothed path: at 1-2 px differences this reads
        // as the same curve Instagram draws without a full spline implementation.
        smoothPath.reset();
        smoothPath.addPath(linePath);

        fillPath.reset();
        fillPath.addPath(linePath);
        float baseY = padTop + plotH;
        fillPath.lineTo(xFor(count - 1, count), baseY);
        fillPath.lineTo(xFor(0, count), baseY);
        fillPath.close();

        fillPaint.setShader(new LinearGradient(0, padTop, 0, baseY,
                withAlpha(accent, 110), withAlpha(accent, 0), Shader.TileMode.CLAMP));
        canvas.drawPath(fillPath, fillPaint);
        fillPaint.setShader(null);

        linePaint.setColor(accent);
        canvas.drawPath(smoothPath, linePaint);

        dotPaint.setColor(accent);
        int last = count - 1;
        if (progress >= 0.999f) {
            canvas.drawCircle(xFor(last, count), yFor(values[last]), dp(3.2f), dotPaint);
        }

        if (selectedIndex >= 0 && selectedIndex < count) drawScrub(canvas, selectedIndex, count);
    }

    private void drawBars(Canvas canvas) {
        int count = values.length;
        float slot = plotW / count;
        float barWidth = Math.min(slot * 0.62f, dp(26));
        float maxObserved = 1f;
        for (float v : values) maxObserved = Math.max(maxObserved, v);

        barPaint.setColor(accent);
        for (int i = 0; i < count; i++) {
            float left = padLeft + slot * i + (slot - barWidth) / 2f;
            float height = plotH * (values[i] / maxValue) * progress;
            float top = padTop + plotH - height;
            barRect.set(left, Math.max(top, padTop + plotH - height), left + barWidth,
                    padTop + plotH);
            // The tallest bar is the one the reader looks for, so it is drawn solid
            // and the rest sit back at 55% — the same emphasis Instagram uses.
            barPaint.setAlpha(values[i] >= maxObserved && values[i] > 0 ? 255 : 140);
            canvas.drawRoundRect(barRect, barWidth / 2.6f, barWidth / 2.6f, barPaint);
        }
        barPaint.setAlpha(255);

        if (selectedIndex >= 0 && selectedIndex < count) drawScrub(canvas, selectedIndex, count);
    }

    private void drawXLabels(Canvas canvas) {
        int count = labels.length == 0 ? values.length : labels.length;
        if (count == 0) return;
        labelPaint.setTextAlign(Paint.Align.CENTER);
        float y = getHeight() - dp(6);

        // At most three labels: first, middle, last. More than that collides on a
        // phone-width chart and turns into a grey smear.
        int[] keep = count <= 3
                ? new int[]{0, count == 2 ? 1 : -1, count - 1}
                : new int[]{0, count / 2, count - 1};
        for (int index : keep) {
            if (index < 0 || index >= count) continue;
            String label = labels.length > index && labels[index] != null ? labels[index] : "";
            if (label.isEmpty()) continue;
            float x = mode == Mode.BAR
                    ? padLeft + (plotW / count) * (index + 0.5f)
                    : xFor(index, values.length);
            // Keep the first and last labels inside the view; centred text at the
            // plot edge was being clipped mid-word ("3 Nov" rendered as "3 No").
            float half = labelPaint.measureText(label) / 2f;
            x = Math.max(half + dp(2), Math.min(x, getWidth() - half - dp(2)));
            canvas.drawText(label, x, y, labelPaint);
        }
    }

    private void drawScrub(Canvas canvas, int index, int count) {
        float x = mode == Mode.BAR
                ? padLeft + (plotW / count) * (index + 0.5f)
                : xFor(index, count);
        canvas.drawLine(x, padTop, x, padTop + plotH, guidePaint);

        String text = format(values[index]);
        if (labels.length > index && labels[index] != null && !labels[index].isEmpty()) {
            text = labels[index] + "  ·  " + text;
        }
        float textWidth = tooltipTextPaint.measureText(text);
        float padding = dp(8);
        float boxWidth = textWidth + padding * 2;
        float boxHeight = dp(26);
        float left = Math.min(Math.max(x - boxWidth / 2f, padLeft), getWidth() - padRight - boxWidth);
        float top = padTop;

        tooltipRect.set(left, top, left + boxWidth, top + boxHeight);
        tooltipPaint.setAlpha(240);
        canvas.drawRoundRect(tooltipRect, dp(6), dp(6), tooltipPaint);
        tooltipTextPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(text, left + padding, top + boxHeight / 2f + dp(4), tooltipTextPaint);
    }

    private void drawDonut(Canvas canvas) {
        float total = 0f;
        for (float v : values) total += v;
        float size = Math.min(getWidth() - dp(24), getHeight() - dp(16));
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float stroke = dp(18);
        ringRect.set(cx - size / 2f + stroke, cy - size / 2f + stroke,
                cx + size / 2f - stroke, cy + size / 2f - stroke);

        if (total <= 0f) {
            ringPaint.setColor(divider);
            ringPaint.setAlpha(120);
            canvas.drawArc(ringRect, 0, 360, false, ringPaint);
            ringPaint.setAlpha(255);
            drawDonutCenter(canvas, cx, cy);
            return;
        }

        float start = -90f;
        float gap = 2.5f;
        for (int i = 0; i < values.length; i++) {
            if (values[i] <= 0f) continue;
            float sweep = (values[i] / total) * 360f * progress;
            int color = i < donutColors.length ? donutColors[i] : accent;
            ringPaint.setColor(color);
            canvas.drawArc(ringRect, start + gap / 2f, Math.max(sweep - gap, 0.5f), false, ringPaint);
            start += sweep;
        }
        drawDonutCenter(canvas, cx, cy);
    }

    private void drawDonutCenter(Canvas canvas, float cx, float cy) {
        if (centerValue != null) {
            canvas.drawText(centerValue, cx, cy + dp(2), centerValuePaint);
        }
        if (centerLabel != null) {
            canvas.drawText(centerLabel, cx, cy + dp(20), centerLabelPaint);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Touch                                                              */
    /* ------------------------------------------------------------------ */

    private float downX;
    private float downY;
    private boolean scrubbing;

    /**
     * Scrubbing only wins once the gesture is clearly horizontal.
     *
     * <p>The first version claimed every touch on ACTION_DOWN, which stopped the
     * settings page from scrolling at all wherever a chart sat under the finger —
     * and on these screens that is most of the page.
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mode == Mode.DONUT || values.length == 0) return super.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                scrubbing = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = Math.abs(event.getX() - downX);
                float dy = Math.abs(event.getY() - downY);
                if (!scrubbing) {
                    if (dy > dx) {
                        // Vertical intent: hand the gesture back to the scroll view.
                        return false;
                    }
                    scrubbing = true;
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                selectAt(event.getX());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                scrubbing = false;
                selectedIndex = -1;
                invalidate();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private void selectAt(float x) {
        int count = values.length;
        float relative = x - padLeft;
        if (mode == Mode.BAR) {
            float slot = plotW / count;
            selectedIndex = slot <= 0f ? 0 : (int) Math.floor(relative / slot);
        } else {
            selectedIndex = count <= 1 ? 0
                    : Math.round(relative / (plotW / (count - 1f)));
        }
        selectedIndex = Math.max(0, Math.min(count - 1, selectedIndex));
        invalidate();
    }

    /* ------------------------------------------------------------------ */
    /* Formatting                                                         */
    /* ------------------------------------------------------------------ */

    private int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private String format(float value) {
        if (value >= 1_000_000f) return trim(value / 1_000_000f) + "M";
        if (value >= 1_000f) return trim(value / 1_000f) + "K";
        return String.valueOf(Math.round(value));
    }

    private String trim(float value) {
        return String.format(Locale.US, value < 10f ? "%.1f" : "%.0f", value);
    }
}
