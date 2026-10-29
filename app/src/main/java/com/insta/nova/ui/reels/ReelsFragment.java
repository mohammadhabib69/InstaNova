package com.insta.nova.ui.reels;

import android.content.Intent;
import android.os.Bundle;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.graphics.Typeface;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import com.bumptech.glide.Glide;
import com.insta.nova.R;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.model.Post;
import com.insta.nova.data.repository.FollowRepository;
import com.insta.nova.data.repository.PostRepository;
import com.insta.nova.data.local.LocalSocialStore;
import com.insta.nova.data.repository.ReelRepository;
import com.insta.nova.databinding.FragmentReelsBinding;
import com.insta.nova.databinding.ItemReelPlayerBinding;
import com.insta.nova.ui.comment.CommentsBottomSheet;
import com.insta.nova.ui.create.CreateReelActivity;
import com.insta.nova.ui.create.NewReelActivity;
import com.insta.nova.util.Constants;
import com.insta.nova.util.Resource;
import com.insta.nova.util.ToastUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Fullscreen vertical scrolling Reels feed with ExoPlayer autoplay and empty state.
 *
 * PAGE 5 presentation only: the data path (ReelRepository / PostRepository),
 * the ViewPager2 paging, autoplay and the play/pause + double-tap behaviour are
 * unchanged. Added here: the status-bar inset for the top chrome, abbreviated
 * action counts, the expandable one-line caption, the author/Follow row and the
 * decorative chrome (title chevron, send, more, follow pill).
 */
public class ReelsFragment extends Fragment {

    private static final String TAG = "ReelsFragment";

    /** Collapsed / expanded line counts for the one-line reel caption. */
    private static final int CAPTION_LINES_COLLAPSED = 1;
    private static final int CAPTION_LINES_EXPANDED = 3;

    private FragmentReelsBinding binding;
    private ReelRepository reelRepository;
    private PostRepository postRepository;
    private FollowRepository followRepository;
    private SessionManager sessionManager;
    /** Last known follow state per author, so the pill does not re-query on every bind. */
    private final java.util.Map<String, Boolean> followState = new java.util.HashMap<>();
    private ReelsAdapter adapter;
    private LocalSocialStore localStore;
    private final List<Post> reelsList = new ArrayList<>();
    private int currentPosition = 0;

    public ReelsFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentReelsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        localStore = new LocalSocialStore(requireContext());
        reelRepository = new ReelRepository(requireContext());
        postRepository = new PostRepository(requireContext());
        followRepository = new FollowRepository(requireContext());
        sessionManager = SessionManager.getInstance(requireContext());

        applyTopInset();
        setupChrome();

        binding.btnCamera.setOnClickListener(v -> openCreateReel());
        binding.btnEmptyCreateReel.setOnClickListener(v -> openCreateReel());

        adapter = new ReelsAdapter(reelsList);
        binding.vpReels.setOrientation(ViewPager2.ORIENTATION_VERTICAL);
        binding.vpReels.setAdapter(adapter);

