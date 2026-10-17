package com.insta.nova.data.repository;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.google.gson.JsonElement;
import com.insta.nova.InstaApp;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.model.Conversation;
import com.insta.nova.data.model.Message;
import com.insta.nova.data.model.Profile;
import com.insta.nova.data.remote.RetrofitClient;
import com.insta.nova.data.remote.api.RestApi;
import com.insta.nova.data.remote.dto.ConversationDto;
import com.insta.nova.data.remote.dto.MessageDto;
import com.insta.nova.data.remote.dto.ProfileDto;
import com.insta.nova.data.remote.dto.UnreadCountsDto;
import com.insta.nova.util.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Repository for direct message conversations and chat messages backed by Supabase.
 */
public class ChatRepository {

    private static final String TAG = "InstaApi";

    /**
     * How long a successful unread-count response is treated as fresh.
     *
     * <p>Deliberately shorter than {@code Constants.POLLING_BADGES_MS} (30 s): with an
     * equal TTL a poll tick could land inside the window and be skipped, pushing the
     * next badge refresh out by a full interval. At 25 s every poll tick is stale
     * enough to fetch, while a Home resume moments after a poll still collapses.
     */
    static final long UNREAD_TTL_MS = 25_000L;

    private static ChatRepository instance;
    private final RestApi restApi;
    private final SessionManager sessionManager;

    /**
     * Single source of unread counts for the whole app. Both the badge poll and
     * the Home screen read this instance, so neither owns a private request.
     */
    private final MutableLiveData<Resource<UnreadCountsDto>> unreadCounts = new MutableLiveData<>();
    private final Object unreadLock = new Object();
    private final LongSupplier clock;
    private boolean unreadInFlight;
    private boolean unreadHasSuccess;
    /** Set only by a successful response; failures leave it untouched. */
    private long unreadLastSuccessAt;
    /** Guards against a late callback from an older request touching current state. */
    private long unreadEpoch;

    public ChatRepository(Context context) {
        this(RetrofitClient.getInstance(context).getRestApi(),
                SessionManager.getInstance(context),
                SystemClock::elapsedRealtime);
    }

    /** Test seam: a fake API plus a controllable clock. */
    ChatRepository(RestApi restApi, SessionManager sessionManager, LongSupplier clock) {
        this.restApi = restApi;
        this.sessionManager = sessionManager;
        this.clock = clock;
    }

    public static synchronized ChatRepository getInstance() {
        if (instance == null) {
            instance = new ChatRepository(InstaApp.getInstance());
        }
        return instance;
    }

    public static synchronized ChatRepository getInstance(Context context) {
        if (instance == null) {
            instance = new ChatRepository(context.getApplicationContext());
        }
        return instance;
    }

