package com.insta.nova.ui.chat;

import android.app.Application;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import com.insta.nova.data.local.ChatSettingsStore;
import com.insta.nova.data.local.LocalChatStore;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.model.Message;
import com.insta.nova.data.model.MessageReaction;
import com.insta.nova.data.model.Profile;
import com.insta.nova.data.repository.ChatRepository;
import com.insta.nova.data.repository.ProfileRepository;
import com.insta.nova.data.repository.StorageRepository;
import com.insta.nova.util.Constants;
import com.insta.nova.util.ImageUtils;
import com.insta.nova.util.Resource;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * PAGE 17 — MVVM state holder for one 1-on-1 conversation.
 *
 * <p>Owns pagination, the 3 s refresh merge (DiffUtil is applied by the adapter),
 * optimistic sends, read receipts and the intro block. All I/O goes through
 * {@link ChatRepository} / {@link StorageRepository}.
 */
public class ChatViewModel extends AndroidViewModel {

    private static final String TAG = "InstaApi";

    /** Everything the screen renders, replaced atomically on each change. */
    public static class ChatState {
        public final List<Message> messages;
        public final ChatIntro intro;
        public final ChatRepository.Capabilities capabilities;
        public final String statusText;
        public final boolean loading;

        ChatState(List<Message> messages, ChatIntro intro,
                  ChatRepository.Capabilities capabilities, String statusText, boolean loading) {
            this.messages = messages;
            this.intro = intro;
            this.capabilities = capabilities;
            this.statusText = statusText;
            this.loading = loading;
        }
    }

    private final ChatRepository chatRepository;
    private final ProfileRepository profileRepository;
    private final StorageRepository storageRepository;
    private final LocalChatStore localChatStore;
    private final SessionManager sessionManager;
    private final String myId;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private final MutableLiveData<ChatState> state = new MutableLiveData<>();
    private final MutableLiveData<String> toast = new MutableLiveData<>();

    /** Local revision is retained for legacy call-entry rendering only. */
    private final Observer<Integer> localRevisionObserver = revision -> onLocalChatChanged();

    private final Map<String, Message> byId = new LinkedHashMap<>();
    private final Set<String> removedIds = new HashSet<>();

    private String conversationId;
    private String otherUserId;
    private String otherUsername;
    private String otherAvatar;
    private ChatIntro intro = new ChatIntro();
    private ChatRepository.Capabilities capabilities;
    private String statusText;
    private boolean initialLoading = true;
    private boolean hasMore = true;
    private boolean loadingOlder;
    private boolean visible;
    private boolean initialized;
    private long lastPresenceFetch;
    private int unreadPatchInFlight;

    public ChatViewModel(@NonNull Application application) {
        super(application);
        chatRepository = ChatRepository.getInstance(application);
        profileRepository = new ProfileRepository(application);
        storageRepository = new StorageRepository(application);
        localChatStore = new LocalChatStore(application);
        sessionManager = SessionManager.getInstance(application);
        myId = sessionManager.getUserId();
        state.setValue(buildState());
        localChatStore.revision().observeForever(localRevisionObserver);
    }

    public LiveData<ChatState> state() { return state; }
    public LiveData<String> toast() { return toast; }

    /**
     * Entry point. Safe to call again after returning to the screen: it keeps the
     * loaded pages and only refreshes.
     */
    public void init(String conversationId, String otherUserId, String username, String avatar) {
        boolean sameConversation = conversationId != null && conversationId.equals(this.conversationId);
        this.conversationId = conversationId;
        this.otherUserId = otherUserId;
        this.otherUsername = username;
        this.otherAvatar = avatar;

        if (!sameConversation) {
            byId.clear();
            removedIds.clear();
            intro = new ChatIntro();
            intro.username = username != null ? username : "";
            intro.avatarUrl = avatar != null ? avatar : "";
            intro.fullName = username != null ? username : "";
            statusText = null;
            hasMore = true;
            initialLoading = true;
            capabilities = null;
            publish();
            mergeLocalMessages();
        }

        if (initialized && sameConversation) {
            refresh();
            return;
        }
        initialized = true;

        loadCapabilities();
        loadIntro();
        if ((this.conversationId == null || this.conversationId.isEmpty())
                && otherUserId != null && !otherUserId.isEmpty()) {
            once(chatRepository.getOrCreateConversation(otherUserId), res -> {
                if (res != null && res.status == Resource.Status.SUCCESS
                        && res.data != null && !res.data.isEmpty()) {
                    this.conversationId = res.data;
                    loadLatest(true);
                } else {
                    initialLoading = false;
                    publish();
                    toast.setValue("Couldn't start conversation");
                }
            });
        } else {
            loadLatest(true);
        }
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
        if (visible) {
            refresh();
            mergeLocalMessages();
            markReadIfNeeded();
        }
    }

