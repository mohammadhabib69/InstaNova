package com.insta.nova.calling;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.insta.nova.R;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.databinding.ActivityIncomingCallBinding;
import com.insta.nova.util.GlideUtils;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

public class IncomingCallActivity extends AppCompatActivity {

    public static final String EXTRA_CALL_ID = "EXTRA_CALL_ID";
    public static final String EXTRA_CALL_TYPE = "EXTRA_CALL_TYPE";
    public static final String EXTRA_CALLER_ID = "EXTRA_CALLER_ID";
    public static final String EXTRA_CALLER_USERNAME = "EXTRA_CALLER_USERNAME";
    public static final String EXTRA_CALLER_AVATAR = "EXTRA_CALLER_AVATAR";
    public static final String EXTRA_CONVERSATION_ID = "EXTRA_CONVERSATION_ID";

    private ActivityIncomingCallBinding binding;
    private String callId;
    private String callType;
    private String callerId;
    private String callerUsername;
    private String callerAvatar;
    private String conversationId;

    private Ringtone ringtone;
    private Vibrator vibrator;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private static final long TIMEOUT_MS = 30_000L;

    private final LocalSignalingBus.SignalingEventListener signalingListener = new LocalSignalingBus.SignalingEventListener() {
        @Override
        public void onCallSessionUpdate(@NonNull CallSession session) {
            if (callId != null && callId.equals(session.callId)) {
                if (CallSession.STATUS_ENDED.equals(session.status) ||
                    CallSession.STATUS_CANCELLED.equals(session.status)) {
                    finishWithDismiss();
                }
            }
        }

        @Override
        public void onRemoteOfferReceived(@NonNull String id, @NonNull SessionDescription sdp) {}

        @Override
        public void onRemoteAnswerReceived(@NonNull String id, @NonNull SessionDescription sdp) {}

        @Override
        public void onRemoteIceCandidateReceived(@NonNull String id, @NonNull IceCandidate candidate) {}

        @Override
        public void onCallEnded(@NonNull String id, @NonNull String reason) {
            if (callId != null && callId.equals(id)) {
                finishWithDismiss();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Turn screen on and show over lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        binding = ActivityIncomingCallBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.incomingCallRoot, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        callId = getIntent().getStringExtra(EXTRA_CALL_ID);
        callType = getIntent().getStringExtra(EXTRA_CALL_TYPE);
        callerId = getIntent().getStringExtra(EXTRA_CALLER_ID);
        callerUsername = getIntent().getStringExtra(EXTRA_CALLER_USERNAME);
        callerAvatar = getIntent().getStringExtra(EXTRA_CALLER_AVATAR);
        conversationId = getIntent().getStringExtra(EXTRA_CONVERSATION_ID);

        boolean isVideo = CallSession.TYPE_VIDEO.equalsIgnoreCase(callType);
        binding.tvCallTypeHeader.setText(isVideo ? (com.insta.nova.util.Constants.APP_NAME + " Video Call") : (com.insta.nova.util.Constants.APP_NAME + " Audio Call"));
        binding.ivCallTypeIcon.setImageResource(isVideo ? R.drawable.ic_video : R.drawable.ic_phone_call);
        binding.tvIncomingSubtitle.setText(isVideo ? "Incoming video call..." : "Incoming audio call...");

        binding.tvCallerUsername.setText(callerUsername != null && !callerUsername.isEmpty() ? callerUsername : (com.insta.nova.util.Constants.APP_NAME + " User"));
        if (callerAvatar != null && !callerAvatar.isEmpty()) {
            GlideUtils.loadAvatar(binding.ivCallerAvatar, callerAvatar);
        } else {
            binding.ivCallerAvatar.setImageResource(R.drawable.ic_person_placeholder);
        }

        // Pulse ring animation
        AlphaAnimation pulse = new AlphaAnimation(0.3f, 1.0f);
        pulse.setDuration(1000);
        pulse.setRepeatMode(Animation.REVERSE);
        pulse.setRepeatCount(Animation.INFINITE);
        binding.pulseRing.startAnimation(pulse);

        // Ringtone and Vibration
        startRingtoneAndVibration();

        // THE ACK (Prompt J). This screen being visible IS the ack: the caller
        // is flipped from "Calling…" to "Ringing…" by this write alone. It is
        // done in onCreate rather than on a listener so the ack happens on the
        // same frame the UI is shown, whether this screen was opened by Realtime
        // (app in the foreground) or by a full-screen intent (app backgrounded).
        CallActivityBridge.registerIncoming(this);
        CallManager.getInstance(this).startRealtime();
        CallManager.getInstance(this).acknowledgeRinging(callId);

        // Listen for call cancellation
        if (callId != null) {
            LocalSignalingBus.getInstance().registerListener(callId, signalingListener);
        }

        // Ring timeout: 30 seconds after the ack. Writes 'missed', which closes
        // both sides — the caller's screen and this one — and leaves the
        // missed-call entry in the thread.
        handler.postDelayed(() -> {
            if (!isFinishing()) {
                CallManager.getInstance(this).ringTimedOut(callId);
                CallThreadEntries.recordTerminatedCall(
                        this,
                        missedSnapshot(),
                        true);
                finishWithDismiss();
            }
        }, TIMEOUT_MS);

        binding.btnDecline.setOnClickListener(v -> {
            stopRingtoneAndVibration();
            CallManager.getInstance(this).rejectIncomingCall(callId);
            finishWithDismiss();
        });

        binding.btnAccept.setOnClickListener(v -> {
            stopRingtoneAndVibration();
            CallSession session = new CallSession(callId, conversationId, callerId,
                    SessionManager.getInstance(this).getUserId(), callType, false);
            session.callerUsername = callerUsername;
            session.callerAvatar = callerAvatar;
            LocalSignalingBus.getInstance().saveSession(session);
            CallManager.getInstance(this).acceptIncomingCall(this, callId);
            finish();
        });
    }

    /** The call id this screen is bound to; null before onCreate binds it. */
    @androidx.annotation.Nullable
    public String getShowingCallId() {
        return callId;
    }

    /**
     * Closes this screen because the call reached a terminal state elsewhere
     * (caller cancelled, or a 30s ring timeout the other device enforced).
     * Leaves the missed-call entry behind on the callee's side.
     */
    public void finishForRemoteEnd(@androidx.annotation.Nullable String status) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            if (CallRecord.STATUS_MISSED.equals(status) || CallRecord.STATUS_NO_ANSWER.equals(status)) {
                CallThreadEntries.recordTerminatedCall(this, missedSnapshot(), true);
            }
            finishWithDismiss();
        });
    }

    private CallRecord missedSnapshot() {
        CallRecord r = new CallRecord();
        r.id = callId;
        r.callerId = callerId;
        r.calleeId = SessionManager.getInstance(this).getUserId();
        r.callType = callType;
        r.status = CallRecord.STATUS_MISSED;
        r.createdAt = CallManager.nowIso();
        return r;
    }

    private void startRingtoneAndVibration() {
        try {
            Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            if (ringtoneUri == null) {
                ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }
            if (ringtoneUri != null) {
                ringtone = RingtoneManager.getRingtone(this, ringtoneUri);
                if (ringtone != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        ringtone.setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build());
                    }
                    Log.i("CallTones", "RINGTONE_START call=" + callId);
                    ringtone.play();
                }
            }

            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                long[] pattern = {0, 800, 800};
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
                } else {
                    vibrator.vibrate(pattern, 0);
                }
            }
        } catch (Exception ignored) {}
    }

    private void stopRingtoneAndVibration() {
        try {
            if (ringtone != null && ringtone.isPlaying()) {
                Log.i("CallTones", "RINGTONE_STOP call=" + callId);
                ringtone.stop();
            }
        } catch (Exception ignored) {}
        try {
            if (vibrator != null) {
                vibrator.cancel();
            }
        } catch (Exception ignored) {}
    }

    private void finishWithDismiss() {
        stopRingtoneAndVibration();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopRingtoneAndVibration();
        handler.removeCallbacksAndMessages(null);
        CallActivityBridge.unregisterIncoming(this);
        if (callId != null) {
            LocalSignalingBus.getInstance().unregisterListener(callId, signalingListener);
        }
    }
}