    public LiveData<Resource<List<Conversation>>> getConversations() {
        MutableLiveData<Resource<List<Conversation>>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));

        restApi.getInbox(new HashMap<>()).enqueue(new Callback<List<ConversationDto>>() {
            @Override
            public void onResponse(Call<List<ConversationDto>> call, Response<List<ConversationDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Conversation> list = new ArrayList<>();
                    for (ConversationDto dto : response.body()) {
                        list.add(dto.toConversation());
                    }
                    result.postValue(Resource.success(list));
                } else {
                    result.postValue(Resource.error("Failed to load inbox", null));
                }
            }

            @Override
            public void onFailure(Call<List<ConversationDto>> call, Throwable t) {
                result.postValue(Resource.error(t.getMessage() != null ? t.getMessage() : "Network error", null));
            }
        });

        return result;
    }

    public LiveData<Resource<String>> getOrCreateConversation(String otherUserId) {
        MutableLiveData<Resource<String>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));

        Map<String, Object> body = new HashMap<>();
        body.put("p_other_user_id", otherUserId);

        restApi.getOrCreateConversation(body).enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String conversationId;
                    try {
                        conversationId = response.body().getAsString();
                    } catch (Exception e) {
                        conversationId = response.body().toString().replace("\"", "");
                    }
                    result.postValue(Resource.success(conversationId));
                } else {
                    result.postValue(Resource.error("Failed to start conversation", null));
                }
            }

            @Override
            public void onFailure(Call<JsonElement> call, Throwable t) {
                result.postValue(Resource.error(t.getMessage() != null ? t.getMessage() : "Network error", null));
            }
        });

        return result;
    }

    public LiveData<Resource<List<Message>>> getMessages(String conversationId) {
        MutableLiveData<Resource<List<Message>>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));

        Map<String, String> query = new HashMap<>();
        query.put("conversation_id", "eq." + conversationId);
        query.put("order", "created_at.asc");

        restApi.getMessages(query).enqueue(new Callback<List<MessageDto>>() {
            @Override
            public void onResponse(Call<List<MessageDto>> call, Response<List<MessageDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Message> list = new ArrayList<>();
                    for (MessageDto dto : response.body()) {
                        list.add(dto.toMessage());
                    }
                    result.postValue(Resource.success(list));
                } else {
                    result.postValue(Resource.error("Failed to load messages", null));
                }
            }

            @Override
            public void onFailure(Call<List<MessageDto>> call, Throwable t) {
                result.postValue(Resource.error(t.getMessage() != null ? t.getMessage() : "Network error", null));
            }
        });

        return result;
    }

    public LiveData<Resource<Message>> sendMessage(String conversationId, String text, String mediaUrl) {
        MutableLiveData<Resource<Message>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));

        String myId = sessionManager.getUserId();
        if (myId == null) {
            result.setValue(Resource.error("Not authenticated", null));
            return result;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("conversation_id", conversationId);
        body.put("sender_id", myId);
        body.put("body", text != null && !text.isEmpty() ? text : "Sent a photo");
        if (mediaUrl != null && !mediaUrl.isEmpty()) {
            body.put("media_url", mediaUrl);
        }

        restApi.sendMessage(body).enqueue(new Callback<List<MessageDto>>() {
            @Override
            public void onResponse(Call<List<MessageDto>> call, Response<List<MessageDto>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    result.postValue(Resource.success(response.body().get(0).toMessage()));
                } else {
                    result.postValue(Resource.error("Failed to send message", null));
                }
            }

            @Override
            public void onFailure(Call<List<MessageDto>> call, Throwable t) {
                result.postValue(Resource.error(t.getMessage() != null ? t.getMessage() : "Network error", null));
            }
        });

        return result;
    }

    public void markMessagesRead(String conversationId) {
        String myId = sessionManager.getUserId();
        if (myId == null) return;

        Map<String, String> query = new HashMap<>();
        query.put("conversation_id", "eq." + conversationId);
        query.put("sender_id", "neq." + myId);
        query.put("is_read", "eq.false");

        Map<String, Object> body = new HashMap<>();
        body.put("is_read", true);

        restApi.markMessagesRead(query, body).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {}

            @Override
            public void onFailure(Call<Void> call, Throwable t) {}
        });
    }

    /**
     * The app-wide unread-count stream. Register observers once per lifecycle
     * owner and call {@link #refreshUnreadCounts(boolean)} when a refresh is due;
     * never create a new request just to observe the result.
     */
    public LiveData<Resource<UnreadCountsDto>> unreadCounts() {
        return unreadCounts;
    }

    /**
     * Requests unread counts, collapsing concurrent callers onto one request.
     *
     * <ul>
     *   <li>If a request is already in flight this is a no-op, whatever the flags.</li>
     *   <li>{@code force = false} skips the call while the last <em>successful</em>
     *       response is younger than {@link #UNREAD_TTL_MS}.</li>
     *   <li>{@code force = true} bypasses that freshness window (explicit refresh),
     *       but still shares an in-flight request.</li>
     *   <li>Failures never advance the freshness timestamp, so the next call retries
     *       immediately instead of waiting out the TTL.</li>
     * </ul>
     *
     * @return true when a network request was actually started
     */
    public boolean refreshUnreadCounts(boolean force) {
        final long epoch;
        synchronized (unreadLock) {
            if (unreadInFlight) return false;
            long now = clock.getAsLong();
            if (!force && unreadHasSuccess && now - unreadLastSuccessAt < UNREAD_TTL_MS) {
                return false;
            }
            unreadInFlight = true;
            epoch = ++unreadEpoch;
            if (unreadCounts.getValue() == null) {
                unreadCounts.postValue(Resource.loading());
            }
        }

        restApi.getUnreadCounts(new HashMap<>()).enqueue(new Callback<UnreadCountsDto>() {
            @Override
            public void onResponse(Call<UnreadCountsDto> call, Response<UnreadCountsDto> response) {
                synchronized (unreadLock) {
                    // A superseded request must not clear the newer request's in-flight flag.
                    if (epoch != unreadEpoch) return;
                    unreadInFlight = false;
                    if (response.isSuccessful() && response.body() != null) {
                        // Only success refreshes the cache window.
                        unreadHasSuccess = true;
                        unreadLastSuccessAt = clock.getAsLong();
                        unreadCounts.postValue(Resource.success(response.body()));
                    } else {
                        unreadCounts.postValue(Resource.error("Failed to fetch unread counts", null));
                    }
                }
            }

            @Override
            public void onFailure(Call<UnreadCountsDto> call, Throwable t) {
                synchronized (unreadLock) {
                    if (epoch != unreadEpoch) return;
                    unreadInFlight = false;
                    unreadCounts.postValue(Resource.error(
                            t.getMessage() != null ? t.getMessage() : "Network error", null));
                }
            }
        });
        return true;
    }

    /**
     * Drops every cached unread value and invalidates any in-flight request.
     *
     * <p>Called on sign-out. Without this the singleton would keep the previous
     * account's counts in {@link #unreadCounts} and its freshness window, so the next
     * account could render a stale badge or skip its first refresh entirely. Bumping the
     * epoch makes any late callback from the old account a no-op.
     */
    public void resetForAccountSwitch() {
        synchronized (unreadLock) {
            unreadEpoch++;
            unreadInFlight = false;
            unreadHasSuccess = false;
            unreadLastSuccessAt = 0L;
            unreadCounts.postValue(null);
        }
    }

    /* ================================================================= */
    /* PAGE 17 — CHAT                                                    */
    /* ================================================================= */

    /** Page size for the newest-first message query. */
    public static final int MESSAGE_PAGE_SIZE = 100;

    /**
     * Database objects PAGE 17 depends on. Each flag is resolved once per chat
     * screen; a missing object disables only its own feature instead of leaving a
     * button that cannot work.
     */
    public static final class Capabilities {
        public final boolean reactions;
        public final boolean presence;
        public final boolean mutualFollowings;

        Capabilities(boolean reactions, boolean presence, boolean mutualFollowings) {
            this.reactions = reactions;
            this.presence = presence;
            this.mutualFollowings = mutualFollowings;
        }
    }

    /**
     * One GET against each optional object. Success (even an empty list) means the
     * object exists; only a 404 / 400 "column does not exist" means it is missing.
     */
    public LiveData<Resource<Capabilities>> probeCapabilities() {
        MutableLiveData<Resource<Capabilities>> result = new MutableLiveData<>();
        final boolean[] flags = new boolean[3];
        final int[] remaining = {3};
        final Object lock = new Object();

        Runnable complete = () -> {
            synchronized (lock) {
                if (--remaining[0] != 0) return;
            }
            result.postValue(Resource.success(
                    new Capabilities(flags[0], flags[1], flags[2])));
        };

        restApi.probeReactions("message_id", "1").enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> call,
                                   Response<List<Map<String, Object>>> response) {
                flags[0] = response.isSuccessful();
                if (!response.isSuccessful()) {
                    Log.w(TAG, "message_reactions unavailable (HTTP " + response.code() + ")");
                }
                complete.run();
            }

            @Override
            public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                flags[0] = false;
                complete.run();
            }
        });

        Map<String, String> presenceQuery = new HashMap<>();
        presenceQuery.put("select", "last_active_at");
        presenceQuery.put("limit", "1");
        restApi.getProfilesRaw(presenceQuery).enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> call,
                                   Response<List<Map<String, Object>>> response) {
                flags[1] = response.isSuccessful();
                if (!response.isSuccessful()) {
                    Log.w(TAG, "profiles.last_active_at unavailable (HTTP " + response.code() + ")");
                }
                complete.run();
            }

            @Override
            public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                flags[1] = false;
                complete.run();
            }
        });

        Map<String, Object> mutualBody = new HashMap<>();
        String myId = sessionManager.getUserId();
        mutualBody.put("p_other", myId != null && !myId.isEmpty() ? myId
                : "00000000-0000-0000-0000-000000000000");
        restApi.mutualFollowings(mutualBody).enqueue(new Callback<List<ProfileDto>>() {
            @Override
            public void onResponse(Call<List<ProfileDto>> call, Response<List<ProfileDto>> response) {
                flags[2] = response.isSuccessful();
                if (!response.isSuccessful()) {
                    Log.w(TAG, "mutual_followings unavailable (HTTP " + response.code() + ")");
                }
                complete.run();
            }

            @Override
            public void onFailure(Call<List<ProfileDto>> call, Throwable t) {
                flags[2] = false;
                complete.run();
            }
        });

        return result;
    }

    /**
     * Newest-first page of messages, oldest-to-newest after reversing.
     *
     * @param beforeRaw {@code created_at} of the oldest loaded message, or null for
     *                  the newest page (keyset pagination, no offset drift)
     * @param withReactions appends the embedded {@code message_reactions} rows
     */
    public LiveData<Resource<List<Message>>> getMessagesPage(String conversationId,
                                                             String beforeRaw,
                                                             int limit,
                                                             boolean withReactions) {
        MutableLiveData<Resource<List<Message>>> result = new MutableLiveData<>();
        if (conversationId == null || conversationId.isEmpty()) {
            result.setValue(Resource.error("Missing conversation", null));
            return result;
        }

        Map<String, String> query = new HashMap<>();
        query.put("conversation_id", "eq." + conversationId);
        query.put("select", withReactions ? "*,message_reactions(user_id,emoji)" : "*");
        query.put("order", "created_at.desc");
        query.put("limit", String.valueOf(limit));
        if (beforeRaw != null && !beforeRaw.isEmpty()) {
            query.put("created_at", "lt." + beforeRaw);
        }

        restApi.getMessages(query).enqueue(new Callback<List<MessageDto>>() {
            @Override
            public void onResponse(Call<List<MessageDto>> call, Response<List<MessageDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Message> list = new ArrayList<>();
                    for (MessageDto dto : response.body()) {
                        if (dto == null) continue;
                        list.add(dto.toMessage());
                    }
                    result.postValue(Resource.success(list));
                } else {
                    result.postValue(Resource.error("Failed to load messages (HTTP "
                            + response.code() + ")", null));
                }
            }

            @Override
            public void onFailure(Call<List<MessageDto>> call, Throwable t) {
                result.postValue(Resource.error(
                        t.getMessage() != null ? t.getMessage() : "Network error", null));
            }
        });
        return result;
    }

    /** PATCH the incoming, still-unread messages of one conversation. */
    public LiveData<Resource<Boolean>> markIncomingRead(String conversationId) {
        MutableLiveData<Resource<Boolean>> result = new MutableLiveData<>();
        String myId = sessionManager.getUserId();
        if (myId == null || conversationId == null) {
            result.setValue(Resource.error("Not authenticated", null));
            return result;
        }

        Map<String, String> query = new HashMap<>();
        query.put("conversation_id", "eq." + conversationId);
        query.put("sender_id", "neq." + myId);
        query.put("is_read", "eq.false");

        Map<String, Object> body = new HashMap<>();
        body.put("is_read", true);

        restApi.markMessagesRead(query, body).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    // The Inbox badge reads this shared stream: refresh immediately.
                    refreshUnreadCounts(true);
                    result.postValue(Resource.success(true));
                } else {
                    result.postValue(Resource.error("Failed to mark read", null));
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                result.postValue(Resource.error("Failed to mark read", null));
            }
        });
        return result;
    }

    public LiveData<Resource<Boolean>> deleteMessage(String messageId) {
        MutableLiveData<Resource<Boolean>> result = new MutableLiveData<>();
        restApi.deleteMessage("eq." + messageId).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                result.postValue(response.isSuccessful()
                        ? Resource.success(true)
                        : Resource.error("Failed to unsend", null));
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                result.postValue(Resource.error("Failed to unsend", null));
            }
        });
        return result;
    }

    /** {@code profiles.last_active_at} in epoch millis, or 0 when unknown. */
    public LiveData<Resource<Long>> getLastActive(String userId) {
        MutableLiveData<Resource<Long>> result = new MutableLiveData<>();
        if (userId == null || userId.isEmpty()) {
            result.setValue(Resource.error("Missing user", null));
            return result;
        }
        Map<String, String> query = new HashMap<>();
        query.put("select", "id,last_active_at");
        query.put("id", "eq." + userId);
        query.put("limit", "1");
        restApi.getProfilesRaw(query).enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> call,
                                   Response<List<Map<String, Object>>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    Object raw = response.body().get(0).get("last_active_at");
                    long ts = com.insta.nova.util.TimeUtils.parseTimestamp(
                            raw == null ? null : String.valueOf(raw));
                    result.postValue(Resource.success(ts));
                } else if (response.isSuccessful()) {
                    result.postValue(Resource.success(0L));
                } else {
                    result.postValue(Resource.error("presence unavailable", null));
                }
            }

            @Override
            public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                result.postValue(Resource.error("presence unavailable", null));
            }
        });
        return result;
    }

    /**
     * Follow relationship in both directions: {@code [0]} = I follow them,
     * {@code [1]} = they follow me.
     */
    public LiveData<Resource<boolean[]>> getFollowState(String otherUserId) {
        MutableLiveData<Resource<boolean[]>> result = new MutableLiveData<>();
        String myId = sessionManager.getUserId();
        if (myId == null || otherUserId == null) {
            result.setValue(Resource.error("Not authenticated", null));
            return result;
        }
        final boolean[] state = new boolean[2];
        final int[] remaining = {2};
        final Object lock = new Object();
        Runnable finish = () -> {
            synchronized (lock) {
                if (--remaining[0] != 0) return;
            }
            result.postValue(Resource.success(state));
        };

        Map<String, String> out = new HashMap<>();
        out.put("follower_id", "eq." + myId);
        out.put("following_id", "eq." + otherUserId);
        out.put("select", "follower_id");
        out.put("limit", "1");
        restApi.getFollows(out).enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> call,
                                   Response<List<Map<String, Object>>> response) {
                state[0] = response.isSuccessful() && response.body() != null && !response.body().isEmpty();
                finish.run();
            }

            @Override
            public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                finish.run();
            }
        });

        Map<String, String> in = new HashMap<>();
        in.put("follower_id", "eq." + otherUserId);
        in.put("following_id", "eq." + myId);
        in.put("select", "follower_id");
        in.put("limit", "1");
        restApi.getFollows(in).enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> call,
                                   Response<List<Map<String, Object>>> response) {
                state[1] = response.isSuccessful() && response.body() != null && !response.body().isEmpty();
                finish.run();
            }

            @Override
            public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                finish.run();
            }
        });

        return result;
    }

    /** People the caller and {@code otherUserId} both follow. */
    public LiveData<Resource<List<Profile>>> getMutualFollowings(String otherUserId) {
        MutableLiveData<Resource<List<Profile>>> result = new MutableLiveData<>();
        Map<String, Object> body = new HashMap<>();
        body.put("p_other", otherUserId);
        restApi.mutualFollowings(body).enqueue(new Callback<List<ProfileDto>>() {
            @Override
            public void onResponse(Call<List<ProfileDto>> call, Response<List<ProfileDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Profile> list = new ArrayList<>();
                    for (ProfileDto dto : response.body()) {
                        if (dto != null) list.add(dto.toProfile());
                    }
                    result.postValue(Resource.success(list));
                } else {
                    result.postValue(Resource.error("mutual_followings unavailable", null));
                }
            }

            @Override
            public void onFailure(Call<List<ProfileDto>> call, Throwable t) {
                result.postValue(Resource.error("mutual_followings unavailable", null));
            }
        });
        return result;
    }

    public LiveData<Resource<Boolean>> setReaction(String messageId, String emoji) {
        MutableLiveData<Resource<Boolean>> result = new MutableLiveData<>();
        String myId = sessionManager.getUserId();
        if (myId == null) {
            result.setValue(Resource.error("Not authenticated", null));
            return result;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("message_id", messageId);
        body.put("user_id", myId);
        body.put("emoji", emoji);
        restApi.upsertReaction(body).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                result.postValue(response.isSuccessful()
                        ? Resource.success(true)
                        : Resource.error("Failed to react", null));
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                result.postValue(Resource.error("Failed to react", null));
            }
        });
        return result;
    }

    public LiveData<Resource<Boolean>> removeReaction(String messageId) {
        MutableLiveData<Resource<Boolean>> result = new MutableLiveData<>();
        String myId = sessionManager.getUserId();
        if (myId == null) {
            result.setValue(Resource.error("Not authenticated", null));
            return result;
        }
        restApi.deleteReaction("eq." + messageId, "eq." + myId).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                result.postValue(response.isSuccessful()
                        ? Resource.success(true)
                        : Resource.error("Failed to remove reaction", null));
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                result.postValue(Resource.error("Failed to remove reaction", null));
            }
        });
        return result;
    }

    /** 0 = unknown, 1 = available, 2 = function missing (stop calling it). */
    private static volatile int presenceState = 0;

    /** POST rpc/touch_active. No-op once the database reports the function missing. */
    public void touchActive() {
        if (presenceState == 2) return;
        restApi.touchActive(new HashMap<>()).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.code() == 404) {
                    presenceState = 2;
                    Log.w(TAG, "touch_active() missing; presence heartbeat stopped");
                } else if (response.isSuccessful()) {
                    presenceState = 1;
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                // Network failure says nothing about the schema: retry next tick.
            }
        });
    }

    /**
     * Presence heartbeat: one touch on every cold start and every 60 s while at
     * least one Activity is started; stopped while the app is in the background.
     */
    public static void startPresenceHeartbeat(android.app.Application app) {
        final Handler handler = new Handler(Looper.getMainLooper());
        final int[] started = {0};
        final Runnable tick = new Runnable() {
            @Override
            public void run() {
                ChatRepository.getInstance(app).touchActive();
                handler.postDelayed(this, 60_000L);
            }
        };
        app.registerActivityLifecycleCallbacks(new android.app.Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(android.app.Activity a, Bundle b) {}
            @Override public void onActivityStarted(android.app.Activity a) {
                started[0]++;
                if (started[0] == 1) {
                    ChatRepository.getInstance(app).touchActive();
                    handler.removeCallbacks(tick);
                    handler.postDelayed(tick, 60_000L);
                }
            }
            @Override public void onActivityResumed(android.app.Activity a) {}
            @Override public void onActivityPaused(android.app.Activity a) {}
            @Override public void onActivityStopped(android.app.Activity a) {
                started[0] = Math.max(0, started[0] - 1);
                if (started[0] == 0) handler.removeCallbacks(tick);
            }
            @Override public void onActivitySaveInstanceState(android.app.Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(android.app.Activity a) {}
        });
    }
}
