package com.insta.nova.hub;

import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.insta.nova.R;
import com.insta.nova.data.NovaDtos;
import com.insta.nova.databinding.ItemNovaCommentBinding;
import com.insta.nova.util.GlideUtils;
import com.insta.nova.util.TimeUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The threaded comment list.
 *
 * <p>Replies hang under their parent, a thread can be collapsed, every comment
 * can be voted on and a comment can be written anonymously — the server then
 * masks the author exactly like it does for a post.
 */
public class NovaCommentAdapter extends RecyclerView.Adapter<NovaCommentAdapter.CommentHolder> {

    /** How deep the indentation goes before it stops growing. */
    private static final int MAX_DEPTH = 3;

    public interface Listener {
        void onVote(NovaDtos.NovaComment comment, int value);
        void onReply(NovaDtos.NovaComment comment);
        /** Owner only: release a comment held because its author is restricted. */
        void onApprove(NovaDtos.NovaComment comment);
    }

    private final List<NovaDtos.NovaComment> all = new ArrayList<>();
    private final List<Row> visible = new ArrayList<>();
    private final Set<String> collapsed = new HashSet<>();
    private final Listener listener;

    /** True when the signed-in account wrote the post this thread belongs to. */
    private boolean canModerate;

    public void setCanModerate(boolean canModerate) {
        this.canModerate = canModerate;
        notifyDataSetChanged();
    }

    private static final class Row {
        final NovaDtos.NovaComment comment;
        final int depth;
        final int replyCount;

        Row(NovaDtos.NovaComment comment, int depth, int replyCount) {
            this.comment = comment;
            this.depth = depth;
            this.replyCount = replyCount;
        }
    }

