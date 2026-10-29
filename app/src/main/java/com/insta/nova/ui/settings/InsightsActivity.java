package com.insta.nova.ui.settings;

import android.os.Bundle;

import com.insta.nova.R;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.remote.RetrofitClient;
import com.insta.nova.data.remote.api.RestApi;
import com.insta.nova.data.remote.dto.PostDto;
import com.insta.nova.ui.widget.InsightsChartView;
import com.insta.nova.util.TimeUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Post and Reel insights.
 *
 * <p>Every number is derived from this account's own rows: the same {@code posts}
 * fetch the rest of the app already uses. Nothing here is estimated or invented —
 * the app has no view-count or reach data, so the screen reports interactions
 * (likes + comments) rather than claiming views it cannot know.
 *
 * <p>Shape follows the Instagram account dashboard: a 7 / 30 / 90 day range, four
 * headline cards, a trend line over the range, a per-post bar comparison and a
 * ring splitting likes from comments.
 */
public class InsightsActivity extends SettingsPageActivity {

    private static final int[] RANGE_DAYS = {7, 30, 90};
    private static final String[] RANGE_LABELS = {"7 days", "30 days", "90 days"};

    private final List<PostDto> posts = new ArrayList<>();
    private int rangeIndex = 1;

    private InsightsChartView trendChart;
    private InsightsChartView topChart;
    private InsightsChartView splitChart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setPageTitle(R.string.st_insights_title);
        showLoading(true);
        load();
    }

    private void load() {
        String myId = SessionManager.getInstance(this).getUserId();
        if (myId == null || myId.isEmpty()) {
            showLoading(false);
            addParagraph(R.string.st_network_error);
            return;
        }
        RestApi api = RetrofitClient.getInstance(this).getRestApi();

        Map<String, String> query = new HashMap<>();
        query.put("user_id", "eq." + myId);
        query.put("order", "created_at.desc");
        query.put("limit", "500");
        api.getPosts(query).enqueue(new Callback<List<PostDto>>() {
            @Override
            public void onResponse(Call<List<PostDto>> call, Response<List<PostDto>> response) {
                showLoading(false);
                if (!response.isSuccessful() || response.body() == null) {
                    addParagraph(R.string.st_network_error);
                    return;
                }
                posts.clear();
                for (PostDto dto : response.body()) {
                    if (dto != null) posts.add(dto);
                }
                render();
            }

            @Override
            public void onFailure(Call<List<PostDto>> call, Throwable t) {
                showLoading(false);
                addParagraph(R.string.st_network_error);
            }
        });
    }

    /* ------------------------------------------------------------------ */
    /* Render                                                             */
    /* ------------------------------------------------------------------ */

    private void render() {
        content.removeAllViews();

        addChipRow(RANGE_LABELS, rangeIndex, index -> {
            if (index == rangeIndex) return;
            rangeIndex = index;
            render();
        });

        List<PostDto> window = postsWithinRange();

        int photos = 0;
        int reels = 0;
        long likes = 0;
        long comments = 0;
        for (PostDto dto : window) {
            if (dto.isReel()) reels++;
            else photos++;
            likes += dto.getLikesCount();
            comments += dto.getCommentsCount();
        }

        List<String[]> stats = new ArrayList<>();
        stats.add(new String[]{String.valueOf(photos), getString(R.string.st_total_posts)});
        stats.add(new String[]{String.valueOf(reels), getString(R.string.st_total_reels)});
        stats.add(new String[]{format(likes), getString(R.string.st_total_likes)});
        stats.add(new String[]{format(comments), getString(R.string.st_total_comments)});
        addStatGrid(stats);

        int accent = resolveThemeColor(R.attr.themePrimary, R.color.color_primary);
        int secondary = resolveThemeColor(R.attr.themeTextSecondary, R.color.color_text_secondary);

        /* Interactions per day over the range. */
        addSection(R.string.st_insights_trend);
        trendChart = addChart(InsightsChartView.Mode.AREA);
        trendChart.setEmptyText(getString(R.string.st_insights_no_activity));
        trendChart.setValues(dailyInteractions(window), labelsForDays());

        /* Per-post comparison — the last ten pieces in the range, oldest first. */
        addSection(R.string.st_insights_top_content);
        topChart = addChart(InsightsChartView.Mode.BAR);
        // Not "nothing posted": the bar chart is empty when the posts in range simply
        // collected no interactions, and the Posts card above may well read 1.
        topChart.setEmptyText(getString(R.string.st_insights_no_activity));
        renderTopContent(window);

        /* Likes against comments. */
        addSection(R.string.st_insights_split);
        splitChart = addChart(InsightsChartView.Mode.DONUT);
        splitChart.setEmptyText(getString(R.string.st_insights_no_activity));
        long totalInteractions = likes + comments;
        splitChart.setDonut(new float[]{likes, comments},
                new int[]{accent, withAlpha(accent, 150)},
                format(totalInteractions),
                getString(R.string.st_insights_interactions));
        addLegend(new String[]{getString(R.string.st_total_likes),
                getString(R.string.st_total_comments)}, new int[]{accent, withAlpha(accent, 150)});

        addParagraph(R.string.st_insights_note);
    }

    /** Newest-first window; stops as soon as it walks past the range boundary. */
    private List<PostDto> postsWithinRange() {
        List<PostDto> window = new ArrayList<>();
        long cutoff = System.currentTimeMillis() - RANGE_DAYS[rangeIndex] * 24L * 60L * 60L * 1000L;
        for (PostDto dto : posts) {
            long created = TimeUtils.parseTimestamp(dto.getCreatedAt());
            if (created <= 0L) {
                // An unparseable date still belongs to the account, so keep it rather
                // than silently dropping a real post from every total.
                window.add(dto);
                continue;
            }
            if (created >= cutoff) window.add(dto);
        }
        return window;
    }

    private long engagementOf(PostDto dto) {
        return dto.getLikesCount() + (long) dto.getCommentsCount();
    }

    /**
     * Buckets interactions into one point per day.
     *
     * <p>x labels are generated separately by {@link #labelsForDays()} because a 90
     * day range has far more columns than the chart has room to label.
     */
    private float[] dailyInteractions(List<PostDto> window) {
        int days = RANGE_DAYS[rangeIndex];
        long[] buckets = new long[days];
        long now = System.currentTimeMillis();
        long dayMs = 24L * 60L * 60L * 1000L;

        for (PostDto dto : window) {
            long created = TimeUtils.parseTimestamp(dto.getCreatedAt());
            if (created <= 0L) continue;
            long ageDays = (now - created) / dayMs;
            int slot = days - 1 - (int) Math.min(ageDays, days - 1);
            if (slot >= 0 && slot < days) buckets[slot] += engagementOf(dto);
        }

        float[] values = new float[days];
        for (int i = 0; i < days; i++) values[i] = buckets[i];
        return values;
    }

    private String[] labelsForDays() {
        int days = RANGE_DAYS[rangeIndex];
        SimpleDateFormat format = new SimpleDateFormat("d MMM", Locale.US);
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -(days - 1));
        String[] labels = new String[days];
        for (int i = 0; i < days; i++) {
            labels[i] = format.format(new Date(calendar.getTimeInMillis()));
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }
        return labels;
    }

    /** Last ten pieces by date, oldest first so the bars read left to right. */
    private void renderTopContent(List<PostDto> window) {
        int take = Math.min(10, window.size());
        if (take == 0) {
            topChart.setValues(new float[0], new String[0]);
            return;
        }
        List<PostDto> slice = new ArrayList<>(window.subList(0, take));
        java.util.Collections.reverse(slice);

        float[] values = new float[slice.size()];
        String[] labels = new String[slice.size()];
        SimpleDateFormat format = new SimpleDateFormat("d MMM", Locale.US);
        for (int i = 0; i < slice.size(); i++) {
            PostDto dto = slice.get(i);
            values[i] = engagementOf(dto);
            long created = TimeUtils.parseTimestamp(dto.getCreatedAt());
            String when = created > 0L ? format.format(new Date(created)) : "";
            labels[i] = dto.isReel() ? "Reel" + (when.isEmpty() ? "" : " " + when)
                    : (when.isEmpty() ? "Post" : when);
        }
        topChart.setValues(values, labels);
    }

    /* ------------------------------------------------------------------ */
    /* Helpers                                                            */
    /* ------------------------------------------------------------------ */

    private int withAlpha(int color, int alpha) {
        return android.graphics.Color.argb(alpha,
                android.graphics.Color.red(color),
                android.graphics.Color.green(color),
                android.graphics.Color.blue(color));
    }

    private String format(long value) {
        if (value >= 1_000_000L) {
            return String.format(Locale.US, "%.1fM", value / 1_000_000d);
        }
        if (value >= 1_000L) {
            return String.format(Locale.US, "%.1fK", value / 1_000d);
        }
        return String.valueOf(value);
    }
}