    /* ------------------------------------------------------------------ */
    /* Loading                                                             */
    /* ------------------------------------------------------------------ */

    private void loadCapabilities() {
        once(chatRepository.probeCapabilities(), res -> {
            if (res != null && res.status == Resource.Status.SUCCESS && res.data != null) {
                capabilities = res.data;
                publish();
                if (capabilities.reactions) loadLatest(true);
                if (capabilities.presence) loadPresence();
                if (capabilities.mutualFollowings) loadMutualFollowings();
                Log.d(TAG, "capabilities reactions=" + capabilities.reactions
                        + " presence=" + capabilities.presence
                        + " mutual=" + capabilities.mutualFollowings);
            }
        });
    }

    /** Newest page (initial load and every 3 s poll). */
    private void loadLatest(boolean initial) {
        if (conversationId == null || conversationId.isEmpty()) {
            initialLoading = false;
            publish();
            return;
        }
        boolean withReactions = capabilities != null && capabilities.reactions;
        once(chatRepository.getMessagesPage(conversationId, null,
                ChatRepository.MESSAGE_PAGE_SIZE, withReactions), res -> {
            initialLoading = false;
            if (res != null && res.status == Resource.Status.SUCCESS && res.data != null) {
                mergePage(res.data, initial);
                // Re-merge persisted call entries after the server page.
                if (initial) hasMore = res.data.size() >= ChatRepository.MESSAGE_PAGE_SIZE;
                markReadIfNeeded();
            } else if (initial) {
                publish();
                toast.setValue(res != null && res.message != null
                        ? res.message : "Couldn't load messages");
            } else {
                publish();
            }
        });
    }

    /** Called by the 3 s poll while the screen is visible. */
    public void refresh() {
        if (!initialized) return;
        mergeLocalMessages();
        loadLatest(false);
        long now = System.currentTimeMillis();
        if (capabilities != null && capabilities.presence && now - lastPresenceFetch > 60_000L) {
            loadPresence();
        }
    }

    /** Older page when the list is scrolled to the very top. */
    public void loadOlder() {
        if (loadingOlder || !hasMore || conversationId == null) return;
        String oldest = oldestServerTimestamp();
        if (oldest == null) return;
        loadingOlder = true;
        boolean withReactions = capabilities != null && capabilities.reactions;
        once(chatRepository.getMessagesPage(conversationId, oldest,
                ChatRepository.MESSAGE_PAGE_SIZE, withReactions), res -> {
            loadingOlder = false;
            if (res != null && res.status == Resource.Status.SUCCESS && res.data != null) {
                hasMore = res.data.size() >= ChatRepository.MESSAGE_PAGE_SIZE;
                mergePage(res.data, false);
            }
        });
    }

    public boolean isLoadingOlder() { return loadingOlder; }

