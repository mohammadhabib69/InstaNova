package com.insta.nova.update;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.insta.nova.R;

import java.io.File;

/**
 * Download progress and "ready to install" notifications.
 *
 * <p>One low-importance channel: an update download is background work the user started,
 * so it must not ring like a message. Progress uses the same notification id as the final
 * state, so a finished download replaces its own progress card instead of stacking.
 */
public final class UpdateNotifications {

    private static final String TAG = "UpdateNotifications";

    public static final String CHANNEL_ID = "instanova_app_updates";
    static final int ID_UPDATE = 9101;

    private UpdateNotifications() {
    }

    public static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.update_channel_name),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(context.getString(R.string.update_channel_description));
        channel.setShowBadge(false);
        channel.enableVibration(false);
        channel.setSound(null, null);
        manager.createNotificationChannel(channel);
    }

    /** Determinate (or indeterminate while the server's size is still unknown) progress. */
    public static Notification progress(Context context, int percent, boolean indeterminate,
                                        String versionName) {
        ensureChannel(context);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_logo_mark_mono)
                .setContentTitle(context.getString(R.string.update_downloading_title))
                .setContentText(context.getString(R.string.update_downloading_body, versionName))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setContentIntent(openAppIntent(context));

        if (indeterminate) {
            builder.setProgress(0, 0, true);
        } else {
            builder.setProgress(100, Math.max(0, Math.min(100, percent)), false);
        }
        return builder.build();
    }

    public static void showProgress(Context context, int percent, boolean indeterminate,
                                    String versionName) {
        post(context, ID_UPDATE, progress(context, percent, indeterminate, versionName));
    }

    /** Terminal state: tapping hands the verified APK to the package installer. */
    public static void showReady(Context context, File apk, UpdateInfo info) {
        ensureChannel(context);
        String version = info == null ? "" : info.displayVersion();
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_logo_mark_mono)
                .setContentTitle(context.getString(R.string.update_ready_title))
                .setContentText(context.getString(R.string.update_ready_body, version))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setContentIntent(installIntent(context, apk, info));
        post(context, ID_UPDATE, builder.build());
    }

    public static void showFailed(Context context) {
        ensureChannel(context);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_logo_mark_mono)
                .setContentTitle(context.getString(R.string.update_failed_title))
                .setContentText(context.getString(R.string.update_failed_body))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openAppIntent(context));
        post(context, ID_UPDATE, builder.build());
    }

    public static void cancel(Context context) {
        try {
            NotificationManagerCompat.from(context).cancel(ID_UPDATE);
        } catch (RuntimeException e) {
            Log.w(TAG, "cancel failed: " + e);
        }
    }

    private static void post(Context context, int id, Notification notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, notification);
        } catch (SecurityException e) {
            // POST_NOTIFICATIONS denied: the foreground service notification still shows,
            // this one is simply dropped. The in-app dialog carries the same state.
            Log.w(TAG, "notification not permitted: " + e.getMessage());
        } catch (RuntimeException e) {
            Log.w(TAG, "notification failed: " + e);
        }
    }

    private static PendingIntent openAppIntent(Context context) {
        Intent launch = context.getPackageManager()
                .getLaunchIntentForPackage(context.getPackageName());
        if (launch == null) {
            launch = new Intent();
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(context, 0, launch, flags);
    }

    private static PendingIntent installIntent(Context context, File apk, UpdateInfo info) {
        Intent intent = new Intent(context, UpdateInstallActivity.class)
                .putExtra(UpdateInstallActivity.EXTRA_APK_PATH, apk == null ? null : apk.getAbsolutePath())
                .putExtra(UpdateInstallActivity.EXTRA_VERSION_CODE, info == null ? 0 : info.versionCode)
                .putExtra(UpdateInstallActivity.EXTRA_VERSION_NAME,
                        info == null ? "" : info.displayVersion())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(context, 1, intent, flags);
    }
}
