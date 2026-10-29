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
    private final com.insta.nova.util.NotificationPermission notificationPermission =
            new com.insta.nova.util.NotificationPermission();
    private NotificationFeedAdapter adapter;
    private final Set<String> newIds = new HashSet<>();

    /**
     * Cold entry from a notification makes this screen its own task root; {@code finish()} then
     * exited the app. Send the user to Home instead, which is where back would land normally.
     */
    private void goBack() {
        if (isTaskRoot()) {
            startActivity(new android.content.Intent(this, com.insta.nova.ui.main.MainActivity.class)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP));
        }
        finish();
    }

    @Override


    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // System back must take the same safe route as the header button.
        getOnBackPressedDispatcher().addCallback(this,
                new androidx.activity.OnBackPressedCallback(true) {
                    @Override public void handleOnBackPressed() { goBack(); }
                });
        binding = ActivityNotificationsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnBack.setOnClickListener(v -> goBack());

        binding.tvFollowRequests.setOnClickListener(v ->
                startActivity(new Intent(this, FollowRequestsActivity.class)));
        loadFollowRequestCount();

        notificationPermission.attach(this, this::bindNotificationPermissionState);
        store = new NotificationStore(this);

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
                openTarget(notification);
            }
        });

        binding.rvNotifications.setLayoutManager(new LinearLayoutManager(this));
        binding.rvNotifications.setAdapter(adapter);

        // Local rows first so the screen is never blank, then the rows the backend
        // really wrote (story reactions, follows) are merged in and it re-renders.
        render(store.getAll());
        NotificationCenter.get(this).syncFromServer(new Runnable() {
            @Override public void run() {
                store.markAllRead();
                render(store.getAll());
                NotificationCenter.get(NotificationsActivity.this).markAllReadOnServer();
            }
        });

        // Seen as soon as the screen is opened: the heart badge clears.
        store.markAllRead();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadFollowRequestCount();
        bindNotificationPermissionState();
    }

    /**
     * Tells the truth about delivery.
     *
     * <p>When POST_NOTIFICATIONS is denied, NotificationManagerCompat.notify() silently
     * does nothing — no exception, no log, no badge. The screen would otherwise look
     * completely healthy while every push on this device is being dropped, so say it
     * here and give one tap that either re-asks or opens the system settings.
     */
    private void bindNotificationPermissionState() {
        boolean enabled = notificationPermission.isEnabled(this);
        binding.boxNotificationsOff.setVisibility(enabled ? android.view.View.GONE
                : android.view.View.VISIBLE);
        binding.btnEnableNotifications.setOnClickListener(v -> {
            if (!notificationPermission.requestIfPossible(this)) {
                notificationPermission.openSystemSettings(this);
            }
        });
    }

    private void loadFollowRequestCount() {
        new com.insta.nova.data.repository.RelationshipRepository(this)
                .loadFollowRequests().observe(this, resource -> {
                    if (resource == null || resource.status
                            != com.insta.nova.util.Resource.Status.SUCCESS
                            || resource.data == null) return;
                    int count = resource.data.size();
                    if (count <= 0) {
                        binding.tvFollowRequests.setVisibility(android.view.View.GONE);
                    } else {
                        binding.tvFollowRequests.setVisibility(android.view.View.VISIBLE);
                        binding.tvFollowRequests.setText(getString(R.string.st_follow_requests)
                                + " (" + count + ")");
                    }
                });
    }

    private void render(List<Notification> items) {
        List<Notification> newest = new ArrayList<>();
        List<Notification> today = new ArrayList<>();
        List<Notification> week = new ArrayList<>();

        com.insta.nova.data.local.AppSettingsStore settings =
                new com.insta.nova.data.local.AppSettingsStore(this);
        long now = System.currentTimeMillis();
        for (Notification n : items) {
            if (n == null) continue;
            if (!settings.allowsNotificationType(n.getType())) continue;
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

    /**
     * A row opens whatever it is about: a follow notification opens the actor's profile,
     * everything else opens the post. A post that is no longer in the local mirror still
     * opens by id, because the detail screen re-reads it from the server.
     */
    private void openTarget(Notification notification) {
        if (notification == null) return;
        String type = notification.getType() == null ? "" : notification.getType().toLowerCase(java.util.Locale.US);
        if (type.startsWith("follow")) {
            String actorId = notification.getActorId();
            String actorName = notification.getActorUsername();
            if (actorId != null && !actorId.trim().isEmpty()) {
                openProfile(actorId, actorName);
                return;
            }
            // Rows written by the backend trigger can carry only the username, so the
            // profile is resolved from it instead of the tap doing nothing.
            if (actorName != null && !actorName.trim().isEmpty()) {
                new com.insta.nova.data.repository.UserProfileRepository(this)
                        .getProfileByUsername(actorName)
                        .observe(this, resource -> {
                            if (resource != null
                                    && resource.status == com.insta.nova.util.Resource.Status.SUCCESS
                                    && resource.data != null && resource.data.getId() != null) {
                                openProfile(resource.data.getId(), actorName);
                            }
                        });
                return;
            }
        }
        openPost(notification);
    }

    private void openProfile(String userId, String username) {
        Intent profile = new Intent(this, com.insta.nova.ui.chat.UserProfileActivity.class);
        profile.putExtra(com.insta.nova.ui.chat.UserProfileActivity.EXTRA_USER_ID, userId);
        profile.putExtra(com.insta.nova.ui.chat.UserProfileActivity.EXTRA_USERNAME, username);
        startActivity(profile);
    }

    private void openPost(Notification notification) {
        if (notification == null || notification.getPostId() == null) return;
        Post post = NotificationCenter.get(this).findPost(notification.getPostId());
        Intent intent = new Intent(this, PostDetailActivity.class);
        if (post != null) {
            intent.putExtra("post", post);
            intent.putExtra("post_id", post.getId());
        } else {
            intent.putExtra("post_id", notification.getPostId());
        }
        startActivity(intent);
    }

    private static long createdAt(Notification n) {
        return n == null ? 0L : n.createdAtMillis();
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
