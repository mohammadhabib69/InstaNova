package com.insta.nova.insights;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.insta.nova.R;
import com.insta.nova.data.NovaDtos;
import com.insta.nova.data.NovaRepository;
import com.insta.nova.databinding.ActivityNovaInsightsBinding;
import com.insta.nova.hub.NovaScreen;
import com.insta.nova.util.Resource;
import com.insta.nova.widget.NovaChartView;
import com.insta.nova.widget.NovaUi;

import java.util.List;

/**
 * Growth dashboard. Every number here comes from {@code nova_my_insights}; when the
 * period has no activity the screen says so instead of drawing an empty chart.
 */
public class NovaInsightsActivity extends AppCompatActivity {

    private static final String[] METRICS = {"likes", "comments", "followers", "posts"};

    private ActivityNovaInsightsBinding binding;
    private NovaRepository repository;
    private int days = 7;
    private String metric = "likes";
    private NovaDtos.Insights data;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNovaInsightsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        NovaScreen.bindHeader(this, R.drawable.ic_nova_insights,
                R.string.nova_insights_title, R.color.nova_insights);

        repository = new NovaRepository(this);
        binding.chart.setMode(NovaChartView.Mode.LINE);
        binding.chart.setAccent(getColor(R.color.nova_insights));
        binding.chart.setEmptyText(getString(R.string.nova_insights_no_data));
        binding.btnBack.setOnClickListener(v -> finish());

        binding.btn7.setOnClickListener(v -> selectDays(7));
        binding.btn14.setOnClickListener(v -> selectDays(14));
        binding.btn30.setOnClickListener(v -> selectDays(30));

