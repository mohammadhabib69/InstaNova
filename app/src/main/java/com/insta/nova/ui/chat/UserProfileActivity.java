package com.insta.nova.ui.chat;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.insta.nova.R;
import com.insta.nova.data.model.Highlight;
import com.insta.nova.data.model.Post;
import com.insta.nova.data.model.Profile;
import com.insta.nova.data.remote.dto.PostDto;
import com.insta.nova.data.remote.dto.ProfileDto;
import com.insta.nova.data.repository.SocialRepository;
import com.insta.nova.databinding.ActivityUserProfileBinding;
import com.insta.nova.databinding.SheetP18MoreBinding;
import com.insta.nova.databinding.SheetP18UnfollowBinding;
import com.insta.nova.ui.common.UserListSheet;
import com.insta.nova.ui.main.MainActivity;
import com.insta.nova.ui.post.PostDetailActivity;
import com.insta.nova.ui.story.StoryViewerActivity;
import com.insta.nova.util.Constants;
import com.insta.nova.util.GlideUtils;
import com.insta.nova.util.ToastUtil;

/** PAGE 18 — another user's profile. */
public class UserProfileActivity extends AppCompatActivity implements ProfileScreenAdapter.Listener {

    public static final String EXTRA_USER_ID = "EXTRA_USER_ID";
    public static final String EXTRA_USERNAME = "EXTRA_USERNAME";

    private ActivityUserProfileBinding binding;
    private UserProfileViewModel viewModel;
    private ProfileScreenAdapter adapter;
    private SocialRepository socialRepository;
    private String userId;
    private String username;
    private int tab;
    private boolean suggestionsOpen;
    private boolean sheetOpen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityUserProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.profileRoot, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(binding.profileRoot);

        userId = getIntent().getStringExtra(EXTRA_USER_ID);
        username = getIntent().getStringExtra(EXTRA_USERNAME);
        socialRepository = new SocialRepository(this);
        viewModel = new ViewModelProvider(this).get(UserProfileViewModel.class);

        String me = com.insta.nova.data.local.SessionManager.getInstance(this).getUserId();
        if (userId == null || userId.isEmpty()) {
            showMissing();
            return;
        }
        if (userId.equals(me)) {
            Intent own = new Intent(this, MainActivity.class);
            own.putExtra(MainActivity.EXTRA_NAV_DESTINATION, R.id.navigation_profile);
            startActivity(own);
            finish();
            return;
        }

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnMissingBack.setOnClickListener(v -> finish());
        binding.btnRetry.setOnClickListener(v -> viewModel.refresh());
        binding.tvTopUsername.setText(username == null ? "" : username);

