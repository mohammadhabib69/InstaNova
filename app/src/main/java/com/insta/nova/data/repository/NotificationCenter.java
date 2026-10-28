package com.insta.nova.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.insta.nova.data.local.NotificationStore;
import com.insta.nova.data.local.RelationshipStore;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.model.Notification;
import com.insta.nova.data.model.Post;
import com.insta.nova.data.remote.RetrofitClient;
import com.insta.nova.data.remote.api.RestApi;
import com.insta.nova.data.remote.dto.NotificationDto;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The read side of {@code public.notifications}.
 *
 * <p>Every row on the notifications screen comes from the backend. This class used to
 * seed a fake history and then invent an "incoming" event whenever the user liked,
 * commented or followed, which is why the list looked random and showed activity about
 * the user's own actions. Now:
 *
 * <ul>
 *   <li>nothing is generated locally — an empty table means an empty screen;</li>
 *   <li>a server sync replaces the cached list, so rows produced by the old generator
 *       disappear on the first open after installing this build;</li>
 *   <li>rows whose actor is the signed-in user are dropped (a like on my own post can
 *       never notify me), and so are rows from accounts I have restricted.</li>
 * </ul>
 */
public final class NotificationCenter {

    private static final int PAGE_SIZE = 60;

    private static NotificationCenter instance;

    private final Context appContext;
    private final NotificationStore store;
    private final RelationshipStore relationships;
    private final String myUserId;
    private final String myUsername;
    private final RestApi restApi;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final List<Post> feed = new ArrayList<>();

    private NotificationCenter(Context context) {
        this.appContext = context.getApplicationContext();
        this.store = new NotificationStore(appContext);
        this.relationships = new RelationshipStore(appContext);
        SessionManager session = SessionManager.getInstance(appContext);
        this.myUserId = session.getUserId();
        this.myUsername = session.getUsername();
        this.restApi = RetrofitClient.getInstance(appContext).getRestApi();
    }

    public static synchronized NotificationCenter get(Context context) {
        if (instance == null) {
            instance = new NotificationCenter(context);
        }
        return instance;
    }

    /**
     * Pulls the rows the backend wrote for this account and makes them the whole list.
     *
     * <p>Replacing rather than merging is deliberate: merging could never remove a row,
     * so the invented rows from an older build would survive forever. A failed request
     * leaves the previous (real) list untouched instead of blanking the screen.
     */
    public void syncFromServer(final Runnable onDone) {
        if (myUserId == null || myUserId.isEmpty()) {
            if (onDone != null) onDone.run();
            return;
        }
        Map<String, String> query = new HashMap<>();
        query.put("user_id", "eq." + myUserId);
        query.put("order", "created_at.desc");
        query.put("limit", String.valueOf(PAGE_SIZE));
        restApi.getNotifications(query).enqueue(new retrofit2.Callback<List<NotificationDto>>() {
            @Override
            public void onResponse(retrofit2.Call<List<NotificationDto>> call,
                                   retrofit2.Response<List<NotificationDto>> response) {
                List<NotificationDto> rows = response.body();
                if (!response.isSuccessful() || rows == null) {
                    finish(onDone);
                    return;
                }
                resolveActorsAndMerge(rows, onDone);
            }

            @Override
            public void onFailure(retrofit2.Call<List<NotificationDto>> call, Throwable t) {
                finish(onDone);
            }
        });
    }

