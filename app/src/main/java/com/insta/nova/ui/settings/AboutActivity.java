package com.insta.nova.ui.settings;

import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.insta.nova.BuildConfig;
import com.insta.nova.R;

/** About: InstaNova name, real build version, Velkyn attribution. */
public class AboutActivity extends SettingsPageActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setPageTitle(R.string.st_about_title);

        ImageView mark = new ImageView(this);
        int size = dp(72);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.topMargin = dp(32);
        mark.setLayoutParams(lp);
        mark.setImageResource(R.drawable.ic_velkyn_v);
        mark.setColorFilter(ContextCompat.getColor(this, R.color.color_text_primary));
        content.addView(mark);

        TextView name = new TextView(this);
        name.setText(R.string.app_name);
        name.setTextSize(26);
        name.setTypeface(name.getTypeface(), android.graphics.Typeface.BOLD);
        name.setGravity(Gravity.CENTER);
        name.setTextColor(resolveThemeColor(R.attr.themeTextPrimary, R.color.color_text_primary));
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameLp.topMargin = dp(14);
        name.setLayoutParams(nameLp);
        content.addView(name);

        TextView from = new TextView(this);
        from.setText(R.string.st_from_velkyn);
        from.setTextSize(14);
        from.setGravity(Gravity.CENTER);
        from.setTextColor(resolveThemeColor(R.attr.themeTextSecondary, R.color.color_text_secondary));
        LinearLayout.LayoutParams fromLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fromLp.topMargin = dp(4);
        from.setLayoutParams(fromLp);
        content.addView(from);

        addSpacer(24);
        addValueRow(getString(R.string.st_version), BuildConfig.VERSION_NAME);
        addParagraph("InstaNova is built by Velkyn. Copyright © 2026 Velkyn. All rights reserved.");
    }
}
