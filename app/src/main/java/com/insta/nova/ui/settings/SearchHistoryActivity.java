package com.insta.nova.ui.settings;

import android.os.Bundle;

import com.insta.nova.R;
import com.insta.nova.data.local.RecentSearchStore;

import java.util.List;

/** The Search screen's real recents, with per-item remove and Clear all. */
public class SearchHistoryActivity extends SettingsPageActivity {

    private RecentSearchStore store;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new RecentSearchStore(this);
        setPageTitle(R.string.st_search_history_title);
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        content.removeAllViews();
        List<String> recent = store.getRecentSearches();
        if (recent == null) recent = java.util.Collections.emptyList();

        if (recent.isEmpty()) {
            addParagraph(R.string.st_search_history_empty);
            return;
        }
        for (String query : recent) {
            if (query == null) continue;
            addRow(query, "✕", R.drawable.ic_search, v -> {
                store.removeSearch(query);
                render();
            });
        }
        addButton(R.string.st_clear_all, R.color.color_destructive, v -> {
            store.clearAll();
            render();
        });
    }
}
