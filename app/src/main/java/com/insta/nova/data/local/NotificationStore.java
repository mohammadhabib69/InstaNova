package com.insta.nova.data.local;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.insta.nova.data.model.Notification;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Local notification feed persistence.
 *
 * <p>Same SharedPreferences + Gson approach as {@link RecentSearchStore}, scoped per
 * account. The unread counter is exposed as process-wide LiveData so the top-bar
 * badge follows changes without polling.
 */
public class NotificationStore {

    private static final String PREF_NAME = "insta_notifications_prefs";
    private static final String KEY_FEED = "feed_";
    private static final String KEY_SEEDED = "seeded_";
    private static final int MAX_ITEMS = 80;

    private static final MutableLiveData<Integer> UNREAD = new MutableLiveData<>(0);

    private final SharedPreferences prefs;
    private final Gson gson;
    private final String accountKey;

    public NotificationStore(Context context) {
        Context app = context.getApplicationContext();
        this.prefs = app.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
        SessionManager session = SessionManager.getInstance(app);
        String userId = session.getUserId();
        this.accountKey = userId == null || userId.trim().isEmpty() ? "local" : userId;
        publishUnread();
    }

    public static LiveData<Integer> unreadCount() {
        return UNREAD;
    }

    /** Instance access so callers that just created the store can observe it. */
    public LiveData<Integer> unreadCountLiveData() {
        return UNREAD;
    }

    private String feedKey() {
        return KEY_FEED + accountKey;
    }

    private String seededKey() {
        return KEY_SEEDED + accountKey;
    }

    public boolean isSeeded() {
        return prefs.getBoolean(seededKey(), false);
    }

    public void markSeeded() {
        prefs.edit().putBoolean(seededKey(), true).apply();
    }

    public List<Notification> getAll() {
        List<Notification> items = read();
        Collections.sort(items, new Comparator<Notification>() {
            @Override
            public int compare(Notification a, Notification b) {
                return Long.compare(createdAt(b), createdAt(a));
            }
        });
        return items;
    }

    public void add(Notification notification) {
        if (notification == null) return;
        List<Notification> items = read();
        items.add(notification);
        while (items.size() > MAX_ITEMS) {
            items.remove(0);
        }
        write(items);
        publishUnread();
    }

    public void markAllRead() {
        List<Notification> items = read();
        boolean changed = false;
        for (Notification n : items) {
            if (n != null && !n.isRead()) {
                n.setRead(true);
                changed = true;
            }
        }
        if (changed) {
            write(items);
        }
        publishUnread();
    }

    /** Persists the Follow/Following state shown on a follow row. */
    public void setActorFollowing(String notificationId, boolean following) {
        List<Notification> items = read();
        for (Notification n : items) {
            if (n != null && notificationId != null && notificationId.equals(n.getId())) {
                n.setActorFollowing(following);
            }
        }
        write(items);
    }

    public int countUnread() {
        int unread = 0;
        for (Notification n : read()) {
            if (n != null && !n.isRead()) unread++;
        }
        return unread;
    }

    public boolean isEmpty() {
        return read().isEmpty();
    }

    private void publishUnread() {
        UNREAD.setValue(countUnread());
    }

    private List<Notification> read() {
        String json = prefs.getString(feedKey(), null);
        if (json == null) return new ArrayList<>();
        try {
            Type type = new TypeToken<List<Notification>>() {}.getType();
            List<Notification> parsed = gson.fromJson(json, type);
            return parsed == null ? new ArrayList<>() : parsed;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void write(List<Notification> items) {
        prefs.edit().putString(feedKey(), gson.toJson(items)).apply();
    }

    private static long createdAt(Notification n) {
        if (n == null || n.getCreatedAt() == null) return 0L;
        try {
            return Long.parseLong(n.getCreatedAt());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
