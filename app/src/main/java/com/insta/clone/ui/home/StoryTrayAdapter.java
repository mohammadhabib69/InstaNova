package com.insta.clone.ui.home;

import android.content.Context;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
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
            for (StoryGroup group : newItems) {
                if (group != null) items.add(group);
            }
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
            if (group == null) {
                binding.getRoot().setVisibility(View.GONE);
                return;
            }
            binding.getRoot().setVisibility(View.VISIBLE);
            Context context = binding.getRoot().getContext();
            boolean owner = group.isOwner();
            boolean unseen = group.hasUnseen();
            String username = group.getUsername() == null ? "" : group.getUsername();
            binding.tvUsername.setText(owner ? context.getString(R.string.your_story) : username);
            binding.storyRingView.loadAvatar(group.getAvatarUrl());

            if (owner) {
                // Active story -> gradient ring, no badge. No active story ->
                // grey ring + the 22dp "add" badge on the avatar corner.
                binding.storyRingView.setRingState(unseen
                        ? StoryRingView.RingState.UNSEEN
                        : StoryRingView.RingState.SEEN);
                binding.ivAddBadge.setVisibility(unseen ? View.GONE : View.VISIBLE);
            } else {
                binding.ivAddBadge.setVisibility(View.GONE);
                binding.storyRingView.setRingState(unseen
                        ? StoryRingView.RingState.UNSEEN
                        : StoryRingView.RingState.SEEN);
            }

            // Unseen -> textPrimary, seen -> textSecondary ("Your story" stays
            // textPrimary so it never greys out).
            boolean secondaryLabel = !owner && !unseen;
            binding.tvUsername.setTextColor(themeColor(context,
                    secondaryLabel ? R.attr.themeTextSecondary : R.attr.themeTextPrimary,
                    secondaryLabel ? R.color.color_text_secondary : R.color.color_text_primary));

            binding.getRoot().setOnClickListener(v -> {
                if (group.isOwner() && !group.hasUnseen()) {
                    if (listener != null) listener.onAddStoryClick();
                } else {
                    if (listener != null) listener.onStoryClick(group, position);
                }
            });
        }

        /** Resolves a PAGE 0 semantic theme attribute to a concrete color. */
        private int themeColor(Context context, int attr, int fallbackColorRes) {
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
    }
}
