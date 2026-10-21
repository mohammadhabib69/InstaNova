package com.insta.nova.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.insta.nova.R;
import com.insta.nova.data.local.NotificationStore;
import com.insta.nova.data.model.Notification;
import com.insta.nova.data.model.Post;
import com.insta.nova.data.repository.NotificationCenter;
import com.insta.nova.databinding.ActivityNotificationsBinding;
import com.insta.nova.ui.post.PostDetailActivity;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Instagram-style activity feed.
 *
 * <p>Groups unseen activity under "New", then "Today" and "This week". Opening the
 * screen snapshots the unseen ids (so the New section and its dots stay stable for
 * this visit) and immediately marks everything read, which clears the top-bar badge.
 */
public class NotificationsActivity extends AppCompatActivity {

    private static final String STATE_NEW_IDS = "state_new_ids";

    private ActivityNotificationsBinding binding;
    private NotificationStore store;
    private NotificationFeedAdapter adapter;
    private final Set<String> newIds = new HashSet<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNotificationsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnBack.setOnClickListener(v -> finish());

        store = new NotificationStore(this);
        NotificationCenter.get(this).seedIfNeeded();

        if (savedInstanceState != null) {
            ArrayList<String> saved = savedInstanceState.getStringArrayList(STATE_NEW_IDS);
            if (saved != null) newIds.addAll(saved);
        } else {
            for (Notification n : store.getAll()) {
                if (n != null && !n.isRead() && n.getId() != null) newIds.add(n.getId());
            }
        }

        adapter = new NotificationFeedAdapter(new NotificationFeedAdapter.Listener() {
            @Override
            public void onFollowToggle(Notification notification) {
                boolean next = !notification.isActorFollowing();
                notification.setActorFollowing(next);
                store.setActorFollowing(notification.getId(), next);
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onRowClick(Notification notification) {
                openPost(notification);
            }
        });

        binding.rvNotifications.setLayoutManager(new LinearLayoutManager(this));
        binding.rvNotifications.setAdapter(adapter);

        render(store.getAll());

        // Seen as soon as the screen is opened: the heart badge clears.
        store.markAllRead();
    }

    private void render(List<Notification> items) {
        List<Notification> newest = new ArrayList<>();
        List<Notification> today = new ArrayList<>();
        List<Notification> week = new ArrayList<>();

        long now = System.currentTimeMillis();
        for (Notification n : items) {
            if (n == null) continue;
            long created = createdAt(n);
            if (n.getId() != null && newIds.contains(n.getId())) {
                newest.add(n);
            } else if (isToday(created, now)) {
                today.add(n);
            } else {
                // Anything older than a week stays visible here rather than vanishing.
                week.add(n);
            }
        }

        List<NotificationFeedAdapter.Row> rows = new ArrayList<>();
        appendSection(rows, getString(R.string.notifications_section_new), newest, true);
        appendSection(rows, getString(R.string.notifications_section_today), today, false);
        appendSection(rows, getString(R.string.notifications_section_week), week, false);

        binding.emptyNotifications.setVisibility(rows.isEmpty() ? android.view.View.VISIBLE
                : android.view.View.GONE);
        adapter.submit(rows);
    }

    private void appendSection(List<NotificationFeedAdapter.Row> rows, String title,
                               List<Notification> section, boolean unseen) {
        if (section.isEmpty()) return;
        rows.add(NotificationFeedAdapter.Row.header(title));
        for (Notification n : section) {
            rows.add(NotificationFeedAdapter.Row.item(n, unseen));
        }
    }

    private void openPost(Notification notification) {
        if (notification == null || notification.getPostId() == null) return;
        Post post = NotificationCenter.get(this).findPost(notification.getPostId());
        if (post == null) return;
        Intent intent = new Intent(this, PostDetailActivity.class);
        intent.putExtra("post", post);
        intent.putExtra("post_id", post.getId());
        startActivity(intent);
    }

    private static long createdAt(Notification n) {
        try {
            return Long.parseLong(n.getCreatedAt());
        } catch (Exception e) {
            return 0L;
        }
    }

    private static boolean isToday(long timestamp, long now) {
        if (timestamp <= 0L) return false;
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(timestamp);
        Calendar current = Calendar.getInstance();
        current.setTimeInMillis(now);
        return then.get(Calendar.YEAR) == current.get(Calendar.YEAR)
                && then.get(Calendar.DAY_OF_YEAR) == current.get(Calendar.DAY_OF_YEAR);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putStringArrayList(STATE_NEW_IDS, new ArrayList<>(newIds));
    }
}