    public NovaCommentAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<NovaDtos.NovaComment> comments) {
        all.clear();
        if (comments != null) {
            for (NovaDtos.NovaComment comment : comments) {
                if (comment != null && comment.id != null) all.add(comment);
            }
        }
        rebuild();
    }

    public void replace(NovaDtos.NovaComment comment) {
        if (comment == null || comment.id == null) return;
        for (int i = 0; i < all.size(); i++) {
            if (comment.id.equals(all.get(i).id)) {
                all.set(i, comment);
                rebuild();
                return;
            }
        }
    }

    /** Rebuilds the visible rows: roots first, replies under their parent. */
    private void rebuild() {
        visible.clear();

        Map<String, List<NovaDtos.NovaComment>> byParent = new HashMap<>();
        Set<String> ids = new HashSet<>();
        for (NovaDtos.NovaComment comment : all) {
            ids.add(comment.id);
        }
        for (NovaDtos.NovaComment comment : all) {
            String parent = comment.parentId;
            String key = (parent == null || parent.trim().isEmpty() || !ids.contains(parent))
                    ? "" : parent;
            List<NovaDtos.NovaComment> bucket = byParent.get(key);
            if (bucket == null) {
                bucket = new ArrayList<>();
                byParent.put(key, bucket);
            }
            bucket.add(comment);
        }

        // byParent has no "" entry at all when the thread is empty, or when
        // every comment is a reply, so this must be null-checked: a post with
        // no comments used to crash the detail screen here.
        List<NovaDtos.NovaComment> roots = byParent.get("");
        if (roots != null) {
            for (NovaDtos.NovaComment root : roots) {
                append(root, 0, byParent);
            }
        }
        notifyDataSetChanged();
    }

    private void append(NovaDtos.NovaComment comment, int depth,
                        Map<String, List<NovaDtos.NovaComment>> byParent) {
        List<NovaDtos.NovaComment> replies = byParent.get(comment.id);
        int count = replies == null ? 0 : replies.size();
        visible.add(new Row(comment, depth, count));
        if (count == 0 || collapsed.contains(comment.id)) return;
        for (NovaDtos.NovaComment reply : replies) {
            append(reply, Math.min(depth + 1, MAX_DEPTH), byParent);
        }
    }

    @NonNull
    @Override
    public CommentHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new CommentHolder(ItemNovaCommentBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull CommentHolder holder, int position) {
        holder.bind(visible.get(position));
    }

    @Override
    public int getItemCount() {
        return visible.size();
    }

    class CommentHolder extends RecyclerView.ViewHolder {

        private final ItemNovaCommentBinding binding;

        CommentHolder(ItemNovaCommentBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Row row) {
            final NovaDtos.NovaComment comment = row.comment;

            binding.tvNovaCommentAlias.setText(comment.alias == null
                    || comment.alias.trim().isEmpty() ? "unknown" : comment.alias);

            boolean anonymous = comment.isAnonymous;
            binding.tvNovaCommentAnonBadge.setVisibility(anonymous ? View.VISIBLE : View.GONE);
            binding.imgNovaCommentAnonAvatar.setVisibility(anonymous ? View.VISIBLE : View.GONE);
            binding.imgNovaCommentAvatar.setVisibility(anonymous ? View.GONE : View.VISIBLE);
            if (!anonymous) {
                GlideUtils.loadAvatar(binding.imgNovaCommentAvatar, comment.avatarUrl);
            }

            String when = TimeUtils.formatRelativeTime(comment.createdAtMillis());
            if (comment.isHeld) {
                // This comment is only readable here because it is mine or because I own
                // the post. The label says which, and offers the release action to the
                // owner — the server rejects it for anybody else.
                binding.tvNovaCommentTime.setText(canModerate
                        ? when + " · Held · Tap to approve"
                        : when + " · Held for review");
                binding.tvNovaCommentTime.setTextColor(accent());
                binding.tvNovaCommentTime.setOnClickListener(v -> {
                    if (canModerate && listener != null) listener.onApprove(comment);
                });
            } else {
                binding.tvNovaCommentTime.setText(when);
                binding.tvNovaCommentTime.setTextColor(secondary());
                binding.tvNovaCommentTime.setOnClickListener(null);
                binding.tvNovaCommentTime.setClickable(false);
            }
            binding.tvNovaCommentBody.setText(comment.body == null ? "" : comment.body);

            // The indentation marks the reply level. Inside a RecyclerView the
            // row's params are RecyclerView.LayoutParams, not
            // LinearLayout.LayoutParams, so the margin must be applied through
            // MarginLayoutParams or the bind throws ClassCastException.
            ViewGroup.LayoutParams current = binding.getRoot().getLayoutParams();
            ViewGroup.MarginLayoutParams params =
                    current instanceof ViewGroup.MarginLayoutParams
                            ? (ViewGroup.MarginLayoutParams) current
                            : new ViewGroup.MarginLayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT);
            params.leftMargin = dp(16 + row.depth * 22);
            binding.getRoot().setLayoutParams(params);

            binding.tvNovaCommentScore.setText(String.valueOf(comment.score));
            binding.btnNovaCommentUpvote.setImageTintList(ColorStateList.valueOf(
                    comment.myVote == 1 ? accent() : secondary()));
            binding.btnNovaCommentDownvote.setImageTintList(ColorStateList.valueOf(
                    comment.myVote == -1 ? accent() : secondary()));

            binding.btnNovaCommentUpvote.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onVote(comment, comment.myVote == 1 ? 0 : 1);
                }
            });
            binding.btnNovaCommentDownvote.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onVote(comment, comment.myVote == -1 ? 0 : -1);
                }
            });
            binding.btnNovaCommentReply.setOnClickListener(v -> {
                if (listener != null) listener.onReply(comment);
            });

            if (row.replyCount > 0) {
                binding.btnNovaCommentToggle.setVisibility(View.VISIBLE);
                boolean isCollapsed = collapsed.contains(comment.id);
                binding.btnNovaCommentToggle.setText(isCollapsed
                        ? binding.getRoot().getContext()
                                .getString(R.string.nova_comments_expand, row.replyCount)
                        : binding.getRoot().getContext()
                                .getString(R.string.nova_comments_collapse));
                binding.btnNovaCommentToggle.setOnClickListener(v -> {
                    if (collapsed.contains(comment.id)) {
                        collapsed.remove(comment.id);
                    } else {
                        collapsed.add(comment.id);
                    }
                    rebuild();
                });
            } else {
                binding.btnNovaCommentToggle.setVisibility(View.GONE);
            }
        }

        private int dp(int value) {
            return Math.round(value * binding.getRoot()
                    .getResources().getDisplayMetrics().density);
        }

        private int accent() {
            return resolve(R.attr.themePrimary, R.color.color_primary);
        }

        private int secondary() {
            return resolve(R.attr.themeTextSecondary, android.graphics.Color.GRAY);
        }

        private int resolve(int attr, int fallbackRes) {
            TypedValue value = new TypedValue();
            android.content.Context context = binding.getRoot().getContext();
            if (context.getTheme().resolveAttribute(attr, value, true)) {
                if (value.resourceId != 0) {
                    return ContextCompat.getColor(context, value.resourceId);
                }
                return value.data;
            }
            return ContextCompat.getColor(context, fallbackRes);
        }
    }
}
