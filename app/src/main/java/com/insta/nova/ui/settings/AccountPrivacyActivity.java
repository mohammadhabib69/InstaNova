package com.insta.nova.ui.settings;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.insta.nova.R;
import com.insta.nova.data.repository.SettingsRepository;
import com.insta.nova.databinding.ActivityAccountPrivacyBinding;
import com.insta.nova.util.Resource;
import com.insta.nova.util.ToastUtil;

/**
 * PAGE 14 — Account privacy.
 *
 * The switch mirrors profiles.is_private and drives the set_account_privacy RPC
 * with an optimistic update plus rollback. Turning privacy off asks for
 * confirmation first because it approves every pending follow request.
 */
public class AccountPrivacyActivity extends AppCompatActivity {

    private ActivityAccountPrivacyBinding binding;
    private SettingsRepository repository;
    private boolean serverValue = false;
    private boolean updating = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAccountPrivacyBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = new SettingsRepository(this);
        binding.btnBack.setOnClickListener(v -> finish());

        applyLearnMore();
        styleSwitch();

        binding.switchPrivate.setOnClickListener(v -> onSwitchTapped());

        repository.isPrivate().observe(this, resource -> {
            if (resource == null || resource.status != Resource.Status.SUCCESS || resource.data == null) return;
            serverValue = resource.data;
            binding.switchPrivate.setChecked(serverValue);
            styleSwitch();
        });
    }

    private void styleSwitch() {
        boolean on = binding.switchPrivate.isChecked();
        int track = on
                ? ContextCompat.getColor(this, R.color.color_switch_track_on)
                : ContextCompat.getColor(this, R.color.color_switch_track_off);
        int knob = resolveBackgroundColor();
        binding.switchPrivate.setTrackTintList(android.content.res.ColorStateList.valueOf(track));
        binding.switchPrivate.setThumbTintList(android.content.res.ColorStateList.valueOf(knob));
    }

    private int resolveBackgroundColor() {
        android.util.TypedValue value = new android.util.TypedValue();
        if (getTheme().resolveAttribute(R.attr.themeBackground, value, true)) {
            return value.data;
        }
        return ContextCompat.getColor(this, R.color.white);
    }

    private void onSwitchTapped() {
        if (updating) {
            binding.switchPrivate.setChecked(serverValue);
            return;
        }
        final boolean target = !binding.switchPrivate.isChecked();
        if (target) {
            applyPrivacy(true);
        } else {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.privacy_switch_public_title)
                    .setMessage(R.string.privacy_switch_public_body)
                    .setPositiveButton(R.string.privacy_switch_public_confirm, (d, w) -> applyPrivacy(false))
                    .setNegativeButton(R.string.settings_cancel, (d, w) -> {
                        binding.switchPrivate.setChecked(true);
                        styleSwitch();
                    })
                    .show();
        }
    }

    /** Optimistic update: flip immediately, roll back and toast if the RPC fails. */
    private void applyPrivacy(final boolean isPrivate) {
        updating = true;
        binding.switchPrivate.setChecked(isPrivate);
        styleSwitch();

        repository.setPrivate(isPrivate).observe(this, resource -> {
            if (resource == null) return;
            if (resource.status == Resource.Status.SUCCESS) {
                updating = false;
                serverValue = isPrivate;
                android.util.Log.d("InstaApi", "set_account_privacy p_private=" + isPrivate + " -> ok");
                setResult(RESULT_OK);
            } else if (resource.status == Resource.Status.ERROR) {
                updating = false;
                serverValue = !isPrivate;
                binding.switchPrivate.setChecked(serverValue);
                styleSwitch();
                String message = resource.message == null
                        ? getString(R.string.privacy_update_failed) : resource.message;
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                android.util.Log.e("InstaApi", "set_account_privacy failed: " + message);
            }
        });
    }

    /** "Learn more" is design-only and follows the decorative contract. */
    private void applyLearnMore() {
        TextView paragraph = binding.tvPrivateParagraph;
        String body = paragraph.getText().toString();
        String link = getString(R.string.privacy_learn_more);
        SpannableString span = new SpannableString(body + " " + link);
        int start = span.length() - link.length();
        span.setSpan(new StyleSpan(Typeface.NORMAL), 0, body.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        span.setSpan(new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                if (com.insta.nova.util.Constants.DECORATIVE_UI) {
                    ToastUtil.showNotAvailable(AccountPrivacyActivity.this);
                }
            }
        }, start, span.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        paragraph.setMovementMethod(LinkMovementMethod.getInstance());
        paragraph.setText(span);
    }
}
