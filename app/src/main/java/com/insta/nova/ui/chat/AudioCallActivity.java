package com.insta.nova.ui.chat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.insta.nova.R;
import com.insta.nova.calling.CallManager;
import com.insta.nova.calling.CallRecord;
import com.insta.nova.calling.CallSession;
import com.insta.nova.calling.CallSignalingClient;
import com.insta.nova.calling.CallTones;
import com.insta.nova.calling.LocalSignalingBus;
import com.insta.nova.calling.WebRtcManager;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.databinding.ActivityAudioCallBinding;
import com.insta.nova.util.GlideUtils;
import com.insta.nova.util.ToastUtil;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Production-ready WebRTC Audio Calling Activity with real-time peer audio streaming,
 * recipient-scoped signaling, mute/speaker toggles, and call duration tracking.
 */
public class AudioCallActivity extends AppCompatActivity {

    private static final String TAG = "AudioCallActivity";

    public static final String EXTRA_CALL_ID = "EXTRA_CALL_ID";
    public static final String EXTRA_IS_INCOMING = "EXTRA_IS_INCOMING";
    public static final String EXTRA_CONVERSATION_ID = "EXTRA_CONVERSATION_ID";
    public static final String EXTRA_OTHER_USER_ID = "EXTRA_OTHER_USER_ID";
    public static final String EXTRA_OTHER_USERNAME = "EXTRA_OTHER_USERNAME";
    public static final String EXTRA_OTHER_AVATAR = "EXTRA_OTHER_AVATAR";

    private ActivityAudioCallBinding binding;
    private String callId;
    private boolean isIncoming = false;
    private String conversationId;
    private String otherUserId;
    private String otherUsername;
    private String otherAvatar;

    private WebRtcManager webRtcManager;
    private CallSignalingClient signalingClient;
    private CallSession currentCallSession;

    private boolean isConnected = false;
    private boolean isEnded = false;
    private long callStartMs = 0L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private static final long TIMEOUT_UNANSWERED_MS = 30_000L;

