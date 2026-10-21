package com.insta.nova.calling;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.insta.nova.R;

/**
 * Manages high-priority Notification Channels, Heads-up full-screen intents,
 * and Accept/Decline action buttons for incoming audio and video calls.
 */
public class CallNotificationHelper {

    public static final String CHANNEL_ID_CALLS = "instanova_incoming_calls";
    public static final String ACTION_DECLINE_CALL = "com.insta.nova.ACTION_DECLINE_CALL";
    public static final String ACTION_ACCEPT_CALL = "com.insta.nova.ACTION_ACCEPT_CALL";
    public static final int NOTIFICATION_ID_CALL = 9001;
    /** Separate id so a missed-call entry survives while a new call rings. */
    public static final int NOTIFICATION_ID_MISSED = 9002;

    public static void createNotificationChannel(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm != null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID_CALLS,
                        "Incoming Calls",
                        NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription("Incoming Audio and Video Calls");
                channel.enableVibration(true);
                channel.setVibrationPattern(new long[]{0, 800, 800});

                Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
                if (ringtoneUri != null) {
                    AudioAttributes audioAttributes = new AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .build();
                    channel.setSound(ringtoneUri, audioAttributes);
                }

                channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                nm.createNotificationChannel(channel);
            }
        }
    }

    public static void showIncomingCallNotification(@NonNull Context context, @NonNull CallRecord record,
                                                  @Nullable String callerName, @Nullable String callerAvatar) {
        CallSession session = new CallSession();
        session.callId = record.id;
        session.callType = record.callType;
        session.callerId = record.callerId;
        session.callerUsername = callerName;
        session.callerAvatar = callerAvatar;
        showIncomingCallNotification(context, session);
    }

    /**
     * Part 5 — a call that ended (missed / no answer) while the app was not in
     * the foreground still has to leave a trace, so the user learns about it
     * without reopening the chat.
     */
    public static void showMissedCallNotification(@NonNull Context context, @NonNull String callerName,
                                                  boolean isVideo) {
        createNotificationChannel(context);
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        String title = isVideo ? "Missed video call" : "Missed audio call";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_CALLS)
                .setSmallIcon(R.drawable.ic_logo_mark_mono)
                .setContentTitle(title)
                .setContentText("Missed call from " + callerName)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true);

        // Tapping a missed-call entry opens the DM list rather than a call
        // screen: there is nothing left to answer.
        Intent inbox = new Intent(context, com.insta.nova.ui.chat.InboxActivity.class);
        inbox.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context, 3, inbox,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        builder.setContentIntent(contentIntent);
        nm.notify(NOTIFICATION_ID_MISSED, builder.build());
    }

    public static void showIncomingCallNotification(@NonNull Context context, @NonNull CallSession session) {
        createNotificationChannel(context);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        // Fullscreen Intent to IncomingCallActivity
        Intent fullScreenIntent = new Intent(context, IncomingCallActivity.class);
        fullScreenIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        fullScreenIntent.putExtra(IncomingCallActivity.EXTRA_CALL_ID, session.callId);
        fullScreenIntent.putExtra(IncomingCallActivity.EXTRA_CALL_TYPE, session.callType);
        fullScreenIntent.putExtra(IncomingCallActivity.EXTRA_CALLER_ID, session.callerId);
        fullScreenIntent.putExtra(IncomingCallActivity.EXTRA_CALLER_USERNAME, session.callerUsername);
        fullScreenIntent.putExtra(IncomingCallActivity.EXTRA_CALLER_AVATAR, session.callerAvatar);
        fullScreenIntent.putExtra(IncomingCallActivity.EXTRA_CONVERSATION_ID, session.conversationId);

        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
                context,
                0,
                fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Decline Action Intent
        Intent declineIntent = new Intent(context, CallActionReceiver.class);
        declineIntent.setAction(ACTION_DECLINE_CALL);
        declineIntent.putExtra(IncomingCallActivity.EXTRA_CALL_ID, session.callId);
        PendingIntent declinePendingIntent = PendingIntent.getBroadcast(
                context,
                1,
                declineIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Accept Action Intent
        Intent acceptIntent = new Intent(context, CallActionReceiver.class);
        acceptIntent.setAction(ACTION_ACCEPT_CALL);
        acceptIntent.putExtra(IncomingCallActivity.EXTRA_CALL_ID, session.callId);
        PendingIntent acceptPendingIntent = PendingIntent.getBroadcast(
                context,
                2,
                acceptIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String callTypeTitle = session.isVideo() ? "Incoming Video Call" : "Incoming Audio Call";
        String callerName = session.callerUsername != null ? session.callerUsername : (com.insta.nova.util.Constants.APP_NAME + " User");

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_CALLS)
                .setSmallIcon(R.drawable.ic_logo_mark_mono)
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_person_placeholder))
                .setContentTitle(callTypeTitle)
                .setContentText(callerName)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setOngoing(true)
                .setContentIntent(fullScreenPendingIntent)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .addAction(R.drawable.ic_call_end, "Decline", declinePendingIntent)
                .addAction(R.drawable.ic_phone_call, "Accept", acceptPendingIntent);

        nm.notify(NOTIFICATION_ID_CALL, builder.build());
    }

    public static void cancelIncomingCallNotification(@NonNull Context context) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.cancel(NOTIFICATION_ID_CALL);
        }
    }
}
