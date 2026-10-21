package com.insta.nova.ui.activity;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.imageview.ShapeableImageView;
import com.insta.nova.R;
import com.insta.nova.data.model.Notification;
import com.insta.nova.databinding.ItemNotificationBinding;
import com.insta.nova.databinding.ItemNotificationHeaderBinding;
import com.insta.nova.util.GlideUtils;
import com.insta.nova.util.TimeUtils;
import java.util.ArrayList;
import java.util.List;

/**
 * Activity feed adapter: section headers plus rows.
 *
 * <p>Rows follow Instagram's sentence style — bold actor, plain action, grey
 * compact timestamp — with the related post thumbnail or a Follow/Following pill
 * on the right and a blue dot for unseen activity.
 */
public class NotificationFeedAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ROW = 1;

    public interface Listener {
        void onFollowToggle(Notification notification);

        void onRowClick(Notification notification);
    }

    /** A header or a row, so the whole grouped feed is one adapter. */
    public static final class Row {
        final int type;
        final String title;
        final Notification notification;
        final boolean unread;

        private Row(int type, String title, Notification notification, boolean unread) {
            this.type = type;
            this.title = title;
            this.notification = notification;
            this.unread = unread;
        }

        public static Row header(String title) {
            return new Row(TYPE_HEADER, title, null, false);
        }

        public static Row item(Notification notification, boolean unread) {
            return new Row(TYPE_ROW, null, notification, unread);
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private final Listener listener;

    public NotificationFeedAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<Row> newRows) {
        rows.clear();
        if (newRows != null) rows.addAll(newRows);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderHolder(ItemNotificationHeaderBinding.inflate(inflater, parent, false));
        }
        return new RowHolder(ItemNotificationBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);
        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).binding.tvSectionTitle.setText(row.title);
        } else if (holder instanceof RowHolder) {
            ((RowHolder) holder).bind(row, listener);
        }
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static final class HeaderHolder extends RecyclerView.ViewHolder {
        final ItemNotificationHeaderBinding binding;

        HeaderHolder(ItemNotificationHeaderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    static final class RowHolder extends RecyclerView.ViewHolder {
        final ItemNotificationBinding binding;

        RowHolder(ItemNotificationBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Row row, final Listener listener) {
            final Notification n = row.notification;
            Context context = binding.getRoot().getContext();
            if (n == null) return;

            GlideUtils.loadAvatar(binding.ivActorAvatar, n.getActorAvatarUrl());

            String type = n.getType() == null ? "like" : n.getType();
            String actor = n.getActorUsername() == null ? "" : n.getActorUsername();

            SpannableStringBuilder text = new SpannableStringBuilder();
            int actorStart = text.length();
            text.append(actor);
            text.setSpan(new StyleSpan(Typeface.BOLD), actorStart, text.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.append(" ");

            if ("follow".equals(type)) {
                text.append(context.getString(R.string.notification_followed));
            } else if ("comment".equals(type)) {
                text.append(context.getString(R.string.notification_commented));
                if (n.getCommentText() != null && !n.getCommentText().isEmpty()) {
                    text.append(" ").append(n.getCommentText());
                }
            } else {
                text.append(context.getString(R.string.notification_liked_post));
            }

            String age = TimeUtils.formatCompactRelativeTime(createdAt(n));
            if (!age.isEmpty()) {
                text.append("  ");
                int timeStart = text.length();
                text.append(age);
                text.setSpan(new ForegroundColorSpan(
                                ContextCompat.getColor(context, R.color.color_text_secondary)),
                        timeStart, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            binding.tvNotificationText.setText(text);

            boolean isFollow = "follow".equals(type);
            binding.btnFollow.setVisibility(isFollow ? View.VISIBLE : View.GONE);
            ShapeableImageView thumb = binding.ivPostThumb;
            if (!isFollow && n.getPostThumbnailUrl() != null
                    && !n.getPostThumbnailUrl().trim().isEmpty()) {
                thumb.setVisibility(View.VISIBLE);
                GlideUtils.load(thumb, "posts", n.getPostThumbnailUrl(),
                        R.drawable.ic_image, R.drawable.ic_image);
            } else {
                thumb.setVisibility(View.GONE);
            }

            if (isFollow) {
                boolean following = n.isActorFollowing();
                binding.btnFollow.setText(following
                        ? R.string.notification_following : R.string.notification_follow);
                binding.btnFollow.setBackgroundResource(following
                        ? R.drawable.bg_notification_following : R.drawable.bg_notification_follow);
                binding.btnFollow.setTextColor(ContextCompat.getColor(context,
                        following ? R.color.color_text_primary : R.color.white));
                binding.btnFollow.setOnClickListener(v -> {
                    if (listener != null) listener.onFollowToggle(n);
                });
            } else {
                binding.btnFollow.setOnClickListener(null);
            }

            binding.vUnreadDot.setVisibility(row.unread ? View.VISIBLE : View.GONE);
            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) listener.onRowClick(n);
            });
        }

        private long createdAt(Notification n) {
            try {
                return Long.parseLong(n.getCreatedAt());
            } catch (Exception e) {
                return 0L;
            }
        }
    }
}
