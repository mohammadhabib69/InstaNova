package com.insta.nova.ui.settings;

import android.os.Bundle;
import android.widget.Toast;

import com.insta.nova.R;
import com.insta.nova.data.local.AppSettingsStore;
import com.insta.nova.ui.chat.ChatTheme;

import java.util.List;

/** Default chat theme, using the same theme set as the in-chat picker. */
public class ChatThemeAppearanceActivity extends SettingsPageActivity {

    private AppSettingsStore settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = new AppSettingsStore(this);
        setPageTitle(R.string.st_chat_theme_title);
        render();
    }

    private void render() {
        content.removeAllViews();
        addParagraph(R.string.st_chat_theme_note);
        addSection(R.string.st_default_chat_theme);

        String current = settings.defaultChatTheme();
        List<ChatTheme> themes = ChatTheme.getThemes();
        for (ChatTheme theme : themes) {
            boolean selected = theme.id.equalsIgnoreCase(current);
            addRow(theme.name, selected ? "✓" : null, R.drawable.ic_palette, v -> {
                settings.setDefaultChatTheme(theme.id);
                Toast.makeText(this, R.string.st_theme_saved, Toast.LENGTH_SHORT).show();
                render();
            });
        }
    }
}
