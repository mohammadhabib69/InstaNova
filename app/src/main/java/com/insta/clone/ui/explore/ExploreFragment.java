package com.insta.clone.ui.explore;

import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.tabs.TabLayout;
import com.insta.clone.R;
import com.insta.clone.data.local.RecentSearchStore;
import com.insta.clone.data.model.Post;
import com.insta.clone.data.model.Profile;
import com.insta.clone.data.repository.ReelRepository;
import com.insta.clone.data.repository.SearchRepository;
import com.insta.clone.ui.post.PostDetailActivity;
import com.insta.clone.util.Constants;
import com.insta.clone.util.Resource;
import com.insta.clone.util.ToastUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Explore (idle Search tab, PAGE 3) and Search (focused + results, PAGE 4).
 *
 * <p>Design-only pass: the existing search / explore repository calls are reused
 * unchanged; this class only owns presentation and the two screen states.
 */
public class ExploreFragment extends Fragment {

    /* PAGE 4 result tabs */
    private static final int TAB_TOP = 0;
    private static final int TAB_ACCOUNTS = 1;
    private static final int TAB_AUDIO = 2;
    private static final int TAB_TAGS = 3;
    private static final int TAB_PLACES = 4;

    /* PAGE 3 filter chips */
    private static final int CHIP_REELS = 0;
    private static final int CHIP_SHOP = 1;
    private static final int CHIP_FIRST_TAG = 2;
    private static final int CHIP_NONE = -1;

    private static final int[] CHIP_IDS = {
            R.id.chipReels, R.id.chipShop, R.id.chipArchitecture, R.id.chipTravel,
            R.id.chipDecor, R.id.chipFood, R.id.chipArt, R.id.chipNature
    };

    private static final int[] CHIP_TAG_RES = {
            0, 0, R.string.explore_tag_architecture, R.string.explore_tag_travel,
            R.string.explore_tag_decor, R.string.explore_tag_food,
            R.string.explore_tag_art, R.string.explore_tag_nature
    };

    private View rootView;
    private LinearLayout searchRow;
    private EditText etSearch;
    private RecyclerView rvExploreGrid;
    private View layoutSearchField;
    private View btnSearchScan;
    private View chipsScroll;
    private View searchTabsContainer;
    private View searchEmptyState;
    private ImageView ivSearchBack;
    private ImageView ivSearchClear;
    private ImageView ivSearchEmptyIcon;
    private TextView tvSearchEmptyTitle;
    private TabLayout searchTabs;

    private SearchRepository searchRepository;
    private ReelRepository reelRepository;
    private RecentSearchStore recentSearchStore;

    private final List<Post> explorePosts = new ArrayList<>();
    private final List<SearchRow> searchRows = new ArrayList<>();
    private final List<Profile> accountResults = new ArrayList<>();
    private int tagResultCount = 0;

    private ExploreGridAdapter gridAdapter;
    private SearchRowAdapter rowAdapter;
    private GridGapDecoration gridGapDecoration;

    private boolean isSearching = false;
    private int activeChip = CHIP_NONE;
    private int searchTab = TAB_TOP;
    private boolean settingText = false;

