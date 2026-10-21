package com.insta.nova.calling;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.remote.RetrofitClient;
import com.insta.nova.data.remote.api.RestApi;
import com.insta.nova.data.remote.dto.CallSessionDto;
import com.insta.nova.data.remote.dto.CallSignalDto;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles WebRTC signaling exchange (SDP offers, answers, ICE candidates, and status)
 * via Supabase PostgREST with in-memory LocalSignalingBus integration.
 */
public class CallSignalingClient {

    private static final String TAG = "CallSignalingClient";
    private static final long POLL_INTERVAL_MS = 1000L;

    public interface SignalingListener {
        void onCallSessionUpdated(@NonNull CallSession session);
        void onOfferReceived(@NonNull SessionDescription sdp);
        void onAnswerReceived(@NonNull SessionDescription sdp);
        void onIceCandidateReceived(@NonNull IceCandidate candidate);
        void onCallEnded(@NonNull String reason);
    }

    private final Context context;
    private final RestApi restApi;
    private final Gson gson = new Gson();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private String activeCallId;
    private SignalingListener activeListener;
    private boolean isPolling = false;
    private final Set<String> processedSignalIds = new HashSet<>();

    /**
     * Prompt L — call ids whose {@code call_sessions} INSERT is still in flight.
     *
     * <p>{@code call_signals.call_id} has a FOREIGN KEY to {@code call_sessions(id)}
     * with ON DELETE CASCADE. {@link #createRemoteSession} posts that row
     * asynchronously, so an offer published immediately after
     * {@code setLocalDescription} could reach Postgres BEFORE its parent row
     * existed. The insert was then rejected by the FK and — because only
     * successes were logged — the offer vanished silently. The caller then had
     * no answer, no ICE, and zero RTP.
     *
     * <p>Offer and ICE candidates are unique opportunities (the offer is sent
     * once), so they are parked here and replayed the moment the session row
     * lands.
     */
    private final Set<String> pendingSessionInserts = new HashSet<>();
    private final Map<String, List<Runnable>> parkedSignals = new HashMap<>();

    public CallSignalingClient(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.restApi = RetrofitClient.getInstance(this.context).getRestApi();
    }

    public void startListening(@NonNull String callId, @NonNull SignalingListener listener) {
        this.activeCallId = callId;
        this.activeListener = listener;

        // 1. Listen on LocalSignalingBus
        LocalSignalingBus.getInstance().registerListener(callId, new LocalSignalingBus.SignalingEventListener() {
            @Override
            public void onCallSessionUpdate(@NonNull CallSession session) {
                if (activeListener != null) {
                    activeListener.onCallSessionUpdated(session);
                }
            }

            @Override
            public void onRemoteOfferReceived(@NonNull String id, @NonNull SessionDescription sdp) {
                if (callId.equals(id) && activeListener != null) {
                    activeListener.onOfferReceived(sdp);
                }
            }

            @Override
            public void onRemoteAnswerReceived(@NonNull String id, @NonNull SessionDescription sdp) {
                if (callId.equals(id) && activeListener != null) {
                    activeListener.onAnswerReceived(sdp);
                }
            }

            @Override
            public void onRemoteIceCandidateReceived(@NonNull String id, @NonNull IceCandidate candidate) {
                if (callId.equals(id) && activeListener != null) {
                    activeListener.onIceCandidateReceived(candidate);
                }
            }

            @Override
            public void onCallEnded(@NonNull String id, @NonNull String reason) {
                if (callId.equals(id) && activeListener != null) {
                    activeListener.onCallEnded(reason);
                }
            }
        });

        // 2. Start Remote Polling Loop
        isPolling = true;
        startRemotePolling();
    }

    public void stopListening(@NonNull String callId) {
        isPolling = false;
        handler.removeCallbacksAndMessages(null);
        LocalSignalingBus.getInstance().removeSession(callId);
        this.activeCallId = null;
        this.activeListener = null;
    }

