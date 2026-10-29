package com.insta.nova.ui.story;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.insta.nova.R;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.model.Story;
import com.insta.nova.data.remote.RetrofitClient;
import com.insta.nova.data.remote.api.RestApi;
import com.insta.nova.data.remote.dto.StoryDto;
import com.insta.nova.data.repository.ChatRepository;
import com.insta.nova.data.repository.HighlightRepository;
import com.insta.nova.data.repository.SocialRepository;
import com.insta.nova.data.repository.StoryRepository;
import com.insta.nova.databinding.ActivityStoryViewerBinding;
import com.insta.nova.ui.common.UserListSheet;
import com.insta.nova.util.Resource;
import com.insta.nova.util.TimeUtils;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * PAGE 12 — story viewer.
 *
 * Playback is 5 s per story with linear segmented progress; tap / hold / swipe
 * gestures drive navigation; replies are real DM messages and story views are
 * recorded through the repository (so the tray ring turns grey).
 */
public class StoryViewerActivity extends AppCompatActivity {

    private static final String TAG_API = "InstaApi";
    private static final String TAG_GLIDE = "InstaGlide";
    private static final long STORY_DURATION_MS = 5000L;

    /* Video stories: the row only carries the original file, so the player applies the
       trim range and mute flag that the editor wrote into editor_meta. */
    private androidx.media3.exoplayer.ExoPlayer storyPlayer;
    private androidx.media3.ui.PlayerView storyPlayerView;
    private long currentDurationMs = STORY_DURATION_MS;
    private long trimStartMs = 0L;
    private long trimEndMs = 0L;
    private boolean metaMuted = false;
    /** 9:16 box the clip and its stroke overlay share, so the drawing stays registered. */
    private android.widget.FrameLayout mediaBox;
    private boolean metaFit = false;
    /** The story's chosen track; one at a time, released whenever the story changes. */
    private androidx.media3.exoplayer.ExoPlayer storyMusic;
    private android.widget.FrameLayout overlayLayer;
    private android.widget.LinearLayout pollCard;
    /** Guards the deferred timer start against a story the user has already swiped past. */
    private int mediaLoadToken;
    private static final int TICK_MS = 16;
    private static final float SWIPE_DOWN_CLOSE_PX_DP = 120f;
    private static final float SWIPE_SIDE_PX_DP = 80f;

    private ActivityStoryViewerBinding binding;
    private final List<Story> stories = new ArrayList<>();
    private final List<View> progressFills = new ArrayList<>();
    private final List<View> progressTracks = new ArrayList<>();
    private int currentStoryIndex = 0;

    private RestApi restApi;
    private StoryRepository storyRepository;
    private SocialRepository socialRepository;
    private ChatRepository chatRepository;
    private HighlightRepository highlightRepository;

    private String currentUserId;
    private String storyUserId;
    private String storyUsername;
    private boolean paused;
    private boolean holdActive;
    private boolean replyFocused;
    private int viewersCount;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable tick;
    private long lastTickAt;
    private float accumulatedMs;

    private float downX, downY;
    private boolean dragging;

    private static final String[] EMOJI = {"😂", "😮", "😍", "😢", "👏", "🔥"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityStoryViewerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        currentUserId = SessionManager.getInstance(this).getUserId();
        storyUserId = getIntent().getStringExtra("user_id");
        storyUsername = getIntent().getStringExtra("username");
        String avatarUrl = getIntent().getStringExtra("avatar_url");
        if (storyUserId == null || storyUserId.isEmpty()) storyUserId = currentUserId;

        restApi = RetrofitClient.getInstance(this).getRestApi();
        storyRepository = new StoryRepository(this);
        socialRepository = new SocialRepository(this);
        chatRepository = ChatRepository.getInstance(this);
        highlightRepository = new HighlightRepository(this);

        if (storyUsername != null) binding.tvStoryUsername.setText(storyUsername);
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            com.insta.nova.util.GlideUtils.loadAvatar(binding.ivStoryAvatar, avatarUrl);
        }

        applyInsets();
        buildEmojiPanel();
        wireHeader();
        wireReply();
        wireOwnerBar();
        wireGestures();

