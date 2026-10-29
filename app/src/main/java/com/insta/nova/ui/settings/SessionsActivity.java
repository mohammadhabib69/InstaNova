package com.insta.nova.ui.settings;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;

import com.google.firebase.auth.FirebaseAuth;
import com.insta.nova.R;
import com.insta.nova.data.local.AppSettingsStore;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.repository.ChatRepository;
import com.insta.nova.ui.auth.AuthActivity;

import java.text.DateFormat;
import java.util.Date;

/**
 * Login activity — only the real current session. Nothing is invented: the device
 * name comes from Build, and the start time is recorded the first time this screen
 * is opened for the account.
 */
public class SessionsActivity extends SettingsPageActivity {

    private static final String KEY_SESSION_STARTED = "session_started_at";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setPageTitle(R.string.st_login_activity_title);

        AppSettingsStore settings = new AppSettingsStore(this);
        long started = 0L;
        try {
            started = Long.parseLong(settings.getString(KEY_SESSION_STARTED, "0"));
        } catch (NumberFormatException ignored) {
        }
        if (started <= 0L) {
            started = System.currentTimeMillis();
            settings.setString(KEY_SESSION_STARTED, String.valueOf(started));
        }

        String device = (Build.MANUFACTURER == null ? "" : Build.MANUFACTURER)
                + " " + (Build.MODEL == null ? "" : Build.MODEL);
        device = device.trim();
        if (device.isEmpty()) device = "Android device";

        addSection(R.string.st_current_session);
        addValueRow(getString(R.string.st_device), device);
        addValueRow(getString(R.string.st_signed_in_since),
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(new Date(started)));
        addParagraph(getString(R.string.st_this_device));

        addButton(R.string.st_log_out_device, R.color.color_destructive, v -> confirmLogout());
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.st_log_out_device)
                .setMessage(R.string.st_log_out_confirm)
                .setNegativeButton(R.string.st_cancel, null)
                .setPositiveButton(R.string.st_log_out, (d, w) -> {
                    FirebaseAuth.getInstance().signOut();
                    SessionManager.getInstance(this).clearSession();
                    ChatRepository.getInstance(getApplicationContext()).resetForAccountSwitch();
                    Intent intent = new Intent(this, AuthActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .show();
    }
}