    private final List<IceCandidate> queuedRemoteCandidates = new ArrayList<>();
    private boolean hasSetRemoteDescription = false;

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (isConnected && callStartMs > 0 && !isEnded) {
                long elapsedSec = (System.currentTimeMillis() - callStartMs) / 1000;
                long mins = elapsedSec / 60;
                long secs = elapsedSec % 60;
                binding.tvCallState.setText(String.format(Locale.US, "%02d:%02d", mins, secs));
                handler.postDelayed(this, 1000L);
            }
        }
    };

    private final Runnable timeoutRunnable = () -> {
        if (!isConnected && !isEnded) {
            binding.tvCallState.setText("No answer");
            handler.postDelayed(this::endCall, 2000L);
        }
    };

    /**
     * Prompt J — authoritative call state, delivered over Supabase Realtime.
     *
     * <p>This replaces the old cosmetic flip from "Contacting…" to
     * "Ringing…" that ran off a hardcoded 1200ms delay. The label now changes
     * only when the callee's device has actually acknowledged the call, and a
     * remote hang-up closes this screen immediately instead of after a poll.
     */
    private final CallManager.CallObserver callObserver = new CallManager.CallObserver() {
        @Override
        public void onIncomingCall(@NonNull CallRecord record, String callerName, String callerAvatar) {
            // Not handled here: an incoming call while this screen is open is
            // a busy situation handled by CallManager, not by this Activity.
        }

        @Override
        public void onCallStateChanged(@NonNull CallRecord record) {
            if (isEnded || !callId.equals(record.id)) return;
            runOnUiThread(() -> {
                if (CallRecord.STATUS_RINGING.equals(record.status)) {
                    binding.tvCallState.setText("Ringing\u2026");
                } else if (CallRecord.STATUS_CONNECTED.equals(record.status)) {
                    isConnected = true;
                    // Both devices derive the timer origin from connected_at so
                    // the two counters read the same value.
                    long origin = record.connectedAtMillis();
                    if (origin > 0) callStartMs = origin;
                    if (callStartMs == 0) callStartMs = System.currentTimeMillis();
                    handler.removeCallbacks(timerRunnable);
                    handler.post(timerRunnable);
                    if (webRtcManager != null) {
                        webRtcManager.logStatsSample("audio-connected-0");
                        handler.postDelayed(() -> webRtcManager.logStatsSample("audio-connected-1"), 4000L);
                    }
                }
            });
        }

        @Override
        public void onCallTerminated(@NonNull CallRecord record) {
            if (isEnded || !callId.equals(record.id)) return;
            runOnUiThread(() -> {
                isEnded = true;
                CallTones.getInstance().stop();
                binding.tvCallState.setText(terminalLabel(record.status));
                // Short 'Call ended' flash then close. 900ms put the total end-sync
                // past the ~1s target once realtime latency and the activity
                // teardown were included.
                handler.postDelayed(AudioCallActivity.this::finishAfterEnd, 350L);
            });
        }
    };

    private static String terminalLabel(String status) {
        if (CallRecord.STATUS_DECLINED.equals(status)) return "Call declined";
        if (CallRecord.STATUS_MISSED.equals(status)) return "No answer";
        if (CallRecord.STATUS_NO_ANSWER.equals(status)) return "No answer";
        return "Call ended";
    }

    private final ActivityResultLauncher<String[]> permissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Boolean audioGranted = result.get(Manifest.permission.RECORD_AUDIO);
                if (audioGranted != null && audioGranted) {
                    startWebRtcCall();
                } else {
                    ToastUtil.showPill(this, "Microphone permission is required for audio calling");
                    binding.tvCallState.setText("Permission denied");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAudioCallBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.audioCallRoot, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        callId = getIntent().getStringExtra(EXTRA_CALL_ID);
        isIncoming = getIntent().getBooleanExtra(EXTRA_IS_INCOMING, false);
        conversationId = getIntent().getStringExtra(EXTRA_CONVERSATION_ID);
        otherUserId = getIntent().getStringExtra(EXTRA_OTHER_USER_ID);
        otherUsername = getIntent().getStringExtra(EXTRA_OTHER_USERNAME);
        otherAvatar = getIntent().getStringExtra(EXTRA_OTHER_AVATAR);

        if (callId == null || callId.isEmpty()) {
            callId = UUID.randomUUID().toString();
        } else if (callId.startsWith("call-")) {
            String stripped = callId.substring("call-".length());
            if (CallManager.isValidUuid(stripped)) {
                callId = stripped;
            }
        }

        binding.tvCallUsername.setText(otherUsername != null && !otherUsername.isEmpty() ? otherUsername : (com.insta.nova.util.Constants.APP_NAME + " User"));
        if (otherAvatar != null && !otherAvatar.isEmpty()) {
            GlideUtils.loadAvatar(binding.ivCallAvatar, otherAvatar);
        } else {
            binding.ivCallAvatar.setImageResource(R.drawable.ic_person_placeholder);
        }

        // Pulse animation for avatar ring
        AlphaAnimation pulse = new AlphaAnimation(0.4f, 1.0f);
        pulse.setDuration(1200);
        pulse.setRepeatMode(Animation.REVERSE);
        pulse.setRepeatCount(Animation.INFINITE);
        binding.pulseRing.startAnimation(pulse);

        binding.btnMinimizeCall.setOnClickListener(v -> finish());
        binding.btnEndCall.setOnClickListener(v -> endCall());
        binding.btnMute.setOnClickListener(v -> toggleMute());
        binding.btnSpeaker.setOnClickListener(v -> toggleSpeaker());

        // Prompt J: observe the authoritative `calls` state machine.
        CallManager callManager = CallManager.getInstance(this);
        callManager.addObserver(callObserver);
        callManager.startRealtime();

        checkPermissionsAndStart();
    }

    private void checkPermissionsAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsLauncher.launch(new String[]{ Manifest.permission.RECORD_AUDIO });
        } else {
            startWebRtcCall();
        }
    }

    private void startWebRtcCall() {
        signalingClient = CallManager.getInstance(this).getSignalingClient();

        // Initialize WebRTC
        webRtcManager = new WebRtcManager(this, false, new WebRtcManager.WebRtcListener() {
            @Override
            public void onLocalIceCandidate(@NonNull IceCandidate candidate) {
                if (otherUserId != null) {
                    Log.d("WRTCDBG", "SIGNAL_TX candidate mid=" + candidate.sdpMid);
                    signalingClient.sendIceCandidate(callId, otherUserId, candidate);
                } else {
                    Log.e("WRTCDBG", "CANDIDATE_NOT_SENT otherUser null");
                }
            }

            @Override
            public void onIceConnectionChange(@NonNull PeerConnection.IceConnectionState state) {
                runOnUiThread(() -> handleIceConnectionChange(state));
            }

            @Override
            public void onConnectionChange(@NonNull PeerConnection.PeerConnectionState state) {
                Log.d(TAG, "WebRTC PeerConnectionState: " + state);
            }

            @Override
            public void onRemoteVideoTrackReceived(@NonNull VideoTrack remoteVideoTrack) {}
        });

        webRtcManager.init();

        // Start Signaling Listener
        signalingClient.startListening(callId, new CallSignalingClient.SignalingListener() {
            @Override
            public void onCallSessionUpdated(@NonNull CallSession session) {
                currentCallSession = session;
                if (CallSession.STATUS_ACCEPTED.equals(session.status) && !isConnected) {
                    binding.tvCallState.setText("Connecting...");
                }
            }

            @Override
            public void onOfferReceived(@NonNull SessionDescription sdp) {
                Log.d("WRTCDBG", "SIGNAL_RX offer incoming=" + isIncoming
                        + " alreadySet=" + hasSetRemoteDescription
                        + " bytes=" + (sdp.description == null ? 0 : sdp.description.length()));
                if (isIncoming && !hasSetRemoteDescription) {
                    handleRemoteOffer(sdp);
                }
            }

            @Override
            public void onAnswerReceived(@NonNull SessionDescription sdp) {
                Log.d("WRTCDBG", "SIGNAL_RX answer incoming=" + isIncoming
                        + " alreadySet=" + hasSetRemoteDescription
                        + " bytes=" + (sdp.description == null ? 0 : sdp.description.length()));
                if (!isIncoming && !hasSetRemoteDescription) {
                    handleRemoteAnswer(sdp);
                }
            }

            @Override
            public void onIceCandidateReceived(@NonNull IceCandidate candidate) {
                Log.d("WRTCDBG", "SIGNAL_RX candidate mid=" + candidate.sdpMid
                        + " remoteDescSet=" + hasSetRemoteDescription);
                if (hasSetRemoteDescription && webRtcManager != null) {
                    webRtcManager.addIceCandidate(candidate);
                } else {
                    queuedRemoteCandidates.add(candidate);
                }
            }

            @Override
            public void onCallEnded(@NonNull String reason) {
                runOnUiThread(() -> {
                    if (!isEnded) {
                        isEnded = true;
                        int durationSec = (isConnected && callStartMs > 0)
                                ? (int) ((System.currentTimeMillis() - callStartMs) / 1000) : 0;
                        CallManager.getInstance(AudioCallActivity.this).endCall(callId, durationSec);
                        binding.tvCallState.setText("reject".equalsIgnoreCase(reason) || CallSession.STATUS_REJECTED.equalsIgnoreCase(reason) ? "Call declined" : "Call ended");
                        handler.postDelayed(AudioCallActivity.this::finishAfterEnd, 1000L);
                    }
                });
            }
        });

        if (isIncoming) {
            // Incoming Call: the callee already answered.
            //
            // Prompt J: CallManager.acceptIncomingCall() wrote status
            // 'connected' with connected_at *before* launching this screen, so
            // this branch must not write a status again — doing so would race
            // the caller's own read and could reset connected_at.
            binding.tvCallState.setText("Connecting\u2026");
            isConnected = true;
            // Take the timer origin from the shared `connected_at`, not the local
            // clock. The caller derives its origin from the same column, so this
            // is what keeps the two counters reading the same value — starting
            // from System.currentTimeMillis() here made the callee's timer run
            // behind by however long the screen transition took.
            CallRecord active = CallManager.getInstance(this).getActiveCall();
            long origin = (active != null && callId.equals(active.id))
                    ? active.connectedAtMillis() : 0L;
            callStartMs = origin > 0 ? origin : System.currentTimeMillis();
            handler.post(timerRunnable);

            // Check if offer is already available in local bus
            CallSession localSession = LocalSignalingBus.getInstance().getSession(callId);
            if (localSession != null) {
                currentCallSession = localSession;
            }
        } else {
            // Outgoing Call: Caller offers
            binding.tvCallState.setText("Calling\u2026");
            String safeConvId = (conversationId != null && CallManager.isValidUuid(conversationId)) ? conversationId : null;
            currentCallSession = CallManager.getInstance(this).startOutgoingCall(
                    callId,
                    safeConvId,
                    otherUserId != null ? otherUserId : "other",
                    otherUsername,
                    otherAvatar,
                    false
            );

            // Prompt J: no local timer flips this to "Ringing…". The label
            // changes only when the callee's device writes the ack over
            // Realtime (see callObserver.onCallStateChanged).
            // Prompt J: no local caller-side timeout here.
            //
            // A 30s local timer was a Prompt I leftover and it raced the
            // callee's 30s ring timeout, whichever fired first winning. The
            // caller therefore wrote 'ended' for calls that should have become
            // 'missed', and it also killed calls the callee was still ringing
            // out. The only caller-side budget is CallManager's 45s no-answer
            // timer, which correctly does nothing once the callee has acked.
            handler.removeCallbacks(timeoutRunnable);

            createWebRtcOffer();
        }
    }

    private void createWebRtcOffer() {
        if (webRtcManager == null) return;
        Log.d("WRTCDBG", "OFFER_START caller otherUser=" + otherUserId);
        webRtcManager.createOffer(new SimpleSdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sdp) {
                Log.d("WRTCDBG", "OFFER_CREATED bytes=" + sdp.description.length());
                webRtcManager.setLocalDescription(sdp, new SimpleSdpObserver() {
                    @Override
                    public void onSetSuccess() {
                        Log.d("WRTCDBG", "LOCAL_DESC_SET offer -> sending SIGNAL_TX offer");
                        if (otherUserId != null) {
                            signalingClient.sendOffer(callId, otherUserId, sdp);
                        } else {
                            Log.e("WRTCDBG", "OFFER_NOT_SENT otherUser null");
                        }
                    }
                });
            }
        });
    }

    private void handleRemoteOffer(@NonNull SessionDescription sdp) {
        if (webRtcManager == null) {
            Log.e("WRTCDBG", "REMOTE_DESC_SKIPPED offer: webRtcManager null");
            return;
        }
        Log.d("WRTCDBG", "REMOTE_DESC_SET offer start bytes=" + sdp.description.length());
        webRtcManager.setRemoteDescription(sdp, new SimpleSdpObserver() {
            @Override
            public void onSetSuccess() {
                hasSetRemoteDescription = true;
                Log.d("WRTCDBG", "REMOTE_DESC_SET offer OK -> ANSWER_START");
                drainQueuedCandidates();
                webRtcManager.createAnswer(new SimpleSdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription answer) {
                        Log.d("WRTCDBG", "ANSWER_CREATED bytes=" + answer.description.length());
                        webRtcManager.setLocalDescription(answer, new SimpleSdpObserver() {
                            @Override
                            public void onSetSuccess() {
                                Log.d("WRTCDBG", "LOCAL_DESC_SET answer -> sending SIGNAL_TX answer");
                                if (otherUserId != null) {
                                    signalingClient.sendAnswer(callId, otherUserId, answer);
                                } else {
                                    Log.e("WRTCDBG", "ANSWER_NOT_SENT otherUser null");
                                }
                            }
                        });
                    }
                });
            }
        });
    }

    private void handleRemoteAnswer(@NonNull SessionDescription sdp) {
        if (webRtcManager == null) {
            Log.e("WRTCDBG", "REMOTE_DESC_SKIPPED answer: webRtcManager null");
            return;
        }
        Log.d("WRTCDBG", "REMOTE_DESC_SET answer start bytes=" + sdp.description.length());
        webRtcManager.setRemoteDescription(sdp, new SimpleSdpObserver() {
            @Override
            public void onSetSuccess() {
                hasSetRemoteDescription = true;
                Log.d("WRTCDBG", "REMOTE_DESC_SET answer OK -> draining candidates");
                drainQueuedCandidates();
            }
        });
    }

    private void drainQueuedCandidates() {
        if (webRtcManager != null) {
            for (IceCandidate c : queuedRemoteCandidates) {
                webRtcManager.addIceCandidate(c);
            }
            queuedRemoteCandidates.clear();
        }
    }

    private void handleIceConnectionChange(PeerConnection.IceConnectionState state) {
        Log.d(TAG, "IceConnectionState changed to: " + state);
        if (state == PeerConnection.IceConnectionState.CONNECTED ||
            state == PeerConnection.IceConnectionState.COMPLETED) {
            if (!isConnected) {
                isConnected = true;
                callStartMs = System.currentTimeMillis();
                handler.removeCallbacks(timeoutRunnable);
                binding.tvCallState.setText("00:00");
                handler.post(timerRunnable);
            }
        } else if (state == PeerConnection.IceConnectionState.DISCONNECTED) {
            if (isConnected) {
                binding.tvCallState.setText("Reconnecting...");
            }
        } else if (state == PeerConnection.IceConnectionState.FAILED) {
            binding.tvCallState.setText("Connection failed");
            handler.postDelayed(this::endCall, 2000L);
        }
    }

    private void toggleMute() {
        if (webRtcManager == null) return;
        boolean newMute = !webRtcManager.isMuted();
        webRtcManager.setMuted(newMute);
        binding.ivMute.setImageResource(newMute ? R.drawable.ic_mic_off : R.drawable.ic_mic);
        binding.btnMute.setAlpha(newMute ? 0.6f : 1.0f);
    }

    private void toggleSpeaker() {
        if (webRtcManager == null) return;
        boolean newSpeaker = !webRtcManager.isSpeakerphoneOn();
        webRtcManager.setSpeakerphoneOn(newSpeaker);
        binding.ivSpeaker.setImageResource(newSpeaker ? R.drawable.ic_volume_up : R.drawable.ic_speaker);
    }

    private void endCall() {
        if (isEnded) return;
        isEnded = true;

        handler.removeCallbacksAndMessages(null);
        int durationSec = (isConnected && callStartMs > 0)
                ? (int) ((System.currentTimeMillis() - callStartMs) / 1000) : 0;

        if (callId != null) {
            if (otherUserId != null && signalingClient != null) {
                signalingClient.sendCallEnded(callId, otherUserId, !isConnected && !isIncoming ? "cancelled" : "ended");
            }
            if (!isConnected && !isIncoming) {
                CallManager.getInstance(this).cancelCall(callId);
            } else {
                CallManager.getInstance(this).endCall(callId, durationSec);
            }
        }

        binding.tvCallState.setText("Call ended");
        handler.postDelayed(this::finishAfterEnd, 600L);
    }

    private void finishAfterEnd() {
        setResult(RESULT_OK);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isEnded = true;
        handler.removeCallbacksAndMessages(null);

        CallManager.getInstance(this).removeObserver(callObserver);
        // Prompt J: no tone may outlive the screen that started it.
        CallTones.getInstance().stop();
        CallManager.getInstance(this).clearActiveSession(callId);

        if (webRtcManager != null) {
            webRtcManager.dispose();
            webRtcManager = null;
        }

        if (signalingClient != null && callId != null) {
            signalingClient.stopListening(callId);
        }
    }

    private static class SimpleSdpObserver implements SdpObserver {
        @Override public void onCreateSuccess(SessionDescription sessionDescription) {}
        @Override public void onSetSuccess() {}
        @Override public void onCreateFailure(String s) {
            Log.e("WRTCDBG", "SDP_CREATE_FAIL " + s);
            Log.e(TAG, "SdpObserver onCreateFailure: " + s);
        }
        @Override public void onSetFailure(String s) {
            Log.e("WRTCDBG", "SDP_SET_FAIL " + s);
            Log.e(TAG, "SdpObserver onSetFailure: " + s);
        }
    }
}
