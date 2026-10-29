package com.insta.nova.ui.home;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import com.bumptech.glide.Glide;
import com.insta.nova.R;
import com.insta.nova.data.model.Post;
import com.insta.nova.databinding.ItemFeedPostBinding;
import com.insta.nova.util.Constants;
import com.insta.nova.util.GlideUtils;
import com.insta.nova.util.NumberFormat;
import com.insta.nova.util.Plural;
import com.insta.nova.util.SpanUtils;
import com.insta.nova.util.TimeUtils;
import com.insta.nova.util.ToastUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Recycler adapter for Main Home Feed posts (single image, carousels, reels preview).
 */
public class FeedPostAdapter extends ListAdapter<Post, FeedPostAdapter.ViewHolder> {

    public interface OnPostInteractionListener {
        void onLikeClick(Post post, int position);
        void onCommentClick(Post post);
        void onShareClick(Post post);

        void onRepostClick(Post post);

        void onLikesClick(Post post);
        void onSaveClick(Post post, int position);
        void onUserClick(String userId, String username);
        void onOptionsClick(Post post, int position);
        void onTagClick(String tag);
    }

    private static boolean isAudioMuted = true;
    private final OnPostInteractionListener listener;
    /** Tag markers per post id; empty list = fetched and there are none. */
    private final java.util.Map<String, List<com.insta.nova.data.remote.dto.PostTagDto>> tagCache =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<String> tagRequests = java.util.Collections.synchronizedSet(
            new java.util.HashSet<>());
    private com.insta.nova.data.remote.api.RestApi tagApi;

