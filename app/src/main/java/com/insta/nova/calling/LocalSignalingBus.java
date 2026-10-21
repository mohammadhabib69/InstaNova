package com.insta.nova.calling;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory local signaling bus that allows instant, reliable signaling
 * between call activities, tests, and active sessions on the device.
 */
public class LocalSignalingBus {

    public interface SignalingEventListener {
        void onCallSessionUpdate(@NonNull CallSession session);
        void onRemoteOfferReceived(@NonNull String callId, @NonNull SessionDescription sdp);
        void onRemoteAnswerReceived(@NonNull String callId, @NonNull SessionDescription sdp);
        void onRemoteIceCandidateReceived(@NonNull String callId, @NonNull IceCandidate candidate);
        void onCallEnded(@NonNull String callId, @NonNull String reason);
    }

    private static LocalSignalingBus instance;
    private final Map<String, List<SignalingEventListener>> listeners = new ConcurrentHashMap<>();
    private final Map<String, CallSession> activeSessions = new ConcurrentHashMap<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static synchronized LocalSignalingBus getInstance() {
        if (instance == null) {
            instance = new LocalSignalingBus();
        }
        return instance;
    }

    private LocalSignalingBus() {}

    public void registerListener(@NonNull String callId, @NonNull SignalingEventListener listener) {
        listeners.computeIfAbsent(callId, k -> Collections.synchronizedList(new ArrayList<>())).add(listener);
    }

    public void unregisterListener(@NonNull String callId, @NonNull SignalingEventListener listener) {
        List<SignalingEventListener> list = listeners.get(callId);
        if (list != null) {
            list.remove(listener);
        }
    }

    public void saveSession(@NonNull CallSession session) {
        if (session.callId != null) {
            activeSessions.put(session.callId, session);
            notifySessionUpdate(session);
        }
    }

    public CallSession getSession(@NonNull String callId) {
        return activeSessions.get(callId);
    }

    public void removeSession(@NonNull String callId) {
        activeSessions.remove(callId);
        listeners.remove(callId);
    }

    public void postSessionUpdate(@NonNull CallSession session) {
        saveSession(session);
    }

    public void postRemoteOffer(@NonNull String callId, @NonNull SessionDescription sdp) {
        mainHandler.post(() -> {
            List<SignalingEventListener> list = listeners.get(callId);
            if (list != null) {
                for (SignalingEventListener l : new ArrayList<>(list)) {
                    l.onRemoteOfferReceived(callId, sdp);
                }
            }
        });
    }

    public void postRemoteAnswer(@NonNull String callId, @NonNull SessionDescription sdp) {
        mainHandler.post(() -> {
            List<SignalingEventListener> list = listeners.get(callId);
            if (list != null) {
                for (SignalingEventListener l : new ArrayList<>(list)) {
                    l.onRemoteAnswerReceived(callId, sdp);
                }
            }
        });
    }

    public void postRemoteIceCandidate(@NonNull String callId, @NonNull IceCandidate candidate) {
        mainHandler.post(() -> {
            List<SignalingEventListener> list = listeners.get(callId);
            if (list != null) {
                for (SignalingEventListener l : new ArrayList<>(list)) {
                    l.onRemoteIceCandidateReceived(callId, candidate);
                }
            }
        });
    }

    public void postCallEnded(@NonNull String callId, @NonNull String reason) {
        mainHandler.post(() -> {
            List<SignalingEventListener> list = listeners.get(callId);
            if (list != null) {
                for (SignalingEventListener l : new ArrayList<>(list)) {
                    l.onCallEnded(callId, reason);
                }
            }
        });
    }

    private void notifySessionUpdate(@NonNull CallSession session) {
        mainHandler.post(() -> {
            List<SignalingEventListener> list = listeners.get(session.callId);
            if (list != null) {
                for (SignalingEventListener l : new ArrayList<>(list)) {
                    l.onCallSessionUpdate(session);
                }
            }
        });
    }
}
