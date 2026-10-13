package com.insta.clone.ui.home;

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
import com.insta.clone.InstaApp;
import com.insta.clone.R;
import com.insta.clone.data.model.Post;
import com.insta.clone.data.model.StoryGroup;
import com.insta.clone.databinding.DialogPostOptionsBinding;
import com.insta.clone.databinding.FragmentHomeBinding;
import com.insta.clone.ui.activity.ActivityFragment;
import com.insta.clone.ui.chat.InboxActivity;
import com.insta.clone.ui.comment.CommentsBottomSheet;
import com.insta.clone.ui.create.CreateStoryActivity;
import com.insta.clone.ui.create.CreateSheet;
import com.insta.clone.ui.story.StoryViewerActivity;
import com.insta.clone.ui.auth.AuthActivity;
import com.insta.clone.data.repository.ChatRepository;
import com.insta.clone.util.Resource;
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
        binding.btnNotifications.setOnClickListener(v -> {
            // Open activity screen
            ActivityFragment activityFragment = new ActivityFragment();
            activityFragment.show(getParentFragmentManager(), "ActivityFragment");
        });

        binding.btnDirect.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), InboxActivity.class);
            startActivity(intent);
        });
        // PAGE 8A — the '+' opens the Create sheet, whose rows launch the existing flows
        binding.btnCreate.setOnClickListener(v -> CreateSheet.show(requireContext()));

        setupRecyclerView();

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

        viewModel.getPosts().observe(getViewLifecycleOwner(), posts ->
                { if (feedPostAdapter != null) feedPostAdapter.submitList(posts); if (binding != null) binding.emptyState.setVisibility(posts == null || posts.isEmpty() ? View.VISIBLE : View.GONE); }
        );

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
        updateUnreadBadges();
    }

    private void updateUnreadBadges() {
        if (!isAdded() || getContext() == null) return;
        ChatRepository.getInstance(requireContext()).getUnreadCounts().observe(getViewLifecycleOwner(), res -> {
            if (binding != null && res != null && res.status == Resource.Status.SUCCESS && res.data != null) {
                int dms = res.data.getDirectMessages();
                int notifs = res.data.getNotifications();
                if (dms > 0) {
                    binding.tvDmBadge.setVisibility(View.VISIBLE);
                    binding.tvDmBadge.setText(dms > 99 ? "99+" : String.valueOf(dms));
                } else {
                    binding.tvDmBadge.setVisibility(View.GONE);
                }
                binding.dotActivityBadge.setVisibility(notifs > 0 ? View.VISIBLE : View.GONE);
            }
        });
    }

    // --- Story Tray Callbacks ---
    @Override
    public void onStoryClick(StoryGroup group, int position) {
        if (group == null || group.getUserId() == null || group.getUserId().trim().isEmpty() || !isAdded()) return;
        Intent intent = new Intent(requireContext(), StoryViewerActivity.class);
        intent.putExtra("user_id", group.getUserId());
        intent.putExtra("username", group.getUsername());
        startActivity(intent);
    }

    @Override
    public void onAddStoryClick() {
        if (!isAdded()) return;
        Intent intent = new Intent(requireContext(), CreateStoryActivity.class);
        startActivity(intent);
    }

    // --- Post Interaction Callbacks ---
    @Override
    public void onLikeClick(Post post, int position) {
        viewModel.toggleLike(post, position);
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
        shareIntent.putExtra(Intent.EXTRA_TEXT, "Check out this post by @" + post.getUsername() + " on Instagram!");
        startActivity(Intent.createChooser(shareIntent, "Share Post"));
    }

    @Override
    public void onSaveClick(Post post, int position) {
        viewModel.toggleSave(post, position);
    }

    @Override
    public void onUserClick(String userId, String username) {
        // Switch to Profile tab or open user profile
        if (isAdded()) Toast.makeText(requireContext(), "Opening @" + (username == null ? "user" : username), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onTagClick(String tag) {
        if (isAdded()) Toast.makeText(requireContext(), "Searching #" + (tag == null ? "" : tag), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onOptionsClick(Post post, int position) {
        if (post == null || !isAdded()) return;
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext(), R.style.InstagramBottomSheetDialog);
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
            ClipData clip = ClipData.newPlainText("Post Link", "https://instagram.com/p/" + post.getId());
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
