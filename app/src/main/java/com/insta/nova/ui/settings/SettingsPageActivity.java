package com.insta.nova.ui.settings;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.insta.nova.R;

/**
 * Prompt N — shared Settings sub-page shell.
 *
 * <p>Every sub-page uses the same 56dp bar and row metrics as Settings itself, so
 * the hierarchy reads like one product. Rows are real XML: each helper inflates
 * {@code item_settings_*}, it never draws a fake control.
 */
public abstract class SettingsPageActivity extends AppCompatActivity {

    protected LinearLayout content;
    private ProgressBar progress;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings_page);
        content = findViewById(R.id.content);
        progress = findViewById(R.id.progressPage);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    protected void setPageTitle(@StringRes int titleRes) {
        ((TextView) findViewById(R.id.tvPageTitle)).setText(titleRes);
    }

    protected void setPageTitle(String title) {
        ((TextView) findViewById(R.id.tvPageTitle)).setText(title);
    }

    protected void showLoading(boolean loading) {
        if (progress != null) progress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    /* ------------------------------------------------------------------ */
    /* Rows                                                                */
    /* ------------------------------------------------------------------ */

    protected void addSection(@StringRes int titleRes) {
        TextView header = (TextView) LayoutInflater.from(this)
                .inflate(R.layout.item_settings_header, content, false);
        header.setText(titleRes);
        content.addView(header);
    }

    protected void addSection(String title) {
        TextView header = (TextView) LayoutInflater.from(this)
                .inflate(R.layout.item_settings_header, content, false);
        header.setText(title);
        content.addView(header);
    }

    protected View addRow(@StringRes int labelRes, @Nullable String value,
                          @DrawableRes int iconRes, @Nullable View.OnClickListener click) {
        return addRow(getString(labelRes), value, iconRes, click);
    }

    /** Standard navigation row: icon, label, optional value, chevron. */
    protected View addRow(String label, @Nullable String value,
                          @DrawableRes int iconRes, @Nullable View.OnClickListener click) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_settings_row, content, false);
        TextView tvLabel = row.findViewById(R.id.tv_label);
        TextView tvValue = row.findViewById(R.id.tv_value);
        ImageView icon = row.findViewById(R.id.iv_icon);
        ImageView chevron = row.findViewById(R.id.iv_chevron);

        tvLabel.setText(label);
        if (iconRes != 0) {
            icon.setImageResource(iconRes);
            icon.setVisibility(View.VISIBLE);
        } else {
            icon.setVisibility(View.GONE);
        }
        if (value != null && !value.isEmpty()) {
            tvValue.setText(value);
            tvValue.setVisibility(View.VISIBLE);
        } else {
            tvValue.setVisibility(View.GONE);
        }
        chevron.setVisibility(click == null ? View.GONE : View.VISIBLE);
        row.setClickable(click != null);
        if (click != null) row.setOnClickListener(click);
        content.addView(row);
        return row;
    }

    /** Read-only label/value pair (account information, session facts). */
    protected View addValueRow(String label, String value) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_settings_row, content, false);
        TextView tvLabel = row.findViewById(R.id.tv_label);
        TextView tvValue = row.findViewById(R.id.tv_value);
        row.findViewById(R.id.iv_icon).setVisibility(View.GONE);
        row.findViewById(R.id.iv_chevron).setVisibility(View.GONE);
        tvLabel.setText(label);
        tvValue.setText(value == null || value.isEmpty() ? "—" : value);
        tvValue.setVisibility(View.VISIBLE);
        content.addView(row);
        return row;
    }

    protected SwitchMaterial addSwitch(@StringRes int labelRes, @StringRes int descRes,
                                       boolean checked, SwitchMaterial.OnCheckedChangeListener listener) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_settings_toggle, content, false);
        TextView label = row.findViewById(R.id.tvToggleLabel);
        TextView desc = row.findViewById(R.id.tvToggleDesc);
        SwitchMaterial toggle = row.findViewById(R.id.switchToggle);

        label.setText(labelRes);
        if (descRes != 0) {
            desc.setText(descRes);
            desc.setVisibility(View.VISIBLE);
        }
        toggle.setChecked(checked);
        styleSwitch(toggle, checked);
        toggle.setOnCheckedChangeListener((button, isChecked) -> {
            styleSwitch(toggle, isChecked);
            if (listener != null) listener.onCheckedChanged(button, isChecked);
        });
        // The whole row is the touch target, like Instagram.
        row.setOnClickListener(v -> toggle.setChecked(!toggle.isChecked()));
        content.addView(row);
        return toggle;
    }

    protected SwitchMaterial addSwitch(String label, String description,
                                       boolean checked, SwitchMaterial.OnCheckedChangeListener listener) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_settings_toggle, content, false);
        TextView labelView = row.findViewById(R.id.tvToggleLabel);
        TextView desc = row.findViewById(R.id.tvToggleDesc);
        SwitchMaterial toggle = row.findViewById(R.id.switchToggle);

        labelView.setText(label);
        if (description != null && !description.isEmpty()) {
            desc.setText(description);
            desc.setVisibility(View.VISIBLE);
        }
        toggle.setChecked(checked);
        styleSwitch(toggle, checked);
        toggle.setOnCheckedChangeListener((button, isChecked) -> {
            styleSwitch(toggle, isChecked);
            if (listener != null) listener.onCheckedChanged(button, isChecked);
        });
        row.setOnClickListener(v -> toggle.setChecked(!toggle.isChecked()));
        content.addView(row);
        return toggle;
    }

    /** Radio group with an optional grey title above it. */
    protected RadioGroup addRadioGroup(@Nullable String title, String[] labels,
                                       int checkedIndex, RadioGroup.OnCheckedChangeListener listener) {
        if (title != null && !title.isEmpty()) {
            TextView header = (TextView) LayoutInflater.from(this)
                    .inflate(R.layout.item_settings_header, content, false);
            header.setText(title);
            content.addView(header);
        }
        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.VERTICAL);
        group.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        for (int i = 0; i < labels.length; i++) {
            RadioButton radio = (RadioButton) LayoutInflater.from(this)
                    .inflate(R.layout.item_settings_radio, group, false);
            radio.setText(labels[i]);
            radio.setId(View.generateViewId());
            radio.setChecked(i == checkedIndex);
            group.addView(radio);
        }
        group.setOnCheckedChangeListener(listener);
        content.addView(group);
        return group;
    }

    protected TextView addParagraph(@StringRes int textRes) {
        TextView view = (TextView) LayoutInflater.from(this)
                .inflate(R.layout.item_settings_paragraph, content, false);
        view.setText(textRes);
        content.addView(view);
        return view;
    }

    protected TextView addParagraph(String text) {
        TextView view = (TextView) LayoutInflater.from(this)
                .inflate(R.layout.item_settings_paragraph, content, false);
        view.setText(text);
        content.addView(view);
        return view;
    }

    protected TextView addButton(@StringRes int labelRes, int colorRes, View.OnClickListener click) {
        TextView button = (TextView) LayoutInflater.from(this)
                .inflate(R.layout.item_settings_button, content, false);
        button.setText(labelRes);
        button.setTextColor(ContextCompat.getColor(this, colorRes));
        button.setOnClickListener(click);
        content.addView(button);
        return button;
    }

    /** Labelled input used by the password and report forms. */
    protected android.widget.EditText addInput(@StringRes int hintRes, int inputType) {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint(hintRes);
        input.setInputType(inputType);
        input.setTextSize(16);
        input.setSingleLine(true);
        input.setBackgroundResource(R.drawable.bg_search_field);
        input.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                resolveThemeColor(R.attr.themeSurfaceVariant, R.color.color_surface_variant)));
        input.setTextColor(resolveThemeColor(R.attr.themeTextPrimary, R.color.color_text_primary));
        input.setHintTextColor(resolveThemeColor(R.attr.themeTextSecondary, R.color.color_text_secondary));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        lp.setMargins(dp(16), dp(6), dp(16), dp(6));
        input.setLayoutParams(lp);
        input.setPadding(dp(12), 0, dp(12), 0);
        content.addView(input);
        return input;
    }

    protected android.widget.EditText addMultilineInput(@StringRes int hintRes, int lines) {
        android.widget.EditText input = addInput(hintRes, android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setSingleLine(false);
        input.setMinLines(lines);
        input.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) input.getLayoutParams();
        lp.height = dp(48 + (lines - 1) * 20);
        input.setLayoutParams(lp);
        return input;
    }

    /** Solid primary action button (blue, 48dp, 8dp radius). */
    protected TextView addPrimaryButton(@StringRes int labelRes, View.OnClickListener click) {
        TextView button = new TextView(this);
        button.setText(labelRes);
        button.setTextColor(ContextCompat.getColor(this, R.color.white));
        button.setTextSize(15);
        button.setTypeface(button.getTypeface(), android.graphics.Typeface.BOLD);
        button.setGravity(android.view.Gravity.CENTER);
        button.setBackgroundResource(R.drawable.bg_button_primary);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        lp.setMargins(dp(16), dp(12), dp(16), dp(4));
        button.setLayoutParams(lp);
        button.setOnClickListener(click);
        content.addView(button);
        return button;
    }

    /** Two-column stat cards (value, label). */
    protected android.widget.GridLayout addStatGrid(java.util.List<String[]> stats) {
        android.widget.GridLayout grid = new android.widget.GridLayout(this);
        grid.setColumnCount(2);
        grid.setRowCount((stats.size() + 1) / 2);
        int gap = dp(8);
        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gridLp.setMargins(dp(12), dp(8), dp(12), dp(8));
        grid.setLayoutParams(gridLp);

        for (int i = 0; i < stats.size(); i++) {
            View card = LayoutInflater.from(this).inflate(R.layout.item_stat_card, grid, false);
            ((TextView) card.findViewById(R.id.tvStatValue)).setText(stats.get(i)[0]);
            ((TextView) card.findViewById(R.id.tvStatLabel)).setText(stats.get(i)[1]);
            android.widget.GridLayout.LayoutParams lp =
                    new android.widget.GridLayout.LayoutParams(
                            android.widget.GridLayout.spec(i / 2),
                            android.widget.GridLayout.spec(i % 2, 1f));
            lp.width = 0;
            lp.height = dp(92);
            lp.setMargins(gap, gap, gap, gap);
            card.setLayoutParams(lp);
            grid.addView(card);
        }
        content.addView(grid);
        return grid;
    }

    /* ------------------------------------------------------------------ */
    /* Charts                                                              */
    /* ------------------------------------------------------------------ */

    /** Adds a themed chart to the page and hands it back for data. */
    protected com.insta.nova.ui.widget.InsightsChartView addChart(
            com.insta.nova.ui.widget.InsightsChartView.Mode mode) {
        com.insta.nova.ui.widget.InsightsChartView chart =
                new com.insta.nova.ui.widget.InsightsChartView(this);
        chart.setMode(mode);
        chart.setAccent(resolveThemeColor(R.attr.themePrimary, R.color.color_primary));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(12), dp(6), dp(12), dp(10));
        chart.setLayoutParams(lp);
        content.addView(chart);
        return chart;
    }

    public interface ChipListener {
        void onChip(int index);
    }

    /**
     * The date-range pills above a chart. Selected reads as a filled pill, the rest
     * as outlined ones, which is the pattern the Instagram insights screens use for
     * their 7 / 30 / 90 day switch.
     */
    protected LinearLayout addChipRow(String[] labels, int selected, ChipListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(dp(12), dp(10), dp(12), dp(2));
        row.setLayoutParams(rowLp);

        for (int i = 0; i < labels.length; i++) {
            TextView chip = new TextView(this);
            chip.setText(labels[i]);
            chip.setTextSize(12f);
            chip.setGravity(android.view.Gravity.CENTER);
            chip.setPadding(dp(14), dp(7), dp(14), dp(7));
            boolean on = i == selected;
            chip.setBackgroundResource(on
                    ? R.drawable.bg_button_primary : R.drawable.bg_button_secondary);
            chip.setTextColor(on
                    ? ContextCompat.getColor(this, R.color.white)
                    : resolveThemeColor(R.attr.themeTextPrimary, R.color.color_text_primary));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.setMarginStart(dp(8));
            chip.setLayoutParams(lp);
            final int index = i;
            chip.setOnClickListener(v -> {
                if (listener != null) listener.onChip(index);
            });
            row.addView(chip);
        }
        content.addView(row);
        return row;
    }

    /** Small colour key under a ring, so a two-colour chart is never a guess. */
    protected void addLegend(String[] labels, int[] colors) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(dp(12), 0, dp(12), dp(8));
        row.setLayoutParams(rowLp);

        for (int i = 0; i < labels.length; i++) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(android.view.Gravity.CENTER_VERTICAL);

            View dot = new View(this);
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(9), dp(9));
            dotLp.setMarginEnd(dp(6));
            dot.setLayoutParams(dotLp);
            dot.setBackgroundColor(i < colors.length
                    ? colors[i] : android.graphics.Color.GRAY);
            item.addView(dot);

            TextView text = new TextView(this);
            text.setText(labels[i]);
            text.setTextSize(11f);
            text.setTextColor(resolveThemeColor(R.attr.themeTextSecondary,
                    R.color.color_text_secondary));
            item.addView(text);

            LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) itemLp.setMarginStart(dp(18));
            item.setLayoutParams(itemLp);
            row.addView(item);
        }
        content.addView(row);
    }

    protected void addDivider() {
        View divider = new View(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.5f)));
        divider.setLayoutParams(lp);
        divider.setBackgroundColor(resolveThemeColor(R.attr.themeDivider, R.color.color_divider));
        content.addView(divider);
    }

    protected void addSpacer(int heightDp) {
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp)));
        content.addView(spacer);
    }

    /* ------------------------------------------------------------------ */
    /* Theme helpers                                                       */
    /* ------------------------------------------------------------------ */

    protected void styleSwitch(SwitchMaterial toggle, boolean on) {
        toggle.setTrackTintList(ColorStateList.valueOf(ContextCompat.getColor(this,
                on ? R.color.color_switch_track_on : R.color.color_switch_track_off)));
        toggle.setThumbTintList(ColorStateList.valueOf(on
                ? ContextCompat.getColor(this, R.color.white)
                : resolveThemeColor(R.attr.themeBackground, R.color.white)));
    }

    protected int resolveThemeColor(int attr, int fallbackRes) {
        TypedValue value = new TypedValue();
        if (getTheme().resolveAttribute(attr, value, true)) return value.data;
        return ContextCompat.getColor(this, fallbackRes);
    }

    protected int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
