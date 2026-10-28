package com.insta.nova.safe;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.widget.CompoundButtonCompat;
import androidx.lifecycle.ViewModelProvider;

import com.insta.nova.R;
import com.insta.nova.data.NovaDtos;
import com.insta.nova.data.NovaSettingsStore;
import com.insta.nova.data.NovaGlide;
import com.insta.nova.databinding.ActivityNovaSafeBinding;
import com.insta.nova.databinding.ItemBlockedUserBinding;
import com.insta.nova.databinding.ItemMutedUserBinding;
import com.insta.nova.databinding.ItemSafeStatBinding;
import com.insta.nova.hub.NovaScreen;
import com.insta.nova.util.Resource;
import com.insta.nova.widget.NovaChartView;
import com.insta.nova.widget.NovaUi;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Nova Safe: kindness score, live stats, settings, block/mute lists and the playground. */
public class NovaSafeActivity extends AppCompatActivity {

    private ActivityNovaSafeBinding binding;
    private NovaSafeViewModel viewModel;
    private NovaSettingsStore settingsStore;
    private NovaModerator.Result playgroundResult;
    private boolean bindingSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().requestFeature(android.view.Window.FEATURE_ACTIVITY_TRANSITIONS);
        binding = ActivityNovaSafeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        NovaScreen.bindHeader(this, R.drawable.ic_nova_safe,
                R.string.nova_module_safe, R.color.nova_safe);
        NovaUi.styleStatusBar(this, NovaUi.isNight(this));

        viewModel = new ViewModelProvider(this).get(NovaSafeViewModel.class);
        settingsStore = NovaSettingsStore.get(this);

