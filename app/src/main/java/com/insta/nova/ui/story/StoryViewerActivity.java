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
        binding.tvSegAvatar.setOnClickListener(v -> {
            if (com.insta.nova.util.Constants.DECORATIVE_UI) {
                com.insta.nova.util.ToastUtil.showNotAvailable(this);
            }
        });
    }

    private void wireHeader() {
        binding.btnCloseStory.setOnClickListener(v -> finish());
        binding.btnStoryMore.setOnClickListener(v -> showMoreSheet());
        binding.btnOwnerMore.setOnClickListener(v -> showMoreSheet());
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
                    toast(getString(R.string.story_vw_open_failed));
                    finish();
                }
            }

            @Override
            public void onFailure(Call<List<StoryDto>> call, Throwable t) {
                binding.progressStory.setVisibility(View.GONE);
                Log.e(TAG_API, "Failed to load stories: " + t.getMessage(), t);
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

        for (int i = 0; i < progressFills.size(); i++) {
            setSegmentProgress(i, i < index ? 1f : 0f);
        }

        com.insta.nova.util.GlideUtils.load(binding.ivStoryMedia, "stories", story.getMediaUrl());

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

        restartPlayback();
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
                float fraction = accumulatedMs / STORY_DURATION_MS;
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

    private void stopTick() {
        if (tick != null) {
            handler.removeCallbacks(tick);
            tick = null;
        }
    }

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

    /* ------------------------------------------------------------------ */
    /* Messaging                                                           */
    /* ------------------------------------------------------------------ */

    private void sendReaction(String payload) {
        if (isOwnStory()) {
            toast(getString(R.string.not_available));
            return;
        }
        playFloating(payload);
        sendMessageToOwner(payload, null);
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
        } else {
            container.addView(sheetRow(R.string.story_vw_report, v -> {
                dialog.dismiss();
                toast(getString(R.string.story_vw_reported));
                Log.d(TAG_API, "Story reported: " + (stories.isEmpty() ? "?" : stories.get(currentStoryIndex).getId()));
            }));
        }
        container.addView(sheetRow(R.string.story_vw_cancel, v -> dialog.dismiss()));

        dialog.setContentView(container);
        dialog.show();
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
