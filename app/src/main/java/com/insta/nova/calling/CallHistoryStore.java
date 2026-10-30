package com.insta.nova.calling;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Local cache of call history, persisted as JSON in SharedPreferences.
 *
 * <h3>Status vocabulary</h3>
 * The display vocabulary is intentionally smaller than the {@code calls} row vocabulary,
 * because a history line answers one question: "what happened to this call, from my side?"
 *
 * <pre>
 *   answered    connected and talked (duration may be short but non-zero)
 *   missed      rang me and I never answered            (callee only)
 *   no_answer   I called and they never answered         (caller only)
 *   declined    the callee pressed decline
 *   cancelled   I hung up before it connected
 *   unknown     a row the mapper does not recognise
 * </pre>
 *
 * <p>Every row used to fall through to {@code "failed"} because the mapper only knew the
 * legacy {@code call_sessions} statuses ({@code accepted}, {@code rejected}, …) while the
 * authoritative {@code calls} table writes {@code connected}, {@code ended},
 * {@code declined}, {@code missed} and {@code no_answer}. A call that connected and lasted
 * ten minutes was therefore stored as "Failed". {@link #displayStatusFor} is that missing
 * mapping, and it is the only place that decides.
 */
public class CallHistoryStore {

    /**
     * True for a value PostgREST can use as {@code id=eq.<uuid>}.
     *
     * <p>History rows are written from two places: the authoritative {@code calls} table
     * (real UUIDs) and the legacy local session objects, which stored the literal string
     * "other" when the peer id was unknown. Looking up "other" as a uuid returns 400, so
     * those entries could never be named and fell back to the generic label forever.
     */
    private static boolean isUuid(@Nullable String value) {
        return value != null && value.matches(
                "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    }

    private static final String PREF_NAME = "insta_call_history";
    private static final String KEY_CALL_LOGS = "call_logs";
    private static final int MAX_RECORDS = 200;

    private static CallHistoryStore instance;

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    public static class CallRecord {
        public String callId;
        public String conversationId;
        public String otherUserId;
        public String otherUsername;
        public String otherAvatar;
        public String callType; // "audio" | "video"
        public String status;   // answered | missed | no_answer | declined | cancelled | unknown
        public boolean isOutgoing;
        public long timestamp;
        public int durationSeconds;

        public CallRecord() {}
    }

    public static synchronized CallHistoryStore getInstance(@NonNull Context context) {
        if (instance == null) {
            instance = new CallHistoryStore(context.getApplicationContext());
        }
        return instance;
    }

    /**
     * Bumped whenever the rows already on disk can no longer be trusted.
     *
     * <p>Version 1 is every build up to the CallRecord mapping fix: those rows were
     * stored from a response whose caller/callee/created_at/duration_sec never
     * decoded, so they carry no peer, a refresh-time timestamp and a zero duration.
     * They cannot be repaired in place because nothing on the row says it is wrong,
     * so the cache is dropped once and rebuilt from {@code calls}, which is the
     * authoritative source the screen fetches on open anyway.
     */
    private static final String KEY_SCHEMA = "schema_version";
    private static final int SCHEMA_VERSION = 2;

    private CallHistoryStore(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int stored = prefs.getInt(KEY_SCHEMA, 1);
        if (stored < SCHEMA_VERSION) {
            prefs.edit().remove(KEY_CALL_LOGS).putInt(KEY_SCHEMA, SCHEMA_VERSION).apply();
        }
    }

    /* ------------------------------------------------------------------ */
    /* Status mapping                                                      */
    /* ------------------------------------------------------------------ */

    /**
     * Maps a {@code public.calls} row to what this device should show.
     *
     * @param outgoing true when this device placed the call
     */
    @NonNull
    public static String displayStatusFor(@Nullable com.insta.nova.calling.CallRecord row, boolean outgoing) {
        if (row == null) return "unknown";
        String status = row.status == null ? "" : row.status;
        if ("declined".equals(status)) return "declined";
        if ("connected".equals(status)) return "answered";

        // One rule, as asked for: did the call ever connect?
        //   connected  -> "answered" (the adapter then shows Outgoing or Incoming by side)
        //   never      -> "missed", on both sides
        // The previous version also returned cancelled/no_answer/unknown, which is how a
        // plain unanswered call could end up labelled several different ways depending on
        // which side hung up first.
        boolean connected = row.durationSec > 0
                || (row.connectedAt != null && !row.connectedAt.isEmpty());
        return connected ? "answered" : "missed";
    }

    /* ------------------------------------------------------------------ */
    /* Writes                                                              */
    /* ------------------------------------------------------------------ */

    /**
     * Inserts or updates the entry for one {@code calls} row. Idempotent by call id, so a
     * refresh, a pull-to-refresh and a live update can all call it.
     *
     * @param row    the authoritative call row
     * @param myId   this device's profile id, used to decide direction and peer
     */
    public synchronized void upsertCall(@NonNull com.insta.nova.calling.CallRecord row, @Nullable String myId) {
        if (row.id == null || row.id.isEmpty()) return;

        boolean outgoing = myId != null && myId.equals(row.callerId);
        String peerId = outgoing ? row.calleeId : row.callerId;
        if (!isUuid(peerId)) peerId = null;
        String status = displayStatusFor(row, outgoing);
        long timestamp = row.createdAtMillis();
        if (timestamp <= 0) timestamp = System.currentTimeMillis();

        List<CallRecord> records = getCalls();
        for (CallRecord existing : records) {
            if (row.id.equals(existing.callId)) {
                existing.status = status;
                existing.durationSeconds = row.durationSec;
                existing.callType = row.callType != null ? row.callType : existing.callType;
                existing.isOutgoing = outgoing;
                existing.timestamp = timestamp;
                if (peerId != null) existing.otherUserId = peerId;
                saveCalls(trim(records));
                return;
            }
        }

        CallRecord record = new CallRecord();
        record.callId = row.id;
        record.otherUserId = peerId;
        // Inherit the identity we already learned for this peer. Without it every new call
        // was inserted blank and only the lazy peer lookup could fill it in - and that lookup
        // is skipped while offline, so a fresh row could read "Unknown" indefinitely.
        if (peerId != null) {
            for (CallRecord sibling : records) {
                if (peerId.equals(sibling.otherUserId)
                        && sibling.otherUsername != null && !sibling.otherUsername.isEmpty()) {
                    record.otherUsername = sibling.otherUsername;
                    record.otherAvatar = sibling.otherAvatar;
                    break;
                }
            }
        }
        record.callType = row.callType;
        record.status = status;
        record.isOutgoing = outgoing;
        record.timestamp = timestamp;
        record.durationSeconds = row.durationSec;

        records.add(0, record);
        saveCalls(trim(records));
    }

    /** Records the display identity for a peer once it is known. */
    public synchronized void updatePeer(@Nullable String peerId,
                                        @Nullable String username,
                                        @Nullable String avatar) {
        if (peerId == null || peerId.isEmpty()) return;
        if ((username == null || username.isEmpty()) && (avatar == null || avatar.isEmpty())) return;
        List<CallRecord> records = getCalls();
        boolean changed = false;
        for (CallRecord record : records) {
            if (!peerId.equals(record.otherUserId)) continue;
            if (username != null && !username.isEmpty()) {
                record.otherUsername = username;
                changed = true;
            }
            if (avatar != null && !avatar.isEmpty() && !"none".equalsIgnoreCase(avatar)) {
                record.otherAvatar = avatar;
                changed = true;
            }
        }
        if (changed) saveCalls(records);
    }

    /** Legacy path: a {@link CallSession} the app created itself. */
    public synchronized void addCall(@NonNull CallSession session) {
        List<CallRecord> records = getCalls();

        // Check for duplicates
        for (CallRecord r : records) {
            if (session.callId != null && session.callId.equals(r.callId)) {
                r.durationSeconds = session.durationSeconds;
                r.status = mapSessionStatus(session);
                saveCalls(records);
                return;
            }
        }

        CallRecord record = new CallRecord();
        record.callId = session.callId;
        record.conversationId = session.conversationId;
        record.isOutgoing = session.isOutgoing;
        record.otherUserId = session.isOutgoing ? session.recipientId : session.callerId;
        record.otherUsername = session.isOutgoing ? session.recipientUsername : session.callerUsername;
        record.otherAvatar = session.isOutgoing ? session.recipientAvatar : session.callerAvatar;
        record.callType = session.callType;
        record.timestamp = session.startedAtMs > 0 ? session.startedAtMs : System.currentTimeMillis();
        record.durationSeconds = session.durationSeconds;
        record.status = mapSessionStatus(session);

        records.add(0, record); // most recent first
        saveCalls(trim(records));
    }

    /**
     * Legacy {@code call_sessions} statuses. Kept so a session the client created locally
     * still lands on the same display vocabulary as the authoritative rows.
     */
    @NonNull
    private String mapSessionStatus(@NonNull CallSession session) {
        if (session.durationSeconds > 0 || CallSession.STATUS_ACCEPTED.equals(session.status)) {
            return "answered";
        }
        if (CallSession.STATUS_REJECTED.equals(session.status)) {
            return "declined";
        }
        if (CallSession.STATUS_MISSED.equals(session.status)) {
            return session.isOutgoing ? "no_answer" : "missed";
        }
        if (CallSession.STATUS_CANCELLED.equals(session.status)) {
            return session.isOutgoing ? "cancelled" : "missed";
        }
        if (CallSession.STATUS_ENDED.equals(session.status)) {
            return session.isOutgoing ? "cancelled" : "missed";
        }
        // Never claim a failure we cannot name: an unfinished record is a cancelled call.
        return session.isOutgoing ? "cancelled" : "unknown";
    }

    /** Reads the cache, dropping peer ids that can never resolve to a profile. */
    public synchronized List<CallRecord> getCalls() {
        String json = prefs.getString(KEY_CALL_LOGS, null);
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            Type type = new TypeToken<List<CallRecord>>() {}.getType();
            List<CallRecord> list = gson.fromJson(json, type);
            if (list == null) return new ArrayList<>();
            for (CallRecord r : list) {
                if (r.otherUserId != null && !isUuid(r.otherUserId)) r.otherUserId = null;
            }
            return list;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * Newest first, by the call's own time.
     *
     * <p>Rows used to be prepended and never sorted, which only looked correct while
     * every timestamp was equal: the screen fetches {@code created_at.desc} and each
     * insert went to index 0, so a full refresh inverted the whole list and the oldest
     * call sat at the top.
     */
    private List<CallRecord> trim(List<CallRecord> records) {
        java.util.Collections.sort(records, new java.util.Comparator<CallRecord>() {
            @Override
            public int compare(CallRecord a, CallRecord b) {
                return Long.compare(b.timestamp, a.timestamp);
            }
        });
        if (records.size() <= MAX_RECORDS) return records;
        return new ArrayList<>(records.subList(0, MAX_RECORDS));
    }

    private synchronized void saveCalls(List<CallRecord> records) {
        prefs.edit().putString(KEY_CALL_LOGS, gson.toJson(records)).apply();
    }
}
