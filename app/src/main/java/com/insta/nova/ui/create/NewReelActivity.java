package com.insta.nova.ui.create;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
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
import com.insta.nova.data.model.Post;
import com.insta.nova.databinding.ActivityNewReelBinding;
import com.insta.nova.util.ToastUtil;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Reel composer: video-only gallery pick, looping preview with tap play/pause,
 * caption, then a local reel record that the Reels pager merges in.
 */
public class NewReelActivity extends AppCompatActivity {

    private ActivityNewReelBinding binding;
    private LocalSocialStore store;
    private String videoUri;
    private boolean playing;

    private final ActivityResultLauncher<Intent> pickVideo =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                Uri uri = result.getResultCode() == RESULT_OK && result.getData() != null
                        ? result.getData().getData() : null;
                if (uri == null) {
                    finish();
                    return;
                }
                importVideo(uri);
            });

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    launchPicker();
                } else {
                    Toast.makeText(this, R.string.reel_permission_needed, Toast.LENGTH_SHORT).show();
                    finish();
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNewReelBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        store = new LocalSocialStore(this);

        binding.btnClose.setOnClickListener(v -> finish());
        binding.btnShare.setOnClickListener(v -> share());
        binding.layoutVideo.setOnClickListener(v -> togglePlayback());

        if (savedInstanceState == null) {
            ensurePermissionThenPick();
        } else {
            videoUri = savedInstanceState.getString("video_uri");
            if (videoUri == null) {
                ensurePermissionThenPick();
            } else {
                showPreview();
            }
        }
    }

    private void ensurePermissionThenPick() {
        String permission = Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_VIDEO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            launchPicker();
        } else {
            permissionLauncher.launch(permission);
        }
    }

    private void launchPicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
        intent.setType("video/*");
        try {
            pickVideo.launch(intent);
        } catch (Exception e) {
            Toast.makeText(this, R.string.reel_pick_failed, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void importVideo(@NonNull Uri uri) {
        try {
            File dir = new File(getFilesDir(), "reels");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("no dir");
            File target = new File(dir, "reel_" + System.currentTimeMillis() + ".mp4");
            try (InputStream in = getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(target)) {
                if (in == null) throw new IllegalStateException("no stream");
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            }
            videoUri = Uri.fromFile(target).toString();
            showPreview();
        } catch (Exception e) {
            Toast.makeText(this, R.string.reel_pick_failed, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void showPreview() {
        try {
            binding.videoView.setVideoURI(Uri.parse(videoUri));
            binding.videoView.setOnPreparedListener(mp -> {
                mp.setLooping(true);
                binding.videoView.start();
                playing = true;
                updatePlayIcon();
            });
            binding.videoView.setOnCompletionListener(mp -> {
                playing = false;
                updatePlayIcon();
            });
        } catch (Exception e) {
            Toast.makeText(this, R.string.reel_pick_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void togglePlayback() {
        if (videoUri == null) return;
        try {
            if (binding.videoView.isPlaying()) {
                binding.videoView.pause();
                playing = false;
            } else {
                binding.videoView.start();
                playing = true;
            }
            updatePlayIcon();
        } catch (Exception ignored) {
        }
    }

    private void updatePlayIcon() {
        binding.ivPlayPause.setVisibility(playing ? View.GONE : View.VISIBLE);
        binding.ivPlayPause.setContentDescription(getString(
                playing ? R.string.reel_pause : R.string.reel_play));
    }

    private void share() {
        if (videoUri == null) {
            finish();
            return;
        }
        SessionManager session = SessionManager.getInstance(this);
        String username = session.getUsername();
        if (username == null || username.trim().isEmpty()) username = "you";

        ArrayList<String> media = new ArrayList<>();
        media.add(videoUri);

        Post reel = new Post(
                "local_reel_" + UUID.randomUUID(),
                session.getUserId() == null ? "local" : session.getUserId(),
                username,
                session.getAvatarUrl(),
                false,
                binding.etCaption.getText().toString().trim(),
                "",
                media,
                "reel",
                0,
                0,
                false,
                false,
                System.currentTimeMillis());

        store.addReel(reel);
        publishReel(Uri.parse(videoUri), reel.getCaption());
        setResult(RESULT_OK);
    }

    /**
     * Makes the reel real.
     *
     * <p>The composer used to stop at {@code store.addReel()} — a device-local record plus a
     * "reel shared" toast — so no other account could ever see it and a reinstall lost it. The
     * clip is now uploaded to Storage and a {@code posts} row with {@code is_reel} is created,
     * the same shape a photo post uses.
     */
    private void publishReel(@NonNull Uri uri, @Nullable String caption) {
        String userId = SessionManager.getInstance(this).getUserId();
        if (userId == null || userId.isEmpty()) {
            ToastUtil.showPill(this, getString(R.string.reel_publish_failed));
            finish();
            return;
        }
        final byte[] data;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IllegalStateException("no stream");
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            data = out.toByteArray();
        } catch (Exception e) {
            ToastUtil.showPill(this, getString(R.string.reel_publish_failed));
            finish();
            return;
        }
        if (data.length == 0) {
            ToastUtil.showPill(this, getString(R.string.reel_publish_failed));
            finish();
            return;
        }

        final String path = userId + "/" + UUID.randomUUID() + ".mp4";
        okhttp3.RequestBody body = okhttp3.RequestBody.create(
                okhttp3.MediaType.parse("video/mp4"), data);
        com.insta.nova.data.remote.RetrofitClient client =
                com.insta.nova.data.remote.RetrofitClient.getInstance(this);
        client.getStorageApi().uploadFile("posts", path, body).enqueue(new retrofit2.Callback<Void>() {
            @Override public void onResponse(retrofit2.Call<Void> c, retrofit2.Response<Void> r) {
                if (!r.isSuccessful()) {
                    ToastUtil.showPill(NewReelActivity.this, getString(R.string.reel_publish_failed));
                    finish();
                    return;
                }
                String base = com.insta.nova.BuildConfig.SUPABASE_URL == null
                        ? "" : com.insta.nova.BuildConfig.SUPABASE_URL.trim();
                while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
                final String url = base + "/storage/v1/object/public/posts/" + path;

                java.util.Map<String, Object> row = new java.util.HashMap<>();
                row.put("user_id", userId);
                row.put("caption", caption == null ? "" : caption);
                row.put("is_reel", true);
                row.put("video_url", url);
                // The grid needs something to paint before the first frame decodes.
                row.put("thumbnail_url", url);
                client.getRestApi().createPost(row).enqueue(
                        new retrofit2.Callback<java.util.List<com.insta.nova.data.remote.dto.PostDto>>() {
                    @Override public void onResponse(
                            retrofit2.Call<java.util.List<com.insta.nova.data.remote.dto.PostDto>> c,
                            retrofit2.Response<java.util.List<com.insta.nova.data.remote.dto.PostDto>> r) {
                        ToastUtil.showPill(NewReelActivity.this, getString(
                                r.isSuccessful() ? R.string.reel_shared : R.string.reel_publish_failed));
                        finish();
                    }

                    @Override public void onFailure(
                            retrofit2.Call<java.util.List<com.insta.nova.data.remote.dto.PostDto>> c,
                            Throwable t) {
                        ToastUtil.showPill(NewReelActivity.this, getString(R.string.reel_publish_failed));
                        finish();
                    }
                });
            }

            @Override public void onFailure(retrofit2.Call<Void> c, Throwable t) {
                ToastUtil.showPill(NewReelActivity.this, getString(R.string.reel_publish_failed));
                finish();
            }
        });
    }

    @Override
    protected void onPause() {
        try {
            binding.videoView.pause();
            playing = false;
            updatePlayIcon();
        } catch (Exception ignored) {
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        try {
            binding.videoView.stopPlayback();
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("video_uri", videoUri);
    }
}
