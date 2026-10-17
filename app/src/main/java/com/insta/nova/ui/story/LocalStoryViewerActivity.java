package com.insta.nova.ui.story;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.VideoView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.bumptech.glide.Glide;
import com.insta.nova.R;
import com.insta.nova.data.local.LocalSocialStore;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.model.Highlight;
import com.insta.nova.data.model.Story;
import com.insta.nova.databinding.ActivityLocalStoryViewerBinding;
import com.insta.nova.util.GlideUtils;
import com.insta.nova.util.TimeUtils;
import java.util.ArrayList;
import java.util.List;

/**
 * Viewer for locally stored stories and highlights.
 *
 * <p>One segment per story: images use a fixed 5 s slot, videos use the player's
 * duration. Tap right/left to move, swipe down or X to close, and the video is
 * always released in {@link #onDestroy()}.
 */
public class LocalStoryViewerActivity extends AppCompatActivity {

    public static final String EXTRA_HIGHLIGHT_ID = "highlight_id";
    private static final long IMAGE_DURATION_MS = 5000L;

    private ActivityLocalStoryViewerBinding binding;
    private final List<Story> stories = new ArrayList<>();
    private final List<View> fills = new ArrayList<>();
    private int index;
    private long storyStartedAt;
    private long storyDuration = IMAGE_DURATION_MS;
    private boolean playing;
    private float touchStartY;
    private float touchStartX;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!playing) return;
            long elapsed = SystemClock.elapsedRealtime() - storyStartedAt;
            float fraction = storyDuration <= 0 ? 1f
                    : Math.min(1f, elapsed / (float) storyDuration);
            setFill(index, fraction);
            if (fraction >= 1f) {
                advance();
            } else {
                handler.postDelayed(this, 16);
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        binding = ActivityLocalStoryViewerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        LocalSocialStore store = new LocalSocialStore(this);
        String highlightId = getIntent().getStringExtra(EXTRA_HIGHLIGHT_ID);
        if (highlightId != null) {
            Highlight highlight = store.findHighlight(highlightId);
            if (highlight != null) stories.addAll(store.storiesForHighlight(highlight));
        } else {
            stories.addAll(store.getActiveStories());
        }

        if (stories.isEmpty()) {
            finish();
            return;
        }

        SessionManager session = SessionManager.getInstance(this);
        binding.tvUsername.setText(session.getUsername() == null ? "" : session.getUsername());
        GlideUtils.loadAvatar(binding.ivAvatar, session.getAvatarUrl());
        binding.btnClose.setOnClickListener(v -> finish());

        View.OnTouchListener gesture = (v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                touchStartY = event.getRawY();
                touchStartX = event.getRawX();
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                float dy = event.getRawY() - touchStartY;
                float dx = Math.abs(event.getRawX() - touchStartX);
                if (dy > dp(100) && dx < dp(80)) {
                    closeAnimated();
                } else if (v.getId() == R.id.tapLeft) {
                    previous();
                } else {
                    advance();
                }
                return true;
            }
            return true;
        };
        binding.tapLeft.setOnTouchListener(gesture);
        binding.tapRight.setOnTouchListener(gesture);

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
            binding.storyHeader.setPadding(dp(12), top + dp(8), dp(12), dp(8));
            return insets;
        });

        buildProgress();
        show(0);
    }

    private void buildProgress() {
        binding.layoutProgress.removeAllViews();
        fills.clear();
        for (int i = 0; i < stories.size(); i++) {
            FrameLayout segment = new FrameLayout(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            if (i > 0) lp.setMarginStart(dp(4));
            segment.setLayoutParams(lp);

            View track = new View(this);
            GradientDrawable trackBg = new GradientDrawable();
            trackBg.setColor(Color.parseColor("#4DFFFFFF"));
            trackBg.setCornerRadius(dp(2));
            track.setBackground(trackBg);
            segment.addView(track, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            View fill = new View(this);
            GradientDrawable fillBg = new GradientDrawable();
            fillBg.setColor(Color.WHITE);
            fillBg.setCornerRadius(dp(2));
            fill.setBackground(fillBg);
            segment.addView(fill, new FrameLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            binding.layoutProgress.addView(segment);
            fills.add(fill);
        }
    }

    private void setFill(int segment, float fraction) {
        if (segment < 0 || segment >= fills.size()) return;
        View fill = fills.get(segment);
        View parent = (View) fill.getParent();
        int width = parent.getWidth();
        if (width <= 0) {
            // First layout pass: retry once the segment has a measured width.
            if (fill.getTag() == null) {
                fill.setTag(Boolean.TRUE);
                parent.post(() -> setFill(segment, fraction));
            }
            return;
        }
        ViewGroup.LayoutParams lp = fill.getLayoutParams();
        lp.width = Math.round(Math.max(0f, Math.min(1f, fraction)) * width);
        fill.setLayoutParams(lp);
    }

    private void closeAnimated() {
        binding.getRoot().animate().translationY(binding.getRoot().getHeight())
                .setDuration(160).withEndAction(this::finish).start();
    }

    private void show(int position) {
        if (position < 0 || position >= stories.size()) {
            finish();
            return;
        }
        handler.removeCallbacks(tick);
        releaseVideo();
        index = position;
        Story story = stories.get(position);

        binding.tvTime.setText(story.getCreatedAt() > 0
                ? TimeUtils.formatCompactRelativeTime(story.getCreatedAt()) : "");

        for (int i = 0; i < fills.size(); i++) {
            setFill(i, i < position ? 1f : 0f);
        }

        boolean video = "video".equalsIgnoreCase(story.getMediaType());
        binding.ivMedia.setVisibility(video ? View.GONE : View.VISIBLE);
        binding.videoView.setVisibility(video ? View.VISIBLE : View.GONE);

        if (video) {
            playVideo(story);
        } else {
            Glide.with(this).load(story.getMediaUrl()).into(binding.ivMedia);
            storyDuration = IMAGE_DURATION_MS;
            startTicker();
        }
    }

    private void playVideo(Story story) {
        storyDuration = IMAGE_DURATION_MS;
        try {
            binding.videoView.setVideoURI(Uri.parse(story.getMediaUrl()));
            binding.videoView.setOnPreparedListener(mp -> {
                mp.setLooping(false);
                storyDuration = Math.max(1000L, mp.getDuration());
                binding.videoView.start();
                startTicker();
            });
            binding.videoView.setOnErrorListener((mp, what, extra) -> {
                // Corrupt/unreadable clip: fall back to the 5 s slot instead of stalling.
                storyDuration = IMAGE_DURATION_MS;
                startTicker();
                return true;
            });
        } catch (Exception e) {
            storyDuration = IMAGE_DURATION_MS;
            startTicker();
        }
    }

    private void startTicker() {
        playing = true;
        storyStartedAt = SystemClock.elapsedRealtime();
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    private void advance() {
        if (index + 1 < stories.size()) {
            show(index + 1);
        } else {
            finish();
        }
    }

    private void previous() {
        if (index > 0) {
            show(index - 1);
        } else {
            show(0);
        }
    }

    private void releaseVideo() {
        try {
            binding.videoView.stopPlayback();
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onPause() {
        playing = false;
        handler.removeCallbacks(tick);
        try {
            binding.videoView.pause();
        } catch (Exception ignored) {
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (binding != null && !stories.isEmpty()) {
            // Restart the current segment so timers and video stay in sync.
            show(index);
        }
    }

    @Override
    protected void onDestroy() {
        playing = false;
        handler.removeCallbacksAndMessages(null);
        releaseVideo();
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
