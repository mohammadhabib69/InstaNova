package com.insta.nova.ui.chat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
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
import com.insta.nova.databinding.ActivityVideoCallBinding;
import com.insta.nova.util.GlideUtils;
import com.insta.nova.util.ToastUtil;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.RendererCommon;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Production-ready WebRTC Video Calling Activity with real-time two-way video rendering,
 * camera flip, camera enable/disable, mic mute, speaker audio routing, and signaling.
 */
public class VideoCallActivity extends AppCompatActivity {

    private static final String TAG = "VideoCallActivity";

    public static final String EXTRA_CALL_ID = "EXTRA_CALL_ID";
    public static final String EXTRA_IS_INCOMING = "EXTRA_IS_INCOMING";
    public static final String EXTRA_CONVERSATION_ID = "EXTRA_CONVERSATION_ID";
    public static final String EXTRA_OTHER_USER_ID = "EXTRA_OTHER_USER_ID";
    public static final String EXTRA_OTHER_USERNAME = "EXTRA_OTHER_USERNAME";
    public static final String EXTRA_OTHER_AVATAR = "EXTRA_OTHER_AVATAR";

    private ActivityVideoCallBinding binding;
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
    private boolean isFrontCamera = true;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private static final long TIMEOUT_UNANSWERED_MS = 30_000L;

    private final List<IceCandidate> queuedRemoteCandidates = new ArrayList<>();
    private boolean hasSetRemoteDescription = false;
    private VideoTrack remoteVideoTrack;
    private boolean remoteVideoAttached = false;

    /**
     * Prompt J — authoritative call state over Supabase Realtime.
     *
     * <p>Identical in purpose to the observer on AudioCallActivity: the video
     * screen's layout, preview and controls are untouched, only what drives its
     * state label and its closing is now the shared `calls` state machine.
     */
    private final CallManager.CallObserver callObserver = new CallManager.CallObserver() {
        @Override
        public void onIncomingCall(@NonNull CallRecord record, String callerName, String callerAvatar) {
        }

        @Override
        public void onCallStateChanged(@NonNull CallRecord record) {
            if (isEnded || !callId.equals(record.id)) return;
            runOnUiThread(() -> {
                if (CallRecord.STATUS_RINGING.equals(record.status)) {
                    binding.tvCallState.setText("Ringing\u2026");
                } else if (CallRecord.STATUS_CONNECTED.equals(record.status)) {
                    isConnected = true;
                    long origin = record.connectedAtMillis();
                    if (origin > 0) callStartMs = origin;
                    if (callStartMs == 0) callStartMs = System.currentTimeMillis();
                    handler.removeCallbacks(timerRunnable);
                    handler.post(timerRunnable);
                    if (webRtcManager != null) {
                        webRtcManager.logStatsSample("video-connected-0");
                        handler.postDelayed(() -> webRtcManager.logStatsSample("video-connected-1"), 4000L);
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
                handler.postDelayed(VideoCallActivity.this::finishAfterEnd, 350L);
            });
        }
    };

    private static String terminalLabel(String status) {
        if (CallRecord.STATUS_DECLINED.equals(status)) return "Call declined";
        if (CallRecord.STATUS_MISSED.equals(status)) return "No answer";
        if (CallRecord.STATUS_NO_ANSWER.equals(status)) return "No answer";
        return "Call ended";
    }

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

    private final ActivityResultLauncher<String[]> permissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Boolean cameraGranted = result.get(Manifest.permission.CAMERA);
                Boolean audioGranted = result.get(Manifest.permission.RECORD_AUDIO);
                if (Boolean.TRUE.equals(cameraGranted) && Boolean.TRUE.equals(audioGranted)) {
                    startWebRtcCall();
                } else {
                    ToastUtil.showPill(this, "Camera and microphone permissions are required for video calling");
                    binding.tvCallState.setText("Permissions denied");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        binding = ActivityVideoCallBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.videoCallRoot, (v, insets) -> {
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

        binding.tvRemoteUsername.setText(otherUsername != null && !otherUsername.isEmpty() ? otherUsername : (com.insta.nova.util.Constants.APP_NAME + " User"));
        if (otherAvatar != null && !otherAvatar.isEmpty()) {
            GlideUtils.loadAvatar(binding.ivRemoteAvatar, otherAvatar);
        } else {
            binding.ivRemoteAvatar.setImageResource(R.drawable.ic_person_placeholder);
        }

        binding.btnMinimize.setOnClickListener(v -> finish());
        binding.btnEndCall.setOnClickListener(v -> endCall());
        binding.btnToggleMic.setOnClickListener(v -> toggleMic());

        // Prompt J: observe the authoritative `calls` state machine.
        CallManager callManager = CallManager.getInstance(this);
        callManager.addObserver(callObserver);
        callManager.startRealtime();
        binding.btnToggleVideo.setOnClickListener(v -> toggleVideo());
        binding.btnFlipCamera.setOnClickListener(v -> flipCamera());

        checkPermissionsAndStart();
    }

    private void checkPermissionsAndStart() {
        List<String> needed = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.CAMERA);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.RECORD_AUDIO);
        }

        if (needed.isEmpty()) {
            startWebRtcCall();
        } else {
            permissionsLauncher.launch(needed.toArray(new String[0]));
        }
    }

