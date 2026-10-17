package com.insta.nova.ui.widget;

import android.content.Context;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

/**
 * Custom ImageView supporting pinch-to-zoom, dragging while zoomed, and double-tap reset.
 */
public class PinchZoomImageView extends AppCompatImageView {

    private static final int NONE = 0;
    private static final int DRAG = 1;
    private int mode = NONE;

    private final PointF start = new PointF();
    private float saveScale = 1.0f;

    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;

    public PinchZoomImageView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public PinchZoomImageView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public PinchZoomImageView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setScaleType(ScaleType.FIT_CENTER);
        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(@NonNull MotionEvent e) {
                if (saveScale > 1.05f) {
                    resetZoom();
                } else {
                    saveScale = 2.5f;
                    setScaleX(2.5f);
                    setScaleY(2.5f);
                }
                return true;
            }
        });
    }

    public void resetZoom() {
        saveScale = 1.0f;
        setScaleX(1.0f);
        setScaleY(1.0f);
        setTranslationX(0f);
        setTranslationY(0f);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (saveScale > 1.0f) {
                    mode = DRAG;
                    start.set(event.getX() - getTranslationX(), event.getY() - getTranslationY());
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (mode == DRAG && saveScale > 1.0f) {
                    setTranslationX(event.getX() - start.x);
                    setTranslationY(event.getY() - start.y);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                mode = NONE;
                break;
        }
        return true;
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float scaleFactor = detector.getScaleFactor();
            saveScale *= scaleFactor;
            if (saveScale < 1.0f) {
                saveScale = 1.0f;
            } else if (saveScale > 5.0f) {
                saveScale = 5.0f;
            }
            setScaleX(saveScale);
            setScaleY(saveScale);
            if (saveScale == 1.0f) {
                setTranslationX(0f);
                setTranslationY(0f);
            }
            return true;
        }
    }
}
