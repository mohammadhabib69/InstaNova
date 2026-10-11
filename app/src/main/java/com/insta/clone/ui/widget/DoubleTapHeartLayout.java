package com.insta.clone.ui.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.insta.clone.R;
import java.util.Random;

/**
 * Custom layout that intercepts double-taps on media to display the Instagram white heart animation
 * (100dp, 0->1.3->1 bounce, 400ms hold, 250ms fade, +/-15° tilt) and trigger haptic feedback.
 */
public class DoubleTapHeartLayout extends FrameLayout {

    private ImageView heartImageView;
    private GestureDetector gestureDetector;
    private OnDoubleTapLikeListener likeListener;
    private final Random random = new Random();

    public interface OnDoubleTapLikeListener {
        void onDoubleTapLike();
    }

    public DoubleTapHeartLayout(@NonNull Context context) {
        super(context);
        init(context);
    }

    public DoubleTapHeartLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public DoubleTapHeartLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        heartImageView = new ImageView(context);
        heartImageView.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_heart_white));
        int size = (int) getResources().getDimension(R.dimen.double_tap_heart_size);
        LayoutParams lp = new LayoutParams(size, size);
        lp.gravity = Gravity.CENTER;
        heartImageView.setLayoutParams(lp);
        heartImageView.setVisibility(INVISIBLE);
        addView(heartImageView);

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                animateHeart();
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                if (likeListener != null) {
                    likeListener.onDoubleTapLike();
                }
                return true;
            }
        });
    }

    public void setOnDoubleTapLikeListener(OnDoubleTapLikeListener listener) {
        this.likeListener = listener;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Enforce 1:1 square aspect ratio based on width
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int squareSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
        super.onMeasure(squareSpec, squareSpec);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        gestureDetector.onTouchEvent(ev);
        return false; // Allow child view (e.g. ViewPager2) to also receive touch events
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event);
    }

    public void animateHeart() {
        heartImageView.setVisibility(VISIBLE);
        heartImageView.setAlpha(1.0f);
        heartImageView.setScaleX(0f);
        heartImageView.setScaleY(0f);

        // Random tilt +/- 15 degrees
        float rotation = (random.nextFloat() * 30f) - 15f;
        heartImageView.setRotation(rotation);

        // 1. Pop in: Scale 0 -> 1.3 -> 1.0 (350 ms)
        ObjectAnimator scaleXIn = ObjectAnimator.ofFloat(heartImageView, View.SCALE_X, 0f, 1.2f, 1.0f);
        ObjectAnimator scaleYIn = ObjectAnimator.ofFloat(heartImageView, View.SCALE_Y, 0f, 1.2f, 1.0f);
        scaleXIn.setDuration(350);
        scaleYIn.setDuration(350);
        scaleXIn.setInterpolator(new OvershootInterpolator(1.5f));
        scaleYIn.setInterpolator(new OvershootInterpolator(1.5f));

        AnimatorSet popIn = new AnimatorSet();
        popIn.playTogether(scaleXIn, scaleYIn);

        // 2. Fade out after 400ms hold (250 ms fade)
        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(heartImageView, View.ALPHA, 1.0f, 0.0f);
        fadeOut.setDuration(250);
        fadeOut.setStartDelay(400);

        AnimatorSet fullAnimation = new AnimatorSet();
        fullAnimation.playSequentially(popIn, fadeOut);
        fullAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                heartImageView.setVisibility(INVISIBLE);
            }
        });

        fullAnimation.start();
    }
}
