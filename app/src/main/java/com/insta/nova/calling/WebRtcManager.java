package com.insta.nova.calling;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpTransceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages WebRTC PeerConnection, AudioTrack, VideoTrack, Camera capturing,
 * STUN candidate gathering, and Android AudioManager routing.
 */
public class WebRtcManager {

    private static final String TAG = "WebRtcManager";
    public static final String AUDIO_TRACK_ID = "INSTA_AUDIO_TRACK_0";
    public static final String VIDEO_TRACK_ID = "INSTA_VIDEO_TRACK_0";

    public interface WebRtcListener {
        void onLocalIceCandidate(@NonNull IceCandidate candidate);
        void onIceConnectionChange(@NonNull PeerConnection.IceConnectionState state);
        void onConnectionChange(@NonNull PeerConnection.PeerConnectionState state);
        void onRemoteVideoTrackReceived(@NonNull VideoTrack remoteVideoTrack);
    }

    private final Context context;
    private final boolean isVideoCall;
    private final WebRtcListener listener;

    private EglBase eglBase;
    private PeerConnectionFactory peerConnectionFactory;
    private PeerConnection peerConnection;

    private AudioSource audioSource;
    private AudioTrack localAudioTrack;

    private VideoSource videoSource;
    private VideoTrack localVideoTrack;
    private CameraVideoCapturer videoCapturer;
    private SurfaceTextureHelper surfaceTextureHelper;

    private final AudioManager audioManager;
    private int originalAudioMode = AudioManager.MODE_NORMAL;
    private boolean originalSpeakerState = false;
    private AudioFocusRequest audioFocusRequest;
    private boolean audioFocusGranted = false;

    private boolean isMuted = false;
    private boolean isSpeaker = false;
    private boolean isVideoEnabled = true;
    private boolean isDisposed = false;

    /** Prompt L Step 1 — temporary peer-lifecycle instrumentation. */
    private static final String WRTCDBG = "WRTCDBG";
    private int localCandidateCount = 0;
    private int addedTrackCount = 0;

    public WebRtcManager(@NonNull Context context, boolean isVideoCall, @NonNull WebRtcListener listener) {
        this.context = context.getApplicationContext();
        this.isVideoCall = isVideoCall;
        this.listener = listener;
        this.audioManager = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
    }

    public void init() {
        if (isDisposed) return;

        // 1. Setup Audio Routing
        setupAudioRouting();

        // 2. Setup EGL context for hardware video acceleration
        eglBase = EglBase.create();

        // 3. Initialize WebRTC Factory
        PeerConnectionFactory.InitializationOptions initOptions =
                PeerConnectionFactory.InitializationOptions.builder(context)
                        .setEnableInternalTracer(false)
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(initOptions);

        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setVideoEncoderFactory(new DefaultVideoEncoderFactory(eglBase.getEglBaseContext(), true, true))
                .setVideoDecoderFactory(new DefaultVideoDecoderFactory(eglBase.getEglBaseContext()))
                .createPeerConnectionFactory();

        // 4. Create Local Audio Track
        MediaConstraints audioConstraints = new MediaConstraints();
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googEchoCancellation", "true"));
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googAutoGainControl", "true"));
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googHighpassFilter", "true"));
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googNoiseSuppression", "true"));

        audioSource = peerConnectionFactory.createAudioSource(audioConstraints);
        localAudioTrack = peerConnectionFactory.createAudioTrack(AUDIO_TRACK_ID, audioSource);
        localAudioTrack.setEnabled(true);
        Log.d(WRTCDBG, "local audio track created enabled=" + localAudioTrack.enabled()
                + " id=" + localAudioTrack.id());

        // 5. Setup PeerConnection
        createPeerConnection();
    }

    public EglBase.Context getEglBaseContext() {
        return eglBase != null ? eglBase.getEglBaseContext() : null;
    }

    public void startVideoCapturer(@NonNull SurfaceViewRenderer localRenderer) {
        if (!isVideoCall || peerConnectionFactory == null || isDisposed) return;

        localRenderer.init(eglBase.getEglBaseContext(), null);
        localRenderer.setEnableHardwareScaler(true);
        localRenderer.setMirror(true);

        videoCapturer = createCameraCapturer();
        if (videoCapturer == null) {
            Log.e(TAG, "No suitable camera found");
            return;
        }

        surfaceTextureHelper = SurfaceTextureHelper.create("WebRtcCaptureThread", eglBase.getEglBaseContext());
        videoSource = peerConnectionFactory.createVideoSource(videoCapturer.isScreencast());
        videoCapturer.initialize(surfaceTextureHelper, context, videoSource.getCapturerObserver());
        videoCapturer.startCapture(640, 480, 30);

        localVideoTrack = peerConnectionFactory.createVideoTrack(VIDEO_TRACK_ID, videoSource);
        localVideoTrack.setEnabled(true);
        localVideoTrack.addSink(localRenderer);

        if (peerConnection != null) {
            peerConnection.addTrack(localVideoTrack, Collections.singletonList("INSTA_STREAM"));
        }
    }

