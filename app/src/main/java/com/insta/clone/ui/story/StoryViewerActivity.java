package com.insta.clone.ui.story;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.insta.clone.R;
import com.insta.clone.data.local.SessionManager;
import com.insta.clone.data.model.Story;
import com.insta.clone.data.remote.RetrofitClient;
import com.insta.clone.data.remote.api.RestApi;
import com.insta.clone.data.remote.dto.StoryDto;
import com.insta.clone.databinding.ActivityStoryViewerBinding;
import com.insta.clone.util.TimeUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Fullscreen activity for viewing stories with segmented progress bar and tap left/right progression.
 * Records story_views on Supabase when viewed.
 */
public class StoryViewerActivity extends AppCompatActivity {

    private static final String TAG_API = "InstaApi";
    private static final String TAG_GLIDE = "InstaGlide";
    private static final long STORY_DURATION_MS = 5000L;

    private ActivityStoryViewerBinding binding;
    private final List<Story> stories = new ArrayList<>();
    private final List<ProgressBar> progressBars = new ArrayList<>();
    private int currentStoryIndex = 0;
    private RestApi restApi;
    private String currentUserId;
    private String storyUserId;
    private boolean isPaused = false;

    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private Runnable progressRunnable;
    private long storyStartTime = 0;
    private long pausedElapsedTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityStoryViewerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        currentUserId = SessionManager.getInstance(this).getUserId();
        storyUserId = getIntent().getStringExtra("user_id");
        String username = getIntent().getStringExtra("username");
        String avatarUrl = getIntent().getStringExtra("avatar_url");

