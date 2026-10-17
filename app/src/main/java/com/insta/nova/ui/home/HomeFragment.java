package com.insta.nova.ui.home;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.insta.nova.InstaApp;
import com.insta.nova.R;
import com.insta.nova.util.Constants;
import com.insta.nova.data.model.Post;
import com.insta.nova.data.model.StoryGroup;
import com.insta.nova.databinding.DialogPostOptionsBinding;
import com.insta.nova.databinding.FragmentHomeBinding;
import com.insta.nova.data.local.CommentStore;
import com.insta.nova.data.local.LocalChatStore;
import com.insta.nova.data.local.LocalSocialStore;
import com.insta.nova.ui.home.LikesBottomSheet;
import com.insta.nova.data.local.NotificationStore;
import com.insta.nova.data.repository.NotificationCenter;
import com.insta.nova.ui.activity.NotificationsActivity;
import com.insta.nova.ui.chat.InboxActivity;
import com.insta.nova.ui.chat.UserProfileActivity;
import com.insta.nova.ui.comment.CommentsBottomSheet;
import com.insta.nova.ui.create.CreateStoryActivity;
import com.insta.nova.ui.create.NewStoryActivity;
import com.insta.nova.ui.create.CreateSheet;
import com.insta.nova.ui.story.LocalStoryViewerActivity;
import com.insta.nova.ui.story.StoryViewerActivity;
import com.insta.nova.ui.auth.AuthActivity;
import com.insta.nova.data.repository.ChatRepository;
import com.insta.nova.util.Resource;
import com.insta.nova.util.ToastUtil;
import java.util.Objects;

/**
 * Main Home feed screen hosting Story Tray, Feed Posts, and All Caught Up footer.
 */
public class HomeFragment extends Fragment implements FeedPostAdapter.OnPostInteractionListener, StoryTrayAdapter.OnStoryClickListener {

    private FragmentHomeBinding binding;
    private HomeViewModel viewModel;

    private StoryTrayAdapter storyTrayAdapter;
    private FeedPostAdapter feedPostAdapter;
    private CaughtUpAdapter caughtUpAdapter;

    /** Last server DM count; real messages are counted server-side. */
    private int serverDmUnread = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        // Wordmark styling
        binding.tvWordmark.setTypeface(InstaApp.getWordmarkTypeface(requireContext()));

