package com.insta.nova.util;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import com.insta.nova.R;

/**
 * Presentation helper for the shared toast pill (black rounded pill, white
 * 14sp text).
 */
public final class ToastUtil {

    private ToastUtil() {}

    /**
     * Shows the standard black pill toast with the given message.
     *
     * <p>Transport failures arrive here as the raw OkHttp sentence — {@code Unable to
     * resolve host "poqdemxiuoocbzkwbyhy.supabase.co": No address associated with
     * hostname} — because several call sites forward {@code Throwable.getMessage()}
     * straight from a repository callback. Sanitising in this one place is what makes the
     * promise hold for every pill in the app, including the ones added later, instead of
     * depending on each caller remembering to map its own errors.
     */
    public static void showPill(@NonNull Context context, @NonNull CharSequence message) {
        CharSequence shown = NetworkErrors.sanitize(
                message == null ? null : message.toString(), "Something went wrong. Try again.");
        View view = LayoutInflater.from(context).inflate(R.layout.view_toast_pill, null);
        TextView text = view.findViewById(R.id.toastText);
        text.setText(shown);

        Toast toast = new Toast(context.getApplicationContext());
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, dp(context, 96));
        toast.setView(view);
        toast.show();
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
