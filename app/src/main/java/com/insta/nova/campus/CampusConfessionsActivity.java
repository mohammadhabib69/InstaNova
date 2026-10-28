package com.insta.nova.campus;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.insta.nova.R;
import com.insta.nova.data.CampusRepository;
import com.insta.nova.data.NovaDtos;
import com.insta.nova.databinding.ActivityCampusConfessionsBinding;
import com.insta.nova.hub.NovaScreen;
import com.insta.nova.safe.NovaModerationDialog;
import com.insta.nova.util.Resource;
import com.insta.nova.util.TimeUtils;
import com.insta.nova.widget.NovaUi;

import java.util.List;

/**
 * Anonymous confessions. Nothing here identifies the author — the client never receives an
 * author id, only whether the row is yours so you can delete it.
 *
 * <p>Posting requires a verified student account. When the server says otherwise the
 * composer is disabled and the reason is shown instead of failing on submit.
 */
public class CampusConfessionsActivity extends AppCompatActivity {

    private ActivityCampusConfessionsBinding binding;
    private CampusRepository repository;
    private boolean verified;
    private boolean loading;

    /** Optional picture for the next confession; uploaded only when it is posted. */
    private android.net.Uri pendingPhoto;
    private android.net.Uri cameraUri;
    /** When set the list is filtered to the signed-in user's own confessions. */
    private boolean mineOnly;
    private androidx.activity.result.ActivityResultLauncher<android.content.Intent> photoLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCampusConfessionsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        NovaScreen.bindHeader(this, R.drawable.ic_nova_campus,
                R.string.nova_campus_confessions, R.color.nova_campus);

