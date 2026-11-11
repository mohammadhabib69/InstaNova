package com.insta.nova.hub;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.insta.nova.R;
import com.insta.nova.data.NovaDtos;
import com.insta.nova.databinding.ItemNovaPostBinding;
import com.insta.nova.ui.post.PostComposerOptionsSheet;
import com.insta.nova.util.GlideUtils;
import com.insta.nova.util.TimeUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * The Nova feed's post cards.
 *
 * <p>Each card carries the Reddit-style controls: an upvote and a downvote with
 * the score between them, the comment count and the save toggle. A post with an
 * expiry moment shows how long it has left and fades as that time runs out.
 *
 * <p>The fade is refreshed on a light timer (once every 30 seconds), not per
 * frame: a card only has to show the remaining time to the minute.
 */
public class NovaPostAdapter extends RecyclerView.Adapter<NovaPostAdapter.PostHolder> {

    /** How often the expiry labels and the fade are refreshed. */
    private static final long TICK_MS = 30_000L;

    public interface Listener {
        void onVote(NovaDtos.NovaPost post, int value);

        /** A poll option was tapped; the server owns the resulting counts. */
        void onPollVote(NovaDtos.NovaPost post, int optionIndex);
        void onComments(NovaDtos.NovaPost post);
        void onSave(NovaDtos.NovaPost post);
        void onOpen(NovaDtos.NovaPost post);
    }