        adapter = new ProfileScreenAdapter(this);
        GridLayoutManager glm = new GridLayoutManager(this, 3);
        glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) {
                int type = adapter.getItemViewType(position);
                return type == ProfileScreenAdapter.TYPE_TILE ? 1 : 3;
            }
        });
        binding.rvProfile.setLayoutManager(glm);
        binding.rvProfile.setAdapter(adapter);

        binding.swipeRefresh.setColorSchemeResources(R.color.profile_link);
        binding.swipeRefresh.setOnRefreshListener(() -> {
            viewModel.refresh();
            binding.swipeRefresh.setRefreshing(false);
        });

        binding.rvProfile.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView rv, int dx, int dy) {
                if (dy <= 0) return;
                int last = glm.findLastVisibleItemPosition();
                if (last >= adapter.getItemCount() - 4) viewModel.loadMore();
            }
        });

        viewModel.state().observe(this, this::render);
        viewModel.toast().observe(this, msg -> {
            if (msg != null && !msg.isEmpty()) ToastUtil.showPill(this, msg);
        });

        binding.btnMore.setOnClickListener(v -> openMoreSheet());
        viewModel.init(userId);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (viewModel != null && viewModel.userId() != null) viewModel.refresh();
    }

    private void render(UserProfileViewModel.Ui ui) {
        if (ui == null) return;
        boolean hasProfile = ui.profile != null;
        findViewById(R.id.layoutSkeleton).setVisibility(
                ui.loading && !hasProfile ? View.VISIBLE : View.GONE);
        binding.layoutError.setVisibility(ui.error && !hasProfile ? View.VISIBLE : View.GONE);
        binding.layoutMissing.setVisibility(ui.missing ? View.VISIBLE : View.GONE);
        binding.rvProfile.setVisibility(hasProfile && !ui.missing ? View.VISIBLE : View.INVISIBLE);

        if (!hasProfile) return;
        if (username == null) username = ui.profile.getUsername();
        binding.tvTopUsername.setText(ui.profile.getUsername());
        binding.ivTopVerified.setVisibility(ui.profile.isVerified() ? View.VISIBLE : View.GONE);

        adapter.submit(ui, tab, suggestionsOpen);
    }

    /* ---------------- adapter callbacks ---------------- */

    @Override public void onAvatar() {
        UserProfileViewModel.Ui ui = viewModel.state().getValue();
        if (ui == null || ui.profile == null) return;
        if (ui.hasStory) {
            Intent intent = new Intent(this, StoryViewerActivity.class);
            intent.putExtra("user_id", userId);
            intent.putExtra("username", ui.profile.getUsername());
            intent.putExtra("avatar_url", ui.profile.getAvatarUrl());
            if (ui.firstUnseenStoryId != null) intent.putExtra("story_id", ui.firstUnseenStoryId);
            startActivity(intent);
        } else {
            Intent intent = new Intent(this, ImageViewerActivity.class);
            intent.putExtra(ImageViewerActivity.EXTRA_IMAGE_URL, ui.profile.getAvatarUrl());
            intent.putExtra(ImageViewerActivity.EXTRA_BUCKET, "avatars");
            startActivity(intent);
        }
    }

    @Override public void onPostsStat() {
        tab = 0;
        GridLayoutManager glm = (GridLayoutManager) binding.rvProfile.getLayoutManager();
        if (glm != null && adapter.getItemCount() > 0) {
            glm.scrollToPositionWithOffset(Math.min(3, adapter.getItemCount() - 1), 0);
        }
        refreshAdapter();
    }

    @Override public void onFollowersStat() { openUserList("Followers", true); }

    @Override public void onFollowingStat() { openUserList("Following", false); }

    @Override public void onTab(int tab) {
        if (this.tab == tab) return;
        this.tab = tab;
        refreshAdapter();
    }

    private void openUserList(String title, boolean followers) {
        UserListSheet.show(this, title, true, "Search", query ->
                        followers ? socialRepository.getFollowers(userId)
                                : socialRepository.getFollowing(userId),
                profile -> {
                    Intent intent = new Intent(this, UserProfileActivity.class);
                    intent.putExtra(EXTRA_USER_ID, profile.getId());
                    intent.putExtra(EXTRA_USERNAME, profile.getUsername());
                    startActivity(intent);
                });
    }

    /**
     * Follow → straight to the optimistic Supabase follow. Following → the same
     * confirmation Instagram asks for ("Unfollow @username?"), and confirming is
     * the only path back to Follow (follower count -1 via the ViewModel delta).
     */
    @Override public void onFollowButton() {
        UserProfileViewModel.Ui ui = viewModel.state().getValue();
        if (ui == null || ui.profile == null) return;
        if (ui.iFollow) openUnfollowConfirm(ui.profile);
        else viewModel.setFollowing(true);
    }

    @Override public void onMessage() {
        UserProfileViewModel.Ui ui = viewModel.state().getValue();
        if (ui == null || ui.profile == null) return;
        com.insta.nova.data.repository.ChatRepository.getInstance(this)
                .getOrCreateConversation(userId)
                .observe(this, res -> {
                    // observe() replays the current value, and this LiveData starts in LOADING.
                    // Treating anything that is not SUCCESS as a failure therefore flashed
                    // "Couldn't open chat" on every tap while the request was still running —
                    // the conversation did open a moment later, behind the error toast.
                    if (res == null) return;
                    if (res.status == com.insta.nova.util.Resource.Status.LOADING) return;
                    if (res.status != com.insta.nova.util.Resource.Status.SUCCESS
                            || res.data == null) {
                        ToastUtil.showPill(this, "Couldn't open chat");
                        return;
                    }
                    Intent intent = new Intent(this, ChatActivity.class);
                    intent.putExtra(ChatActivity.EXTRA_CONVERSATION_ID, res.data);
                    intent.putExtra(ChatActivity.EXTRA_OTHER_USER_ID, userId);
                    intent.putExtra(ChatActivity.EXTRA_OTHER_USERNAME, ui.profile.getUsername());
                    intent.putExtra(ChatActivity.EXTRA_OTHER_AVATAR, ui.profile.getAvatarUrl());
                    startActivity(intent);
                });
    }

    @Override public void onAddPerson() {
        suggestionsOpen = !suggestionsOpen;
        refreshAdapter();
        UserProfileViewModel.Ui ui = viewModel.state().getValue();
        if (suggestionsOpen && ui != null && ui.suggestions.isEmpty()) {
            ToastUtil.showPill(this, "No suggestions right now");
        }
    }

    @Override public void onHighlight(Highlight highlight) {
        Intent intent = new Intent(this, HighlightViewerActivity.class);
        intent.putExtra(HighlightViewerActivity.EXTRA_HIGHLIGHT_ID, highlight.getId());
        intent.putExtra(HighlightViewerActivity.EXTRA_TITLE, highlight.getTitle());
        startActivity(intent);
    }

    @Override public void onPost(PostDto post) {
        Post model = post.toPost();
        Intent intent = new Intent(this, PostDetailActivity.class);
        intent.putExtra("post", model);
        startActivity(intent);
    }

    @Override public void onBioMore() { viewModel.setBioExpanded(); }

    @Override public void onHashtag(String tag) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_NAV_DESTINATION, R.id.navigation_search);
        intent.putExtra("search_query", "#" + tag);
        startActivity(intent);
    }

    @Override public void onMention(String username) {
        com.insta.nova.data.repository.UserProfileRepository repo =
                new com.insta.nova.data.repository.UserProfileRepository(this);
        repo.getProfileByUsername(username).observe(this, res -> {
            if (res == null || res.status != com.insta.nova.util.Resource.Status.SUCCESS
                    || res.data == null) {
                ToastUtil.showPill(this, "User not found");
                return;
            }
            Intent intent = new Intent(this, UserProfileActivity.class);
            intent.putExtra(EXTRA_USER_ID, res.data.getId());
            intent.putExtra(EXTRA_USERNAME, res.data.getUsername());
            startActivity(intent);
        });
    }

    @Override public void onUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(normalize(url))));
        } catch (Exception e) {
            android.widget.Toast.makeText(this, "Could not open link", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    @Override public void onSuggestionFollow(ProfileDto profile) {
        viewModel.followSuggestion(profile);
    }

    @Override public void onSuggestionDismiss(ProfileDto profile) {
        viewModel.dismissSuggestion(profile);
        refreshAdapter();
    }

    /* ---------------- sheets ---------------- */

    private void openMoreSheet() {
        UserProfileViewModel.Ui ui = viewModel.state().getValue();
        if (ui == null || ui.profile == null) return;
        ProfileDto p = ui.profile;
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.InstaNovaBottomSheetDialog);
        SheetP18MoreBinding sheet = SheetP18MoreBinding.inflate(getLayoutInflater());
        sheet.rowShare.setOnClickListener(v -> {
            dialog.dismiss();
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_TEXT, p.getFullName() + " (@" + p.getUsername()
                    + ") on " + Constants.APP_NAME);
            startActivity(Intent.createChooser(share, "Share profile"));
        });
        sheet.rowCopyUrl.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("profile",
                    Constants.PROFILE_URL_BASE + p.getUsername()));
            dialog.dismiss();
            ToastUtil.showPill(this, "Link copied");
        });
        // "About this account" has no real data source in this project; hide it
        // rather than ship a dead row.
        sheet.rowAbout.setVisibility(View.GONE);
        sheet.rowReport.setOnClickListener(v -> {
            dialog.dismiss();
            com.insta.nova.safe.SafeActions.report(this, "user", userId);
        });
        com.insta.nova.data.local.RelationshipStore relationshipStore =
                new com.insta.nova.data.local.RelationshipStore(this);
        com.insta.nova.data.repository.RelationshipRepository relationshipRepository =
                new com.insta.nova.data.repository.RelationshipRepository(this);

        boolean restricted = relationshipStore.isRestricted(userId);
        sheet.rowRestrict.setText(restricted ? R.string.st_unrestrict : R.string.st_restrict);
        sheet.rowRestrict.setOnClickListener(v -> {
            dialog.dismiss();
            boolean next = !relationshipStore.isRestricted(userId);
            relationshipRepository.setRestricted(userId, next).observe(this, r -> {
                if (r != null && r.status == com.insta.nova.util.Resource.Status.SUCCESS) {
                    ToastUtil.showPill(this, next
                            ? getString(R.string.st_restricted_toast, p.getUsername())
                            : getString(R.string.st_unrestricted_toast, p.getUsername()));
                }
            });
        });

        // Real block: nova_block_user + BlockMuteCache refresh, same sheet as every other surface.
        // The label used to say "Block" even when the account was already blocked, which left the
        // action one-way: the feed filtered them out and no screen offered a way back.
        final boolean alreadyBlocked =
                com.insta.nova.data.BlockMuteCache.get(this).isBlocked(userId);
        sheet.rowBlock.setText(alreadyBlocked ? R.string.nova_unblock : R.string.st_block);
        sheet.rowBlock.setOnClickListener(v -> {
            dialog.dismiss();
            if (alreadyBlocked) {
                com.insta.nova.safe.SafeActions.unblock(this, userId, this::finish);
            } else {
                com.insta.nova.safe.SafeActions.confirmBlock(this, userId, p.getUsername(), this::finish);
            }
        });
        dialog.setContentView(sheet.getRoot());
        dialog.show();
    }

    private void openUnfollowConfirm(ProfileDto p) {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.InstaNovaBottomSheetDialog);
        SheetP18UnfollowBinding sheet = SheetP18UnfollowBinding.inflate(getLayoutInflater());
        GlideUtils.loadAvatar(sheet.ivConfirmAvatar, p.getAvatarUrl());
        sheet.tvConfirmTitle.setText("Unfollow @" + p.getUsername() + "?");
        sheet.btnCancelUnfollow.setOnClickListener(v -> dialog.dismiss());
        sheet.btnConfirmUnfollow.setOnClickListener(v -> {
            dialog.dismiss();
            viewModel.setFollowing(false);
        });
        dialog.setContentView(sheet.getRoot());
        dialog.show();
    }

    private void refreshAdapter() {
        UserProfileViewModel.Ui ui = viewModel.state().getValue();
        if (ui != null) adapter.submit(ui, tab, suggestionsOpen);
    }

    private void showMissing() {
        binding.layoutMissing.setVisibility(View.VISIBLE);
        findViewById(R.id.layoutSkeleton).setVisibility(View.GONE);
    }

    private static String normalize(String url) {
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        return "https://" + url;
    }
}
