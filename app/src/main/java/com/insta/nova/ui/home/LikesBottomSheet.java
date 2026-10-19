package com.insta.nova.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.insta.nova.R;
import com.insta.nova.data.local.LocalSocialStore;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.databinding.BottomSheetLikesBinding;
import com.insta.nova.databinding.ItemLikeRowBinding;
import com.insta.nova.ui.chat.UserProfileActivity;
import com.insta.nova.util.GlideUtils;
import java.util.ArrayList;
import java.util.List;

/**
 * Instagram-style likes list: every liker with avatar, username, name and a
 * Follow/Following toggle backed by the local follow set.
 */
public class LikesBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_POST_ID = "post_id";
    private static final String ARG_COUNT = "count";
    private static final String ARG_LIKED = "liked";

    private BottomSheetLikesBinding binding;
    private LocalSocialStore store;
    private final List<LocalSocialStore.Liker> likers = new ArrayList<>();

    public static LikesBottomSheet newInstance(String postId, int count, boolean likedByMe) {
        LikesBottomSheet sheet = new LikesBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_POST_ID, postId);
        args.putInt(ARG_COUNT, count);
        args.putBoolean(ARG_LIKED, likedByMe);
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetLikesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        store = new LocalSocialStore(requireContext());

        String postId = getArguments() == null ? null : getArguments().getString(ARG_POST_ID);
        int count = getArguments() == null ? 0 : getArguments().getInt(ARG_COUNT, 0);
        boolean likedByMe = getArguments() != null && getArguments().getBoolean(ARG_LIKED, false);

        SessionManager session = SessionManager.getInstance(requireContext());
        likers.addAll(store.getLikers(postId, count, session.getUserId(),
                session.getUsername(), session.getAvatarUrl(), likedByMe));

        binding.tvLikesTitle.setText(likers.isEmpty()
                ? getString(R.string.likes)
                : getString(R.string.likes) + " · " + likers.size());

        binding.rvLikers.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvLikers.setAdapter(new LikerAdapter());

        binding.tvLikesEmpty.setVisibility(likers.isEmpty() ? View.VISIBLE : View.GONE);
        binding.rvLikers.setVisibility(likers.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private class LikerAdapter extends RecyclerView.Adapter<LikerAdapter.Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemLikeRowBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            LocalSocialStore.Liker liker = likers.get(position);
            GlideUtils.loadAvatar(holder.binding.ivAvatar, liker.avatarUrl);
            holder.binding.tvUsername.setText(liker.username);
            holder.binding.tvName.setText(liker.name == null ? "" : liker.name);

            boolean following = store.isFollowing(liker.id);
            applyFollowStyle(holder, following);
            holder.binding.btnFollow.setOnClickListener(v -> {
                boolean next = !store.isFollowing(liker.id);
                store.setFollowing(liker.id, next);
                applyFollowStyle(holder, next);
            });

            holder.binding.getRoot().setOnClickListener(v -> {
                if (liker.id == null || liker.id.startsWith("demo-")) return;
                Intent intent = new Intent(requireContext(), UserProfileActivity.class);
                intent.putExtra(UserProfileActivity.EXTRA_USER_ID, liker.id);
                intent.putExtra(UserProfileActivity.EXTRA_USERNAME, liker.username);
                startActivity(intent);
            });
        }

        private void applyFollowStyle(Holder holder, boolean following) {
            holder.binding.btnFollow.setText(following
                    ? R.string.notification_following : R.string.notification_follow);
            holder.binding.btnFollow.setBackgroundResource(following
                    ? R.drawable.bg_notification_following : R.drawable.bg_notification_follow);
            holder.binding.btnFollow.setTextColor(androidx.core.content.ContextCompat.getColor(
                    requireContext(), following ? R.color.color_text_primary : R.color.white));
        }

        @Override
        public int getItemCount() {
            return likers.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final ItemLikeRowBinding binding;

            Holder(ItemLikeRowBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
