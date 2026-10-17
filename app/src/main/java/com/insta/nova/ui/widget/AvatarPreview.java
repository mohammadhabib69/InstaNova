package com.insta.nova.ui.widget;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;

import com.insta.nova.R;
import com.insta.nova.util.GlideUtils;

import de.hdodenhof.circleimageview.CircleImageView;

/** Temporary, anchored avatar preview used by long-press gestures. */
public final class AvatarPreview {

    private static PopupWindow popup;

    private AvatarPreview() {}

    public static void show(View anchor, String avatarUrl) {
        if (anchor == null || anchor.getWindowToken() == null) return;
        dismiss();

        int size = anchor.getResources().getDimensionPixelSize(R.dimen.avatar_preview_size);
        CircleImageView image = new CircleImageView(anchor.getContext());
        image.setLayoutParams(new ViewGroup.LayoutParams(size, size));
        GlideUtils.loadAvatar(image, avatarUrl);
        image.setContentDescription(anchor.getContext().getString(R.string.profile_picture_preview));

        popup = new PopupWindow(image, size, size, false);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setFocusable(false);
        popup.setTouchable(true);

        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        int gap = anchor.getResources().getDimensionPixelSize(R.dimen.avatar_preview_gap);
        int screenWidth = anchor.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = anchor.getResources().getDisplayMetrics().heightPixels;
        int x = location[0] + (anchor.getWidth() - size) / 2;
        x = Math.max(gap, Math.min(x, screenWidth - size - gap));
        int above = location[1] - size - gap;
        int below = location[1] + anchor.getHeight() + gap;
        int y = above >= gap ? above : Math.min(below, screenHeight - size - gap);
        popup.showAtLocation(anchor, Gravity.TOP | Gravity.START, x, y);
    }

    public static boolean isShowing() {
        return popup != null && popup.isShowing();
    }

    public static void dismiss() {
        if (popup != null) {
            popup.dismiss();
            popup = null;
        }
    }
}