    public static String cleanUuid(String id) {
        if (id == null) return null;
        if (id.startsWith("call-")) {
            String stripped = id.substring("call-".length());
            if (CallManager.isValidUuid(stripped)) {
                return stripped;
            }
        }
        return id;
    }

    public void createRemoteSession(@NonNull CallSession session) {
        // Save locally
        LocalSignalingBus.getInstance().saveSession(session);

        String remoteCallId = cleanUuid(session.callId);

        // Post to remote Supabase if online
        Map<String, Object> body = new HashMap<>();
        body.put("id", remoteCallId);
        if (session.conversationId != null && CallManager.isValidUuid(session.conversationId)) {
            body.put("conversation_id", session.conversationId);
        }
        body.put("caller_id", session.callerId);
        body.put("recipient_id", session.recipientId);
        body.put("call_type", session.callType);
        body.put("status", session.status);

        synchronized (pendingSessionInserts) {
            pendingSessionInserts.add(remoteCallId);
        }

        restApi.createCallSession(body).enqueue(new Callback<List<CallSessionDto>>() {
            @Override
            public void onResponse(@NonNull Call<List<CallSessionDto>> call, @NonNull Response<List<CallSessionDto>> response) {
                if (response.isSuccessful()) {
                    Log.i(TAG, "Remote call session created: " + session.callId);

                    // Prompt K: push dispatch is owned exclusively by
                    // CallManager.placeCall -> send-call-push. This legacy media
                    // session write must not dispatch a second notification.
                } else {
                    Log.e(TAG, "Remote session create FAILED code=" + response.code()
                            + " call=" + session.callId);
                }
                releaseParkedSignals(remoteCallId);
            }

            @Override
            public void onFailure(@NonNull Call<List<CallSessionDto>> call, @NonNull Throwable t) {
                Log.e(TAG, "Failed to create remote call session: " + t.getMessage());
                // Release anyway: blocking the offer forever is worse than one
                // FK-rejected insert, and the failure is now visible above.
                releaseParkedSignals(remoteCallId);
            }
        });
    }

    /** Flushes signals that were waiting for the {@code call_sessions} parent row. */
    private void releaseParkedSignals(@NonNull String remoteCallId) {
        List<Runnable> parked;
        synchronized (pendingSessionInserts) {
            pendingSessionInserts.remove(remoteCallId);
            parked = parkedSignals.remove(remoteCallId);
        }
        if (parked == null || parked.isEmpty()) return;
        Log.i(TAG, "Replaying " + parked.size() + " parked signal(s) for " + remoteCallId);
        for (Runnable r : parked) {
            try {
                r.run();
            } catch (Exception e) {
                Log.e(TAG, "Parked signal replay failed: " + e.getMessage());
            }
        }
    }

    public void sendOffer(@NonNull String callId, @NonNull String recipientId, @NonNull SessionDescription sdp) {
        // Broadcast locally
        LocalSignalingBus.getInstance().postRemoteOffer(callId, sdp);

        // Remote Supabase
        sendRemoteSignal(callId, recipientId, "offer", sdp.description);
    }

    public void sendAnswer(@NonNull String callId, @NonNull String recipientId, @NonNull SessionDescription sdp) {
        // Broadcast locally
        LocalSignalingBus.getInstance().postRemoteAnswer(callId, sdp);

        // Remote Supabase
        sendRemoteSignal(callId, recipientId, "answer", sdp.description);
    }

    public void sendIceCandidate(@NonNull String callId, @NonNull String recipientId, @NonNull IceCandidate candidate) {
        // Broadcast locally
        LocalSignalingBus.getInstance().postRemoteIceCandidate(callId, candidate);

        // Remote Supabase
        JsonObject json = new JsonObject();
        json.addProperty("sdpMid", candidate.sdpMid);
        json.addProperty("sdpMLineIndex", candidate.sdpMLineIndex);
        json.addProperty("sdp", candidate.sdp);

        sendRemoteSignal(callId, recipientId, "ice_candidate", json.toString());
    }

