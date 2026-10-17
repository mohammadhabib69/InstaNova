package com.insta.nova.ui.create;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.bumptech.glide.Glide;
import com.insta.nova.R;
import com.insta.nova.data.local.LocalSocialStore;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.model.Post;
import com.insta.nova.databinding.ActivityNewPostBinding;
import com.insta.nova.util.ToastUtil;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Simple Instagram-style "New Post" flow: pick one image from the gallery, add a
 * caption, share. The image is copied into app storage so the content:// grant
 * cannot expire, then a local {@link Post} is persisted by {@link LocalSocialStore}.
 */
public class NewPostActivity extends AppCompatActivity {

    /** Pre-rendered card from the template editor (file:// URI); skips the picker. */
    public static final String EXTRA_MEDIA_URI = "EXTRA_MEDIA_URI";
    /** Caption to prefill when the media came from a template. */
    public static final String EXTRA_CAPTION = "EXTRA_CAPTION";

    private ActivityNewPostBinding binding;
    private LocalSocialStore store;
    private String mediaUri;

    private final ActivityResultLauncher<Intent> pickImage =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                Uri uri = result.getResultCode() == RESULT_OK && result.getData() != null
                        ? result.getData().getData() : null;
                if (uri == null) {
                    finish();
                    return;
                }
                importImage(uri);
            });

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    launchPicker();
                } else {
                    Toast.makeText(this, R.string.new_post_permission_needed, Toast.LENGTH_SHORT).show();
                    finish();
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNewPostBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        store = new LocalSocialStore(this);

        binding.btnClose.setOnClickListener(v -> finish());
        binding.btnShare.setOnClickListener(v -> share());

        if (savedInstanceState == null) {
            // Template cards arrive already rendered, so the gallery picker is
            // skipped and only the caption is prefilled.
            String provided = getIntent().getStringExtra(EXTRA_MEDIA_URI);
            String caption = getIntent().getStringExtra(EXTRA_CAPTION);
            if (provided != null && !provided.isEmpty()) {
                mediaUri = provided;
                if (caption != null) binding.etCaption.setText(caption);
                showPreview();
            } else {
                ensurePermissionThenPick();
            }
        } else {
            mediaUri = savedInstanceState.getString("media_uri");
            if (mediaUri == null) {
                finish();
            } else {
                showPreview();
            }
        }
    }

    private void ensurePermissionThenPick() {
        String permission = Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            launchPicker();
        } else {
            permissionLauncher.launch(permission);
        }
    }

    private void launchPicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        try {
            pickImage.launch(intent);
        } catch (Exception e) {
            Toast.makeText(this, R.string.new_post_pick_failed, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void importImage(@NonNull Uri uri) {
        try {
            File dir = new File(getFilesDir(), "posts");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("no dir");
            File target = new File(dir, "post_" + System.currentTimeMillis() + ".jpg");
            try (InputStream in = getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(target)) {
                if (in == null) throw new IllegalStateException("no stream");
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
            mediaUri = Uri.fromFile(target).toString();
            showPreview();
        } catch (Exception e) {
            Toast.makeText(this, R.string.new_post_pick_failed, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void showPreview() {
        binding.btnShare.setEnabled(true);
        Glide.with(this).load(mediaUri).into(binding.ivPreview);
    }

    private void share() {
        if (mediaUri == null) {
            finish();
            return;
        }
        SessionManager session = SessionManager.getInstance(this);
        String userId = session.getUserId();
        String username = session.getUsername();
        if (username == null || username.trim().isEmpty()) username = "you";

        ArrayList<String> media = new ArrayList<>();
        media.add(mediaUri);

        Post post = new Post(
                "local_" + UUID.randomUUID(),
                userId == null ? "local" : userId,
                username,
                session.getAvatarUrl(),
                false,
                binding.etCaption.getText().toString().trim(),
                "",
                media,
                "image",
                0,
                0,
                false,
                false,
                System.currentTimeMillis());

        store.addPost(post);
        ToastUtil.showPill(this, getString(R.string.post_shared));
        setResult(RESULT_OK);
        finish();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("media_uri", mediaUri);
    }
}