        binding.vpReels.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                adapter.onPageSelected(position);
                currentPosition = position;
            }
        });

        loadReels();
    }

    /**
     * Pushes the top system-bar inset into the chrome container so 'Reels' and
     * the camera clear the status bar while the video stays edge to edge.
     */
    private void applyTopInset() {
        final int basePaddingTop = binding.layoutReelTopBar.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutReelTopBar, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), basePaddingTop + bars.top,
                    v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
    }

    /**
     * Wires the decorative PAGE 5 chrome. Design-only elements follow the
     * shared Constants.DECORATIVE_UI contract.
     */
    private void setupChrome() {
        if (Constants.DECORATIVE_UI) {
            binding.btnReelTitle.setVisibility(View.VISIBLE);
            binding.btnReelTitle.setOnClickListener(v -> {
                if (binding.vpReels != null) binding.vpReels.setCurrentItem(0, true);
            });
        } else {
            binding.btnReelTitle.setVisibility(View.GONE);
            binding.ivReelTitleChevron.setVisibility(View.GONE);
        }
    }

    private void openCreateReel() {
        startActivity(new Intent(requireContext(), NewReelActivity.class));
    }

    /** Always / Wi-Fi only / Never, applied to every reel page change. */
    private boolean autoplayAllowed() {
        if (!isAdded()) return true;
        String mode = new com.insta.nova.data.local.AppSettingsStore(requireContext()).autoplay();
        if (com.insta.nova.data.local.AppSettingsStore.AUTOPLAY_NEVER.equals(mode)) return false;
        if (com.insta.nova.data.local.AppSettingsStore.AUTOPLAY_WIFI.equals(mode)) {
            android.net.NetworkInfo info = ((android.net.ConnectivityManager)
                    requireContext().getSystemService(android.content.Context.CONNECTIVITY_SERVICE))
                    .getActiveNetworkInfo();
            return info != null && info.getType() == android.net.ConnectivityManager.TYPE_WIFI;
        }
        return true;
    }

    /** Opens the pager at the reel explicitly requested by Profile. */
    private void scrollToRequestedReel() {
        if (getActivity() == null || getActivity().getIntent() == null) return;
        String reelId = getActivity().getIntent().getStringExtra(
                com.insta.nova.ui.main.MainActivity.EXTRA_REEL_ID);
        if (reelId == null || reelId.isEmpty()) return;
        for (int i = 0; i < reelsList.size(); i++) {
            if (reelId.equals(reelsList.get(i).getId())) {
                binding.vpReels.setCurrentItem(i, false);
                break;
            }
        }
        getActivity().getIntent().removeExtra(com.insta.nova.ui.main.MainActivity.EXTRA_REEL_ID);
    }

    public void loadReels() {
        reelRepository.getReels(20, 0).observe(getViewLifecycleOwner(), resource -> {
            if (resource == null) return;
            if (resource.status == Resource.Status.SUCCESS) {
                reelsList.clear();
                // Locally composed reels first: they are available even when the
                // backend page is empty.
                reelsList.addAll(localStore.getReels());
                if (resource.data != null) {
                    reelsList.addAll(resource.data);
                }
                adapter.notifyDataSetChanged();

                if (reelsList.isEmpty()) {
                    binding.layoutReelsEmpty.setVisibility(View.VISIBLE);
                    binding.vpReels.setVisibility(View.GONE);
                } else {
                    binding.layoutReelsEmpty.setVisibility(View.GONE);
                    binding.vpReels.setVisibility(View.VISIBLE);
                    binding.vpReels.post(() -> {
                        scrollToRequestedReel();
                        adapter.onPageSelected(binding.vpReels.getCurrentItem());
                    });
                }
            } else if (resource.status == Resource.Status.ERROR) {
                // Offline: locally composed reels still play.
                reelsList.clear();
                reelsList.addAll(localStore.getReels());
                adapter.notifyDataSetChanged();
                if (reelsList.isEmpty()) {
                    binding.layoutReelsEmpty.setVisibility(View.VISIBLE);
                    binding.vpReels.setVisibility(View.GONE);
                } else {
                    binding.layoutReelsEmpty.setVisibility(View.GONE);
                    binding.vpReels.setVisibility(View.VISIBLE);
                    binding.vpReels.post(() -> {
                        scrollToRequestedReel();
                        adapter.onPageSelected(binding.vpReels.getCurrentItem());
                    });
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        loadReels();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (adapter != null) adapter.pauseCurrentPlayer();
    }

    @Override
    public void onDestroyView() {
        if (adapter != null) adapter.releaseAllPlayers();
        super.onDestroyView();
        binding = null;
    }

    /** 999 -> "999", 1200 -> "1.2K", 12000 -> "12K", 1200000 -> "1.2M". */
    private static String abbreviate(int count) {
        if (count < 1000) {
            return String.valueOf(count);
        }
        if (count < 10000) {
            return String.format(Locale.US, "%.1fK", count / 1000.0);
        }
        if (count < 1000000) {
            return String.format(Locale.US, "%dK", Math.round(count / 1000.0));
        }
        if (count < 10000000) {
            return String.format(Locale.US, "%.1fM", count / 1000000.0);
        }
        return String.format(Locale.US, "%dM", Math.round(count / 1000000.0));
    }

    /**
     * Inner adapter managing ExoPlayer per reel item.
     */
    class ReelsAdapter extends RecyclerView.Adapter<ReelsAdapter.ReelViewHolder> {

        private final List<Post> items;
        private final List<ExoPlayer> activePlayers = new ArrayList<>();
        private ReelViewHolder currentHolder;

        ReelsAdapter(List<Post> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ReelViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemReelPlayerBinding itemBinding = ItemReelPlayerBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false
            );
            return new ReelViewHolder(itemBinding);
        }

        @Override
        public void onBindViewHolder(@NonNull ReelViewHolder holder, int position) {
            holder.bind(items.get(position));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public void onViewRecycled(@NonNull ReelViewHolder holder) {
            super.onViewRecycled(holder);
            holder.releasePlayer();
        }

        void onPageSelected(int position) {
            if (currentHolder != null) {
                currentHolder.pausePlayer();
            }
            if (!autoplayAllowed()) return;
            RecyclerView rv = (RecyclerView) binding.vpReels.getChildAt(0);
            if (rv != null) {
                ReelViewHolder holder = (ReelViewHolder) rv.findViewHolderForAdapterPosition(position);
                if (holder != null) {
                    currentHolder = holder;
                    holder.startPlayer();
                }
            }
        }

        void pauseCurrentPlayer() {
            if (currentHolder != null) currentHolder.pausePlayer();
        }

        void releaseAllPlayers() {
            for (ExoPlayer p : activePlayers) {
                try {
                    p.stop();
                    p.release();
                } catch (Exception ignored) {}
            }
            activePlayers.clear();
        }

        class ReelViewHolder extends RecyclerView.ViewHolder {
            private final ItemReelPlayerBinding itemBinding;
            private ExoPlayer exoPlayer;
            private ExoPlayer musicPlayer;
            private Post currentPost;
            private boolean isMuted = false;
            private float appliedOriginalVolume = 1f;
            private float appliedMusicVolume = 0f;
            /** Timed text lines from the author's editor meta: {text,startMs,endMs}. */
            private java.util.List<java.util.Map<String, Object>> timedLines =
                    new java.util.ArrayList<>();
            private android.widget.TextView timedText;
            private final android.os.Handler uiHandler =
                    new android.os.Handler(android.os.Looper.getMainLooper());
            private final Runnable ticker = new Runnable() {
                @Override public void run() {
                    showLineAt(positionMs());
                    uiHandler.postDelayed(this, 120L);
                }
            };

            ReelViewHolder(ItemReelPlayerBinding itemBinding) {
                super(itemBinding.getRoot());
                this.itemBinding = itemBinding;
            }

            void bind(Post post) {
                this.currentPost = post;

                // Decorative chrome (design-only): visible with a toast when the
                // shared flag is on, hidden when it is off.
                if (Constants.DECORATIVE_UI) {
                    itemBinding.layoutReelSend.setVisibility(View.VISIBLE);
                    // Real action: hand the reel to the system share sheet.
                    itemBinding.ivReelSend.setOnClickListener(v -> shareReel(itemView.getContext(), post));
                    itemBinding.ivReelMore.setVisibility(View.VISIBLE);
                    itemBinding.ivReelMore.setOnClickListener(v ->
                            com.insta.nova.ui.post.PostOptionsSheet.show(
                                    itemView.getContext(), post,
                                    new com.insta.nova.ui.post.PostOptionsSheet.Callback() {
                                        @Override public void onChanged() { }
                                        @Override public void onDeleted() { loadReels(); }
                                        @Override public void onArchived() { loadReels(); }
                                    }));
                } else {
                    itemBinding.layoutReelSend.setVisibility(View.GONE);
                    itemBinding.ivReelMore.setVisibility(View.GONE);
                }

                // Author row
                itemBinding.tvReelUsername.setText(post.getUsername());
                itemBinding.badgeReelVerified.setVerified(post.isVerified());
                if (post.getAvatarUrl() != null && !post.getAvatarUrl().isEmpty()) {
                    com.insta.nova.util.GlideUtils.loadAvatar(itemBinding.ivReelAvatar, post.getAvatarUrl());
                }

                // Caption: one line with ellipsis, tap to expand to three lines
                bindCaption(itemBinding.tvReelCaption, post.getCaption());

                // Action column counts (abbreviated, hidden at 0)
                bindCount(itemBinding.tvReelLikesCount, post.getLikesCount());
                bindCount(itemBinding.tvReelCommentsCount, post.getCommentsCount());
                // The model exposes no share count, so that counter stays hidden.
                itemBinding.tvReelSharesCount.setVisibility(View.GONE);

                // Audio thumbnail: the reel author's avatar
                if (post.getAvatarUrl() != null && !post.getAvatarUrl().isEmpty()) {
                    Glide.with(itemView.getContext())
                            .load(post.getAvatarUrl())
                            .centerCrop()
                            .into(itemBinding.ivReelAudioThumb);
                }

                // 'Follow' pill is hidden for the signed-in user's own reels
                // (the reel model carries no follow state, so it shows otherwise)
                boolean isMine = sessionManager != null
                        && Objects.equals(sessionManager.getUserId(), post.getUserId());
                if (isMine || !Constants.DECORATIVE_UI) {
                    itemBinding.btnReelFollow.setVisibility(View.GONE);
                } else {
                    itemBinding.btnReelFollow.setVisibility(View.VISIBLE);
                    bindFollowPill(itemBinding, post);
                }

                updateLikeIcon(post.isLikedByMe());

                itemBinding.ivReelLike.setOnClickListener(v -> {
                    boolean newState = !post.isLikedByMe();
                    post.setLikedByMe(newState);
                    post.setLikesCount(Math.max(0, post.getLikesCount() + (newState ? 1 : -1)));
                    bindCount(itemBinding.tvReelLikesCount, post.getLikesCount());
                    updateLikeIcon(newState);
                    postRepository.toggleLike(post.getId(), newState);
                });

                itemBinding.ivReelComment.setOnClickListener(v -> {
                    CommentsBottomSheet sheet = CommentsBottomSheet.newInstance(post.getId(), post.getCommentsCount(),
                            null, post.isCommentsDisabled());
                    sheet.show(getChildFragmentManager(), "CommentsBottomSheet");
                });

                itemBinding.ivReelMute.setOnClickListener(v -> {
                    isMuted = !isMuted;
                    if (exoPlayer != null) {
                        exoPlayer.setVolume(isMuted ? 0f : appliedOriginalVolume);
                    }
                    if (musicPlayer != null) {
                        musicPlayer.setVolume(isMuted ? 0f : appliedMusicVolume);
                    }
                    itemBinding.ivReelMute.setImageResource(isMuted ? R.drawable.ic_volume_off : R.drawable.ic_volume_up);
                });

                itemBinding.playerView.setOnClickListener(v -> {
                    if (exoPlayer != null) {
                        if (exoPlayer.isPlaying()) {
                            exoPlayer.pause();
                            if (musicPlayer != null) musicPlayer.pause();
                            uiHandler.removeCallbacks(ticker);
                            itemBinding.ivPlayIndicator.setVisibility(View.VISIBLE);
                        } else {
                            exoPlayer.play();
                            // Autoplay can be off (Wi-Fi only, for instance), and then the
                            // track and the caption clock have to start with the manual tap.
                            if (musicPlayer != null) {
                                try {
                                    musicPlayer.seekTo(Math.max(0L, exoPlayer.getCurrentPosition()));
                                } catch (Exception ignored) {
                                }
                                musicPlayer.play();
                            }
                            uiHandler.removeCallbacks(ticker);
                            uiHandler.post(ticker);
                            itemBinding.ivPlayIndicator.setVisibility(View.GONE);
                        }
                    }
                });

                ensureTimedText();
                initPlayer(post);
            }

            /** One centred caption above the video, reused across binds. */
            private void ensureTimedText() {
                if (timedText != null) return;
                timedText = new android.widget.TextView(itemView.getContext());
                timedText.setTextColor(android.graphics.Color.WHITE);
                timedText.setTextSize(22);
                timedText.setGravity(android.view.Gravity.CENTER);
                timedText.setShadowLayer(8f, 0f, 0f, 0xCC000000);
                timedText.setVisibility(View.GONE);
                android.widget.FrameLayout.LayoutParams lp =
                        new android.widget.FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.gravity = android.view.Gravity.CENTER;
                lp.leftMargin = lp.rightMargin = 32;
                ((ViewGroup) itemView).addView(timedText, lp);
            }

            private long positionMs() {
                if (exoPlayer == null) return 0L;
                try {
                    return exoPlayer.getCurrentPosition();
                } catch (Exception e) {
                    return 0L;
                }
            }

            private void showLineAt(long ms) {
                if (timedText == null) return;
                String text = null;
                for (java.util.Map<String, Object> line : timedLines) {
                    long start = longOf(line.get("startMs"));
                    long end = longOf(line.get("endMs"));
                    if (ms >= start && ms <= end) {
                        Object raw = line.get("text");
                        text = raw == null ? null : String.valueOf(raw);
                        break;
                    }
                }
                if (text == null || text.isEmpty()) {
                    timedText.setVisibility(View.GONE);
                } else {
                    timedText.setText(text);
                    timedText.setVisibility(View.VISIBLE);
                }
            }

            private long longOf(Object value) {
                return value instanceof Number ? ((Number) value).longValue() : 0L;
            }

            /**
             * Applies the author's editor meta: the two volume levels, the attached track and
             * the timed text. The clip itself is unchanged — the text and the music are drawn
             * and mixed at playback.
             */
            private void applyEditorMeta(Post post) {
                // The editor block is parsed out of the caption by PostDto.toPost(), so it is
                // already on the model when the row is bound.
                java.util.Map<String, Object> meta = post.getReelMeta();
                if (meta == null || meta.isEmpty()) {
                    timedLines = new java.util.ArrayList<>();
                    return;
                }
                float original = com.insta.nova.data.reel.ReelMetaStore.number(
                        meta, "originalVolume", 1f);
                appliedOriginalVolume = original;
                if (exoPlayer != null) {
                    exoPlayer.setVolume(isMuted ? 0f : original);
                }
                timedLines = com.insta.nova.data.reel.ReelMetaStore.list(meta, "elements");
                Object musicUrl = meta.get("musicUrl");
                float musicLevel = com.insta.nova.data.reel.ReelMetaStore.number(
                        meta, "musicVolume", 0f);
                appliedMusicVolume = musicLevel;
                if (musicUrl != null && !String.valueOf(musicUrl).isEmpty()) {
                    startMusicTrack(String.valueOf(musicUrl), musicLevel);
                }
                uiHandler.removeCallbacks(ticker);
                uiHandler.post(ticker);
            }

            private void startMusicTrack(String rawUrl, float level) {
                releaseMusic();
                try {
                    String url = com.insta.nova.util.MediaUrl.toAuthenticated(
                            com.insta.nova.util.MediaUrl.normalize("posts", rawUrl));
                    musicPlayer = new ExoPlayer.Builder(itemView.getContext()).build();
                    musicPlayer.setRepeatMode(Player.REPEAT_MODE_ONE);
                    musicPlayer.setVolume(level);
                    musicPlayer.setMediaSource(new ProgressiveMediaSource.Factory(
                            com.insta.nova.util.MediaUrl.streamFactory())
                            .createMediaSource(MediaItem.fromUri(url)));
                    musicPlayer.prepare();
                    if (exoPlayer != null && exoPlayer.isPlaying()) musicPlayer.play();
                } catch (Exception e) {
                    releaseMusic();
                }
            }

            private void releaseMusic() {
                if (musicPlayer == null) return;
                try {
                    musicPlayer.stop();
                    musicPlayer.release();
                } catch (Exception ignored) {
                }
                musicPlayer = null;
            }

            /** Abbreviated counter, hidden at 0. */
            private void bindCount(TextView view, int count) {
                if (count > 0) {
                    view.setText(abbreviate(count));
                    view.setVisibility(View.VISIBLE);
                } else {
                    view.setText(null);
                    view.setVisibility(View.GONE);
                }
            }

            /**
             * One-line caption with an inline "more"; tapping toggles between
             * one and three lines.
             */
            /**
     * The Follow pill is a real follow/unfollow control. The reel model carries no
     * follow state, so the author's state is read once and cached per author id.
     */
    private void bindFollowPill(ItemReelPlayerBinding itemBinding, Post post) {
        final String targetId = post.getUserId();
        if (targetId == null || targetId.isEmpty()) {
            itemBinding.btnReelFollow.setVisibility(View.GONE);
            return;
        }

        Boolean cached = followState.get(targetId);
        if (cached != null) {
            itemBinding.btnReelFollow.setText(cached ? "Following" : "Follow");
        } else {
            itemBinding.btnReelFollow.setEnabled(false);
            followRepository.isFollowing(targetId).observe(getViewLifecycleOwner(), res -> {
                itemBinding.btnReelFollow.setEnabled(true);
                if (res != null && res.status == Resource.Status.SUCCESS && res.data != null) {
                    followState.put(targetId, res.data);
                    itemBinding.btnReelFollow.setText(res.data
                            ? "Following" : "Follow");
                }
            });
        }

        itemBinding.btnReelFollow.setOnClickListener(v -> {
            final boolean next = !Boolean.TRUE.equals(followState.get(targetId));
            itemBinding.btnReelFollow.setEnabled(false);
            followRepository.toggleFollow(targetId, next).observe(getViewLifecycleOwner(), res -> {
                itemBinding.btnReelFollow.setEnabled(true);
                if (res != null && res.status == Resource.Status.SUCCESS) {
                    followState.put(targetId, next);
                    itemBinding.btnReelFollow.setText(next
                            ? "Following" : "Follow");
                } else {
                    ToastUtil.showPill(itemBinding.getRoot().getContext(), "Couldn't update follow");
                }
            });
        });
    }

    /** Shares the reel through the system sheet — same mechanic as the feed's Share. */
    private void shareReel(android.content.Context context, Post post) {
        if (context == null || post == null) return;
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, "Check out @" + post.getUsername() + "'s reel on InstaNova");
        context.startActivity(Intent.createChooser(share, "Share Reel"));
    }

    private void bindCaption(TextView view, String caption) {
                final String text = caption == null ? "" : caption.trim();
                if (text.isEmpty()) {
                    view.setText(null);
                    view.setVisibility(View.GONE);
                    return;
                }

                view.setVisibility(View.VISIBLE);
                view.setTag(text);
                view.setText(text);
                view.setMaxLines(CAPTION_LINES_COLLAPSED);
                view.setEllipsize(TextUtils.TruncateAt.END);
                view.setOnClickListener(v -> {
                    boolean collapsed = view.getMaxLines() == CAPTION_LINES_COLLAPSED;
                    view.setMaxLines(collapsed ? CAPTION_LINES_EXPANDED : CAPTION_LINES_COLLAPSED);
                });

                // Append the inline "more" only once layout proves the text is cut off.
                view.post(() -> {
                    if (!text.equals(view.getTag())) return;
                    if (view.getMaxLines() != CAPTION_LINES_COLLAPSED) return;
                    Layout layout = view.getLayout();
                    if (layout == null || layout.getEllipsisCount(0) <= 0) return;

                    SpannableStringBuilder sb = new SpannableStringBuilder(text)
                            .append(' ')
                            .append(view.getContext().getString(R.string.more));
                    sb.setSpan(new StyleSpan(Typeface.BOLD), text.length() + 1, sb.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    view.setText(sb);
                });
            }

            private void updateLikeIcon(boolean liked) {
                itemBinding.ivReelLike.setImageResource(liked ? R.drawable.ic_heart_filled : R.drawable.ic_heart);
                if (liked) {
                    itemBinding.ivReelLike.setImageTintList(null);
                    itemBinding.ivReelLike.clearColorFilter();
                } else {
                    itemBinding.ivReelLike.setColorFilter(
                            itemView.getContext().getColor(R.color.white));
                }
            }

            private void initPlayer(Post post) {
                releasePlayer();
                String videoUrl = post.getVideoUrl();
                if (videoUrl == null || videoUrl.isEmpty()) {
                    videoUrl = post.getFirstMediaUrl();
                }

                if (videoUrl == null || videoUrl.isEmpty()) {
                    itemBinding.ivReelThumbnail.setVisibility(View.VISIBLE);
                    com.insta.nova.util.GlideUtils.load(itemBinding.ivReelThumbnail, "reels", post.getThumbnailUrl());
                    return;
                }

                exoPlayer = new ExoPlayer.Builder(itemView.getContext()).build();
                exoPlayer.setRepeatMode(Player.REPEAT_MODE_ONE);
                exoPlayer.setVolume(isMuted ? 0f : 1f);

                // ExoPlayer does not share the app's OkHttp client, so it does not get the
                // apikey/bearer pair that a private bucket requires. The URL is rewritten to
                // the authenticated endpoint here and the same headers are attached to the
                // data source; without this the player fails with HTTP 400 on every reel.
                String streamUrl = com.insta.nova.util.MediaUrl.toAuthenticated(
                        com.insta.nova.util.MediaUrl.normalize("posts", videoUrl));
                MediaSource source = new ProgressiveMediaSource.Factory(
                        com.insta.nova.util.MediaUrl.streamFactory())
                        .createMediaSource(MediaItem.fromUri(streamUrl));
                exoPlayer.setMediaSource(source);
                exoPlayer.addListener(new Player.Listener() {
                    @Override
                    public void onPlayerError(androidx.media3.common.PlaybackException error) {
                        Log.e("InstaReel", "playback error on " + streamUrl, error);
                    }
                });
                exoPlayer.prepare();

                itemBinding.playerView.setPlayer(exoPlayer);
                activePlayers.add(exoPlayer);
                applyEditorMeta(post);
            }

            void startPlayer() {
                if (exoPlayer != null) {
                    exoPlayer.play();
                    itemBinding.ivPlayIndicator.setVisibility(View.GONE);
                }
                if (musicPlayer != null) {
                    musicPlayer.play();
                }
                uiHandler.removeCallbacks(ticker);
                uiHandler.post(ticker);
            }

            void pausePlayer() {
                if (exoPlayer != null) {
                    exoPlayer.pause();
                }
                if (musicPlayer != null) {
                    musicPlayer.pause();
                }
                uiHandler.removeCallbacks(ticker);
            }

            void releasePlayer() {
                uiHandler.removeCallbacks(ticker);
                releaseMusic();
                if (exoPlayer != null) {
                    activePlayers.remove(exoPlayer);
                    try {
                        exoPlayer.stop();
                        exoPlayer.release();
                    } catch (Exception ignored) {}
                    exoPlayer = null;
                }
            }
        }
    }
}
