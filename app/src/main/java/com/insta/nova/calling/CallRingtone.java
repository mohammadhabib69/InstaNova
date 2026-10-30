package com.insta.nova.calling;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The one thing allowed to make an incoming call ring.
 *
 * <h3>Why this exists</h3>
 * Three independent sources used to play the ringtone for a single incoming call:
 * the {@code instanova_incoming_calls} notification channel (its system ringtone),
 * a {@code Ringtone} owned by {@code IncomingCallActivity}, and a re-post of the
 * same notification from the FCM path. Two of them overlap audibly, and because
 * each has its own stop path, dismissing one (accept, decline, activity finish)
 * left the other playing — the "I stopped one and another started" report.
 *
 * <p>Now the ringtone has exactly one owner: this process singleton. Every entry
 * point (Realtime poll, FCM, notification tap, incoming screen) calls
 * {@link #start(Context, String)} with the same call id, and {@link #start} is
 * idempotent per call id, so a duplicated delivery cannot stack a second player.
 * The notification channel is silent (see {@code CallNotificationHelper}) and only
 * carries the visual notification + Accept/Decline actions.
 *
 * <p>Ringing is started from the manager, not only from the activity, because the
 * activity may never be allowed to start (Android 10+ blocks background activity
 * starts). Starting it at the decision point is what makes a backgrounded or
 * freshly-woken process ring at all.
 */
public final class CallRingtone {

    private static final String TAG = "CallRingtone";

    private static final CallRingtone INSTANCE = new CallRingtone();

    public static CallRingtone get() {
        return INSTANCE;
    }

    private Ringtone ringtone;
    private Vibrator vibrator;
    /** The call currently ringing, or null. Guarded by {@code this}. */
    private String ringingCallId;

    private CallRingtone() {
    }

    /**
     * Starts ringing for {@code callId}. Safe to call from any thread and from any
     * number of entry points: a repeat for the call already ringing is a no-op, and
     * a new call id replaces the previous player instead of layering on top.
     */
    public synchronized void start(@NonNull Context context, @Nullable String callId) {
        if (callId == null || callId.isEmpty()) return;
        if (callId.equals(ringingCallId) && ringtone != null && ringtone.isPlaying()) {
            return;
        }
        stopInternal();

        ringingCallId = callId;
        Context app = context.getApplicationContext();

        try {
            Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            if (uri != null) {
                ringtone = RingtoneManager.getRingtone(app, uri);
                if (ringtone != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        ringtone.setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build());
                    }
                    // A ringtone loops by itself on every supported release; setLooping
                    // only exists from API 28 and is therefore a hint, not a requirement.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ringtone.setLooping(true);
                    }
                    ringtone.play();
                    Log.i(TAG, "RING_START call=" + callId);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "could not start the ringtone: " + e.getMessage());
        }

        try {
            // Settings > Calls > Vibrate for calls. The switch used to persist a key that
            // nothing read, so turning it off still vibrated on every incoming call.
            boolean vibrateEnabled = new com.insta.nova.data.local.AppSettingsStore(app)
                    .vibrateCalls();
            vibrator = vibrateEnabled
                    ? (Vibrator) app.getSystemService(Context.VIBRATOR_SERVICE) : null;
            if (vibrator != null && vibrator.hasVibrator()) {
                long[] pattern = {0, 800, 800};
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
                } else {
                    vibrator.vibrate(pattern, 0);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "could not start vibration: " + e.getMessage());
        }
    }

    /** Stops the ringtone and the vibration. Idempotent. */
    public synchronized void stop() {
        if (ringingCallId != null) Log.i(TAG, "RING_STOP call=" + ringingCallId);
        stopInternal();
    }

    /** Stops only if {@code callId} is the call currently ringing. */
    public synchronized void stopFor(@Nullable String callId) {
        if (callId != null && callId.equals(ringingCallId)) stop();
    }

    public synchronized boolean isRinging() {
        return ringingCallId != null;
    }

    @Nullable
    public synchronized String ringingCallId() {
        return ringingCallId;
    }

    private void stopInternal() {
        ringingCallId = null;
        if (ringtone != null) {
            try {
                if (ringtone.isPlaying()) ringtone.stop();
            } catch (Exception ignored) {
            }
            ringtone = null;
        }
        if (vibrator != null) {
            try {
                vibrator.cancel();
            } catch (Exception ignored) {
            }
            vibrator = null;
        }
    }
}
