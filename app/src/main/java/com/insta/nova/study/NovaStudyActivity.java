package com.insta.nova.study;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.insta.nova.R;
import com.insta.nova.data.NovaDtos;
import com.insta.nova.data.StudyRepository;
import com.insta.nova.databinding.ActivityNovaStudyBinding;
import com.insta.nova.hub.NovaScreen;
import com.insta.nova.util.Resource;
import com.insta.nova.util.TimeUtils;
import com.insta.nova.widget.NovaUi;

import java.util.List;

/**
 * Focus timer, streak and leaderboard. A finished session is written through the same
 * {@code save_session} call the streak card reads back, so the numbers can never drift.
 */
public class NovaStudyActivity extends AppCompatActivity {

    private static final int[] MINUTES = {25, 45, 60};
    private static final String[] SUBJECTS = {"general", "math", "physics", "ict"};

    private ActivityNovaStudyBinding binding;
    private StudyRepository repository;
    private String subject = "general";
    private int duration = 25;
    private CountDownTimer timer;
    private long startedAt;
    private String scope = "week";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNovaStudyBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        NovaScreen.bindHeader(this, R.drawable.ic_nova_study,
                R.string.nova_study_title, R.color.nova_study);

        repository = new StudyRepository(this);
        binding.btnStart.setOnClickListener(v -> start());
        binding.btnQa.setOnClickListener(v ->
                startActivity(new android.content.Intent(this, NovaQaActivity.class)));
        // Both tabs are backed by nova_study_leaderboard('week'|'all'), which ranks the
        // people you follow plus you — see
        // supabase/migrations/2026-10-23_nova_study_leaderboard_fix.sql.

        binding.btnWeek.setOnClickListener(v -> {
            scope = "week";
            renderScope();
            loadLeaderboard();
        });
        binding.btnAll.setOnClickListener(v -> {
            scope = "all";
            renderScope();
            loadLeaderboard();
        });

