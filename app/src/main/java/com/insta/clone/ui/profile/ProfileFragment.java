package com.insta.clone.ui.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.tabs.TabLayout;
import com.insta.clone.R;
import com.bumptech.glide.Glide;
import com.insta.clone.data.local.SessionManager;
import com.insta.clone.data.repository.ProfileRepository;
import com.insta.clone.util.Resource;
import com.insta.clone.ui.settings.SyncTestActivity;
import android.content.Intent;

/**
 * User Profile Fragment with stats, highlights, bio, and multi-tab post grid.
 */
public class ProfileFragment extends Fragment {

    private ImageView ivAvatar;
    private TextView tvUsername, tvPostsCount, tvFollowersCount, tvFollowingCount, tvBio;
    private TabLayout tabLayout;
    private RecyclerView rvMediaGrid;

    public ProfileFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);
        ivAvatar = view.findViewById(R.id.iv_avatar);
        tvUsername = view.findViewById(R.id.tv_username);
        tvPostsCount = view.findViewById(R.id.tv_posts_count);
        tvFollowersCount = view.findViewById(R.id.tv_followers_count);
        tvFollowingCount = view.findViewById(R.id.tv_following_count);
        tvBio = view.findViewById(R.id.tv_bio);
        tabLayout = view.findViewById(R.id.tab_layout_profile);
        rvMediaGrid = view.findViewById(R.id.rv_media_grid);

        rvMediaGrid.setLayoutManager(new GridLayoutManager(getContext(), 3));

        view.findViewById(R.id.iv_settings).setOnClickListener(v -> startActivity(new Intent(requireContext(), SyncTestActivity.class)));
        ProfileRepository repository = new ProfileRepository(requireContext());
        repository.getProfile(SessionManager.getInstance(requireContext()).getUserId()).observe(getViewLifecycleOwner(), resource -> {
            if (resource.status == Resource.Status.SUCCESS && resource.data != null) {
                com.insta.clone.data.model.Profile profile = resource.data;
                tvUsername.setText(profile.getUsername());
                tvPostsCount.setText(String.valueOf(profile.getPostsCount()));
                tvFollowersCount.setText(String.valueOf(profile.getFollowersCount()));
                tvFollowingCount.setText(String.valueOf(profile.getFollowingCount()));
                tvBio.setText((profile.getFullName() == null ? "" : profile.getFullName()) + (profile.getBio() == null || profile.getBio().isEmpty() ? "" : "\n" + profile.getBio()));
                Glide.with(this).load(profile.getAvatarUrl()).placeholder(R.drawable.ic_person_placeholder).error(R.drawable.ic_person_placeholder).into(ivAvatar);
            } else if (resource.status == Resource.Status.ERROR) {
                tvBio.setText(resource.message);
            }
        });

        return view;
    }
}
