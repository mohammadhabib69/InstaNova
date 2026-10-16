package com.insta.clone.ui.profile;

import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.android.material.tabs.TabLayout;
import com.insta.clone.BuildConfig;
import com.insta.clone.R;
import com.insta.clone.data.local.SessionManager;
import com.insta.clone.data.model.Post;
import com.insta.clone.data.model.Profile;
import com.insta.clone.data.remote.RetrofitClient;
import com.insta.clone.data.remote.api.RestApi;
import com.insta.clone.data.remote.api.StorageApi;
import com.insta.clone.data.remote.dto.ProfileDto;
import com.insta.clone.data.repository.PostRepository;
import com.insta.clone.data.repository.ProfileRepository;
import com.insta.clone.data.repository.ReelRepository;
import com.insta.clone.ui.create.CreateSheet;
import com.insta.clone.ui.post.PostDetailActivity;
import com.insta.clone.ui.settings.SettingsActivity;
import com.insta.clone.util.Constants;
import com.insta.clone.util.ImageUtils;
import com.insta.clone.util.Resource;
import com.insta.clone.util.ToastUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * User Profile Fragment with stats, bio, avatar upload, tabbed media grid, and Settings entry.
 */
public class ProfileFragment extends Fragment {

    private static final String TAG_API = "InstaApi";
    private static final String TAG_GLIDE = "InstaGlide";

    /** PAGE 2 tab positions: 0 = grid, 1 = reels, 2 = reposts, 3 = tagged. */
    private static final int TAB_REPOSTS = 2;
    private static final int TAB_TAGGED = 3;

    private ImageView ivAvatar;
    private TextView tvUsername, tvPostsCount, tvFollowersCount, tvFollowingCount, tvBio;
    private TextView tvDisplayName, tvEmptyTitle;
    private View profileEmptyState;
    private ImageView ivEmptyIcon;
    private TabLayout tabLayout;
    private RecyclerView rvMediaGrid;

    private ProfileRepository profileRepository;
    private PostRepository postRepository;
    private ReelRepository reelRepository;
    private StorageApi storageApi;
    private RestApi restApi;
    private okhttp3.OkHttpClient okHttpClient;
    private SessionManager sessionManager;
    private String currentUserId;

    private final List<Post> gridPosts = new ArrayList<>();
    private MediaGridAdapter gridAdapter;

    private ActivityResultLauncher<PickVisualMediaRequest> avatarPickerLauncher;

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
        tvDisplayName = view.findViewById(R.id.tv_display_name);
        profileEmptyState = view.findViewById(R.id.profileEmptyState);
        ivEmptyIcon = view.findViewById(R.id.ivEmptyIcon);
        tvEmptyTitle = view.findViewById(R.id.tvEmptyTitle);
        tabLayout = view.findViewById(R.id.tab_layout_profile);
        rvMediaGrid = view.findViewById(R.id.rv_media_grid);

