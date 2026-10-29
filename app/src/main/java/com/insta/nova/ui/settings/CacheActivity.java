package com.insta.nova.ui.settings;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.insta.nova.R;

import java.io.File;

/** Cache management with a computed size and a real clear. */
public class CacheActivity extends SettingsPageActivity {

    private TextView sizeValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setPageTitle(R.string.st_cache_title);

        addSection(R.string.st_cache_size);
        sizeValue = addParagraph(getString(R.string.st_calculating));
        addButton(R.string.st_clear_cache, R.color.color_destructive, v -> clearCache());
        addParagraph("Clearing the cache removes temporary images and files. Your posts, messages and settings are not affected.");
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshSize();
    }

    private void refreshSize() {
        new Thread(() -> {
            long bytes = sizeOf(getCacheDir()) + sizeOf(getExternalCacheDir());
            String label = android.text.format.Formatter.formatShortFileSize(this, bytes);
            runOnUiThread(() -> {
                if (sizeValue != null) sizeValue.setText(label);
            });
        }).start();
    }

    private void clearCache() {
        new Thread(() -> {
            deleteChildren(getCacheDir());
            deleteChildren(getExternalCacheDir());
            Glide.get(getApplicationContext()).clearMemory();
            try {
                Glide.get(getApplicationContext()).clearDiskCache();
            } catch (Exception ignored) {
            }
            long bytes = sizeOf(getCacheDir()) + sizeOf(getExternalCacheDir());
            String label = android.text.format.Formatter.formatShortFileSize(this, bytes);
            runOnUiThread(() -> {
                if (sizeValue != null) sizeValue.setText(label);
                Toast.makeText(this, R.string.st_cache_cleared, Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    private long sizeOf(File file) {
        if (file == null || !file.exists()) return 0L;
        if (file.isFile()) return file.length();
        long total = 0L;
        File[] children = file.listFiles();
        if (children == null) return 0L;
        for (File child : children) total += sizeOf(child);
        return total;
    }

    private void deleteChildren(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                deleteChildren(child);
                //noinspection ResultOfMethodCallIgnored
                child.delete();
            } else {
                //noinspection ResultOfMethodCallIgnored
                child.delete();
            }
        }
    }
}