    private final List<NovaDtos.NovaPost> items = new ArrayList<>();
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refreshExpiry();
            handler.postDelayed(this, TICK_MS);
        }
    };

    public NovaPostAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<NovaDtos.NovaPost> posts) {
        items.clear();
        if (posts != null) {
            for (NovaDtos.NovaPost post : posts) {
                if (post != null && post.id != null) items.add(post);
            }
        }
        notifyDataSetChanged();
        refreshExpiry();
    }

    public List<NovaDtos.NovaPost> items() {
        return new ArrayList<>(items);
    }

    /** Re-renders only the expiry text and the fade of the bound cards. */
    private void refreshExpiry() {
        long now = System.currentTimeMillis();
        for (int i = bound.size() - 1; i >= 0; i--) {
            PostHolder holder = bound.get(i);
            if (holder.postId == null) {
                bound.remove(i);
                continue;
            }
            NovaDtos.NovaPost post = byId(holder.postId);
            if (post != null) holder.paintExpiry(post, now);
        }
    }

    private NovaDtos.NovaPost byId(String id) {
        for (NovaDtos.NovaPost post : items) {
            if (post != null && id.equals(post.id)) return post;
        }
        return null;
    }

    /** Holds the bound views of the posts so the timer can repaint them. */
    private final List<PostHolder> bound = new ArrayList<>();

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, TICK_MS);
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        handler.removeCallbacks(tick);
        bound.clear();
        super.onDetachedFromRecyclerView(recyclerView);
    }

    @NonNull
    @Override
    public PostHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemNovaPostBinding binding = ItemNovaPostBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new PostHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull PostHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class PostHolder extends RecyclerView.ViewHolder {

        private final ItemNovaPostBinding binding;
        String postId;

        PostHolder(ItemNovaPostBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        @SuppressLint("SetTextI18n")
        /**
         * Draws the poll options under the media slot.
         *
         * <p>Rows are built here because the option count is variable and every row
         * carries its own result bar. Nothing is drawn unless there are at least two
         * options, so a malformed poll degrades to a plain post instead of an empty box.
         */
        private void bindPoll(NovaDtos.NovaPost post) {
            binding.pollBox.removeAllViews();

            java.util.List<String> options = post.pollOptions;
            if (options == null || options.size() < 2) {
                binding.pollBox.setVisibility(View.GONE);
                return;
            }
            binding.pollBox.setVisibility(View.VISIBLE);

            java.util.List<Integer> counts = post.pollVotes;
            int total = 0;
            for (int i = 0; i < options.size(); i++) {
                Integer c = counts != null && i < counts.size() ? counts.get(i) : null;
                if (c != null) total += c;
            }

            for (int i = 0; i < options.size(); i++) {
                Integer count = counts != null && i < counts.size() ? counts.get(i) : null;
                int n = count == null ? 0 : count;
                int percent = total == 0 ? 0 : Math.round(n * 100f / total);
                boolean mine = post.myPollVote != null && post.myPollVote == i;

                android.widget.TextView row = new android.widget.TextView(binding.pollBox.getContext());
                String label = options.get(i) == null ? "" : options.get(i);
                row.setText(total == 0
                        ? label
                        : label + "  ·  " + percent + "% (" + n + ")");
                row.setTextSize(13f);
                if (mine) row.setTextColor(accent());
                row.setBackgroundResource(mine
                        ? R.drawable.bg_nova_chip_off : android.R.color.transparent);
                int pad = Math.round(10 * binding.pollBox.getResources().getDisplayMetrics().density);
                row.setPadding(pad, pad, pad, pad);

                androidx.recyclerview.widget.RecyclerView.LayoutParams lp =
                        new androidx.recyclerview.widget.RecyclerView.LayoutParams(
                                androidx.recyclerview.widget.RecyclerView.LayoutParams.MATCH_PARENT,
                                androidx.recyclerview.widget.RecyclerView.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = Math.round(6 * binding.pollBox.getResources().getDisplayMetrics().density);
                row.setLayoutParams(lp);

                final int index = i;
                row.setOnClickListener(v -> {
                    if (listener != null) listener.onPollVote(post, index);
                });
                binding.pollBox.addView(row);
            }
        }

        void bind(NovaDtos.NovaPost post) {
            postId = post.id;
            if (!bound.contains(this)) bound.add(this);

            binding.tvNovaAlias.setText(post.alias == null || post.alias.trim().isEmpty()
                    ? "unknown" : post.alias);

            // An anonymous post shows the badge and the placeholder avatar; the
            // server never sends the real author id, so there is nothing else
            // to hide here.
            boolean anonymous = post.isAnonymous;
            binding.tvNovaAnonBadge.setVisibility(anonymous ? View.VISIBLE : View.GONE);
            binding.imgNovaAnonAvatar.setVisibility(anonymous ? View.VISIBLE : View.GONE);
            binding.imgNovaAvatar.setVisibility(anonymous ? View.GONE : View.VISIBLE);
            if (!anonymous) {
                GlideUtils.loadAvatar(binding.imgNovaAvatar, post.avatarUrl);
            }

            if (post.flair == null || post.flair.trim().isEmpty()) {
                binding.tvNovaFlair.setVisibility(View.GONE);
            } else {
                binding.tvNovaFlair.setVisibility(View.VISIBLE);
                binding.tvNovaFlair.setText(post.flair);
            }

            String body = post.body == null ? "" : post.body.trim();
            if (body.isEmpty()) {
                binding.tvNovaBody.setVisibility(View.GONE);
            } else {
                binding.tvNovaBody.setVisibility(View.VISIBLE);
                binding.tvNovaBody.setText(body);
            }

            if (post.mediaUrl == null || post.mediaUrl.trim().isEmpty()) {
                binding.imgNovaMedia.setVisibility(View.GONE);
            } else {
                binding.imgNovaMedia.setVisibility(View.VISIBLE);
                // A reel stores the mp4 here; the card is an ImageView, so load the
                // poster frame that sits next to it instead of a video URL that Glide
                // cannot decode (it used to render the broken-image placeholder).
                String media = post.mediaUrl;
                if (post.isReel() && media.toLowerCase().endsWith(".mp4")) {
                    media = media.substring(0, media.length() - 4) + ".jpg";
                }
                GlideUtils.load(binding.imgNovaMedia, "nova", media);
            }

            bindPoll(post);

            long now = System.currentTimeMillis();
            binding.tvNovaMeta.setText(metaLine(post));
            paintExpiry(post, now);

            binding.tvNovaScore.setText(String.valueOf(post.score));
            binding.tvNovaCommentCount.setText(String.valueOf(post.commentCount));
            paintVoteState(post);

            binding.btnNovaSave.setImageResource(post.isSaved
                    ? R.drawable.ic_bookmark_filled : R.drawable.ic_bookmark_outline);
            binding.btnNovaSave.setImageTintList(ColorStateList.valueOf(
                    post.isSaved ? accent() : secondary()));

            binding.btnNovaUpvote.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onVote(post, post.myVote == 1 ? 0 : 1);
                }
            });
            binding.btnNovaDownvote.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onVote(post, post.myVote == -1 ? 0 : -1);
                }
            });
            binding.btnNovaComments.setOnClickListener(v -> {
                if (listener != null) listener.onComments(post);
            });
            binding.btnNovaSave.setOnClickListener(v -> {
                if (listener != null) listener.onSave(post);
            });
            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) listener.onOpen(post);
            });
        }

        /** The author line: when it was posted and, when set, the flair. */
        private String metaLine(NovaDtos.NovaPost post) {
            String when = TimeUtils.formatRelativeTime(post.createdAtMillis());
            if (post.isMine) {
                return when + " · " + binding.getRoot().getContext()
                        .getString(R.string.nova_badge_mine);
            }
            return when;
        }

        /**
         * The expiry badge and the fade.
         *
         * <p>The opacity is clamp(remaining / total, 0.4, 1.0): a post that was
         * just published is fully opaque, and it dims towards the end of its
         * window.
         */
        void paintExpiry(NovaDtos.NovaPost post, long now) {
            String label = PostComposerOptionsSheet.expiryLabel(
                    binding.getRoot().getContext(), post.expiresAtMillis(), now);
            if (label == null) {
                binding.tvNovaExpiry.setVisibility(View.GONE);
                binding.getRoot().setAlpha(1f);
                return;
            }
            binding.tvNovaExpiry.setVisibility(View.VISIBLE);
            binding.tvNovaExpiry.setText(label);
            binding.getRoot().setAlpha(post.fadeAlpha(now));
        }

        private void paintVoteState(NovaDtos.NovaPost post) {
            binding.btnNovaUpvote.setImageTintList(ColorStateList.valueOf(
                    post.myVote == 1 ? accent() : secondary()));
            binding.btnNovaDownvote.setImageTintList(ColorStateList.valueOf(
                    post.myVote == -1 ? accent() : secondary()));
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