    private CameraVideoCapturer createCameraCapturer() {
        CameraEnumerator enumerator = new Camera2Enumerator(context);
        final String[] deviceNames = enumerator.getDeviceNames();

        // Front-facing camera first
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                CameraVideoCapturer capturer = enumerator.createCapturer(deviceName, null);
                if (capturer != null) return capturer;
            }
        }
        // Fall back to back-facing
        for (String deviceName : deviceNames) {
            if (enumerator.isBackFacing(deviceName)) {
                CameraVideoCapturer capturer = enumerator.createCapturer(deviceName, null);
                if (capturer != null) return capturer;
            }
        }
        return null;
    }

    public void switchCamera(@Nullable CameraVideoCapturer.CameraSwitchHandler switchHandler) {
        if (videoCapturer != null) {
            videoCapturer.switchCamera(switchHandler);
        }
    }

    public void setVideoEnabled(boolean enable) {
        isVideoEnabled = enable;
        if (localVideoTrack != null) {
            localVideoTrack.setEnabled(enable);
        }
    }

    public boolean isVideoEnabled() {
        return isVideoEnabled;
    }

    private void createPeerConnection() {
        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer());

        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;

        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, new PeerConnection.Observer() {
            @Override
            public void onSignalingChange(PeerConnection.SignalingState signalingState) {
                Log.d(TAG, "onSignalingChange: " + signalingState);
            }

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
                Log.d(WRTCDBG, "ICE_STATE " + iceConnectionState);
                listener.onIceConnectionChange(iceConnectionState);
            }

            @Override
            public void onConnectionChange(PeerConnection.PeerConnectionState state) {
                Log.d(WRTCDBG, "PC_STATE " + state);
                listener.onConnectionChange(state);
            }

            @Override
            public void onIceConnectionReceivingChange(boolean b) {}

            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
                Log.d(TAG, "onIceGatheringChange: " + iceGatheringState);
            }

            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                localCandidateCount++;
                Log.d(WRTCDBG, "LOCAL_CANDIDATE #" + localCandidateCount
                        + " mid=" + iceCandidate.sdpMid + " mline=" + iceCandidate.sdpMLineIndex);
                listener.onLocalIceCandidate(iceCandidate);
            }

            @Override
            public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {}

            @Override
            public void onAddStream(MediaStream mediaStream) {
                Log.d(TAG, "onAddStream: " + mediaStream);
                if (!mediaStream.videoTracks.isEmpty()) {
                    listener.onRemoteVideoTrackReceived(mediaStream.videoTracks.get(0));
                }
            }

            @Override
            public void onRemoveStream(MediaStream mediaStream) {}

            @Override
            public void onDataChannel(DataChannel dataChannel) {}

            @Override
            public void onRenegotiationNeeded() {
                Log.d(TAG, "onRenegotiationNeeded");
            }

            @Override
            public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
                if (rtpReceiver.track() instanceof VideoTrack) {
                    listener.onRemoteVideoTrackReceived((VideoTrack) rtpReceiver.track());
                }
            }

            @Override
            public void onTrack(RtpTransceiver transceiver) {
                if (transceiver.getReceiver() != null && transceiver.getReceiver().track() instanceof VideoTrack) {
                    listener.onRemoteVideoTrackReceived((VideoTrack) transceiver.getReceiver().track());
                }
            }
        });

        // Add local audio track
        if (peerConnection != null && localAudioTrack != null) {
            peerConnection.addTrack(localAudioTrack, Collections.singletonList("INSTA_STREAM"));
            addedTrackCount++;
            Log.d(WRTCDBG, "PEER_CREATED iceServers=" + iceServers.size()
                    + " tracksAdded=" + addedTrackCount
                    + " audioEnabled=" + localAudioTrack.enabled());
        } else {
            Log.e(WRTCDBG, "PEER_CREATED WITHOUT AUDIO TRACK pc=" + (peerConnection != null)
                    + " track=" + (localAudioTrack != null));
        }
    }

    public void createOffer(@NonNull SdpObserver sdpObserver) {
        if (peerConnection == null) return;
        MediaConstraints sdpConstraints = new MediaConstraints();
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        if (isVideoCall) {
            sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        }
        peerConnection.createOffer(sdpObserver, sdpConstraints);
    }

    public void createAnswer(@NonNull SdpObserver sdpObserver) {
        if (peerConnection == null) return;
        MediaConstraints sdpConstraints = new MediaConstraints();
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        if (isVideoCall) {
            sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        }
        peerConnection.createAnswer(sdpObserver, sdpConstraints);
    }

    public void setLocalDescription(@NonNull SessionDescription sdp, @NonNull SdpObserver sdpObserver) {
        if (peerConnection != null) {
            peerConnection.setLocalDescription(sdpObserver, sdp);
        }
    }

    public void setRemoteDescription(@NonNull SessionDescription sdp, @NonNull SdpObserver sdpObserver) {
        if (peerConnection != null) {
            peerConnection.setRemoteDescription(sdpObserver, sdp);
        }
    }

    public void addIceCandidate(@NonNull IceCandidate candidate) {
        if (peerConnection == null) {
            Log.e(WRTCDBG, "ADD_REMOTE_CANDIDATE dropped: peerConnection null mid=" + candidate.sdpMid);
            return;
        }
        boolean ok = peerConnection.addIceCandidate(candidate);
        Log.d(WRTCDBG, "ADD_REMOTE_CANDIDATE mid=" + candidate.sdpMid
                + " mline=" + candidate.sdpMLineIndex + " result=" + ok);
    }

    public void setMuted(boolean mute) {
        this.isMuted = mute;
        if (localAudioTrack != null) {
            localAudioTrack.setEnabled(!mute);
        }
    }

    public boolean isMuted() {
        return isMuted;
    }

    public void setSpeakerphoneOn(boolean speaker) {
        this.isSpeaker = speaker;
        if (audioManager != null) {
            try {
                audioManager.setSpeakerphoneOn(speaker);
            } catch (Exception e) {
                Log.w(TAG, "Error setting speakerphone: " + e.getMessage());
            }
        }
    }

    public boolean isSpeakerphoneOn() {
        return isSpeaker;
    }

    /** Prompt K: capture a single stats sample for objective RTP verification. */
    public void logStatsSample(@NonNull String label) {
        if (peerConnection == null || isDisposed) return;
        peerConnection.getStats(report -> {
            long sentAudio = 0L;
            long receivedAudio = 0L;
            long sentVideo = 0L;
            long receivedVideo = 0L;
            for (org.webrtc.RTCStats stat : report.getStatsMap().values()) {
                java.util.Map<String, Object> m = stat.getMembers();
                String type = stat.getType();
                Object kind = m.get("kind");
                Object mediaType = m.get("mediaType");
                boolean video = "video".equals(kind) || "video".equals(mediaType);
                if ("outbound-rtp".equals(type)) {
                    long bytes = number(m.get("bytesSent"));
                    if (video) sentVideo += bytes; else sentAudio += bytes;
                } else if ("inbound-rtp".equals(type)) {
                    long bytes = number(m.get("bytesReceived"));
                    if (video) receivedVideo += bytes; else receivedAudio += bytes;
                }
            }
            Log.i(TAG, "RTC_STATS label=" + label + " audioSent=" + sentAudio
                    + " audioReceived=" + receivedAudio + " videoSent=" + sentVideo
                    + " videoReceived=" + receivedVideo);
        });
    }

    private static long number(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    /**
     * Samples RTC stats every two seconds for the first 40 s of a call.
     *
     * <p>This exists to answer one question with evidence rather than a theory: "how long
     * after answering does inbound audio actually arrive, and over which candidate pair?"
     * A first sample at t=0 and a second 4 s later cannot distinguish a fast call from one
     * that spends 20 s finding a path; this trace can.
     */
    public void startStatsTrace() {
        if (isDisposed) return;
        final Handler handler = new Handler(Looper.getMainLooper());
        for (int i = 0; i <= 20; i++) {
            final int step = i;
            handler.postDelayed(() -> logStatsSample("t" + (step * 2) + "s"), step * 2000L);
        }
    }

    /** The negotiated candidate pair, logged once, to tell host/srflx/relay apart. */
    public void logSelectedCandidatePair() {
        if (peerConnection == null || isDisposed) return;
        peerConnection.getStats(report -> {
            String selectedPair = null;
            for (org.webrtc.RTCStats stat : report.getStatsMap().values()) {
                if (!"transport".equals(stat.getType())) continue;
                Object pairId = stat.getMembers().get("selectedCandidatePairId");
                if (pairId instanceof String) selectedPair = (String) pairId;
            }
            if (selectedPair == null) return;
            org.webrtc.RTCStats pair = report.getStatsMap().get(selectedPair);
            if (pair == null) return;
            Object localId = pair.getMembers().get("localCandidateId");
            Object remoteId = pair.getMembers().get("remoteCandidateId");
            org.webrtc.RTCStats local = localId == null ? null : report.getStatsMap().get(localId);
            org.webrtc.RTCStats remote = remoteId == null ? null : report.getStatsMap().get(remoteId);
            Log.i(TAG, "RTC_PAIR local=" + describeCandidate(local)
                    + " remote=" + describeCandidate(remote));
        });
    }

    private static String describeCandidate(@Nullable org.webrtc.RTCStats candidate) {
        if (candidate == null) return "?";
        java.util.Map<String, Object> m = candidate.getMembers();
        return m.get("candidateType") + "/" + m.get("protocol") + " " + m.get("address");
    }

    /**
     * Puts the device into a call route AND takes audio focus.
     *
     * <p>{@code MODE_IN_COMMUNICATION} alone only changes the routing policy. Without an
     * audio-focus request every other app is still free to keep the output stream, and on
     * several OEM builds (Xiaomi/vivo in particular) the call then plays silently until the
     * other app releases the stream — which is exactly the "no voice for the first X
     * seconds" symptom. Focus is requested as
     * {@code GAIN_TRANSIENT_EXCLUSIVE} on {@code USAGE_VOICE_COMMUNICATION}, the same
     * contract the platform telephony stack uses.
     */
    private void setupAudioRouting() {
        if (audioManager == null) return;
        try {
            originalAudioMode = audioManager.getMode();
            originalSpeakerState = audioManager.isSpeakerphoneOn();
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            // Audio calls default to earpiece, video calls default to speakerphone
            setSpeakerphoneOn(isVideoCall);
            requestAudioFocus();
        } catch (Exception e) {
            Log.w(TAG, "Failed to setup audio routing: " + e.getMessage());
        }
    }

    @SuppressWarnings("deprecation")
    private void requestAudioFocus() {
        if (audioManager == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AudioFocusRequest request = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build())
                        .setAcceptsDelayedFocusGain(false)
                        .setWillPauseWhenDucked(true)
                        .setOnAudioFocusChangeListener(change -> Log.i(TAG, "AUDIO_FOCUS " + change))
                        .build();
                audioFocusRequest = request;
                audioFocusGranted = audioManager.requestAudioFocus(request)
                        == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            } else {
                audioFocusGranted = audioManager.requestAudioFocus(
                        change -> Log.i(TAG, "AUDIO_FOCUS " + change),
                        AudioManager.STREAM_VOICE_CALL,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                        == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            }
            Log.i(TAG, "AUDIO_FOCUS granted=" + audioFocusGranted);
        } catch (Exception e) {
            Log.w(TAG, "audio focus request failed: " + e.getMessage());
        }
    }

    @SuppressWarnings("deprecation")
    private void abandonAudioFocus() {
        if (audioManager == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (audioFocusRequest != null) audioManager.abandonAudioFocusRequest(audioFocusRequest);
            } else {
                audioManager.abandonAudioFocus(null);
            }
        } catch (Exception ignored) {
        }
        audioFocusRequest = null;
        audioFocusGranted = false;
    }

    private void restoreAudioRouting() {
        if (audioManager != null) {
            try {
                audioManager.setSpeakerphoneOn(originalSpeakerState);
                audioManager.setMode(originalAudioMode);
            } catch (Exception e) {
                Log.w(TAG, "Failed to restore audio routing: " + e.getMessage());
            }
        }
        abandonAudioFocus();
    }

    public void dispose() {
        if (isDisposed) return;
        isDisposed = true;

        restoreAudioRouting();

        if (videoCapturer != null) {
            try {
                videoCapturer.stopCapture();
                videoCapturer.dispose();
            } catch (Exception ignored) {}
            videoCapturer = null;
        }

        if (surfaceTextureHelper != null) {
            surfaceTextureHelper.dispose();
            surfaceTextureHelper = null;
        }

        if (localVideoTrack != null) {
            localVideoTrack.dispose();
            localVideoTrack = null;
        }

        if (videoSource != null) {
            videoSource.dispose();
            videoSource = null;
        }

        if (localAudioTrack != null) {
            localAudioTrack.dispose();
            localAudioTrack = null;
        }

        if (audioSource != null) {
            audioSource.dispose();
            audioSource = null;
        }

        if (peerConnection != null) {
            peerConnection.close();
            peerConnection.dispose();
            peerConnection = null;
        }

        if (peerConnectionFactory != null) {
            peerConnectionFactory.dispose();
            peerConnectionFactory = null;
        }

        if (eglBase != null) {
            eglBase.release();
            eglBase = null;
        }
    }
}
