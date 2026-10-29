package com.insta.nova.ui.settings;

import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.appcompat.app.AlertDialog;

import com.insta.nova.R;
import com.insta.nova.data.local.AppSettingsStore;

import java.util.List;

/** Hidden words: the offensive-comment switch plus a persisted custom word list. */
public class HiddenWordsActivity extends SettingsPageActivity {

    private AppSettingsStore settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = new AppSettingsStore(this);
        setPageTitle(R.string.st_hidden_words_title);
        render();
    }

    private void render() {
        content.removeAllViews();

        addSwitch(R.string.st_hide_offensive, R.string.st_hide_offensive_desc,
                settings.hideOffensiveComments(), (b, c) -> settings.setHideOffensiveComments(c));

        addSection(R.string.st_custom_words);
        addParagraph(R.string.st_custom_words_desc);

        List<String> words = settings.hiddenWords();
        if (words.isEmpty()) {
            addParagraph(R.string.st_no_hidden_words);
        } else {
            for (String word : words) {
                addRow(word, getString(R.string.st_remove), 0, v -> {
                    settings.removeHiddenWord(word);
                    render();
                });
            }
        }

        addButton(R.string.st_add_word, R.color.color_primary, v -> showAddDialog());
    }

    private void showAddDialog() {
        int pad = dp(20);
        FrameLayout container = new FrameLayout(this);
        container.setPadding(pad, dp(8), pad, 0);
        EditText input = new EditText(this);
        input.setHint(R.string.st_word_hint);
        input.setSingleLine(true);
        container.addView(input, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle(R.string.st_add_word)
                .setView(container)
                .setNegativeButton(R.string.st_cancel, null)
                .setPositiveButton(R.string.st_add, (d, w) -> {
                    String word = input.getText().toString();
                    if (!word.trim().isEmpty()) {
                        settings.addHiddenWord(word);
                        render();
                    }
                })
                .show();
    }
}
