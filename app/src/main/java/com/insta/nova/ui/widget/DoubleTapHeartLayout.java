package com.insta.nova.ui.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.insta.nova.R;
import java.util.Random;

/**
 * Media container that shows Instagram's white heart burst on double-tap.
 *
 * <p>Two things had to be fixed to make the burst visible:
 * <ul>
 *   <li><b>Z-order.</b> The heart used to be added in {@code init()} — before the XML
 *       children inflate — so it was child #0 and every media view drew over it. It is
 *       now created in {@code onFinishInflate()}, i.e. after the media, and lifted with
 *       elevation/translationZ so no sibling can cover it.</li>
 *   <li><b>Gesture feed.</b> The detector was driven from both
 *       {@code onInterceptTouchEvent} and {@code onTouchEvent}, so the same ACTION_DOWN
 *       reached it twice and the double-tap window was unreliable. The detector is now
 *       fed exactly once per event in {@code dispatchTouchEvent()}.</li>
 * </ul>
 *
 * <p>The burst is a single ~800 ms run: scale 0 -> 1.25 -> 1.0 (280 ms), hold (220 ms),
 * fade 1 -> 0 (300 ms).
 */
public class DoubleTapHeartLayout extends FrameLayout {

    private static final long POP_MS = 280L;
    private static final long HOLD_MS = 220L;
    private static final long FADE_MS = 300L;

