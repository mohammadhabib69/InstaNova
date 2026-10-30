package com.insta.nova.calling;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonElement;
import com.insta.nova.data.local.ChatSettingsStore;
import com.insta.nova.data.local.LocalChatStore;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.remote.RetrofitClient;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Part 5 — derives the chat thread's call entry from the {@code calls} row.
 *
 * <p>Both devices run this against the <em>same</em> terminal row, which is why
 * they show the same entry without any extra sync: the call id is used as the
 * message id, so re-running it is idempotent and a duplicate Realtime frame or
 * a reconnect re-read cannot produce two entries for one call.
 *
 * <p>The conversation is resolved with {@code get_or_create_conversation} rather
 * than stored on the call row, because Prompt J's {@code calls} schema has no
 * {@code conversation_id} column and adding one would drift from the spec.
 */
public final class CallThreadEntries {

    private static final String TAG = "CallThreadEntries";

    /** call_ids already written to the thread in this process. */
    private static final Set<String> RECORDED = new HashSet<>();

    private CallThreadEntries() {
    }

    /**
     * Writes (or refreshes) the thread entry for a call that has terminated.
     *
     * @param record    the terminal row
     * @param iAmCallee true when this device was called
     */
    public static void recordTerminatedCall(@NonNull Context context,
                                            @NonNull CallRecord record,
                                            boolean iAmCallee) {
        if (record.id == null || record.calleeId == null || record.callerId == null) return;

        synchronized (RECORDED) {
            if (!RECORDED.add(record.id)) {
                Log.i(TAG, "call " + record.id + " already written to the thread");
                return;
            }
        }

        String myId = SessionManager.getInstance(context).getUserId();
        if (myId == null) return;
        final String peerId = myId.equals(record.callerId) ? record.calleeId : record.callerId;

        // Persist the history entry from the same authoritative row and the same moment
        // as the thread entry, so the call-history screen is correct offline and does not
        // depend on a later refresh happening to succeed.
        CallHistoryStore.getInstance(context).upsertCall(record, myId);

        final Context app = context.getApplicationContext();

        // #65 — publish the entry server-side as well. The local overlay above only
        // reaches this device, and `get_inbox` previews the newest row of `messages`,
        // so a conversation whose latest activity was a call showed the older text
        // message (or nothing). The row is what puts "Audio call · 53s" in the inbox
        // and in the other device's thread. Idempotent on the call id, so both
        // devices calling it — and any retry — converge on one row.
        Map<String, Object> callBody = new HashMap<>();
        callBody.put("p_call_id", record.id);
        RetrofitClient.getInstance(app).getRestApi()
                .recordCallMessage(callBody)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                        if (!response.isSuccessful()) {
                            Log.w(TAG, "thread row rejected: HTTP " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                        Log.w(TAG, "thread row not published: " + t.getMessage());
                    }
                });

        Map<String, Object> body = new HashMap<>();
        body.put("p_other_user_id", peerId);

        RetrofitClient.getInstance(app).getRestApi()
                .getOrCreateConversation(body)
                .enqueue(new Callback<JsonElement>() {
                    @Override
                    public void onResponse(@NonNull Call<JsonElement> call,
                                           @NonNull Response<JsonElement> response) {
                        if (!response.isSuccessful() || response.body() == null) {
                            Log.w(TAG, "conversation lookup failed: HTTP " + response.code());
                            return;
                        }
                        String conversationId;
                        try {
                            conversationId = response.body().getAsString();
                        } catch (Exception e) {
                            Log.w(TAG, "conversation id not a scalar: " + response.body());
                            return;
                        }
                        write(app, record, conversationId, myId, iAmCallee);
                    }

                    @Override
                    public void onFailure(@NonNull Call<JsonElement> call, @NonNull Throwable t) {
                        Log.w(TAG, "conversation lookup failed: " + t.getMessage());
                    }
                });
    }

    private static void write(@NonNull Context app, @NonNull CallRecord record,
                              @NonNull String conversationId, @NonNull String myId,
                              boolean iAmCallee) {
        try {
            boolean isVideo = record.isVideo();
            String kind = isVideo ? "Video call" : "Audio call";

            // The sender must be whoever PLACED the call, not always the local user.
            // ChatAdapter derives `sent` from senderId == myId, so pinning the
            // sender to me made the callee render its own missed call as an
            // outgoing "Missed" instead of the specified "Missed audio call".
            String senderId = iAmCallee ? record.callerId : myId;

            // The call id doubles as the message id: idempotent across
            // reconnect re-reads and across the two devices' own local stores.
            LocalChatStore.ChatMessage cm = new LocalChatStore.ChatMessage(
                    record.id,
                    conversationId,
                    senderId,
                    kind,
                    // The call's own time, not "now": the server row written below is
                    // stamped with ended_at/connected_at, so using the local clock here
                    // made the same call appear at two different positions depending on
                    // whether the thread was showing the overlay or the fetched row.
                    timestampOf(record),
                    true
            );
            cm.messageType = isVideo ? "video_call" : "audio_call";
            new LocalChatStore(app).appendMessage(cm);

            // duration_sec is only populated for connected calls; for the
            // missed/no-answer/declined outcomes the renderer shows the status
            // instead, exactly as Part 5 specifies.
            ChatSettingsStore.getInstance(app).saveCallMeta(
                    record.id,
                    isVideo,
                    Math.max(0, record.durationSec),
                    record.status
            );

            Log.i(TAG, "thread entry written for " + record.id + " (" + record.status
                    + ", callee=" + iAmCallee + ")");
        } catch (Exception e) {
            Log.e(TAG, "failed to write thread entry: " + e.getMessage());
        }
    }

    /** When the call happened, in the same order the server row uses. */
    private static long timestampOf(@NonNull CallRecord record) {
        long ended = record.endedAtMillis();
        if (ended > 0) return ended;
        long connected = record.connectedAtMillis();
        if (connected > 0) return connected;
        long created = record.createdAtMillis();
        return created > 0 ? created : System.currentTimeMillis();
    }

    /**
     * Frees the in-memory guard so a genuinely new call with the same id (only
     * possible across a process restart, where the set is empty anyway) is not
     * suppressed. Exposed for tests.
     */
    public static void resetForTests() {
        synchronized (RECORDED) {
            RECORDED.clear();
        }
    }

    @Nullable
    static String peerOf(@NonNull CallRecord record, @Nullable String myId) {
        if (myId == null) return null;
        if (myId.equals(record.callerId)) return record.calleeId;
        if (myId.equals(record.calleeId)) return record.callerId;
        return null;
    }
}