    public void sendCallEnded(@NonNull String callId, @NonNull String recipientId, @NonNull String reason) {
        LocalSignalingBus.getInstance().postCallEnded(callId, reason);
        sendRemoteSignal(callId, recipientId, "end", reason);
    }

    public void updateCallStatus(@NonNull String callId, @NonNull String status, int durationSeconds) {
        CallSession session = LocalSignalingBus.getInstance().getSession(callId);
        if (session != null) {
            session.status = status;
            session.durationSeconds = durationSeconds;
            LocalSignalingBus.getInstance().postSessionUpdate(session);
        }

        if (CallSession.STATUS_ENDED.equals(status) || CallSession.STATUS_CANCELLED.equals(status) || CallSession.STATUS_REJECTED.equals(status)) {
            LocalSignalingBus.getInstance().postCallEnded(callId, status);
            if (session != null) {
                String targetId = session.isOutgoing ? session.recipientId : session.callerId;
                if (targetId != null) {
                    sendRemoteSignal(callId, targetId, CallSession.STATUS_REJECTED.equals(status) ? "reject" : "end", status);
                }
            }
        }

        String remoteCallId = cleanUuid(callId);

        // Remote Supabase
        Map<String, Object> body = new HashMap<>();
        body.put("status", status);
        if (durationSeconds > 0) {
            body.put("duration_seconds", durationSeconds);
        }

        restApi.updateCallSession("eq." + remoteCallId, body).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                Log.d(TAG, "Call status updated remotely to " + status + " (" + response.code() + ")");
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                Log.w(TAG, "Failed to update call status remotely: " + t.getMessage());
            }
        });
    }

    private void sendRemoteSignal(String callId, String recipientId, String type, String payload) {
        String myUserId = SessionManager.getInstance(context).getUserId();
        if (myUserId == null) return;

        String remoteCallId = cleanUuid(callId);

        Map<String, Object> body = new HashMap<>();
        body.put("call_id", remoteCallId);
        body.put("sender_id", myUserId);
        body.put("recipient_id", recipientId);
        body.put("signal_type", type);
        body.put("payload", payload);

        // Gate: the parent call_sessions row may still be in flight. Park the
        // signal rather than letting the FK reject it silently.
        synchronized (pendingSessionInserts) {
            if (pendingSessionInserts.contains(remoteCallId)) {
                List<Runnable> parked = parkedSignals.get(remoteCallId);
                if (parked == null) {
                    parked = new ArrayList<>();
                    parkedSignals.put(remoteCallId, parked);
                }
                parked.add(() -> postSignal(body, type, null));
                Log.d(TAG, "Signal " + type + " parked until session row exists");
                return;
            }
        }

        postSignal(body, type, null);
    }

    /**
     * Posts one signal row and reports the outcome.
     *
     * @param attempt used for the one retry a rejected insert gets
     */
    private void postSignal(@NonNull Map<String, Object> body, @NonNull String type,
                            @Nullable Integer attempt) {
        final int current = attempt == null ? 1 : attempt;

        restApi.sendCallSignal(body).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (response.isSuccessful()) {
                    Log.d(TAG, "SIGNAL_TX_OK " + type);
                    return;
                }
                // A rejected insert used to be invisible here, which is how a
                // lost offer could look like a callee that simply never
                // answered. Report it, and give it exactly one retry so a
                // parent row that lands a moment late still gets the offer.
                Log.e(TAG, "SIGNAL_TX_FAILED " + type + " code=" + response.code());
                if (current < 2) {
                    handler.postDelayed(() -> postSignal(body, type, current + 1), 400L);
                }
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                Log.e(TAG, "SIGNAL_TX_ERROR " + type + ": " + t.getMessage());
                if (current < 2) {
                    handler.postDelayed(() -> postSignal(body, type, current + 1), 400L);
                }
            }
        });
    }

    private void startRemotePolling() {
        if (!isPolling || activeCallId == null) return;

        String remoteCallId = cleanUuid(activeCallId);

        Map<String, String> query = new HashMap<>();
        query.put("call_id", "eq." + remoteCallId);
        query.put("order", "created_at.asc");

        Map<String, String> sessionQuery = new HashMap<>();
        sessionQuery.put("id", "eq." + remoteCallId);
        restApi.getCallSessions(sessionQuery).enqueue(new Callback<List<CallSessionDto>>() {
            @Override
            public void onResponse(@NonNull Call<List<CallSessionDto>> call, @NonNull Response<List<CallSessionDto>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    CallSessionDto dto = response.body().get(0);
                    if (dto != null && dto.status != null && activeListener != null) {
                        CallSession s = LocalSignalingBus.getInstance().getSession(activeCallId);
                        if (s != null && !dto.status.equals(s.status)) {
                            s.status = dto.status;
                            s.durationSeconds = dto.durationSeconds;
                            activeListener.onCallSessionUpdated(s);
                        }
                        if (CallSession.STATUS_REJECTED.equals(dto.status) ||
                            CallSession.STATUS_ENDED.equals(dto.status) ||
                            CallSession.STATUS_CANCELLED.equals(dto.status)) {
                            activeListener.onCallEnded(dto.status);
                        }
                    }
                }
            }
            @Override public void onFailure(@NonNull Call<List<CallSessionDto>> call, @NonNull Throwable t) {}
        });

        restApi.getCallSignals(query).enqueue(new Callback<List<CallSignalDto>>() {
            @Override
            public void onResponse(@NonNull Call<List<CallSignalDto>> call, @NonNull Response<List<CallSignalDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String myUserId = SessionManager.getInstance(context).getUserId();
                    for (CallSignalDto signal : response.body()) {
                        if (signal.id != null && !processedSignalIds.contains(signal.id)) {
                            processedSignalIds.add(signal.id);

                            // Don't process our own outgoing signals
                            if (myUserId != null && myUserId.equals(signal.senderId)) {
                                continue;
                            }

                            processRemoteSignal(signal);
                        }
                    }
                }

                if (isPolling) {
                    handler.postDelayed(() -> startRemotePolling(), POLL_INTERVAL_MS);
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<CallSignalDto>> call, @NonNull Throwable t) {
                if (isPolling) {
                    handler.postDelayed(() -> startRemotePolling(), POLL_INTERVAL_MS);
                }
            }
        });
    }

    private void processRemoteSignal(CallSignalDto signal) {
        if (activeListener == null) return;

        try {
            switch (signal.signalType) {
                case "offer":
                    SessionDescription offer = new SessionDescription(SessionDescription.Type.OFFER, signal.payload);
                    activeListener.onOfferReceived(offer);
                    break;
                case "answer":
                    SessionDescription answer = new SessionDescription(SessionDescription.Type.ANSWER, signal.payload);
                    activeListener.onAnswerReceived(answer);
                    break;
                case "ice_candidate":
                    JsonObject json = gson.fromJson(signal.payload, JsonObject.class);
                    String sdpMid = json.has("sdpMid") ? json.get("sdpMid").getAsString() : "";
                    int sdpMLineIndex = json.has("sdpMLineIndex") ? json.get("sdpMLineIndex").getAsInt() : 0;
                    String sdp = json.has("sdp") ? json.get("sdp").getAsString() : "";
                    IceCandidate candidate = new IceCandidate(sdpMid, sdpMLineIndex, sdp);
                    activeListener.onIceCandidateReceived(candidate);
                    break;
                case "reject":
                case "end":
                    activeListener.onCallEnded(signal.signalType);
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing signal: " + e.getMessage());
        }
    }
}
