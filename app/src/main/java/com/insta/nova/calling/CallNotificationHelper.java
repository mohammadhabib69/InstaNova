package com.insta.nova.calling;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.insta.nova.R;
import com.insta.nova.ui.chat.AudioCallActivity;
import com.insta.nova.ui.chat.VideoCallActivity;

/**
 * Notification channels, full-screen intents and Accept/Decline actions for calls.
 *
 * <h3>Two channels on purpose</h3>
 * <ul>
 *   <li>{@link #CHANNEL_ID_CALLS} — visual only. Its sound is deliberately {@code null}
 *       because {@link CallRingtone} owns the ringing, and a channel that also plays the
 *       system ringtone produced two overlapping ringtones for one call. The ringtone is
 *       played once, by the process, so stopping it stops everything.</li>
 *   <li>{@link #CHANNEL_ID_ONGOING} — low importance, silent, for the "call in progress"
 *       chip that keeps an active call visible in the status bar and is the way back into
 *       the call screen if the user navigated away.</li>
 * </ul>
 *
 * <p>The calls channel id carries a {@code _v2} suffix on purpose: a NotificationChannel's
 * sound is immutable after creation, so an existing install would otherwise keep the old
 * looping ringtone alongside the new silent one.
 */
public class CallNotificationHelper {

    /** Incoming-call channel. Silent — {@link CallRingtone} plays the ringtone. */
    public static final String CHANNEL_ID_CALLS = "instanova_incoming_calls_v2";
    /** Ongoing-call channel. Silent, but high importance so the chip is not folded away. */
    // "_v3": a channel's importance is fixed at creation. LOW was invisible on OriginOS/MIUI and
    // DEFAULT still did not render a shade card there, so the chip moves to a new id at HIGH
    // importance while staying silent through setSilent(true) on the builder.
    public static final String CHANNEL_ID_ONGOING = "instanova_call_ongoing_v3";

    /** Superseded ongoing channels, deleted from upgraded installs. */
    private static final String LEGACY_CHANNEL_ID_ONGOING = "instanova_call_ongoing_v2";

    /** The pre-v2 channel, kept only so it can be deleted from upgraded installs. */
    private static final String LEGACY_CHANNEL_ID_CALLS = "instanova_incoming_calls";

    public static final String ACTION_DECLINE_CALL = "com.insta.nova.ACTION_DECLINE_CALL";
    public static final String ACTION_ACCEPT_CALL = "com.insta.nova.ACTION_ACCEPT_CALL";
    public static final String ACTION_END_CALL = "com.insta.nova.ACTION_END_CALL";

    public static final int NOTIFICATION_ID_CALL = 9001;
    /** Separate id so a missed-call entry survives while a new call rings. */
    public static final int NOTIFICATION_ID_MISSED = 9002;
    /** The in-progress call chip. */
    public static final int NOTIFICATION_ID_ONGOING = 9003;

    public static void createNotificationChannel(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;

        // The old channel played the system ringtone on top of the in-app one. It has to
        // go, or every upgraded install keeps both sounds no matter what this class does.
        try {
            nm.deleteNotificationChannel(LEGACY_CHANNEL_ID_CALLS);
            nm.deleteNotificationChannel(LEGACY_CHANNEL_ID_ONGOING);
        } catch (Exception ignored) {
        }

        NotificationChannel calls = new NotificationChannel(
                CHANNEL_ID_CALLS,
                "Incoming calls",
                NotificationManager.IMPORTANCE_HIGH
        );
        calls.setDescription("Incoming audio and video calls");
        // Sound and vibration are owned by CallRingtone so there is exactly one player.
        calls.setSound(null, null);
        calls.enableVibration(false);
        calls.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(calls);

        NotificationChannel ongoing = new NotificationChannel(
                CHANNEL_ID_ONGOING,
                "Call in progress",
                NotificationManager.IMPORTANCE_HIGH
        );
        ongoing.setDescription("Shows an active call in the status bar");
        ongoing.setSound(null, null);
        ongoing.enableVibration(false);
        ongoing.setShowBadge(false);
        nm.createNotificationChannel(ongoing);
    }

    // ------------------------------------------------------------------ incoming

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

    public static void showIncomingCallNotification(@NonNull Context context, @NonNull CallSession session) {
        createNotificationChannel(context);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        Intent fullScreenIntent = incomingIntent(context, session);
        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
                context, 0, fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        PendingIntent declinePendingIntent = actionIntent(context, session.callId,
                ACTION_DECLINE_CALL, 1);
        PendingIntent acceptPendingIntent = actionIntent(context, session.callId,
                ACTION_ACCEPT_CALL, 2);

        String callTypeTitle = session.isVideo() ? "Incoming video call" : "Incoming audio call";
        String callerName = session.callerUsername != null && !session.callerUsername.isEmpty()
                ? session.callerUsername
                : (com.insta.nova.util.Constants.APP_NAME + " user");

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_CALLS)
                .setSmallIcon(R.drawable.ic_logo_mark_mono)
                .setLargeIcon(largeIcon(context, session.callerAvatar))
                .setContentTitle(callTypeTitle)
                .setContentText(callerName)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(fullScreenPendingIntent)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .addAction(R.drawable.ic_call_end, "Decline", declinePendingIntent)
                .addAction(R.drawable.ic_phone_call, "Accept", acceptPendingIntent);

