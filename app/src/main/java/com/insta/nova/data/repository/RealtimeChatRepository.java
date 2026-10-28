package com.insta.nova.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.insta.nova.data.firebase.CrashReporter;
import com.insta.nova.data.firebase.FirebaseData;

import java.util.HashMap;
import java.util.Map;

/**
 * Chat transport on Realtime Database (Phase 6).
 *
 * <p>Named {@code RealtimeChatRepository}: {@code ChatRepository} is the live Supabase
 * implementation.
 *
 * <p>Shape (matches database.rules.json):
 * <pre>
 * chats/{cid}/members/{uid} = true
 * chats/{cid}/messages/{mid} = { senderUid, text, type, mediaUrl?, createdAt }
 * presence/{uid} = { state, lastChanged }
 * typing/{cid}/{uid} = true
 * </pre>
 *
 * <p>RTDB rather than Firestore because a thread is a hot, ordered list: RTDB pushes are
 * ordered by server timestamp and cost one socket, not one read per message.
 */
public class RealtimeChatRepository {

    public static final String TYPE_TEXT = "text";
    public static final String TYPE_IMAGE = "image";
    public static final String STATE_ONLINE = "online";
    public static final String STATE_OFFLINE = "offline";

    private final FirebaseDatabase db;

    public RealtimeChatRepository() {
        this.db = FirebaseData.database();
    }

    private DatabaseReference messages(@NonNull String conversationId) {
        return db.getReference("chats").child(conversationId).child("messages");
    }

    /** Appends a message; the server stamps `createdAt` so ordering cannot be spoofed. */
    public void sendMessage(@NonNull String conversationId, @NonNull String senderUid,
                            @NonNull String text, @NonNull String type,
                            @Nullable String mediaUrl,
                            @Nullable Callback callback) {
        Map<String, Object> message = new HashMap<>();
        message.put("senderUid", senderUid);
        message.put("type", type);
        message.put("text", text);
        if (mediaUrl != null && !mediaUrl.isEmpty()) {
            message.put("mediaUrl", mediaUrl);
        }
        message.put("createdAt", ServerValue.TIMESTAMP);

        messages(conversationId).push().setValue(message, (error, ref) -> {
            if (callback == null) {
                return;
            }
            if (error == null) {
                callback.onSuccess();
            } else {
                CrashReporter.record(error.toException(), "chat_send");
                callback.onError(error.getMessage());
            }
        });
    }

    /** Live thread listener. Detach with {@link #stopListening(ValueEventListener, String)}. */
    public void listenMessages(@NonNull String conversationId, @NonNull ValueEventListener listener) {
        messages(conversationId).limitToLast(100).addValueEventListener(listener);
    }

    public void stopListening(@NonNull String conversationId, @NonNull ValueEventListener listener) {
        messages(conversationId).removeEventListener(listener);
    }

    /**
     * Presence with an onDisconnect safety net: if the app is killed, the server flips the
     * state to offline instead of leaving a ghost "online".
     */
    public void goOnline(@NonNull String uid) {
        DatabaseReference presence = db.getReference("presence").child(uid);
        presence.onDisconnect().setValue(offline()).addOnFailureListener(
                e -> CrashReporter.record(e, "presence_on_disconnect"));
        Map<String, Object> online = new HashMap<>();
        online.put("state", STATE_ONLINE);
        online.put("lastChanged", ServerValue.TIMESTAMP);
        presence.setValue(online, (error, ref) -> {
            if (error != null) {
                CrashReporter.record(error.toException(), "presence_online");
            }
        });
    }

    public void goOffline(@NonNull String uid) {
        db.getReference("presence").child(uid).setValue(offline(), (error, ref) -> {
            if (error != null) {
                CrashReporter.record(error.toException(), "presence_offline");
            }
        });
    }

    /** Live presence of one user: {@code state} plus {@code lastChanged}. */
    public void listenPresence(@NonNull String uid, @NonNull ValueEventListener listener) {
        db.getReference("presence").child(uid).addValueEventListener(listener);
    }

    public void stopListeningPresence(@NonNull String uid, @NonNull ValueEventListener listener) {
        db.getReference("presence").child(uid).removeEventListener(listener);
    }

    /**
     * Live typing flags for a thread, keyed by uid.
     *
     * <p>Reading this node needs chat membership, so a brand-new conversation shows nothing until
     * {@link #addMember} has run — which is also what the write rule requires.
     */
    public void listenTyping(@NonNull String conversationId, @NonNull ValueEventListener listener) {
        db.getReference("typing").child(conversationId).addValueEventListener(listener);
    }

