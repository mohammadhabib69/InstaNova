package com.insta.nova.calling;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.insta.nova.BuildConfig;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.remote.FirebaseTokens;
import com.insta.nova.data.remote.RetrofitClient;
import com.insta.nova.data.remote.api.RestApi;
import com.insta.nova.ui.chat.AudioCallActivity;
import com.insta.nova.ui.chat.VideoCallActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Prompt J coordinator: owns the {@code public.calls} state machine.
 *
 * <p>This is the only place that writes a call status, and every write is
 * mirrored to the other device through Supabase Realtime. The WebRTC media
 * exchange (SDP/ICE) is unchanged and still travels over {@code call_signals}
 * via {@link CallSignalingClient}; this class layers call <em>state</em> on top
 * without touching the call screens' design.
 *
 * <h3>State machine</h3>
 * <pre>
 *   caller                          callee
 *   ------                          ------
 *   INSERT status='calling'
 *     tone: CALLING                 (no ack yet)
 *   45s budget expires ──▶ 'no_answer' on both sides
 *
 *                    ◀── INSERT seen, device shows incoming UI
 *                        ACK: PATCH 'ringing' + ringing_at
 *   tone: CALLING ──▶ RINGBACK       ringtone starts, 30s budget starts
 *   30s budget expires ──▶ 'missed' on both sides
 *
 *                    ◀── callee accepts
 *                        PATCH 'connected' + connected_at
 *   timer from connected_at          timer from connected_at
 *
 *   either side ends ──▶ PATCH 'ended' + ended_at + ended_by + duration_sec
 *   both screens close within ~1s of the Realtime frame
 * </pre>
 *
 * <p>The 45s and 30s budgets are enforced by <em>both</em> sides writing the
 * same terminal status, so a device that missed the final Realtime frame still
 * self-heals instead of stranding the user on a call screen.
 */
public class CallManager {

    private static final String TAG = "CallManager";

    /** Caller gives up if the callee's device never acknowledges. */
    public static final long NO_ANSWER_TIMEOUT_MS = 45_000L;
    /** Callee gives up if the user never taps Accept after the ack. */
    public static final long RING_TIMEOUT_MS = 30_000L;

    /** Bounded wait for Firebase to restore a session at process start. */
    private static final int MAX_REALTIME_TOKEN_RETRIES = 12;
    private static final long REALTIME_TOKEN_RETRY_MS = 1_500L;

    private int realtimeRetries = 0;

    public interface CallObserver {
        /** An incoming call should be presented. */
        void onIncomingCall(@NonNull CallRecord record, @Nullable String callerName, @Nullable String callerAvatar);

        /** A non-terminal transition on the call this device is party to. */
        void onCallStateChanged(@NonNull CallRecord record);

        /**
         * The call reached a terminal state on the remote side (or locally).
         * The screen must close and return to the chat now.
         */
        void onCallTerminated(@NonNull CallRecord record);
    }

    private static CallManager instance;

    private final Context context;
    private final RestApi restApi;
    private final CallRealtimeClient realtime;
    private final CallSignalingClient signalingClient;
    private final CopyOnWriteArrayList<CallObserver> observers = new CopyOnWriteArrayList<>();

    /** The call this device is currently party to, or null. */
    private volatile CallRecord activeCall;
    private volatile boolean activeIsOutgoing;
    private volatile String peerId;
    private volatile String peerName;
    private volatile String peerAvatar;

    /**
     * call_ids whose incoming UI has already been raised. Realtime and FCM can
     * both deliver the same call, and the poll path a third time; without this
     * the callee would see three incoming screens for one call.
     */
    private final Set<String> raisedIncoming = new HashSet<>();

    private static final android.os.Handler MAIN = new android.os.Handler(android.os.Looper.getMainLooper());

    private final Runnable noAnswerTimeout = new Runnable() {
        @Override
        public void run() {
            CallRecord call = activeCall;
            if (call == null || !activeIsOutgoing) return;
            // Only escalate if the callee still has not acked. If they acked,
            // their own 30s ring timeout owns the outcome.
            if (CallRecord.isAcked(call.status)) {
                Log.i(TAG, "no-answer timer fired but call is already " + call.status + "; ignoring");
                return;
            }
            Log.i(TAG, "no-answer: call " + call.id + " never acked");
            terminate(call.id, CallRecord.STATUS_NO_ANSWER, null, 0);
        }
    };

    public static synchronized CallManager getInstance(@NonNull Context context) {
        if (instance == null) {
            instance = new CallManager(context.getApplicationContext());
        }
        return instance;
    }

