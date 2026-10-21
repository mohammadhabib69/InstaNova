package com.insta.nova.ui.chat;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.insta.nova.R;
import com.insta.nova.data.local.ChatSettingsStore;
import com.insta.nova.databinding.ActivityChatDetailsBinding;
import com.insta.nova.util.GlideUtils;
import com.insta.nova.util.ToastUtil;

public class ChatDetailsActivity extends AppCompatActivity {

    public static final String EXTRA_CONVERSATION_ID = "EXTRA_CONVERSATION_ID";
    public static final String EXTRA_OTHER_USER_ID = "EXTRA_OTHER_USER_ID";
    public static final String EXTRA_OTHER_USERNAME = "EXTRA_OTHER_USERNAME";
    public static final String EXTRA_OTHER_AVATAR = "EXTRA_OTHER_AVATAR";

    public static final String RESULT_OPEN_SEARCH = "RESULT_OPEN_SEARCH";
    public static final String RESULT_THEME_CHANGED = "RESULT_THEME_CHANGED";
    public static final String RESULT_USER_BLOCKED = "RESULT_USER_BLOCKED";

    private ActivityChatDetailsBinding binding;
    private String conversationId;
    private String otherUserId;
    private String otherUsername;
    private String otherAvatar;
    private ChatSettingsStore settingsStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatDetailsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.detailsRoot, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(binding.detailsRoot);

        conversationId = getIntent().getStringExtra(EXTRA_CONVERSATION_ID);
        otherUserId = getIntent().getStringExtra(EXTRA_OTHER_USER_ID);
        otherUsername = getIntent().getStringExtra(EXTRA_OTHER_USERNAME);
        otherAvatar = getIntent().getStringExtra(EXTRA_OTHER_AVATAR);
        settingsStore = ChatSettingsStore.getInstance(this);

        binding.btnBack.setOnClickListener(v -> finish());

        // Header User info
        String display = otherUsername != null ? otherUsername : "User";
        binding.tvFullName.setText(display);
        binding.tvUsername.setText("@" + display);
        binding.tvBlockTitle.setText("Block @" + display);

        if (otherAvatar != null && !otherAvatar.isEmpty()) {
            GlideUtils.loadAvatar(binding.ivAvatar, otherAvatar);
        } else {
            binding.ivAvatar.setImageResource(R.drawable.ic_person_placeholder);
        }

        binding.btnViewProfile.setOnClickListener(v -> openUserProfile());
        binding.ivAvatar.setOnClickListener(v -> openUserProfile());

        // Theme Row
        updateThemeLabel();
        binding.rowTheme.setOnClickListener(v -> showThemePicker());

        // Mute Row
        boolean isMuted = settingsStore.isMuted(conversationId);
        binding.switchMute.setChecked(isMuted);
        binding.switchMute.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settingsStore.setMuted(conversationId, isChecked);
            ToastUtil.showPill(this, isChecked ? "Messages muted" : "Messages unmuted");
        });
        binding.rowMute.setOnClickListener(v -> binding.switchMute.toggle());

        // Search in conversation
        binding.rowSearch.setOnClickListener(v -> {
            Intent result = new Intent();
            result.putExtra(RESULT_OPEN_SEARCH, true);
            setResult(RESULT_OK, result);
            finish();
        });

        // Block Row
        binding.rowBlock.setOnClickListener(v -> confirmBlock());

        // Report Row
        binding.rowReport.setOnClickListener(v -> showReportDialog());
    }

    private void updateThemeLabel() {
        String themeId = settingsStore.getConversationTheme(conversationId);
        ChatTheme current = ChatTheme.findById(themeId);
        binding.tvThemeSubtitle.setText(current.name);
    }

    private void openUserProfile() {
        if (otherUserId == null || otherUserId.isEmpty()) {
            ToastUtil.showNotAvailable(this);
            return;
        }
        Intent intent = new Intent(this, UserProfileActivity.class);
        intent.putExtra(UserProfileActivity.EXTRA_USER_ID, otherUserId);
        intent.putExtra(UserProfileActivity.EXTRA_USERNAME, otherUsername);
        startActivity(intent);
    }

    private void showThemePicker() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.InstaNovaBottomSheetDialog);
        View sheet = getLayoutInflater().inflate(R.layout.sheet_chat_theme_picker, null);
        RecyclerView rvThemes = sheet.findViewById(R.id.rvThemes);
        rvThemes.setLayoutManager(new LinearLayoutManager(this));

        String currentId = settingsStore.getConversationTheme(conversationId);
        ThemeAdapter adapter = new ThemeAdapter(ChatTheme.getThemes(), currentId, theme -> {
            settingsStore.setConversationTheme(conversationId, theme.id);
            updateThemeLabel();
            Intent result = new Intent();
            result.putExtra(RESULT_THEME_CHANGED, theme.id);
            setResult(RESULT_OK, result);
            dialog.dismiss();
            ToastUtil.showPill(this, "Theme set to " + theme.name);
        });
        rvThemes.setAdapter(adapter);

        dialog.setContentView(sheet);
        dialog.show();
    }

    private void confirmBlock() {
        String name = otherUsername != null ? otherUsername : "this user";
        new AlertDialog.Builder(this)
                .setTitle("Block " + name + "?")
                .setMessage("They won't be able to message you or find your profile, posts, or story on " + com.insta.nova.util.Constants.APP_NAME + ". They won't know you blocked them.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Block", (d, w) -> {
                    settingsStore.setBlocked(otherUserId, true);
                    ToastUtil.showPill(this, "Blocked " + name);
                    Intent result = new Intent();
                    result.putExtra(RESULT_USER_BLOCKED, true);
                    setResult(RESULT_OK, result);
                    finish();
                })
                .show();
    }

    private void showReportDialog() {
        String[] reasons = {
                "It's spam",
                "I just don't like it",
                "Hate speech or symbols",
                "Harassment or bullying",
                "False information",
                "Scam or fraud"
        };
        new AlertDialog.Builder(this)
                .setTitle("Why are you reporting this account?")
                .setItems(reasons, (d, which) -> {
                    ToastUtil.showPill(this, "Thanks for reporting. We use these reports to keep " + com.insta.nova.util.Constants.APP_NAME + " safe.");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