    private ImageView heartShadowView;
    private ImageView heartImageView;
    private GestureDetector gestureDetector;
    private AnimatorSet burst;
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
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                Log.d("HeartBurst", "onDoubleTap " + e.getX() + "," + e.getY());
                animateHeart();
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                if (likeListener != null) {
                    likeListener.onDoubleTapLike();
                }
                return true;
            }
        });
        // The media keeps receiving touches; this view only observes them.
        setClipChildren(false);
        setClipToPadding(false);
    }

    public void setOnDoubleTapLikeListener(OnDoubleTapLikeListener listener) {
        this.likeListener = listener;
    }

    /**
     * Added last so it is drawn last. Combining index order with elevation covers both
     * the plain FrameLayout ordering and any z-based ordering the platform applies.
     */
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        installHeart();
    }

    private void installHeart() {
        if (heartImageView != null) return;
        Context context = getContext();
        int size = (int) getResources().getDimension(R.dimen.double_tap_heart_size);
        heartImageView = new ImageView(context);
        // Soft dark copy behind the white heart: on a near-white photo a pure white burst
        // has no contrast at all, so the shadow is what makes it readable.
        heartShadowView = new ImageView(context);
        heartShadowView.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_heart_white));
        heartShadowView.setColorFilter(0x66000000);
        heartShadowView.setLayoutParams(new LayoutParams(size, size));
        heartShadowView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        heartShadowView.setVisibility(INVISIBLE);
        heartShadowView.setAlpha(0f);
        heartShadowView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        ((LayoutParams) heartShadowView.getLayoutParams()).gravity = Gravity.CENTER;
        addView(heartShadowView);

        heartImageView.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_heart_white));
        LayoutParams lp = new LayoutParams(size, size);
        lp.gravity = Gravity.CENTER;
        heartImageView.setLayoutParams(lp);
        heartImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        heartImageView.setVisibility(INVISIBLE);
        heartImageView.setAlpha(0f);
        heartImageView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        // Above every sibling, whatever order they were added in.
        heartImageView.setElevation(dp(24));
        heartImageView.setTranslationZ(dp(24));
        addView(heartImageView);
        bringChildToFront(heartImageView);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        installHeart();
        bringChildToFront(heartImageView);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Enforce 1:1 square aspect ratio based on width
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int squareSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
        super.onMeasure(squareSpec, squareSpec);
    }

    /**
     * One feed point for the detector. Intercepting here (instead of in
     * onInterceptTouchEvent + onTouchEvent) keeps child scrolling intact and stops the
     * same event being delivered twice.
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (gestureDetector != null) {
            if (ev.getActionMasked() == MotionEvent.ACTION_DOWN
                    || ev.getActionMasked() == MotionEvent.ACTION_UP) {
                Log.d("HeartBurst", "touch " + ev.getActionMasked() + " @" + ev.getX() + "," + ev.getY());
            }
            gestureDetector.onTouchEvent(ev);
        }
        return super.dispatchTouchEvent(ev);
    }

    /**
     * Returns true so this view becomes the touch target when no media child consumes the
     * gesture. Without it only ACTION_DOWN was delivered (nothing owned the stream), so the
     * detector never saw ACTION_UP and the double tap could not complete.
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return true;
    }

    /** Plays the burst. Safe to call repeatedly: a running run is restarted. */
    public void animateHeart() {
        if (heartImageView == null) installHeart();
        Log.d("HeartBurst", "animateHeart heart=" + heartImageView
                + " vis=" + (heartImageView == null ? -1 : heartImageView.getVisibility())
                + " idx=" + (heartImageView == null ? -1 : indexOfChild(heartImageView))
                + " children=" + getChildCount()
                + " size=" + (heartImageView == null ? -1 : heartImageView.getWidth()));
        if (heartImageView == null) return;

        if (burst != null) {
            burst.removeAllListeners();
            burst.cancel();
        }

        heartImageView.animate().cancel();
        heartShadowView.animate().cancel();
        heartShadowView.setVisibility(VISIBLE);
        heartShadowView.setAlpha(0f);
        heartShadowView.setScaleX(0f);
        heartShadowView.setScaleY(0f);
        heartShadowView.setRotation(heartImageView.getRotation());
        heartImageView.setVisibility(VISIBLE);
        heartImageView.setAlpha(0f);
        heartImageView.setScaleX(0f);
        heartImageView.setScaleY(0f);
        float tilt = (random.nextFloat() * 30f) - 15f;
        heartImageView.setRotation(tilt);
        heartShadowView.setRotation(tilt);
        bringChildToFront(heartShadowView);
        bringChildToFront(heartImageView);

        ObjectAnimator scaleX = ObjectAnimator.ofFloat(heartImageView, View.SCALE_X, 0f, 1.25f, 1.0f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(heartImageView, View.SCALE_Y, 0f, 1.25f, 1.0f);
        ObjectAnimator alphaIn = ObjectAnimator.ofFloat(heartImageView, View.ALPHA, 0f, 1f);

        for (ObjectAnimator a : new ObjectAnimator[]{scaleX, scaleY, alphaIn}) {
            a.setDuration(POP_MS);
        }
        scaleX.setInterpolator(new OvershootInterpolator(1.4f));
        scaleY.setInterpolator(new OvershootInterpolator(1.4f));
        alphaIn.setInterpolator(new DecelerateInterpolator());

        ObjectAnimator sx2 = ObjectAnimator.ofFloat(heartShadowView, View.SCALE_X, 0f, 1.28f, 1.03f);
        ObjectAnimator sy2 = ObjectAnimator.ofFloat(heartShadowView, View.SCALE_Y, 0f, 1.28f, 1.03f);
        ObjectAnimator a2 = ObjectAnimator.ofFloat(heartShadowView, View.ALPHA, 0f, 1f);
        for (ObjectAnimator a : new ObjectAnimator[]{sx2, sy2, a2}) a.setDuration(POP_MS);
        sx2.setInterpolator(new OvershootInterpolator(1.4f));
        sy2.setInterpolator(new OvershootInterpolator(1.4f));

        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(heartImageView, View.ALPHA, 1f, 0f);
        fadeOut.setDuration(FADE_MS);
        ObjectAnimator fadeOut2 = ObjectAnimator.ofFloat(heartShadowView, View.ALPHA, 1f, 0f);
        fadeOut2.setDuration(FADE_MS);

        AnimatorSet pop = new AnimatorSet();
        pop.playTogether(scaleX, scaleY, alphaIn, sx2, sy2, a2);
        pop.setStartDelay(HOLD_MS);

        AnimatorSet run = new AnimatorSet();
        AnimatorSet out = new AnimatorSet();
        out.playTogether(fadeOut, fadeOut2);
        run.playSequentially(pop, out);
        run.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (cancelled || heartImageView == null) return;
                heartImageView.setVisibility(INVISIBLE);
                heartImageView.setAlpha(0f);
                if (heartShadowView != null) {
                    heartShadowView.setVisibility(INVISIBLE);
                    heartShadowView.setAlpha(0f);
                }
            }
        });
        burst = run;
        run.start();
    }
}