        // PAGE 1: keep the 48dp home top bar clear of the status bar. The window is
        // edge-to-edge (PAGE 0), so only the top inset is applied here.
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), bars.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
        ViewCompat.requestApplyInsets(binding.appBarLayout);

        // Header click listeners
        binding.btnNotifications.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), NotificationsActivity.class)));

        binding.btnDirect.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), InboxActivity.class);
            startActivity(intent);
        });
        // PAGE 8A — the '+' opens the Create sheet, whose rows launch the existing flows
        binding.btnCreate.setOnClickListener(v -> CreateSheet.show(requireContext()));

        setupRecyclerView();

        // Registered once per view: the badges follow the shared repository stream
        // instead of a fresh request and observer on every resume.
        observeUnreadBadges();
        observeNotificationBadge();
        observeCommentChanges();
        observeLocalChatBadge();

        // Swipe-to-refresh
        binding.swipeRefresh.setColorSchemeResources(R.color.color_primary);
        binding.swipeRefresh.setOnRefreshListener(() -> viewModel.refresh());

        // Observe ViewModel data
        viewModel.getIsRefreshing().observe(getViewLifecycleOwner(), refreshing ->
                { if (binding != null) binding.swipeRefresh.setRefreshing(Boolean.TRUE.equals(refreshing)); }
        );

        viewModel.getStoryGroups().observe(getViewLifecycleOwner(), groups ->
                { if (storyTrayAdapter != null) storyTrayAdapter.submitList(groups); }
        );

        viewModel.getPosts().observe(getViewLifecycleOwner(), posts -> {
            if (feedPostAdapter != null) feedPostAdapter.submitList(posts);
            if (binding != null) binding.emptyState.setVisibility(
                    posts == null || posts.isEmpty() ? View.VISIBLE : View.GONE);
            // Lets the activity feed use real authors, avatars and post thumbnails.
            if (isAdded() && posts != null) {
                NotificationCenter.get(requireContext()).onFeedLoaded(posts);
            }
        });

        viewModel.getIsAllCaughtUp().observe(getViewLifecycleOwner(), caughtUp ->
                { if (caughtUpAdapter != null) caughtUpAdapter.setVisible(Boolean.TRUE.equals(caughtUp)); }
        );

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), message -> {
            if (message != null && !message.trim().isEmpty() && isAdded()) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.getSessionExpired().observe(getViewLifecycleOwner(), expired -> {
            if (Boolean.TRUE.equals(expired) && isAdded()) {
                Intent intent = new Intent(requireContext(), AuthActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                requireActivity().finish();
            }
        });

        // Start only after observers and adapters are ready. The ViewModel makes this idempotent.
        viewModel.loadInitial();
    }

    private void setupRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        binding.rvFeed.setLayoutManager(layoutManager);

        // Horizontal Story Tray in a simple wrapping RecyclerView adapter
        storyTrayAdapter = new StoryTrayAdapter(this);
        StoryTrayHeaderAdapter headerAdapter = new StoryTrayHeaderAdapter(storyTrayAdapter);

        feedPostAdapter = new FeedPostAdapter(this);
        caughtUpAdapter = new CaughtUpAdapter();

        ConcatAdapter concatAdapter = new ConcatAdapter(headerAdapter, feedPostAdapter, caughtUpAdapter);
        binding.rvFeed.setAdapter(concatAdapter);

        // Keyset Pagination: trigger when 3 items from bottom
        binding.rvFeed.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0) {
                    int visibleCount = layoutManager.getChildCount();
                    int totalCount = layoutManager.getItemCount();
                    int pastVisible = layoutManager.findFirstVisibleItemPosition();

                    if (totalCount > 0 && (visibleCount + pastVisible) >= totalCount - 3) {
                        viewModel.loadMore();
                    }
                }
            }
        });
    }

    public void scrollToTop() {
        if (binding != null && binding.rvFeed != null) {
            binding.rvFeed.smoothScrollToPosition(0);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (viewModel != null) viewModel.refresh();
        refreshUnreadBadges();
    }

    /**
     * Nudges the shared unread-count owner. Inside the freshness window this is a
     * no-op, and while a request is in flight it joins that request instead of
     * starting another.
     */
    private void refreshUnreadBadges() {
        if (!isAdded() || getContext() == null) return;
        ChatRepository.getInstance(requireContext()).refreshUnreadCounts(false);
    }

    private void observeUnreadBadges() {
        if (getContext() == null) return;
        ChatRepository.getInstance(requireContext()).unreadCounts().observe(getViewLifecycleOwner(), res -> {
            if (binding != null && res != null && res.status == Resource.Status.SUCCESS && res.data != null) {
                serverDmUnread = Math.max(0, res.data.getDirectMessages());
                renderDmBadge();
                // The server unread count is authoritative for real DM messages.
            }
        });
    }

    /** DM badge is driven by the server unread count for real messages. */
    private void renderDmBadge() {
        if (binding == null || getContext() == null) return;
        int total = serverDmUnread;
        if (total > 0) {
            binding.tvDmBadge.setVisibility(View.VISIBLE);
            binding.tvDmBadge.setText(total > 99 ? "99+" : String.valueOf(total));
        } else {
            binding.tvDmBadge.setVisibility(View.GONE);
        }
    }

    private void observeLocalChatBadge() {
        if (getContext() == null) return;
        LocalChatStore.revision().observe(getViewLifecycleOwner(), revision -> renderDmBadge());
    }

    /** Red unseen counter on the heart, owned by the local notification store. */
    private void observeNotificationBadge() {
        if (getContext() == null) return;
        new NotificationStore(requireContext()).unreadCountLiveData()
                .observe(getViewLifecycleOwner(), unread -> {
                    if (binding == null) return;
                    int count = unread == null ? 0 : unread;
                    if (count > 0) {
                        binding.tvActivityBadge.setVisibility(View.VISIBLE);
                        binding.tvActivityBadge.setText(count > 9 ? "9+" : String.valueOf(count));
                    } else {
                        binding.tvActivityBadge.setVisibility(View.GONE);
                    }
                });
    }

    /** Comment counts come from the local thread once a sheet has hydrated it. */
    private void observeCommentChanges() {
        CommentStore.revision().observe(getViewLifecycleOwner(), revision -> {
            if (viewModel != null) viewModel.refreshCommentCounts();
        });
        LocalSocialStore.revision().observe(getViewLifecycleOwner(), revision -> {
            if (viewModel != null) viewModel.refreshLocalContent();
        });
    }

    // --- Story Tray Callbacks ---
    @Override
    public void onStoryClick(StoryGroup group, int position) {
        if (group == null || !isAdded()) return;
        LocalSocialStore localStore = new LocalSocialStore(requireContext());
        if (group.isOwner() && !localStore.getActiveStories().isEmpty()) {
            startActivity(new Intent(requireContext(), LocalStoryViewerActivity.class));
            return;
        }
        if (group.getUserId() == null || group.getUserId().trim().isEmpty()) return;
        Intent intent = new Intent(requireContext(), StoryViewerActivity.class);
        intent.putExtra("user_id", group.getUserId());
        intent.putExtra("username", group.getUsername());
        startActivity(intent);
    }

    @Override
    public void onAddStoryClick() {
        if (!isAdded()) return;
        startActivity(new Intent(requireContext(), NewStoryActivity.class));
    }

    // --- Post Interaction Callbacks ---
    @Override
    public void onRepostClick(Post post) {
        if (post == null || post.getId() == null || !isAdded()) return;
        // A "You reposted" row is a copy: the repost state belongs to the original id, so
        // Undo has to work from that row too instead of being ignored.
        String id = post.getId();
        if (id.startsWith(HomeViewModel.REPOST_ID_PREFIX)) {
            id = id.substring(HomeViewModel.REPOST_ID_PREFIX.length());
        }
        if (id.isEmpty()) return;
        LocalSocialStore store = new LocalSocialStore(requireContext());
        boolean reposted = store.isReposted(id);
        final String originId = id;
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(reposted ? R.string.undo_repost : R.string.repost)
                .setPositiveButton(reposted ? R.string.undo_repost : R.string.repost, (d, w) -> {
                    boolean next = !reposted;
                    store.setReposted(originId, next);
                    if (viewModel != null) viewModel.applyRepost(post, next);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public void onLikesClick(Post post) {
        if (post == null || !isAdded()) return;
        LikesBottomSheet.newInstance(post.getId(), post.getLikesCount(), post.isLikedByMe())
                .show(getChildFragmentManager(), "likes");
    }

    @Override
    public void onLikeClick(Post post, int position) {
        if (post == null) return;
        boolean becomingLiked = !post.isLikedByMe();
        viewModel.toggleLike(post, position);
        if (becomingLiked && isAdded()) {
            // Demo audience reacts to real in-app activity.
            NotificationCenter.get(requireContext()).onPostLiked(post);
        }
    }

    @Override
    public void onCommentClick(Post post) {
        if (post == null || post.getId() == null || post.getId().trim().isEmpty() || !isAdded()) return;
        CommentsBottomSheet sheet = CommentsBottomSheet.newInstance(post.getId(), post.getCommentsCount());
        sheet.show(getChildFragmentManager(), "CommentsBottomSheet");
    }

    @Override
    public void onShareClick(Post post) {
        if (post == null || !isAdded()) return;
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, "Check out this post by @" + post.getUsername() + " on " + Constants.APP_NAME + "!");
        startActivity(Intent.createChooser(shareIntent, "Share Post"));
    }

    @Override
    public void onSaveClick(Post post, int position) {
        if (post == null) return;
        boolean saving = !post.isSavedByMe();
        viewModel.toggleSave(post, position);
        if (saving && isAdded()) {
            ToastUtil.showPill(requireContext(), getString(R.string.saved_to_collection));
        }
    }

    @Override
    public void onUserClick(String userId, String username) {
        // Real profile screen instead of a placeholder toast, so a post's author can be
        // opened and followed straight from the feed.
        if (!isAdded() || userId == null || userId.trim().isEmpty()) return;
        Intent intent = new Intent(requireContext(), UserProfileActivity.class);
        intent.putExtra(UserProfileActivity.EXTRA_USER_ID, userId);
        intent.putExtra(UserProfileActivity.EXTRA_USERNAME, username);
        startActivity(intent);
    }

    @Override
    public void onTagClick(String tag) {
        if (isAdded()) Toast.makeText(requireContext(), "Searching #" + (tag == null ? "" : tag), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onOptionsClick(Post post, int position) {
        if (post == null || !isAdded()) return;
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext(), R.style.InstaNovaBottomSheetDialog);
        DialogPostOptionsBinding dialogBinding = DialogPostOptionsBinding.inflate(getLayoutInflater());
        dialog.setContentView(dialogBinding.getRoot());

        boolean isMyPost = Objects.equals(post.getUserId(), viewModel.getCurrentUserId());

        if (isMyPost) {
            dialogBinding.btnDeletePost.setVisibility(View.VISIBLE);
            dialogBinding.btnDeletePost.setOnClickListener(v -> {
                dialog.dismiss();
                viewModel.deletePost(post);
                Toast.makeText(requireContext(), "Post deleted", Toast.LENGTH_SHORT).show();
            });
        } else {
            dialogBinding.btnUnfollow.setVisibility(View.VISIBLE);
            dialogBinding.btnUnfollow.setText("Unfollow @" + post.getUsername());
            dialogBinding.btnUnfollow.setOnClickListener(v -> {
                dialog.dismiss();
                viewModel.unfollowUser(post.getUserId());
                Toast.makeText(requireContext(), "Unfollowed @" + post.getUsername(), Toast.LENGTH_SHORT).show();
            });
        }

        dialogBinding.btnSharePost.setOnClickListener(v -> {
            dialog.dismiss();
            onShareClick(post);
        });

        dialogBinding.btnCopyLink.setOnClickListener(v -> {
            dialog.dismiss();
            ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Post Link", Constants.PROFILE_URL_BASE + "p/" + post.getId());
            if (cm != null) cm.setPrimaryClip(clip);
            Toast.makeText(requireContext(), "Link copied to clipboard", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    @Override
    public void onDestroyView() {
        if (binding != null) {
            binding.rvFeed.clearOnScrollListeners();
            binding.rvFeed.setAdapter(null);
        }
        storyTrayAdapter = null;
        feedPostAdapter = null;
        caughtUpAdapter = null;
        super.onDestroyView();
        binding = null;
    }

    /**
     * Header wrapper adapter holding horizontal Story Tray inside ConcatAdapter.
     */
    static class StoryTrayHeaderAdapter extends RecyclerView.Adapter<StoryTrayHeaderAdapter.ViewHolder> {
        private final StoryTrayAdapter trayAdapter;

        StoryTrayHeaderAdapter(StoryTrayAdapter trayAdapter) {
            this.trayAdapter = trayAdapter;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context context = parent.getContext();
            Resources res = context.getResources();

            RecyclerView rv = new RecyclerView(context);
            rv.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    res.getDimensionPixelSize(R.dimen.story_tray_height)
            ));
            rv.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
            rv.setClipToPadding(false);
            int gutter = res.getDimensionPixelSize(R.dimen.story_tray_padding_horizontal);
            rv.setPadding(gutter, 0, gutter, 0);
            rv.setAdapter(trayAdapter);

            // 1dp divider under the tray.
            View divider = new View(context);
            divider.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    res.getDimensionPixelSize(R.dimen.divider_hairline)
            ));
            divider.setBackgroundColor(themeColor(context, R.attr.themeDivider, R.color.color_divider));

            LinearLayout container = new LinearLayout(context);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            container.addView(rv);
            container.addView(divider);
            return new ViewHolder(container);
        }

        @Override public void onBindViewHolder(@NonNull ViewHolder holder, int position) {}
        @Override public int getItemCount() { return 1; }

        /** Resolves a PAGE 0 semantic theme attribute to a concrete color. */
        private static int themeColor(Context context, int attr, int fallbackColorRes) {
            try {
                TypedValue tv = new TypedValue();
                if (context.getTheme().resolveAttribute(attr, tv, true)) {
                    if (tv.resourceId != 0) {
                        return ContextCompat.getColor(context, tv.resourceId);
                    }
                    return tv.data;
                }
            } catch (Exception ignored) {
                // Fall through to the token default.
            }
            return ContextCompat.getColor(context, fallbackColorRes);
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ViewHolder(@NonNull View itemView) { super(itemView); }
        }
    }
}
