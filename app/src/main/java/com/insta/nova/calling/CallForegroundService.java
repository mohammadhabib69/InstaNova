package com.insta.nova.calling;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Holds the in-call chip in the status bar for the duration of a call.
 *
 * <h3>Why a service and not just a notification</h3>
 * The call screen used to post an ordinary ONGOING notification. On OriginOS the system
 * accepted the record — {@code dumpsys notification} showed id 9003 with
 * {@code ONGOING_EVENT|SILENT}, {@code category=call} and the "End call" action — but the
 * shade never rendered it, so minimising a call left the user with no way back and no way
 * to hang up. A notification passed to {@link #startForeground(int, Notification)} is
 * mandatory-visible on every OEM, and since Android 14 a long-running call is expected to
 * be a {@code phoneCall} foreground service anyway.
 */
public class CallForegroundService extends Service {

    private static final String TAG = "CallFgService";

    private static final String ACTION_START = "com.insta.nova.action.CALL_FGS_START";
    private static final String EXTRA_CALL_ID = "EXTRA_FGS_CALL_ID";
    private static final String EXTRA_PEER_NAME = "EXTRA_FGS_PEER_NAME";
    private static final String EXTRA_PEER_AVATAR = "EXTRA_FGS_PEER_AVATAR";
    private static final String EXTRA_IS_VIDEO = "EXTRA_FGS_IS_VIDEO";

    /** Starts (or refreshes) the chip. Safe to call repeatedly. */
    public static void start(@NonNull Context context, @NonNull String callId,
                             @Nullable String peerName, @Nullable String peerAvatar, boolean isVideo) {
        Intent intent = new Intent(context, CallForegroundService.class)
                .setAction(ACTION_START)
                .putExtra(EXTRA_CALL_ID, callId)
                .putExtra(EXTRA_PEER_NAME, peerName)
                .putExtra(EXTRA_PEER_AVATAR, peerAvatar)
                .putExtra(EXTRA_IS_VIDEO, isVideo);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception e) {
            // A background start can be refused. The call still works; only the chip is
            // missing, so this must never take the call down with it.
            Log.w(TAG, "could not start call foreground service", e);
        }
    }

    /** Removes the chip by tearing the service down. Safe to call when it is not running. */
    public static void stop(@NonNull Context context) {
        try {
            context.stopService(new Intent(context, CallForegroundService.class));
        } catch (Exception e) {
            Log.w(TAG, "could not stop call foreground service", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String callId = intent != null ? intent.getStringExtra(EXTRA_CALL_ID) : null;
        if (callId == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        String peerName = intent.getStringExtra(EXTRA_PEER_NAME);
        String peerAvatar = intent.getStringExtra(EXTRA_PEER_AVATAR);
        boolean isVideo = intent.getBooleanExtra(EXTRA_IS_VIDEO, false);

        Notification chip = CallNotificationHelper.buildOngoingCallNotification(
                this, callId, peerName, peerAvatar, isVideo);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(CallNotificationHelper.NOTIFICATION_ID_ONGOING, chip,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL);
            } else {
                startForeground(CallNotificationHelper.NOTIFICATION_ID_ONGOING, chip);
            }
        } catch (Exception e) {
            Log.w(TAG, "startForeground refused", e);
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }
        } catch (Exception ignored) {
        }
        CallNotificationHelper.cancelOngoingCallNotification(this);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
