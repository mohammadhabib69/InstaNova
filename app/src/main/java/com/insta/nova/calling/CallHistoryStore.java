package com.insta.nova.calling;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Persists call history records locally (SharedPreferences + JSON)
 * ensuring call records survive application relaunches.
 */
public class CallHistoryStore {

    private static final String PREF_NAME = "insta_call_history";
    private static final String KEY_CALL_LOGS = "call_logs";
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
        public String status;   // "answered", "missed", "cancelled", "rejected", "failed"
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

    private CallHistoryStore(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public synchronized void addCall(@NonNull CallSession session) {
        List<CallRecord> records = getCalls();

        // Check for duplicates
        for (CallRecord r : records) {
            if (session.callId != null && session.callId.equals(r.callId)) {
                r.durationSeconds = session.durationSeconds;
                r.status = mapStatus(session);
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
        record.status = mapStatus(session);

        records.add(0, record); // most recent first
        // Limit to 200 records
        if (records.size() > 200) {
            records = records.subList(0, 200);
        }
        saveCalls(records);
    }

    private String mapStatus(CallSession session) {
        if (session.durationSeconds > 0 || CallSession.STATUS_ACCEPTED.equals(session.status)) {
            return "answered";
        } else if (CallSession.STATUS_REJECTED.equals(session.status)) {
            return "rejected";
        } else if (CallSession.STATUS_CANCELLED.equals(session.status)) {
            return session.isOutgoing ? "cancelled" : "missed";
        } else if (CallSession.STATUS_MISSED.equals(session.status)) {
            return "missed";
        } else {
            return "failed";
        }
    }

    public synchronized List<CallRecord> getCalls() {
        String json = prefs.getString(KEY_CALL_LOGS, null);
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            Type type = new TypeToken<List<CallRecord>>() {}.getType();
            List<CallRecord> list = gson.fromJson(json, type);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private synchronized void saveCalls(List<CallRecord> records) {
        prefs.edit().putString(KEY_CALL_LOGS, gson.toJson(records)).apply();
    }
}
