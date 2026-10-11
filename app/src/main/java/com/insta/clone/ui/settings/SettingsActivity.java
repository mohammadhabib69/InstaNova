package com.insta.clone.ui.settings;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import com.insta.clone.BuildConfig;
import com.insta.clone.data.local.SessionManager;
import com.insta.clone.data.remote.RetrofitClient;
import com.insta.clone.data.remote.api.AuthApi;
import com.insta.clone.databinding.ActivitySettingsBinding;
import com.insta.clone.ui.auth.AuthActivity;
import java.util.HashMap;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Settings Activity providing:
 * Theme (Light / Dark / System), Saved, Archive, Change password, Help, About, Diagnostics, Log out.
 */
public class SettingsActivity extends AppCompatActivity {

    public static final String PREF_THEME = "app_theme_mode";
    public static final int THEME_SYSTEM = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    public static final int THEME_LIGHT = AppCompatDelegate.MODE_NIGHT_NO;
    public static final int THEME_DARK = AppCompatDelegate.MODE_NIGHT_YES;

    private ActivitySettingsBinding binding;
    private SessionManager sessionManager;
    private AuthApi authApi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = SessionManager.getInstance(this);
        authApi = RetrofitClient.getInstance(this).getAuthApi();

        binding.btnBackSettings.setOnClickListener(v -> finish());

        updateCurrentThemeLabel();

        binding.rowTheme.setOnClickListener(v -> showThemeDialog());
        binding.rowSaved.setOnClickListener(v -> {
            Toast.makeText(this, "Opening Saved Posts", Toast.LENGTH_SHORT).show();
            // Saved posts are also accessible on the Saved tab in bottom navigation
            finish();
        });
        binding.rowArchive.setOnClickListener(v -> showArchiveDialog());
        binding.rowChangePassword.setOnClickListener(v -> showChangePasswordDialog());
        binding.rowHelp.setOnClickListener(v -> showHelpDialog());
        binding.rowAbout.setOnClickListener(v -> showAboutDialog());
        binding.rowDiagnostics.setOnClickListener(v -> {
            startActivity(new Intent(this, SyncTestActivity.class));
        });
        binding.rowLogout.setOnClickListener(v -> showLogoutDialog());
    }

    private void updateCurrentThemeLabel() {
        SharedPreferences sp = getSharedPreferences("insta_settings", Context.MODE_PRIVATE);
        int mode = sp.getInt(PREF_THEME, THEME_SYSTEM);
        if (mode == THEME_LIGHT) {
            binding.tvCurrentTheme.setText("Light");
        } else if (mode == THEME_DARK) {
            binding.tvCurrentTheme.setText("Dark");
        } else {
            binding.tvCurrentTheme.setText("System");
        }
    }

    private void showThemeDialog() {
        String[] options = {"System default", "Light", "Dark"};
        SharedPreferences sp = getSharedPreferences("insta_settings", Context.MODE_PRIVATE);
        int current = sp.getInt(PREF_THEME, THEME_SYSTEM);
        int checkedItem = (current == THEME_LIGHT) ? 1 : (current == THEME_DARK ? 2 : 0);

        new AlertDialog.Builder(this)
                .setTitle("Select Theme")
                .setSingleChoiceItems(options, checkedItem, (dialog, which) -> {
                    int selectedMode;
                    if (which == 1) selectedMode = THEME_LIGHT;
                    else if (which == 2) selectedMode = THEME_DARK;
                    else selectedMode = THEME_SYSTEM;

                    sp.edit().putInt(PREF_THEME, selectedMode).apply();
                    AppCompatDelegate.setDefaultNightMode(selectedMode);
                    updateCurrentThemeLabel();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showArchiveDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Story Archive")
                .setMessage("Your expired stories (older than 24 hours) are preserved safely in your database archive.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void showChangePasswordDialog() {
        final EditText input = new EditText(this);
        input.setHint("Enter new password (min 6 characters)");
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setPadding(48, 32, 48, 32);

        new AlertDialog.Builder(this)
                .setTitle("Change Password")
                .setView(input)
                .setPositiveButton("Update", (dialog, which) -> {
                    String newPass = input.getText().toString().trim();
                    if (newPass.length() < 6) {
                        Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Map<String, Object> body = new HashMap<>();
                    body.put("password", newPass);
                    authApi.updateUser(body).enqueue(new Callback<Void>() {
                        @Override
                        public void onResponse(Call<Void> call, Response<Void> response) {
                            if (response.isSuccessful()) {
                                Toast.makeText(SettingsActivity.this, "Password updated successfully!", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(SettingsActivity.this, "Password update failed (" + response.code() + ")", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onFailure(Call<Void> call, Throwable t) {
                            Toast.makeText(SettingsActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showHelpDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Help & Support")
                .setMessage("InstaNova is powered by Supabase REST API & Storage.\n\nNeed assistance? Visit the repository or reach out via settings diagnostics.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void showAboutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("About InstaNova")
                .setMessage("InstaNova v" + BuildConfig.VERSION_NAME + "\nBuilt with Java + Android SDK + Supabase backend.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void showLogoutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Log Out")
                .setMessage("Are you sure you want to log out of @" + sessionManager.getUsername() + "?")
                .setPositiveButton("Log out", (dialog, which) -> {
                    sessionManager.clearSession();
                    Intent intent = new Intent(this, AuthActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
