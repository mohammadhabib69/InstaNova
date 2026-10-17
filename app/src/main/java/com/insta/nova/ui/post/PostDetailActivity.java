package com.insta.nova.ui.post;

import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.insta.nova.R;
import com.insta.nova.data.local.LocalSocialStore;
import com.insta.nova.data.model.Post;
import com.insta.nova.data.repository.PostRepository;
import com.insta.nova.databinding.ItemFeedPostBinding;
import com.insta.nova.ui.comment.CommentsBottomSheet;
import com.insta.nova.util.TimeUtils;
import com.insta.nova.util.ToastUtil;
import java.util.List;

/**
 * Activity for displaying single post details.
 */
public class PostDetailActivity extends AppCompatActivity {

    private PostRepository postRepository;
    private LocalSocialStore localStore;
    private ItemFeedPostBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_detail);

        ImageView ivBack = findViewById(R.id.ivBack);
        if (ivBack != null) {
            ivBack.setOnClickListener(v -> finish());
        }

        FrameLayout postContainer = findViewById(R.id.postContainer);
        postRepository = new PostRepository(this);
        localStore = new LocalSocialStore(this);

        binding = ItemFeedPostBinding.inflate(getLayoutInflater(), postContainer, true);

        Post post = (Post) getIntent().getSerializableExtra("post");
        if (post != null) {
            bindPost(post);
        }
    }

    private void bindPost(Post post) {
        if (post == null || binding == null) return;

        binding.tvUsername.setText(post.getUsername() != null ? post.getUsername() : "");
        binding.badgeVerified.setVerified(post.isVerified());

        if (post.getLocation() != null && !post.getLocation().isEmpty()) {
            binding.tvLocation.setText(post.getLocation());
            binding.tvLocation.setVisibility(View.VISIBLE);
        } else {
            binding.tvLocation.setVisibility(View.GONE);
        }

        com.insta.nova.util.GlideUtils.loadAvatar(binding.ivAvatar, post.getAvatarUrl());

        List<String> mediaUrls = post.getMediaUrls();
        String displayUrl = (mediaUrls != null && !mediaUrls.isEmpty()) ? mediaUrls.get(0) : post.getFirstMediaUrl();

        binding.ivSingleMedia.setVisibility(View.VISIBLE);
        binding.viewPagerCarousel.setVisibility(View.GONE);
        binding.dotsIndicator.setVisibility(View.GONE);

        com.insta.nova.util.GlideUtils.load(binding.ivSingleMedia, "posts", displayUrl);

        binding.tvCaption.setText(post.getCaption() != null ? post.getCaption() : "");
        binding.tvTimestamp.setText(TimeUtils.formatRelativeTime(post.getCreatedAt()));

        int likes = post.getLikesCount();
        binding.tvLikesCount.setText(com.insta.nova.util.Plural.likes(this, likes));

        updateLikeState(post.isLiked());

        binding.ivLike.setOnClickListener(v -> {
            boolean nextState = !post.isLiked();
            post.setLiked(nextState);
            post.setLikesCount(post.getLikesCount() + (nextState ? 1 : -1));
            updateLikeState(nextState);
            binding.tvLikesCount.setText(com.insta.nova.util.Plural.likes(this, post.getLikesCount()));
            postRepository.toggleLike(post.getId(), nextState);
        });

        binding.ivComment.setOnClickListener(v -> {
            CommentsBottomSheet sheet = CommentsBottomSheet.newInstance(post.getId(), post.getCommentsCount());
            sheet.show(getSupportFragmentManager(), "comments");
        });

        binding.ivBookmark.setOnClickListener(v -> {
            boolean nextState = !post.isSaved();
            post.setSaved(nextState);
            binding.ivBookmark.setImageResource(nextState ? R.drawable.ic_bookmark_filled : R.drawable.ic_bookmark_outline);
            if (localStore != null && post.getId() != null) {
                localStore.setSaved(post.getId(), nextState);
            }
            if (nextState) {
                ToastUtil.showPill(this, getString(R.string.saved_to_collection));
            }
            // Locally created posts have no saved_posts row to write.
            if (post.getId() != null && !post.getId().startsWith("local_")) {
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
        binding.ivBookmark.setImageResource(post.isSaved() ? R.drawable.ic_bookmark_filled : R.drawable.ic_bookmark_outline);
    }

    private void updateLikeState(boolean liked) {
        if (binding == null) return;
        binding.ivLike.setImageResource(liked ? R.drawable.ic_favorite_filled : R.drawable.ic_favorite_outline);
        binding.ivLike.setColorFilter(getColor(liked ? R.color.color_like : R.color.color_text_primary));
    }
}
