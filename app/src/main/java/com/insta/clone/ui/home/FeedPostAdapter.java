package com.insta.clone.ui.home;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import com.bumptech.glide.Glide;
import com.insta.clone.R;
import com.insta.clone.data.model.Post;
import com.insta.clone.databinding.ItemFeedPostBinding;
import com.insta.clone.util.SpanUtils;
import com.insta.clone.util.TimeUtils;
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
        void onSaveClick(Post post, int position);
        void onUserClick(String userId, String username);
        void onOptionsClick(Post post, int position);
        void onTagClick(String tag);
    }

    private static boolean isAudioMuted = true;
    private final OnPostInteractionListener listener;

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
        holder.bind(post, position, listener);
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

        void bind(Post post, int position, OnPostInteractionListener listener) {
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

            com.insta.clone.util.GlideUtils.loadAvatar(binding.ivAvatar, post.getAvatarUrl());

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

                CarouselPagerAdapter carouselAdapter = new CarouselPagerAdapter(mediaUrls);
                binding.viewPagerCarousel.setAdapter(carouselAdapter);
                carouselPageChangeCallback = new ViewPager2.OnPageChangeCallback() {
                    @Override
                    public void onPageSelected(int pagePos) {
                        binding.dotsIndicator.setSelectedPosition(pagePos);
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
                com.insta.clone.util.GlideUtils.load(binding.ivSingleMedia, "posts", singleUrl);
            }

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

            // 3. Double-tap to like
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
                if (listener != null) listener.onLikeClick(post, position);
            });

            // 5. Save button state
            updateSaveButton(post.isSavedByMe());
            binding.ivBookmark.setOnClickListener(v -> {
                if (listener != null) listener.onSaveClick(post, position);
            });

            // 6. Comments click
            binding.ivComment.setOnClickListener(v -> {
                if (listener != null) listener.onCommentClick(post);
            });
            binding.tvViewComments.setOnClickListener(v -> {
                if (listener != null) listener.onCommentClick(post);
            });

            // 7. Share click
            binding.ivShare.setOnClickListener(v -> {
                if (listener != null) listener.onShareClick(post);
            });

            // 8. 3-dots options
            binding.ivOptions.setOnClickListener(v -> {
                if (listener != null) listener.onOptionsClick(post, position);
            });

            // 9. Likes count
            if (post.getLikesCount() > 0) {
                binding.tvLikesCount.setText(context.getString(R.string.likes_count, String.format(Locale.US, "%,d", post.getLikesCount())));
                binding.tvLikesCount.setVisibility(View.VISIBLE);
            } else {
                binding.tvLikesCount.setVisibility(View.GONE);
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
                binding.tvViewComments.setText(context.getString(R.string.view_all_comments, post.getCommentsCount()));
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
                binding.ivLike.setImageResource(R.drawable.ic_favorite_filled);
                binding.ivLike.setImageTintList(null);
                binding.ivLike.clearColorFilter();
            } else {
                binding.ivLike.setImageResource(R.drawable.ic_favorite_outline);
                binding.ivLike.setColorFilter(ContextCompat.getColor(c, R.color.color_text_primary));
            }
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
    static class CarouselPagerAdapter extends RecyclerView.Adapter<CarouselPagerAdapter.PageViewHolder> {
        private final List<String> urls;

        CarouselPagerAdapter(List<String> urls) {
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
            com.insta.clone.util.GlideUtils.load(holder.imageView, "posts", urls.get(position));
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
