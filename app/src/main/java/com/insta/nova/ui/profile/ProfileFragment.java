package com.insta.nova.ui.profile;

import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import androidx.appcompat.app.AlertDialog;
import com.bumptech.glide.Glide;
import com.insta.nova.data.local.LocalSocialStore;
import com.insta.nova.data.model.Highlight;
import com.insta.nova.ui.story.LocalStoryViewerActivity;
import com.google.android.material.tabs.TabLayout;
import com.insta.nova.BuildConfig;
import com.insta.nova.R;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.model.Post;
import com.insta.nova.data.model.Profile;
import com.insta.nova.data.model.StoryGroup;
import com.insta.nova.data.remote.ProfileColumns;
import com.insta.nova.data.remote.RetrofitClient;
import com.insta.nova.data.remote.api.RestApi;
import com.insta.nova.data.remote.api.StorageApi;
import com.insta.nova.data.remote.dto.ProfileDto;
import com.insta.nova.data.repository.ProfileWriteCheck;
import com.insta.nova.data.repository.PostRepository;
import com.insta.nova.data.repository.ProfileRepository;
import com.insta.nova.data.repository.ReelRepository;
import com.insta.nova.data.repository.StoryRepository;
import com.insta.nova.ui.create.CreateSheet;
import com.insta.nova.ui.create.CreateStoryActivity;
import com.insta.nova.ui.home.StoryTrayOrdering;
import com.insta.nova.ui.post.PostDetailActivity;
import com.insta.nova.ui.settings.SettingsActivity;
import com.insta.nova.ui.story.StoryViewerActivity;
import com.insta.nova.ui.widget.AvatarPreview;
import com.insta.nova.ui.widget.StoryRingView;
import com.insta.nova.util.Constants;
import com.insta.nova.util.ImageUtils;
import com.insta.nova.util.Resource;
import com.insta.nova.util.ToastUtil;
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
    private StoryRingView profileAvatarRing;
    private TextView tvUsername, tvPostsCount, tvFollowersCount, tvFollowingCount, tvBio;
    private TextView tvDisplayName, tvEmptyTitle;
    private View profileEmptyState;
    private ImageView ivEmptyIcon;
    private TabLayout tabLayout;
    private RecyclerView rvMediaGrid;

    private ProfileRepository profileRepository;
    private PostRepository postRepository;
    private ReelRepository reelRepository;
    private StoryRepository storyRepository;
    private StorageApi storageApi;
    private RestApi restApi;
    private okhttp3.OkHttpClient okHttpClient;
    private SessionManager sessionManager;
    private String currentUserId;
    private String currentAvatarUrl;
    private boolean hasActiveStory;

    private final List<Post> gridPosts = new ArrayList<>();
    private MediaGridAdapter gridAdapter;

    private ActivityResultLauncher<PickVisualMediaRequest> avatarPickerLauncher;

    public ProfileFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);
        ivAvatar = view.findViewById(R.id.iv_avatar);
        profileAvatarRing = view.findViewById(R.id.profileAvatarRing);
        if (profileAvatarRing != null) profileAvatarRing.setRingState(StoryRingView.RingState.NONE);
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
        bindHighlights(view);
        // Saved entry: reuses the existing SavedFragment grid through a thin host.
        View rowSaved = view.findViewById(R.id.rowSaved);
        if (rowSaved != null) {
            rowSaved.setOnClickListener(v -> startActivity(
                    new Intent(requireContext(), com.insta.nova.ui.saved.SavedActivity.class)));
        }
        // Real action: hand the profile to the system share sheet.
        View btnShareProfile = view.findViewById(R.id.btnShareProfile);
        if (btnShareProfile != null) {
            btnShareProfile.setOnClickListener(v -> shareProfile());
        }
        wireDecorative(view.findViewById(R.id.btnFollow), false);
        wireDecorative(view.findViewById(R.id.btnMessage), false);

        sessionManager = SessionManager.getInstance(requireContext());
        currentUserId = sessionManager.getUserId();

        profileRepository = new ProfileRepository(requireContext());
        postRepository = new PostRepository(requireContext());
        reelRepository = new ReelRepository(requireContext());
        storyRepository = new StoryRepository(requireContext());
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

        // Avatar: tap opens the active own story, otherwise the existing story
        // composer. Editing the photo stays on the existing Edit Profile button.
        setupAvatarPicker();
        ivAvatar.setOnClickListener(v -> openOwnStoryOrComposer());
        ivAvatar.setOnLongClickListener(v -> {
            AvatarPreview.show(ivAvatar, currentAvatarUrl);
            return true;
        });
        ivAvatar.setOnTouchListener((v, event) -> {
            if ((event.getAction() == MotionEvent.ACTION_UP
                    || event.getAction() == MotionEvent.ACTION_CANCEL)
                    && AvatarPreview.isShowing()) {
                AvatarPreview.dismiss();
            }
            return false;
        });
        View editProfile = view.findViewById(R.id.btnEditProfile);
        if (editProfile != null) {
            editProfile.setOnClickListener(v -> avatarPickerLauncher.launch(
                    new PickVisualMediaRequest.Builder()
                            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                            .build()));
        }

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
    /**
     * Highlights: the "New" circle opens the creator (or explains why it cannot),
     * saved highlights are appended to the existing row and open the local viewer.
     */
    private void bindHighlights(View root) {
        View add = root.findViewById(R.id.layoutHighlightNew);
        if (add != null) {
            add.setOnClickListener(v -> {
                if (!isAdded()) return;
                LocalSocialStore store = new LocalSocialStore(requireContext());
                if (store.getPastStories().isEmpty()) {
                    new AlertDialog.Builder(requireContext())
                            .setMessage(R.string.highlight_no_stories)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                } else {
                    new HighlightCreateSheet().show(getChildFragmentManager(), "highlight_create");
                }
            });
        }
        refreshHighlights(root);
        LocalSocialStore.revision().observe(getViewLifecycleOwner(), revision -> refreshHighlights(root));
    }

    private void refreshHighlights(View root) {
        if (root == null || !isAdded()) return;
        LinearLayout row = root.findViewById(R.id.highlightsRow);
        if (row == null) return;
        for (int i = row.getChildCount() - 1; i >= 0; i--) {
            View child = row.getChildAt(i);
            if ("local_highlight".equals(child.getTag())) row.removeViewAt(i);
        }
        LocalSocialStore store = new LocalSocialStore(requireContext());
        for (final Highlight highlight : store.getHighlights()) {
            View item = getLayoutInflater().inflate(R.layout.item_p18_highlight, row, false);
            TextView title = item.findViewById(R.id.tvTitle);
            ImageView cover = item.findViewById(R.id.ivCover);
            if (title != null) title.setText(highlight.getTitle());
            if (cover != null && highlight.getCoverUrl() != null) {
                Glide.with(this).load(highlight.getCoverUrl()).into(cover);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(16));
            item.setLayoutParams(lp);
            item.setTag("local_highlight");
            item.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), LocalStoryViewerActivity.class);
                intent.putExtra(LocalStoryViewerActivity.EXTRA_HIGHLIGHT_ID, highlight.getId());
                startActivity(intent);
            });
            row.addView(item);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void wireDecorative(@Nullable View view, boolean hideWhenOff) {
        if (view == null) return;
        if (Constants.DECORATIVE_UI) {
            view.setOnClickListener(v -> ToastUtil.showNotAvailable(requireContext()));
        } else if (hideWhenOff) {
            view.setVisibility(View.GONE);
        }
    }

    private void shareProfile() {
        String username = sessionManager == null ? null : sessionManager.getUsername();
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT,
                "Follow @" + (username == null || username.isEmpty() ? "me" : username) + " on InstaNova");
        startActivity(Intent.createChooser(share, "Share Profile"));
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
                String publicUrl = com.insta.nova.util.MediaUrl.normalize("avatars", base + "storage/v1/object/public/avatars/" + path);
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
                                restApi.updateProfile("eq." + currentUserId, ProfileColumns.WRITE_ECHO, update).enqueue(new Callback<List<ProfileDto>>() {
                                    @Override
                                    public void onResponse(Call<List<ProfileDto>> patchCall, Response<List<ProfileDto>> r) {
                                        if (r.isSuccessful() && ProfileWriteCheck.confirmed(r.body(), currentUserId)) {
                                            Toast.makeText(requireContext(), "Profile photo updated!", Toast.LENGTH_SHORT).show();
                                            sessionManager.updateAvatar(publicUrl);
                                            currentAvatarUrl = publicUrl;
                                            if (isAdded()) {
                                                com.insta.nova.util.GlideUtils.loadAvatar(ivAvatar, publicUrl);
                                            }
                                        } else {
                                            Log.e(TAG_API, "Failed to patch profile avatar: HTTP " + r.code()
                                                    + " rows=" + (r.body() == null ? 0 : r.body().size()));
                                            Toast.makeText(requireContext(),
                                                    ProfileWriteCheck.ZERO_ROWS_MESSAGE, Toast.LENGTH_SHORT).show();
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

    /** Uses the active-story RPC (it filters expires_at > now()) for the ring. */
    private void loadOwnStoryState() {
        if (storyRepository == null || currentUserId == null || currentUserId.trim().isEmpty()) {
            applyRing(false, false);
            return;
        }
        storyRepository.getStoryGroups().observe(getViewLifecycleOwner(), resource -> {
            if (resource == null || resource.status != Resource.Status.SUCCESS) return;
            StoryGroup own = StoryTrayOrdering.findOwner(resource.data, currentUserId);
            applyRing(own != null && own.hasActiveStory(), own != null && own.hasUnseen());
        });
    }

    private void applyRing(boolean active, boolean unseen) {
        hasActiveStory = active;
        if (profileAvatarRing == null) return;
        profileAvatarRing.setRingState(!active ? StoryRingView.RingState.NONE
                : (unseen ? StoryRingView.RingState.UNSEEN : StoryRingView.RingState.SEEN));
    }

    private void openOwnStoryOrComposer() {
        if (!isAdded()) return;
        if (hasActiveStory) {
            Intent intent = new Intent(requireContext(), StoryViewerActivity.class);
            intent.putExtra("user_id", currentUserId);
            intent.putExtra("username", sessionManager.getUsername());
            intent.putExtra("avatar_url", currentAvatarUrl);
            startActivity(intent);
        } else {
            startActivity(new Intent(requireContext(), CreateStoryActivity.class));
        }
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

                currentAvatarUrl = profile.getAvatarUrl();
                if (isAdded()) {
                    com.insta.nova.util.GlideUtils.loadAvatar(ivAvatar, currentAvatarUrl);
                }
            }
        });
    }

    /** 84 / 1,134 / 12.3K / 1.2M (PAGE 2 header format). */
    private String formatStatCount(int count) {
        return com.insta.nova.util.NumberFormat.formatProfileStat(count);
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
        if (currentUserId == null || currentUserId.trim().isEmpty()) {
            // The splash identity gate did not resolve a profiles.id. Querying
            // user_id=eq. would be a malformed request, so show the recoverable state
            // instead of pretending the account has no posts.
            gridPosts.clear();
            gridAdapter.notifyDataSetChanged();
            if (profileEmptyState != null && tvEmptyTitle != null) {
                tvEmptyTitle.setText(R.string.profile_identity_unavailable);
                profileEmptyState.setVisibility(View.VISIBLE);
            }
            return;
        }
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
    public static class GridGapDecoration extends RecyclerView.ItemDecoration {
        private final int leadingGap;
        private final int trailingGap;

        public GridGapDecoration(int gapPx) {
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
        loadOwnStoryState();
        if (tabLayout != null) {
            loadTabContent(tabLayout.getSelectedTabPosition());
        }
    }

    @Override
    public void onPause() {
        AvatarPreview.dismiss();
        super.onPause();
    }

    /**
     * Adapter for 3-column post grid in Profile.
     */
    public static class MediaGridAdapter extends RecyclerView.Adapter<MediaGridAdapter.GridViewHolder> {
        private final List<Post> items;
        private final OnPostClickListener listener;

        public interface OnPostClickListener {
            void onPostClick(Post post);
        }

        public MediaGridAdapter(List<Post> items, OnPostClickListener listener) {
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

            com.insta.nova.util.GlideUtils.load(holder.ivMedia, "posts", url);

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
