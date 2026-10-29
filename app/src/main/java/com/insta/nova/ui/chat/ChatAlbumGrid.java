package com.insta.nova.ui.chat;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import com.insta.nova.R;

/**
 * Instagram-style photo album for a DM burst.
 *
 * <p>Two photos sit side by side, three put one large tile on the left with two stacked
 * on the right, four make a 2x2 grid. Children are declared in {@code item_chat_album.xml}
 * (four {@code ShapeableImageView}s plus the "+N" overlay); this container only measures
 * and positions them.
 */
public class ChatAlbumGrid extends ViewGroup {

    private static final int MAX_TILES = 4;

    private int gapPx;
    private int albumWidthPx = -1;
    private int count;

    public ChatAlbumGrid(Context context) {
        this(context, null);
    }

    public ChatAlbumGrid(Context context, AttributeSet attrs) {
        super(context, attrs);
        gapPx = (int) (3 * context.getResources().getDisplayMetrics().density);
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ChatAlbumGrid, 0, 0);
            gapPx = a.getDimensionPixelSize(R.styleable.ChatAlbumGrid_albumGap, gapPx);
            a.recycle();
        }
    }

    /** Number of photos actually shown as tiles (2..4). */
    public void setTileCount(int count) {
        int clamped = Math.max(0, Math.min(MAX_TILES, count));
        if (this.count != clamped) {
            this.count = clamped;
            requestLayout();
            invalidate();
        }
    }

    public int getTileCount() {
        return count;
    }

    /** Fixed bubble width, so the grid does not depend on the parent's available space. */
    public void setAlbumWidthPx(int px) {
        if (px > 0 && albumWidthPx != px) {
            albumWidthPx = px;
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = albumWidthPx > 0 ? albumWidthPx : MeasureSpec.getSize(widthMeasureSpec);
        if (width <= 0) width = getSuggestedMinimumWidth();
        int cell = (width - gapPx) / 2;
        int height = (count >= 4) ? cell * 2 + gapPx : cell;

        int visible = Math.min(count, MAX_TILES);
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            child.measure(MeasureSpec.makeMeasureSpec(cell, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(cell, MeasureSpec.EXACTLY));
            child.setVisibility(i < visible ? View.VISIBLE : View.GONE);
        }
        setMeasuredDimension(width, Math.max(height, cell));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (count < 2) return;
        int width = getMeasuredWidth();
        int cell = (width - gapPx) / 2;
        int big = cell * 2 + gapPx;

        for (int i = 0; i < Math.min(count, MAX_TILES); i++) {
            View child = getChildAt(i);
            int left;
            int top;
            int cw = cell;
            int ch = cell;
            if (count == 2) {
                left = i == 0 ? 0 : cell + gapPx;
                top = 0;
            } else if (count == 3) {
                if (i == 0) {
                    left = 0;
                    top = 0;
                    cw = cell;
                    ch = big;
                } else {
                    left = cell + gapPx;
                    ch = cell;
                    top = (i == 1) ? 0 : cell + gapPx;
                }
            } else {
                left = (i % 2 == 0) ? 0 : cell + gapPx;
                top = (i < 2) ? 0 : cell + gapPx;
            }
            child.layout(left, top, left + cw, top + ch);
        }
    }
}
