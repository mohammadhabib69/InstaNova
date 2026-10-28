package com.insta.nova.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.insta.nova.R;

/**
 * Soft coloured glow drawn behind active elements. Replaces elevation shadows, which
 * the Nova look deliberately avoids.
 */
public class GlowView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int accent;
    private float intensity = 0.42f;

    public GlowView(Context context) {
        this(context, null);
    }

    public GlowView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        accent = ContextCompat.getColor(context, R.color.nova_violet);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setAccent(int color) {
        accent = color;
        buildShader();
        invalidate();
    }

    public void setIntensity(float intensity) {
        this.intensity = Math.max(0f, Math.min(1f, intensity));
        buildShader();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        buildShader();
    }

    private void buildShader() {
        if (getWidth() == 0 || getHeight() == 0) return;
        float radius = Math.max(getWidth(), getHeight()) * 0.5f;
        paint.setShader(new RadialGradient(getWidth() / 2f, getHeight() / 2f, radius,
                new int[]{NovaUi.withAlpha(accent, intensity), NovaUi.withAlpha(accent, intensity * 0.35f), Color.TRANSPARENT},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawCircle(getWidth() / 2f, getHeight() / 2f,
                Math.max(getWidth(), getHeight()) * 0.5f, paint);
    }
}
