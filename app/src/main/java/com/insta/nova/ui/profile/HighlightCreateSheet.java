package com.insta.nova.ui.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.insta.nova.R;
import com.insta.nova.data.local.LocalSocialStore;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.model.Highlight;
import com.insta.nova.data.model.HighlightItem;
import com.insta.nova.data.model.Story;
import com.insta.nova.databinding.BottomSheetHighlightCreateBinding;
import com.insta.nova.util.TimeUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Creates a highlight from archived (past) stories: name field plus a selectable
 * grid of thumbnails. Saved locally through {@link LocalSocialStore}.
 */
public class HighlightCreateSheet extends BottomSheetDialogFragment {

    private BottomSheetHighlightCreateBinding binding;
    private final List<Story> stories = new ArrayList<>();
    private final Set<Integer> selected = new HashSet<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetHighlightCreateBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        LocalSocialStore store = new LocalSocialStore(requireContext());
        stories.addAll(store.getPastStories());

        PickerAdapter adapter = new PickerAdapter();
        binding.rvHighlightStories.setLayoutManager(new GridLayoutManager(requireContext(), 3));
        binding.rvHighlightStories.setAdapter(adapter);

        binding.btnSaveHighlight.setOnClickListener(v -> save(store));
    }

    private void save(LocalSocialStore store) {
        if (selected.isEmpty()) {
            Toast.makeText(requireContext(), R.string.highlight_pick_one, Toast.LENGTH_SHORT).show();
            return;
        }
        String name = binding.etHighlightName.getText().toString().trim();
        if (name.isEmpty()) name = getString(R.string.highlight_default_name);

        String id = "local_highlight_" + UUID.randomUUID();
        Highlight highlight = new Highlight(id,
                SessionManager.getInstance(requireContext()).getUserId(), name, null);
        highlight.setCreatedAt(String.valueOf(System.currentTimeMillis()));

        List<HighlightItem> items = new ArrayList<>();
        String cover = null;
        for (int i = 0; i < stories.size(); i++) {
            if (!selected.contains(i)) continue;
            Story story = stories.get(i);
            if (cover == null) cover = story.getMediaUrl();
            items.add(new HighlightItem(id + "_" + i, id, story.getMediaUrl()));
        }
        highlight.setCoverUrl(cover);
        highlight.setItems(items);

        store.addHighlight(highlight);
        Toast.makeText(requireContext(), R.string.highlight_saved, Toast.LENGTH_SHORT).show();
        dismiss();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private class PickerAdapter extends RecyclerView.Adapter<PickerAdapter.Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_highlight_pick, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Story story = stories.get(position);
            Glide.with(holder.ivThumb).load(story.getMediaUrl()).into(holder.ivThumb);
            holder.cbSelect.setChecked(selected.contains(position));
            holder.itemView.setOnClickListener(v -> {
                if (selected.contains(position)) selected.remove(position);
                else selected.add(position);
                notifyItemChanged(position);
            });
        }

        @Override
        public int getItemCount() {
            return stories.size();
        }

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