    private void mergePage(List<Message> page, boolean replace) {
        if (replace) {
            // Keep messages that are still in flight; the server cannot know them yet.
            List<Message> pending = new ArrayList<>();
            for (Message m : byId.values()) {
                if (m.isPending() || m.isFailed()) pending.add(m);
            }
            byId.clear();
            for (Message m : pending) byId.put(m.getId(), m);
        }
        ChatSettingsStore settings = ChatSettingsStore.getInstance(getApplication());
        for (Message m : page) {
            if (m == null || m.getId() == null) continue;
            if (removedIds.contains(m.getId()) || settings.isDeleted(m.getId())) continue;
            Message local = byId.get(m.getId());
            if (local != null && (local.isPending() || local.isFailed())) continue;

            // Merge local reactions
            List<MessageReaction> storedReactions = settings.getReactions(m.getId());
            if (!storedReactions.isEmpty()) {
                m.setReactions(storedReactions);
            }
            // Merge reply meta
            ChatSettingsStore.ReplyMeta replyMeta = settings.getReplyMeta(m.getId());
            if (replyMeta != null) {
                m.setReplyToId(replyMeta.replyToId);
                m.setReplyToSender(replyMeta.replyToSender);
                m.setReplyToText(replyMeta.replyToText);
            }
            byId.put(m.getId(), m);
        }
        publish();
    }

    private String oldestServerTimestamp() {
        String oldest = null;
        long oldestTs = Long.MAX_VALUE;
        for (Message m : byId.values()) {
            if (m.getId() != null && m.getId().startsWith("local-")) continue;
            String raw = m.getCreatedAtRaw();
            if (raw == null || raw.isEmpty()) continue;
            if (m.getCreatedAt() < oldestTs) {
                oldestTs = m.getCreatedAt();
                oldest = raw;
            }
        }
        return oldest;
    }

    /* ------------------------------------------------------------------ */
    /* Intro                                                               */
    /* ------------------------------------------------------------------ */

    private void loadIntro() {
        if (otherUserId == null) {
            publish();
            return;
        }
        once(profileRepository.getProfile(otherUserId), res -> {
            if (res != null && res.status == Resource.Status.SUCCESS && res.data != null) {
                Profile p = res.data;
                intro.fullName = p.getFullName() != null && !p.getFullName().isEmpty()
                        ? p.getFullName() : p.getUsername();
                intro.username = p.getUsername() != null ? p.getUsername() : otherUsername;
                intro.avatarUrl = p.getAvatarUrl();
                intro.followers = p.getFollowersCount();
                intro.posts = p.getPostsCount();
                if (intro.avatarUrl == null || intro.avatarUrl.isEmpty()) {
                    intro.avatarUrl = otherAvatar;
                }
                publish();
            }
        });

        once(chatRepository.getFollowState(otherUserId), res -> {
            if (res != null && res.status == Resource.Status.SUCCESS && res.data != null) {
                boolean iFollow = res.data[0];
                boolean followsMe = res.data[1];
                String name = Constants.APP_NAME;
                if (iFollow && followsMe) {
                    intro.followLine = "You follow each other on " + name;
                } else if (iFollow) {
                    intro.followLine = "You follow " + handle();
                } else if (followsMe) {
                    intro.followLine = handle() + " follows you";
                } else {
                    intro.followLine = null;
                }
                publish();
            }
        });
    }

    private void loadMutualFollowings() {
        if (otherUserId == null) return;
        once(chatRepository.getMutualFollowings(otherUserId), res -> {
            if (res != null && res.status == Resource.Status.SUCCESS && res.data != null) {
                List<Profile> mutual = res.data;
                if (mutual.isEmpty()) {
                    intro.mutualLine = null;
                } else {
                    String sample = mutual.get(0).getUsername();
                    if (mutual.size() == 1) {
                        intro.mutualLine = "You both follow " + sample;
                    } else {
                        int others = mutual.size() - 1;
                        intro.mutualLine = "You both follow " + sample + " "
                                + com.insta.nova.util.Plural.likedByTail(others, Locale.getDefault());
                    }
                }
                publish();
            }
        });
    }

    private void loadPresence() {
        if (otherUserId == null) return;
        lastPresenceFetch = System.currentTimeMillis();
        once(chatRepository.getLastActive(otherUserId), res -> {
            if (res != null && res.status == Resource.Status.SUCCESS && res.data != null) {
                statusText = formatActive(res.data);
                publish();
            }
        });
    }

