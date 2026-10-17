package com.insta.nova.ui.create;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.insta.nova.R;
import com.insta.nova.data.local.LocalSocialStore;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.model.Story;
import com.insta.nova.ui.story.LocalStoryViewerActivity;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Story capture: gallery picker (image or video), local persistence, then the
 * story viewer opens on the freshly added item. No intermediate editor, matching
 * Instagram's quick "add to story" path.
 */
public class NewStoryActivity extends AppCompatActivity {

    /** Pre-rendered card from the template editor (file:// image); skips the picker. */
    public static final String EXTRA_MEDIA_URI = "EXTRA_MEDIA_URI";

    private LocalSocialStore store;

    private final ActivityResultLauncher<Intent> pickMedia =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                Uri uri = result.getResultCode() == RESULT_OK && result.getData() != null
                        ? result.getData().getData() : null;
                if (uri == null) {
                    finish();
                    return;
                }
                saveStory(uri);
            });

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean granted = false;
                for (Boolean value : result.values()) {
                    if (Boolean.TRUE.equals(value)) granted = true;
                }
                if (granted) {
                    launchPicker();
                } else {
                    Toast.makeText(this, R.string.story_permission_needed, Toast.LENGTH_SHORT).show();
                    finish();
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new LocalSocialStore(this);
        String provided = savedInstanceState == null
                ? getIntent().getStringExtra(EXTRA_MEDIA_URI) : null;
        if (provided != null && !provided.isEmpty()) {
            // A rendered template card is already a local file, so it is imported
            // straight into the story store without a picker round-trip.
            saveStory(Uri.parse(provided));
        } else {
            // Recreated while the picker was open (e.g. rotation): reopen it.
            ensurePermissionsThenPick();
        }
    }

    private void ensurePermissionsThenPick() {
        List<String> needed = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.READ_MEDIA_VIDEO);
            }
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        if (needed.isEmpty()) {
            launchPicker();
        } else {
            permissionLauncher.launch(needed.toArray(new String[0]));
        }
    }

    private void launchPicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
        try {
            pickMedia.launch(Intent.createChooser(intent, getString(R.string.story_pick_title)));
        } catch (Exception e) {
            Toast.makeText(this, R.string.story_pick_failed, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void saveStory(@NonNull Uri uri) {
        String type = getContentResolver().getType(uri);
        boolean video = type != null && type.startsWith("video");
        String extension = video ? ".mp4" : ".jpg";
        try {
            File dir = new File(getFilesDir(), "stories");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("no dir");
            File target = new File(dir, "story_" + System.currentTimeMillis() + extension);
            try (InputStream in = getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(target)) {
                if (in == null) throw new IllegalStateException("no stream");
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            }

            SessionManager session = SessionManager.getInstance(this);
            String userId = session.getUserId();
            Story story = new Story(
                    "local_" + UUID.randomUUID(),
                    userId == null ? "local" : userId,
                    session.getUsername(),
                    session.getAvatarUrl(),
                    Uri.fromFile(target).toString(),
                    video ? "video" : "image",
                    false,
                    System.currentTimeMillis());
            store.addStory(story);
            store.pruneExpiredStories();

            Intent viewer = new Intent(this, LocalStoryViewerActivity.class);
            startActivity(viewer);
            finish();
        } catch (Exception e) {
            Toast.makeText(this, R.string.story_pick_failed, Toast.LENGTH_SHORT).show();
            finish();
        }
    }
}