        binding.kindnessRing.setAccent(getColor(R.color.nova_safe));
        binding.kindnessRing.setMax(100);
        binding.categoryChart.setAccent(getColor(R.color.nova_safe));
        binding.categoryChart.setMode(NovaChartView.Mode.BAR);

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnSafeRetry.setOnClickListener(v -> viewModel.refresh());
        binding.btnHowItWorks.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(R.string.nova_how_safe)
                .setMessage(R.string.nova_how_safe_body)
                .setPositiveButton(R.string.nova_close, null)
                .show());

        bindSettings();
        setupPlayground();
        observe();
    }

    private void bindSettings() {
        bindingSettings = true;
        NovaDtos.Settings settings = settingsStore.current();
        binding.switchHide.setChecked(settings.hideToxic);
        binding.switchWarn.setChecked(settings.warnBeforePost);
        binding.switchTeen.setChecked(settings.teenSafe);
        tint(binding.switchHide, R.color.nova_safe);
        tint(binding.switchWarn, R.color.nova_safe);
        tint(binding.switchTeen, R.color.nova_safe);
        bindingSettings = false;

        binding.switchHide.setOnCheckedChangeListener((button, checked) -> {
            if (bindingSettings) return;
            settingsStore.setHideToxic(checked);
        });
        binding.switchWarn.setOnCheckedChangeListener((button, checked) -> {
            if (bindingSettings) return;
            settingsStore.setWarnBeforePost(checked);
        });
        binding.switchTeen.setOnCheckedChangeListener((button, checked) -> {
            if (bindingSettings) return;
            settingsStore.setTeenSafe(checked);
            if (checked) {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.nova_toggle_teen)
                        .setMessage(R.string.nova_teen_explain)
                        .setPositiveButton(R.string.nova_done, null)
                        .show();
            }
        });
    }

    private void tint(SwitchCompat view, int colorRes) {
        int accent = getColor(colorRes);
        CompoundButtonCompat.setButtonTintList(view,
                new android.content.res.ColorStateList(
                        new int[][]{{android.R.attr.state_checked}, {}},
                        new int[]{accent, 0xFF3A4266}));
    }

    private void observe() {
        viewModel.stats().observe(this, resource -> {
            binding.safeLoading.setVisibility(resource.status == Resource.Status.LOADING ? View.VISIBLE : View.GONE);
            binding.safeError.setVisibility(resource.status == Resource.Status.ERROR ? View.VISIBLE : View.GONE);
            if (resource.status == Resource.Status.SUCCESS && resource.data != null) {
                renderStats(resource.data);
            }
        });
        viewModel.blocked().observe(this, resource -> {
            if (resource.status == Resource.Status.SUCCESS) renderBlocked(resource.data);
        });
        viewModel.muted().observe(this, resource -> {
            if (resource.status == Resource.Status.SUCCESS) renderMuted(resource.data);
        });
    }

    private void renderStats(NovaDtos.SafetyStats stats) {
        binding.kindnessRing.setProgress(stats.kindnessScore, true);
        binding.tvKindnessCaption.setText(R.string.nova_safe_score_caption);

        binding.statsRowOne.removeAllViews();
        addStat(binding.statsRowOne, stats.warned, R.string.nova_warned, 0);
        addStat(binding.statsRowOne, stats.blocked, R.string.nova_blocked, 1);
        addStat(binding.statsRowOne, stats.edited, R.string.nova_edited, 0);

        binding.statsRowTwo.removeAllViews();
        addStat(binding.statsRowTwo, stats.postedAnyway, R.string.nova_posted_anyway, 0);
        addStat(binding.statsRowTwo, stats.hidden, R.string.nova_hidden, 1);

        String[] order = {"insult", "threat", "harassment", "hate", "sexual", "spam"};
        String[] labels = new String[order.length];
        float[] values = new float[order.length];
        Map<String, Integer> byCategory = stats.byCategory;
        for (int i = 0; i < order.length; i++) {
            labels[i] = shortLabel(order[i]);
            Integer count = byCategory == null ? null : byCategory.get(order[i]);
            values[i] = count == null ? 0 : count;
        }
        binding.categoryChart.setEmptyText(getString(R.string.nova_no_incidents));
        binding.categoryChart.setData(values, labels);
    }

    /** Localised, short axis labels — the raw category keys never reach the screen. */
    private String shortLabel(String category) {
        switch (category) {
            case "insult": return getString(R.string.nova_cat_insult);
            case "threat": return getString(R.string.nova_cat_threat);
            case "harassment": return getString(R.string.nova_cat_harassment);
            case "hate": return getString(R.string.nova_cat_hate);
            case "sexual": return getString(R.string.nova_cat_sexual);
            case "spam": return getString(R.string.nova_cat_spam);
            default: return category;
        }
    }

    private void addStat(LinearLayout row, int value, int label, int index) {
        ItemSafeStatBinding item = ItemSafeStatBinding.inflate(LayoutInflater.from(this), row, false);
        item.statValue.setText(NovaUi.number(value));
        item.statLabel.setText(label);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        if (index > 0) params.setMarginStart(NovaUi.dp(this, 10));
        item.getRoot().setLayoutParams(params);
        row.addView(item.getRoot());
    }

    private void renderBlocked(List<NovaDtos.BlockedUser> users) {
        binding.blockedList.removeAllViews();
        boolean empty = users == null || users.isEmpty();
        binding.tvNoBlocked.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) return;
        for (NovaDtos.BlockedUser user : users) {
            ItemBlockedUserBinding item = ItemBlockedUserBinding.inflate(
                    LayoutInflater.from(this), binding.blockedList, false);
            item.username.setText("@" + (user.username == null ? "user" : user.username));
            NovaGlide.loadAvatar(item.avatar, user.avatarUrl);
            item.btnUnblock.setOnClickListener(v ->
                    SafeActions.unblock(this, user.userId, viewModel::refresh));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.topMargin = NovaUi.dp(this, 8);
            item.getRoot().setLayoutParams(params);
            binding.blockedList.addView(item.getRoot());
        }
    }

    private void renderMuted(List<NovaDtos.MutedUser> users) {
        binding.mutedList.removeAllViews();
        boolean empty = users == null || users.isEmpty();
        binding.tvNoMuted.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) return;
        for (NovaDtos.MutedUser user : users) {
            ItemMutedUserBinding item = ItemMutedUserBinding.inflate(
                    LayoutInflater.from(this), binding.mutedList, false);
            item.username.setText("@" + (user.username == null ? "user" : user.username));
            NovaGlide.loadAvatar(item.avatar, user.avatarUrl);
            styleChip(item.chipPosts, user.mutePosts);
            styleChip(item.chipStories, user.muteStories);
            item.chipPosts.setOnClickListener(v ->
                    viewModel.setMute(user.targetId, !user.mutePosts, user.muteStories));
            item.chipStories.setOnClickListener(v ->
                    viewModel.setMute(user.targetId, user.mutePosts, !user.muteStories));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.topMargin = NovaUi.dp(this, 8);
            item.getRoot().setLayoutParams(params);
            binding.mutedList.addView(item.getRoot());
        }
    }

    private void styleChip(TextView chip, boolean active) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(NovaUi.dpFloat(this, 17));
        if (active) {
            drawable.setColor(NovaUi.withAlpha(getColor(R.color.nova_violet), 0.20f));
            drawable.setStroke(NovaUi.dp(this, 1), getColor(R.color.nova_violet));
            chip.setTextColor(getColor(R.color.nova_text));
        } else {
            drawable.setColor(getColor(R.color.nova_card_high));
            drawable.setStroke(NovaUi.dp(this, 1), getColor(R.color.nova_stroke));
            chip.setTextColor(getColor(R.color.nova_text_3));
        }
        chip.setBackground(drawable);
    }

    /* ------------------------------------------------------------------ */
    /* Playground                                                          */
    /* ------------------------------------------------------------------ */

    private void setupPlayground() {
        binding.etPlayground.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                runGuard(s.toString());
            }
        });
        binding.btnCopyResult.setOnClickListener(v -> {
            if (playgroundResult == null) return;
            String text = String.format(Locale.US, "Nova Safe — category=%s severity=%d score=%d matched=%s",
                    playgroundResult.category == null ? "clean" : playgroundResult.category,
                    playgroundResult.severity, playgroundResult.score,
                    playgroundResult.matchedTerms());
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("Nova Safe", text));
                Toast.makeText(this, R.string.nova_copied, Toast.LENGTH_SHORT).show();
            }
        });
        runGuard("");
    }

    private void runGuard(String text) {
        playgroundResult = NovaGuard.evaluate(this, text);
        if (text.trim().isEmpty()) {
            binding.tvPlaygroundScore.setText(R.string.nova_playground_clean);
            binding.tvPlaygroundPreview.setText("");
            return;
        }
        NovaModerator.Result result = playgroundResult;
        if (result.isClean()) {
            binding.tvPlaygroundScore.setText(R.string.nova_playground_clean);
            binding.tvPlaygroundScore.setTextColor(getColor(R.color.nova_text_2));
            binding.tvPlaygroundPreview.setText(text);
        } else {
            binding.tvPlaygroundScore.setText(getString(R.string.nova_playground_result,
                    result.category, result.severity, result.score));
            binding.tvPlaygroundScore.setTextColor(getColor(R.color.nova_danger));
            binding.tvPlaygroundPreview.setText(NovaModerationDialog.highlight(this, text, result));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.refresh();
        bindSettings();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