    private String handle() {
        String name = intro.username;
        if (name == null || name.isEmpty()) name = otherUsername;
        return name == null ? "user" : name;
    }

    /** "Active now" ... "Active 6d ago", or null when the last touch is stale. */
    static String formatActive(long lastActiveMs) {
        if (lastActiveMs <= 0L) return null;
        long diff = Math.max(0L, System.currentTimeMillis() - lastActiveMs);
        if (diff < 2 * 60_000L) return "Active now";
        if (diff < 60 * 60_000L) return "Active " + (diff / 60_000L) + "m ago";
        if (diff < 24 * 60 * 60_000L) return "Active " + (diff / (60 * 60_000L)) + "h ago";
        if (diff < 48 * 60 * 60_000L) return "Active yesterday";
        if (diff < 7 * 24 * 60 * 60_000L) return "Active " + (diff / (24 * 60 * 60_000L)) + "d ago";
        return null;
    }

    /* ------------------------------------------------------------------ */
    /* Sending                                                             */
    /* ------------------------------------------------------------------ */

    public void sendText(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty() || conversationId == null) return;
        if (text.length() > 1000) text = text.substring(0, 1000);
        String sender = myId != null ? myId : "local";

        Message local = new Message("local-" + UUID.randomUUID(), conversationId, sender,
                text, null, false, System.currentTimeMillis());
        local.setPending(true);
        byId.put(local.getId(), local);
        publish();

