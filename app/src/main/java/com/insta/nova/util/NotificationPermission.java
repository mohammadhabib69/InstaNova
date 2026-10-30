package com.insta.nova.util;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

/**
 * One place that answers "would a notification actually be shown on this device".
 *
 * <p>Two independent things can block it: the runtime POST_NOTIFICATIONS permission
 * (Android 13+) and the app-level notification switch in system settings. Both matter,
 * because {@code NotificationManagerCompat.notify()} is a silent no-op when either is
 * off — it does not throw and it logs nothing. Code that assumes "notify() returned, so
 * the user saw it" is wrong on a fresh install where the prompt was dismissed.
 */
public class NotificationPermission {

    private ActivityResultLauncher<String> launcher;

    /**
     * Registers the permission launcher. Must be called during the Activity's
     * onCreate, before any request.
     */
    public void attach(ComponentActivity activity, Runnable onResult) {
        launcher = activity.registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (onResult != null) onResult.run();
                });
    }

    /** True when a notification posted right now would actually be displayed. */
    public boolean isEnabled(Context context) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    /**
     * Asks the OS, but only while the OS is still willing to show a prompt.
     *
     * <p>After the user has denied twice the platform ignores further requests: no
     * dialog appears and no result comes back, so a bare {@code launch()} here would
     * look like a button that does nothing. In that state this returns false and the
     * caller sends the user to system settings instead, where the switch can actually
     * be changed.
     *
     * @return true if a prompt was raised
     */
    public boolean requestIfPossible(ComponentActivity activity) {
        if (isEnabled(activity)) return false;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false;
        if (launcher == null) return false;
        if (!activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            // Granted-but-app-switch-off, or permanently denied. Either way there is no
            // prompt to show. MainActivity asks once at startup, so on this screen a
            // false rationale means the question has already been answered.
            return false;
        }
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS);
        return true;
    }

    /** Opens this app's notification page, where a denied permission can be undone. */
    public void openSystemSettings(Context context) {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
        } else {
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:" + context.getPackageName()));
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (android.content.ActivityNotFoundException e) {
            context.startActivity(new Intent(Settings.ACTION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }
}