    public ExploreFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_explore, container, false);
        rootView = view;

        searchRow = view.findViewById(R.id.searchRow);
        etSearch = view.findViewById(R.id.et_search);
        rvExploreGrid = view.findViewById(R.id.rv_explore_grid);
        layoutSearchField = view.findViewById(R.id.layoutSearchField);
        btnSearchScan = view.findViewById(R.id.btnSearchScan);
        chipsScroll = view.findViewById(R.id.chipsScroll);
        searchTabsContainer = view.findViewById(R.id.searchTabsContainer);
        searchEmptyState = view.findViewById(R.id.searchEmptyState);
        ivSearchBack = view.findViewById(R.id.ivSearchBack);
        ivSearchClear = view.findViewById(R.id.ivSearchClear);
        ivSearchEmptyIcon = view.findViewById(R.id.ivSearchEmptyIcon);
        tvSearchEmptyTitle = view.findViewById(R.id.tvSearchEmptyTitle);
        searchTabs = view.findViewById(R.id.search_tabs);

        searchRepository = new SearchRepository(requireContext());
        reelRepository = new ReelRepository(requireContext());
        recentSearchStore = new RecentSearchStore(requireContext());

        gridAdapter = new ExploreGridAdapter(explorePosts, post -> {
            Intent intent = new Intent(requireContext(), PostDetailActivity.class);
            intent.putExtra("post", post);
            intent.putExtra("post_id", post.getId());
            startActivity(intent);
        });

        rowAdapter = new SearchRowAdapter(searchRows, new SearchRowAdapter.Listener() {
            @Override
            public void onAccountClick(Profile profile) {
                Toast.makeText(requireContext(), "Selected @" + profile.getUsername(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onRecentClick(String query) {
                setQueryText(query);
                runSearch(query);
            }

            @Override
            public void onRecentRemove(String query) {
                recentSearchStore.removeSearch(query);
                showRecentRows();
            }

            @Override
            public void onClearAllRecents() {
                recentSearchStore.clearAll();
                showRecentRows();
            }

            @Override
            public void onPlaceClick() {
                if (Constants.DECORATIVE_UI) ToastUtil.showNotAvailable(requireContext());
            }
        });

        gridGapDecoration = new GridGapDecoration(
                getResources().getDimensionPixelSize(R.dimen.explore_grid_gap));

        setupChips(view);
        setupTabs();
        setupSearchField();
        setupEvents(view);

        // PAGE 3/4: keep the search row clear of the status bar (edge-to-edge window).
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), bars.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
        ViewCompat.requestApplyInsets(view);

        applyMode(false);
        showExploreGrid();
        loadExploreFeed();

        return view;
    }

    /* ------------------------------------------------------------------ */
    /* PAGE 3 — chips                                                      */
    /* ------------------------------------------------------------------ */

    private void setupChips(View root) {
        for (int i = 0; i < CHIP_IDS.length; i++) {
            final int chipIndex = i;
            TextView chip = root.findViewById(CHIP_IDS[i]);
            if (chip == null) continue;
            chip.setOnClickListener(v -> onChipClick(chipIndex));
        }
        // Shop is design-only.
        if (!Constants.DECORATIVE_UI) {
            View shop = root.findViewById(R.id.chipShop);
            if (shop != null) shop.setVisibility(View.GONE);
        }
        applyChipSelection();
    }

    private void onChipClick(int chipIndex) {
        if (chipIndex == CHIP_SHOP) {
            if (Constants.DECORATIVE_UI) ToastUtil.showNotAvailable(requireContext());
            return;
        }
        activeChip = (activeChip == chipIndex) ? CHIP_NONE : chipIndex;
        applyChipSelection();
        loadChipFeed();
    }

    private void applyChipSelection() {
        for (int i = 0; i < CHIP_IDS.length; i++) {
            TextView chip = rootView == null ? null : rootView.findViewById(CHIP_IDS[i]);
            if (chip == null) continue;
            boolean selected = (i == activeChip);
            chip.setBackgroundResource(selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip_outline);
            chip.setTextColor(selected
                    ? ContextCompat.getColor(requireContext(), R.color.color_background)
                    : ContextCompat.getColor(requireContext(), R.color.color_text_primary));
            if (CHIP_TAG_RES[i] == 0 && i != CHIP_SHOP) {
                // Chips with a leading icon (Reels) follow the same tint.
                TextViewCompat.setCompoundDrawableTintList(chip, android.content.res.ColorStateList.valueOf(
                        selected
                                ? ContextCompat.getColor(requireContext(), R.color.color_background)
                                : ContextCompat.getColor(requireContext(), R.color.color_text_primary)));
            }
        }
    }

    private void loadChipFeed() {
        if (activeChip == CHIP_REELS) {
            // Existing is_reel query.
            reelRepository.getReels(30, 0).observe(getViewLifecycleOwner(), resource -> {
                if (resource != null && resource.status == Resource.Status.SUCCESS && resource.data != null) {
                    explorePosts.clear();
                    explorePosts.addAll(resource.data);
                    gridAdapter.notifyDataSetChanged();
                }
            });
            return;
        }
        if (activeChip >= CHIP_FIRST_TAG) {
            // Existing tag search.
            String tag = getString(CHIP_TAG_RES[activeChip]);
            searchRepository.searchTags(tag).observe(getViewLifecycleOwner(), resource -> {
                if (resource != null && resource.status == Resource.Status.SUCCESS && resource.data != null) {
                    explorePosts.clear();
                    explorePosts.addAll(resource.data);
                    gridAdapter.notifyDataSetChanged();
                }
            });
            return;
        }
        loadExploreFeed();
    }

    /* ------------------------------------------------------------------ */
    /* PAGE 4 — tabs                                                       */
    /* ------------------------------------------------------------------ */

    private void setupTabs() {
        searchTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                searchTab = tab.getPosition();
                if (searchTab == TAB_AUDIO || searchTab == TAB_PLACES) {
                    if (Constants.DECORATIVE_UI) ToastUtil.showNotAvailable(requireContext());
                    searchRows.clear();
                    rowAdapter.notifyDataSetChanged();
                    showEmptyState(true);
                    return;
                }
                String query = etSearch.getText() == null ? "" : etSearch.getText().toString().trim();
                if (query.isEmpty()) {
                    showRecentRows();
                } else {
                    runSearch(query);
                }
            }

            @Override public void onTabUnselected(TabLayout.Tab tab) {}

            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    /* ------------------------------------------------------------------ */
    /* Search field + state                                                */
    /* ------------------------------------------------------------------ */

    private void setupSearchField() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (settingText) return;
                String q = s != null ? s.toString().trim() : "";
                ivSearchClear.setVisibility(q.isEmpty() ? View.GONE : View.VISIBLE);
                if (q.isEmpty()) {
                    if (isSearching) {
                        showRecentRows();
                    } else {
                        showExploreGrid();
                    }
                } else {
                    runSearch(q);
                }
            }
        });

        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                String q = etSearch.getText() == null ? "" : etSearch.getText().toString().trim();
                if (!q.isEmpty()) {
                    recentSearchStore.addSearch(q);
                    runSearch(q);
                }
                hideKeyboard();
                return true;
            }
            return false;
        });
    }

    private void setupEvents(View root) {
        // Tapping the idle field opens the search screen (PAGE 4).
        layoutSearchField.setOnClickListener(v -> enterSearchMode());
        etSearch.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && !isSearching) enterSearchMode();
        });
        ivSearchBack.setOnClickListener(v -> exitSearchMode());
        ivSearchClear.setOnClickListener(v -> {
            setQueryText("");
            if (isSearching) {
                showRecentRows();
            } else {
                showExploreGrid();
            }
        });
        btnSearchScan.setOnClickListener(v -> {
            if (Constants.DECORATIVE_UI) ToastUtil.showNotAvailable(requireContext());
        });
        if (!Constants.DECORATIVE_UI) btnSearchScan.setVisibility(View.GONE);
    }

    private void enterSearchMode() {
        if (isSearching) return;
        applyMode(true);
        etSearch.requestFocus();
        showKeyboard();
        showRecentRows();
    }

    private void exitSearchMode() {
        if (!isSearching) return;
        setQueryText("");
        etSearch.clearFocus();
        hideKeyboard();
        applyMode(false);
        loadChipFeed();
    }

    /**
     * PAGE 3 <-> PAGE 4 chrome. Idle: 38dp field with 12dp margins, chips, grid.
     * Focused: 56dp row, back arrow, 40dp field, tabs, rows.
     */
    private void applyMode(boolean searching) {
        isSearching = searching;

        int rowMargin = getResources().getDimensionPixelSize(R.dimen.explore_search_row_margin);
        int rowPaddingEnd = getResources().getDimensionPixelSize(R.dimen.search_row_padding_end);
        int backPaddingStart = getResources().getDimensionPixelSize(R.dimen.search_back_padding_start);
        int fieldMarginStart = getResources().getDimensionPixelSize(R.dimen.search_field_margin_start);

        ViewGroup.LayoutParams rowParams = searchRow.getLayoutParams();
        rowParams.height = searching
                ? getResources().getDimensionPixelSize(R.dimen.search_top_row_height)
                : ViewGroup.LayoutParams.WRAP_CONTENT;
        searchRow.setLayoutParams(rowParams);
        searchRow.setPadding(
                searching ? backPaddingStart : rowMargin,
                searching ? 0 : rowMargin,
                searching ? rowPaddingEnd : rowMargin,
                searching ? 0 : rowMargin);

        ViewGroup.LayoutParams fieldParams = layoutSearchField.getLayoutParams();
        fieldParams.height = getResources().getDimensionPixelSize(searching
                ? R.dimen.search_field_height_active
                : R.dimen.search_field_height_idle);
        ((LinearLayout.LayoutParams) fieldParams).setMarginStart(searching ? fieldMarginStart : 0);
        layoutSearchField.setLayoutParams(fieldParams);
        ((LinearLayout) layoutSearchField).setGravity(searching ? android.view.Gravity.START : android.view.Gravity.CENTER);

        etSearch.setLayoutParams(searching
                ? new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ivSearchBack.setVisibility(searching ? View.VISIBLE : View.GONE);
        btnSearchScan.setVisibility(searching || !Constants.DECORATIVE_UI ? View.GONE : View.VISIBLE);
        chipsScroll.setVisibility(searching ? View.GONE : View.VISIBLE);
        searchTabsContainer.setVisibility(searching ? View.VISIBLE : View.GONE);

        if (!searching) {
            ivSearchClear.setVisibility(View.GONE);
            showEmptyState(false);
        } else {
            ivSearchClear.setVisibility(etSearch.getText() != null && etSearch.getText().length() > 0
                    ? View.VISIBLE : View.GONE);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Data                                                                */
    /* ------------------------------------------------------------------ */

    private void showExploreGrid() {
        showEmptyState(false);
        rvExploreGrid.setAdapter(gridAdapter);
        GridLayoutManager layoutManager = new GridLayoutManager(getContext(), 3);
        rvExploreGrid.setLayoutManager(layoutManager);
        if (rvExploreGrid.getItemDecorationCount() == 0) {
            rvExploreGrid.addItemDecoration(gridGapDecoration);
        }
    }

    private void showSearchRows() {
        rvExploreGrid.setAdapter(rowAdapter);
        rvExploreGrid.setLayoutManager(new LinearLayoutManager(getContext()));
        if (rvExploreGrid.getItemDecorationCount() > 0) {
            rvExploreGrid.removeItemDecoration(gridGapDecoration);
        }
    }

    private void loadExploreFeed() {
        searchRepository.getExplorePosts(30, 0).observe(getViewLifecycleOwner(), resource -> {
            if (resource != null && resource.status == Resource.Status.SUCCESS && resource.data != null) {
                explorePosts.clear();
                explorePosts.addAll(resource.data);
                if (!isSearching) gridAdapter.notifyDataSetChanged();
            }
        });
    }

    /** Runs the existing account / tag search for the selected tab. */
    private void runSearch(String query) {
        if (!isSearching) applyMode(true);
        showSearchRows();

        if (searchTab == TAB_AUDIO || searchTab == TAB_PLACES) {
            searchRows.clear();
            rowAdapter.notifyDataSetChanged();
            showEmptyState(true);
            return;
        }

        boolean wantAccounts = (searchTab == TAB_TOP || searchTab == TAB_ACCOUNTS);
        boolean wantTags = (searchTab == TAB_TOP || searchTab == TAB_TAGS);

        accountResults.clear();
        tagResultCount = 0;
        rebuildRows();
        showEmptyState(false);

        if (wantAccounts) {
            searchRepository.searchUsers(query).observe(getViewLifecycleOwner(), resource -> {
                if (resource != null && resource.status == Resource.Status.SUCCESS) {
                    accountResults.clear();
                    if (resource.data != null) accountResults.addAll(resource.data);
                    rebuildRows();
                }
            });
        }
        if (wantTags) {
            searchRepository.searchTags(query).observe(getViewLifecycleOwner(), resource -> {
                if (resource != null && resource.status == Resource.Status.SUCCESS) {
                    tagResultCount = resource.data == null ? 0 : resource.data.size();
                    rebuildRows();
                }
            });
        }
    }

    private void rebuildRows() {
        searchRows.clear();
        for (Profile profile : accountResults) {
            searchRows.add(SearchRow.account(profile));
        }
        if (searchTab == TAB_TOP || searchTab == TAB_TAGS) {
            String query = etSearch.getText() == null ? "" : etSearch.getText().toString().trim();
            if (!query.isEmpty()) {
                searchRows.add(SearchRow.tag(
                        query.replace("#", ""),
                        getString(R.string.tag_posts_count, formatTagCount(tagResultCount))));
            }
        }
        rowAdapter.notifyDataSetChanged();
        showEmptyState(searchRows.isEmpty());
    }

    /** PAGE 4 — focused with an empty query: existing recents logic. */
    private void showRecentRows() {
        showSearchRows();
        if (searchTab == TAB_AUDIO || searchTab == TAB_PLACES) {
            searchRows.clear();
            rowAdapter.notifyDataSetChanged();
            showEmptyState(true);
            return;
        }
        searchRows.clear();
        List<String> recents = recentSearchStore.getRecentSearches();
        if (!recents.isEmpty()) {
            searchRows.add(SearchRow.header());
            for (String query : recents) {
                searchRows.add(SearchRow.recent(query));
            }
        }
        rowAdapter.notifyDataSetChanged();
        // No recents yet -> nothing to show, and no "No results" notice either.
        showEmptyState(false);
    }

    private void showEmptyState(boolean show) {
        if (searchEmptyState == null) return;
        searchEmptyState.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            boolean decorative = searchTab == TAB_AUDIO || searchTab == TAB_PLACES;
            ivSearchEmptyIcon.setImageResource(decorative
                    ? (searchTab == TAB_AUDIO ? R.drawable.ic_reels : R.drawable.ic_location)
                    : R.drawable.ic_search);
            tvSearchEmptyTitle.setText(R.string.search_no_results);
        }
    }

    private void setQueryText(String text) {
        settingText = true;
        etSearch.setText(text);
        etSearch.setSelection(etSearch.getText().length());
        settingText = false;
        ivSearchClear.setVisibility(text == null || text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /**
     * Counts for tag rows. The app has no tag counter (the existing tag search
     * returns the matching posts), so the count is the number of results that
     * query returned.
     */
    private String formatTagCount(int count) {
        if (count < 100) {
            return getString(R.string.tag_count_fewer_than_100);
        }
        if (count < 1000) {
            return String.format(Locale.US, "%d+", (count / 100) * 100);
        }
        if (count < 1000000) {
            return String.format(Locale.US, "%dK", count / 1000);
        }
        return String.format(Locale.US, "%.1fM", count / 1000000.0);
    }

    private void showKeyboard() {
        etSearch.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) requireContext()
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT);
        }, 100);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) requireContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isSearching) loadExploreFeed();
    }

    /* ------------------------------------------------------------------ */
    /* Rows                                                                */
    /* ------------------------------------------------------------------ */

    /** One line of the PAGE 4 result list. */
    static class SearchRow {
        static final int ACCOUNT = 0;
        static final int TAG = 1;
        static final int PLACE = 2;
        static final int RECENT = 3;
        static final int HEADER = 4;

        final int type;
        final Profile profile;
        final String title;
        final String subtitle;

        private SearchRow(int type, Profile profile, String title, String subtitle) {
            this.type = type;
            this.profile = profile;
            this.title = title;
            this.subtitle = subtitle;
        }

        static SearchRow account(Profile profile) {
            return new SearchRow(ACCOUNT, profile, profile.getUsername(), profile.getFullName());
        }

        static SearchRow tag(String tag, String subtitle) {
            return new SearchRow(TAG, null, "#" + tag, subtitle);
        }

        static SearchRow recent(String query) {
            return new SearchRow(RECENT, null, query, null);
        }

        static SearchRow header() {
            return new SearchRow(HEADER, null, null, null);
        }
    }

    static class SearchRowAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        interface Listener {
            void onAccountClick(Profile profile);
            void onRecentClick(String query);
            void onRecentRemove(String query);
            void onClearAllRecents();
            void onPlaceClick();
        }

        private final List<SearchRow> items;
        private final Listener listener;

        SearchRowAdapter(List<SearchRow> items, Listener listener) {
            this.items = items;
            this.listener = listener;
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position).type;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == SearchRow.HEADER) {
                return new HeaderHolder(inflater.inflate(R.layout.item_search_recent_header, parent, false));
            }
            return new RowHolder(inflater.inflate(R.layout.item_search_row, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            SearchRow row = items.get(position);
            if (holder instanceof HeaderHolder) {
                ((HeaderHolder) holder).tvClearAll.setOnClickListener(v -> {
                    if (listener != null) listener.onClearAllRecents();
                });
                return;
            }
            RowHolder h = (RowHolder) holder;

            // Reset the recycled row.
            h.ivAvatar.setVisibility(View.VISIBLE);
            h.layoutCircle.setVisibility(View.GONE);
            h.ivVerified.setVisibility(View.GONE);
            h.ivTrailing.setVisibility(View.GONE);
            h.tvSubtitle.setVisibility(View.GONE);
            h.tvSubtitle.setText("");
            h.tvTitle.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));

            switch (row.type) {
                case SearchRow.ACCOUNT:
                    h.tvTitle.setText(row.title);
                    if (row.subtitle != null && !row.subtitle.isEmpty()) {
                        h.tvSubtitle.setText(row.subtitle);
                        h.tvSubtitle.setVisibility(View.VISIBLE);
                    }
                    if (row.profile != null && row.profile.isVerified()) {
                        h.ivVerified.setVisibility(View.VISIBLE);
                    }
                    if (row.profile != null && row.profile.getAvatarUrl() != null
                            && !row.profile.getAvatarUrl().isEmpty()) {
                        com.insta.clone.util.GlideUtils.loadAvatar(h.ivAvatar, row.profile.getAvatarUrl());
                    } else {
                        h.ivAvatar.setImageResource(R.drawable.ic_person_placeholder);
                    }
                    h.itemView.setOnClickListener(v -> {
                        if (listener != null && row.profile != null) listener.onAccountClick(row.profile);
                    });
                    break;

                case SearchRow.TAG:
                    showCircleRow(h, R.drawable.ic_hashtag);
                    h.tvTitle.setText(row.title);
                    h.tvSubtitle.setText(row.subtitle);
                    h.tvSubtitle.setVisibility(View.VISIBLE);
                    h.itemView.setOnClickListener(null);
                    break;

                case SearchRow.PLACE:
                    showCircleRow(h, R.drawable.ic_location);
                    h.tvTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                    h.tvTitle.setText(row.title);
                    h.tvSubtitle.setText(row.subtitle);
                    h.tvSubtitle.setVisibility(View.VISIBLE);
                    h.itemView.setOnClickListener(v -> {
                        if (listener != null) listener.onPlaceClick();
                    });
                    break;

                case SearchRow.RECENT:
                    showCircleRow(h, R.drawable.ic_search);
                    h.tvTitle.setText(row.title);
                    h.ivTrailing.setVisibility(View.VISIBLE);
                    h.itemView.setOnClickListener(v -> {
                        if (listener != null) listener.onRecentClick(row.title);
                    });
                    h.ivTrailing.setOnClickListener(v -> {
                        if (listener != null) listener.onRecentRemove(row.title);
                    });
                    break;

                default:
                    break;
            }
        }

        private void showCircleRow(RowHolder h, int iconRes) {
            h.ivAvatar.setVisibility(View.GONE);
            h.layoutCircle.setVisibility(View.VISIBLE);
            h.ivCircleIcon.setImageResource(iconRes);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class RowHolder extends RecyclerView.ViewHolder {
            final ImageView ivAvatar;
            final View layoutCircle;
            final ImageView ivCircleIcon;
            final TextView tvTitle;
            final ImageView ivVerified;
            final TextView tvSubtitle;
            final ImageView ivTrailing;

            RowHolder(@NonNull View itemView) {
                super(itemView);
                ivAvatar = itemView.findViewById(R.id.ivRowAvatar);
                layoutCircle = itemView.findViewById(R.id.layoutRowCircle);
                ivCircleIcon = itemView.findViewById(R.id.ivRowCircleIcon);
                tvTitle = itemView.findViewById(R.id.tvRowTitle);
                ivVerified = itemView.findViewById(R.id.ivRowVerified);
                tvSubtitle = itemView.findViewById(R.id.tvRowSubtitle);
                ivTrailing = itemView.findViewById(R.id.ivRowTrailing);
            }
        }

        static class HeaderHolder extends RecyclerView.ViewHolder {
            final TextView tvClearAll;

            HeaderHolder(@NonNull View itemView) {
                super(itemView);
                tvClearAll = itemView.findViewById(R.id.tvRecentClearAll);
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /* Grid                                                                */
    /* ------------------------------------------------------------------ */

    /** PAGE 3 grid gutters: 1dp split across neighbours, columns stay equal. */
    static class GridGapDecoration extends RecyclerView.ItemDecoration {
        private final int leadingGap;
        private final int trailingGap;

        GridGapDecoration(int gapPx) {
            this.leadingGap = gapPx / 2;
            this.trailingGap = gapPx - leadingGap;
        }

        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                                   @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            outRect.set(leadingGap, leadingGap, trailingGap, trailingGap);
        }
    }

    static class ExploreGridAdapter extends RecyclerView.Adapter<ExploreGridAdapter.Holder> {
        private final List<Post> items;
        private final OnPostClickListener listener;

        interface OnPostClickListener { void onPostClick(Post post); }

        ExploreGridAdapter(List<Post> items, OnPostClickListener listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_saved_media, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Post p = items.get(position);
            String url = p.getThumbnailUrl();
            if (url == null || url.isEmpty()) url = p.getFirstMediaUrl();

            com.insta.clone.util.GlideUtils.load(holder.ivMedia, "posts", url);

            // PAGE 3: carousel / reel markers (real post data, not decoration).
            boolean carousel = p.getMediaUrls() != null && p.getMediaUrls().size() > 1;
            holder.ivMultiBadge.setVisibility(carousel ? View.VISIBLE : View.GONE);
            holder.ivReelBadge.setVisibility(p.isReel() ? View.VISIBLE : View.GONE);

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onPostClick(p);
            });
        }

        @Override public int getItemCount() { return items.size(); }

        static class Holder extends RecyclerView.ViewHolder {
            final ImageView ivMedia;
            final ImageView ivMultiBadge;
            final ImageView ivReelBadge;
            Holder(@NonNull View itemView) {
                super(itemView);
                ivMedia = itemView.findViewById(R.id.ivMedia);
                ivMultiBadge = itemView.findViewById(R.id.ivMultiBadge);
                ivReelBadge = itemView.findViewById(R.id.ivReelBadge);
            }
        }
    }
}
