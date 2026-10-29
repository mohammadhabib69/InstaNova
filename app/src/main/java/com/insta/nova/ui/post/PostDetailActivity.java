package com.insta.nova.ui.post;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.viewpager2.widget.ViewPager2;

import com.insta.nova.R;
import com.insta.nova.data.local.LocalSocialStore;
import com.insta.nova.data.model.Post;
import com.insta.nova.data.repository.PostRepository;
import com.insta.nova.databinding.ItemFeedPostBinding;
import com.insta.nova.ui.comment.CommentsBottomSheet;
import com.insta.nova.ui.home.FeedPostAdapter;
import com.insta.nova.util.GlideUtils;
import com.insta.nova.util.Plural;
import com.insta.nova.util.TimeUtils;
import com.insta.nova.util.ToastUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Post detail opened from a profile grid, a notification or a search result.
 *
 * <p>The screen paints the caller's object immediately, then replaces it with the row the
 * server holds. Counters and the multi-image carousel are bound the same way the feed binds
 * them, so a post opened from the grid is not a degraded copy of the feed row.
 */
public class PostDetailActivity extends AppCompatActivity {

    private PostRepository postRepository;
    private LocalSocialStore localStore;
    private ItemFeedPostBinding binding;
    private ProgressBar mediaProgress;

