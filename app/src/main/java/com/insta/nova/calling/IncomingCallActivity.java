package com.insta.nova.calling;

import android.os.Build;
import android.os.Bundle;

import java.util.HashMap;
import java.util.Map;
import android.os.Handler;
import android.os.Looper;
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
        if (callerAvatar != null && !callerAvatar.isEmpty() && !"none".equalsIgnoreCase(callerAvatar)) {
            GlideUtils.loadAvatar(binding.ivCallerAvatar, callerAvatar);
        } else {
            binding.ivCallerAvatar.setImageResource(R.drawable.ic_person_placeholder);
            // The push payload does not always carry the avatar (the polling path never
            // does), and an incoming call is the one screen where the caller's face has to
            // be right — resolve it from the profile row instead of leaving a placeholder.
            resolveCallerAvatar();
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

    /**
     * Ringing is owned by {@link CallRingtone}, not by this screen.
     *
     * <p>This used to create its own {@code Ringtone}, so the notification channel's
     * ringtone and this one played at the same time and stopping one left the other
     * going. {@code start} is idempotent per call id, so reaching this screen after the
     * manager already started the ring is a no-op rather than a second player.
     */
    private void startRingtoneAndVibration() {
        CallRingtone.get().start(this, callId);
    }

    private void stopRingtoneAndVibration() {
        if (callId != null) {
            CallRingtone.get().stopFor(callId);
        } else {
            CallRingtone.get().stop();
        }
    }

    private void finishWithDismiss() {
        stopRingtoneAndVibration();
        // The notification is part of "ringing": leaving it behind is what made a
        // dismissed call keep a live Accept/Decline card in the shade.
        CallNotificationHelper.cancelIncomingCallNotification(this);
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

    /** Fetches the caller's avatar when the launch intent did not carry one. */
    private void resolveCallerAvatar() {
        if (callerId == null || callerId.isEmpty()) return;
        Map<String, String> q = new HashMap<>();
        q.put("id", "eq." + callerId);
        q.put("select", "avatar_url,username");
        q.put("limit", "1");

        com.insta.nova.data.remote.RetrofitClient.getInstance(this).getRestApi()
                .getProfiles(q)
                .enqueue(new retrofit2.Callback<java.util.List<com.insta.nova.data.remote.dto.ProfileDto>>() {
                    @Override
                    public void onResponse(@NonNull retrofit2.Call<java.util.List<com.insta.nova.data.remote.dto.ProfileDto>> call,
                                           @NonNull retrofit2.Response<java.util.List<com.insta.nova.data.remote.dto.ProfileDto>> response) {
                        if (!response.isSuccessful() || response.body() == null || response.body().isEmpty()) return;
                        com.insta.nova.data.remote.dto.ProfileDto p = response.body().get(0);
                        if (p.getAvatarUrl() != null && !p.getAvatarUrl().isEmpty()) {
                            callerAvatar = p.getAvatarUrl();
                            GlideUtils.loadAvatar(binding.ivCallerAvatar, callerAvatar);
                        }
                        if ((callerUsername == null || callerUsername.isEmpty()) && p.getUsername() != null) {
                            callerUsername = p.getUsername();
                            binding.tvCallerUsername.setText(callerUsername);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull retrofit2.Call<java.util.List<com.insta.nova.data.remote.dto.ProfileDto>> call,
                                          @NonNull Throwable t) {
                        Log.d("InstaCall", "caller avatar lookup failed");
                    }
                });
    }
}
