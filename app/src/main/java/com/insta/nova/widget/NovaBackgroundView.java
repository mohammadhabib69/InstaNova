package com.insta.nova.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.insta.nova.R;

import java.util.Random;

/**
 * The Nova sky: vertical space gradient, two drifting nebulae and 60 twinkling stars.
 *
 * <p>Everything is allocated in {@link #onSizeChanged}; {@link #onDraw} only sets alpha
 * and draws. Frames are capped at 20 fps because the motion is deliberately slow, and
 * the loop stops whenever the view is detached or not visible.
 */
public class NovaBackgroundView extends View {

    private static final int STAR_COUNT = 60;
    private static final long FRAME_MS = 50L;

    private final Paint backgroundPaint = new Paint();
    private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nebulaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private LinearGradient backgroundGradient;
    private RadialGradient violetNebula;
    private RadialGradient cyanNebula;

    private final float[] starX = new float[STAR_COUNT];
    private final float[] starY = new float[STAR_COUNT];
    private final float[] starRadius = new float[STAR_COUNT];
    private final float[] starPhase = new float[STAR_COUNT];
    private final float[] starPeriod = new float[STAR_COUNT];

    private boolean running;
    private boolean lightMode;
    private long startedAt;
    private float widthPx;
    private float heightPx;

    private final Runnable frame = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            invalidate();
            postDelayed(this, FRAME_MS);
        }
    };

    public NovaBackgroundView(Context context) {
        this(context, null);
    }

    public NovaBackgroundView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setLayerType(LAYER_TYPE_HARDWARE, null);
        starPaint.setStyle(Paint.Style.FILL);
        nebulaPaint.setStyle(Paint.Style.FILL);
        nebulaPaint.setDither(true);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        widthPx = w;
        heightPx = h;
        lightMode = !NovaUi.isNight(getContext());

        int top = ContextCompat.getColor(getContext(), R.color.nova_bg);
        int bottom = lightMode ? Color.parseColor("#E9ECFF")
                : ContextCompat.getColor(getContext(), R.color.nova_surface);
        backgroundGradient = new LinearGradient(0, 0, 0, h, top, bottom, Shader.TileMode.CLAMP);
        backgroundPaint.setShader(backgroundGradient);

        int violet = ContextCompat.getColor(getContext(), R.color.nova_violet);
        int cyan = ContextCompat.getColor(getContext(), R.color.nova_cyan);
        float violetAlpha = lightMode ? 0.10f : 0.22f;
        float cyanAlpha = lightMode ? 0.10f : 0.18f;
        violetNebula = new RadialGradient(0, 0, Math.max(1f, w * 0.55f),
                new int[]{NovaUi.withAlpha(violet, violetAlpha), NovaUi.withAlpha(violet, 0f)},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP);
        cyanNebula = new RadialGradient(0, 0, Math.max(1f, w * 0.45f),
                new int[]{NovaUi.withAlpha(cyan, cyanAlpha), NovaUi.withAlpha(cyan, 0f)},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP);

        Random random = new Random(0x4E4F5641L); // stable sky between rotations
        float minR = NovaUi.dpFloat(getContext(), 1f);
        float maxR = NovaUi.dpFloat(getContext(), 2.5f);
        for (int i = 0; i < STAR_COUNT; i++) {
            starX[i] = random.nextFloat() * w;
            starY[i] = random.nextFloat() * h;
            starRadius[i] = minR + random.nextFloat() * (maxR - minR);
            starPhase[i] = random.nextFloat() * 6.283f;
            starPeriod[i] = 2f + random.nextFloat() * 3f;
        }
        starPaint.setColor(lightMode ? Color.parseColor("#7C4DFF") : Color.WHITE);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (backgroundGradient == null || widthPx <= 0) return;

        float seconds = (android.os.SystemClock.uptimeMillis() - startedAt) / 1000f;
        canvas.drawRect(0, 0, widthPx, heightPx, backgroundPaint);

        // Nebulae drift on slow, out-of-phase sine paths.
        canvas.save();
        canvas.translate(widthPx * 0.18f + (float) Math.sin(seconds * 0.05f) * widthPx * 0.06f,
                heightPx * 0.22f + (float) Math.cos(seconds * 0.04f) * heightPx * 0.03f);
        nebulaPaint.setShader(violetNebula);
        canvas.drawCircle(0, 0, widthPx * 0.55f, nebulaPaint);
        canvas.restore();

        canvas.save();
        canvas.translate(widthPx * 0.86f + (float) Math.cos(seconds * 0.045f) * widthPx * 0.05f,
                heightPx * 0.58f + (float) Math.sin(seconds * 0.06f) * heightPx * 0.04f);
        nebulaPaint.setShader(cyanNebula);
        canvas.drawCircle(0, 0, widthPx * 0.45f, nebulaPaint);
        canvas.restore();

        // Stars twinkle between 20% and 100% alpha.
        for (int i = 0; i < STAR_COUNT; i++) {
            float twinkle = 0.6f + 0.4f * (float) Math.sin(seconds * (6.283f / starPeriod[i]) + starPhase[i]);
            starPaint.setAlpha((int) (255 * Math.max(0.2f, Math.min(1f, twinkle))));
            canvas.drawCircle(starX[i], starY[i], starRadius[i], starPaint);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        start();
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) start();
        else stop();
    }

    /** Starts the sky unless animations are disabled for the device. */
    public void start() {
        if (running) return;
        if (!NovaUi.animationsEnabled(getContext())) {
            invalidate();
            return;
        }
        running = true;
        startedAt = android.os.SystemClock.uptimeMillis();
        removeCallbacks(frame);
        post(frame);
    }

    public void stop() {
        running = false;
        removeCallbacks(frame);
    }
}