    private String postId;
    /** Latest bound row; kept so a like tap can rebuild the counters from real data. */
    private Post boundPost;
    private ViewPager2.OnPageChangeCallback carouselCallback;
    private boolean singleMediaFailed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_detail);

        ImageView ivBack = findViewById(R.id.ivBack);
        if (ivBack != null) {
            ivBack.setOnClickListener(v -> finish());
        }

        mediaProgress = findViewById(R.id.mediaProgress);
        FrameLayout postContainer = findViewById(R.id.postContainer);
        postRepository = new PostRepository(this);
        localStore = new LocalSocialStore(this);

        binding = ItemFeedPostBinding.inflate(getLayoutInflater(), postContainer, true);

        // The comment sheet writes its count server-side, so the row has to be re-read when
        // the sheet closes; otherwise the screen keeps the number it opened with.
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(
                new FragmentManager.FragmentLifecycleCallbacks() {
                    @Override
                    public void onFragmentViewDestroyed(@NonNull FragmentManager fm,
                                                        @NonNull Fragment fragment) {
                        if (fragment instanceof CommentsBottomSheet) refreshFromServer();
                    }
                }, true);

        Post post = (Post) getIntent().getSerializableExtra("post");
        if (post != null) {
            postId = post.getId();
            bindPost(post);
        }
        String extraId = getIntent().getStringExtra("post_id");
        if (extraId != null) postId = extraId;
        if (isServerUuid(postId)) {
            refreshFromServer();
        }
    }

    /** Re-reads the row so likes/comments reflect what the server holds right now. */
    private void refreshFromServer() {
        if (!isServerUuid(postId)) return;
        postRepository.getPostById(postId).observe(this, resource -> {
            if (resource != null && resource.status == com.insta.nova.util.Resource.Status.SUCCESS
                    && resource.data != null) {
                bindPost(resource.data);
            }
        });
    }

    private void bindPost(Post post) {
        if (post == null || binding == null) return;
        boundPost = post;

        binding.tvUsername.setText(post.getUsername() != null ? post.getUsername() : "");
        binding.badgeVerified.setVerified(post.isVerified());

        if (post.getLocation() != null && !post.getLocation().isEmpty()) {
            binding.tvLocation.setText(post.getLocation());
            binding.tvLocation.setVisibility(View.VISIBLE);
        } else {
            binding.tvLocation.setVisibility(View.GONE);
        }

        GlideUtils.loadAvatar(binding.ivAvatar, post.getAvatarUrl());

        bindMedia(post);

        binding.tvCaption.setText(post.getCaption() != null ? post.getCaption() : "");
        binding.tvTimestamp.setText(TimeUtils.formatRelativeTime(post.getCreatedAt()));

        binding.ivOptions.setOnClickListener(v -> PostOptionsSheet.show(this, post,
                new PostOptionsSheet.Callback() {
                    @Override
                    public void onChanged() {
                        binding.tvCaption.setText(post.getCaption() == null ? "" : post.getCaption());
                        setResult(RESULT_OK);
                    }

                    @Override
                    public void onDeleted() {
                        setResult(RESULT_OK);
                        finish();
                    }

                    @Override
                    public void onArchived() {
                        setResult(RESULT_OK);
                        finish();
                    }
                }));

        bindCounters(post);
        updateLikeState(post.isLiked());

        LocalSocialStore socialStore = new LocalSocialStore(this);
        binding.ivRepost.setOnClickListener(v -> {
            boolean next = !socialStore.isReposted(post.getId());
            socialStore.setReposted(post.getId(), next);
            binding.ivRepost.setColorFilter(getColor(next
                    ? R.color.color_like : R.color.color_text_primary));
            Toast.makeText(this, next ? R.string.st_reposted : R.string.st_unreposted,
                    Toast.LENGTH_SHORT).show();
        });
        binding.ivShare.setOnClickListener(v -> {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_TEXT,
                    com.insta.nova.util.Constants.PROFILE_URL_BASE + "p/" + post.getId());
            startActivity(Intent.createChooser(share, getString(R.string.st_share)));
        });

        binding.ivLike.setOnClickListener(v -> {
            if (boundPost == null) return;
            setPostLiked(!boundPost.isLiked());
        });

        // Double-tap on the photo. The layout only fires when a listener is registered, and
        // this screen never registered one, so the gesture was silently swallowed here while
        // it worked in the feed. It only ever likes: a second double-tap must not undo it.
        binding.doubleTapLayout.setOnDoubleTapLikeListener(() -> setPostLiked(true));

        View.OnClickListener openComments = v -> openComments(post);
        binding.ivComment.setOnClickListener(openComments);
        binding.tvViewComments.setOnClickListener(openComments);

        binding.ivBookmark.setOnClickListener(v -> {
            boolean nextState = !post.isSaved();
            post.setSaved(nextState);
            binding.ivBookmark.setImageResource(nextState
                    ? R.drawable.ic_bookmark_filled : R.drawable.ic_bookmark_outline);
            if (localStore != null && post.getId() != null) {
                localStore.setSaved(post.getId(), nextState);
            }
            if (nextState) {
                ToastUtil.showPill(this, getString(R.string.saved_to_collection));
            }
            // Only a real post uuid has a saved_posts row; local and repost rows do not.
            if (isServerUuid(post.getId())) {
                postRepository.toggleSave(post.getId(), nextState);
            }
        });

        if (localStore != null && post.getId() != null) {
            if (localStore.isSaved(post.getId())) {
                post.setSaved(true);
            } else if (localStore.isExplicitlyUnsaved(post.getId())) {
                post.setSaved(false);
            }
        }
        binding.ivBookmark.setImageResource(post.isSaved()
                ? R.drawable.ic_bookmark_filled : R.drawable.ic_bookmark_outline);
    }

    /**
     * Single photo or carousel, matching the feed row.
     *
     * <p>The screen used to hide the carousel outright and draw {@code media[0]} only, so a
     * three-photo post opened from the grid showed one photo and no way to reach the rest.
     */
    private void bindMedia(Post post) {
        clearCarousel();

        List<String> urls = validMediaUrls(post.getMediaUrls());
        if (urls.isEmpty()) {
            String fallback = post.getThumbnailUrl();
            if (fallback != null && !fallback.trim().isEmpty()) urls.add(fallback);
        }

        if (urls.size() > 1) {
            final int pageCount = urls.size();
            binding.ivSingleMedia.setVisibility(View.GONE);
            binding.viewPagerCarousel.setVisibility(View.VISIBLE);
            binding.ivCarouselBadge.setVisibility(View.VISIBLE);
            binding.dotsIndicator.setVisibility(View.VISIBLE);
            binding.dotsIndicator.setCount(pageCount);
            binding.dotsIndicator.setSelectedPosition(0);
            binding.ivCarouselBadge.setText(
                    getString(R.string.carousel_position, 1, pageCount));

            binding.viewPagerCarousel.setAdapter(new FeedPostAdapter.CarouselPagerAdapter(urls));
            carouselCallback = new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    binding.dotsIndicator.setSelectedPosition(position);
                    binding.ivCarouselBadge.setText(
                            getString(R.string.carousel_position, position + 1, pageCount));
                }
            };
            binding.viewPagerCarousel.registerOnPageChangeCallback(carouselCallback);
            showMediaProgress(false);
            return;
        }

        binding.ivSingleMedia.setVisibility(View.VISIBLE);
        binding.viewPagerCarousel.setVisibility(View.GONE);
        binding.ivCarouselBadge.setVisibility(View.GONE);
        binding.dotsIndicator.setVisibility(View.GONE);

        String singleUrl = urls.isEmpty() ? null : urls.get(0);
        loadSingleMedia(singleUrl);
    }

    private void loadSingleMedia(String url) {
        singleMediaFailed = false;
        showMediaProgress(true);
        GlideUtils.loadProgressive(binding.ivSingleMedia, "posts", url, ok -> {
            showMediaProgress(false);
            singleMediaFailed = !ok;
        });
        // A dropped request on mobile data used to leave the placeholder forever; one tap
        // now retries instead of forcing the user to reopen the post.
        binding.ivSingleMedia.setOnClickListener(v -> {
            if (singleMediaFailed) loadSingleMedia(url);
        });
    }

    private void showMediaProgress(boolean visible) {
        if (mediaProgress != null) {
            mediaProgress.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void clearCarousel() {
        if (binding == null) return;
        if (carouselCallback != null) {
            binding.viewPagerCarousel.unregisterOnPageChangeCallback(carouselCallback);
            carouselCallback = null;
        }
    }

    private void openComments(Post post) {
        if (post == null) return;
        CommentsBottomSheet sheet = CommentsBottomSheet.newInstance(
                post.getId(), post.getCommentsCount(), post.getUserId(), post.isCommentsDisabled());
        sheet.show(getSupportFragmentManager(), "comments");
    }

    /**
     * Like tally, comment tally and the "View all N comments" line.
     *
     * <p>{@code tvLikeCount} / {@code tvCommentCount} / {@code tvLikesCount} ship as
     * {@code visibility="gone"} inside the shared feed row, and this screen never un-hid them,
     * which is why a post opened from the profile looked like it had no counts at all.
     */
    private void bindCounters(Post post) {
        if (binding == null || post == null) return;

        boolean hideLikes = post.isHideLikeCount();
        int likes = Math.max(0, post.getLikesCount());
        View.OnClickListener openLikes = v -> openLikers();
        if (likes > 0 && !hideLikes) {
            binding.tvLikesCount.setText(buildLikesLine(post, likes));
            // The names in this line are only the newest three; the full list lives behind
            // the same tap that opens it in the feed.
            binding.tvLikesCount.setOnClickListener(openLikes);
            binding.tvLikeCount.setOnClickListener(openLikes);
            binding.tvLikesCount.setVisibility(View.VISIBLE);
            binding.tvLikeCount.setText(abbreviate(likes));
            binding.tvLikeCount.setVisibility(View.VISIBLE);
        } else {
            binding.tvLikesCount.setText(null);
            binding.tvLikesCount.setVisibility(View.GONE);
            binding.tvLikesCount.setOnClickListener(null);
            binding.tvLikeCount.setOnClickListener(null);
            binding.tvLikeCount.setVisibility(View.GONE);
        }

        int comments = Math.max(0, post.getCommentsCount());
        if (comments > 0) {
            binding.tvCommentCount.setText(abbreviate(comments));
            binding.tvCommentCount.setVisibility(View.VISIBLE);
            binding.tvViewComments.setText(Plural.comments(this, comments));
            binding.tvViewComments.setVisibility(View.VISIBLE);
        } else {
            binding.tvCommentCount.setVisibility(View.GONE);
            binding.tvViewComments.setVisibility(View.GONE);
        }

        if (post.isCommentsDisabled()) {
            binding.tvViewComments.setText(R.string.post_comments_turned_off);
            binding.tvViewComments.setVisibility(View.VISIBLE);
            binding.ivComment.setAlpha(0.4f);
        } else {
            binding.ivComment.setAlpha(1f);
        }
    }

    /**
     * "Liked by <name> and 1 other" — the names v_posts already returns as top_likers.
     *
     * <p>The screen used to print a bare tally ("2 likes"), so it said how many hearts a post
     * had but never who left them. Falls back to the tally when the view returns no names
     * (an anonymous like, or a row cached before this field existed).
     */
    private CharSequence buildLikesLine(Post post, int likes) {
        List<String> names = post == null ? null : post.getLikerNames();
        if (names == null || names.isEmpty()) return Plural.likes(this, likes);

        List<String> parts = new ArrayList<>(names.subList(0, Math.min(names.size(), 3)));
        int others = likes - parts.size();
        if (others > 0) parts.add(Plural.likedByTail(this, others));

        android.text.SpannableStringBuilder line = new android.text.SpannableStringBuilder();
        line.append(getString(R.string.liked_by)).append(' ');
        for (int i = 0; i < parts.size(); i++) {
            String part = parts.get(i);
            if (i > 0) {
                line.append(i == parts.size() - 1 ? " and " : ", ");
            }
            int start = line.length();
            line.append(part);
            line.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                    start, line.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return line;
    }

    /** Opens the full list of accounts that liked this post. */
    private void openLikers() {
        if (boundPost == null || !isServerUuid(boundPost.getId())) return;
        com.insta.nova.ui.home.LikesBottomSheet
                .newInstance(boundPost.getId(), boundPost.getLikesCount(), boundPost.isLiked())
                .show(getSupportFragmentManager(), "likes");
    }

    /** 999 -> "999", 1200 -> "1.2K", 12000 -> "12K", 1200000 -> "1.2M". */
    private static String abbreviate(int count) {
        if (count < 1000) return String.valueOf(count);
        if (count < 10000) return String.format(Locale.US, "%.1fK", count / 1000.0);
        if (count < 1000000) return String.format(Locale.US, "%dK", Math.round(count / 1000.0));
        if (count < 10000000) return String.format(Locale.US, "%.1fM", count / 1000000.0);
        return String.format(Locale.US, "%dM", Math.round(count / 1000000.0));
    }

    private static List<String> validMediaUrls(List<String> urls) {
        List<String> valid = new ArrayList<>();
        if (urls == null) return valid;
        for (String url : urls) {
            if (url != null && !url.trim().isEmpty()) valid.add(url);
        }
        return valid;
    }

    /**
     * Single write path for the heart button and the double tap.
     *
     * <p>Both used to compute their own delta; routing them through one method keeps a
     * double tap that lands on an already-liked post from inflating the count.
     */
    private void setPostLiked(boolean like) {
        if (boundPost == null || boundPost.isLiked() == like) return;
        boundPost.setLiked(like);
        boundPost.setLikesCount(Math.max(0, boundPost.getLikesCount() + (like ? 1 : -1)));
        updateLikeState(like);
        bindCounters(boundPost);
        if (isServerUuid(boundPost.getId())) {
            postRepository.toggleLike(boundPost.getId(), like);
        }
    }

    private void updateLikeState(boolean liked) {
        if (binding == null) return;
        binding.ivLike.setImageResource(liked
                ? R.drawable.ic_favorite_filled : R.drawable.ic_favorite_outline);
        binding.ivLike.setColorFilter(getColor(liked
                ? R.color.color_like : R.color.color_text_primary));
    }

    @Override
    protected void onDestroy() {
        clearCarousel();
        super.onDestroy();
    }

    /** A real {@code posts.id} is a uuid; "local_…" and "repost_…" rows are not. */
    private static boolean isServerUuid(String id) {
        return id != null && id.matches(
                "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    }
}