        // PAGE 2: keep the 56dp top bar clear of the status bar (edge-to-edge window).
        // The inset goes on the fragment root so the bar keeps its 56dp height.
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), bars.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
        ViewCompat.requestApplyInsets(view);

        // PAGE 2: design-only elements -> "Not available" toast (DECORATIVE_UI).
        // hideWhenOff = the element itself is decorative in the reference design.
        wireDecorative(view.findViewById(R.id.ivChevronDown), true);
        wireDecorative(view.findViewById(R.id.btnAddPerson), true);
        wireDecorative(view.findViewById(R.id.layoutHighlightNew), true);
        wireDecorative(view.findViewById(R.id.btnEditProfile), false);
        wireDecorative(view.findViewById(R.id.btnShareProfile), false);
        wireDecorative(view.findViewById(R.id.btnFollow), false);
        wireDecorative(view.findViewById(R.id.btnMessage), false);

        sessionManager = SessionManager.getInstance(requireContext());
        currentUserId = sessionManager.getUserId();

        profileRepository = new ProfileRepository(requireContext());
        postRepository = new PostRepository(requireContext());
        reelRepository = new ReelRepository(requireContext());
        RetrofitClient client = RetrofitClient.getInstance(requireContext());
        storageApi = client.getStorageApi();
        restApi = client.getRestApi();
        okHttpClient = client.getOkHttpClient();

        // Top bar buttons
        View ivCreate = view.findViewById(R.id.iv_settings); // Add box icon
        if (ivCreate != null) {
            // PAGE 8A — the '+' opens the Create sheet, whose rows launch the existing flows
            ivCreate.setOnClickListener(v -> CreateSheet.show(requireContext()));
        }

        View ivMenu = view.findViewById(R.id.iv_menu);
        if (ivMenu != null) {
            ivMenu.setOnClickListener(v -> startActivity(new Intent(requireContext(), SettingsActivity.class)));
        }

        // Avatar change
        setupAvatarPicker();
        ivAvatar.setOnClickListener(v -> {
            avatarPickerLauncher.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build());
        });

        // Grid setup (3 columns, 1dp gutters)
        rvMediaGrid.setLayoutManager(new GridLayoutManager(getContext(), 3));
        rvMediaGrid.addItemDecoration(new GridGapDecoration(
                getResources().getDimensionPixelSize(R.dimen.profile_grid_gap)));
        gridAdapter = new MediaGridAdapter(gridPosts, post -> {
            Intent intent = new Intent(requireContext(), PostDetailActivity.class);
            intent.putExtra("post", post);
            intent.putExtra("post_id", post.getId());
            startActivity(intent);
        });
        rvMediaGrid.setAdapter(gridAdapter);

        // Tab selection listener
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int position = tab.getPosition();
                // PAGE 2: reposts / tagged tabs are design-only (no feature behind them).
                if (Constants.DECORATIVE_UI
                        && (position == TAB_REPOSTS || position == TAB_TAGGED)) {
                    ToastUtil.showNotAvailable(requireContext());
                }
                loadTabContent(position);
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        loadProfileData();
        loadTabContent(0);

        return view;
    }

    /**
     * PAGE 2 — design-only element. When {@link Constants#DECORATIVE_UI} is on the
     * element surfaces the shared "Not available" pill toast; when it is off the
     * element either disappears (hideWhenOff) or stays inert.
     */
    private void wireDecorative(@Nullable View view, boolean hideWhenOff) {
        if (view == null) return;
        if (Constants.DECORATIVE_UI) {
            view.setOnClickListener(v -> ToastUtil.showNotAvailable(requireContext()));
        } else if (hideWhenOff) {
            view.setVisibility(View.GONE);
        }
    }

    private void setupAvatarPicker() {
        avatarPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(),
                uri -> {
                    if (uri != null) {
                        uploadAvatar(uri);
                    }
                }
        );
    }

    private void uploadAvatar(Uri uri) {
        if (currentUserId == null) return;
        Toast.makeText(requireContext(), "Updating profile photo…", Toast.LENGTH_SHORT).show();

        byte[] jpegBytes;
        try {
            jpegBytes = ImageUtils.processUriToJpegBytes(requireContext(), uri);
        } catch (IOException e) {
            Log.e(TAG_API, "Failed to process avatar: " + e.getMessage(), e);
            Toast.makeText(requireContext(), "Failed to process photo: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }

        if (jpegBytes == null || jpegBytes.length == 0) {
            Toast.makeText(requireContext(), "Processed photo was empty", Toast.LENGTH_SHORT).show();
            return;
        }

        String path = currentUserId + "/" + UUID.randomUUID() + ".jpg";
        RequestBody body = RequestBody.create(MediaType.parse("image/jpeg"), jpegBytes);
        Log.d(TAG_API, "uploaded " + jpegBytes.length + " bytes to avatars/" + path);

        storageApi.uploadFile("avatars", path, body).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (!response.isSuccessful()) {
                    Log.e(TAG_API, "HTTP " + response.code() + " uploading avatar");
                    Toast.makeText(requireContext(), "Avatar upload failed (" + response.code() + ")", Toast.LENGTH_SHORT).show();
                    return;
                }

                String base = BuildConfig.SUPABASE_URL.trim();
                if (!base.endsWith("/")) base += "/";
                String publicUrl = com.insta.clone.util.MediaUrl.normalize("avatars", base + "storage/v1/object/public/avatars/" + path);
                Log.d(TAG_API, "HTTP 200 uploadFile avatars/" + path + ". Verifying via HEAD...");

                okhttp3.Request headReq = new okhttp3.Request.Builder()
                        .url(publicUrl)
                        .head()
                        .build();

                okHttpClient.newCall(headReq).enqueue(new okhttp3.Callback() {
                    @Override
                    public void onResponse(@NonNull okhttp3.Call c, @NonNull okhttp3.Response headResponse) throws IOException {
                        int headCode = headResponse.code();
                        String contentType = headResponse.header("Content-Type");
                        String lenStr = headResponse.header("Content-Length");
                        long contentLength = -1;
                        try {
                            if (lenStr != null) {
                                contentLength = Long.parseLong(lenStr);
                            }
                        } catch (NumberFormatException ignored) {}
                        headResponse.close();

                        Log.d(TAG_API, "HEAD status=" + headCode + " Content-Type=" + contentType
                                + " Content-Length=" + contentLength + " expected=" + jpegBytes.length + " for " + publicUrl);

                        if (headCode == 200) {
                            if (getActivity() == null) return;
                            getActivity().runOnUiThread(() -> {
                                Map<String, Object> update = new HashMap<>();
                                update.put("avatar_url", publicUrl);
                                restApi.updateProfile("eq." + currentUserId, update).enqueue(new Callback<List<ProfileDto>>() {
                                    @Override
                                    public void onResponse(Call<List<ProfileDto>> patchCall, Response<List<ProfileDto>> r) {
                                        if (r.isSuccessful()) {
                                            Toast.makeText(requireContext(), "Profile photo updated!", Toast.LENGTH_SHORT).show();
                                            sessionManager.saveSession(
                                                    sessionManager.getAccessToken(),
                                                    sessionManager.getRefreshToken(),
                                                    sessionManager.getUserId(),
                                                    sessionManager.getUsername(),
                                                    publicUrl
                                            );
                                            if (isAdded()) {
                                                com.insta.clone.util.GlideUtils.loadAvatar(ivAvatar, publicUrl);
                                            }
                                        } else {
                                            Log.e(TAG_API, "Failed to patch profile avatar: " + r.code());
                                        }
                                    }

                                    @Override
                                    public void onFailure(Call<List<ProfileDto>> patchCall, Throwable t) {
                                        Log.e(TAG_API, "Network error updating profile: " + t.getMessage());
                                    }
                                });
                            });
                        } else {
                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "HEAD verification failed (HTTP " + headCode + ")", Toast.LENGTH_SHORT).show());
                            }
                        }
                    }

                    @Override
                    public void onFailure(@NonNull okhttp3.Call c, @NonNull IOException e) {
                        Log.e(TAG_API, "HEAD verification error: " + e.getMessage(), e);
                    }
                });
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e(TAG_API, "Network error uploading avatar: " + t.getMessage());
                Toast.makeText(requireContext(), "Upload network error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadProfileData() {
        profileRepository.getProfile(currentUserId).observe(getViewLifecycleOwner(), resource -> {
            if (resource == null) return;
            if (resource.status == Resource.Status.SUCCESS && resource.data != null) {
                Profile profile = resource.data;
                tvUsername.setText(profile.getUsername());
                tvPostsCount.setText(formatStatCount(profile.getPostsCount()));
                tvFollowersCount.setText(formatStatCount(profile.getFollowersCount()));
                tvFollowingCount.setText(formatStatCount(profile.getFollowingCount()));

                // PAGE 2: the display name sits above the stats, the bio holds the bio.
                String displayName = profile.getFullName() == null ? "" : profile.getFullName().trim();
                if (displayName.isEmpty()) {
                    tvDisplayName.setText("");
                    tvDisplayName.setVisibility(View.GONE);
                } else {
                    tvDisplayName.setText(displayName);
                    tvDisplayName.setVisibility(View.VISIBLE);
                }
                String bio = profile.getBio() == null ? "" : profile.getBio().trim();
                tvBio.setText(bio);

                if (isAdded()) {
                    com.insta.clone.util.GlideUtils.loadAvatar(ivAvatar, profile.getAvatarUrl());
                }
            }
        });
    }

    /** 84 / 1,134 / 12.3K / 1.2M (PAGE 2 header format). */
    private String formatStatCount(int count) {
        if (count < 10_000) {
            return String.format(Locale.US, "%,d", count);
        }
        if (count < 1_000_000) {
            return trimTrailingZero(count / 1000.0) + "K";
        }
        return trimTrailingZero(count / 1_000_000.0) + "M";
    }

    private String trimTrailingZero(double value) {
        String text = String.format(Locale.US, "%.1f", value);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }

    private void showEmptyState(int tabPosition) {
        if (profileEmptyState == null || ivEmptyIcon == null || tvEmptyTitle == null) return;
        boolean reposts = tabPosition == TAB_REPOSTS;
        ivEmptyIcon.setImageResource(reposts ? R.drawable.ic_repost : R.drawable.ic_tagged);
        tvEmptyTitle.setText(reposts ? R.string.profile_no_reposts : R.string.profile_no_photos_of_you);
        profileEmptyState.setVisibility(View.VISIBLE);
    }

    private void hideEmptyState() {
        if (profileEmptyState != null) profileEmptyState.setVisibility(View.GONE);
    }

    private void loadTabContent(int tabPosition) {
        if (tabPosition == TAB_REPOSTS || tabPosition == TAB_TAGGED) {
            // PAGE 2: design-only tabs show their empty state instead of feed data.
            gridPosts.clear();
            gridAdapter.notifyDataSetChanged();
            showEmptyState(tabPosition);
            return;
        }

        hideEmptyState();

        if (tabPosition == 0) {
            // Posts Grid: v_posts?user_id=eq.{id}&is_reel=eq.false
            postRepository.getUserPosts(currentUserId).observe(getViewLifecycleOwner(), resource -> {
                if (resource != null && resource.status == Resource.Status.SUCCESS && resource.data != null) {
                    gridPosts.clear();
                    gridPosts.addAll(resource.data);
                    gridAdapter.notifyDataSetChanged();
                }
            });
        } else if (tabPosition == 1) {
            // Reels Grid: v_posts?user_id=eq.{id}&is_reel=eq.true
            reelRepository.getUserReels(currentUserId).observe(getViewLifecycleOwner(), resource -> {
                if (resource != null && resource.status == Resource.Status.SUCCESS && resource.data != null) {
                    gridPosts.clear();
                    gridPosts.addAll(resource.data);
                    gridAdapter.notifyDataSetChanged();
                }
            });
        }
        // Tabs 3 and 4 (reposts / tagged) are design-only and never reach here.
    }

    /**
     * PAGE 2 grid gutters: the 1dp gap is split across neighbours (3px total at
     * 440dpi = 1.09dp, the closest achievable value). Equal insets keep the three
     * columns the same width.
     */
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

    @Override
    public void onResume() {
        super.onResume();
        loadProfileData();
        if (tabLayout != null) {
            loadTabContent(tabLayout.getSelectedTabPosition());
        }
    }

    /**
     * Adapter for 3-column post grid in Profile.
     */
    static class MediaGridAdapter extends RecyclerView.Adapter<MediaGridAdapter.GridViewHolder> {
        private final List<Post> items;
        private final OnPostClickListener listener;

        interface OnPostClickListener {
            void onPostClick(Post post);
        }

        MediaGridAdapter(List<Post> items, OnPostClickListener listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public GridViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_saved_media, parent, false);
            return new GridViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull GridViewHolder holder, int position) {
            Post post = items.get(position);
            String url = post.getThumbnailUrl();
            if (url == null || url.isEmpty()) {
                url = post.getFirstMediaUrl();
            }

            com.insta.clone.util.GlideUtils.load(holder.ivMedia, "posts", url);

            // PAGE 2: carousel / reel markers (real post data, not decoration).
            boolean carousel = post.getMediaUrls() != null && post.getMediaUrls().size() > 1;
            holder.ivMultiBadge.setVisibility(carousel ? View.VISIBLE : View.GONE);
            holder.ivReelBadge.setVisibility(post.isReel() ? View.VISIBLE : View.GONE);

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onPostClick(post);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class GridViewHolder extends RecyclerView.ViewHolder {
            final ImageView ivMedia;
            final ImageView ivMultiBadge;
            final ImageView ivReelBadge;
            GridViewHolder(@NonNull View itemView) {
                super(itemView);
                ivMedia = itemView.findViewById(R.id.ivMedia);
                ivMultiBadge = itemView.findViewById(R.id.ivMultiBadge);
                ivReelBadge = itemView.findViewById(R.id.ivReelBadge);
            }
        }
    }
}
