package com.insta.nova.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.insta.nova.R;

/**
 * The Nova core: a breathing orb, two tilted orbit rings with moons, a rotating lens
 * flare and a shooting star every 6–10 seconds. 30 fps cap; every paint and shader is
 * created once in {@link #onSizeChanged}, so {@link #onDraw} performs no allocations.
 */
public class NovaHeroView extends View {

    public interface OnCoreTapListener {
        void onCoreTapped();
    }

    private static final long FRAME_MS = 33L;
    private static final float ORBIT_PERIOD_1 = 18f;
    private static final float ORBIT_PERIOD_2 = 26f;

    private final Paint orbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint moonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint moonGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flarePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shootingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float cx;
    private float cy;
    private float orbRadius;
    private float ringOne;
    private float ringTwo;
    private float moonRadius;
    private float density;

    private boolean running;
    private long startedAt;
    private float waveProgress = 1f;
    private ValueAnimator waveAnimator;

    private long nextShootingAt;
    private long shootingStartedAt = -1L;
    private float shootingAngle;

    private OnCoreTapListener tapListener;

    private final Runnable frame = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            invalidate();
            postDelayed(this, FRAME_MS);
        }
    };

    public NovaHeroView(Context context) {
        this(context, null);
    }

    public NovaHeroView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        setClickable(true);
        setFocusable(true);
        setContentDescription(context.getString(R.string.nova_about));
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(1.2f * density);
        ringPaint.setStrokeCap(Paint.Cap.ROUND);
        moonPaint.setStyle(Paint.Style.FILL);
        moonGlowPaint.setStyle(Paint.Style.FILL);
        haloPaint.setStyle(Paint.Style.FILL);
        shootingPaint.setStyle(Paint.Style.STROKE);
        shootingPaint.setStrokeWidth(1.4f * density);
        shootingPaint.setStrokeCap(Paint.Cap.ROUND);
        wavePaint.setStyle(Paint.Style.STROKE);
        wavePaint.setStrokeWidth(1.6f * density);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        cx = w / 2f;
        cy = h / 2f + 6f * density;
        orbRadius = Math.min(w, h) * 0.155f;
        ringOne = orbRadius * 1.85f;
        ringTwo = orbRadius * 2.55f;
        moonRadius = 2.6f * density;

        int white = Color.WHITE;
        int cyan = ContextCompat.getColor(getContext(), R.color.nova_cyan);
        int violet = ContextCompat.getColor(getContext(), R.color.nova_violet);
        int magenta = ContextCompat.getColor(getContext(), R.color.nova_magenta);

        orbPaint.setShader(new RadialGradient(0, 0, Math.max(1f, orbRadius),
                new int[]{white, NovaUi.withAlpha(cyan, 0.95f), NovaUi.withAlpha(violet, 0.85f), NovaUi.withAlpha(violet, 0f)},
                new float[]{0f, 0.28f, 0.62f, 1f}, Shader.TileMode.CLAMP));
        haloPaint.setShader(new RadialGradient(0, 0, Math.max(1f, orbRadius * 3.1f),
                new int[]{NovaUi.withAlpha(cyan, 0.22f), NovaUi.withAlpha(violet, 0.10f), Color.TRANSPARENT},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        ringPaint.setShader(new LinearGradient(-ringTwo, 0, ringTwo, 0,
                new int[]{NovaUi.withAlpha(cyan, 0.05f), NovaUi.withAlpha(cyan, 0.55f),
                        NovaUi.withAlpha(magenta, 0.55f), NovaUi.withAlpha(magenta, 0.05f)},
                new float[]{0f, 0.35f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        moonPaint.setColor(Color.WHITE);
        moonGlowPaint.setColor(NovaUi.withAlpha(cyan, 0.30f));

        flarePaint.setShader(new LinearGradient(-ringTwo * 1.25f, 0, ringTwo * 1.25f, 0,
                new int[]{Color.TRANSPARENT, NovaUi.withAlpha(white, 0.34f), Color.TRANSPARENT},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        flarePaint.setStrokeWidth(1f * density);

        shootingPaint.setShader(new LinearGradient(0, 0, 90f * density, 0,
                new int[]{Color.TRANSPARENT, NovaUi.withAlpha(white, 0.9f)},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        wavePaint.setColor(NovaUi.withAlpha(cyan, 0.9f));
        scheduleShooting(android.os.SystemClock.uptimeMillis());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (orbRadius <= 0) return;
        long now = android.os.SystemClock.uptimeMillis();
        float seconds = (now - startedAt) / 1000f;

        // Halo.
        canvas.save();
        canvas.translate(cx, cy);
        canvas.drawCircle(0, 0, orbRadius * 3.1f, haloPaint);

        // Breathing core (0.92 – 1.08 over 3.2 s).
        float breath = 0.92f + 0.08f * (float) Math.sin(seconds * (6.283f / 3.2f));
        canvas.save();
        canvas.scale(breath, breath);
        canvas.drawCircle(0, 0, orbRadius, orbPaint);
        canvas.restore();

        // Orbit rings (tilted ellipses).
        drawOrbit(canvas, ringOne, 0.42f, seconds * (360f / ORBIT_PERIOD_1), 3);
        drawOrbit(canvas, ringTwo, 0.30f, -seconds * (360f / ORBIT_PERIOD_2), 2);

        // Lens-flare cross.
        canvas.save();
        canvas.rotate(seconds * 6f);
        canvas.drawLine(-ringTwo * 1.25f, 0, ringTwo * 1.25f, 0, flarePaint);
        canvas.rotate(90f);
        canvas.drawLine(-ringTwo * 0.55f, 0, ringTwo * 0.55f, 0, flarePaint);
        canvas.restore();
        canvas.restore();

        // Shockwave.
        if (waveProgress < 1f) {
            float radius = orbRadius * (1f + 2.6f * waveProgress);
            wavePaint.setAlpha((int) (255 * (1f - waveProgress)));
            canvas.drawCircle(cx, cy, radius, wavePaint);
        }

        // Shooting star every 6–10 s.
        if (shootingStartedAt < 0 && now >= nextShootingAt) {
            shootingStartedAt = now;
            shootingAngle = -0.45f + (float) Math.random() * 0.5f;
            scheduleShooting(now + 6000L + (long) (Math.random() * 4000L));
        }
        if (shootingStartedAt >= 0) {
            float p = (now - shootingStartedAt) / 900f;
            if (p >= 1f) {
                shootingStartedAt = -1L;
            } else {
                float startX = cx + (float) Math.cos(shootingAngle) * ringTwo * 1.6f;
                float startY = cy + (float) Math.sin(shootingAngle) * ringTwo * 0.8f - ringTwo;
                float len = 80f * density * Math.min(1f, p * 3f);
                float dx = (float) Math.cos(shootingAngle + 0.9f) * len;
                float dy = (float) Math.sin(shootingAngle + 0.9f) * len;
                shootingPaint.setAlpha((int) (255 * (1f - p)));
                canvas.drawLine(startX, startY, startX + dx, startY + dy, shootingPaint);
            }
        }
    }

    private void drawOrbit(Canvas canvas, float radius, float flatten, float rotation, int moons) {
        canvas.save();
        canvas.rotate(rotation);
        canvas.save();
        canvas.scale(1f, flatten);
        canvas.drawCircle(0, 0, radius, ringPaint);
        canvas.restore();

        for (int i = 0; i < moons; i++) {
            float angle = (float) (i * (6.283f / moons) + rotation * 0.012f);
            float px = (float) Math.cos(angle) * radius;
            float py = (float) Math.sin(angle) * radius * flatten;
            float rad = (float) Math.toRadians(rotation);
            float x = px * (float) Math.cos(rad) - py * (float) Math.sin(rad);
            float y = px * (float) Math.sin(rad) + py * (float) Math.cos(rad);
            moonGlowPaint.setAlpha(90);
            canvas.drawCircle(x, y, moonRadius * 2.6f, moonGlowPaint);
            canvas.drawCircle(x, y, moonRadius, moonPaint);
        }
        canvas.restore();
    }

    private void scheduleShooting(long at) {
        nextShootingAt = at;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            float dx = event.getX() - cx;
            float dy = event.getY() - cy;
            if (Math.hypot(dx, dy) <= orbRadius * 2.1f) {
                shockwave();
                NovaUi.haptic(this, true);
                if (tapListener != null) tapListener.onCoreTapped();
                performClick();
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private void shockwave() {
        if (waveAnimator != null) waveAnimator.cancel();
        if (!NovaUi.animationsEnabled(getContext())) {
            waveProgress = 1f;
            invalidate();
            return;
        }
        waveAnimator = ValueAnimator.ofFloat(0f, 1f);
        waveAnimator.setDuration(700);
        waveAnimator.addUpdateListener(a -> {
            waveProgress = (float) a.getAnimatedValue();
            invalidate();
        });
        waveAnimator.start();
    }

    public void setOnCoreTapListener(OnCoreTapListener listener) {
        this.tapListener = listener;
    }

    /** Pauses the 30 fps loop; called by the host fragment/activity in onStop. */
    public void pause() {
        running = false;
        removeCallbacks(frame);
        if (waveAnimator != null) waveAnimator.cancel();
    }

    /** Resumes the loop; called from onStart. */
    public void resume() {
        if (running) return;
        if (!NovaUi.animationsEnabled(getContext())) {
            invalidate();
            return;
        }
        running = true;
        if (startedAt == 0L) startedAt = android.os.SystemClock.uptimeMillis();
        removeCallbacks(frame);
        post(frame);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        resume();
    }

    @Override
    protected void onDetachedFromWindow() {
        pause();
        super.onDetachedFromWindow();
    }

    /** Optional gyroscope parallax: shift up to 8dp. */
    public void setParallax(float x, float y) {
        setTranslationX(Math.max(-1f, Math.min(1f, x)) * NovaUi.dpFloat(getContext(), 8));
        setTranslationY(Math.max(-1f, Math.min(1f, y)) * NovaUi.dpFloat(getContext(), 8));
    }
}