        loadStories();
    }

    /* ------------------------------------------------------------------ */
    /* Layout / wiring                                                     */
    /* ------------------------------------------------------------------ */

    private void applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            ViewGroup.MarginLayoutParams top = (ViewGroup.MarginLayoutParams) binding.layoutViewerTop.getLayoutParams();
            top.topMargin = bars.top + dp(R.dimen.story_vw_header_gap);
            binding.layoutViewerTop.setLayoutParams(top);

            FrameLayout.LayoutParams bar = (FrameLayout.LayoutParams) binding.layoutReplyBar.getLayoutParams();
            bar.bottomMargin = bars.bottom;
            binding.layoutReplyBar.setLayoutParams(bar);

            FrameLayout.LayoutParams owner = (FrameLayout.LayoutParams) binding.layoutOwnerBar.getLayoutParams();
            owner.bottomMargin = bars.bottom + dp(R.dimen.story_vw_bar_height);
            binding.layoutOwnerBar.setLayoutParams(owner);

            FrameLayout.LayoutParams emoji = (FrameLayout.LayoutParams) binding.layoutEmojiPanel.getLayoutParams();
            emoji.topMargin = bars.top + dp(R.dimen.story_vw_bar_height);
            binding.layoutEmojiPanel.setLayoutParams(emoji);
            return insets;
        });
        ViewCompat.requestApplyInsets(binding.getRoot());
    }

    private void buildEmojiPanel() {
        LinearLayout row1 = binding.layoutEmojiRow1;
        LinearLayout row2 = binding.layoutEmojiRow2;
        for (int i = 0; i < EMOJI.length; i++) {
            TextView tv = new TextView(this);
            tv.setText(EMOJI[i]);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.story_vw_emoji_size));
            tv.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginStart(dp(R.dimen.story_vw_emoji_gap) / 2);
            lp.setMarginEnd(dp(R.dimen.story_vw_emoji_gap) / 2);
            tv.setLayoutParams(lp);
            final String emoji = EMOJI[i];
            tv.setOnClickListener(v -> sendReaction(emoji));
            (i < 3 ? row1 : row2).addView(tv);
        }
        // 'Avatar' segment is design-only.
        binding.tvSegAvatar.setVisibility(View.GONE);
    }

    private void wireHeader() {
        binding.btnCloseStory.setOnClickListener(v -> finish());
        binding.btnStoryMore.setOnClickListener(v -> showMoreSheet());
    }

    private void wireReply() {
        binding.etStoryReply.setOnFocusChangeListener((v, hasFocus) -> setReplyFocused(hasFocus));
        binding.etStoryReply.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                boolean hasText = s != null && s.toString().trim().length() > 0;
                binding.btnStorySend.setVisibility(hasText ? View.VISIBLE : View.GONE);
                binding.btnStoryHeart.setVisibility(hasText ? View.GONE : View.VISIBLE);
                binding.btnStorySendIcon.setVisibility(hasText ? View.GONE : View.VISIBLE);
            }
        });
        binding.etStoryReply.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendTypedReply();
                return true;
            }
            return false;
        });
        binding.btnStorySend.setOnClickListener(v -> sendTypedReply());
        binding.btnStoryHeart.setOnClickListener(v -> sendReaction(getString(R.string.story_vw_heart)));
        binding.btnStorySendIcon.setOnClickListener(v -> shareStory());
    }

    private void wireOwnerBar() {
        binding.btnSeenBy.setOnClickListener(v -> showViewersSheet());
    }

    private void setReplyFocused(boolean focused) {
        if (replyFocused == focused) return;
        replyFocused = focused;
        if (focused) {
            binding.viewDim.setVisibility(View.VISIBLE);
            binding.layoutEmojiPanel.setVisibility(View.VISIBLE);
            pausePlayback();
        } else {
            binding.viewDim.setVisibility(View.GONE);
            binding.layoutEmojiPanel.setVisibility(View.GONE);
            resumePlayback();
        }
    }

    /* ------------------------------------------------------------------ */
    /* Gestures                                                            */
    /* ------------------------------------------------------------------ */

    private void wireGestures() {
        binding.getRoot().setOnTouchListener((v, event) -> {
            if (replyFocused) return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getX();
                    downY = event.getY();
                    dragging = false;
                    handler.postDelayed(holdRunnable, 260);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getX() - downX;
                    float dy = event.getY() - downY;
                    if (Math.abs(dx) > dpF(SWIPE_SIDE_PX_DP) || Math.abs(dy) > dpF(SWIPE_SIDE_PX_DP)) {
                        handler.removeCallbacks(holdRunnable);
                    }
                    if (Math.abs(dy) > Math.abs(dx) && dy > 0) {
                        dragging = true;
                        float scale = Math.max(0.5f, 1f - dy / (getResources().getDisplayMetrics().heightPixels * 0.9f));
                        binding.ivStoryMedia.setTranslationY(dy);
                        binding.ivStoryMedia.setScaleX(scale);
                        binding.ivStoryMedia.setScaleY(scale);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(holdRunnable);
                    float upDx = event.getX() - downX;
                    float upDy = event.getY() - downY;
                    if (holdActive) {
                        endHold();
                        return true;
                    }
                    if (upDy > dpF(SWIPE_DOWN_CLOSE_PX_DP) && Math.abs(upDy) > Math.abs(upDx)) {
                        finish();
                        return true;
                    }
                    if (dragging) {
                        resetDrag();
                        return true;
                    }
                    if (Math.abs(upDx) > dpF(SWIPE_SIDE_PX_DP)) {
                        if (upDx < 0) nextStory(); else previousStory();
                        return true;
                    }
                    if (Math.abs(upDx) < dpF(24) && Math.abs(upDy) < dpF(24)) {
                        boolean rightSide = downX > binding.getRoot().getWidth() / 3f;
                        if (rightSide) nextStory(); else previousStory();
                    }
                    return true;
                default:
                    return false;
            }
        });
    }

    private final Runnable holdRunnable = new Runnable() {
        @Override
        public void run() {
            holdActive = true;
            pausePlayback();
            binding.layoutViewerTop.setVisibility(View.INVISIBLE);
        binding.layoutReplyBar.setVisibility(View.INVISIBLE);
            binding.layoutOwnerBar.setVisibility(View.INVISIBLE);
        }
    };

    private void endHold() {
        holdActive = false;
        binding.layoutViewerTop.setVisibility(View.VISIBLE);
        binding.layoutReplyBar.setVisibility(View.VISIBLE);
        if (isOwnStory()) binding.layoutOwnerBar.setVisibility(View.VISIBLE);
        resumePlayback();
    }

    private void resetDrag() {
        dragging = false;
        binding.ivStoryMedia.animate().translationY(0f).scaleX(1f).scaleY(1f).setDuration(150).start();
    }

    /* ------------------------------------------------------------------ */
    /* Playback                                                            */
    /* ------------------------------------------------------------------ */

    /**
     * Images are baked by the editor, so they only need Glide. A video keeps its original
     * file: the trim range, the mute flag and the story's on-screen duration all come from
     * editor_meta, and the player has to stop at the trim end instead of the file end.
     */
    private void bindStoryMedia(Story story) {
        Log.e("StoryMedia", "type=" + story.getMediaType() + " meta=" + story.getEditorMeta());
        currentDurationMs = STORY_DURATION_MS;
        trimStartMs = 0L;
        trimEndMs = 0L;
        metaMuted = false;

        String meta = story.getEditorMeta();
        org.json.JSONObject json = null;
        if (meta != null && !meta.isEmpty()) {
            try {
                json = new org.json.JSONObject(meta);
                trimStartMs = json.optLong("trimStartMs", 0L);
                trimEndMs = json.optLong("trimEndMs", 0L);
                metaMuted = json.optBoolean("muted", false);
                metaFit = json.optBoolean("fit", false);
            } catch (Exception e) {
                Log.w(TAG_API, "bad editor_meta: " + e.getMessage());
            }
        }

        startStoryMusic(json);

        boolean isVideo = story.getMediaType() != null
                && story.getMediaType().equalsIgnoreCase("video");
        if (!isVideo) {
            // stopStoryPlayer() clears overlayLayer and pollCard, so it must run *before*
            // renderPoll(); the other order silently discards the poll card.
            stopStoryPlayer();
            binding.ivStoryMedia.setVisibility(View.VISIBLE);
            // Nothing used to put the photo into this view, so every photo story showed the
            // empty black image view — including fully baked collages.
            loadStoryPhoto(story.getMediaUrl());
            // Section: GIF stickers are animated, so they are never baked into the photo; the
            // author's editor_meta is replayed here, over the image.
            renderGifStickers(json);
            renderPoll(json, story);
            return;
        }

        long span = trimEndMs - trimStartMs;
        currentDurationMs = span > 0 ? Math.min(span, 60_000L) : 60_000L;
        binding.ivStoryMedia.setVisibility(View.INVISIBLE);
        playStoryVideo(story.getMediaUrl());
        renderOverlays(json);
        renderPoll(json, story);
    }

    /**
     * Plays the story's attached track, if it has one.
     *
     * <p>Music is not part of the media file: a photo story is a baked JPEG and a video story
     * is the original clip, so the track is a separate column in editor_meta. It loops under
     * the story and is released by stopStoryMusic() as soon as the story changes.
     */
    private void startStoryMusic(@Nullable org.json.JSONObject json) {
        stopStoryMusic();
        if (json == null) return;
        String rawUrl = json.optString("music_url", "");
        if (rawUrl.isEmpty()) return;
        try {
            String url = com.insta.nova.util.MediaUrl.toAuthenticated(
                    com.insta.nova.util.MediaUrl.normalize("stories", rawUrl));
            storyMusic = new androidx.media3.exoplayer.ExoPlayer.Builder(this).build();
            storyMusic.setVolume(metaMuted ? 0f : 0.9f);
            storyMusic.setRepeatMode(androidx.media3.common.Player.REPEAT_MODE_ONE);
            storyMusic.setMediaSource(new androidx.media3.exoplayer.source.ProgressiveMediaSource
                    .Factory(com.insta.nova.util.MediaUrl.streamFactory())
                    .createMediaSource(androidx.media3.common.MediaItem.fromUri(url)));
            storyMusic.prepare();
            storyMusic.setPlayWhenReady(true);
        } catch (Exception e) {
            Log.e(TAG_API, "story music failed: " + e.getMessage(), e);
            stopStoryMusic();
        }
    }

    private void stopStoryMusic() {
        if (storyMusic == null) return;
        try {
            storyMusic.release();
        } catch (Exception ignored) {
        }
        storyMusic = null;
    }

    /**
     * Draws the story's freehand strokes.
     *
     * <p>The editor bakes strokes into a transparent PNG and stores it as meta.overlay, but
     * nothing ever read that key back, so drawing on a story — photo or video — vanished on
     * publish. The PNG is scaled to the media box, which is the same 9:16 frame it was made in.
     */
    /** Loads a photo story; the bucket is private, so the request carries the auth headers. */
    private void loadStoryPhoto(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) return;
        String url = com.insta.nova.util.MediaUrl.toAuthenticated(
                com.insta.nova.util.MediaUrl.normalize("stories", rawUrl));
        com.bumptech.glide.request.RequestOptions options =
                new com.bumptech.glide.request.RequestOptions()
                        .placeholder(android.R.color.black)
                        .error(android.R.color.black);
        com.bumptech.glide.load.model.LazyHeaders.Builder headers =
                new com.bumptech.glide.load.model.LazyHeaders.Builder();
        for (java.util.Map.Entry<String, String> header
                : com.insta.nova.util.MediaUrl.streamHeaders().entrySet()) {
            headers.addHeader(header.getKey(), header.getValue());
        }
        com.bumptech.glide.Glide.with(this)
                .load(new com.bumptech.glide.load.model.GlideUrl(url, headers.build()))
                .apply(options)
                .into(binding.ivStoryMedia);
    }

    private void playStoryVideo(String rawUrl) {
        stopStoryPlayer();
        try {
            ViewGroup parent = (ViewGroup) binding.ivStoryMedia.getParent();
            if (parent == null) return;

            int parentW = parent.getWidth() > 0 ? parent.getWidth() : 1080;
            int parentH = parent.getHeight() > 0 ? parent.getHeight() : 1920;
            // The editor's stage is a fixed 9:16 frame and the stroke overlay is drawn in that
            // space. The clip therefore has to be boxed the same way, otherwise a FIT_XY
            // overlay is stretched to the full-bleed screen and the drawing drifts off target.
            int boxH = Math.min(parentH, Math.round(parentW * 16f / 9f));
            int boxW = Math.min(parentW, Math.round(boxH * 9f / 16f));

            mediaBox = new android.widget.FrameLayout(this);
            mediaBox.setClipChildren(false);
            android.widget.FrameLayout.LayoutParams boxLp = new android.widget.FrameLayout.LayoutParams(
                    boxW, boxH);
            boxLp.gravity = android.view.Gravity.CENTER;
            parent.addView(mediaBox, parent.indexOfChild(binding.ivStoryMedia), boxLp);

            storyPlayerView = new androidx.media3.ui.PlayerView(this);
            storyPlayerView.setUseController(false);
            storyPlayerView.setResizeMode(metaFit
                    ? androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    : androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
            mediaBox.addView(storyPlayerView, new android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            String url = com.insta.nova.util.MediaUrl.toAuthenticated(
                    com.insta.nova.util.MediaUrl.normalize("stories", rawUrl));
            storyPlayer = new androidx.media3.exoplayer.ExoPlayer.Builder(this).build();
            storyPlayer.setVolume(metaMuted ? 0f : 1f);
            storyPlayer.setMediaSource(new androidx.media3.exoplayer.source.ProgressiveMediaSource
                    .Factory(com.insta.nova.util.MediaUrl.streamFactory())
                    .createMediaSource(androidx.media3.common.MediaItem.fromUri(url)));
            storyPlayer.prepare();
            storyPlayer.seekTo(trimStartMs);
            storyPlayer.setPlayWhenReady(true);
            storyPlayerView.setPlayer(storyPlayer);
        } catch (Exception e) {
            Log.e(TAG_API, "video story failed: " + e.getMessage(), e);
            binding.ivStoryMedia.setVisibility(View.VISIBLE);
        }
    }

    private android.widget.FrameLayout ensureOverlayLayer() {
        if (overlayLayer != null) return overlayLayer;
        ViewGroup parent = mediaBox != null
                ? mediaBox : (ViewGroup) binding.ivStoryMedia.getParent();
        if (parent == null) return null;
        overlayLayer = new android.widget.FrameLayout(this);
        overlayLayer.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlayLayer.setClipChildren(false);
        parent.addView(overlayLayer);
        return overlayLayer;
    }

    /**
     * Poll sticker: the question and options come from editor_meta, the counts from
     * story_poll_vote / story_poll_results. The card is rebuilt after a vote so percentages
     * and the chosen option replace the plain options.
     */
    private void renderPoll(org.json.JSONObject meta, Story story) {
        if (meta == null) return;
        org.json.JSONObject poll = meta.optJSONObject("poll");
        if (poll == null) return;
        android.widget.FrameLayout layer = ensureOverlayLayer();
        if (layer == null) return;

        String question = poll.optString("question", "");
        org.json.JSONArray options = poll.optJSONArray("options");
        if (question.isEmpty() || options == null || options.length() < 2) return;

        pollCard = new android.widget.LinearLayout(this);
        pollCard.setOrientation(android.widget.LinearLayout.VERTICAL);
        pollCard.setBackgroundColor(0xFFFFFFFF);
        pollCard.setPadding(32, 24, 32, 24);
        int w = (int) (getResources().getDisplayMetrics().density * 260);
        android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                w, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = android.view.Gravity.CENTER;
        layer.addView(pollCard, lp);

        android.widget.TextView q = new android.widget.TextView(this);
        q.setText(question);
        q.setTextColor(0xFF111111);
        q.setTextSize(16);
        q.setGravity(android.view.Gravity.CENTER);
        pollCard.addView(q);

        final java.util.List<String> labels = new java.util.ArrayList<>();
        for (int i = 0; i < options.length(); i++) labels.add(options.optString(i, ""));
        final String storyId = story.getId();
        final boolean votable = !isOwnStory();
        // A poll needs reading time; the 5 s story clock would otherwise advance while the
        // viewer is still deciding. The author only sees the tally, so their story keeps going.
        if (votable) pauseForPoll();
        paintPollOptions(storyId, labels, null, -1, 0, votable);

        com.insta.nova.data.remote.api.RestApi api = com.insta.nova.data.remote.RetrofitClient
                .getInstance(this).getRestApi();

        java.util.Map<String, Object> args = new java.util.HashMap<>();
        args.put("p_story", storyId);
        api.storyPollMine(args).enqueue(new retrofit2.Callback<Integer>() {
            @Override public void onResponse(retrofit2.Call<Integer> c,
                                             retrofit2.Response<Integer> r) {
                Integer mine = r.body();
                int mineValue = mine == null ? -1 : mine;
                api.storyPollResults(args).enqueue(
                        resultsCallback(storyId, labels, mineValue, votable));
            }

            @Override public void onFailure(retrofit2.Call<Integer> c, Throwable t) {
                android.util.Log.w(TAG_API, "poll mine failed: " + t.getMessage());
            }
        });
    }

    private retrofit2.Callback<java.util.List<java.util.Map<String, Object>>> resultsCallback(
            String storyId, java.util.List<String> labels, int mine, boolean votable) {
        return new retrofit2.Callback<java.util.List<java.util.Map<String, Object>>>() {
            @Override public void onResponse(
                    retrofit2.Call<java.util.List<java.util.Map<String, Object>>> c,
                    retrofit2.Response<java.util.List<java.util.Map<String, Object>>> r) {
                int[] counts = new int[labels.size()];
                int total = 0;
                if (r.body() != null) {
                    for (java.util.Map<String, Object> row : r.body()) {
                        int index = ((Number) row.get("option_index")).intValue();
                        int votes = ((Number) row.get("votes")).intValue();
                        if (index >= 0 && index < counts.length) {
                            counts[index] = votes;
                            total += votes;
                        }
                    }
                }
                paintPollOptions(storyId, labels, counts, mine, total, votable);
            }

            @Override public void onFailure(
                    retrofit2.Call<java.util.List<java.util.Map<String, Object>>> c,
                    Throwable t) {
                android.util.Log.w(TAG_API, "poll results failed: " + t.getMessage());
            }
        };
    }

    private void paintPollOptions(String storyId, java.util.List<String> labels,
                                   int[] counts, int mine, int total, boolean votable) {
        if (pollCard == null) return;
        while (pollCard.getChildCount() > 1) pollCard.removeViewAt(1);

        for (int i = 0; i < labels.size(); i++) {
            final int index = i;
            android.widget.TextView row = new android.widget.TextView(this);
            String text = labels.get(i);
            if (counts != null && total > 0) {
                int pct = Math.round(counts[i] * 100f / total);
                text = text + "   " + pct + "%";
            }
            row.setText(text);
            row.setTextSize(14);
            row.setTextColor(i == mine ? 0xFF1E88FF : 0xFF333333);
            row.setGravity(android.view.Gravity.CENTER);
            row.setPadding(16, 18, 16, 18);
            android.graphics.drawable.GradientDrawable bg =
                    new android.graphics.drawable.GradientDrawable();
            bg.setColor(i == mine ? 0x221E88FF : 0xFFF2F2F2);
            bg.setCornerRadius(24f);
            row.setBackground(bg);
            android.widget.LinearLayout.LayoutParams rowLp =
                    new android.widget.LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.topMargin = 12;
            pollCard.addView(row, rowLp);

            // The author sees the tally, not a ballot: voting on your own story is not a
            // thing Instagram allows and would skew the counts you are reading.
            if (mine < 0 && votable) {
                row.setOnClickListener(v -> vote(storyId, index, labels));
            }
        }
    }

    private void vote(String storyId, int option, java.util.List<String> labels) {
        java.util.Map<String, Object> args = new java.util.HashMap<>();
        args.put("p_story", storyId);
        args.put("p_option", option);
        com.insta.nova.data.remote.RetrofitClient.getInstance(this).getRestApi()
                .voteStoryPoll(args).enqueue(new retrofit2.Callback<Void>() {
                    @Override public void onResponse(retrofit2.Call<Void> c,
                                                     retrofit2.Response<Void> r) {
                        if (!r.isSuccessful()) {
                            android.util.Log.w(TAG_API, "vote HTTP " + r.code());
                            return;
                        }
                        // The ballot is answered, so the story can move on immediately
                        // instead of waiting out the hold.
                        stopPollHold();
                        resumePlayback();
                        java.util.Map<String, Object> query = new java.util.HashMap<>();
                        query.put("p_story", storyId);
                        com.insta.nova.data.remote.RetrofitClient.getInstance(
                                StoryViewerActivity.this).getRestApi()
                                .storyPollResults(query)
                                .enqueue(resultsCallback(storyId, labels, option, true));
                    }

                    @Override public void onFailure(retrofit2.Call<Void> c, Throwable t) {
                        android.util.Log.w(TAG_API, "vote failed: " + t.getMessage());
                    }
                });
    }

    private int dpPx(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** Draws only the animated GIF stickers of a photo story, on their own layer. */
    private void renderGifStickers(@Nullable org.json.JSONObject json) {
        if (json == null) return;
        org.json.JSONArray elements = json.optJSONArray("elements");
        if (elements == null) return;
        final java.util.List<org.json.JSONObject> gifs = new java.util.ArrayList<>();
        for (int i = 0; i < elements.length(); i++) {
            org.json.JSONObject el = elements.optJSONObject(i);
            if (el == null) continue;
            String name = el.optString("gif", "");
            if (!name.isEmpty()) gifs.add(el);
        }
        if (gifs.isEmpty()) return;
        ViewGroup parent = (ViewGroup) binding.ivStoryMedia.getParent();
        if (parent == null) return;
        android.widget.FrameLayout layer = new android.widget.FrameLayout(this);
        layer.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        parent.addView(layer);
        layer.post(() -> {
            int w = layer.getWidth();
            int h = layer.getHeight();
            for (org.json.JSONObject el : gifs) {
                android.widget.ImageView sticker = new android.widget.ImageView(this);
                com.bumptech.glide.Glide.with(this)
                        .load("file:///android_asset/gifs/" + el.optString("gif") + ".gif")
                        .into(sticker);
                float scale = (float) el.optDouble("scale", 1);
                sticker.setScaleX(scale);
                sticker.setScaleY(scale);
                sticker.setRotation((float) el.optDouble("rot", 0));
                int size = dpPx(120);
                layer.addView(sticker, new android.widget.FrameLayout.LayoutParams(size, size));
                final float x = (float) el.optDouble("x", 0.5) * w;
                final float y = (float) el.optDouble("y", 0.45) * h;
                final int half = size / 2;
                sticker.post(() -> {
                    sticker.setX(x - half);
                    sticker.setY(y - half);
                });
            }
        });
    }

    private void renderOverlays(org.json.JSONObject meta) {
        if (meta == null) return;
        // Videos live inside a 9:16 mediaBox so the stroke PNG lines up with the frame it was
        // drawn on; photos keep painting straight into the full-bleed media container.
        ViewGroup parent = mediaBox != null
                ? mediaBox : (ViewGroup) binding.ivStoryMedia.getParent();
        if (parent == null) return;

        overlayLayer = new android.widget.FrameLayout(this);
        overlayLayer.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlayLayer.setClipChildren(false);
        parent.addView(overlayLayer);
        overlayLayer.post(() -> {
            int w = overlayLayer.getWidth();
            int h = overlayLayer.getHeight();

            String overlayUrl = meta.optString("overlay", null);
            if (overlayUrl != null && !overlayUrl.isEmpty()) {
                android.widget.ImageView art = new android.widget.ImageView(this);
                art.setScaleType(android.widget.ImageView.ScaleType.FIT_XY);
                overlayLayer.addView(art, new android.widget.FrameLayout.LayoutParams(w, h));
                com.insta.nova.util.GlideUtils.load(art, "stories", overlayUrl);
            }

            org.json.JSONArray elements = meta.optJSONArray("elements");
            if (elements == null) return;
            for (int i = 0; i < elements.length(); i++) {
                org.json.JSONObject el = elements.optJSONObject(i);
                if (el == null) continue;
                String gif = el.optString("gif", "");
                if (!gif.isEmpty()) {
                    // Bundled GIF: same asset the author picked, so no network fetch.
                    android.widget.ImageView sticker = new android.widget.ImageView(this);
                    com.bumptech.glide.Glide.with(this)
                            .load("file:///android_asset/gifs/" + gif + ".gif")
                            .into(sticker);
                    float gscale = (float) el.optDouble("scale", 1);
                    sticker.setScaleX(gscale);
                    sticker.setScaleY(gscale);
                    sticker.setRotation((float) el.optDouble("rot", 0));
                    overlayLayer.addView(sticker, new android.widget.FrameLayout.LayoutParams(
                            dpPx(120), dpPx(120)));
                    final float gx = (float) el.optDouble("x", 0.5) * w;
                    final float gy = (float) el.optDouble("y", 0.45) * h;
                    final int half = dpPx(60);
                    sticker.post(() -> {
                        sticker.setX(gx - half);
                        sticker.setY(gy - half);
                    });
                    continue;
                }
                String text = el.optString("text", "");
                if (text.isEmpty()) continue;
                android.widget.TextView tv = new android.widget.TextView(this);
                tv.setText(text);
                tv.setTextSize(el.optInt("sp", 22));
                tv.setTextColor(el.optInt("color", 0xFFFFFFFF));
                tv.setGravity(android.view.Gravity.CENTER);
                int font = el.optInt("font", 0);
                tv.setTypeface(font == 0
                        ? android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD)
                        : font == 3
                        ? android.graphics.Typeface.create("monospace", android.graphics.Typeface.NORMAL)
                        : android.graphics.Typeface.create("serif", android.graphics.Typeface.ITALIC));
                tv.setShadowLayer(font == 2 ? 18f : 6f, 0f, 0f,
                        font == 2 ? 0xFF00E5FF : 0x99000000);
                int bg = el.optInt("bg", 0);
                if (bg > 0) {
                    android.graphics.drawable.GradientDrawable d =
                            new android.graphics.drawable.GradientDrawable();
                    d.setColor(bg == 1 ? 0xCC000000 : 0x66000000);
                    d.setCornerRadius(24f);
                    tv.setBackground(d);
                    tv.setPadding(16, 8, 16, 8);
                }
                float scale = (float) el.optDouble("scale", 1);
                tv.setScaleX(scale);
                tv.setScaleY(scale);
                tv.setRotation((float) el.optDouble("rot", 0));
                overlayLayer.addView(tv, new android.widget.FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                final float x = (float) el.optDouble("x", 0.5) * w;
                final float y = (float) el.optDouble("y", 0.4) * h;
                tv.post(() -> {
                    tv.setX(x - tv.getWidth() / 2f);
                    tv.setY(y - tv.getHeight() / 2f);
                });
            }
        });
    }

    private void stopStoryPlayer() {
        pollCard = null;
        if (mediaBox != null) {
            ViewGroup boxParent = (ViewGroup) mediaBox.getParent();
            if (boxParent != null) boxParent.removeView(mediaBox);
            mediaBox = null;
        }
        if (overlayLayer != null) {
            ViewGroup parent = (ViewGroup) overlayLayer.getParent();
            if (parent != null) parent.removeView(overlayLayer);
            overlayLayer = null;
        }
        if (storyPlayerView != null) {
            ViewGroup parent = (ViewGroup) storyPlayerView.getParent();
            if (parent != null) parent.removeView(storyPlayerView);
            storyPlayerView = null;
        }
        if (storyPlayer != null) {
            storyPlayer.release();
            storyPlayer = null;
        }
    }

    /**
     * Own ring with no story: hand off to the camera instead of a dead "could not open"
     * toast, which is what Instagram does when you tap your empty story circle.
     */
    private boolean openCameraIfMine() {
        if (storyUserId == null || currentUserId == null
                || !storyUserId.trim().equals(currentUserId.trim())) {
            return false;
        }
        startActivity(new android.content.Intent(this,
                com.insta.nova.ui.story2.StoryCameraActivity.class));
        finish();
        return true;
    }

    private void loadStories() {
        binding.progressStory.setVisibility(View.VISIBLE);
        String singleStoryId = getIntent() != null ? getIntent().getStringExtra("story_id") : null;
        Map<String, String> query = new HashMap<>();
        if (singleStoryId != null && !singleStoryId.isEmpty()) {
            // Archive (Settings > Archive): open one exact story, expired or not.
            query.put("id", "eq." + singleStoryId);
        } else {
            query.put("user_id", "eq." + storyUserId);
            query.put("expires_at", "gt." + com.insta.nova.util.TimeUtils.toSupabaseTimestamp(System.currentTimeMillis()));
        }
query.put("order", "created_at.asc");

        restApi.getStories(query).enqueue(new Callback<List<StoryDto>>() {
            @Override
            public void onResponse(Call<List<StoryDto>> call, Response<List<StoryDto>> response) {
                binding.progressStory.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    stories.clear();
                    for (StoryDto dto : response.body()) stories.add(dto.toDomain());
                    binding.tvStoryUsername.setText(stories.get(0).getUsername() != null
                            ? stories.get(0).getUsername() : storyUsername);
                    buildProgressBars();
                    showStory(0);
                } else {
                    Log.d(TAG_API, "No active stories for " + storyUserId + " (HTTP " + response.code() + ")");
                    if (fallBackToLocalCopy()) return;
                    if (openCameraIfMine()) return;
                    toast(getString(R.string.story_vw_open_failed));
                    finish();
                }
            }

            @Override
            public void onFailure(Call<List<StoryDto>> call, Throwable t) {
                binding.progressStory.setVisibility(View.GONE);
                Log.e(TAG_API, "Failed to load stories: " + t.getMessage(), t);
                if (fallBackToLocalCopy()) return;
                if (openCameraIfMine()) return;
                toast(getString(R.string.story_vw_open_failed));
                finish();
            }
        });
    }

    /** One segment per story: a track with a white fill that grows linearly. */
    private void buildProgressBars() {
        binding.layoutProgressBars.removeAllViews();
        progressFills.clear();
        progressTracks.clear();

        for (int i = 0; i < stories.size(); i++) {
            FrameLayout segment = new FrameLayout(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            if (i > 0) lp.setMarginStart(dp(R.dimen.story_vw_progress_gap));
            segment.setLayoutParams(lp);

            View track = new View(this);
            track.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            track.setBackgroundResource(R.drawable.bg_story_progress_track);

            View fill = new View(this);
            fill.setLayoutParams(new FrameLayout.LayoutParams(0, FrameLayout.LayoutParams.MATCH_PARENT));
            fill.setBackgroundResource(R.drawable.bg_story_progress_fill);

            segment.addView(track);
            segment.addView(fill);
            binding.layoutProgressBars.addView(segment);
            progressTracks.add(track);
            progressFills.add(fill);
        }
    }

    private void setSegmentProgress(int index, float fraction) {
        if (index < 0 || index >= progressFills.size()) return;
        View track = progressTracks.get(index);
        int width = track.getWidth() > 0 ? track.getWidth()
                : binding.layoutProgressBars.getWidth() / Math.max(1, stories.size());
        ViewGroup.LayoutParams lp = progressFills.get(index).getLayoutParams();
        lp.width = Math.round(Math.max(0f, Math.min(1f, fraction)) * width);
        progressFills.get(index).setLayoutParams(lp);
    }

    private void showStory(int index) {
        if (index < 0 || index >= stories.size()) {
            finish();
            return;
        }
        currentStoryIndex = index;
        Story story = stories.get(index);

        if (story.getUsername() != null && !story.getUsername().isEmpty()) {
            binding.tvStoryUsername.setText(story.getUsername());
        }
        if (story.getAvatarUrl() != null && !story.getAvatarUrl().isEmpty()) {
            com.insta.nova.util.GlideUtils.loadAvatar(binding.ivStoryAvatar, story.getAvatarUrl());
        }
        binding.tvStoryTime.setText(story.getCreatedAt() > 0
                ? TimeUtils.formatRelativeTime(story.getCreatedAt()) : "");
        if (binding.tvCfIndicator != null) {
            binding.tvCfIndicator.setVisibility(story.isCloseFriends() ? View.VISIBLE : View.GONE);
        }

        for (int i = 0; i < progressFills.size(); i++) {
            setSegmentProgress(i, i < index ? 1f : 0f);
        }

        // The timer must not start until the frame is actually on screen. Starting it at
        // showStory() meant a slow load could run the whole 5 s behind a spinner and close
        // the viewer before the story was ever visible.
        final int loadToken = ++mediaLoadToken;
        boolean storyIsVideo = story.getMediaType() != null
                && story.getMediaType().equalsIgnoreCase("video");
        if (storyIsVideo) {
            com.insta.nova.util.GlideUtils.load(binding.ivStoryMedia, "stories", story.getMediaUrl());
        } else {
            com.insta.nova.util.GlideUtils.load(binding.ivStoryMedia, "stories",
                    story.getMediaUrl(), () -> {
                        if (loadToken == mediaLoadToken) restartPlayback();
                    });
        }
        bindStoryMedia(story);

        // Use the owner id returned with this exact story, not only the launch
        // intent's group id. This keeps owner-view exclusion correct even when
        // a viewer advances through a mixed or stale story payload.
        boolean hasIdentity = currentUserId != null
                && story.getUserId() != null
                && !currentUserId.trim().isEmpty()
                && !story.getUserId().trim().isEmpty();
        boolean mine = hasIdentity && currentUserId.equals(story.getUserId());
        boolean shouldRecord = hasIdentity
                && StoryRepository.shouldRecordView(currentUserId, story.getUserId());
        binding.layoutOwnerBar.setVisibility(mine ? View.VISIBLE : View.GONE);
        binding.layoutReplyBar.setVisibility(mine ? View.GONE : View.VISIBLE);

        if (mine) {
            loadViewersCount(story.getId());
        } else if (shouldRecord) {
            recordSeenState(story.getId());
        } else {
            Log.w(TAG_API, "Skipping story view: unresolved viewer/story owner identity for " + story.getId());
        }

        if (storyIsVideo) restartPlayback();
    }

    /**
     * Marks the story as seen by the current viewer. The repository returns the
     * outcome so we can log only on confirmed success — the previous
     * "Recorded view" log was written before the API responded and reported
     * success even when the row insert failed.
     */
    private void recordSeenState(String storyId) {
        storyRepository.markStorySeen(storyId).observe(this, resource -> {
            if (resource == null) return;
            if (resource.status == Resource.Status.SUCCESS) {
                Log.d(TAG_API, "Recorded view for story " + storyId);
            } else if (resource.status == Resource.Status.ERROR) {
                Log.w(TAG_API, "Could not record view for story " + storyId
                        + ": " + resource.message);
            }
        });
    }

    private void restartPlayback() {
        stopTick();
        accumulatedMs = 0;
        lastTickAt = System.currentTimeMillis();
        paused = false;
        tick = new Runnable() {
            @Override
            public void run() {
                if (paused) {
                    lastTickAt = System.currentTimeMillis();
                    handler.postDelayed(this, TICK_MS);
                    return;
                }
                long now = System.currentTimeMillis();
                accumulatedMs += now - lastTickAt;
                lastTickAt = now;
                float fraction = accumulatedMs / (float) Math.max(1000L, currentDurationMs);
                setSegmentProgress(currentStoryIndex, fraction);
                if (fraction >= 1f) {
                    nextStory();
                } else {
                    handler.postDelayed(this, TICK_MS);
                }
            }
        };
        handler.post(tick);
    }

    private void stopPollHold() {
        handler.removeCallbacks(pollResume);
    }

    private void stopTick() {
        stopPollHold();
        if (tick != null) {
            handler.removeCallbacks(tick);
            tick = null;
        }
    }

    /** Holds the story open for a ballot, with a ceiling so it can never stick. */
    private void pauseForPoll() {
        pausePlayback();
        handler.removeCallbacks(pollResume);
        handler.postDelayed(pollResume, 10_000L);
    }

    private final Runnable pollResume = new Runnable() {
        @Override public void run() {
            resumePlayback();
        }
    };

    private void pausePlayback() {
        paused = true;
    }

    private void resumePlayback() {
        paused = false;
        lastTickAt = System.currentTimeMillis();
    }

    private void nextStory() {
        stopTick();
        setSegmentProgress(currentStoryIndex, 1f);
        if (currentStoryIndex + 1 < stories.size()) {
            showStory(currentStoryIndex + 1);
        } else {
            finish();
        }
    }

    private void previousStory() {
        stopTick();
        setSegmentProgress(currentStoryIndex, 0f);
        showStory(Math.max(0, currentStoryIndex - 1));
    }

    private boolean isOwnStory() {
        return currentUserId != null && currentUserId.equals(storyUserId);
    }

    /**
     * Nothing came back from the server for the viewer's own story.
     *
     * <p>This is the case that answered "Story unavailable" for a story the phone still held: the
     * row had expired, or the object was deleted while the cached copy stayed. The cached copy
     * always wins over a dead end.
     */
    private boolean fallBackToLocalCopy() {
        if (!isOwnStory()) return false;
        com.insta.nova.data.local.LocalSocialStore store =
                new com.insta.nova.data.local.LocalSocialStore(this);
        if (store.getActiveStories().isEmpty()) return false;
        Log.d(TAG_API, "No server story; showing the cached copy instead");
        startActivity(new android.content.Intent(this, LocalStoryViewerActivity.class));
        finish();
        return true;
    }

    /* ------------------------------------------------------------------ */
    /* Messaging                                                           */
    /* ------------------------------------------------------------------ */

    /**
     * A reaction to a story notifies the owner; it is deliberately not a direct
     * message. It used to call sendMessageToOwner(), which created a conversation and
     * posted the emoji into it, so every reaction showed up as a DM.
     *
     * <p>Typed replies still go through sendMessageToOwner() — a reply is a message,
     * a tap on the emoji is not.
     */
    private void sendReaction(String payload) {
        if (isOwnStory()) {
            return;
        }
        playFloating(payload);

        String storyId = stories.isEmpty() ? null : stories.get(currentStoryIndex).getId();
        if (storyId == null) return;

        Map<String, Object> body = new HashMap<>();
        body.put("p_story", storyId);
        body.put("p_reaction", payload);
        restApi.reactToStory(body).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (!response.isSuccessful()) {
                    Log.e(TAG_API, "react_to_story failed HTTP " + response.code());
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e(TAG_API, "react_to_story network failure: " + t.getMessage(), t);
            }
        });
    }

    private void sendTypedReply() {
        String text = binding.etStoryReply.getText() != null ? binding.etStoryReply.getText().toString().trim() : "";
        if (text.isEmpty()) return;
        binding.etStoryReply.setText("");
        sendMessageToOwner(text, null);
    }

    /** Sends the story image itself as an image message. */
    private void shareStory() {
        final String mediaUrl = stories.isEmpty() ? null : stories.get(currentStoryIndex).getMediaUrl();
        if (mediaUrl == null) return;
        UserListSheet.show(this, getString(R.string.share), true, getString(R.string.inbox_new_message),
                query -> socialRepository.getFollowing(currentUserId),
                profile -> sendMessageToOwner(null, mediaUrl, profile));
    }

    private void sendMessageToOwner(String text, String mediaUrl) {
        sendMessageToOwner(text, mediaUrl, null);
    }

    private void sendMessageToOwner(String text, String mediaUrl, com.insta.nova.data.model.Profile target) {
        final String ownerId = target != null ? target.getId() : storyUserId;
        if (ownerId == null) return;
        chatRepository.getOrCreateConversation(ownerId).observe(this, resource -> {
            if (resource == null) return;
            if (resource.status == Resource.Status.SUCCESS && resource.data != null) {
                chatRepository.sendMessage(resource.data, text, mediaUrl).observe(this, sent -> {
                    if (sent == null) return;
                    if (sent.status == Resource.Status.ERROR) {
                        Log.e(TAG_API, "Story reply failed: " + sent.message);
                        toast(sent.message == null ? getString(R.string.story_vw_open_failed) : sent.message);
                    } else if (sent.status == Resource.Status.SUCCESS) {
                        Log.d(TAG_API, "Story reply sent to " + ownerId
                                + (mediaUrl != null ? " (image " + mediaUrl + ")" : " (text)"));
                    }
                });
            } else if (resource.status == Resource.Status.ERROR) {
                Log.e(TAG_API, "get_or_create_conversation failed: " + resource.message);
                toast(resource.message == null ? getString(R.string.story_vw_open_failed) : resource.message);
            }
        });
    }

    private void playFloating(String payload) {
        final TextView tv = new TextView(this);
        tv.setText(payload);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.story_vw_emoji_size));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM | Gravity.END;
        lp.bottomMargin = dp(R.dimen.story_vw_bar_height);
        lp.rightMargin = dp(R.dimen.story_vw_bar_pad);
        tv.setLayoutParams(lp);
        binding.getRoot().addView(tv);

        ObjectAnimator up = ObjectAnimator.ofFloat(tv, "translationY", 0f, -dpF(240f));
        ObjectAnimator fade = ObjectAnimator.ofFloat(tv, "alpha", 1f, 0f);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(up, fade);
        set.setDuration(1200);
        set.start();
        handler.postDelayed(() -> binding.getRoot().removeView(tv), 1300);
    }

    /* ------------------------------------------------------------------ */
    /* Owner bar / sheets                                                  */
    /* ------------------------------------------------------------------ */

    private void loadViewersCount(String storyId) {
        Map<String, String> query = new HashMap<>();
        query.put("story_id", "eq." + storyId);
        query.put("select", "viewer_id");
        restApi.getStoryViews(query).enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> call, Response<List<Map<String, Object>>> response) {
                viewersCount = response.isSuccessful() && response.body() != null ? response.body().size() : 0;
                binding.tvViewersCount.setText(getString(R.string.story_vw_seen_by, viewersCount));
            }

            @Override
            public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                viewersCount = 0;
                binding.tvViewersCount.setText(getString(R.string.story_vw_seen_by, viewersCount));
            }
        });
    }

    private void showViewersSheet() {
        if (stories.isEmpty()) return;
        final String storyId = stories.get(currentStoryIndex).getId();
        UserListSheet.show(this, getString(R.string.story_vw_seen_by, viewersCount), false, null,
                query -> socialRepository.getStoryViewers(storyId),
                profile -> Log.d(TAG_API, "Viewer tapped: @" + profile.getUsername()));
    }

    private void showMoreSheet() {
        final BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.InstaNovaBottomSheetDialog);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundResource(R.drawable.bg_bottom_sheet);

        if (isOwnStory()) {
            container.addView(sheetRow(R.string.story_vw_delete, v -> {
                dialog.dismiss();
                confirmDeleteStory();
            }));
            container.addView(sheetRow(R.string.story_vw_save, v -> {
                dialog.dismiss();
                saveCurrentStory();
            }));
            container.addView(sheetRow(R.string.story_vw_highlight, v -> {
                dialog.dismiss();
                addToHighlight();
            }));
            // Per-story privacy: close friends, everyone, or everyone except the hide list.
            TextView privacyRow = sheetRow(R.string.story_vw_privacy, v -> {
                dialog.dismiss();
                choosePrivacy();
            });
            privacyRow.setText(getString(R.string.story_vw_privacy_row, privacyLabel()));
            container.addView(privacyRow);
            container.addView(sheetRow(R.string.story_vw_archive, v -> {
                dialog.dismiss();
                archiveCurrentStory();
            }));
        } else {
            container.addView(sheetRow(R.string.story_vw_report, v -> {
                dialog.dismiss();
                String id = stories.isEmpty() ? null : stories.get(currentStoryIndex).getId();
                if (id == null) {
                    toast(getString(R.string.nova_error));
                    return;
                }
                Log.d(TAG_API, "Story reported: " + id);
                com.insta.nova.safe.SafeActions.report(this, "story", id);
            }));
        }
        container.addView(sheetRow(R.string.story_vw_cancel, v -> dialog.dismiss()));

        dialog.setContentView(container);
        dialog.show();
    }

    private boolean isCurrentStoryCloseFriends() {
        return !stories.isEmpty() && stories.get(currentStoryIndex).isCloseFriends();
    }

    /** audience values: everyone | public (everyone except the hide list) | close_friends. */
    private String currentAudience() {
        if (stories.isEmpty()) return "public";
        String audience = stories.get(currentStoryIndex).getAudience();
        return audience == null || audience.trim().isEmpty() ? "public" : audience;
    }

    private String privacyLabel() {
        String audience = currentAudience();
        if ("close_friends".equalsIgnoreCase(audience)) {
            return getString(R.string.story_vw_privacy_close);
        }
        if ("everyone".equalsIgnoreCase(audience)) {
            return getString(R.string.story_vw_privacy_everyone);
        }
        return getString(R.string.story_vw_privacy_except);
    }

    /**
     * Three-way story privacy. Writes the same {@code audience} column {@code story_visible()}
     * reads, so the choice takes effect for every client at once instead of being filtered
     * locally: {@code close_friends} narrows to the list, {@code everyone} ignores the hide
     * list, and {@code public} keeps the hide list in force.
     */
    private void choosePrivacy() {
        if (stories.isEmpty()) return;
        final Story story = stories.get(currentStoryIndex);
        final String[] values = {"close_friends", "everyone", "public"};
        final String[] labels = {
                getString(R.string.story_vw_privacy_close),
                getString(R.string.story_vw_privacy_everyone),
                getString(R.string.story_vw_privacy_except)
        };
        String current = currentAudience();
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equalsIgnoreCase(current)) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.story_vw_privacy)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    applyAudience(story, values[which]);
                })
                .setNegativeButton(R.string.story_vw_cancel, null)
                .show();
    }

    /**
     * Archive = leave the tray now and keep the row for the Archive tab. The row is not
     * deleted: it is flagged and its window is closed, which is exactly what the archive
     * screen lists and what "Unarchive" reverses.
     */
    private void archiveCurrentStory() {
        if (stories.isEmpty()) return;
        final Story story = stories.get(currentStoryIndex);
        // Only the flag: writing expires_at as well made the new row fail the SELECT policy
        // (which requires expires_at > now()), so the PATCH returned 204 and changed nothing.
        // Archived rows are hidden by v_stories / get_story_groups on is_archived alone.
        Map<String, Object> body = new HashMap<>();
        body.put("is_archived", true);
        restApi.updateStory("eq." + story.getId(), body).enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> call, Response<Void> response) {
                if (!response.isSuccessful()) {
                    toast(getString(R.string.story_vw_archive_failed) + " (HTTP " + response.code() + ")");
                    return;
                }
                toast(getString(R.string.story_vw_archived));
                int index = currentStoryIndex;
                stories.remove(index);
                if (stories.isEmpty()) {
                    finish();
                } else {
                    buildProgressBars();
                    showStory(Math.min(index, stories.size() - 1));
                }
            }

            @Override public void onFailure(Call<Void> call, Throwable t) {
                toast(getString(R.string.story_vw_archive_failed));
            }
        });
    }

    private void applyAudience(@NonNull Story story, @NonNull String audience) {
        if (audience.equalsIgnoreCase(story.getAudience())) return;
        Map<String, Object> body = new HashMap<>();
        body.put("audience", audience);
        restApi.updateStory("eq." + story.getId(), body).enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    story.setAudience(audience);
                    toast(getString(R.string.story_vw_audience_saved));
                } else {
                    toast(getString(R.string.story_vw_audience_failed) + " (HTTP " + response.code() + ")");
                }
            }

            @Override public void onFailure(Call<Void> call, Throwable t) {
                toast(getString(R.string.story_vw_audience_failed));
            }
        });
    }

    private TextView sheetRow(int textRes, View.OnClickListener listener) {
        TextView row = new TextView(this);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(R.dimen.create_sheet_row_height)));
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(R.dimen.create_sheet_row_padding_horizontal), 0,
                dp(R.dimen.create_sheet_row_padding_horizontal), 0);
        row.setText(textRes);
        row.setTextAppearance(R.style.TextAppearance_Insta_CreateRow);
        row.setOnClickListener(listener);
        return row;
    }

    private void confirmDeleteStory() {
        if (stories.isEmpty()) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.story_vw_delete_confirm)
                .setMessage(R.string.story_vw_delete_confirm_msg)
                .setPositiveButton(R.string.story_vw_delete, (d, w) -> deleteCurrentStory())
                .setNegativeButton(R.string.story_vw_cancel, null)
                .show();
    }

    private void deleteCurrentStory() {
        final Story story = stories.get(currentStoryIndex);
        restApi.deleteStory("eq." + story.getId()).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Log.d(TAG_API, "Deleted story " + story.getId());
                    stories.remove(currentStoryIndex);
                    if (stories.isEmpty()) {
                        finish();
                    } else {
                        buildProgressBars();
                        showStory(Math.min(currentStoryIndex, stories.size() - 1));
                    }
                } else {
                    toast("Delete failed (HTTP " + response.code() + ")");
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                toast("Delete failed: " + t.getMessage());
            }
        });
    }

    private void saveCurrentStory() {
        if (stories.isEmpty()) return;
        final String url = stories.get(currentStoryIndex).getMediaUrl();
        Glide.with(this).asBitmap().load(url).into(new com.bumptech.glide.request.target.CustomTarget<Bitmap>() {
            @Override public void onResourceReady(@NonNull Bitmap resource, @Nullable com.bumptech.glide.request.transition.Transition<? super Bitmap> transition) {
                String name = "instanova_story_" + System.currentTimeMillis() + ".jpg";
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);
                }
                Uri target = null;
                try {
                    target = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                    if (target == null) throw new IOException("MediaStore returned no uri");
                    try (OutputStream os = getContentResolver().openOutputStream(target)) {
                        if (os == null) throw new IOException("No output stream");
                        resource.compress(Bitmap.CompressFormat.JPEG, 95, os);
                        os.flush();
                    }
                    Log.d(TAG_API, "Saved story to gallery: " + target);
                    toast(getString(R.string.story_ed_saved));
                } catch (IOException e) {
                    if (target != null) getContentResolver().delete(target, null, null);
                    Log.e(TAG_API, "Save story failed: " + e.getMessage(), e);
                    toast("Could not save: " + e.getMessage());
                }
            }
            @Override public void onLoadCleared(@Nullable Drawable placeholder) {}
            @Override public void onLoadFailed(@Nullable Drawable errorDrawable) {
                toast(getString(R.string.story_vw_open_failed));
            }
        });
    }

    private void addToHighlight() {
        if (stories.isEmpty()) return;
        final String url = stories.get(currentStoryIndex).getMediaUrl();
        String name = getString(R.string.story_vw_new_highlight);
        highlightRepository.createHighlight(name, url, Collections.singletonList(url))
                .observe(this, resource -> {
                    if (resource == null) return;
                    if (resource.status == Resource.Status.SUCCESS) {
                        Log.d(TAG_API, "Added story to a new highlight");
                        toast(getString(R.string.story_vw_highlight));
                    } else if (resource.status == Resource.Status.ERROR) {
                        toast(resource.message == null ? getString(R.string.story_vw_open_failed) : resource.message);
                    }
                });
    }

    /* ------------------------------------------------------------------ */
    /* Lifecycle                                                           */
    /* ------------------------------------------------------------------ */

    @Override
    protected void onPause() {
        stopStoryMusic();
        super.onPause();
        pausePlayback();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!replyFocused) resumePlayback();
    }

    @Override
    protected void onDestroy() {
        stopStoryPlayer();
        super.onDestroy();
        stopTick();
        handler.removeCallbacksAndMessages(null);
        binding = null;
    }

    private int dp(int dimenRes) {
        return getResources().getDimensionPixelSize(dimenRes);
    }

    private float dpF(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