    private CallManager(Context context) {
        this.context = context;
        this.restApi = RetrofitClient.getInstance(context).getRestApi();
        this.realtime = CallRealtimeClient.getInstance(context);
        this.signalingClient = new CallSignalingClient(context);

        realtime.setListener(new CallRealtimeClient.Listener() {
            @Override
            public void onCallRow(@NonNull String eventType, @NonNull CallRecord record) {
                handleRealtimeRow(record);
            }

            @Override
            public void onRealtimeState(@NonNull String state, @Nullable String detail) {
                Log.i(TAG, "realtime " + state + (detail != null ? (": " + detail) : ""));
                if ("joined".equals(state)) {
                    // A reconnect may have missed frames; re-read any call that
                    // is still open so the UI cannot be stranded.
                    reconcileActiveCall();
                    surfaceMissedCalls();
                }
            }
        });
    }

    // ------------------------------------------------------------- lifecycle

    /**
     * Subscribes to Realtime. Call once after sign-in; safe to call repeatedly.
     *
     * <p>Retries while the Firebase token is still resolving: the first call
     * comes from {@code InstaApp.onCreate}, which runs before FirebaseAuth has
     * necessarily restored the user. Connecting anonymously would leave the
     * channel joined but delivering no rows, because RLS sees a null identity.
     */
    public void startRealtime() {
        surfaceMissedCalls();
        if (realtimeRetries >= MAX_REALTIME_TOKEN_RETRIES) {
            Log.w(TAG, "no Firebase token after " + MAX_REALTIME_TOKEN_RETRIES
                    + " retries; realtime will stay unauthenticated");
            return;
        }
        realtimeRetries++;
        FirebaseTokens.currentAsync(token -> {
            if (token != null && !token.isEmpty()) {
                realtimeRetries = 0;
                realtime.connect(token);
            } else {
                MAIN.postDelayed(this::startRealtime, REALTIME_TOKEN_RETRY_MS);
            }
        });
    }

    public void stopRealtime() {
        realtime.disconnect();
    }

    public void addObserver(@NonNull CallObserver observer) {
        if (!observers.contains(observer)) observers.add(observer);
    }

    public void removeObserver(@NonNull CallObserver observer) {
        observers.remove(observer);
    }

    @Nullable
    public CallRecord getActiveCall() {
        return activeCall;
    }

    public boolean isCallActive() {
        return activeCall != null && !CallRecord.isTerminal(activeCall.status);
    }

    public CallSignalingClient getSignalingClient() {
        return signalingClient;
    }

    @Nullable
    public String getPeerName() {
        return peerName;
    }

    @Nullable
    public String getPeerAvatar() {
        return peerAvatar;
    }

    private String me() {
        return SessionManager.getInstance(context).getUserId();
    }

    // ------------------------------------------------------- outgoing call

