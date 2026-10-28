package com.insta.nova.hub;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.insta.nova.R;
import com.insta.nova.alerts.NovaAlertsActivity;
import com.insta.nova.data.NovaBadgeStore;
import com.insta.nova.data.NovaDtos;
import com.insta.nova.data.NovaSettingsStore;
import com.insta.nova.data.local.LocalSocialStore;
import com.insta.nova.data.repository.NovaPostRepository;
import com.insta.nova.databinding.FragmentNovaHubBinding;
import com.insta.nova.databinding.SheetNovaFilterBinding;
import com.insta.nova.ui.nova.NovaComposerActivity;
import com.insta.nova.util.Resource;
import com.insta.nova.util.ToastUtil;

import java.util.List;

/**
 * The Nova tab.
 *
 * <p>The tab is a self-contained space: the feed reads {@code nova_posts_public}
 * only, the composer writes {@code nova_posts} only, and the only path from
 * Nova to Instagram is the explicit "Also share to Instagram" switch in the
 * composer.
 *
 * <p>Top bar: the wordmark, the filter glyph, the '+' and the Alerts bell with
 * its unread badge. Below: the chip rail (All, Photos, Reels, Saved, Mine)
 * with the sort selector (Hot, New, Top), then the feed.
 */
public class NovaHubFragment extends Fragment implements NovaPostAdapter.Listener {

    private FragmentNovaHubBinding binding;
    private NovaFeedViewModel feedViewModel;
    private NovaHubViewModel hubViewModel;
    private NovaPostAdapter postAdapter;
    private NovaPostRepository repository;
    private NovaSettingsStore settingsStore;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private NovaDtos.HubSummary lastSummary;
    private NovaFeedViewModel.Filter renderedFilter;
    private boolean introLaunched;

    /* ------------------------------------------------------------------ */
    /* Lifecycle                                                           */
    /* ------------------------------------------------------------------ */

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        binding = FragmentNovaHubBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        feedViewModel = new ViewModelProvider(this).get(NovaFeedViewModel.class);
        hubViewModel = new ViewModelProvider(this).get(NovaHubViewModel.class);
        settingsStore = NovaSettingsStore.get(requireContext());
        repository = new NovaPostRepository(requireContext());

        applyInsets();
        setupFeed();
        setupActions();
        observeFeed();
        observeBadges();
        observeSummary();

        buildFilterChips();
        maybeShowIntro();