    private void startWebRtcCall() {
        signalingClient = CallManager.getInstance(this).getSignalingClient();

        // Initialize WebRTC
        webRtcManager = new WebRtcManager(this, true, new WebRtcManager.WebRtcListener() {
            @Override
            public void onLocalIceCandidate(@NonNull IceCandidate candidate) {
                if (otherUserId != null) {
                    signalingClient.sendIceCandidate(callId, otherUserId, candidate);
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
            public void onRemoteVideoTrackReceived(@NonNull VideoTrack track) {
                runOnUiThread(() -> attachRemoteVideo(track));
            }
        });

        webRtcManager.init();

        // Initialize SurfaceViewRenderers
        initVideoRenderers();

        // Start Local Video Camera Capturer
        webRtcManager.startVideoCapturer(binding.localVideoRenderer);

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
                if (isIncoming && !hasSetRemoteDescription) {
                    handleRemoteOffer(sdp);
                }
            }

            @Override
            public void onAnswerReceived(@NonNull SessionDescription sdp) {
                if (!isIncoming && !hasSetRemoteDescription) {
                    handleRemoteAnswer(sdp);
                }
            }

            @Override
            public void onIceCandidateReceived(@NonNull IceCandidate candidate) {
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
                        CallManager.getInstance(VideoCallActivity.this).endCall(callId, durationSec);
                        binding.tvCallState.setText("reject".equalsIgnoreCase(reason) || CallSession.STATUS_REJECTED.equalsIgnoreCase(reason) ? "Call declined" : "Call ended");
                        handler.postDelayed(VideoCallActivity.this::finishAfterEnd, 1000L);
                    }
                });
            }
        });

        if (isIncoming) {
            // Incoming Call: the callee already answered.
            //
            // Prompt J: acceptIncomingCall() wrote 'connected' + connected_at
            // before launching this screen, so this branch only reads.
            binding.tvCallState.setText("Connecting\u2026");
            isConnected = true;
            // Shared timer origin — see the identical note in AudioCallActivity.
            CallRecord active = CallManager.getInstance(this).getActiveCall();
            long origin = (active != null && callId.equals(active.id))
                    ? active.connectedAtMillis() : 0L;
            callStartMs = origin > 0 ? origin : System.currentTimeMillis();
            handler.post(timerRunnable);

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
                    true
            );

            // Prompt J: "Ringing…" is driven by the callee's ack over Realtime,
            // not by a local 1200ms delay. See callObserver.
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

    private void initVideoRenderers() {
        if (webRtcManager == null) return;
        org.webrtc.EglBase.Context eglContext = webRtcManager.getEglBaseContext();
        if (eglContext == null) return;

        // Remote Renderer (Full screen)
        binding.remoteVideoRenderer.init(eglContext, null);
        binding.remoteVideoRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
        binding.remoteVideoRenderer.setEnableHardwareScaler(true);

        // Local Renderer (Floating PIP)
        binding.localVideoRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
        binding.localVideoRenderer.setZOrderMediaOverlay(true);
    }

    private void attachRemoteVideo(@NonNull VideoTrack track) {
        // onAddTrack and onTrack both report the same receiver track. Attaching
        // twice republishes the same sink onto the SurfaceView, which tears the
        // buffer queue down and logs "rejecting buffer" — the remote tile then
        // stays black even though RTP is arriving.
        if (this.remoteVideoTrack == track && remoteVideoAttached) {
            Log.d("WRTCDBG", "REMOTE_VIDEO_ATTACH skipped: already attached");
            return;
        }
        this.remoteVideoTrack = track;
        try {
            track.addSink(binding.remoteVideoRenderer);
            binding.remoteVideoRenderer.setVisibility(View.VISIBLE);
            binding.layoutRemotePlaceholder.setVisibility(View.GONE);
            remoteVideoAttached = true;
            Log.d("WRTCDBG", "REMOTE_VIDEO_ATTACH ok id=" + track.id());
        } catch (Exception e) {
            Log.e(TAG, "Error attaching remote video track: " + e.getMessage());
        }
    }

    private void createWebRtcOffer() {
        if (webRtcManager == null) return;
        webRtcManager.createOffer(new SimpleSdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sdp) {
                webRtcManager.setLocalDescription(sdp, new SimpleSdpObserver() {
                    @Override
                    public void onSetSuccess() {
                        if (otherUserId != null) {
                            signalingClient.sendOffer(callId, otherUserId, sdp);
                        }
                    }
                });
            }
        });
    }

    private void handleRemoteOffer(@NonNull SessionDescription sdp) {
        if (webRtcManager == null) return;
        hasSetRemoteDescription = true;
        webRtcManager.setRemoteDescription(sdp, new SimpleSdpObserver() {
            @Override
            public void onSetSuccess() {
                drainQueuedCandidates();
                webRtcManager.createAnswer(new SimpleSdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription answer) {
                        webRtcManager.setLocalDescription(answer, new SimpleSdpObserver() {
                            @Override
                            public void onSetSuccess() {
                                if (otherUserId != null) {
                                    signalingClient.sendAnswer(callId, otherUserId, answer);
                                }
                            }
                        });
                    }
                });
            }
        });
    }

    private void handleRemoteAnswer(@NonNull SessionDescription sdp) {
        if (webRtcManager == null) return;
        hasSetRemoteDescription = true;
        webRtcManager.setRemoteDescription(sdp, new SimpleSdpObserver() {
            @Override
            public void onSetSuccess() {
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

    private void flipCamera() {
        if (webRtcManager == null) return;
        webRtcManager.switchCamera(new CameraVideoCapturer.CameraSwitchHandler() {
            @Override
            public void onCameraSwitchDone(boolean isFront) {
                isFrontCamera = isFront;
                binding.localVideoRenderer.setMirror(isFront);
            }

            @Override
            public void onCameraSwitchError(String s) {
                Log.e(TAG, "Failed to flip camera: " + s);
            }
        });
    }

    private void toggleVideo() {
        if (webRtcManager == null) return;
        boolean enable = !webRtcManager.isVideoEnabled();
        webRtcManager.setVideoEnabled(enable);

        if (enable) {
            binding.ivVideoToggle.setImageResource(R.drawable.ic_video);
            binding.localVideoRenderer.setVisibility(View.VISIBLE);
            binding.layoutCameraOffPlaceholder.setVisibility(View.GONE);
        } else {
            binding.ivVideoToggle.setImageResource(R.drawable.ic_video_off);
            binding.localVideoRenderer.setVisibility(View.GONE);
            binding.layoutCameraOffPlaceholder.setVisibility(View.VISIBLE);
        }
    }

    private void toggleMic() {
        if (webRtcManager == null) return;
        boolean newMute = !webRtcManager.isMuted();
        webRtcManager.setMuted(newMute);
        binding.ivMicToggle.setImageResource(newMute ? R.drawable.ic_mic_off : R.drawable.ic_mic);
        binding.btnToggleMic.setAlpha(newMute ? 0.6f : 1.0f);
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
        CallTones.getInstance().stop();
        CallManager.getInstance(this).clearActiveSession(callId);

        if (remoteVideoTrack != null) {
            try {
                remoteVideoTrack.removeSink(binding.remoteVideoRenderer);
            } catch (Exception ignored) {}
            remoteVideoTrack = null;
        }

        try {
            binding.localVideoRenderer.release();
            binding.remoteVideoRenderer.release();
        } catch (Exception ignored) {}

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
        @Override public void onCreateFailure(String s) { Log.e(TAG, "SdpObserver onCreateFailure: " + s); }
        @Override public void onSetFailure(String s) { Log.e(TAG, "SdpObserver onSetFailure: " + s); }
    }
}