    /**
     * Places a call: inserts the {@code calls} row in status {@code calling},
     * starts the calling tone, arms the 45s no-answer budget, and asks the edge
     * function to wake the callee's device.
     *
     * <p>The generated id is used for both the {@code calls} row and the legacy
     * {@code call_sessions} row, so media signalling and call state share one
     * identity.
     */
    public void placeCall(@Nullable String preferredCallId, @NonNull String calleeId,
                          @Nullable String calleeName,
                          @Nullable String calleeAvatar, boolean isVideo,
                          @Nullable Runnable onReady) {
        String callerId = me();
        if (callerId == null) {
            Log.w(TAG, "cannot place a call without a signed-in user");
            return;
        }

        // The call screens hold this id and match Realtime rows against it
        // (`callId.equals(record.id)`), and the callee receives the same id
        // through the push payload. Generating one here instead of reusing the
        // caller's would silently break every state transition on the screen.
        final String callId = (preferredCallId != null && isValidUuid(preferredCallId))
                ? preferredCallId
                : UUID.randomUUID().toString();

        Map<String, Object> body = new HashMap<>();
        body.put("id", callId);
        body.put("caller_id", callerId);
        body.put("callee_id", calleeId);
        body.put("call_type", isVideo ? CallSession.TYPE_VIDEO : CallSession.TYPE_AUDIO);
        body.put("status", CallRecord.STATUS_CALLING);

        final CallRecord optimistic = new CallRecord();
        optimistic.id = callId;
        optimistic.callerId = callerId;
        optimistic.calleeId = calleeId;
        optimistic.callType = isVideo ? CallSession.TYPE_VIDEO : CallSession.TYPE_AUDIO;
        optimistic.status = CallRecord.STATUS_CALLING;
        optimistic.createdAt = nowIso();

        setActive(optimistic, true, calleeId, calleeName, calleeAvatar);
        CallTones.getInstance().start(CallTones.Type.CALLING);
        MAIN.postDelayed(noAnswerTimeout, NO_ANSWER_TIMEOUT_MS);

        restApi.createCall(body).enqueue(new Callback<List<CallRecord>>() {
            @Override
            public void onResponse(@NonNull Call<List<CallRecord>> call, @NonNull Response<List<CallRecord>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    CallRecord created = response.body().get(0);
                    setActive(created, true, calleeId, calleeName, calleeAvatar);
                    Log.i(TAG, "call created: " + created);
                    // Push only after the authoritative row exists. The Edge
                    // Function claims push_dispatched_at atomically, so retries
                    // remain one notification per call id.
                    dispatchPush(callId, callerId, calleeId, optimistic.callType);
                } else {
                    Log.e(TAG, "call insert failed: HTTP " + response.code());
                    CallTones.getInstance().stop();
                    MAIN.removeCallbacks(noAnswerTimeout);
                    clearActive(callId);
                }
                if (onReady != null) onReady.run();
            }

            @Override
            public void onFailure(@NonNull Call<List<CallRecord>> call, @NonNull Throwable t) {
                Log.e(TAG, "call insert failed: " + t.getMessage());
                CallTones.getInstance().stop();
                MAIN.removeCallbacks(noAnswerTimeout);
                clearActive(callId);
                if (onReady != null) onReady.run();
            }
        });
    }

    private void dispatchPush(String callId, String callerId, String calleeId, String callType) {
        Map<String, Object> body = new HashMap<>();
        body.put("call_id", callId);
        body.put("caller_id", callerId);
        body.put("callee_id", calleeId);
        body.put("call_type", callType);
        restApi.sendCallPush(body).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(@NonNull Call<Map<String, Object>> c, @NonNull Response<Map<String, Object>> r) {
                Log.i(TAG, "send-call-push HTTP " + r.code());
            }

            @Override
            public void onFailure(@NonNull Call<Map<String, Object>> c, @NonNull Throwable t) {
                Log.w(TAG, "send-call-push failed: " + t.getMessage());
            }
        });
    }

    // -------------------------------------------------------- incoming call

    /**
     * Entry point for an incoming call from either Realtime or FCM.
     *
     * <p>De-duplicates on call_id, and returns false if the id was already
     * handled so the caller knows not to raise the UI a second time.
     */
    public boolean claimIncomingCall(@NonNull String callId) {
        synchronized (raisedIncoming) {
            if (raisedIncoming.contains(callId)) {
                Log.i(TAG, "incoming " + callId + " already handled; de-duplicated");
                return false;
            }
            raisedIncoming.add(callId);
            // Bound the set: a long-lived process must not accumulate ids forever.
            if (raisedIncoming.size() > 64) {
                raisedIncoming.clear();
            }
        }
        return true;
    }

    /** Records the call this device is now party to as the callee. */
    public void setIncomingCall(@NonNull CallRecord record, @Nullable String name, @Nullable String avatar) {
        setActive(record, false, record.callerId, name, avatar);
    }

    // ------------------------------------------------------------- writes

    /**
     * THE ACK. Called the instant the callee's device displays the incoming
     * call — full-screen activity or notification. This write is what flips the
     * caller from "Calling…" to "Ringing…".
     */
    public void acknowledgeRinging(@NonNull String callId) {
        final String ringingAt = nowIso();
        Map<String, Object> body = new HashMap<>();
        body.put("status", CallRecord.STATUS_RINGING);
        body.put("ringing_at", ringingAt);

        // Compare-and-set is important for the foreground Realtime path and the
        // FCM/background path racing each other. Only a row still in `calling`
        // may be ACKed, so the first ACK owns ringing_at and later ACKs are no-ops.
        restApi.acknowledgeCall("eq." + callId, "eq." + CallRecord.STATUS_CALLING, body)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(@NonNull Call<Void> request,
                                           @NonNull Response<Void> response) {
                        if (response.isSuccessful()) {
                            Log.i(TAG, "ACK-OK " + callId + " ringing_at=" + ringingAt);
                        } else {
                            Log.e(TAG, "ACK failed HTTP " + response.code() + " for " + callId);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<Void> request, @NonNull Throwable t) {
                        Log.e(TAG, "ACK failed for " + callId + ": " + t.getMessage());
                    }
                });

        CallRecord call = activeCall;
        if (call != null && callId.equals(call.id)) {
            call.status = CallRecord.STATUS_RINGING;
            call.ringingAt = ringingAt;
            notifyState(call);
        }
    }

    /**
     * Callee answered. {@code connected_at} is written server-side by the same
     * request, and both devices read the timer origin back from the row, which
     * is what keeps the two timers in step.
     */
    public void accept(@NonNull String callId) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", CallRecord.STATUS_CONNECTED);
        body.put("connected_at", nowIso());
        patch(callId, body);

        CallTones.getInstance().stop();
        MAIN.removeCallbacks(noAnswerTimeout);

        CallRecord call = activeCall;
        if (call != null && callId.equals(call.id)) {
            call.status = CallRecord.STATUS_CONNECTED;
            call.connectedAt = nowIso();
            notifyState(call);
        }
    }

    /** Callee declined. */
    public void decline(@NonNull String callId) {
        terminate(callId, CallRecord.STATUS_DECLINED, me(), 0);
    }

    /**
     * Caller cancelled while still in the calling/ringing phase. Stored as
     * 'ended' with ended_by set to the caller, so the callee's incoming UI
     * closes on the same terminal state as a hang-up.
     */
    public void cancel(@NonNull String callId) {
        terminate(callId, CallRecord.STATUS_ENDED, me(), 0);
    }

    /** Either side hung up after connecting. */
    public void end(@NonNull String callId, int durationSec) {
        terminate(callId, CallRecord.STATUS_ENDED, me(), durationSec);
    }

    /** Ring timeout on the callee side. */
    public void ringTimedOut(@NonNull String callId) {
        terminate(callId, CallRecord.STATUS_MISSED, me(), 0);
    }

    /**
     * Writes a terminal status. Terminal states are written by whichever side
     * owns the timer, and every screen on both devices closes on seeing it.
     */
    private void terminate(@NonNull String callId, @NonNull String status,
                          @Nullable String endedBy, int durationSec) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", status);
        body.put("ended_at", nowIso());
        body.put("duration_sec", durationSec);
        if (endedBy != null) body.put("ended_by", endedBy);
        patch(callId, body);

        // Local effects are applied immediately rather than waiting for the
        // Realtime echo, so the user who acted never sees a stale screen.
        CallTones.getInstance().stop();
        MAIN.removeCallbacks(noAnswerTimeout);

        CallRecord call = activeCall;
        if (call != null && callId.equals(call.id)) {
            call.status = status;
            call.endedAt = nowIso();
            call.endedBy = endedBy;
            call.durationSec = durationSec;
            notifyTerminal(call);
            clearActive(callId);
        }
    }

    private void patch(@NonNull String callId, @NonNull Map<String, Object> body) {
        final Object statusObj = body.get("status");
        final String status = statusObj != null ? statusObj.toString() : "?";
        restApi.updateCall("eq." + callId, body).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (response.isSuccessful()) {
                    // App-side clock marker: comparing this with the peer's
                    // "remote terminal" line gives a true end-sync latency,
                    // independent of ADB polling overhead.
                    Log.i(TAG, "PATCH-OK " + status + " " + callId
                            + " t=" + System.currentTimeMillis());
                } else {
                    Log.e(TAG, "patch " + callId + " failed: HTTP " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                Log.e(TAG, "patch " + callId + " failed: " + t.getMessage());
            }
        });
    }

    // ------------------------------------------------------------ realtime

    /**
     * Applies a {@code calls} row delivered over Realtime.
     *
     * <p>RLS already guarantees the row belongs to this device, so the only
     * remaining checks are "is it the call I am party to" and, for an incoming
     * call, "has it already been raised".
     */
    private void handleRealtimeRow(@NonNull CallRecord record) {
        String myId = me();
        if (myId == null || record.id == null) return;

        boolean iAmCaller = myId.equals(record.callerId);
        boolean iAmCallee = myId.equals(record.calleeId);
        if (!iAmCaller && !iAmCallee) return;

        CallRecord current = activeCall;
        boolean isActive = current != null && record.id.equals(current.id);

        if (CallRecord.isTerminal(record.status)) {
            if (isActive) {
                Log.i(TAG, "remote terminal " + record.status + " for " + record.id);
                CallTones.getInstance().stop();
                MAIN.removeCallbacks(noAnswerTimeout);

                // If this device is the one showing the incoming screen, that
                // screen is not an observer and would never hear about the
                // terminal state — it has to be closed explicitly, otherwise a
                // caller cancelling leaves the callee staring at a dead ring
                // screen. This is the Prompt J "no device may be left stuck"
                // guarantee for the cancel/decline/timeout paths.
                if (!activeIsOutgoing) {
                    CallActivityBridge.closeIncomingUi(context, record);
                }

                notifyTerminal(record);

                // Record the thread entry on BOTH sides. Previously only the
                // callee wrote one, so a call the peer ended left the other
                // device's thread with no entry at all.
                CallThreadEntries.recordTerminatedCall(context, record, !activeIsOutgoing);
                clearActive(record.id);
            } else if (iAmCallee) {
                // An incoming call that ended before this device ever saw it —
                // e.g. the caller cancelled while we were backgrounded. Close
                // any incoming UI and leave a missed entry.
                CallActivityBridge.closeIncomingUi(context, record);
                CallThreadEntries.recordTerminatedCall(context, record, true);
            }
            return;
        }

        if (isActive) {
            String previous = current.status;
            current.status = record.status;
            current.ringingAt = record.ringingAt;
            current.connectedAt = record.connectedAt;

            // Tone follows state, and only for the caller.
            if (activeIsOutgoing) {
                if (CallRecord.STATUS_RINGING.equals(record.status)) {
                    CallTones.getInstance().start(CallTones.Type.RINGBACK);
                } else if (CallRecord.STATUS_CONNECTED.equals(record.status)) {
                    CallTones.getInstance().stop();
                }
            }

            notifyState(current);
            Log.i(TAG, "state " + previous + " -> " + record.status);
            return;
        }

        // Not currently in a call: this is a new incoming call.
        if (iAmCallee && CallRecord.STATUS_CALLING.equals(record.status)) {
            onIncomingCallDetected(record);
        }
    }

    private void onIncomingCallDetected(@NonNull CallRecord record) {
        if (!claimIncomingCall(record.id)) return;

        setActive(record, false, record.callerId, null, null);

        // ACK here, not only in the Activity. When this device is backgrounded,
        // Android refuses the activity start and we fall back to a notification;
        // the screen then never reaches onCreate, so an Activity-only ACK left
        // the row at 'calling' and the caller stuck on "Calling…". This path
        // commits to presenting the call either way, so it owns the ACK; the
        // Activity's own ACK and the FCM one are no-ops behind the CAS.
        acknowledgeRinging(record.id);

        fetchCallerIdentity(record);
    }

    /** Resolves the caller's display name/avatar for the incoming UI. */
    private void fetchCallerIdentity(@NonNull CallRecord record) {
        if (record.callerId == null) return;
        Map<String, String> q = new HashMap<>();
        q.put("id", "eq." + record.callerId);
        q.put("select", "username,avatar_url");
        q.put("limit", "1");

        restApi.getProfiles(q).enqueue(new Callback<List<com.insta.nova.data.remote.dto.ProfileDto>>() {
            @Override
            public void onResponse(@NonNull Call<List<com.insta.nova.data.remote.dto.ProfileDto>> call,
                                   @NonNull Response<List<com.insta.nova.data.remote.dto.ProfileDto>> response) {
                String name = null;
                String avatar = null;
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    com.insta.nova.data.remote.dto.ProfileDto p = response.body().get(0);
                    if (p != null) {
                        name = p.getUsername();
                        avatar = p.getAvatarUrl();
                    }
                }
                peerName = name;
                peerAvatar = avatar;
                notifyIncoming(record, name, avatar);
                launchIncomingUi(record, name, avatar);
            }

            @Override
            public void onFailure(@NonNull Call<List<com.insta.nova.data.remote.dto.ProfileDto>> call,
                                  @NonNull Throwable t) {
                notifyIncoming(record, null, null);
                launchIncomingUi(record, null, null);
            }
        });
    }

    /**
     * Opens the incoming-call screen immediately.
     *
     * <p>Reached both when the process is foregrounded (this device's own
     * Realtime subscription fires on its own INSERT) and when it was woken by
     * FCM. {@link #claimIncomingCall} has already guaranteed this runs at most
     * once per call id, so the two paths cannot stack two screens.
     */
    private void launchIncomingUi(@NonNull CallRecord record, @Nullable String name, @Nullable String avatar) {
        if (record.callType == null) record.callType = CallSession.TYPE_AUDIO;
        Intent intent = new Intent(context, IncomingCallActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra(IncomingCallActivity.EXTRA_CALL_ID, record.id);
        intent.putExtra(IncomingCallActivity.EXTRA_CALL_TYPE, record.callType);
        intent.putExtra(IncomingCallActivity.EXTRA_CALLER_ID, record.callerId);
        intent.putExtra(IncomingCallActivity.EXTRA_CALLER_USERNAME, name);
        intent.putExtra(IncomingCallActivity.EXTRA_CALLER_AVATAR, avatar);
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            // Android 10+ blocks background activity starts. The FCM path posts
            // a full-screen-intent notification as the fallback, so this is not
            // fatal — record it so logcat shows why no screen appeared.
            Log.w(TAG, "could not launch incoming UI (background start blocked): " + e.getMessage());
            try {
                CallNotificationHelper.showIncomingCallNotification(context, record, name, avatar);
            } catch (Exception e2) {
                Log.e(TAG, "fallback notification failed too: " + e2.getMessage());
            }
        }
    }

    /**
     * Prompt K catch-up for terminal calls that happened while this callee was
     * offline. The PATCH uses callee_surfaced_at=IS NULL as a CAS marker, so a
     * relaunch or another reconnect cannot duplicate the notification/entry.
     */
    private void surfaceMissedCalls() {
        final String myId = me();
        if (myId == null || myId.isEmpty()) return;
        Map<String, String> q = new HashMap<>();
        q.put("callee_id", "eq." + myId);
        q.put("status", "in.(missed,no_answer)");
        q.put("callee_surfaced_at", "is.null");
        q.put("select", "*");
        q.put("order", "ended_at.asc");
        q.put("limit", "20");
        restApi.getTerminalCalleeCalls(q).enqueue(new Callback<List<CallRecord>>() {
            @Override
            public void onResponse(@NonNull Call<List<CallRecord>> request,
                                   @NonNull Response<List<CallRecord>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    Log.w(TAG, "missed catch-up query HTTP " + response.code());
                    return;
                }
                for (CallRecord record : response.body()) {
                    if (record == null || record.id == null) continue;
                    Map<String, Object> marker = new HashMap<>();
                    marker.put("callee_surfaced_at", nowIso());
                    restApi.markCalleeSurfaced("eq." + record.id, "is.null", marker).enqueue(new Callback<Void>() {
                        @Override public void onResponse(@NonNull Call<Void> c, @NonNull Response<Void> r) {
                            if (!r.isSuccessful()) {
                                Log.w(TAG, "missed catch-up marker HTTP " + r.code() + " for " + record.id);
                                return;
                            }
                            fetchCallerNameForMissed(record);
                            CallThreadEntries.recordTerminatedCall(context, record, true);
                            Log.i(TAG, "missed catch-up surfaced " + record.id);
                        }
                        @Override public void onFailure(@NonNull Call<Void> c, @NonNull Throwable t) {
                            Log.w(TAG, "missed catch-up marker failed for " + record.id + ": " + t.getMessage());
                        }
                    });
                }
            }
            @Override public void onFailure(@NonNull Call<List<CallRecord>> request, @NonNull Throwable t) {
                Log.w(TAG, "missed catch-up query failed: " + t.getMessage());
            }
        });
    }

    private void fetchCallerNameForMissed(@NonNull CallRecord record) {
        if (record.callerId == null) {
            CallNotificationHelper.showMissedCallNotification(context, "user", record.isVideo());
            return;
        }
        Map<String, String> q = new HashMap<>();
        q.put("id", "eq." + record.callerId);
        q.put("select", "username");
        q.put("limit", "1");
        restApi.getProfiles(q).enqueue(new Callback<List<com.insta.nova.data.remote.dto.ProfileDto>>() {
            @Override public void onResponse(@NonNull Call<List<com.insta.nova.data.remote.dto.ProfileDto>> c,
                                              @NonNull Response<List<com.insta.nova.data.remote.dto.ProfileDto>> r) {
                String name = "user";
                if (r.isSuccessful() && r.body() != null && !r.body().isEmpty() && r.body().get(0) != null) {
                    name = r.body().get(0).getUsername();
                }
                CallNotificationHelper.showMissedCallNotification(context, name, record.isVideo());
            }
            @Override public void onFailure(@NonNull Call<List<com.insta.nova.data.remote.dto.ProfileDto>> c,
                                            @NonNull Throwable t) {
                CallNotificationHelper.showMissedCallNotification(context, "user", record.isVideo());
            }
        });
    }

    /**
     * Re-reads the open call after a Realtime reconnect.
     *
     * <p>Frames sent while the socket was down are gone. Without this, a device
     * that reconnected during an end could sit on a call screen forever.
     */
    private void reconcileActiveCall() {
        final String myId = me();
        CallRecord current = activeCall;
        if (myId == null || current == null) return;

        Map<String, String> q = new HashMap<>();
        q.put("id", "eq." + current.id);
        q.put("limit", "1");

        restApi.getCalls(q).enqueue(new Callback<List<CallRecord>>() {
            @Override
            public void onResponse(@NonNull Call<List<CallRecord>> call, @NonNull Response<List<CallRecord>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    CallRecord fresh = response.body().get(0);
                    if (CallRecord.isTerminal(fresh.status)) {
                        Log.i(TAG, "reconcile: call already " + fresh.status);
                        CallTones.getInstance().stop();
                        notifyTerminal(fresh);
                        clearActive(fresh.id);
                    } else if (!fresh.status.equals(current.status)) {
                        current.status = fresh.status;
                        notifyState(current);
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<CallRecord>> call, @NonNull Throwable t) {
            }
        });
    }

    // ------------------------------------------------------------ plumbing

    private void setActive(CallRecord record, boolean outgoing, String peer,
                          String name, String avatar) {
        activeCall = record;
        activeIsOutgoing = outgoing;
        peerId = peer;
        peerName = name;
        peerAvatar = avatar;
    }

    private void clearActive(@Nullable String callId) {
        if (callId == null || activeCall == null || callId.equals(activeCall.id)) {
            activeCall = null;
        }
    }

    private void notifyIncoming(CallRecord r, String name, String avatar) {
        for (CallObserver o : observers) o.onIncomingCall(r, name, avatar);
    }

    private void notifyState(CallRecord r) {
        for (CallObserver o : observers) o.onCallStateChanged(r);
    }

    private void notifyTerminal(CallRecord r) {
        for (CallObserver o : observers) o.onCallTerminated(r);
    }

    static String nowIso() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(new Date());
    }

    // ========================================================================
    // Compatibility surface for the Prompt I call screens
    //
    // AudioCallActivity / VideoCallActivity / IncomingCallActivity /
    // CallActionReceiver were written against the older CallManager API. These
    // shims keep them compiling and keep their behaviour, but every state
    // change now flows through the `calls` state machine above rather than the
    // legacy `call_sessions` polling.
    // ========================================================================

    /** Legacy entry point used by AudioCallActivity / VideoCallActivity. */
    public CallSession startOutgoingCall(@Nullable String legacyCallId,
                                         @Nullable String conversationId,
                                         @NonNull String recipientId,
                                         @Nullable String recipientUsername,
                                         @Nullable String recipientAvatar,
                                         boolean isVideo) {
        String callerId = me();
        CallSession session = new CallSession(
                legacyCallId == null || legacyCallId.isEmpty()
                        ? UUID.randomUUID().toString() : legacyCallId,
                conversationId,
                callerId != null ? callerId : "local-user",
                recipientId,
                isVideo ? CallSession.TYPE_VIDEO : CallSession.TYPE_AUDIO,
                true);
        session.recipientUsername = recipientUsername;
        session.recipientAvatar = recipientAvatar;

        placeCall(session.callId, recipientId, recipientUsername, recipientAvatar, isVideo, null);

        // Also publish the legacy `call_sessions` row that CallSignalingClient
        // uses to carry the WebRTC SDP/ICE exchange. Without it the media
        // signalling has no remote counterpart even though call *state* works.
        signalingClient.createRemoteSession(session);
        return session;
    }

    public boolean isInCall() {
        return isCallActive();
    }

    public void clearActiveSession(@Nullable String callId) {
        clearActive(callId);
    }

    /** Legacy decline path (IncomingCallActivity + notification action). */
    public void rejectIncomingCall(@Nullable String callId) {
        if (callId == null) return;
        decline(callId);
        CallRecord record = terminalSnapshot(callId, CallRecord.STATUS_DECLINED);
        if (record != null) {
            CallThreadEntries.recordTerminatedCall(context, record, true);
        }
    }

    /**
     * Accepts and opens the existing call screen.
     *
     * <p>The screen itself is unchanged; only what precedes it differs — the
     * {@code connected} write now carries {@code connected_at}, which both
     * devices read back to start their timer on the same instant.
     */
    public void acceptIncomingCall(@NonNull Context fromContext, @NonNull String callId) {
        accept(callId);

        CallSession session = LocalSignalingBus.getInstance().getSession(callId);
        if (session == null) {
            // No legacy session: rebuild one from the active `calls` row so the
            // existing call screen still receives the peer identity it needs.
            CallRecord record = activeCall;
            String type = (record != null && record.callType != null)
                    ? record.callType : CallSession.TYPE_AUDIO;
            session = new CallSession(callId, null, record != null ? record.callerId : null,
                    me(), type, false);
            session.callerUsername = peerName;
            session.callerAvatar = peerAvatar;
        }

        boolean isVideo = session.isVideo();
        Intent intent;
        if (isVideo) {
            intent = new Intent(fromContext, VideoCallActivity.class);
            intent.putExtra(VideoCallActivity.EXTRA_CALL_ID, session.callId);
            intent.putExtra(VideoCallActivity.EXTRA_IS_INCOMING, true);
            intent.putExtra(VideoCallActivity.EXTRA_CONVERSATION_ID, session.conversationId);
            intent.putExtra(VideoCallActivity.EXTRA_OTHER_USER_ID, session.callerId);
            intent.putExtra(VideoCallActivity.EXTRA_OTHER_USERNAME, session.callerUsername);
            intent.putExtra(VideoCallActivity.EXTRA_OTHER_AVATAR, session.callerAvatar);
        } else {
            intent = new Intent(fromContext, AudioCallActivity.class);
            intent.putExtra(AudioCallActivity.EXTRA_CALL_ID, session.callId);
            intent.putExtra(AudioCallActivity.EXTRA_IS_INCOMING, true);
            intent.putExtra(AudioCallActivity.EXTRA_CONVERSATION_ID, session.conversationId);
            intent.putExtra(AudioCallActivity.EXTRA_OTHER_USER_ID, session.callerId);
            intent.putExtra(AudioCallActivity.EXTRA_OTHER_USERNAME, session.callerUsername);
            intent.putExtra(AudioCallActivity.EXTRA_OTHER_AVATAR, session.callerAvatar);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        fromContext.startActivity(intent);
    }

    public void cancelCall(@Nullable String callId) {
        if (callId == null) return;
        cancel(callId);
        CallRecord record = terminalSnapshot(callId, CallRecord.STATUS_ENDED);
        if (record != null) {
            CallThreadEntries.recordTerminatedCall(context, record, false);
        }
    }

    public void endCall(@Nullable String callId, int durationSeconds) {
        if (callId == null) return;
        end(callId, durationSeconds);
        CallRecord record = terminalSnapshot(callId, CallRecord.STATUS_ENDED);
        if (record != null) {
            CallThreadEntries.recordTerminatedCall(context, record, activeIsOutgoing);
        }
    }

    public void startIncomingCallListener() {
        startRealtime();
    }

    public void stopIncomingCallListener() {
        stopRealtime();
    }

    /**
     * Legacy incoming hand-off kept for {@code InstaFirebaseMessagingService}.
     *
     * <p>The caller MUST already have claimed this call id. Claiming here as
     * well would be a self-inflicted de-duplication: the second claim returns
     * false and this method would return before the caller showed anything, so
     * the very first incoming call of a session would be silently dropped.
     */
    public void onIncomingCallReceived(@NonNull CallSession session) {
        if (session.callId == null) return;

        // Defence in depth against the same self-delivery that the FCM handler
        // guards against: a push for a call this device placed must never open
        // an incoming screen over the live call screen.
        String myId = me();
        if (myId != null && myId.equals(session.callerId)) {
            Log.i(TAG, "ignoring incoming hand-off for our own outgoing call " + session.callId);
            return;
        }

        CallRecord record = new CallRecord();
        record.id = session.callId;
        record.callerId = session.callerId;
        record.calleeId = me();
        record.callType = session.callType;
        record.status = CallRecord.STATUS_CALLING;
        setIncomingCall(record, session.callerUsername, session.callerAvatar);
        notifyIncoming(record, session.callerUsername, session.callerAvatar);

        // The push path must raise the incoming screen itself. The Realtime
        // path does this in onIncomingCallDetected(), but a push can arrive
        // when Realtime never fires (app process just woken, socket not yet
        // joined), so this cannot be left to the observer callbacks.
        launchIncomingUi(record, session.callerUsername, session.callerAvatar);
    }

    /**
     * Builds the terminal snapshot the thread entry needs, from the row we
     * already hold. The write itself has been issued; this only reads local
     * state so the entry appears without waiting for the Realtime echo.
     */
    @Nullable
    private CallRecord terminalSnapshot(@NonNull String callId, @NonNull String status) {
        CallRecord current = activeCall;
        if (current != null && callId.equals(current.id)) {
            CallRecord copy = new CallRecord();
            copy.id = current.id;
            copy.callerId = current.callerId;
            copy.calleeId = current.calleeId;
            copy.callType = current.callType;
            copy.status = status;
            copy.durationSec = current.durationSec;
            copy.createdAt = current.createdAt;
            return copy;
        }
        // clearActive() already ran, so fall back to a minimal row built from
        // the legacy session the activities keep in the bus.
        CallSession session = LocalSignalingBus.getInstance().getSession(callId);
        if (session == null) return null;
        CallRecord copy = new CallRecord();
        copy.id = session.callId;
        copy.callerId = session.callerId;
        copy.calleeId = session.recipientId;
        copy.callType = session.callType;
        copy.status = status;
        copy.durationSec = session.durationSeconds;
        return copy;
    }

    @Nullable
    public CallSession getCurrentSession() {
        return LocalSignalingBus.getInstance().getSession(activeCall == null ? "" : activeCall.id);
    }

    /**
     * Static helpers the existing call screens use. They are kept here so the
     * state machine stays in one file and the activities only react, never write.
     */
    public static boolean isValidUuid(@Nullable String value) {
        if (value == null || value.trim().isEmpty()) return false;
        try {
            UUID.fromString(value.trim());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static String supabaseUrl() {
        return BuildConfig.SUPABASE_URL;
    }
}