        // Prompt K: text is server-only. Writing it into LocalChatStore made the
        // optimistic bubble look delivered even when the POST never ran, which is
        // exactly the "message never reaches the other account" symptom. The
        // local overlay is now reserved for call entries.
        postText(local);
    }

    private void postText(Message local) {
        String text = local.getText();
        once(chatRepository.sendMessage(conversationId, text, null), res -> {
            if (res != null && res.status == Resource.Status.SUCCESS && res.data != null) {
                byId.remove(local.getId());
                byId.put(res.data.getId(), res.data);
                publish();
            } else {
                local.setPending(false);
                local.setFailed(true);
                publish();
                toast.setValue("Couldn't send. Tap the icon to retry.");
            }
        });
    }

    public void sendImage(Uri uri) {
        if (uri == null || conversationId == null) return;
        String sender = myId != null ? myId : "local";
        Message local = new Message("local-" + UUID.randomUUID(), conversationId, sender,
                "", null, false, System.currentTimeMillis());
        local.setLocalImageUri(uri.toString());
        local.setImageUrl(uri.toString());
        local.setMessageType("image");
        local.setPending(true);
        byId.put(local.getId(), local);
        publish();

        LocalChatStore.ChatMessage cm = new LocalChatStore.ChatMessage(
                local.getId(), conversationId, sender, "Photo", local.getCreatedAt(), true);
        cm.messageType = "image";
        cm.mediaUrl = uri.toString();
        localChatStore.appendMessage(cm);

        upload(local, uri);
    }

    private void upload(Message local, Uri uri) {
        io.execute(() -> {
            byte[] bytes;
            try {
                bytes = ImageUtils.processUriToJpegBytes(getApplication(), uri);
            } catch (Exception e) {
                Log.e(TAG, "chat image compression failed: " + e.getMessage());
                bytes = null;
            }
            final byte[] data = bytes;
            mainHandler.post(() -> {
                if (data == null || data.length == 0) {
                    failImage(local);
                    return;
                }
                once(storageRepository.uploadFile("chat", data, "jpg", "image/jpeg"), res -> {
                    if (res != null && res.status == Resource.Status.SUCCESS && res.data != null) {
                        once(chatRepository.sendMessage(conversationId, "", res.data), msg -> {
                            if (msg != null && msg.status == Resource.Status.SUCCESS && msg.data != null) {
                                byId.remove(local.getId());
                                byId.put(msg.data.getId(), msg.data);
                                publish();
                            } else {
                                failImage(local);
                            }
                        });
                    } else {
                        failImage(local);
                    }
                });
            });
        });
    }

    private void failImage(Message local) {
        local.setPending(false);
        local.setFailed(true);
        publish();
        toast.setValue("Couldn't send photo. Tap the icon to retry.");
    }

    /** Tap the red ic_error: resend the same bubble. */
    public void retry(Message message) {
        if (message == null) return;
        message.setFailed(false);
        message.setPending(true);
        publish();
        if (message.getLocalImageUri() != null && !message.getLocalImageUri().isEmpty()) {
            upload(message, Uri.parse(message.getLocalImageUri()));
        } else {
            postText(message);
        }
    }

    public void sendVoiceMessage(String localPath, long durationMs) {
        if (localPath == null || conversationId == null) return;
        String sender = myId != null ? myId : "local";
        String id = "local-voice-" + UUID.randomUUID();
        Message local = new Message(id, conversationId, sender,
                "Voice message", null, false, System.currentTimeMillis());
        local.setMessageType("voice");
        local.setVoicePath(localPath);
        local.setVoiceDurationMs(durationMs);
        byId.put(local.getId(), local);
        publish();

        LocalChatStore.ChatMessage cm = new LocalChatStore.ChatMessage(
                id, conversationId, sender, "Voice message", local.getCreatedAt(), true);
        cm.messageType = "voice";
        cm.localVoicePath = localPath;
        cm.voiceDurationMs = durationMs;
        localChatStore.appendMessage(cm);
        ChatSettingsStore.getInstance(getApplication()).saveVoiceMeta(id, localPath, durationMs);

    }

    public void sendCallMessage(boolean isVideo, long durationSec) {
        if (conversationId == null) return;
        String sender = myId != null ? myId : "local";
        String id = "local-call-" + UUID.randomUUID();
        String text = isVideo ? "Video call" : "Audio call";
        Message local = new Message(id, conversationId, sender,
                text, null, false, System.currentTimeMillis());
        local.setMessageType(isVideo ? "video_call" : "audio_call");
        local.setCallDurationSec(durationSec);
        byId.put(local.getId(), local);
        publish();

        LocalChatStore.ChatMessage cm = new LocalChatStore.ChatMessage(
                id, conversationId, sender, text, local.getCreatedAt(), true);
        cm.messageType = isVideo ? "video_call" : "audio_call";
        localChatStore.appendMessage(cm);
        ChatSettingsStore.getInstance(getApplication()).saveCallMeta(id, isVideo, durationSec);
    }

    public void sendReplyText(String raw, String replyToId, String replyToSender, String replyToText) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty() || conversationId == null) return;
        if (text.length() > 1000) text = text.substring(0, 1000);
        String sender = myId != null ? myId : "local";

        String id = "local-" + UUID.randomUUID();
        Message local = new Message(id, conversationId, sender,
                text, null, false, System.currentTimeMillis());
        local.setPending(true);
        local.setReplyToId(replyToId);
        local.setReplyToSender(replyToSender);
        local.setReplyToText(replyToText);
        byId.put(local.getId(), local);
        publish();

        LocalChatStore.ChatMessage cm = new LocalChatStore.ChatMessage(
                id, conversationId, sender, text, local.getCreatedAt(), true);
        cm.replyToId = replyToId;
        cm.replyToSender = replyToSender;
        cm.replyToText = replyToText;
        localChatStore.appendMessage(cm);
        ChatSettingsStore.getInstance(getApplication()).saveReplyMeta(id, replyToId, replyToSender, replyToText);

        // No simulated reply: post the real message only.
        postText(local);
    }

    public void unsend(Message message) {
        if (message == null || message.getId() == null) return;
        final String id = message.getId();
        byId.remove(id);
        removedIds.add(id);
        ChatSettingsStore.getInstance(getApplication()).markDeleted(id);
        localChatStore.removeMessage(id);
        publish();
        if (!id.startsWith("local-")) {
            chatRepository.deleteMessage(id);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Reactions                                                           */
    /* ------------------------------------------------------------------ */

    public boolean reactionsAvailable() {
        return true;
    }

    public void toggleHeart(Message message) {
        if (message == null) return;
        String mine = myReaction(message);
        if (mine == null) setReaction(message, "\u2764\uFE0F");
        else removeMyReaction(message);
    }

    public void setReaction(Message message, String emoji) {
        if (message == null || emoji == null) return;
        applyLocalReaction(message, emoji);
        String sender = myId != null ? myId : "local";
        ChatSettingsStore.getInstance(getApplication()).saveReaction(message.getId(), sender, emoji);
        if (capabilities != null && capabilities.reactions && !message.getId().startsWith("local-")) {
            chatRepository.setReaction(message.getId(), emoji);
        }
    }

    public void removeMyReaction(Message message) {
        if (message == null) return;
        applyLocalReaction(message, null);
        String sender = myId != null ? myId : "local";
        ChatSettingsStore.getInstance(getApplication()).saveReaction(message.getId(), sender, null);
        if (capabilities != null && capabilities.reactions && !message.getId().startsWith("local-")) {
            chatRepository.removeReaction(message.getId());
        }
    }

    public String myReaction(Message message) {
        if (message == null) return null;
        for (MessageReaction r : message.getReactions()) {
            if (myId != null && myId.equals(r.getUserId())) return r.getEmoji();
        }
        return null;
    }

    private void applyLocalReaction(Message message, String emoji) {
        List<MessageReaction> next = new ArrayList<>();
        for (MessageReaction r : message.getReactions()) {
            if (myId != null && myId.equals(r.getUserId())) continue;
            next.add(r);
        }
        if (emoji != null) next.add(new MessageReaction(message.getId(), myId, emoji));
        message.setReactions(next);
        publish();
    }

    /* ------------------------------------------------------------------ */
    /* Local call-entry compatibility                                      */
    /* ------------------------------------------------------------------ */

    /** Refreshes locally persisted call-entry metadata only. */
    private void onLocalChatChanged() {
        if (!initialized) return;
        mergeLocalMessages();
    }

    /** Adds persisted call entries the server messages table does not contain. */
    private void mergeLocalMessages() {
        if (conversationId == null || conversationId.isEmpty()) return;
        boolean changed = false;
        ChatSettingsStore settings = ChatSettingsStore.getInstance(getApplication());
        for (LocalChatStore.ChatMessage stored : localChatStore.messages(conversationId)) {
            if (stored == null || stored.id == null) continue;
            // Text never lives in the local overlay: it is authoritative only in
            // the shared messages table. Entries with no messageType are legacy
            // text (including the old simulated replies), so they are skipped
            // rather than allowed to reappear in a real thread.
            if (stored.messageType == null || stored.messageType.isEmpty()) continue;
            if (removedIds.contains(stored.id) || settings.isDeleted(stored.id)) continue;
            Message existing = byId.get(stored.id);
            if (existing != null) {
                if (existing.isRead() != stored.read) {
                    existing.setRead(stored.read);
                    changed = true;
                }
                // Refresh reactions
                List<MessageReaction> storedReactions = settings.getReactions(stored.id);
                if (!storedReactions.isEmpty()) {
                    existing.setReactions(storedReactions);
                }
                continue;
            }
            Message model = new Message(stored.id, conversationId, stored.senderId,
                    stored.text, null, false, stored.createdAt);
            model.setRead(stored.read);
            if (stored.messageType != null) {
                model.setMessageType(stored.messageType);
            }
            if (stored.mediaUrl != null) {
                model.setImageUrl(stored.mediaUrl);
                model.setLocalImageUri(stored.mediaUrl);
            }
            if (stored.localVoicePath != null) {
                model.setVoicePath(stored.localVoicePath);
                model.setVoiceDurationMs(stored.voiceDurationMs);
            }
            if (stored.replyToText != null) {
                model.setReplyToId(stored.replyToId);
                model.setReplyToSender(stored.replyToSender);
                model.setReplyToText(stored.replyToText);
            }
            // Check call meta
            ChatSettingsStore.CallMeta callMeta = settings.getCallMeta(stored.id);
            if (callMeta != null) {
                model.setCallDurationSec(callMeta.durationSec);
                model.setCallOutcome(callMeta.outcome);
            }
            // Reactions
            List<MessageReaction> storedReactions = settings.getReactions(stored.id);
            if (!storedReactions.isEmpty()) {
                model.setReactions(storedReactions);
            }
            byId.put(stored.id, model);
            changed = true;
        }
        if (changed) publish();
    }

    /* ------------------------------------------------------------------ */
    /* Read receipts                                                       */
    /* ------------------------------------------------------------------ */

    private void markReadIfNeeded() {
        if (!visible || conversationId == null || unreadPatchInFlight > 0) return;
        boolean hasUnread = false;
        for (Message m : byId.values()) {
            if (m.getSenderId() != null && !m.getSenderId().equals(myId)
                    && !m.isRead() && m.getId() != null && !m.getId().startsWith("local-")) {
                hasUnread = true;
                break;
            }
        }
        if (!hasUnread) return;

        // Optimistic: the next poll must not queue a second PATCH for the same rows.
        for (Message m : byId.values()) {
            if (m.getSenderId() != null && !m.getSenderId().equals(myId)) m.setRead(true);
        }
        publish();
        unreadPatchInFlight = 1;
        once(chatRepository.markIncomingRead(conversationId), res -> {
            unreadPatchInFlight = 0;
            if (res == null || res.status != Resource.Status.SUCCESS) {
                Log.w(TAG, "mark read failed; next poll retries");
            }
        });
    }

    /* ------------------------------------------------------------------ */
    /* Helpers                                                             */
    /* ------------------------------------------------------------------ */

    private void publish() {
        state.setValue(buildState());
    }

    private ChatState buildState() {
        List<Message> list = new ArrayList<>(byId.values());
        Collections.sort(list, new Comparator<Message>() {
            @Override
            public int compare(Message a, Message b) {
                int byTime = Long.compare(a.getCreatedAt(), b.getCreatedAt());
                if (byTime != 0) return byTime;
                String ia = a.getId() == null ? "" : a.getId();
                String ib = b.getId() == null ? "" : b.getId();
                return ia.compareTo(ib);
            }
        });
        return new ChatState(list, intro, capabilities, statusText, initialLoading);
    }

    private <T> void once(LiveData<Resource<T>> live, Consumer<Resource<T>> onResult) {
        if (live == null) return;
        live.observeForever(new Observer<Resource<T>>() {
            @Override
            public void onChanged(Resource<T> resource) {
                if (resource == null || resource.status == Resource.Status.LOADING) return;
                live.removeObserver(this);
                onResult.accept(resource);
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        localChatStore.revision().removeObserver(localRevisionObserver);
        io.shutdownNow();
    }

    /* ------------------------------------------------------------------ */
    /* Separator labels                                                    */
    /* ------------------------------------------------------------------ */

    static boolean needsSeparator(Message previous, Message current) {
        if (current == null) return false;
        if (previous == null) return true;
        if (sameDay(previous.getCreatedAt(), current.getCreatedAt())) {
            return current.getCreatedAt() - previous.getCreatedAt() > 60 * 60 * 1000L;
        }
        return true;
    }

    static String separatorLabel(long timestamp) {
        Calendar now = Calendar.getInstance();
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(timestamp);

        String time = new SimpleDateFormat("h:mm a", Locale.US).format(then.getTime());
        if (sameDay(now.getTimeInMillis(), timestamp)) {
            return "TODAY " + time;
        }
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        if (sameDay(yesterday.getTimeInMillis(), timestamp)) {
            return "YESTERDAY " + time;
        }
        String date = new SimpleDateFormat("MMM d, ", Locale.US).format(then.getTime());
        return (date + time).toUpperCase(Locale.US);
    }

    private static boolean sameDay(long a, long b) {
        Calendar ca = Calendar.getInstance();
        ca.setTimeInMillis(a);
        Calendar cb = Calendar.getInstance();
        cb.setTimeInMillis(b);
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR)
                && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR);
    }
}
