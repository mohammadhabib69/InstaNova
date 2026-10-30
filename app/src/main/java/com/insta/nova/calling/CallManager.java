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

    /**
     * How old a non-terminal {@code calls} row may be and still surface as a live call.
     *
     * <p>Longer than the ring timeout on purpose: a call placed while this device had no
     * signal should still ring when it comes back a moment later. Two minutes is well
     * past {@link #NO_ANSWER_TIMEOUT_MS}, so anything older is an abandoned row.
     */
    private static final long LIVE_CALL_MAX_AGE_MS = 120_000L;

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
        sweepStaleCalls();
        startIncomingPolling();
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

    private static final long INCOMING_POLL_MS = 2500L;
    private final Runnable incomingPoll = new Runnable() {
        @Override public void run() {
            pollIncoming();
            MAIN.postDelayed(this, INCOMING_POLL_MS);
        }
    };
    private boolean incomingPolling;

    /**
     * Discovers an incoming call without Realtime.
     *
     * <p>Realtime cannot authenticate with a Firebase token, so the only inbound path was dead and
     * the callee never saw a ring. This asks the {@code calls} table for my own pending rows and
     * feeds them through exactly the same handler Realtime used.
     */
    public void startIncomingPolling() {
        if (incomingPolling) return;
        incomingPolling = true;
        MAIN.post(incomingPoll);
    }

    /**
     * Closes calls that were started but never finished — mine as caller or callee.
     *
     * <p>Runs once per sign-in. Without it a row left at {@code calling}/{@code ringing}
     * is immortal: the poll ignores it now (too old to ring), nothing else looks at it,
     * and it accumulates. The statuses mirror the ring timeout — {@code no_answer} when
     * the callee never saw it, {@code missed} when it rang and nobody picked up.
     */
    /**
     * Closes one leaked row without the write-once {@code ended_by} filter.
     *
     * <p>Deliberately not routed through {@link #terminate}: that method must stay
     * write-once for real hang-ups. Here the only thing being asserted is the current
     * status, and the response tells us whether anything actually changed.
     */
    private void closeLeakedCall(@NonNull String callId, @NonNull String closeAs) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", closeAs);
        body.put("ended_at", nowIso());
        body.put("duration_sec", 0);
        restApi.closePendingCall("eq." + callId, "in.(calling,ringing,connected)", body)
                .enqueue(new Callback<List<CallRecord>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<CallRecord>> call,
                                           @NonNull Response<List<CallRecord>> response) {
                        int n = response.body() == null ? 0 : response.body().size();
                        if (response.isSuccessful() && n > 0) {
                            Log.i(TAG, "CLOSE-OK " + callId + " -> " + closeAs);
                        } else if (response.isSuccessful()) {
                            Log.i(TAG, "CLOSE-NOOP " + callId + " already closed");
                        } else {
                            Log.w(TAG, "CLOSE-FAIL " + callId + " HTTP " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<CallRecord>> call, @NonNull Throwable t) {
                        Log.w(TAG, "CLOSE-FAIL " + callId + " " + t.getMessage());
                    }
                });
    }

    private void sweepStaleCalls() {
        final String myId = me();
        if (myId == null || myId.isEmpty()) return;
        if (isCallActive()) return;

        Map<String, String> q = new java.util.HashMap<>();
        q.put("or", "(caller_id.eq." + myId + ",callee_id.eq." + myId + ")");
        // `connected` is included: a call whose two devices both went away never got an
        // ended_at, and a row that says "connected" for a day is as wrong as one that
        // says "ringing" for a day.
        q.put("status", "in.(calling,ringing,connected)");
        q.put("created_at", "lt." + isoSecondsAgo(LIVE_CALL_MAX_AGE_MS));
        q.put("select", "*");
        q.put("limit", "20");
        restApi.getCalls(q).enqueue(new Callback<List<CallRecord>>() {
            @Override
            public void onResponse(@NonNull Call<List<CallRecord>> call,
                                   @NonNull Response<List<CallRecord>> response) {
                if (!response.isSuccessful() || response.body() == null) return;
                for (CallRecord row : response.body()) {
                    if (row == null || row.id == null) continue;
                    String closeAs;
                    if (CallRecord.STATUS_CONNECTED.equals(row.status)) {
                        closeAs = CallRecord.STATUS_ENDED;
                    } else {
                        closeAs = row.ringingAt == null
                                ? CallRecord.STATUS_NO_ANSWER : CallRecord.STATUS_MISSED;
                    }
                    Log.i(TAG, "sweeping stale " + row.status + " " + row.id + " -> " + closeAs);
                    closeLeakedCall(row.id, closeAs);
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<CallRecord>> call, @NonNull Throwable t) {
                Log.w(TAG, "stale sweep failed: " + t.getMessage());
            }
        });
    }

    public void stopIncomingPolling() {
        incomingPolling = false;
        MAIN.removeCallbacks(incomingPoll);
    }

    private void pollIncoming() {
        if (isCallActive()) return;
        String me = SessionManager.getInstance(context).getUserId();
        if (me == null || me.isEmpty()) return;
        Map<String, String> q = new java.util.HashMap<>();
        q.put("callee_id", "eq." + me);
        q.put("status", "in.(calling,ringing)");
        // Nothing older than the ring budget can still be live, but this query used to
        // have no age filter at all: it picked up whatever `calling` row was left behind
        // and rang for it. A row abandoned ten hours earlier rang the phone the moment
        // the app was opened, and the ring timeout then "answered" it 30 s later.
        q.put("created_at", "gte." + isoSecondsAgo(LIVE_CALL_MAX_AGE_MS));
        q.put("order", "created_at.desc");
        q.put("limit", "1");
        restApi.getCalls(q).enqueue(new retrofit2.Callback<java.util.List<CallRecord>>() {
            @Override public void onResponse(@NonNull retrofit2.Call<java.util.List<CallRecord>> call,
                                             @NonNull retrofit2.Response<java.util.List<CallRecord>> response) {
                if (!response.isSuccessful() || response.body() == null || response.body().isEmpty()) return;
                handleRealtimeRow(response.body().get(0));
            }

            @Override public void onFailure(@NonNull retrofit2.Call<java.util.List<CallRecord>> call,
                                            @NonNull Throwable t) {
                Log.w(TAG, "incoming poll failed: " + t.getMessage());
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

    /**
     * True when a still-pending row was created longer ago than any live call can be.
     *
     * <p>Unknown creation time is treated as fresh: a row that cannot be dated should
     * not be silently swallowed, and the ring timeout will close it either way.
     */
    private static boolean isStalePending(@NonNull CallRecord record) {
        long created = record.createdAtMillis();
        if (created <= 0) return false;
        return System.currentTimeMillis() - created > LIVE_CALL_MAX_AGE_MS;
    }

    /** PostgREST timestamptz filter value, e.g. {@code gte.2026-11-01T11:02:03+00:00}. */
    @NonNull
    private static String isoSecondsAgo(long millis) {
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US);
        f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return f.format(new java.util.Date(System.currentTimeMillis() - millis));
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

        // The call screens must show the person being called. Callers reach here from a
        // chat header (which knows the name but not always the avatar), from the call
        // history and from a call bubble, so resolve the missing half from the row that
        // owns it instead of drawing a grey placeholder for the whole call.
        if (calleeId != null && (isEmpty(calleeName) || isEmpty(calleeAvatar))) {
            resolveProfile(calleeId, (name, avatar) -> {
                if (isEmpty(peerName) && name != null) peerName = name;
                if (isEmpty(peerAvatar) && avatar != null) peerAvatar = avatar;
                CallRecord current = activeCall;
                if (current != null && callId.equals(current.id)) notifyState(current);
            });
        }

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
        CallRecord current = activeCall;
        if (current != null && callId.equals(current.id) && CallRecord.isTerminal(current.status)) {
            Log.i(TAG, "accept ignored: call " + callId + " is already terminal (" + current.status + ")");
            return;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("status", CallRecord.STATUS_CONNECTED);
        body.put("connected_at", nowIso());
        restApi.acceptCall("eq." + callId, "in.(calling,ringing)", body).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (response.isSuccessful()) {
                    Log.i(TAG, "ACCEPT-OK " + callId);
                } else {
                    Log.e(TAG, "accept " + callId + " failed: HTTP " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                Log.e(TAG, "accept " + callId + " failed: " + t.getMessage());
            }
        });

        CallTones.getInstance().stop();
        // Accepting ends the ring everywhere: the player, the vibration and the
        // Accept/Decline card in the shade.
        CallRingtone.get().stopFor(callId);
        CallNotificationHelper.cancelIncomingCallNotification(context);
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
     * Prompt M: Writes a terminal status. Terminal state is WRITE-ONCE.
     * The first terminal transition wins. Once a call is in a terminal status,
     * no later write may change its status, ended_by, ended_at, or duration_sec.
     */
    private void terminate(@NonNull String callId, @NonNull String status,
                          @Nullable String endedBy, int durationSec) {
        CallRecord current = activeCall;
        if (current != null && callId.equals(current.id) && CallRecord.isTerminal(current.status)) {
            Log.i(TAG, "terminate ignored: call " + callId + " is already locally terminal (" + current.status + ")");
            return;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("status", status);
        body.put("ended_at", nowIso());
        body.put("duration_sec", durationSec);
        if (endedBy != null) body.put("ended_by", endedBy);

        // Prompt M: conditional terminal write — only matches rows in calling, ringing, or connected,
        // and with ended_by IS NULL. Any subsequent write matches 0 rows and changes nothing.
        restApi.terminateCall("eq." + callId, "in.(calling,ringing,connected)", "is.null", body)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                        if (response.isSuccessful()) {
                            Log.i(TAG, "TERMINATE-OK " + status + " " + callId + " endedBy=" + endedBy
                                    + " t=" + System.currentTimeMillis());
                        } else {
                            Log.e(TAG, "terminate " + callId + " failed: HTTP " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                        Log.e(TAG, "terminate " + callId + " failed: " + t.getMessage());
                    }
                });

        // Local effects are applied immediately rather than waiting for the
        // Realtime echo, so the user who acted never sees a stale screen.
        CallTones.getInstance().stop();
        CallRingtone.get().stopFor(callId);
        CallNotificationHelper.cancelAllCallNotifications(context);
        MAIN.removeCallbacks(noAnswerTimeout);

        if (current != null && callId.equals(current.id)) {
            current.status = status;
            current.endedAt = nowIso();
            current.endedBy = endedBy;
            current.durationSec = durationSec;
            notifyTerminal(current);
            // Persist the finished row locally. Without this the history entry kept the
            // status it had when the call started (often "answered" with duration 0, or a
            // pre-answer state), which is why answered calls could still read "Missed".
            try {
                com.insta.nova.calling.CallHistoryStore.getInstance(context)
                        .upsertCall(current, SessionManager.getInstance(context).getUserId());
            } catch (Exception e) {
                Log.w(TAG, "history upsert failed: " + e.getMessage());
            }
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

        // A pending row that is already older than the ring budget is not a call to
        // raise; it is a row nobody closed. Checked here rather than only in the poll
        // query so every entry point inherits it — Realtime, the poll, and any future
        // catch-up query.
        if (!CallRecord.isTerminal(record.status) && !isActive && isStalePending(record)) {
            // Close it instead of only ignoring it. A pending row nobody finished stays
            // pending forever otherwise, and the next launch finds it again: the row
            // that rang this phone today had been sitting there since the previous day.
            // ended_by stays null, which is accurate — no participant ended it, it aged
            // out. no_answer when the callee never even saw it, missed when it did ring.
            String closeAs;
            if (CallRecord.STATUS_CONNECTED.equals(record.status)) {
                closeAs = CallRecord.STATUS_ENDED;
            } else {
                closeAs = record.ringingAt == null
                        ? CallRecord.STATUS_NO_ANSWER : CallRecord.STATUS_MISSED;
            }
            Log.i(TAG, "stale " + record.status + " row " + record.id + " -> " + closeAs);
            closeLeakedCall(record.id, closeAs);
            return;
        }

        if (CallRecord.isTerminal(record.status)) {
            // History first, and regardless of whether this call is still "active" here.
            // The terminal row is the only record that carries duration_sec and
            // connected_at, and by the time it arrives the local call is often already
            // finished - which is why history kept the ring-time status and showed
            // answered calls as missed with 0s.
            try {
                CallHistoryStore.getInstance(context).upsertCall(record, myId);
            } catch (Exception e) {
                Log.w(TAG, "history upsert (terminal) failed: " + e.getMessage());
            }
            if (isActive) {
                Log.i(TAG, "remote terminal " + record.status + " for " + record.id);
                CallTones.getInstance().stop();
                CallRingtone.get().stopFor(record.id);
                CallNotificationHelper.cancelAllCallNotifications(context);
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
                CallRingtone.get().stopFor(record.id);
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

    /**
     * The callee never had a ring timeout. The caller's own 30 s timer carried a comment
     * claiming "their own 30 s ring timeout owns the outcome" - it did not exist, so an
     * unanswered call stayed at status=ringing forever and nothing ever became a missed
     * call. This is that timer.
     */
    private void scheduleRingTimeout(@NonNull final CallRecord ringing) {
        final String callId = ringing.id;
        MAIN.postDelayed(new Runnable() {
            @Override
            public void run() {
                CallRecord current = activeCall;
                if (current == null || !callId.equals(current.id)) return;
                // Only a call that moved past ringing (connected, or already terminal)
                // is out of this timer's hands. "ringing" is exactly the state we are
                // waiting to expire - isAcked() counts it as acked, which is why the
                // first version of this timer never fired.
                if (!CallRecord.STATUS_RINGING.equals(current.status)
                        && !CallRecord.STATUS_CALLING.equals(current.status)) {
                    Log.i(TAG, "ring timer: " + callId + " already " + current.status + "; ignoring");
                    return;
                }
                Log.i(TAG, "ring timer fired: " + callId + " unanswered, marking missed");
                // Order matters: terminate() refuses a call that is already locally
                // terminal, so setting the status here first made the server write a
                // no-op and left the row at "ringing" forever. Let terminate() own the
                // transition; it updates the in-memory record and clearActive() as well.
                ringTimedOut(callId);
                // Write the local thread entry too: terminate() only updates state, and the
                // realtime echo that normally writes the bubble never arrives when the call
                // simply times out.
                CallThreadEntries.recordTerminatedCall(context, current, true);
            }
        }, RING_TIMEOUT_MS);
    }

    private void onIncomingCallDetected(@NonNull CallRecord record) {
        scheduleRingTimeout(record);
        com.insta.nova.data.local.AppSettingsStore callSettings =
                new com.insta.nova.data.local.AppSettingsStore(context);
        if (!callSettings.allowsNotificationType("call")) {
            Log.i(TAG, "call notifications off; ignoring incoming " + record.id);
            return;
        }
        if (record.callerId != null
                && new com.insta.nova.data.local.RelationshipStore(context)
                .isBlocked(record.callerId)) {
            Log.i(TAG, "blocked caller; ignoring incoming " + record.id);
            return;
        }
        if (com.insta.nova.data.local.AppSettingsStore.WHO_NOBODY.equals(
                callSettings.whoCanCall())) {
            Log.i(TAG, "who_can_call=nobody; ignoring incoming " + record.id);
            return;
        }

        if (!claimIncomingCall(record.id)) return;

        setActive(record, false, record.callerId, null, null);

        // Ring here, at the decision point, not only in the incoming screen. Android 10+
        // refuses background activity starts, so a callee whose phone is locked or whose
        // app is backgrounded may never get the screen — but it must still ring. This is
        // idempotent per call id, so the screen starting it again is a no-op.
        CallRingtone.get().start(context, record.id);

        // ACK here, not only in the Activity. When this device is backgrounded,
        // Android refuses the activity start and we fall back to a notification;
        // the screen then never reaches onCreate, so an Activity-only ACK left
        // the row at 'calling' and the caller stuck on "Calling…". This path
        // commits to presenting the call either way, so it owns the ACK; the
        // Activity's own ACK and the FCM one are no-ops behind the CAS.
        acknowledgeRinging(record.id);

        fetchCallerIdentity(record);
    }

    /** Receives a resolved display name/avatar pair; either may be null. */
    private interface ProfileCallback {
        void onResolved(@Nullable String name, @Nullable String avatar);
    }

    private static boolean isEmpty(@Nullable String s) {
        return s == null || s.trim().isEmpty() || "none".equalsIgnoreCase(s.trim());
    }

    /** Looks up a profile's username and avatar and always calls back, once. */
    private void resolveProfile(@Nullable String profileId, @NonNull ProfileCallback callback) {
        if (isEmpty(profileId)) {
            callback.onResolved(null, null);
            return;
        }
        Map<String, String> q = new HashMap<>();
        q.put("id", "eq." + profileId);
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
                callback.onResolved(name, avatar);
            }

            @Override
            public void onFailure(@NonNull Call<List<com.insta.nova.data.remote.dto.ProfileDto>> call,
                                  @NonNull Throwable t) {
                callback.onResolved(null, null);
            }
        });
    }

    /** Resolves the caller's display name/avatar for the incoming UI. */
    private void fetchCallerIdentity(@NonNull CallRecord record) {
        if (record.callerId == null) {
            notifyIncoming(record, null, null);
            launchIncomingUi(record, null, null);
            return;
        }
        resolveProfile(record.callerId, (name, avatar) -> {
            if (name != null) peerName = name;
            if (avatar != null) peerAvatar = avatar;
            notifyIncoming(record, name, avatar);
            launchIncomingUi(record, name, avatar);
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
                        CallRingtone.get().stopFor(fresh.id);
                        CallNotificationHelper.cancelAllCallNotifications(context);
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


    /** True when one of this app's processes is actually on screen. */
    private boolean isAppVisible() {
        try {
            android.app.ActivityManager am = (android.app.ActivityManager)
                    context.getSystemService(android.content.Context.ACTIVITY_SERVICE);
            if (am == null) return false;
            java.util.List<android.app.ActivityManager.RunningAppProcessInfo> procs =
                    am.getRunningAppProcesses();
            if (procs == null) return false;
            String pkg = context.getPackageName();
            for (android.app.ActivityManager.RunningAppProcessInfo p : procs) {
                if (p.processName == null || !p.processName.startsWith(pkg)) continue;
                return p.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                        || p.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE;
            }
        } catch (Exception e) {
            Log.w(TAG, "isAppVisible failed: " + e.getMessage());
        }
        return false;
    }

    private void notifyIncoming(CallRecord r, String name, String avatar) {
        // Post the system card here, unconditionally. launchIncomingUi() only falls back
        // to a notification when startActivity() throws, but Android 10+ does not throw
        // for a background activity start - it accepts the call and drops the activity
        // later. That left a callee sitting on the home screen hearing the ringtone with
        // no card and no way to answer. The card is what makes the call reachable; the
        // full-screen intent still raises the screen whenever the platform allows it.
        // Only when the app is not visible. In the foreground the incoming screen is the
        // announcement, and posting a card as well produced two competing call UIs for the
        // same call.
        if (!isAppVisible()) {
            try {
                CallNotificationHelper.showIncomingCallNotification(context, r, name, avatar);
            } catch (Exception e) {
                Log.w(TAG, "incoming call notification failed: " + e.getMessage());
            }
        }
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

    /**
     * Re-opens the incoming screen for a call that rang while the app was in the
     * background.
     *
     * <p>Android 10+ refuses a background activity start, so a call that arrives while
     * the app is behind the launcher can only post a notification. If the user then
     * opens the app from its icon rather than from that notification, nothing brought
     * the screen back: {@link #handleRealtimeRow} only surfaces
     * {@code STATUS_CALLING}, and by that point this device has acknowledged the row and
     * it reads {@code ringing}. The call was then unanswerable until it timed out.
     *
     * <p>Called from the main screen's {@code onStart}. Deliberately narrow: only an
     * incoming call this device has not answered, only while the app is visible, and
     * only when no incoming screen is already showing.
     */
    public void onAppForegrounded() {
        CallRecord c = activeCall;
        if (c == null || c.id == null) return;
        if (activeIsOutgoing) return;
        if (CallRecord.isTerminal(c.status)) return;
        if (CallRecord.STATUS_CONNECTED.equals(c.status)) return;
        if (!CallRecord.STATUS_RINGING.equals(c.status)
                && !CallRecord.STATUS_CALLING.equals(c.status)) return;
        if (CallActivityBridge.isIncomingShownFor(c.id)) return;
        if (!isAppVisible()) return;
        Log.i(TAG, "app foregrounded with live incoming " + c.id + " (" + c.status + ")");
        launchIncomingUi(c, peerName, peerAvatar);
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

        // Prompt Q: the same preference gate as the FCM path, so a Realtime
        // delivery cannot bypass "Who can call me" or the call switch.
        com.insta.nova.data.local.AppSettingsStore callSettings =
                new com.insta.nova.data.local.AppSettingsStore(context);
        if (!callSettings.allowsNotificationType("call")) {
            Log.i(TAG, "call notifications off; ignoring incoming " + session.callId);
            return;
        }
        if (session.callerId != null
                && new com.insta.nova.data.local.RelationshipStore(context)
                .isBlocked(session.callerId)) {
            Log.i(TAG, "blocked caller; ignoring incoming " + session.callId);
            return;
        }
        if (com.insta.nova.data.local.AppSettingsStore.WHO_NOBODY.equals(
                callSettings.whoCanCall())) {
            Log.i(TAG, "who_can_call=nobody; ignoring incoming " + session.callId);
            return;
        }

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
        // A push can arrive with the process cold and the screen blocked; the ring must
        // not depend on the activity winning that race.
        CallRingtone.get().start(context, session.callId);
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