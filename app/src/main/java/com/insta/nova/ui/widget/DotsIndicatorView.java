package com.insta.nova.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.insta.nova.R;

/**
 * Custom page dots indicator for multi-image post carousels.
 */
public class DotsIndicatorView extends View {

    private int count = 0;
    private int selectedPosition = 0;

    private float dotRadius;
    private float dotSpacing;
    private int activeColor;
    private int inactiveColor;

    private Paint activePaint;
    private Paint inactivePaint;

    public DotsIndicatorView(Context context) {
        super(context);
        init(context);
    }

    public DotsIndicatorView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public DotsIndicatorView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        dotRadius = getResources().getDimension(R.dimen.indicator_dot_size) / 2f;
        dotSpacing = getResources().getDimension(R.dimen.indicator_dot_spacing);

        activeColor = ContextCompat.getColor(context, R.color.color_primary);
        inactiveColor = ContextCompat.getColor(context, R.color.dot_inactive);

        activePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        activePaint.setColor(activeColor);

        inactivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        inactivePaint.setColor(inactiveColor);
    }

    public void setCount(int count) {
        this.count = count;
        setVisibility(count > 1 ? VISIBLE : GONE);
        requestLayout();
        invalidate();
    }

    public void setSelectedPosition(int position) {
        this.selectedPosition = position;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (count <= 1) {
            setMeasuredDimension(0, 0);
            return;
        }

        float totalWidth = (count * 2 * dotRadius) + ((count - 1) * dotSpacing);
        float totalHeight = 2 * dotRadius + getPaddingTop() + getPaddingBottom();

        setMeasuredDimension(resolveSize((int) Math.ceil(totalWidth), widthMeasureSpec),
                resolveSize((int) Math.ceil(totalHeight), heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (count <= 1) return;

        float totalWidth = (count * 2 * dotRadius) + ((count - 1) * dotSpacing);
        float startX = (getWidth() - totalWidth) / 2f + dotRadius;
        float cy = getHeight() / 2f;

        for (int i = 0; i < count; i++) {
            float cx = startX + (i * (2 * dotRadius + dotSpacing));
            Paint paint = (i == selectedPosition) ? activePaint : inactivePaint;
            canvas.drawCircle(cx, cy, dotRadius, paint);
        }
    }
}