        buildSubjectChips();
        buildDurationChips();
        renderScope();
        binding.ring.setProgress(0, false);
        loadStats();
        loadLeaderboard();
    }

    private void buildSubjectChips() {
        binding.subjectRow.removeAllViews();
        for (String candidate : SUBJECTS) {
            boolean active = candidate.equals(subject);
            TextView chip = new TextView(this);
            chip.setText(labelFor(candidate));
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
                subject = candidate;
                buildSubjectChips();
            });
            binding.subjectRow.addView(chip);
        }
    }

    private String labelFor(String subject) {
        switch (subject) {
            case "general": return getString(R.string.nova_study_subject_general);
            case "math": return getString(R.string.nova_study_subject_math);
            case "physics": return getString(R.string.nova_study_subject_physics);
            case "ict": return getString(R.string.nova_study_subject_ict);
            default: return subject;
        }
    }

    private void buildDurationChips() {
        binding.durationRow.removeAllViews();
        int[] choices = new int[MINUTES.length + 1];
        System.arraycopy(MINUTES, 0, choices, 0, MINUTES.length);
        choices[MINUTES.length] = -1;
        for (int minutes : choices) {
            boolean custom = minutes < 0;
            boolean active = custom
                    ? duration != MINUTES[0] && duration != MINUTES[1] && duration != MINUTES[2]
                    : minutes == duration;
            TextView chip = new TextView(this);
            chip.setText(custom ? (active
                    ? getString(R.string.nova_study_minutes, duration)
                    : getString(R.string.nova_study_custom)) : getString(R.string.nova_study_minutes, minutes));
            chip.setTextSize(12f);
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
                if (custom) askCustomMinutes();
                else {
                    duration = minutes;
                    buildDurationChips();
                }
            });
            binding.durationRow.addView(chip);
        }
    }

    private void askCustomMinutes() {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint(R.string.nova_study_duration);
        input.setText(String.valueOf(duration));
        android.widget.FrameLayout wrap = new android.widget.FrameLayout(this);
        int pad = NovaUi.dp(this, 20);
        wrap.setPadding(pad, NovaUi.dp(this, 8), pad, 0);
        wrap.addView(input);
        new AlertDialog.Builder(this)
                .setTitle(R.string.nova_study_custom)
                .setView(wrap)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    int value;
                    try {
                        value = Integer.parseInt(input.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        value = 0;
                    }
                    if (value < 1) value = 1;
                    if (value > 180) value = 180;
                    duration = value;
                    buildDurationChips();
                })
                .show();
    }

    private void renderScope() {
        boolean week = "week".equals(scope);
        binding.btnWeek.setBackgroundResource(week
                ? R.drawable.bg_nova_chip_on : R.drawable.bg_nova_chip);
        binding.btnWeek.setTextColor(getColor(week ? R.color.color_background : R.color.nova_text_3));
        binding.btnAll.setBackgroundResource(week
                ? R.drawable.bg_nova_chip : R.drawable.bg_nova_chip_on);
        binding.btnAll.setTextColor(getColor(week ? R.color.nova_text_3 : R.color.nova_text));
    }

    private void loadStats() {
        repository.stats().observe(this, resource -> {
            if (binding == null || resource == null) return;
            if (resource.status != Resource.Status.SUCCESS || resource.data == null) return;
            NovaDtos.StudyStats stats = resource.data;
            binding.tvStreak.setText(getString(R.string.nova_study_streak, stats.currentStreak));
            binding.tvStats.setText(getString(R.string.nova_study_today, stats.todayMinutes)
                    + " · " + getString(R.string.nova_study_best, stats.bestStreak));
        });
    }

    private void loadLeaderboard() {
        repository.leaderboard(scope, 20).observe(this, resource -> {
            if (binding == null || resource == null) return;
            if (resource.status != Resource.Status.SUCCESS || resource.data == null) return;
            renderLeaderboard(resource.data);
        });
    }

    private void renderLeaderboard(List<NovaDtos.LeaderRow> rows) {
        binding.leaderList.removeAllViews();
        boolean empty = rows == null || rows.isEmpty();
        binding.tvLeaderEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.leaderList.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) return;
        for (NovaDtos.LeaderRow row : rows) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setPadding(NovaUi.dp(this, 12), NovaUi.dp(this, 10),
                    NovaUi.dp(this, 12), NovaUi.dp(this, 10));
            line.setBackgroundResource(row.isMe ? R.drawable.bg_nova_chip : R.drawable.bg_nova_card);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = NovaUi.dp(this, 6);
            line.setLayoutParams(params);

            TextView rank = new TextView(this);
            rank.setText(String.valueOf(row.rank));
            rank.setTextSize(12f);
            rank.setTextColor(getColor(R.color.nova_text_3));
            rank.setMinWidth(NovaUi.dp(this, 26));
            line.addView(rank);

            TextView name = new TextView(this);
            name.setText("@" + (row.username == null ? "" : row.username));
            name.setTextSize(13f);
            name.setTextColor(getColor(R.color.nova_text));
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            name.setLayoutParams(nameParams);
            line.addView(name);

            TextView minutes = new TextView(this);
            minutes.setText(getString(R.string.nova_study_minutes, row.minutes));
            minutes.setTextSize(12f);
            minutes.setTextColor(getColor(R.color.nova_study));
            line.addView(minutes);

            binding.leaderList.addView(line);
        }
    }

    private void start() {
        startedAt = System.currentTimeMillis();
        binding.ring.setProgress(0, false);
        binding.ring.setMax(duration * 60);
        binding.btnStart.setText(R.string.nova_study_give_up);
        binding.btnStart.setOnClickListener(v -> confirmGiveUp());

        timer = new CountDownTimer(duration * 60_000L, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (binding == null) return;
                int elapsed = (int) ((duration * 60_000L - millisUntilFinished) / 1000L);
                binding.ring.setProgress(elapsed, false);
            }

            @Override
            public void onFinish() {
                if (binding == null) return;
                binding.ring.setProgress(duration * 60, false);
                save(duration);
                reset();
            }
        }.start();
        NovaUi.haptic(binding.getRoot(), true);
    }

    private void confirmGiveUp() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.nova_study_give_up)
                .setMessage(R.string.nova_study_give_up_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.nova_study_give_up, (d, w) -> {
                    int elapsed = (int) ((System.currentTimeMillis() - startedAt) / 60_000L);
                    reset();
                    if (elapsed >= 1) save(elapsed);
                    else android.widget.Toast.makeText(this, R.string.nova_study_too_short,
                            android.widget.Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void reset() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        if (binding == null) return;
        binding.btnStart.setText(R.string.nova_study_start);
        binding.btnStart.setOnClickListener(v -> start());
        binding.ring.setProgress(0, false);
    }

    private void save(int minutes) {
        repository.saveSession(subject, minutes,
                TimeUtils.toSupabaseTimestamp(startedAt)).observe(this, resource -> {
            if (binding == null || resource == null) return;
            if (resource.status == Resource.Status.SUCCESS) {
                NovaUi.haptic(binding.getRoot(), true);
                loadStats();
                loadLeaderboard();
            } else if (resource.status == Resource.Status.ERROR) {
                binding.tvStudyError.setText(getString(R.string.nova_error)
                        + (resource.message == null ? "" : " (" + resource.message + ")"));
                binding.tvStudyError.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) timer.cancel();
        binding = null;
    }
}