        notify(context, nm, NOTIFICATION_ID_CALL, builder.build());
    }

    public static void cancelIncomingCallNotification(@NonNull Context context) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            try {
                nm.cancel(NOTIFICATION_ID_CALL);
            } catch (Exception ignored) {
            }
        }
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
        notify(context, nm, NOTIFICATION_ID_MISSED, builder.build());
    }

    // ------------------------------------------------------------------ ongoing

    /**
     * Keeps an active call visible in the status bar and gives a one-tap way back
     * into the call screen. Without it, minimising the call screen left nothing in
     * the shade and the user had no way back to hang up.
     */
    public static void showOngoingCallNotification(@NonNull Context context, @NonNull String callId,
                                                   @Nullable String peerName, @Nullable String peerAvatar,
                                                   boolean isVideo) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        notify(context, nm, NOTIFICATION_ID_ONGOING,
                buildOngoingCallNotification(context, callId, peerName, peerAvatar, isVideo));
    }

    /**
     * Builds the in-call chip.
     *
     * <p>Public and separate from the posting call so the foreground service can hand the
     * exact same notification to {@code startForeground()} — a foreground notification is
     * mandatory-visible, which is what makes the chip survive OEMs that fold away ordinary
     * ongoing notifications.
     */
    @NonNull
    public static Notification buildOngoingCallNotification(@NonNull Context context, @NonNull String callId,
                                                           @Nullable String peerName, @Nullable String peerAvatar,
                                                           boolean isVideo) {
        createNotificationChannel(context);

        Intent open = isVideo ? new Intent(context, VideoCallActivity.class)
                : new Intent(context, AudioCallActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        open.putExtra(AudioCallActivity.EXTRA_CALL_ID, callId);
        open.putExtra(VideoCallActivity.EXTRA_CALL_ID, callId);
        open.putExtra(AudioCallActivity.EXTRA_IS_INCOMING, true);
        open.putExtra(VideoCallActivity.EXTRA_IS_INCOMING, true);
        open.putExtra(AudioCallActivity.EXTRA_OTHER_USERNAME, peerName);
        open.putExtra(VideoCallActivity.EXTRA_OTHER_USERNAME, peerName);
        open.putExtra(AudioCallActivity.EXTRA_OTHER_AVATAR, peerAvatar);
        open.putExtra(VideoCallActivity.EXTRA_OTHER_AVATAR, peerAvatar);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context, 4, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        PendingIntent endIntent = actionIntent(context, callId, ACTION_END_CALL, 5);

        String title = isVideo ? "Video call in progress" : "Audio call in progress";
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_ONGOING)
                .setSmallIcon(R.drawable.ic_phone_call)
                .setContentTitle(title)
                .setContentText(peerName != null ? peerName : "Tap to return to the call")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setSilent(true)
                .setContentIntent(contentIntent)
                .addAction(R.drawable.ic_call_end, "End call", endIntent);

        return builder.build();
    }

    public static void cancelOngoingCallNotification(@NonNull Context context) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            try {
                nm.cancel(NOTIFICATION_ID_ONGOING);
            } catch (Exception ignored) {
            }
        }
    }

    /** Pulls every call notification down; used when a call reaches a terminal state. */
    public static void cancelAllCallNotifications(@NonNull Context context) {
        cancelIncomingCallNotification(context);
        cancelOngoingCallNotification(context);
    }

    /** True when the user has allowed this app to post notifications. */
    public static boolean canPostNotifications(@NonNull Context context) {
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    // ------------------------------------------------------------------ helpers

    private static Intent incomingIntent(@NonNull Context context, @NonNull CallSession session) {
        Intent intent = new Intent(context, IncomingCallActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(IncomingCallActivity.EXTRA_CALL_ID, session.callId);
        intent.putExtra(IncomingCallActivity.EXTRA_CALL_TYPE, session.callType);
        intent.putExtra(IncomingCallActivity.EXTRA_CALLER_ID, session.callerId);
        intent.putExtra(IncomingCallActivity.EXTRA_CALLER_USERNAME, session.callerUsername);
        intent.putExtra(IncomingCallActivity.EXTRA_CALLER_AVATAR, session.callerAvatar);
        intent.putExtra(IncomingCallActivity.EXTRA_CONVERSATION_ID, session.conversationId);
        return intent;
    }

    private static PendingIntent actionIntent(@NonNull Context context, @Nullable String callId,
                                              @NonNull String action, int requestCode) {
        Intent intent = new Intent(context, CallActionReceiver.class);
        intent.setAction(action);
        intent.putExtra(IncomingCallActivity.EXTRA_CALL_ID, callId);
        return PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static Bitmap largeIcon(@NonNull Context context, @Nullable String avatarUrl) {
        // A remote avatar is loaded into the notification by the same Glide pipeline the
        // screens use; a synchronous fallback keeps the notification legal if it is not
        // in cache yet.
        Bitmap cached = com.insta.nova.util.GlideUtils.notificationBanner(context, avatarUrl);
        return cached != null ? cached
                : BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_person_placeholder);
    }

    private static void notify(@NonNull Context context, @NonNull NotificationManager nm,
                               int id, @NonNull Notification notification) {
        if (!canPostNotifications(context)) {
            // The user turned notifications off (or never granted POST_NOTIFICATIONS).
            // Posting would be a silent no-op; say so once in logcat instead of pretending.
            android.util.Log.w("CallNotificationHelper",
                    "notifications are disabled for this app; notification " + id + " not shown");
            return;
        }
        try {
            nm.notify(id, notification);
        } catch (SecurityException e) {
            android.util.Log.w("CallNotificationHelper", "notify(" + id + ") denied: " + e.getMessage());
        }
    }
}
