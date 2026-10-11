package com.insta.clone.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.insta.clone.R;
import com.insta.clone.data.model.StoryGroup;
import com.insta.clone.databinding.ItemStoryTrayBinding;
import com.insta.clone.ui.widget.StoryRingView;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for horizontal Story Tray displayed at top of Home feed.
 */
public class StoryTrayAdapter extends RecyclerView.Adapter<StoryTrayAdapter.ViewHolder> {

    private final List<StoryGroup> items = new ArrayList<>();
    private final OnStoryClickListener listener;

    public interface OnStoryClickListener {
        void onStoryClick(StoryGroup group, int position);
        void onAddStoryClick();
    }

    public StoryTrayAdapter(OnStoryClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<StoryGroup> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemStoryTrayBinding binding = ItemStoryTrayBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StoryGroup group = items.get(position);
        holder.bind(group, position, listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemStoryTrayBinding binding;

        ViewHolder(ItemStoryTrayBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(StoryGroup group, int position, OnStoryClickListener listener) {
            binding.tvUsername.setText(group.isOwner() ? binding.getRoot().getContext().getString(R.string.your_story) : group.getUsername());
            binding.storyRingView.loadAvatar(group.getAvatarUrl());

            if (group.isOwner()) {
                if (group.hasUnseen()) {
                    binding.storyRingView.setRingState(StoryRingView.RingState.UNSEEN);
                    binding.ivAddBadge.setVisibility(View.GONE);
                } else {
                    binding.storyRingView.setRingState(StoryRingView.RingState.NONE);
                    binding.ivAddBadge.setVisibility(View.VISIBLE);
                }
            } else {
                binding.ivAddBadge.setVisibility(View.GONE);
                binding.storyRingView.setRingState(group.hasUnseen() ? StoryRingView.RingState.UNSEEN : StoryRingView.RingState.SEEN);
            }

            binding.getRoot().setOnClickListener(v -> {
                if (group.isOwner() && !group.hasUnseen()) {
                    if (listener != null) listener.onAddStoryClick();
                } else {
                    if (listener != null) listener.onStoryClick(group, position);
                }
            });
        }
    }
}
