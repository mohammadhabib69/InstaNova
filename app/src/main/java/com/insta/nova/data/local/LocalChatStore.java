package com.insta.nova.data.local;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Local compatibility store for call-entry metadata and legacy cached messages.
 *
 * <p>Real text messages are stored and read from Supabase. This store is never used
 * to synthesize replies or to replace the server inbox/message stream.
 */
public class LocalChatStore {

    private static final String PREF_NAME = "insta_local_chat";
    private static final int MAX_MESSAGES = 300;
    private static final MutableLiveData<Integer> REVISION = new MutableLiveData<>(0);

    /** One persisted legacy/local message used only for call-entry rendering. */
    public static class ChatMessage {
        public String id;
        public String conversationId;
        public String senderId;
        public String text;
        public long createdAt;
        public boolean read;
        public String mediaUrl;
        public String messageType; // "text", "image", "voice", "audio_call", "video_call"
        public String localVoicePath;
        public long voiceDurationMs;
        public String replyToId;
        public String replyToSender;
        public String replyToText;

        public ChatMessage() {}

        public ChatMessage(String id, String conversationId, String senderId,
                           String text, long createdAt, boolean read) {
            this.id = id;
            this.conversationId = conversationId;
            this.senderId = senderId;
            this.text = text;
            this.createdAt = createdAt;
            this.read = read;
        }
    }

    private final Context appContext;
    private final SharedPreferences prefs;
    private final Gson gson = new Gson();
    private final String account;

    public LocalChatStore(Context context) {
        Context app = context.getApplicationContext();
        this.appContext = app;
        prefs = app.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SessionManager session = SessionManager.getInstance(app);
        String id = session.getUserId();
        account = id == null || id.trim().isEmpty() ? "local" : id;
    }

    public static LiveData<Integer> revision() {
        return REVISION;
    }

    private void bump() {
        Integer v = REVISION.getValue();
        REVISION.setValue(v == null ? 1 : v + 1);
    }

    private String key() {
        return "messages_" + account;
    }

    /** Every locally persisted call-entry message for one conversation, oldest first. */
    public List<ChatMessage> messages(String conversationId) {
        List<ChatMessage> out = new ArrayList<>();
        if (conversationId == null) return out;
        for (ChatMessage m : all()) {
            if (m != null && conversationId.equals(m.conversationId)
                    && (m.id == null || !m.id.startsWith("local-sim-"))
                    && m.messageType != null && !m.messageType.isEmpty()) out.add(m);
        }
        return out;
    }

    /**
     * Legacy no-op retained for source compatibility. Real conversations must
     * never synthesize a local reply; delivery is server-backed.
     */
    @Deprecated
    public void scheduleReply(String conversationId, String senderId, long delayMs) {
        // Intentionally empty: Prompt K removes the single-device fake reply.
    }

    public void appendMessage(ChatMessage message) {
        if (message == null || message.conversationId == null) return;
        List<ChatMessage> all = all();
        all.add(message);
        if (all.size() > MAX_MESSAGES) {
            all = new ArrayList<>(all.subList(all.size() - MAX_MESSAGES, all.size()));
        }
        write(all);
        bump();
    }

    public void removeMessage(String messageId) {
        if (messageId == null) return;
        List<ChatMessage> all = all();
        boolean removed = false;
        for (int i = all.size() - 1; i >= 0; i--) {
            if (messageId.equals(all.get(i).id)) {
                all.remove(i);
                removed = true;
            }
        }
        if (removed) {
            write(all);
            bump();
        }
    }

    /** Number of unread locally persisted entries in one thread. */
    public int unread(String conversationId) {
        if (conversationId == null) return 0;
        if (ChatSettingsStore.getInstance(appContext).isMuted(conversationId)) return 0;
        int count = 0;
        for (ChatMessage m : all()) {
            if (m != null && conversationId.equals(m.conversationId) && !m.read
                    && m.messageType != null && !m.messageType.isEmpty()) count++;
        }
        return count;
    }

    /** Total unread locally persisted entries. */
    public int totalUnread() {
        int count = 0;
        for (ChatMessage m : all()) {
            if (m != null && (m.id == null || !m.id.startsWith("local-sim-")) && !m.read
                    && m.messageType != null && !m.messageType.isEmpty()) {
                if (!ChatSettingsStore.getInstance(appContext).isMuted(m.conversationId)) {
                    count++;
                }
            }
        }
        return count;
    }

    /** Opening a thread clears its badges and persists the read state. */
    public void markRead(String conversationId) {
        if (conversationId == null) return;
        List<ChatMessage> all = all();
        boolean changed = false;
        for (ChatMessage m : all) {
            if (m != null && conversationId.equals(m.conversationId) && !m.read) {
                m.read = true;
                changed = true;
            }
        }
        if (changed) {
            write(all);
            bump();
        }
    }

    /** Timestamp of the newest locally persisted entry, or 0. */
    public long lastAt(String conversationId) {
        long newest = 0L;
        for (ChatMessage m : messages(conversationId)) {
            if (m != null && m.createdAt > newest) newest = m.createdAt;
        }
        return newest;
    }

    /** Preview text of the newest locally persisted entry, or null. */
    public String lastText(String conversationId) {
        ChatMessage newest = null;
        for (ChatMessage m : messages(conversationId)) {
            if (m == null) continue;
            if (newest == null || m.createdAt >= newest.createdAt) newest = m;
        }
        return newest == null ? null : newest.text;
    }

    // ------------------------------------------------------------------
    // Storage
    // ------------------------------------------------------------------

    private List<ChatMessage> all() {
        String json = prefs.getString(key(), null);
        if (json == null) return new ArrayList<>();
        try {
            Type type = new TypeToken<List<ChatMessage>>() {}.getType();
            List<ChatMessage> list = gson.fromJson(json, type);
            return list == null ? new ArrayList<>() : list;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void write(List<ChatMessage> messages) {
        prefs.edit().putString(key(), gson.toJson(messages)).apply();
    }
}
