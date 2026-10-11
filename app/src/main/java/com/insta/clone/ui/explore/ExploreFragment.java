package com.insta.clone.ui.explore;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.insta.clone.R;
import com.insta.clone.data.model.Post;
import com.insta.clone.data.model.Profile;
import com.insta.clone.data.repository.SearchRepository;
import com.insta.clone.ui.post.PostDetailActivity;
import com.insta.clone.util.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * Explore & Search Fragment with 3-column media grid and live account search.
 */
public class ExploreFragment extends Fragment {

    private EditText etSearch;
    private RecyclerView rvExploreGrid;
    private SearchRepository searchRepository;

    private final List<Post> explorePosts = new ArrayList<>();
    private final List<Profile> searchResults = new ArrayList<>();
    private ExploreGridAdapter gridAdapter;
    private SearchResultsAdapter searchAdapter;
    private boolean isSearching = false;

    public ExploreFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_explore, container, false);
        etSearch = view.findViewById(R.id.et_search);
        rvExploreGrid = view.findViewById(R.id.rv_explore_grid);

        searchRepository = new SearchRepository(requireContext());

        gridAdapter = new ExploreGridAdapter(explorePosts, post -> {
            Intent intent = new Intent(requireContext(), PostDetailActivity.class);
            intent.putExtra("post", post);
            intent.putExtra("post_id", post.getId());
            startActivity(intent);
        });

        searchAdapter = new SearchResultsAdapter(searchResults, profile -> {
            Toast.makeText(requireContext(), "Selected @" + profile.getUsername(), Toast.LENGTH_SHORT).show();
        });

        showExploreGrid();
        setupSearchListener();
        loadExploreFeed();

        return view;
    }

    private void showExploreGrid() {
        isSearching = false;
        GridLayoutManager layoutManager = new GridLayoutManager(getContext(), 3);
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return (position % 6 == 0) ? 2 : 1;
            }
        });
        rvExploreGrid.setLayoutManager(layoutManager);
        rvExploreGrid.setAdapter(gridAdapter);
    }

    private void showSearchResultsList() {
        isSearching = true;
        rvExploreGrid.setLayoutManager(new LinearLayoutManager(getContext()));
        rvExploreGrid.setAdapter(searchAdapter);
    }

    private void loadExploreFeed() {
        searchRepository.getExplorePosts(30, 0).observe(getViewLifecycleOwner(), resource -> {
            if (resource != null && resource.status == Resource.Status.SUCCESS && resource.data != null) {
                explorePosts.clear();
                explorePosts.addAll(resource.data);
                if (!isSearching) {
                    gridAdapter.notifyDataSetChanged();
                }
            }
        });
    }

    private void setupSearchListener() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String q = s != null ? s.toString().trim() : "";
                if (q.isEmpty()) {
                    showExploreGrid();
                } else {
                    if (!isSearching) showSearchResultsList();
                    searchRepository.searchUsers(q).observe(getViewLifecycleOwner(), resource -> {
                        if (resource != null && resource.status == Resource.Status.SUCCESS && resource.data != null) {
                            searchResults.clear();
                            searchResults.addAll(resource.data);
                            searchAdapter.notifyDataSetChanged();
                        }
                    });
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isSearching) loadExploreFeed();
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

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onPostClick(p);
            });
        }

        @Override public int getItemCount() { return items.size(); }

        static class Holder extends RecyclerView.ViewHolder {
            final ImageView ivMedia;
            Holder(@NonNull View itemView) {
                super(itemView);
                ivMedia = itemView.findViewById(R.id.ivMedia);
            }
        }
    }

    static class SearchResultsAdapter extends RecyclerView.Adapter<SearchResultsAdapter.Holder> {
        private final List<Profile> items;
        private final OnProfileClickListener listener;

        interface OnProfileClickListener { void onProfileClick(Profile profile); }

        SearchResultsAdapter(List<Profile> items, OnProfileClickListener listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_story_tray, parent, false);
            // Re-use user avatar + username styling
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Profile profile = items.get(position);
            holder.tvUsername.setText(profile.getUsername());
            if (profile.getAvatarUrl() != null && !profile.getAvatarUrl().isEmpty()) {
                com.insta.clone.util.GlideUtils.loadAvatar(holder.ivAvatar, profile.getAvatarUrl());
            } else {
                holder.ivAvatar.setImageResource(R.drawable.ic_person_placeholder);
            }
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onProfileClick(profile);
            });
        }

        @Override public int getItemCount() { return items.size(); }

        static class Holder extends RecyclerView.ViewHolder {
            final ImageView ivAvatar;
            final TextView tvUsername;
            Holder(@NonNull View itemView) {
                super(itemView);
                ivAvatar = itemView.findViewById(R.id.iv_avatar);
                tvUsername = itemView.findViewById(R.id.tv_username);
            }
        }
    }
}
