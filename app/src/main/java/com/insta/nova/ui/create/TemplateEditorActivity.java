package com.insta.nova.ui.create;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.insta.nova.R;
import com.insta.nova.data.local.TemplateCatalog;
import com.insta.nova.databinding.ActivityTemplateEditorBinding;
import com.insta.nova.databinding.DialogTemplatePreviewBinding;
import com.insta.nova.util.TemplateRenderer;
import com.insta.nova.util.ToastUtil;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Local template editor: pick a photo, fill the two fields, preview the rendered
 * card, then hand the image to the existing New Post / New Story flows (which keep
 * owning persistence and the feed/story-tray updates).
 */
public class TemplateEditorActivity extends AppCompatActivity {

    private ActivityTemplateEditorBinding binding;
    private TemplateCatalog.Template template;
    private String photoPath;
    private final List<EditText> fields = new ArrayList<>();

    private final ActivityResultLauncher<PickVisualMediaRequest> pickPhoto =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) importPhoto(uri);
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTemplateEditorBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        template = TemplateCatalog.byId(getIntent().getStringExtra(TemplateCatalog.EXTRA_TEMPLATE_ID));
        binding.tvEditorTitle.setText(template.title);

        binding.btnEditorClose.setOnClickListener(v -> finish());
        View.OnClickListener pick = v -> pickPhoto.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
        binding.ivTemplatePhoto.setOnClickListener(pick);
        binding.btnPickPhoto.setOnClickListener(pick);

        buildFields();

        binding.btnTemplatePreview.setOnClickListener(v -> showPreview());
        binding.btnShareAsPost.setOnClickListener(v -> share(false));
        binding.btnShareAsStory.setOnClickListener(v -> share(true));

        if (savedInstanceState != null) {
            photoPath = savedInstanceState.getString("photo_path");
            if (photoPath != null) showPhoto();
        }
    }

    private void buildFields() {
        fields.clear();
        binding.fieldsContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (String hint : template.fieldHints) {
            EditText field = (EditText) inflater.inflate(
                    R.layout.item_template_field, binding.fieldsContainer, false);
            field.setHint(hint);
            binding.fieldsContainer.addView(field);
            fields.add(field);
        }
    }

    private void importPhoto(Uri uri) {
        try {
            File dir = new File(getFilesDir(), "templates");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("no dir");
            File target = new File(dir, "photo_" + System.currentTimeMillis() + ".jpg");
            try (InputStream in = getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(target)) {
                if (in == null) throw new IllegalStateException("no stream");
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            }
            photoPath = target.getAbsolutePath();
            showPhoto();
        } catch (Exception e) {
            ToastUtil.showPill(this, getString(R.string.templates_photo_failed));
        }
    }

    private void showPhoto() {
        Glide.with(this).load(new File(photoPath)).into(binding.ivTemplatePhoto);
    }

    private String[] values() {
        String[] out = new String[fields.size()];
        for (int i = 0; i < fields.size(); i++) {
            out[i] = fields.get(i).getText() == null ? "" : fields.get(i).getText().toString();
        }
        return out;
    }

    private void showPreview() {
        Bitmap card = TemplateRenderer.render(template, values(), photoPath, false);
        if (card == null) {
            ToastUtil.showPill(this, getString(R.string.templates_render_failed));
            return;
        }
        DialogTemplatePreviewBinding preview = DialogTemplatePreviewBinding.inflate(getLayoutInflater());
        preview.ivTemplatePreview.setImageBitmap(card);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(preview.getRoot())
                .create();
        dialog.setOnDismissListener(d -> {
            preview.ivTemplatePreview.setImageDrawable(null);
            if (!card.isRecycled()) card.recycle();
        });
        preview.getRoot().setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    /** Story card sized to this device so the viewer's centre-crop cuts nothing. */
    private Bitmap renderStory() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int width = 1080;
        int height = metrics.widthPixels > 0
                ? Math.round(1080f * metrics.heightPixels / metrics.widthPixels)
                : 1920;
        return TemplateRenderer.render(template, values(), photoPath, width, height);
    }

    private void share(boolean asStory) {
        Bitmap card = asStory ? renderStory() : TemplateRenderer.render(template, values(), photoPath, false);
        if (card == null) {
            ToastUtil.showPill(this, getString(R.string.templates_render_failed));
            return;
        }
        String mediaUri;
        try {
            mediaUri = saveCard(card, asStory ? "stories" : "posts");
        } catch (Exception e) {
            ToastUtil.showPill(this, getString(R.string.templates_render_failed));
            return;
        } finally {
            if (!card.isRecycled()) card.recycle();
        }
        if (mediaUri == null) return;

        String[] inputs = values();
        if (asStory) {
            Intent next = new Intent(this, NewStoryActivity.class);
            next.putExtra(NewStoryActivity.EXTRA_MEDIA_URI, mediaUri);
            startActivity(next);
        } else {
            Intent next = new Intent(this, NewPostActivity.class);
            next.putExtra(NewPostActivity.EXTRA_MEDIA_URI, mediaUri);
            next.putExtra(NewPostActivity.EXTRA_CAPTION,
                    template.caption(inputs.length > 0 ? inputs[0] : "",
                            inputs.length > 1 ? inputs[1] : ""));
            startActivity(next);
        }
        finish();
    }

    /** Writes the rendered card into app-private storage and returns a file:// URI. */
    private String saveCard(Bitmap bitmap, String folder) throws Exception {
        File dir = new File(getFilesDir(), folder);
        if (!dir.exists() && !dir.mkdirs()) return null;
        File target = new File(dir, "template_" + System.currentTimeMillis() + ".png");
        try (FileOutputStream out = new FileOutputStream(target)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) return null;
        }
        return Uri.fromFile(target).toString();
    }

    @Override
    protected void onSaveInstanceState(@androidx.annotation.NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("photo_path", photoPath);
    }
}
