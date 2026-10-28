package com.insta.nova.hub;

import android.app.Activity;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.insta.nova.R;

/** Shared chrome for Nova module screens (back, accent icon, shared-element title). */
public final class NovaScreen {

    private NovaScreen() {
    }

    /**
     * Applies the module header. The icon and title carry the transition names used by
     * the hub's shared-element transition.
     */
    public static void bindHeader(Activity activity, int iconRes, int titleRes, int accentColorRes) {
        ImageView back = activity.findViewById(R.id.btnBack);
        if (back != null) back.setOnClickListener(v -> activity.finish());

        ImageView icon = activity.findViewById(R.id.headerIcon);
        if (icon != null) {
            icon.setImageResource(iconRes);
            icon.setTransitionName("nova_module_icon");
            // Plain glyph in the bar: the accent belongs to the content below,
            // not to a tinted badge in the header.
            icon.setBackground(null);
            icon.setColorFilter(ContextCompat.getColor(activity, R.color.nova_text));
        }
        TextView title = activity.findViewById(R.id.headerTitle);
        if (title != null) {
            title.setText(titleRes);
            title.setTransitionName("nova_module_title");
        }
        com.insta.nova.widget.NovaUi.styleStatusBar(activity,
                com.insta.nova.widget.NovaUi.isNight(activity));
    }
}
