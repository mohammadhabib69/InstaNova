package com.insta.nova.alerts;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.insta.nova.R;
import com.insta.nova.data.NovaDtos;

/**
 * The single Nova notification channel. SOS alerts use their own channel id and high
 * importance because a blood request is time critical; everything else is default.
 */
public final class NovaNotifications {

    public static final String CHANNEL_SOS = "nova_sos";
    public static final String CHANNEL_ALERTS = "nova_alerts";

    private NovaNotifications() {
    }

    public static void ensureChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;

        NotificationChannel sos = new NotificationChannel(CHANNEL_SOS,
                context.getString(R.string.nova_alerts_sos_channel),
                NotificationManager.IMPORTANCE_HIGH);
        sos.setDescription(context.getString(R.string.nova_sos_channel_body));
        sos.enableVibration(true);
        manager.createNotificationChannel(sos);

        NotificationChannel alerts = new NotificationChannel(CHANNEL_ALERTS,
                context.getString(R.string.nova_alerts_title),
                NotificationManager.IMPORTANCE_DEFAULT);
        manager.createNotificationChannel(alerts);
    }

    /** Posts one alert row. Silently does nothing when the user revoked notifications. */
    public static void show(Context context, NovaDtos.Alert alert, int id) {
        if (alert == null) return;
        ensureChannels(context);
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return;

        boolean sos = alert.kind != null && (alert.kind.startsWith("sos")
                || alert.kind.startsWith("blood"));
        Intent intent = new Intent(context, NovaAlertsActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context,
                sos ? CHANNEL_SOS : CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_nova)
                .setContentTitle(alert.title == null ? context.getString(R.string.nova_alerts_title)
                        : alert.title)
                .setContentText(alert.body == null ? "" : alert.body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(
                        alert.body == null ? "" : alert.body))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setPriority(sos ? NotificationCompat.PRIORITY_HIGH
                        : NotificationCompat.PRIORITY_DEFAULT);
        if (sos) builder.setCategory(Notification.CATEGORY_MESSAGE);

        try {
            NotificationManagerCompat.from(context).notify(id, builder.build());
        } catch (SecurityException ignored) {
            // POST_NOTIFICATIONS revoked between the check and the call.
        }
    }
}
