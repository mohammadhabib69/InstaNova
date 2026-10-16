package com.insta.clone.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * PAGE 11 — freehand drawing layer for the story editor.
 *
 * Strokes are stored as paths in view coordinates together with their colour and
 * width. {@link #renderScaled(Canvas, float, float)} re-plays every stroke onto an
 * arbitrary canvas so the flattened 1080x1920 export matches what the user drew.
 */
public class OverlayDrawView extends View {

    private static final class Stroke {
        final Path path = new Path();
        final int color;
        final float width;
        Stroke(int color, float width) {
            this.color = color;
            this.width = width;
        }
    }

    private final List<Stroke> strokes = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Stroke active;

    private int strokeColor;
    private float strokeWidth;

    public OverlayDrawView(Context context) {
        super(context);
        init();
    }

    public OverlayDrawView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public OverlayDrawView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setDither(true);
    }

    public void setStrokeColor(int color) {
        this.strokeColor = color;
    }

    public void setStrokeWidth(float widthPx) {
        this.strokeWidth = widthPx;
    }

    public void undo() {
        if (!strokes.isEmpty()) {
            strokes.remove(strokes.size() - 1);
            invalidate();
        }
    }

    public void clearAll() {
        strokes.clear();
        active = null;
        invalidate();
    }

    public boolean isEmpty() {
        return strokes.isEmpty();
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                active = new Stroke(strokeColor, strokeWidth);
                active.path.moveTo(event.getX(), event.getY());
                strokes.add(active);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (active != null) {
                    active.path.lineTo(event.getX(), event.getY());
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (active != null) {
                    active.path.lineTo(event.getX(), event.getY());
                    active = null;
                    invalidate();
                }
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        for (Stroke stroke : strokes) {
            paint.setColor(stroke.color);
            paint.setStrokeWidth(stroke.width);
            canvas.drawPath(stroke.path, paint);
        }
    }

    /**
     * Draws every stroke onto {@code canvas} scaled by (sx, sy). Used by the
     * editor when flattening the overlays into the exported JPEG.
     */
    public void renderScaled(@NonNull Canvas canvas, float sx, float sy) {
        int save = canvas.save();
        canvas.scale(sx, sy);
        for (Stroke stroke : strokes) {
            paint.setColor(stroke.color);
            paint.setStrokeWidth(stroke.width);
            canvas.drawPath(stroke.path, paint);
        }
        canvas.restoreToCount(save);
    }
}
