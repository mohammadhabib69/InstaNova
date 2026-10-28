package com.insta.nova.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.insta.nova.data.firebase.CrashReporter;
import com.insta.nova.data.firebase.FirebaseData;

import java.util.HashMap;
import java.util.Map;

/**
 * Call signalling on Realtime Database (tasks P8-03..P8-07, P8-09).
 *
 * <p>Canonical state machine (P8-02): {@code ringing → accepted → ended}, with
 * {@code rejected}, {@code cancelled} and {@code missed} as terminal branches. The legacy
 * {@code call_sessions}/{@code call_signals} polling stack is not used here and retires in
 * P8-11 once this passes device testing.
 *
 * <p>Shape:
 * <pre>
 * calls/{callId}          = { state, callerUid, calleeUid, createdAt, endedAt? , type }
 * callSignals/{callId}/{uid}/{signalId} = { type, payload, createdAt }
 * </pre>
 *
 * <p>Signals sit at top level rather than under the call because RTDB write rules cascade:
 * a participant allowed to write the call node could otherwise write the other side's
 * signal slot (proven by a failing test, see MIGRATION_LOG).
 *
 * <p>Media stays in WebRTC — this class only carries the handshake and the state.
 */
public class RealtimeCallRepository {

    public static final String STATE_RINGING = "ringing";
    public static final String STATE_ACCEPTED = "accepted";
    public static final String STATE_REJECTED = "rejected";
    public static final String STATE_CANCELLED = "cancelled";
    public static final String STATE_ENDED = "ended";
    public static final String STATE_MISSED = "missed";

    /** Ring timeout before the caller's app marks the call missed (P8-06). */
    public static final long RING_TIMEOUT_MS = 45_000L;

    private final FirebaseDatabase db;

    public RealtimeCallRepository() {
        this.db = FirebaseData.database();
    }

    public void createCall(@NonNull String callId, @NonNull String callerUid,
                           @NonNull String calleeUid, @NonNull String type,
                           @Nullable Callback callback) {
        Map<String, Object> call = new HashMap<>();
        call.put("state", STATE_RINGING);
        call.put("callerUid", callerUid);
        call.put("calleeUid", calleeUid);
        call.put("type", type);
        call.put("createdAt", ServerValue.TIMESTAMP);

        db.getReference("calls").child(callId).setValue(call, (error, ref) -> finish(callback, error, "call_create"));
    }

    public void accept(@NonNull String callId, @Nullable Callback callback) {
        transition(callId, STATE_ACCEPTED, null, callback, "call_accept");
    }

    public void reject(@NonNull String callId, @Nullable Callback callback) {
        transition(callId, STATE_REJECTED, null, callback, "call_reject");
    }

    public void cancel(@NonNull String callId, @Nullable Callback callback) {
        transition(callId, STATE_CANCELLED, null, callback, "call_cancel");
    }

    public void end(@NonNull String callId, @Nullable Callback callback) {
        Map<String, Object> extra = new HashMap<>();
        extra.put("endedAt", ServerValue.TIMESTAMP);
        transition(callId, STATE_ENDED, extra, callback, "call_end");
    }

    /** Called by the caller's app when {@link #RING_TIMEOUT_MS} elapses with no answer. */
    public void markMissed(@NonNull String callId, @Nullable Callback callback) {
        transition(callId, STATE_MISSED, null, callback, "call_missed");
    }

    private void transition(@NonNull String callId, @NonNull String state,
                            @Nullable Map<String, Object> extra, @Nullable Callback callback,
                            @NonNull String context) {
        Map<String, Object> patch = new HashMap<>();
        patch.put("state", state);
        if (extra != null) {
            patch.putAll(extra);
        }
        db.getReference("calls").child(callId).updateChildren(patch, (error, ref) -> finish(callback, error, context));
    }

    /** Live call document; the UI reacts to state changes (incoming, accepted, ended). */
    public void listenCall(@NonNull String callId, @NonNull ValueEventListener listener) {
        db.getReference("calls").child(callId).addValueEventListener(listener);
    }

    public void stopListening(@NonNull String callId, @NonNull ValueEventListener listener) {
        db.getReference("calls").child(callId).removeEventListener(listener);
    }

    /** WebRTC offer / answer / ICE — written only into the caller's own slot. */
    public void sendSignal(@NonNull String callId, @NonNull String uid, @NonNull String type,
                           @NonNull String payload) {
        Map<String, Object> signal = new HashMap<>();
        signal.put("type", type);
        signal.put("payload", payload);
        signal.put("createdAt", ServerValue.TIMESTAMP);
        db.getReference("callSignals").child(callId).child(uid).push()
                .setValue(signal, (error, ref) -> {
                    if (error != null) {
                        CrashReporter.record(error.toException(), "call_signal");
                    }
                });
    }

    public void listenSignals(@NonNull String callId, @NonNull String remoteUid,
                              @NonNull ValueEventListener listener) {
        db.getReference("callSignals").child(callId).child(remoteUid).addValueEventListener(listener);
    }

    /** Read once to decide whether an incoming push still corresponds to a live call. */
    public void loadOnce(@NonNull String callId, @NonNull ValueEventListener listener) {
        db.getReference("calls").child(callId).addListenerForSingleValueEvent(listener);
    }

    private static void finish(@Nullable Callback callback,
                               @Nullable com.google.firebase.database.DatabaseError error,
                               @NonNull String context) {
        if (callback == null) {
            return;
        }
        if (error == null) {
            callback.onSuccess();
        } else {
            CrashReporter.record(error.toException(), context);
            callback.onError(error.getMessage());
        }
    }

    /** Minimal callback so Firebase types do not leak into the calling screens. */
    public interface Callback {
        void onSuccess();

        void onError(String message);
    }

    /** Convenience type for callers that only read a snapshot. */
    public interface SnapshotReader {
        void onData(@Nullable DataSnapshot snapshot);
    }
}
