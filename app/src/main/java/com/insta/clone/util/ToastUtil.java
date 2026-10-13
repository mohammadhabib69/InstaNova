package com.insta.clone.util;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import com.insta.clone.R;

/**
 * Presentation helper for the shared toast pill (black rounded pill, white
 * 14sp text). Centralizes the "Not available" message used by decorative UI.
 */
public final class ToastUtil {

    private ToastUtil() {}

    /** Shows the standard black pill toast with the given message. */
    public static void showPill(@NonNull Context context, @NonNull CharSequence message) {
        View view = LayoutInflater.from(context).inflate(R.layout.view_toast_pill, null);
        TextView text = view.findViewById(R.id.toastText);
        text.setText(message);

        Toast toast = new Toast(context.getApplicationContext());
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, dp(context, 96));
        toast.setView(view);
        toast.show();
    }

    /** Convenience for the decorative-only "Not available" message. */
    public static void showNotAvailable(@NonNull Context context) {
        showPill(context, context.getString(R.string.not_available));
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
