package com.insta.nova.data.local;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.insta.nova.data.model.MessageReaction;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Persisted local settings for Direct Messaging:
 * themes, muted threads, blocked users, deletions, reactions, and reply references.
 */
public class ChatSettingsStore {

    private static final String PREF_NAME = "insta_chat_settings";
    private static ChatSettingsStore instance;

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    public static synchronized ChatSettingsStore getInstance(Context context) {
        if (instance == null) {
            instance = new ChatSettingsStore(context.getApplicationContext());
        }
        return instance;
    }

    private ChatSettingsStore(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /* ------------------------------------------------------------------ */
    /* Chat Themes                                                         */
    /* ------------------------------------------------------------------ */

    public String getConversationTheme(String conversationId) {
        if (conversationId == null) return "default";
        return prefs.getString("theme_" + conversationId, "default");
    }

    public void setConversationTheme(String conversationId, String themeId) {
        if (conversationId == null) return;
        prefs.edit().putString("theme_" + conversationId, themeId).apply();
    }

    /* ------------------------------------------------------------------ */
    /* Mute State                                                          */
    /* ------------------------------------------------------------------ */

    public boolean isMuted(String conversationId) {
        if (conversationId == null) return false;
        return prefs.getBoolean("mute_" + conversationId, false);
    }

    public void setMuted(String conversationId, boolean muted) {
        if (conversationId == null) return;
        prefs.edit().putBoolean("mute_" + conversationId, muted).apply();
    }

    /* ------------------------------------------------------------------ */
    /* Block State                                                         */
    /* ------------------------------------------------------------------ */

    public boolean isBlocked(String userId) {
        if (userId == null) return false;
        Set<String> set = prefs.getStringSet("blocked_users", new HashSet<>());
        return set.contains(userId);
    }

    public void setBlocked(String userId, boolean blocked) {
        if (userId == null) return;
        Set<String> set = new HashSet<>(prefs.getStringSet("blocked_users", new HashSet<>()));
        if (blocked) {
            set.add(userId);
        } else {
            set.remove(userId);
        }
        prefs.edit().putStringSet("blocked_users", set).apply();
    }

    /* ------------------------------------------------------------------ */
    /* Message Deletions                                                   */
    /* ------------------------------------------------------------------ */

    public boolean isDeleted(String messageId) {
        if (messageId == null) return false;
        Set<String> set = prefs.getStringSet("deleted_messages", new HashSet<>());
        return set.contains(messageId);
    }

    public void markDeleted(String messageId) {
        if (messageId == null) return;
        Set<String> set = new HashSet<>(prefs.getStringSet("deleted_messages", new HashSet<>()));
        set.add(messageId);
        prefs.edit().putStringSet("deleted_messages", set).apply();
    }

    /* ------------------------------------------------------------------ */
    /* Local Reactions                                                     */
    /* ------------------------------------------------------------------ */

    public List<MessageReaction> getReactions(String messageId) {
        if (messageId == null) return new ArrayList<>();
        String json = prefs.getString("reactions_" + messageId, null);
        if (json == null) return new ArrayList<>();
        try {
            Type type = new TypeToken<List<MessageReaction>>() {}.getType();
            List<MessageReaction> list = gson.fromJson(json, type);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public void saveReaction(String messageId, String userId, String emoji) {
        if (messageId == null || userId == null) return;
        List<MessageReaction> current = getReactions(messageId);
        List<MessageReaction> next = new ArrayList<>();
        for (MessageReaction r : current) {
            if (!userId.equals(r.getUserId())) next.add(r);
        }
        if (emoji != null && !emoji.isEmpty()) {
            next.add(new MessageReaction(messageId, userId, emoji));
        }
        prefs.edit().putString("reactions_" + messageId, gson.toJson(next)).apply();
    }

    /* ------------------------------------------------------------------ */
    /* Reply Metadata                                                      */
    /* ------------------------------------------------------------------ */

    public static class ReplyMeta {
        public String replyToId;
        public String replyToSender;
        public String replyToText;

        public ReplyMeta() {}
        public ReplyMeta(String id, String sender, String text) {
            this.replyToId = id;
            this.replyToSender = sender;
            this.replyToText = text;
        }
    }

    public ReplyMeta getReplyMeta(String messageId) {
        if (messageId == null) return null;
        String json = prefs.getString("reply_" + messageId, null);
        if (json == null) return null;
        try {
            return gson.fromJson(json, ReplyMeta.class);
        } catch (Exception e) {
            return null;
        }
    }

    public void saveReplyMeta(String messageId, String replyToId, String replyToSender, String replyToText) {
        if (messageId == null) return;
        ReplyMeta meta = new ReplyMeta(replyToId, replyToSender, replyToText);
        prefs.edit().putString("reply_" + messageId, gson.toJson(meta)).apply();
    }

    /* ------------------------------------------------------------------ */
    /* Voice Note Metadata                                                 */
    /* ------------------------------------------------------------------ */

    public static class VoiceMeta {
        public String localPath;
        public long durationMs;

        public VoiceMeta() {}
        public VoiceMeta(String path, long durationMs) {
            this.localPath = path;
            this.durationMs = durationMs;
        }
    }

    public VoiceMeta getVoiceMeta(String messageId) {
        if (messageId == null) return null;
        String json = prefs.getString("voice_" + messageId, null);
        if (json == null) return null;
        try {
            return gson.fromJson(json, VoiceMeta.class);
        } catch (Exception e) {
            return null;
        }
    }

    public void saveVoiceMeta(String messageId, String localPath, long durationMs) {
        if (messageId == null) return;
        VoiceMeta meta = new VoiceMeta(localPath, durationMs);
        prefs.edit().putString("voice_" + messageId, gson.toJson(meta)).apply();
    }

    /* ------------------------------------------------------------------ */
    /* Call Records                                                        */
    /* ------------------------------------------------------------------ */

    public static class CallMeta {
        public boolean isVideo;
        public long durationSec;
        public long timestamp;

        /**
         * Prompt J: the terminal status of the {@code calls} row this entry was
         * derived from (connected / missed / no_answer / declined / ended).
         *
         * <p>Null on entries written before Prompt J; the renderer treats that
         * as the legacy "call ended" case so historical entries keep rendering.
         */
        public String outcome;

        public CallMeta() {}
        public CallMeta(boolean isVideo, long durationSec, long timestamp) {
            this.isVideo = isVideo;
            this.durationSec = durationSec;
            this.timestamp = timestamp;
        }
        public CallMeta(boolean isVideo, long durationSec, long timestamp, String outcome) {
            this.isVideo = isVideo;
            this.durationSec = durationSec;
            this.timestamp = timestamp;
            this.outcome = outcome;
        }
    }

    public CallMeta getCallMeta(String messageId) {
        if (messageId == null) return null;
        String json = prefs.getString("call_" + messageId, null);
        if (json == null) return null;
        try {
            return gson.fromJson(json, CallMeta.class);
        } catch (Exception e) {
            return null;
        }
    }

    public void saveCallMeta(String messageId, boolean isVideo, long durationSec) {
        saveCallMeta(messageId, isVideo, durationSec, null);
    }

    /** Prompt J overload carrying the terminal status of the originating call. */
    public void saveCallMeta(String messageId, boolean isVideo, long durationSec, String outcome) {
        if (messageId == null) return;
        CallMeta meta = new CallMeta(isVideo, durationSec, System.currentTimeMillis(), outcome);
        prefs.edit().putString("call_" + messageId, gson.toJson(meta)).apply();
    }
}
