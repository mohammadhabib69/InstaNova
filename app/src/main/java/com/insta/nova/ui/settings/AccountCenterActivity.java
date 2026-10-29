package com.insta.nova.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;

import com.insta.nova.R;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.repository.SettingsRepository;
import com.insta.nova.util.GlideUtils;
import com.insta.nova.util.Resource;

/**
 * Prompt N — Accounts Centre ("Velkyn Account").
 *
 * <p>Header card with the real avatar/name/username, then the account rows. Every
 * row opens a real page; privacy shows its live value.
 */
public class AccountCenterActivity extends AppCompatActivity {

    private SettingsRepository repository;
    private LinearLayout content;
    private TextView tvPrivacyValue;
    private boolean isPrivate;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_accounts_centre);

        repository = new SettingsRepository(this);
        content = findViewById(R.id.content);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        bindHeader();
        buildRows();
        loadPrivacy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindHeader();
        loadPrivacy();
    }

    private void bindHeader() {
        ImageView avatar = findViewById(R.id.ivAvatar);
        TextView name = findViewById(R.id.tvName);
        TextView username = findViewById(R.id.tvUsername);

        SessionManager session = SessionManager.getInstance(this);
        username.setText("@" + session.getUsername());
        GlideUtils.loadAvatar(avatar, session.getAvatarUrl());
        name.setText(session.getUsername());

        repository.getMyProfileRow().observe(this, resource -> {
            if (resource == null || resource.status != Resource.Status.SUCCESS || resource.data == null) return;
            Object fullName = resource.data.get("full_name");
            Object user = resource.data.get("username");
            Object avatarUrl = resource.data.get("avatar_url");
            if (fullName != null && !String.valueOf(fullName).trim().isEmpty()) {
                name.setText(String.valueOf(fullName));
            }
            if (user != null) username.setText("@" + user);
            if (avatarUrl != null) GlideUtils.loadAvatar(avatar, String.valueOf(avatarUrl));
        });
    }

    private void buildRows() {
        content.removeAllViews();
        addSection(R.string.st_sec_account);
        addRow(R.string.st_edit_profile, R.drawable.ic_edit,
                v -> startActivity(new Intent(this, EditProfileActivity.class)));
        addRow(R.string.st_account_information, R.drawable.ic_person_circle,
                v -> startActivity(new Intent(this, AccountInfoActivity.class)));
        addRow(R.string.st_change_password, R.drawable.ic_lock,
                v -> startActivity(new Intent(this, ChangePasswordActivity.class)));
        addRow(R.string.st_login_activity, R.drawable.ic_activity,
                v -> startActivity(new Intent(this, SessionsActivity.class)));
        View privacy = addRow(R.string.st_account_privacy, R.drawable.ic_lock,
                v -> startActivity(new Intent(this, AccountPrivacyActivity.class)));
        tvPrivacyValue = privacy.findViewById(R.id.tv_value);
        tvPrivacyValue.setVisibility(View.VISIBLE);
        tvPrivacyValue.setText(R.string.st_private);
        addRow(R.string.st_deactivate_delete, R.drawable.ic_error,
                v -> startActivity(new Intent(this, DeactivateDeleteActivity.class)));
    }

    private void loadPrivacy() {
        repository.isPrivate().observe(this, resource -> {
            if (resource == null || resource.status != Resource.Status.SUCCESS || resource.data == null) return;
            isPrivate = resource.data;
            if (tvPrivacyValue != null) {
                tvPrivacyValue.setText(isPrivate ? R.string.st_private : R.string.st_public);
            }
        });
    }

    private void addSection(@StringRes int titleRes) {
        TextView header = (TextView) LayoutInflater.from(this)
                .inflate(R.layout.item_settings_header, content, false);
        header.setText(titleRes);
        content.addView(header);
    }

    private View addRow(@StringRes int labelRes, @DrawableRes int iconRes, View.OnClickListener click) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_settings_row, content, false);
        ((TextView) row.findViewById(R.id.tv_label)).setText(labelRes);
        ImageView icon = row.findViewById(R.id.iv_icon);
        icon.setImageResource(iconRes);
        icon.setVisibility(View.VISIBLE);
        row.findViewById(R.id.iv_chevron).setVisibility(View.VISIBLE);
        row.setOnClickListener(click);
        content.addView(row);
        return row;
    }
}
