package com.insta.nova.calling;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

/**
 * One row of {@code public.calls} — the single authoritative record of a call.
 *
 * <p>The status vocabulary is the Prompt J state machine and is constrained by
 * a CHECK in the schema, so these constants and the database cannot drift:
 *
 * <pre>
 *   calling    caller inserted the row; callee has not acknowledged yet
 *   ringing    callee device displayed the incoming call (the ack)
 *   connected  callee answered; connected_at is the shared timer origin
 *   ended      somebody hung up
 *   declined   callee explicitly declined
 *   missed     rang for 30s with no answer
 *   no_answer  never acked within 45s of creation
 * </pre>
 *
 * <p>Timestamp fields are kept as the raw ISO-8601 strings Supabase returns.
 * They are parsed lazily because the call screens only need {@code connected_at},
 * and parsing every row on every Realtime event would be wasted work.
 */
public class CallRecord {

    public static final String STATUS_CALLING = "calling";
    public static final String STATUS_RINGING = "ringing";
    public static final String STATUS_CONNECTED = "connected";
    public static final String STATUS_ENDED = "ended";
    public static final String STATUS_DECLINED = "declined";
    public static final String STATUS_MISSED = "missed";
    public static final String STATUS_NO_ANSWER = "no_answer";

    /** Statuses after which no screen may remain open on either device. */
    public static boolean isTerminal(@Nullable String status) {
        return STATUS_ENDED.equals(status)
                || STATUS_DECLINED.equals(status)
                || STATUS_MISSED.equals(status)
                || STATUS_NO_ANSWER.equals(status);
    }

    /** True once the callee's device has acknowledged the call. */
    public static boolean isAcked(@Nullable String status) {
        return STATUS_RINGING.equals(status)
                || STATUS_CONNECTED.equals(status)
                || isTerminal(status);
    }

    public String id;
    public String callerId;
    public String calleeId;
    public String callType;
    public String status;
    public String createdAt;
    public String ringingAt;
    public String connectedAt;
    public String endedAt;
    public String endedBy;
    public int durationSec;
    public String calleeSurfacedAt;

    public CallRecord() {
    }

    /**
     * Parses a Realtime {@code data.record} object or a PostgREST row body.
     *
     * <p>PostgREST {@code *} returns typed JSON, whereas Realtime postgres_changes
     * returns every column as a JSON string, so {@code duration_sec} arrives as
     * {@code "12"} on one path and {@code 12} on the other. {@link #asInt}
     * accepts both.
     */
    public static CallRecord fromJson(@Nullable JSONObject o) {
        CallRecord r = new CallRecord();
        if (o == null) return r;
        r.id = optString(o, "id");
        r.callerId = optString(o, "caller_id");
        r.calleeId = optString(o, "callee_id");
        r.callType = optString(o, "call_type");
        r.status = optString(o, "status");
        r.createdAt = optString(o, "created_at");
        r.ringingAt = optString(o, "ringing_at");
        r.connectedAt = optString(o, "connected_at");
        r.endedAt = optString(o, "ended_at");
        r.endedBy = optString(o, "ended_by");
        r.durationSec = asInt(o.opt("duration_sec"));
        r.calleeSurfacedAt = optString(o, "callee_surfaced_at");
        return r;
    }

    private static String optString(@NonNull JSONObject o, @NonNull String key) {
        if (o.isNull(key)) return null;
        String v = o.optString(key, null);
        return (v == null || v.isEmpty() || "null".equals(v)) ? null : v;
    }

    private static int asInt(@Nullable Object v) {
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) {
            try {
                return (int) Double.parseDouble(((String) v).trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    public boolean isVideo() {
        return "video".equals(callType);
    }

    /**
     * Epoch millis of {@code connected_at}, or 0 when the call never connected.
     *
     * <p>Both devices derive the call timer from this single timestamp rather
     * than from their own "answer pressed" moment, which is what keeps the two
     * timers in sync even when one clock lags or a frame is dropped.
     */
    public long connectedAtMillis() {
        return parseIsoMillis(connectedAt);
    }

    /** Epoch millis of {@code created_at}, used for the caller's 45s budget. */
    public long createdAtMillis() {
        return parseIsoMillis(createdAt);
    }

    /**
     * Patterns covering the timestamptz forms PostgREST and Realtime emit:
     * {@code 2026-10-22T01:23:45.678+00:00}, {@code ...+00}, and a bare
     * {@code 2026-10-22T01:23:45.678} when the session timezone is UTC.
     *
     * <p>{@code java.time} is deliberately avoided: the app's minSdk is 24 and
     * {@code java.time} only exists from API 26 without core-library
     * desugaring, which this project does not enable. The {@code X} offset
     * pattern letters used here landed in API 24.
     */
    private static final String[] ISO_PATTERNS = {
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss",
    };

    private static long parseIsoMillis(@Nullable String iso) {
        if (iso == null || iso.isEmpty()) return 0L;
        String v = iso.trim();
        // SimpleDateFormat's XXX does not accept the military "Z" suffix.
        if (v.endsWith("Z")) {
            v = v.substring(0, v.length() - 1) + "+00:00";
        }
        for (String pattern : ISO_PATTERNS) {
            try {
                return new java.text.SimpleDateFormat(pattern, java.util.Locale.US)
                        .parse(v).getTime();
            } catch (Exception ignored) {
                // Try the next shape.
            }
        }
        return 0L;
    }

    @NonNull
    @Override
    public String toString() {
        return "CallRecord{" + id + " " + callType + " " + status
                + " caller=" + callerId + " callee=" + calleeId + "}";
    }
}