        feedViewModel.loadInitial();
    }

    private void applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            ViewGroup.LayoutParams params = binding.novaStatusSpacer.getLayoutParams();
            if (params.height != top) {
                params.height = top;
                binding.novaStatusSpacer.setLayoutParams(params);
            }
            return insets;
        });
        ViewCompat.requestApplyInsets(binding.getRoot());
    }

    /* ------------------------------------------------------------------ */
    /* Chrome                                                              */
    /* ------------------------------------------------------------------ */

    private void setupActions() {
        binding.btnNovaCreate.setOnClickListener(v ->
                NovaSheet.showCreate(this, lastSummary));
        binding.btnNovaTools.setOnClickListener(v ->
                NovaSheet.showTools(this, lastSummary));

        binding.btnNovaFilter.setOnClickListener(v -> showFilterSheet());

        binding.btnNovaAlerts.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), NovaAlertsActivity.class)));

        binding.novaSortButton.setOnClickListener(v -> showSortMenu(v));

        // Empty-state CTA writes into the Nova space, never the Insta feed.
        binding.btnEmptyAction.setOnClickListener(v ->
                NovaComposerActivity.start(requireContext()));

        binding.btnErrorRetry.setOnClickListener(v -> feedViewModel.refresh());
        binding.novaErrorBanner.setOnClickListener(v -> feedViewModel.refresh());

        binding.novaSwipe.setColorSchemeResources(R.color.color_primary);
        binding.novaSwipe.setOnRefreshListener(() -> feedViewModel.refresh());
    }

    /** Single-select filter rail, built to match Explore's chip tokens. */
    private void buildFilterChips() {
        binding.novaFilterRow.removeAllViews();
        for (NovaFeedViewModel.Filter filter : NovaFeedViewModel.Filter.values()) {
            binding.novaFilterRow.addView(createChip(filter));
        }
    }

    private View createChip(NovaFeedViewModel.Filter filter) {
        TextView chip = new TextView(requireContext());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(34));
        params.setMarginEnd(dp(8));
        chip.setLayoutParams(params);
        chip.setGravity(Gravity.CENTER);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        chip.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
                android.graphics.Typeface.NORMAL));
        chip.setPadding(dp(12), 0, dp(12), 0);
        chip.setText(labelFor(filter));
        chip.setOnClickListener(v -> feedViewModel.setFilter(filter));
        chip.setTag(filter);
        return chip;
    }

    private String labelFor(NovaFeedViewModel.Filter filter) {
        switch (filter) {
            case PHOTOS: return getString(R.string.nova_filter_photos);
            case REELS: return getString(R.string.nova_filter_reels);
            case SAVED: return getString(R.string.nova_filter_saved);
            case MINE: return getString(R.string.nova_filter_mine);
            default: return getString(R.string.nova_filter_all);
        }
    }

    private void paintChips(NovaFeedViewModel.Filter selected) {
        if (binding == null) return;
        for (int i = 0; i < binding.novaFilterRow.getChildCount(); i++) {
            View child = binding.novaFilterRow.getChildAt(i);
            if (!(child instanceof TextView) || !(child.getTag() instanceof NovaFeedViewModel.Filter)) {
                continue;
            }
            boolean active = child.getTag() == selected;
            TextView chip = (TextView) child;
            chip.setBackgroundResource(active ? R.drawable.bg_chip_selected : R.drawable.bg_chip_outline);
            chip.setTextColor(active
                    ? themeColor(R.attr.themeBackground, android.graphics.Color.WHITE)
                    : themeColor(R.attr.themeTextPrimary, android.graphics.Color.BLACK));
        }
    }

    /* ------------------------------------------------------------------ */
    /* Feed                                                                */
    /* ------------------------------------------------------------------ */

    private void setupFeed() {
        final LinearLayoutManager layout = new LinearLayoutManager(requireContext());
        binding.novaGrid.setLayoutManager(layout);
        binding.novaGrid.setHasFixedSize(true);
        postAdapter = new NovaPostAdapter(this);
        binding.novaGrid.setAdapter(postAdapter);

        // Ask for the next page a few cards before the end, so the feed keeps
        // scrolling instead of stopping on the last loaded card.
        binding.novaGrid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recycler, int dx, int dy) {
                if (dy <= 0) return;
                int lastVisible = layout.findLastVisibleItemPosition();
                int total = postAdapter.getItemCount();
                if (total > 0 && lastVisible >= total - 3) {
                    feedViewModel.loadMore();
                }
            }
        });
    }

    private void observeFeed() {
        feedViewModel.posts().observe(getViewLifecycleOwner(), posts -> {
            if (binding == null) return;
            postAdapter.submitList(posts);
            renderEmptyState(posts);
        });

        feedViewModel.initialLoading().observe(getViewLifecycleOwner(), loading -> {
            if (binding == null) return;
            boolean showSpinner = Boolean.TRUE.equals(loading) && postAdapter.getItemCount() == 0;
            binding.novaLoading.setVisibility(showSpinner ? View.VISIBLE : View.GONE);
            renderEmptyState(feedViewModel.posts().getValue());
        });

        feedViewModel.refreshing().observe(getViewLifecycleOwner(), refreshing -> {
            if (binding == null) return;
            binding.novaSwipe.setRefreshing(Boolean.TRUE.equals(refreshing)
                    && binding.novaLoading.getVisibility() != View.VISIBLE);
        });

        feedViewModel.filter().observe(getViewLifecycleOwner(), filter -> {
            if (binding == null || filter == null) return;
            paintChips(filter);
            if (renderedFilter != filter) {
                renderedFilter = filter;
                binding.novaGrid.scrollToPosition(0);
            }
        });

        feedViewModel.sort().observe(getViewLifecycleOwner(), sort -> {
            if (binding == null || sort == null) return;
            binding.tvNovaSort.setText(labelFor(sort));
        });

        feedViewModel.error().observe(getViewLifecycleOwner(), message -> {
            if (binding == null) return;
            boolean visible = !TextUtils.isEmpty(message);
            binding.novaErrorBanner.setVisibility(visible ? View.VISIBLE : View.GONE);
            if (visible) binding.tvErrorBanner.setText(message);
        });
    }

    private String labelFor(NovaFeedViewModel.Sort sort) {
        switch (sort) {
            case NEW: return getString(R.string.nova_sort_new);
            case TOP: return getString(R.string.nova_sort_top);
            default: return getString(R.string.nova_sort_hot);
        }
    }

    private void renderEmptyState(List<NovaDtos.NovaPost> posts) {
        if (binding == null) return;
        boolean empty = posts == null || posts.isEmpty();
        boolean stillLoading = Boolean.TRUE.equals(feedViewModel.initialLoading().getValue());
        if (!empty || stillLoading) {
            binding.novaEmpty.setVisibility(View.GONE);
            return;
        }

        NovaFeedViewModel.Filter filter = feedViewModel.currentFilter();
        boolean canCreate = filter == NovaFeedViewModel.Filter.ALL
                || filter == NovaFeedViewModel.Filter.MINE;

        binding.novaEmpty.setVisibility(View.VISIBLE);
        binding.tvEmptyTitle.setText(canCreate
                ? R.string.nova_empty_title : R.string.nova_empty_filtered_title);

        switch (filter) {
            case MINE:
                binding.tvEmptyBody.setText(R.string.nova_empty_mine_body);
                break;
            case SAVED:
                binding.tvEmptyBody.setText(R.string.nova_empty_saved_body);
                break;
            case PHOTOS:
            case REELS:
                binding.tvEmptyBody.setText(R.string.nova_empty_filtered_body);
                break;
            default:
                binding.tvEmptyBody.setText(R.string.nova_empty_body);
                break;
        }
        binding.btnEmptyAction.setVisibility(canCreate ? View.VISIBLE : View.GONE);
    }

    /* ------------------------------------------------------------------ */
    /* Badges, summary                                                     */
    /* ------------------------------------------------------------------ */

    private void observeBadges() {
        NovaBadgeStore.get().unread().observe(getViewLifecycleOwner(), count -> {
            if (binding == null) return;
            int unread = count == null ? 0 : count;
            if (unread <= 0) {
                binding.novaAlertsBadge.setVisibility(View.GONE);
                return;
            }
            binding.novaAlertsBadge.setText(unread > 9 ? "9+" : String.valueOf(unread));
            binding.novaAlertsBadge.setVisibility(View.VISIBLE);
        });
    }

    private void observeSummary() {
        hubViewModel.summary().observe(getViewLifecycleOwner(), resource -> {
            if (resource == null) return;
            if (resource.status == Resource.Status.SUCCESS && resource.data != null) {
                lastSummary = resource.data;
            }
        });
    }

    private void maybeShowIntro() {
        if (introLaunched || getContext() == null || settingsStore == null) return;
        if (settingsStore.current() == null || settingsStore.current().introSeen) return;
        introLaunched = true;
        handler.postDelayed(() -> {
            if (isAdded()) startActivity(new Intent(requireContext(), NovaIntroActivity.class));
        }, 450);
    }

    /* ------------------------------------------------------------------ */
    /* Sort and filter                                                     */
    /* ------------------------------------------------------------------ */

    /** The sort selector next to the chip rail. */
    private void showSortMenu(View anchor) {
        final View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.popup_nova_sort, null, false);

        final PopupWindow popup = new PopupWindow(requireContext());
        popup.setContentView(view);
        popup.setBackgroundDrawable(null);      // the layout carries the surface
        popup.setOutsideTouchable(true);
        popup.setFocusable(true);
        popup.setElevation(dp(10));

        final int popupWidth = dp(168);
        popup.setWidth(popupWidth);

        // Mark the sort that is live, and hide the tick on the other two.
        final NovaFeedViewModel.Sort current = feedViewModel.currentSort();
        tick(view, R.id.checkSortHot, current == NovaFeedViewModel.Sort.HOT);
        tick(view, R.id.checkSortNew, current == NovaFeedViewModel.Sort.NEW);
        tick(view, R.id.checkSortTop, current == NovaFeedViewModel.Sort.TOP);

        view.findViewById(R.id.rowSortHot).setOnClickListener(v -> {
            popup.dismiss();
            feedViewModel.setSort(NovaFeedViewModel.Sort.HOT);
        });
        view.findViewById(R.id.rowSortNew).setOnClickListener(v -> {
            popup.dismiss();
            feedViewModel.setSort(NovaFeedViewModel.Sort.NEW);
        });
        view.findViewById(R.id.rowSortTop).setOnClickListener(v -> {
            popup.dismiss();
            feedViewModel.setSort(NovaFeedViewModel.Sort.TOP);
        });

        // Align the popup's right edge with the chip so it cannot run off the
        // screen, and drop it just under the chip.
        popup.showAsDropDown(anchor, anchor.getWidth() - popupWidth, 0);
    }

    private void tick(View root, int checkId, boolean on) {
        View check = root.findViewById(checkId);
        check.setVisibility(on ? View.VISIBLE : View.INVISIBLE);
    }

    /** The filter sheet: sort, content type, hide anonymous, hide expiring soon. */
    private void showFilterSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext(),
                R.style.InstaNovaBottomSheetDialog);
        SheetNovaFilterBinding sheet = SheetNovaFilterBinding.inflate(getLayoutInflater());
        dialog.setContentView(sheet.getRoot());

        final int visible = themeColor(R.attr.themeTextPrimary, android.graphics.Color.WHITE);
        final int hidden = android.graphics.Color.TRANSPARENT;

        final NovaFeedViewModel.Sort[] sort = {feedViewModel.currentSort()};
        final String[] type = {feedViewModel.currentContentType()};

        sheet.switchHideAnonymous.setChecked(feedViewModel.currentHideAnonymous());
        sheet.switchHideExpiring.setChecked(feedViewModel.currentHideExpiringSoon());

        final Runnable paintSort = () -> {
            sheet.checkSortHot.setImageTintList(ColorStateList.valueOf(
                    sort[0] == NovaFeedViewModel.Sort.HOT ? visible : hidden));
            sheet.checkSortNew.setImageTintList(ColorStateList.valueOf(
                    sort[0] == NovaFeedViewModel.Sort.NEW ? visible : hidden));
            sheet.checkSortTop.setImageTintList(ColorStateList.valueOf(
                    sort[0] == NovaFeedViewModel.Sort.TOP ? visible : hidden));
        };
        final Runnable paintType = () -> {
            sheet.checkTypeAll.setImageTintList(ColorStateList.valueOf(
                    NovaFeedViewModel.TYPE_ALL.equals(type[0]) ? visible : hidden));
            sheet.checkTypePhoto.setImageTintList(ColorStateList.valueOf(
                    NovaFeedViewModel.TYPE_PHOTO.equals(type[0]) ? visible : hidden));
            sheet.checkTypeReel.setImageTintList(ColorStateList.valueOf(
                    NovaFeedViewModel.TYPE_REEL.equals(type[0]) ? visible : hidden));
            sheet.checkTypeText.setImageTintList(ColorStateList.valueOf(
                    NovaFeedViewModel.TYPE_TEXT.equals(type[0]) ? visible : hidden));
            sheet.checkTypePoll.setImageTintList(ColorStateList.valueOf(
                    NovaFeedViewModel.TYPE_POLL.equals(type[0]) ? visible : hidden));
        };
        paintSort.run();
        paintType.run();

        sheet.rowSortHot.setOnClickListener(v -> { sort[0] = NovaFeedViewModel.Sort.HOT; paintSort.run(); });
        sheet.rowSortNew.setOnClickListener(v -> { sort[0] = NovaFeedViewModel.Sort.NEW; paintSort.run(); });
        sheet.rowSortTop.setOnClickListener(v -> { sort[0] = NovaFeedViewModel.Sort.TOP; paintSort.run(); });

        sheet.rowTypeAll.setOnClickListener(v -> { type[0] = NovaFeedViewModel.TYPE_ALL; paintType.run(); });
        sheet.rowTypePhoto.setOnClickListener(v -> { type[0] = NovaFeedViewModel.TYPE_PHOTO; paintType.run(); });
        sheet.rowTypeReel.setOnClickListener(v -> { type[0] = NovaFeedViewModel.TYPE_REEL; paintType.run(); });
        sheet.rowTypeText.setOnClickListener(v -> { type[0] = NovaFeedViewModel.TYPE_TEXT; paintType.run(); });
        sheet.rowTypePoll.setOnClickListener(v -> { type[0] = NovaFeedViewModel.TYPE_POLL; paintType.run(); });

        sheet.rowHideAnonymous.setOnClickListener(v ->
                sheet.switchHideAnonymous.setChecked(!sheet.switchHideAnonymous.isChecked()));
        sheet.rowHideExpiring.setOnClickListener(v ->
                sheet.switchHideExpiring.setChecked(!sheet.switchHideExpiring.isChecked()));

        sheet.btnApplyFilters.setOnClickListener(v -> {
            feedViewModel.applyFilters(sort[0], type[0],
                    sheet.switchHideAnonymous.isChecked(),
                    sheet.switchHideExpiring.isChecked());
            dialog.dismiss();
        });

        dialog.show();
    }

    /* ------------------------------------------------------------------ */
    /* Card actions                                                        */
    /* ------------------------------------------------------------------ */

    @Override
    public void onVote(NovaDtos.NovaPost post, int value) {
        if (post == null || repository == null) return;
        repository.vote(post.id, value).observe(getViewLifecycleOwner(), resource -> {
            if (resource == null) return;
            if (resource.status == Resource.Status.SUCCESS && resource.data != null) {
                NovaDtos.VoteOutcome outcome = resource.data;
                post.myVote = outcome.myVote;
                post.score = outcome.score;
                post.upvoteCount = outcome.upvoteCount;
                post.downvoteCount = outcome.downvoteCount;
                feedViewModel.updatePost(post);
                postAdapter.submitList(feedViewModel.posts().getValue());
            } else if (resource.status == Resource.Status.ERROR) {
                ToastUtil.showPill(requireContext(), getString(R.string.nova_vote_failed));
            }
        });
    }

    @Override
    public void onPollVote(NovaDtos.NovaPost post, int optionIndex) {
        if (post == null || repository == null) return;
        repository.votePoll(post.id, optionIndex).observe(getViewLifecycleOwner(), resource -> {
            if (resource == null) return;
            if (resource.status == Resource.Status.SUCCESS && resource.data != null) {
                // The server rebuilds the array; showing its answer keeps the client from
                // drifting when a vote changes rather than adds.
                post.pollVotes = resource.data.counts;
                post.myPollVote = resource.data.myVote;
                feedViewModel.updatePost(post);
                postAdapter.submitList(feedViewModel.posts().getValue());
            } else if (resource.status == Resource.Status.ERROR) {
                ToastUtil.showPill(requireContext(), getString(R.string.nova_poll_vote_failed));
            }
        });
    }

    @Override
    public void onComments(NovaDtos.NovaPost post) {
        if (post == null) return;
        NovaPostDetailActivity.start(requireContext(), post);
    }

    @Override
    public void onSave(NovaDtos.NovaPost post) {
        if (post == null || repository == null) return;
        boolean next = !post.isSaved;
        repository.setSaved(post.id, next).observe(getViewLifecycleOwner(), resource -> {
            if (resource == null) return;
            if (resource.status == Resource.Status.SUCCESS) {
                post.isSaved = next;
                feedViewModel.updatePost(post);
                postAdapter.submitList(feedViewModel.posts().getValue());
            } else if (resource.status == Resource.Status.ERROR) {
                ToastUtil.showPill(requireContext(), getString(R.string.nova_save_failed));
            }
        });
    }

    @Override
    public void onOpen(NovaDtos.NovaPost post) {
        if (post == null) return;
        NovaPostDetailActivity.start(requireContext(), post);
    }

    /* ------------------------------------------------------------------ */
    /* Lifecycle hooks                                                     */
    /* ------------------------------------------------------------------ */

    /** Called when the Nova tab is re-selected. */
    public void scrollToTop() {
        if (binding != null && binding.novaGrid != null) {
            binding.novaGrid.smoothScrollToPosition(0);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Coming back from the composer or the detail screen: pull the rows
        // again so a new post and its votes are current.
        if (feedViewModel != null) feedViewModel.refresh();
        if (hubViewModel != null) hubViewModel.refresh();
    }

    @Override
    public void onDestroyView() {
        handler.removeCallbacksAndMessages(null);
        if (binding != null) {
            binding.novaGrid.clearOnScrollListeners();
            binding.novaGrid.setAdapter(null);
        }
        postAdapter = null;
        binding = null;
        super.onDestroyView();
    }

    /* ------------------------------------------------------------------ */
    /* Helpers                                                             */
    /* ------------------------------------------------------------------ */

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int themeColor(int attr, int fallback) {
        TypedValue value = new TypedValue();
        if (requireContext().getTheme().resolveAttribute(attr, value, true)) {
            if (value.resourceId != 0) {
                return ContextCompat.getColor(requireContext(), value.resourceId);
            }
            return value.data;
        }
        return fallback;
    }
}