        photoLauncher = registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                result -> {
                    // Backing out of the camera leaves a prepared empty file behind, so the
                    // result code decides whether anything was actually picked.
                    android.net.Uri captured = cameraUri;
                    cameraUri = null;
                    if (result.getResultCode() != RESULT_OK) return;
                    android.content.Intent data = result.getData();
                    android.net.Uri picked = data != null && data.getData() != null
                            ? data.getData() : captured;
                    if (picked == null) return;
                    pendingPhoto = picked;
                    paintPhoto();
                });

        repository = new CampusRepository(this);
        binding.tvGate.setText(R.string.nova_campus_verified_note);
        binding.btnPost.setOnClickListener(v -> post());
        binding.btnConfPhoto.setOnClickListener(v -> {
            if (pendingPhoto != null) {
                pendingPhoto = null;
                paintPhoto();
                return;
            }
            pickPhoto();
        });
        binding.btnMyConfessions.setOnClickListener(v -> {
            mineOnly = !mineOnly;
            paintFilter();
            load();
        });
        paintPhoto();
        paintFilter();

        // Assume unverified until the server says otherwise, and render that state now.
        // The observer used to react only to SUCCESS, so a failed or empty verification
        // check left the composer hidden and the explanation hidden too — the student saw
        // an empty screen with no way to post and no reason why.
        renderComposer();
        repository.isVerifiedStudent().observe(this, resource -> {
            if (resource == null) return;
            if (resource.status == Resource.Status.SUCCESS) {
                verified = Boolean.TRUE.equals(resource.data);
            } else if (resource.status == Resource.Status.ERROR) {
                verified = false;
                android.util.Log.w("CampusConfessions",
                        "verification check failed: " + resource.message);
            } else {
                return;
            }
            renderComposer();
        });
        load();
    }

    private void renderComposer() {
        binding.composer.setVisibility(verified ? View.VISIBLE : View.GONE);
        binding.tvGate.setVisibility(verified ? View.GONE : View.VISIBLE);
    }

    private void load() {
        if (loading) return;
        loading = true;
        repository.confessions(30, 0).observe(this, resource -> {
            loading = false;
            if (binding == null || resource == null) return;
            if (resource.status != Resource.Status.SUCCESS || resource.data == null) return;
            latest = resource.data;
            render(latest);
        });
    }

    /** The most recent page, kept so the mine/all switch does not need a refetch. */
    private java.util.List<NovaDtos.Confession> latest = new java.util.ArrayList<>();

    private void post() {
        String text = binding.etConfession.getText().toString().trim();
        if (text.length() < 5) {
            error(getString(R.string.nova_campus_confess_short));
            return;
        }
        NovaModerationDialog.guard(this, "confession", text, 3, false, () -> upload(text), () -> { });
    }

    private void upload(String text) {
        if (pendingPhoto == null) {
            send(text, null);
            return;
        }
        binding.btnPost.setEnabled(false);
        com.insta.nova.util.NovaImageUpload.upload(this, pendingPhoto, "nova",
                new com.insta.nova.util.NovaImageUpload.UploadCallback() {
                    @Override public void onUploaded(String url) { send(text, url); }
                    // A failed upload must not swallow the confession itself.
                    @Override public void onFailed(String message) { send(text, null); }
                });
    }

    private void send(String text, String photoUrl) {
        binding.btnPost.setEnabled(false);
        repository.postConfession(text, photoUrl).observe(this, resource -> {
            if (binding == null || resource == null) return;
            if (resource.status == Resource.Status.LOADING) return;
            binding.btnPost.setEnabled(true);
            if (resource.status == Resource.Status.SUCCESS) {
                binding.etConfession.setText("");
                pendingPhoto = null;
                cameraUri = null;
                paintPhoto();
                NovaUi.haptic(binding.getRoot(), true);
                load();
            } else {
                error(getString(R.string.nova_error)
                        + (resource.message == null ? "" : " (" + resource.message + ")"));
            }
        });
    }

    private void render(List<NovaDtos.Confession> confessions) {
        List<NovaDtos.Confession> shown = new java.util.ArrayList<>();
        for (NovaDtos.Confession confession : confessions) {
            if (!mineOnly || confession.isMine) shown.add(confession);
        }
        binding.confessionList.removeAllViews();
        binding.tvConfEmpty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
        if (shown.isEmpty() && mineOnly) {
            binding.tvConfEmpty.setText(R.string.nova_campus_confess_mine_empty);
        } else {
            binding.tvConfEmpty.setText(R.string.nova_campus_confess_empty);
        }
        for (NovaDtos.Confession confession : shown) {
            binding.confessionList.addView(card(confession));
        }
    }

    private View card(NovaDtos.Confession confession) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.bg_nova_card);
        box.setPadding(NovaUi.dp(this, 16), NovaUi.dp(this, 14),
                NovaUi.dp(this, 16), NovaUi.dp(this, 12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = NovaUi.dp(this, 10);
        box.setLayoutParams(params);

        if (confession.photoUrl != null && !confession.photoUrl.trim().isEmpty()) {
            android.widget.ImageView photo = new android.widget.ImageView(this);
            LinearLayout.LayoutParams photoParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, NovaUi.dp(this, 180));
            photoParams.bottomMargin = NovaUi.dp(this, 10);
            photo.setLayoutParams(photoParams);
            photo.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            com.insta.nova.util.GlideUtils.load(photo, "nova", confession.photoUrl,
                    R.drawable.ic_image, R.drawable.ic_image);
            photo.setOnClickListener(v -> openPhoto(confession.photoUrl));
            box.addView(photo);
        }

        TextView body = new TextView(this);
        body.setText(confession.body == null ? "" : confession.body);
        body.setTextColor(getColor(R.color.nova_text));
        body.setTextSize(14f);
        // Every card keeps the same shape: a long confession is cut to a few lines and the
        // full text opens on tap, instead of one post pushing the rest off screen.
        body.setMaxLines(6);
        body.setEllipsize(android.text.TextUtils.TruncateAt.END);
        box.addView(body);

        LinearLayout foot = new LinearLayout(this);
        foot.setOrientation(LinearLayout.HORIZONTAL);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams footParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        footParams.topMargin = NovaUi.dp(this, 10);
        foot.setLayoutParams(footParams);

        TextView like = new TextView(this);
        like.setText("\u2661 " + confession.likesCount);
        like.setTextSize(12f);
        like.setTextColor(getColor(confession.likedByMe
                ? R.color.color_like : R.color.nova_text_3));
        like.setPadding(0, NovaUi.dp(this, 4), NovaUi.dp(this, 16), NovaUi.dp(this, 4));
        like.setOnClickListener(v -> toggleLike(confession));
        foot.addView(like);

        TextView when = new TextView(this);
        long created = TimeUtils.parseTimestamp(confession.createdAt);
        when.setText(created > 0 ? TimeUtils.formatCompactRelativeTime(created) : "");
        when.setTextSize(11f);
        when.setTextColor(getColor(R.color.nova_text_3));
        LinearLayout.LayoutParams whenParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        when.setLayoutParams(whenParams);
        foot.addView(when);

        if (confession.isMine) {
            TextView delete = new TextView(this);
            delete.setText(R.string.comments_delete_action);
            delete.setTextSize(11f);
            delete.setTextColor(getColor(R.color.nova_danger));
            delete.setOnClickListener(v -> delete(confession));
            foot.addView(delete);
        }
        box.addView(foot);
        return box;
    }

    private void openPhoto(String url) {
        android.content.Intent intent = new android.content.Intent(this,
                com.insta.nova.ui.chat.ImageViewerActivity.class);
        intent.putExtra(com.insta.nova.ui.chat.ImageViewerActivity.EXTRA_IMAGE_URL, url);
        intent.putExtra(com.insta.nova.ui.chat.ImageViewerActivity.EXTRA_BUCKET, "nova");
        startActivity(intent);
    }

    /** The photo row doubles as the attach state: "Add photo" / "Photo attached · remove". */
    private void paintPhoto() {
        if (binding == null) return;
        boolean attached = pendingPhoto != null;
        binding.btnConfPhoto.setText(attached
                ? R.string.nova_campus_confess_photo_attached
                : R.string.nova_campus_confess_photo);
        binding.ivConfPreview.setVisibility(attached ? View.VISIBLE : View.GONE);
        if (attached) {
            com.insta.nova.util.GlideUtils.load(binding.ivConfPreview, "nova",
                    pendingPhoto.toString(), R.drawable.ic_image, R.drawable.ic_image);
        } else {
            binding.ivConfPreview.setImageDrawable(null);
        }
    }

    private void paintFilter() {
        if (binding == null) return;
        binding.btnMyConfessions.setText(mineOnly
                ? R.string.nova_campus_confess_all
                : R.string.nova_campus_confess_mine);
        binding.btnMyConfessions.setBackgroundResource(mineOnly
                ? R.drawable.bg_nova_chip_on : R.drawable.bg_nova_chip);
        binding.btnMyConfessions.setTextColor(getColor(mineOnly
                ? R.color.color_background : R.color.nova_text_3));
    }

    /** Camera + gallery chooser for the confession picture. */
    private void pickPhoto() {
        android.content.Intent gallery = new android.content.Intent(
                android.content.Intent.ACTION_GET_CONTENT);
        gallery.setType("image/*");
        gallery.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        android.content.Intent chooser = android.content.Intent.createChooser(
                gallery, getString(R.string.nova_campus_confess_photo));
        try {
            java.io.File dir = new java.io.File(getCacheDir(), "nova_photo");
            if (!dir.exists()) dir.mkdirs();
            java.io.File file = java.io.File.createTempFile("conf_", ".jpg", dir);
            cameraUri = androidx.core.content.FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", file);
            android.content.Intent camera = new android.content.Intent(
                    android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            camera.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, cameraUri);
            camera.addFlags(android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            chooser.putExtra(android.content.Intent.EXTRA_INITIAL_INTENTS,
                    new android.content.Intent[]{camera});
        } catch (Exception ignored) {
            // No camera: the chooser still offers the gallery.
        }
        photoLauncher.launch(chooser);
    }

    private void toggleLike(NovaDtos.Confession confession) {
        repository.likeConfession(confession.id, !confession.likedByMe).observe(this, resource -> {
            if (resource != null && resource.status == Resource.Status.SUCCESS) load();
        });
    }

    private void delete(NovaDtos.Confession confession) {
        repository.deleteConfession(confession.id).observe(this, resource -> {
            if (resource != null && resource.status == Resource.Status.SUCCESS) load();
        });
    }

    private void error(String message) {
        if (binding == null) return;
        binding.tvConfError.setText(message);
        binding.tvConfError.setVisibility(View.VISIBLE);
        NovaUi.haptic(binding.getRoot(), false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