        if (username != null) {
            binding.tvStoryUsername.setText(username);
        }
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            com.insta.clone.util.GlideUtils.loadAvatar(binding.ivStoryAvatar, avatarUrl);
        }

        restApi = RetrofitClient.getInstance(this).getRestApi();

        binding.btnCloseStory.setOnClickListener(v -> finish());

        setupTouchControls();

        if (storyUserId == null || storyUserId.isEmpty()) {
            storyUserId = currentUserId;
        }

        loadStoriesForUser(storyUserId);
    }

    private void setupTouchControls() {
        binding.touchLeft.setOnClickListener(v -> previousStory());
        binding.touchRight.setOnClickListener(v -> nextStory());

        View.OnTouchListener pauseTouchListener = (v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    pauseStory();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    resumeStory();
                    break;
            }
            return false;
        };

        binding.touchLeft.setOnTouchListener(pauseTouchListener);
        binding.touchRight.setOnTouchListener(pauseTouchListener);
    }

    private void loadStoriesForUser(String userId) {
        binding.progressStory.setVisibility(View.VISIBLE);
        Map<String, String> query = new HashMap<>();
        query.put("user_id", "eq." + userId);
        query.put("order", "created_at.asc");

        restApi.getStories(query).enqueue(new Callback<List<StoryDto>>() {
            @Override
            public void onResponse(Call<List<StoryDto>> call, Response<List<StoryDto>> response) {
                binding.progressStory.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    stories.clear();
                    for (StoryDto dto : response.body()) {
                        stories.add(dto.toDomain());
                    }
                    setupProgressBars();
                    showStory(0);
                } else {
                    Toast.makeText(StoryViewerActivity.this, "No active stories", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }

            @Override
            public void onFailure(Call<List<StoryDto>> call, Throwable t) {
                binding.progressStory.setVisibility(View.GONE);
                Log.e(TAG_API, "Failed to load stories: " + t.getMessage(), t);
                Toast.makeText(StoryViewerActivity.this, "Failed to load stories", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void setupProgressBars() {
        binding.layoutProgressBars.removeAllViews();
        progressBars.clear();

        for (int i = 0; i < stories.size(); i++) {
            ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            if (i > 0) lp.setMarginStart(6);
            pb.setLayoutParams(lp);
            pb.setMax(100);
            pb.setProgress(0);
            pb.setProgressDrawable(getDrawable(R.drawable.bg_story_add_badge)); // will fill smoothly
            binding.layoutProgressBars.addView(pb);
            progressBars.add(pb);
        }
    }

    private void showStory(int index) {
        if (index < 0 || index >= stories.size()) {
            finish();
            return;
        }

        currentStoryIndex = index;
        Story story = stories.get(index);

        binding.tvStoryUsername.setText(story.getUsername());
        if (story.getAvatarUrl() != null && !story.getAvatarUrl().isEmpty()) {
            com.insta.clone.util.GlideUtils.loadAvatar(binding.ivStoryAvatar, story.getAvatarUrl());
        }

        long createdTime = story.getCreatedAt();
        if (createdTime > 0) {
            binding.tvStoryTime.setText(TimeUtils.formatRelativeTime(createdTime));
        } else {
            binding.tvStoryTime.setText("");
        }

        for (int i = 0; i < progressBars.size(); i++) {
            if (i < index) progressBars.get(i).setProgress(100);
            else progressBars.get(i).setProgress(0);
        }

        // Record story view in Supabase
        recordStoryView(story.getId());

        // Check if viewing own story to show views count
        if (currentUserId != null && currentUserId.equals(story.getUserId())) {
            loadStoryViewsCount(story.getId());
        } else {
            binding.tvViewersCount.setVisibility(View.GONE);
        }

        // Load media image
        com.insta.clone.util.GlideUtils.load(binding.ivStoryMedia, "stories", story.getMediaUrl());

        startStoryTimer();
    }

    private void startStoryTimer() {
        stopStoryTimer();
        storyStartTime = System.currentTimeMillis();
        pausedElapsedTime = 0;

        progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (isPaused) return;

                long elapsed = (System.currentTimeMillis() - storyStartTime) + pausedElapsedTime;
                int progress = (int) Math.min(100, (elapsed * 100) / STORY_DURATION_MS);

                if (currentStoryIndex < progressBars.size()) {
                    progressBars.get(currentStoryIndex).setProgress(progress);
                }

                if (elapsed >= STORY_DURATION_MS) {
                    nextStory();
                } else {
                    progressHandler.postDelayed(this, 30);
                }
            }
        };
        progressHandler.post(progressRunnable);
    }

    private void stopStoryTimer() {
        if (progressRunnable != null) {
            progressHandler.removeCallbacks(progressRunnable);
            progressRunnable = null;
        }
    }

    private void pauseStory() {
        if (isPaused) return;
        isPaused = true;
        pausedElapsedTime += System.currentTimeMillis() - storyStartTime;
        stopStoryTimer();
    }

    private void resumeStory() {
        if (!isPaused) return;
        isPaused = false;
        storyStartTime = System.currentTimeMillis();
        if (progressRunnable != null) {
            progressHandler.post(progressRunnable);
        } else {
            startStoryTimer();
        }
    }

    private void nextStory() {
        stopStoryTimer();
        if (currentStoryIndex < progressBars.size()) {
            progressBars.get(currentStoryIndex).setProgress(100);
        }
        if (currentStoryIndex + 1 < stories.size()) {
            showStory(currentStoryIndex + 1);
        } else {
            finish();
        }
    }

    private void previousStory() {
        stopStoryTimer();
        if (currentStoryIndex < progressBars.size()) {
            progressBars.get(currentStoryIndex).setProgress(0);
        }
        if (currentStoryIndex - 1 >= 0) {
            showStory(currentStoryIndex - 1);
        } else {
            showStory(0);
        }
    }

    private void recordStoryView(String storyId) {
        if (currentUserId == null || storyId == null) return;
        Map<String, Object> body = new HashMap<>();
        body.put("story_id", storyId);
        body.put("viewer_id", currentUserId);

        restApi.recordStoryView(body).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Log.d(TAG_API, "Recorded view for story " + storyId);
                }
            }
            @Override
            public void onFailure(Call<Void> call, Throwable t) {}
        });
    }

    private void loadStoryViewsCount(String storyId) {
        Map<String, String> query = new HashMap<>();
        query.put("story_id", "eq." + storyId);
        restApi.getStoryViews(query).enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> call, Response<List<Map<String, Object>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    int count = response.body().size();
                    binding.tvViewersCount.setVisibility(View.VISIBLE);
                    binding.tvViewersCount.setText("Seen by " + count);
                }
            }
            @Override
            public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {}
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        pauseStory();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumeStory();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopStoryTimer();
        binding = null;
    }
}