    /** The base table stores actor_id only, so names and avatars come from profiles. */
    private void resolveActorsAndMerge(List<NotificationDto> rows, final Runnable onDone) {
        final Set<String> actorIds = new HashSet<>();
        for (NotificationDto row : rows) {
            if (row != null && row.getActorId() != null && !row.getActorId().isEmpty()) {
                actorIds.add(row.getActorId());
            }
        }
        if (actorIds.isEmpty()) {
            merge(rows, new HashMap<String, Map<String, Object>>());
            finish(onDone);
            return;
        }
        StringBuilder in = new StringBuilder();
        for (String id : actorIds) {
            if (in.length() > 0) in.append(',');
            in.append(id);
        }
        Map<String, String> query = new HashMap<>();
        query.put("id", "in.(" + in + ")");
        query.put("select", "id,username,avatar_url");
        restApi.getProfilesRaw(query).enqueue(
                new retrofit2.Callback<List<Map<String, Object>>>() {
                    @Override
                    public void onResponse(retrofit2.Call<List<Map<String, Object>>> call,
                                           retrofit2.Response<List<Map<String, Object>>> response) {
                        Map<String, Map<String, Object>> byId = new HashMap<>();
                        List<Map<String, Object>> profiles = response.body();
                        if (response.isSuccessful() && profiles != null) {
                            for (Map<String, Object> profile : profiles) {
                                Object id = profile.get("id");
                                if (id != null) byId.put(String.valueOf(id), profile);
                            }
                        }
                        merge(rows, byId);
                        finish(onDone);
                    }

                    @Override
                    public void onFailure(retrofit2.Call<List<Map<String, Object>>> call, Throwable t) {
                        merge(rows, new HashMap<String, Map<String, Object>>());
                        finish(onDone);
                    }
                });
    }

    private void merge(List<NotificationDto> rows, Map<String, Map<String, Object>> profilesById) {
        final Set<String> restricted = relationships.restricted();
        List<Notification> mapped = new ArrayList<>();
        for (NotificationDto row : rows) {
            if (row == null || row.getId() == null) continue;

            Notification n = new Notification();
            n.setId(row.getId());
            n.setUserId(row.getUserId());
            n.setType(row.getType());
            n.setPostId(row.getPostId());
            n.setRead(row.isRead());
            n.setCreatedAt(row.getCreatedAt());
            // getText() already prefers `body`, which is where a reaction emoji lands.
            n.setCommentText(row.getText());
            n.setActorId(row.getActorId());
            n.setActorUsername(row.getActorUsername());
            n.setActorAvatarUrl(row.getActorAvatarUrl());

            Map<String, Object> profile = row.getActorId() == null
                    ? null : profilesById.get(row.getActorId());
            if (profile != null) {
                Object name = profile.get("username");
                Object avatar = profile.get("avatar_url");
                if (n.getActorUsername() == null && name != null) {
                    n.setActorUsername(String.valueOf(name));
                }
                if (n.getActorAvatarUrl() == null && avatar != null) {
                    n.setActorAvatarUrl(String.valueOf(avatar));
                }
            }

            if (isMyOwnAction(n, restricted)) continue;
            mapped.add(n);
        }
        store.replaceAll(mapped);
    }

    /**
     * True for rows that must never reach this screen: my own actions, and activity from
     * an account I have restricted (a restricted person's like or comment is not shown).
     */
    private boolean isMyOwnAction(Notification n, Set<String> restricted) {
        String actorId = n.getActorId();
        if (myUserId != null && actorId != null && myUserId.equals(actorId)) return true;
        if (actorId == null && myUsername != null && n.getActorUsername() != null
                && myUsername.equalsIgnoreCase(n.getActorUsername())) {
            return true;
        }
        return actorId != null && restricted.contains(actorId);
    }

    /**
     * Marks this account's unread rows as read on the server, so the badge stays cleared
     * after the screen is closed. Fire-and-forget: a failure only costs a badge.
     */
    public void markAllReadOnServer() {
        if (myUserId == null || myUserId.isEmpty()) return;
        Map<String, Object> body = new HashMap<>();
        body.put("read", true);
        restApi.markNotificationsRead("eq." + myUserId, "eq.false", body)
                .enqueue(new retrofit2.Callback<Void>() {
                    @Override public void onResponse(retrofit2.Call<Void> call,
                                                     retrofit2.Response<Void> response) { }

                    @Override public void onFailure(retrofit2.Call<Void> call, Throwable t) { }
                });
    }

    private void finish(final Runnable onDone) {
        if (onDone == null) return;
        handler.post(onDone);
    }

    /** Keeps a copy of the visible feed so a tapped row can open its post directly. */
    public void onFeedLoaded(List<Post> posts) {
        feed.clear();
        if (posts != null) {
            for (Post post : posts) {
                if (post != null) feed.add(post);
            }
        }
    }

    public Post findPost(String postId) {
        if (postId == null) return null;
        for (Post post : feed) {
            if (postId.equals(post.getId())) return post;
        }
        return null;
    }
}
