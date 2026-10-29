package com.insta.nova.ui.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.insta.nova.R;
import com.insta.nova.data.local.LocalSocialStore;
import com.insta.nova.data.model.Highlight;
import com.insta.nova.data.model.HighlightItem;
import com.insta.nova.data.model.Story;
import com.insta.nova.data.repository.HighlightRepository;
import com.insta.nova.util.Resource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Edits an existing highlight: removes single slides, or appends more past stories.
 *
 * <p>Before this a highlight was fixed at creation time — a wrong slide could only be fixed by
 * deleting the whole highlight and building it again, which is why the tile menu had no "edit".
 */
public class HighlightEditSheet extends BottomSheetDialogFragment {

    public static final String ARG_HIGHLIGHT_ID = "ARG_HIGHLIGHT_ID";

    private View root;
    private final List<HighlightItem> current = new ArrayList<>();
    private final List<Story> addable = new ArrayList<>();
    private final Set<Integer> selected = new HashSet<>();
    private Highlight highlight;
    private String highlightId;
    private boolean serverBacked;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.bottom_sheet_highlight_edit, container, false);
        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        highlightId = getArguments() == null ? null : getArguments().getString(ARG_HIGHLIGHT_ID);
        LocalSocialStore store = new LocalSocialStore(requireContext());
        highlight = highlightId == null ? null : store.findHighlight(highlightId);
        if (highlight == null || highlightId == null) {
            dismiss();
            return;
        }
        serverBacked = !highlightId.startsWith("local_highlight_");

        ((TextView) root.findViewById(R.id.tvEditTitle)).setText(highlight.getTitle());
        ((RecyclerView) root.findViewById(R.id.rvCurrentItems)).setLayoutManager(
                new GridLayoutManager(requireContext(), 3));
        ((RecyclerView) root.findViewById(R.id.rvAddableStories)).setLayoutManager(
                new GridLayoutManager(requireContext(), 3));

        loadCurrent(store);
        loadAddable();

        root.findViewById(R.id.btnAddSelected).setOnClickListener(v -> addSelected());
    }

    private void loadCurrent(LocalSocialStore store) {
        if (highlight.getItems() != null) current.addAll(highlight.getItems());
        if (serverBacked) {
            new HighlightRepository(requireContext()).getHighlightItems(highlightId)
                    .observe(getViewLifecycleOwner(), resource -> {
                        if (resource == null || resource.status == Resource.Status.LOADING) return;
                        if (resource.status == Resource.Status.SUCCESS && resource.data != null) {
                            current.clear();
                            current.addAll(resource.data);
                            store.setHighlightItems(highlightId, new ArrayList<>(current));
                            bindCurrent();
                            // The exclusion list was built before these rows arrived, so a slide
                            // already in the highlight would be offered again — and added twice.
                            loadAddable();
                        }
                    });
        }
        bindCurrent();
    }

    private void bindCurrent() {
        RecyclerView rv = root.findViewById(R.id.rvCurrentItems);
        rv.setAdapter(new CurrentAdapter());
        root.findViewById(R.id.tvEmptyItems)
                .setVisibility(current.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void loadAddable() {
        LocalSocialStore store = new LocalSocialStore(requireContext());
        // Two different local files can resolve to the same published URL, so compare both the
        // raw path and what it resolves to. Otherwise the picker offers a slide that is already in
        // the highlight and the user appears to add nothing.
        Set<String> inHighlight = new HashSet<>();
        for (HighlightItem item : current) {
            if (item == null || item.getMediaUrl() == null) continue;
            inHighlight.add(item.getMediaUrl());
            String resolved = resolveMedia(item.getMediaUrl());
            if (resolved != null) inHighlight.add(resolved);
        }
        for (Story story : store.getPastStories()) {
            if (story == null || story.getMediaUrl() == null) continue;
            if (inHighlight.contains(story.getMediaUrl())) continue;
            String resolved = resolveMedia(story.getMediaUrl());
            if (resolved != null && inHighlight.contains(resolved)) continue;
            addable.add(story);
        }
        RecyclerView rv = root.findViewById(R.id.rvAddableStories);
        rv.setAdapter(new AddableAdapter());
        root.findViewById(R.id.tvAddHeader).setVisibility(addable.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void removeItem(HighlightItem item) {
        if (item == null) return;
        current.remove(item);
        bindCurrent();
        LocalSocialStore store = new LocalSocialStore(requireContext());
        List<HighlightItem> mirror = new ArrayList<>(current);
        store.setHighlightItems(highlightId, mirror);

        if (!serverBacked || item.getId() == null || item.getId().isEmpty()) {
            Toast.makeText(requireContext(), "Story removed", Toast.LENGTH_SHORT).show();
            loadAddable();
            return;
        }
        new HighlightRepository(requireContext()).deleteHighlightItem(item.getId())
                .observe(getViewLifecycleOwner(), resource -> {
                    if (resource == null || resource.status == Resource.Status.LOADING) return;
                    if (resource.status == Resource.Status.SUCCESS) {
                        syncCover();
                        Toast.makeText(requireContext(), "Story removed", Toast.LENGTH_SHORT).show();
                        loadAddable();
                    } else {
                        // Put it back: the user is looking at a list that no longer matches reality.
                        current.add(item);
                        bindCurrent();
                        Toast.makeText(requireContext(),
                                resource.message == null ? "Could not remove that story."
                                        : resource.message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void addSelected() {
        if (selected.isEmpty()) {
            Toast.makeText(requireContext(), R.string.highlight_pick_one, Toast.LENGTH_SHORT).show();
            return;
        }
        List<String> resolved = new ArrayList<>();
        List<HighlightItem> added = new ArrayList<>();
        Set<String> already = new HashSet<>();
        for (HighlightItem item : current) {
            if (item != null && item.getMediaUrl() != null) already.add(item.getMediaUrl());
        }
        for (int index : selected) {
            Story story = addable.get(index);
            String url = resolveMedia(story.getMediaUrl());
            if (url == null || already.contains(url)) continue;
            resolved.add(url);
            added.add(new HighlightItem(null, highlightId, url));
        }
        if (resolved.isEmpty()) {
            Toast.makeText(requireContext(),
                    "Those stories never finished uploading, so they cannot be shared yet.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        LocalSocialStore store = new LocalSocialStore(requireContext());
        final List<HighlightItem> next = new ArrayList<>(current);
        next.addAll(added);
        store.setHighlightItems(highlightId, next);

        if (!serverBacked) {
            current.clear();
            current.addAll(next);
            bindCurrent();
            selected.clear();
            loadAddable();
            Toast.makeText(requireContext(), "Added", Toast.LENGTH_SHORT).show();
            return;
        }
        new HighlightRepository(requireContext()).addHighlightItems(highlightId, resolved)
                .observe(getViewLifecycleOwner(), resource -> {
                    if (resource == null || resource.status == Resource.Status.LOADING) return;
                    if (resource.status == Resource.Status.SUCCESS) {
                        current.clear();
                        current.addAll(next);
                        bindCurrent();
                        syncCover();
                        selected.clear();
                        loadAddable();
                        Toast.makeText(requireContext(), "Added", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(),
                                resource.message == null ? "Could not add those stories."
                                        : resource.message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    /** The first slide is the cover; after an edit it may be a different file. */
    private void syncCover() {
        if (!serverBacked || current.isEmpty()) return;
        String first = current.get(0) == null ? null : current.get(0).getMediaUrl();
        if (first == null) return;
        new HighlightRepository(requireContext()).setCover(highlightId, first);
    }

    private String resolveMedia(String url) {
        if (url == null || url.isEmpty()) return null;
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        String name = url.substring(url.lastIndexOf('/') + 1);
        return requireContext().getSharedPreferences("story_urls",
                        android.content.Context.MODE_PRIVATE)
                .getString("story_url_" + name, null);
    }

    private class CurrentAdapter extends RecyclerView.Adapter<CurrentAdapter.Holder> {
        @NonNull @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_highlight_manage, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            HighlightItem item = current.get(position);
            Glide.with(holder.ivThumb).load(item.getMediaUrl()).into(holder.ivThumb);
            holder.btnRemove.setOnClickListener(v -> removeItem(item));
        }

        @Override
        public int getItemCount() { return current.size(); }

        class Holder extends RecyclerView.ViewHolder {
            final ImageView ivThumb;
            final ImageButton btnRemove;
            Holder(@NonNull View itemView) {
                super(itemView);
                ivThumb = itemView.findViewById(R.id.ivThumb);
                btnRemove = itemView.findViewById(R.id.btnRemoveItem);
            }
        }
    }

    private class AddableAdapter extends RecyclerView.Adapter<AddableAdapter.Holder> {
        @NonNull @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_highlight_pick, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Story story = addable.get(position);
            Glide.with(holder.ivThumb).load(story.getMediaUrl()).into(holder.ivThumb);
            holder.cbSelect.setChecked(selected.contains(position));
            holder.itemView.setOnClickListener(v -> {
                if (selected.contains(position)) selected.remove(position);
                else selected.add(position);
                notifyItemChanged(position);
            });
        }

        @Override
        public int getItemCount() { return addable.size(); }

        class Holder extends RecyclerView.ViewHolder {
            final ImageView ivThumb;
            final CheckBox cbSelect;
            Holder(@NonNull View itemView) {
                super(itemView);
                ivThumb = itemView.findViewById(R.id.ivThumb);
                cbSelect = itemView.findViewById(R.id.cbSelect);
            }
        }
    }
}