    public FeedPostAdapter(OnPostInteractionListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<Post> DIFF_CALLBACK = new DiffUtil.ItemCallback<Post>() {
        @Override
        public boolean areItemsTheSame(@NonNull Post oldItem, @NonNull Post newItem) {
            return oldItem != null && newItem != null && Objects.equals(oldItem.getId(), newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Post oldItem, @NonNull Post newItem) {
            return Objects.equals(oldItem, newItem)
                    && Objects.equals(oldItem.getMediaUrls(), newItem.getMediaUrls())
                    && Objects.equals(oldItem.getThumbnailUrl(), newItem.getThumbnailUrl())
                    && Objects.equals(oldItem.getAvatarUrl(), newItem.getAvatarUrl())
                    && oldItem.isLikedByMe() == newItem.isLikedByMe()
                    && oldItem.isSavedByMe() == newItem.isSavedByMe()
                    && oldItem.getLikesCount() == newItem.getLikesCount()
                    && oldItem.getCommentsCount() == newItem.getCommentsCount();
        }
    };

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemFeedPostBinding binding = ItemFeedPostBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Post post = getItem(position);
        holder.bind(post, position, listener, this);
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        holder.clearCarouselCallback();
        super.onViewRecycled(holder);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemFeedPostBinding binding;
        private ViewPager2.OnPageChangeCallback carouselPageChangeCallback;

        ViewHolder(ItemFeedPostBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        /**
         * Draws the post's tag markers and toggles them on tap.
         *
         * <p>Instagram keeps tags hidden until the photo is tapped, so the markers are built
         * once and kept in the overlay; the tap only flips its visibility. Coordinates come
         * straight from {@code post_tags.x/y} (0..1) and are scaled to the media bounds the
         * first time the overlay is laid out, which is also what makes them survive a resize.
         */
        private void bindTags(ItemFeedPostBinding binding, Post post,
                              FeedPostAdapter adapter, OnPostInteractionListener listener) {
            binding.layoutTags.removeAllViews();
            binding.layoutTags.setVisibility(View.INVISIBLE);
            final String postId = post.getId();
            if (postId == null || postId.startsWith("local_") || postId.startsWith("repost_")) {
                binding.ivSingleMedia.setOnClickListener(null);
                return;
            }

            List<com.insta.nova.data.remote.dto.PostTagDto> tags = adapter.tagCache.get(postId);
            if (tags != null) {
                paintTags(binding, tags, listener);
            } else {
                fetchTags(postId, binding, adapter, listener);
            }

            binding.ivSingleMedia.setOnClickListener(v -> {
                List<com.insta.nova.data.remote.dto.PostTagDto> current =
                        adapter.tagCache.get(postId);
                if (current == null || current.isEmpty()) return;
                binding.layoutTags.setVisibility(
                        binding.layoutTags.getVisibility() == View.VISIBLE
                                ? View.INVISIBLE : View.VISIBLE);
            });
        }

        private void fetchTags(String postId, ItemFeedPostBinding binding,
                               FeedPostAdapter adapter, OnPostInteractionListener listener) {
            if (!adapter.tagRequests.add(postId)) return;
            if (adapter.tagApi == null) {
                adapter.tagApi = com.insta.nova.data.remote.RetrofitClient
                        .getInstance(binding.getRoot().getContext()).getRestApi();
            }
            java.util.Map<String, String> query = new java.util.HashMap<>();
            query.put("select", "user_id,x,y,profiles(username)");
            query.put("post_id", "eq." + postId);
            adapter.tagApi.getPostTagMarkers(query).enqueue(
                    new retrofit2.Callback<List<com.insta.nova.data.remote.dto.PostTagDto>>() {
                @Override public void onResponse(
                        retrofit2.Call<List<com.insta.nova.data.remote.dto.PostTagDto>> call,
                        retrofit2.Response<List<com.insta.nova.data.remote.dto.PostTagDto>> response) {
                    List<com.insta.nova.data.remote.dto.PostTagDto> rows =
                            response.isSuccessful() && response.body() != null
                                    ? response.body()
                                    : new ArrayList<>();
                    adapter.tagCache.put(postId, rows);
                    if (!rows.isEmpty()) paintTags(binding, rows, listener);
                }

                @Override public void onFailure(
                        retrofit2.Call<List<com.insta.nova.data.remote.dto.PostTagDto>> call,
                        Throwable t) {
                    adapter.tagRequests.remove(postId);
                }
            });
        }

        /** Places one pill per tag at its stored position inside the media box. */
        private void paintTags(ItemFeedPostBinding binding,
                               List<com.insta.nova.data.remote.dto.PostTagDto> tags,
                               OnPostInteractionListener listener) {
            binding.layoutTags.removeAllViews();
            if (tags.isEmpty()) return;
            final float density = binding.getRoot().getResources().getDisplayMetrics().density;
            for (com.insta.nova.data.remote.dto.PostTagDto tag : tags) {
                android.widget.TextView pill = new android.widget.TextView(
                        binding.getRoot().getContext());
                String name = tag.username();
                pill.setText("@" + (name.isEmpty() ? "tagged" : name));
                pill.setTextColor(android.graphics.Color.WHITE);
                pill.setTextSize(11);
                pill.setAlpha(0.95f);
                android.graphics.drawable.GradientDrawable bg =
                        new android.graphics.drawable.GradientDrawable();
                bg.setColor(0x99000000);
                bg.setCornerRadius(10 * density);
                pill.setBackground(bg);
                pill.setPadding((int) (8 * density), (int) (3 * density),
                        (int) (8 * density), (int) (3 * density));
                final String taggedName = name;
                pill.setOnClickListener(v -> {
                    if (listener != null) listener.onUserClick(tag.userId, taggedName);
                });
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                float x = Math.max(0f, Math.min(1f, tag.x));
                float y = Math.max(0f, Math.min(1f, tag.y));
                binding.layoutTags.addView(pill, lp);
                final FrameLayout.LayoutParams placed = lp;
                binding.layoutTags.post(() -> {
                    int w = binding.layoutTags.getWidth();
                    int h = binding.layoutTags.getHeight();
                    placed.leftMargin = Math.round(x * w - pill.getWidth() / 2f);
                    placed.topMargin = Math.round(y * h - pill.getHeight() / 2f);
                    pill.setLayoutParams(placed);
                });
            }
        }

        void bind(Post post, int position, OnPostInteractionListener listener, FeedPostAdapter adapter) {
            Context context = binding.getRoot().getContext();
            binding.getRoot().setVisibility(View.VISIBLE);
            clearCarouselCallback();
            binding.viewPagerCarousel.setAdapter(null);

            if (post == null) {
                binding.getRoot().setVisibility(View.GONE);
                return;
            }

            // 1. Author info
            binding.tvUsername.setText(post.getUsername() == null ? "" : post.getUsername());
            binding.badgeVerified.setVerified(post.isVerified());

            if (post.getLocation() != null && !post.getLocation().isEmpty()) {
                binding.tvLocation.setText(post.getLocation());
                binding.tvLocation.setVisibility(View.VISIBLE);
            } else {
                binding.tvLocation.setVisibility(View.GONE);
            }

            com.insta.nova.util.GlideUtils.loadAvatar(binding.ivAvatar, post.getAvatarUrl());

            View.OnClickListener userClickListener = v -> {
                if (listener != null) listener.onUserClick(post.getUserId(), post.getUsername());
            };
            binding.ivAvatar.setOnClickListener(userClickListener);
            binding.layoutAuthor.setOnClickListener(userClickListener);

            // 2. Media handling (Carousel vs Single Media)
            List<String> mediaUrls = validMediaUrls(post.getMediaUrls());
            if (mediaUrls != null && mediaUrls.size() > 1) {
                // Multi-image carousel
                binding.ivSingleMedia.setVisibility(View.GONE);
                binding.viewPagerCarousel.setVisibility(View.VISIBLE);
                binding.ivCarouselBadge.setVisibility(View.VISIBLE);
                binding.dotsIndicator.setVisibility(View.VISIBLE);
                binding.dotsIndicator.setCount(mediaUrls.size());

                final int pageCount = mediaUrls.size();
                binding.ivCarouselBadge.setText(context.getString(R.string.carousel_position, 1, pageCount));

                CarouselPagerAdapter carouselAdapter = new CarouselPagerAdapter(mediaUrls);
                binding.viewPagerCarousel.setAdapter(carouselAdapter);
                carouselPageChangeCallback = new ViewPager2.OnPageChangeCallback() {
                    @Override
                    public void onPageSelected(int pagePos) {
                        binding.dotsIndicator.setSelectedPosition(pagePos);
                        binding.ivCarouselBadge.setText(
                                context.getString(R.string.carousel_position, pagePos + 1, pageCount));
                    }
                };
                binding.viewPagerCarousel.registerOnPageChangeCallback(carouselPageChangeCallback);
            } else {
                // Single image or reel preview
                binding.ivSingleMedia.setVisibility(View.VISIBLE);
                binding.viewPagerCarousel.setVisibility(View.GONE);
                binding.ivCarouselBadge.setVisibility(View.GONE);
                binding.dotsIndicator.setVisibility(View.GONE);

                String singleUrl = (mediaUrls != null && !mediaUrls.isEmpty()) ? mediaUrls.get(0) : post.getThumbnailUrl();
                com.insta.nova.util.GlideUtils.load(binding.ivSingleMedia, "posts", singleUrl);
            }

            // 2b. Tag markers: fetched once per post, drawn on tap.
            bindTags(binding, post, adapter, listener);

            // Audio indicator for reels
            if (post.isReel()) {
                binding.ivAudioToggle.setVisibility(View.VISIBLE);
                updateAudioIcon();
                binding.ivAudioToggle.setOnClickListener(v -> {
                    isAudioMuted = !isAudioMuted;
                    updateAudioIcon();
                });
            } else {
                binding.ivAudioToggle.setVisibility(View.GONE);
            }

            // Audio / repost attribution overlay (design-only, decorative)
            if (post.isReel() && Constants.DECORATIVE_UI) {
                binding.layoutMediaAttribution.setVisibility(View.VISIBLE);
                GlideUtils.loadAvatar(binding.ivMediaAvatar, post.getAvatarUrl());
                binding.layoutMediaAttribution.setOnClickListener(v -> {
                    if (listener != null) listener.onUserClick(post.getUserId(), post.getUsername());
                });
            } else {
                binding.layoutMediaAttribution.setVisibility(View.GONE);
                binding.layoutMediaAttribution.setOnClickListener(null);
            }

            // 3. Double-tap to like. The white burst is played by the layout itself, so it
            // also shows on an already-liked post (the like call is what stays guarded).
            binding.doubleTapLayout.setOnDoubleTapLikeListener(() -> {
                if (!post.isLikedByMe()) {
                    animateLikeButton();
                    if (listener != null) listener.onLikeClick(post, position);
                }
            });

            // 4. Like button state
            updateLikeButton(post.isLikedByMe());
            binding.ivLike.setOnClickListener(v -> {
                animateLikeButton();
                // Resolve by id at click time: the bound object and position go stale
                // as soon as the list is replaced or the row is recycled.
                int pos = getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION || listener == null) return;
                Post target = adapter.getItem(pos);
                if (target != null) listener.onLikeClick(target, pos);
            });

            // 5. Save button state
            updateSaveButton(post.isSavedByMe());
            binding.ivBookmark.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION || listener == null) return;
                Post target = adapter.getItem(pos);
                if (target != null) listener.onSaveClick(target, pos);
            });