    public void stopListeningTyping(@NonNull String conversationId,
                                    @NonNull ValueEventListener listener) {
        db.getReference("typing").child(conversationId).removeEventListener(listener);
    }

    /** Typing indicators expire by themselves: the flag is cleared on stop or timeout. */
    /**
     * Typing flag of one member.
     *
     * <p>{@code typing/$cid/$uid} is where the rules put the read permission; the parent
     * {@code typing/$cid} has none, so the whole-node listener never delivered anything.
     */
    public void listenTypingFor(@NonNull String conversationId, @NonNull String uid,
                                @NonNull ValueEventListener listener) {
        db.getReference("typing").child(conversationId).child(uid).addValueEventListener(listener);
    }

    public void stopListeningTypingFor(@NonNull String conversationId, @NonNull String uid,
                                       @NonNull ValueEventListener listener) {
        db.getReference("typing").child(conversationId).child(uid).removeEventListener(listener);
    }

    public void setTyping(@NonNull String conversationId, @NonNull String uid, boolean typing) {
        DatabaseReference flag = db.getReference("typing").child(conversationId).child(uid);
        if (typing) {
            flag.setValue(true);
            flag.onDisconnect().removeValue();
        } else {
            flag.removeValue();
        }
    }

    /** Marks the thread read up to now (task P6-05). One marker per member, self-write only. */
    public void markRead(@NonNull String conversationId, @NonNull String uid) {
        Map<String, Object> marker = new HashMap<>();
        marker.put("lastReadAt", ServerValue.TIMESTAMP);
        db.getReference("chats").child(conversationId).child("reads").child(uid)
                .setValue(marker, (error, ref) -> {
                    if (error != null) {
                        CrashReporter.record(error.toException(), "chat_mark_read");
                    }
                });
    }

    /**
     * Unread count for a thread (task P6-06), computed from the loaded page.
     *
     * <p>Client-side on purpose: RTDB has no aggregate queries, and a per-message unread
     * node per member would multiply writes by member count. The page is capped at 100
     * messages, so this is a cheap scan over data we already have.
     */
    public static int unreadCount(@Nullable DataSnapshot messages, long lastReadAt) {
        if (messages == null || !messages.exists()) {
            return 0;
        }
        int unread = 0;
        for (DataSnapshot message : messages.getChildren()) {
            Long createdAt = message.child("createdAt").getValue(Long.class);
            String sender = message.child("senderUid").getValue(String.class);
            if (createdAt != null && createdAt > lastReadAt) {
                unread += 1;
            }
        }
        return unread;
    }

    /** Sets or clears the caller's reaction on a message (task P6-07). */
    public void setReaction(@NonNull String conversationId, @NonNull String messageId,
                            @NonNull String uid, @Nullable String emoji) {
        // Reactions live beside the messages, not under them: RTDB write rules cascade
        // downward, so a message-level write would implicitly permit writing reactions.
        DatabaseReference reaction = db.getReference("chats").child(conversationId)
                .child("reactions").child(messageId).child(uid);
        if (emoji == null || emoji.isEmpty()) {
            reaction.removeValue();
        } else {
            reaction.setValue(emoji);
        }
    }

    /** Membership write is required before any message can be sent (see rules). */
    public void addMember(@NonNull String conversationId, @NonNull String uid) {
        db.getReference("chats").child(conversationId).child("members").child(uid).setValue(true);
    }

    public void ensureConversation(@NonNull String conversationId, @NonNull String... members) {
        DatabaseReference chat = db.getReference("chats").child(conversationId);
        Map<String, Object> created = new HashMap<>();
        created.put("createdAt", ServerValue.TIMESTAMP);
        chat.child("meta").setValue(created);
        for (String uid : members) {
            chat.child("members").child(uid).setValue(true);
        }
    }

    /** One-shot read used by tests and by the "jump to message" path. */
    public void loadOnce(@NonNull String conversationId, @NonNull ValueEventListener listener) {
        messages(conversationId).limitToLast(100)
                .addListenerForSingleValueEvent(listener);
    }

    private static Map<String, Object> offline() {
        Map<String, Object> offline = new HashMap<>();
        offline.put("state", STATE_OFFLINE);
        offline.put("lastChanged", ServerValue.TIMESTAMP);
        return offline;
    }

    /** Minimal callback so this layer does not leak Firebase types into callers. */
    public interface Callback {
        void onSuccess();

        void onError(String message);
    }

    /** Convenience for callers that only need the raw snapshot. */
    public interface OnSnapshot {
        void onData(@Nullable DataSnapshot snapshot);

        void onCancelled(@NonNull DatabaseError error);
    }
}