        buildMetricChips();
        renderPeriod();
        load();
    }

    private void selectDays(int value) {
        if (value == days) return;
        days = value;
        renderPeriod();
        load();
    }

    private void renderPeriod() {
        style(binding.btn7, days == 7);
        style(binding.btn14, days == 14);
        style(binding.btn30, days == 30);
    }

    private void style(TextView chip, boolean active) {
        chip.setBackgroundResource(active ? R.drawable.bg_nova_chip_on : R.drawable.bg_nova_chip);
        chip.setTextColor(getColor(active ? R.color.color_background : R.color.nova_text_3));
    }

    private void buildMetricChips() {
        binding.metricRow.removeAllViews();
        for (String key : METRICS) {
            boolean active = key.equals(metric);
            TextView chip = new TextView(this);
            chip.setText(labelFor(key));
            chip.setTextSize(11f);
            chip.setSingleLine(true);
            chip.setGravity(Gravity.CENTER);
            chip.setMinHeight(NovaUi.dp(this, 34));
            chip.setBackgroundResource(active
                    ? R.drawable.bg_nova_chip_on : R.drawable.bg_nova_chip);
            chip.setTextColor(getColor(active ? R.color.color_background : R.color.nova_text_3));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            params.setMarginEnd(NovaUi.dp(this, 6));
            chip.setLayoutParams(params);
            chip.setOnClickListener(v -> {
                if (key.equals(metric)) return;
                metric = key;
                buildMetricChips();
                if (data != null) renderChart();
            });
            binding.metricRow.addView(chip);
        }
    }

    private String labelFor(String key) {
        switch (key) {
            case "likes": return getString(R.string.nova_insights_likes);
            case "comments": return getString(R.string.nova_insights_comments);
            case "followers": return getString(R.string.nova_insights_followers);
            default: return getString(R.string.nova_insights_posts);
        }
    }

    private void load() {
        binding.progress.setVisibility(View.VISIBLE);
        binding.tvError.setVisibility(View.GONE);
        repository.insights(days).observe(this, resource -> {
            if (binding == null || resource == null) return;
            if (resource.status == Resource.Status.LOADING) return;
            binding.progress.setVisibility(View.GONE);
            if (resource.status == Resource.Status.SUCCESS && resource.data != null) {
                data = resource.data;
                renderChart();
                renderTotals();
                renderTopPost();
                renderBestHour();
            } else if (resource.status == Resource.Status.ERROR) {
                binding.tvError.setVisibility(View.VISIBLE);
                binding.tvError.setText(getString(R.string.nova_error)
                        + (resource.message == null ? "" : " (" + resource.message + ")"));
            }
        });
    }

    private void renderChart() {
        List<NovaDtos.InsightDay> rows = data.days;
        if (rows == null || rows.isEmpty()) {
            binding.chart.setData(new float[0], new String[0]);
            binding.tvBigValue.setText("0");
            binding.tvBigLabel.setText(labelFor(metric));
            return;
        }
        float[] values = new float[rows.size()];
        String[] labels = new String[rows.size()];
        int total = 0;
        for (int i = 0; i < rows.size(); i++) {
            NovaDtos.InsightDay day = rows.get(i);
            values[i] = valueOf(day, metric);
            labels[i] = shortDay(day.day);
            total += (int) values[i];
        }
        binding.chart.setData(values, labels);
        binding.tvBigValue.setText(NovaUi.number(total));
        binding.tvBigLabel.setText(labelFor(metric) + " · " + getString(R.string.nova_insights_days, days));
    }

    private static float valueOf(NovaDtos.InsightDay day, String key) {
        switch (key) {
            case "comments": return day.comments;
            case "followers": return day.followers;
            case "posts": return day.posts;
            default: return day.likes;
        }
    }

    /** "2026-10-23" -> "10-23"; the chart only has room for a short label. */
    private static String shortDay(String iso) {
        if (iso == null) return "";
        return iso.length() >= 10 ? iso.substring(5, 10) : iso;
    }

    private void renderTotals() {
        NovaDtos.InsightTotals totals = data.totals;
        if (totals == null) {
            binding.tvTotalLikes.setText("");
            binding.tvTotalComments.setText("");
            binding.tvTotalFollowers.setText("");
            binding.tvTotalPosts.setText("");
            return;
        }
        binding.tvTotalLikes.setText(getString(R.string.nova_insights_row,
                getString(R.string.nova_insights_likes), NovaUi.number(totals.likes)));
        binding.tvTotalComments.setText(getString(R.string.nova_insights_row,
                getString(R.string.nova_insights_comments), NovaUi.number(totals.comments)));
        binding.tvTotalFollowers.setText(getString(R.string.nova_insights_row,
                getString(R.string.nova_insights_new_followers), NovaUi.number(totals.newFollowers)));
        binding.tvTotalPosts.setText(getString(R.string.nova_insights_row,
                getString(R.string.nova_insights_posts), NovaUi.number(totals.posts)));
    }

    private void renderTopPost() {
        NovaDtos.TopPost top = data.topPost;
        if (top == null || top.id == null) {
            binding.topPostCard.setVisibility(View.GONE);
            return;
        }
        binding.topPostCard.setVisibility(View.VISIBLE);
        String caption = top.caption == null || top.caption.trim().isEmpty()
                ? getString(R.string.nova_insights_no_caption) : top.caption.trim();
        binding.tvTopCaption.setText(caption);
        binding.tvTopMeta.setText(getString(R.string.nova_insights_row,
                getString(R.string.nova_insights_likes), NovaUi.number(top.likes))
                + " · " + getString(R.string.nova_insights_row,
                getString(R.string.nova_insights_comments), NovaUi.number(top.comments)));
    }

    private void renderBestHour() {
        // Hour 0 is also the backend default when it has no signal, so do not claim
        // "midnight" on no evidence.
        if (data.bestHour <= 0) {
            binding.tvBestHour.setText(R.string.nova_insights_no_time);
            return;
        }
        binding.tvBestHour.setText(getString(R.string.nova_insights_best_time_body,
                NovaUi.formatHour(this, data.bestHour)));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
