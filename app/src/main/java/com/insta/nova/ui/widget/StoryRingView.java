package com.insta.nova.ui.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.bumptech.glide.Glide;
import com.insta.nova.R;
import de.hdodenhof.circleimageview.CircleImageView;

/**
 * Custom Story Ring View wrapping circular avatars.
 * Supports NONE, UNSEEN (Instagram 5-color linear gradient), and SEEN (muted grey ring).
 */
public class StoryRingView extends FrameLayout {

    public enum RingState {
        NONE,
        UNSEEN,
        SEEN
    }

    private RingState ringState = RingState.NONE;
    private boolean preserveAvatarInsetWhenNone;
    private float ringStrokeWidth;
    private float gapWidth;
    private int ringSeenColor;
    private int backgroundColor;

    private Paint ringPaint;
    private Paint gapPaint;
    private RectF ringBounds;
    private RectF gapBounds;
    private CircleImageView avatarImageView;

    public StoryRingView(@NonNull Context context) {
        super(context);
        init(context, null);
    }

    public StoryRingView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public StoryRingView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        setWillNotDraw(false);

        ringStrokeWidth = getResources().getDimension(R.dimen.story_ring_stroke_unseen);
        gapWidth = getResources().getDimension(R.dimen.story_ring_gap);
        ringSeenColor = ContextCompat.getColor(context, R.color.color_ring_seen);
        backgroundColor = ContextCompat.getColor(context, R.color.color_background);

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.StoryRingView);
            int stateVal = a.getInt(R.styleable.StoryRingView_ringState, 0);
            if (stateVal == 1) ringState = RingState.UNSEEN;
            else if (stateVal == 2) ringState = RingState.SEEN;
            else ringState = RingState.NONE;

            ringStrokeWidth = a.getDimension(R.styleable.StoryRingView_ringStrokeWidth, ringStrokeWidth);
            gapWidth = a.getDimension(R.styleable.StoryRingView_gapWidth, gapWidth);
            a.recycle();
        }

        ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ringPaint.setStyle(Paint.Style.STROKE);
        gapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gapPaint.setStyle(Paint.Style.STROKE);
        ringBounds = new RectF();
        gapBounds = new RectF();

        // Add child CircleImageView
        avatarImageView = new CircleImageView(context);
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        lp.gravity = Gravity.CENTER;
        avatarImageView.setLayoutParams(lp);
        addView(avatarImageView);

        updatePaddings();
    }

    public void setRingState(RingState state) {
        setRingState(state, false);
    }

    /**
     * @param preserveRingSpace keep the avatar inset even when the ring is
     *                          hidden, so a no-story tray item does not resize
     *                          the avatar relative to active neighbours.
     */
    public void setRingState(RingState state, boolean preserveRingSpace) {
        this.ringState = state;
        this.preserveAvatarInsetWhenNone = preserveRingSpace;
        updatePaddings();
        invalidate();
    }

    public RingState getRingState() {
        return ringState;
    }

    public CircleImageView getAvatarView() {
        return avatarImageView;
    }

    public void loadAvatar(String url) {
        if (url != null && !url.isEmpty()) {
            com.insta.nova.util.GlideUtils.loadAvatar(avatarImageView, url);
        } else {
            avatarImageView.setImageResource(R.drawable.ic_person_placeholder);
        }
    }

    private void updatePaddings() {
        if (ringState == RingState.NONE && !preserveAvatarInsetWhenNone) {
            int p = 0;
            setPadding(p, p, p, p);
        } else {
            int p = Math.round(ringStrokeWidth + gapWidth);
            setPadding(p, p, p, p);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float stroke = (ringState == RingState.SEEN)
                ? getResources().getDimension(R.dimen.story_ring_stroke_seen)
                : ringStrokeWidth;
        float halfStroke = stroke / 2f;
        ringBounds.set(halfStroke, halfStroke, w - halfStroke, h - halfStroke);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (ringState == RingState.NONE) return;

        if (ringState == RingState.SEEN) {
            ringPaint.setShader(null);
            ringPaint.setColor(ringSeenColor);
            ringPaint.setStrokeWidth(getResources().getDimension(R.dimen.story_ring_stroke_seen));
            canvas.drawOval(ringBounds, ringPaint);
            return;
        }

        // UNSEEN: Instagram 5-color linear gradient (bottom-left -> top-right),
        // separated from the avatar by a gap drawn in the background color.
        int[] colors = new int[]{
                ContextCompat.getColor(getContext(), R.color.story_gradient_1),
                ContextCompat.getColor(getContext(), R.color.story_gradient_2),
                ContextCompat.getColor(getContext(), R.color.story_gradient_3),
                ContextCompat.getColor(getContext(), R.color.story_gradient_4),
                ContextCompat.getColor(getContext(), R.color.story_gradient_5)
        };
        LinearGradient gradient = new LinearGradient(
                0, getHeight(),
                getWidth(), 0,
                colors,
                null,
                Shader.TileMode.CLAMP
        );
        ringPaint.setShader(gradient);
        ringPaint.setStrokeWidth(ringStrokeWidth);
        canvas.drawOval(ringBounds, ringPaint);

        // Background-colored gap between the gradient ring and the avatar.
        if (gapWidth > 0) {
            gapPaint.setColor(backgroundColor);
            gapPaint.setStrokeWidth(gapWidth);
            float inset = ringStrokeWidth + gapWidth / 2f;
            gapBounds.set(inset, inset, getWidth() - inset, getHeight() - inset);
            canvas.drawOval(gapBounds, gapPaint);
        }
    }
}