            // 6. Comments click. "Turn off commenting" applies to every viewer, so the entry
            // still opens the post but the caption link states the thread is closed.
            if (post.isCommentsDisabled()) {
                binding.ivComment.setOnClickListener(v -> {
                    if (listener != null) listener.onCommentClick(post);
                });
                binding.ivComment.setAlpha(0.4f);
                binding.tvViewComments.setText(R.string.post_comments_turned_off);
                binding.tvViewComments.setAlpha(0.6f);
            } else {
                binding.ivComment.setAlpha(1f);
                binding.ivComment.setOnClickListener(v -> {
                    if (listener != null) listener.onCommentClick(post);
                });
                binding.tvViewComments.setAlpha(1f);
                binding.tvViewComments.setOnClickListener(v -> {
                    if (listener != null) listener.onCommentClick(post);
                });
            }

            // 7. Share click
            binding.ivShare.setOnClickListener(v -> {
                if (listener != null) listener.onShareClick(post);
            });

            // 7b. Repost
            binding.ivRepost.setVisibility(View.VISIBLE);
            binding.ivRepost.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION || listener == null) return;
                Post target = adapter.getItem(pos);
                if (target != null) listener.onRepostClick(target);
            });

            // 8. 3-dots options
            binding.ivOptions.setOnClickListener(v -> {
                if (listener != null) listener.onOptionsClick(post, position);
            });

            // 8b. The post's track, when it has one.
            final String musicUrl = post.getMusicUrl();
            final String musicTitle = post.getMusicTitle();
            if (musicTitle != null && !musicTitle.trim().isEmpty()) {
                binding.tvMusic.setVisibility(View.VISIBLE);
                binding.tvMusic.setText(com.insta.nova.util.PostMusicPlayer.isActive(musicUrl)
                        ? "\u23F8  " + musicTitle : "\u266A  " + musicTitle);
                binding.tvMusic.setOnClickListener(v -> {
                    final android.widget.TextView chip = binding.tvMusic;
                    Runnable repaint = () -> {
                        boolean playing = com.insta.nova.util.PostMusicPlayer.isActive(musicUrl);
                        chip.setText(playing ? "\u23F8  " + musicTitle : "\u266A  " + musicTitle);
                    };
                    com.insta.nova.util.PostMusicPlayer.toggle(v.getContext(), musicUrl);
                    // The chip repaints itself instead of notifying the adapter: the feed lives
                    // inside a ConcatAdapter, where a feed position is not a global position.
                    repaint.run();
                    // Playback begins after the file is cached, so refresh again once it runs.
                    chip.postDelayed(repaint, 1200);
                    chip.postDelayed(repaint, 2600);
                });
            } else {
                binding.tvMusic.setVisibility(View.GONE);
                binding.tvMusic.setOnClickListener(null);
            }

            // 9. Likes line: "Liked by <user> and <n> others" (hidden at 0).
            // "Hide like count" is an author choice that applies to every viewer, so the whole
            // line and the action-row tally disappear for everyone.
            boolean hideLikes = post.isHideLikeCount();
            if (post.getLikesCount() > 0 && !hideLikes) {
                binding.tvLikesCount.setText(buildLikesLine(context, post));
                binding.tvLikesCount.setVisibility(View.VISIBLE);
                final Post likesPost = post;
                binding.tvLikesCount.setOnClickListener(v -> {
                    if (listener != null) listener.onLikesClick(likesPost);
                });
            } else {
                binding.tvLikesCount.setText(null);
                binding.tvLikesCount.setVisibility(View.GONE);
                binding.tvLikesCount.setOnClickListener(null);
            }

            // 9b. Action-row counts (abbreviated, hidden at 0)
            if (post.getLikesCount() > 0 && !hideLikes) {
                binding.tvLikeCount.setText(abbreviate(post.getLikesCount()));
                binding.tvLikeCount.setVisibility(View.VISIBLE);
            } else {
                binding.tvLikeCount.setVisibility(View.GONE);
            }

            if (post.getCommentsCount() > 0) {
                binding.tvCommentCount.setText(abbreviate(post.getCommentsCount()));
                binding.tvCommentCount.setVisibility(View.VISIBLE);
            } else {
                binding.tvCommentCount.setVisibility(View.GONE);
            }

            // 10. Caption
            binding.tvCaption.setCaption(
                    post.getUsername(),
                    post.getCaption(),
                    new SpanUtils.OnSpanClickListener() {
                        @Override public void onTagClick(String tag) {
                            if (listener != null) listener.onTagClick(tag);
                        }
                        @Override public void onMentionClick(String mentionUsername) {
                            if (listener != null) listener.onUserClick(null, mentionUsername);
                        }
                    },
                    username -> {
                        if (listener != null) listener.onUserClick(post.getUserId(), username);
                    }
            );

            // 11. Comments count text
            if (post.getCommentsCount() > 0) {
                binding.tvViewComments.setText(Plural.comments(context, post.getCommentsCount()));
                binding.tvViewComments.setVisibility(View.VISIBLE);
            } else {
                binding.tvViewComments.setVisibility(View.GONE);
            }

            // 12. Timestamp
            binding.tvTimestamp.setText(TimeUtils.formatRelativeTime(post.getCreatedAt()));
        }

        private void updateLikeButton(boolean liked) {
            Context c = binding.getRoot().getContext();
            if (liked) {
                // Liked heart: ic_heart_filled (tinted with @color/color_like).
                binding.ivLike.setImageResource(R.drawable.ic_heart_filled);
                binding.ivLike.setImageTintList(null);
                binding.ivLike.clearColorFilter();
            } else {
                binding.ivLike.setImageResource(R.drawable.ic_heart);
                binding.ivLike.setColorFilter(ContextCompat.getColor(c, R.color.color_text_primary));
            }
        }

        /** "Liked by <user>" / "Liked by <user> and <n> others" with bold usernames. */
        private CharSequence buildLikesLine(Context context, Post post) {
            String username = post.getUsername() == null ? "" : post.getUsername();
            SpannableStringBuilder sb = new SpannableStringBuilder();
            sb.append(context.getString(R.string.liked_by)).append(' ');

            int nameStart = sb.length();
            sb.append(username);
            sb.setSpan(new StyleSpan(Typeface.BOLD), nameStart, sb.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            int others = post.getLikesCount() - 1;
            if (others > 0) {
                sb.append(' ');
                int othersStart = sb.length();
                sb.append(Plural.likedByTail(context, others));
                sb.setSpan(new StyleSpan(Typeface.BOLD), othersStart, sb.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return sb;
        }

        /** 999 -> "999", 1200 -> "1.2K", 12000 -> "12K", 1200000 -> "1.2M". */
        private String abbreviate(int count) {
            if (count < 1000) {
                return String.valueOf(count);
            }
            if (count < 10000) {
                return String.format(Locale.US, "%.1fK", count / 1000.0);
            }
            if (count < 1000000) {
                return String.format(Locale.US, "%dK", Math.round(count / 1000.0));
            }
            if (count < 10000000) {
                return String.format(Locale.US, "%.1fM", count / 1000000.0);
            }
            return String.format(Locale.US, "%dM", Math.round(count / 1000000.0));
        }

        private void animateLikeButton() {
            binding.ivLike.animate()
                    .scaleX(1.3f)
                    .scaleY(1.3f)
                    .setDuration(150)
                    .setInterpolator(new OvershootInterpolator())
                    .withEndAction(() -> {
                        binding.ivLike.animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .setDuration(100)
                                .start();
                    }).start();
        }

        private void updateSaveButton(boolean saved) {
            Context c = binding.getRoot().getContext();
            if (saved) {
                binding.ivBookmark.setImageResource(R.drawable.ic_bookmark_filled);
            } else {
                binding.ivBookmark.setImageResource(R.drawable.ic_bookmark_outline);
            }
            binding.ivBookmark.setColorFilter(ContextCompat.getColor(c, R.color.color_text_primary));
        }

        private void updateAudioIcon() {
            binding.ivAudioToggle.setImageResource(isAudioMuted ? R.drawable.ic_volume_off : R.drawable.ic_volume_up);
        }

        void clearCarouselCallback() {
            if (carouselPageChangeCallback != null) {
                binding.viewPagerCarousel.unregisterOnPageChangeCallback(carouselPageChangeCallback);
                carouselPageChangeCallback = null;
            }
        }

        private static List<String> validMediaUrls(List<String> urls) {
            List<String> valid = new ArrayList<>();
            if (urls == null) return valid;
            for (String url : urls) {
                if (url != null && !url.trim().isEmpty()) valid.add(url);
            }
            return valid;
        }
    }

    /**
     * Inner adapter for carousel pages.
     */
    public static class CarouselPagerAdapter
            extends RecyclerView.Adapter<CarouselPagerAdapter.PageViewHolder> {
        private final List<String> urls;

        public CarouselPagerAdapter(List<String> urls) {
            this.urls = urls;
        }

        @NonNull
        @Override
        public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ImageView iv = new ImageView(parent.getContext());
            iv.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ));
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setAdjustViewBounds(false);
            return new PageViewHolder(iv);
        }

        @Override
        public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
            com.insta.nova.util.GlideUtils.load(holder.imageView, "posts", urls.get(position));
        }

        @Override
        public int getItemCount() {
            return urls.size();
        }

        static class PageViewHolder extends RecyclerView.ViewHolder {
            final ImageView imageView;
            PageViewHolder(ImageView iv) {
                super(iv);
                this.imageView = iv;
            }
        }
    }
}
