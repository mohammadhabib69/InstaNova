package com.insta.nova.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.insta.nova.data.local.NotificationStore;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.model.Notification;
import com.insta.nova.data.model.Post;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Turns in-app activity into simulated incoming notifications.
 *
 * <p>The project has no second live user, so every actor is a demo account taken
 * from the feed where possible (real username and avatar, real post thumbnail).
 * The centre seeds a short history once per account and then reacts to the user's
 * own actions — liking, commenting and following — with a delayed "incoming"
 * event, which is what makes the badge and the New section move.
 */
public final class NotificationCenter {

    private static final long MIN_GAP_MS = 8_000L;
    private static final long EVENT_DELAY_MS = 1_400L;

    private static final String[] FALLBACK_ACTORS = {
            "qaverify", "anna.makes", "theo.frames"
    };

    private static final String[] REPLY_TEXTS = {
            "so good 🔥", "this deserves more likes", "wait, where is this?",
            "the colours here are unreal", "okay this is my new favourite"
    };

    private static NotificationCenter instance;

    private final Context appContext;
    private final NotificationStore store;
    private final String myUserId;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final List<Post> feed = new ArrayList<>();
    private long lastGeneratedAt;
    private int actorCursor;
    private int replyCursor;

    private NotificationCenter(Context context) {
        this.appContext = context.getApplicationContext();
        this.store = new NotificationStore(appContext);
        this.myUserId = SessionManager.getInstance(appContext).getUserId();
    }

    public static synchronized NotificationCenter get(Context context) {
        if (instance == null) {
            instance = new NotificationCenter(context);
        }
        return instance;
    }

    /** Keeps a copy of the visible feed: source of actors and post thumbnails. */
    public void onFeedLoaded(List<Post> posts) {
        feed.clear();
        if (posts != null) {
            for (Post post : posts) {
                if (post != null) feed.add(post);
            }
        }
        // Home publishes an empty list before the first network response; seeding
        // then would produce rows without avatars or thumbnails.
        if (!feed.isEmpty()) {
            seedIfNeeded();
        }
    }

    public Post findPost(String postId) {
        if (postId == null) return null;
        for (Post post : feed) {
            if (postId.equals(post.getId())) return post;
        }
        return null;
    }

    /** First run only: two or three events so the grouped feed is never empty. */
    public void seedIfNeeded() {
        if (store.isSeeded() || myUserId == null) return;
        store.markSeeded();

        long now = System.currentTimeMillis();
        Post first = feed.size() > 0 ? feed.get(0) : null;
        Post second = feed.size() > 1 ? feed.get(1) : first;

        Actor a = nextActor();
        Actor b = nextActor();
        Actor c = nextActor();

        Notification liked = build("like", a, first, null,
                now - TimeUnit.HOURS.toMillis(2), false);
        Notification followed = build("follow", b, null, null,
                now - TimeUnit.HOURS.toMillis(5), true);
        Notification commented = build("comment", c, second, REPLY_TEXTS[0],
                now - TimeUnit.DAYS.toMillis(3), true);

        store.add(liked);
        store.add(followed);
        store.add(commented);
    }

    /** The user liked a post: a demo account reacts to it shortly afterwards. */
    public void onPostLiked(Post post) {
        if (!canGenerate()) return;
        Post subject = post;
        if (subject == null && !feed.isEmpty()) subject = feed.get(0);
        Notification notification = build("like", nextActor(), subject, null,
                System.currentTimeMillis(), false);
        schedule(notification);
    }

    /** The user commented: a demo account answers on the same thread. */
    public void onCommentPosted(String postId, String commentText) {
        if (!canGenerate()) return;
        String reply = REPLY_TEXTS[replyCursor % REPLY_TEXTS.length];
        replyCursor++;
        Notification notification = build("comment", nextActor(), findPost(postId), reply,
                System.currentTimeMillis(), false);
        if (notification.getPostId() == null) notification.setPostId(postId);
        schedule(notification);
    }

    /** The user followed someone: that account follows back. */
    public void onFollowed(String userId, String username, String avatarUrl) {
        if (!canGenerate()) return;
        if (username == null || username.trim().isEmpty()) return;
        Actor actor = new Actor(userId, username, avatarUrl);
        Notification notification = build("follow", actor, null, null,
                System.currentTimeMillis(), false);
        schedule(notification);
    }

    private boolean canGenerate() {
        if (myUserId == null) return false;
        long now = System.currentTimeMillis();
        if (now - lastGeneratedAt < MIN_GAP_MS) return false;
        lastGeneratedAt = now;
        return true;
    }

    private void schedule(final Notification notification) {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                store.add(notification);
            }
        }, EVENT_DELAY_MS);
    }

    private Notification build(String type, Actor actor, Post post, String commentText,
                               long createdAt, boolean read) {
        Notification n = new Notification();
        n.setId(UUID.randomUUID().toString());
        n.setUserId(myUserId);
        n.setType(type);
        n.setActorId(actor.id);
        n.setActorUsername(actor.username);
        n.setActorAvatarUrl(actor.avatarUrl);
        n.setCommentText(commentText);
        n.setRead(read);
        n.setCreatedAt(String.valueOf(createdAt));
        if (post != null) {
            n.setPostId(post.getId());
            n.setPostThumbnailUrl(thumbnail(post));
        }
        return n;
    }

    private String thumbnail(Post post) {
        if (post == null) return null;
        String thumb = post.getThumbnailUrl();
        if (thumb != null && !thumb.trim().isEmpty()) return thumb;
        List<String> media = post.getMediaUrls();
        if (media != null && !media.isEmpty()) return media.get(0);
        return post.getFirstMediaUrl();
    }

    /**
     * Feed authors first (real avatars), then the fixed demo roster so three
     * distinct names are always available. The list is de-duplicated by username.
     */
    private Actor nextActor() {
        List<Actor> candidates = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (Post post : feed) {
            String id = post.getUserId();
            String username = post.getUsername();
            if (id == null || id.equals(myUserId)) continue;
            if (username == null || username.trim().isEmpty()) continue;
            if (names.add(username)) {
                candidates.add(new Actor(id, username, post.getAvatarUrl()));
            }
        }
        for (String name : FALLBACK_ACTORS) {
            if (names.add(name)) {
                candidates.add(new Actor(null, name, null));
            }
        }
        return candidates.get(Math.abs(actorCursor++) % candidates.size());
    }

    private static final class Actor {
        final String id;
        final String username;
        final String avatarUrl;

        Actor(String id, String username, String avatarUrl) {
            this.id = id;
            this.username = username;
            this.avatarUrl = avatarUrl;
        }
    }
}
