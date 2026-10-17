package com.insta.nova.ui.auth;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.insta.nova.BuildConfig;
import com.insta.nova.R;
import com.insta.nova.databinding.FragmentWizardVibeBinding;
import com.insta.nova.util.Constants;
import com.insta.nova.util.GlideUtils;
import com.insta.nova.util.Resource;
import com.yalantis.ucrop.UCrop;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Screen 10 — "Create a profile that shows your vibe" (reference image 7).
 *
 * The avatar flow is real: pick or shoot, crop 1:1 with uCrop, resize to 400px and
 * re-encode as JPEG, upload the raw bytes as {uid}/{uuid}.jpg, then PATCH the
 * profile's avatar_url and show it immediately.
 */
public class WizardVibeFragment extends WizardStepFragment {

    private FragmentWizardVibeBinding binding;
    private RegistrationViewModel viewModel;
    private ActivityResultLauncher<PickVisualMediaRequest> pickMedia;
    private ActivityResultLauncher<Uri> takePicture;
    private Uri pendingCameraUri;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pickMedia = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(),
                uri -> { if (uri != null) startCrop(uri); });
        takePicture = registerForActivityResult(new ActivityResultContracts.TakePicture(),
                saved -> { if (saved != null && saved && pendingCameraUri != null) startCrop(pendingCameraUri); });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentWizardVibeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(RegistrationViewModel.class);

        binding.tvVibeUsername.setText(viewModel.getUsername());
        binding.tvVibeFullname.setText(viewModel.getFullName());

        binding.btnSkip.setOnClickListener(v -> goNext());
        binding.btnDone.setOnClickListener(v -> goNext());
        binding.tvVibeBio.setOnClickListener(v -> NavHostFragment.findNavController(this)
                .navigate(R.id.action_vibe_to_bio));
        binding.ivVibeAvatar.setOnClickListener(v -> showAvatarSheet());
        binding.btnCameraBadge.setOnClickListener(v -> showAvatarSheet());

        // Restore anything already chosen / uploaded, and any bio already saved.
        if (viewModel.getAvatarUri() != null) {
            binding.ivVibeAvatar.setImageURI(viewModel.getAvatarUri());
        } else if (notEmpty(viewModel.getAvatarUrl())) {
            GlideUtils.loadAvatar(binding.ivVibeAvatar, viewModel.getAvatarUrl());
        }
        renderBio();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (viewModel != null) renderBio();
    }

    private void renderBio() {
        if (binding == null) return;
        String bio = viewModel.getBio();
        if (notEmpty(bio)) {
            binding.tvVibeBio.setText(bio);
            binding.tvVibeBio.setTextColor(androidx.core.content.ContextCompat
                    .getColor(requireContext(), R.color.vibe_text));
            binding.tvVibeBio.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                    getResources().getDimension(R.dimen.vibe_bio_size_saved));
        } else {
            binding.tvVibeBio.setText(R.string.vibe_add_bio);
        }
    }

    private void goNext() {
        NavHostFragment.findNavController(this).navigate(R.id.action_vibe_to_allow);
    }

    /* ------------------------------------------------------------------ */
    /* Avatar                                                              */
    /* ------------------------------------------------------------------ */

    private void showAvatarSheet() {
        BottomSheetDialog sheet =
                new BottomSheetDialog(requireContext(), R.style.InstaNovaBottomSheetDialog);
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.bg_bottom_sheet);

        TextView title = new TextView(requireContext());
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tp.topMargin = dpPx(20);
        title.setLayoutParams(tp);
        title.setPadding(dpPx(16), 0, dpPx(16), dpPx(8));
        title.setText(R.string.vibe_new_profile_picture);
        title.setTextAppearance(R.style.TextAppearance_Insta_AuthSheetRow);
        root.addView(title);

        TextView gallery = sheetRow(root, getString(R.string.vibe_choose_gallery));
        gallery.setOnClickListener(v -> {
            sheet.dismiss();
            pickMedia.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build());
        });

        TextView camera = sheetRow(root, getString(R.string.vibe_take_photo));
        camera.setOnClickListener(v -> {
            sheet.dismiss();
            launchCamera();
        });

        TextView cancel = sheetRow(root, getString(R.string.auth_cancel));
        cancel.setOnClickListener(v -> sheet.dismiss());

        sheet.setContentView(root);
        sheet.show();
    }

    private TextView sheetRow(LinearLayout parent, String label) {
        TextView row = new TextView(requireContext());
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpPx(56)));
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dpPx(16), 0, dpPx(16), 0);
        row.setText(label);
        row.setTextAppearance(R.style.TextAppearance_Insta_AuthSheetRow);
        row.setBackgroundResource(android.R.drawable.list_selector_background);
        parent.addView(row);
        return row;
    }

    private void launchCamera() {
        try {
            File dir = new File(requireContext().getCacheDir(), "avatars");
            if (!dir.exists() && !dir.mkdirs()) throw new java.io.IOException("no cache dir");
            File file = File.createTempFile("capture", ".jpg", dir);
            pendingCameraUri = FileProvider.getUriForFile(requireContext(),
                    BuildConfig.APPLICATION_ID + ".fileprovider", file);
            takePicture.launch(pendingCameraUri);
        } catch (Exception e) {
            toast(getString(R.string.vibe_camera_failed));
        }
    }

    private void startCrop(Uri source) {
        try {
            File dir = new File(requireContext().getCacheDir(), "avatars");
            if (!dir.exists() && !dir.mkdirs()) throw new java.io.IOException("no cache dir");

            // Copy input stream to private cache file so UCropActivity can read it safely without URI permission issues
            File sourceFile = File.createTempFile("source_avatar", ".jpg", dir);
            try (InputStream in = requireContext().getContentResolver().openInputStream(source);
                 java.io.FileOutputStream outStream = new java.io.FileOutputStream(sourceFile)) {
                if (in == null) throw new java.io.IOException("Cannot open input stream for source");
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) {
                    outStream.write(buf, 0, len);
                }
            }

            File out = File.createTempFile("cropped", ".jpg", dir);
            UCrop.of(Uri.fromFile(sourceFile), Uri.fromFile(out))
                    .withAspectRatio(1f, 1f)
                    .withMaxResultSize(Constants.AVATAR_TARGET_PX, Constants.AVATAR_TARGET_PX)
                    .start(requireContext(), this);
        } catch (Exception e) {
            android.util.Log.e("WizardVibe", "startCrop failed", e);
            toast(getString(R.string.vibe_upload_failed));
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == UCrop.REQUEST_CROP) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                Uri cropped = UCrop.getOutput(data);
                if (cropped != null) {
                    viewModel.setAvatarUri(cropped);
                    binding.ivVibeAvatar.setImageURI(cropped);
                    uploadAvatar(cropped);
                }
            } else if (resultCode == UCrop.RESULT_ERROR && data != null) {
                Throwable cropError = UCrop.getError(data);
                android.util.Log.e("WizardVibe", "UCrop failed: " + (cropError != null ? cropError.getMessage() : "unknown"), cropError);
                toast(getString(R.string.vibe_upload_failed));
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /** 400px square JPEG, then raw-bytes upload and an immediate profile PATCH. */
    private void uploadAvatar(final Uri cropped) {
        byte[] bytes;
        try {
            bytes = toSquareJpeg(cropped);
        } catch (Exception e) {
            android.util.Log.e("WizardVibe", "toSquareJpeg failed", e);
            toast(getString(R.string.vibe_upload_failed));
            return;
        }
        binding.progressAvatar.setVisibility(View.VISIBLE);
        viewModel.uploadImage("avatars", bytes, "jpg").observe(getViewLifecycleOwner(), resource -> {
            if (resource == null || resource.status == Resource.Status.LOADING) return;
            if (resource.status != Resource.Status.SUCCESS || resource.data == null) {
                binding.progressAvatar.setVisibility(View.GONE);
                android.util.Log.e("WizardVibe", "uploadImage failed: " + resource.message);
                toast(resource.message == null ? getString(R.string.vibe_upload_failed) : resource.message);
                return;
            }
            final String publicUrl = resource.data;
            Map<String, Object> fields = new HashMap<>();
            fields.put("avatar_url", publicUrl);
            viewModel.patchProfileFields(fields).observe(getViewLifecycleOwner(), patch -> {
                if (patch == null || patch.status == Resource.Status.LOADING) return;
                binding.progressAvatar.setVisibility(View.GONE);
                if (patch.status == Resource.Status.SUCCESS) {
                    viewModel.setAvatarUrl(publicUrl);
                    com.insta.nova.data.local.SessionManager.getInstance(requireContext())
                            .saveIdentity(viewModel.getUserId(), viewModel.getProfileId(),
                                    viewModel.getUsername(), publicUrl);
                    GlideUtils.loadAvatar(binding.ivVibeAvatar, publicUrl);
                } else {
                    android.util.Log.e("WizardVibe", "patchProfileFields failed: " + patch.message);
                    toast(patch.message == null ? getString(R.string.vibe_upload_failed) : patch.message);
                }
            });
        });
    }

    private InputStream openUri(Uri uri) throws Exception {
        if ("file".equalsIgnoreCase(uri.getScheme()) && uri.getPath() != null) {
            return new java.io.FileInputStream(new File(uri.getPath()));
        }
        return requireContext().getContentResolver().openInputStream(uri);
    }

    /** Decodes with a sampled bitmap, centre-crops square and compresses to JPEG. */
    private byte[] toSquareJpeg(Uri uri) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = openUri(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= Constants.AVATAR_TARGET_PX
                && bounds.outHeight / (sample * 2) >= Constants.AVATAR_TARGET_PX) {
            sample *= 2;
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap decoded;
        try (InputStream in = openUri(uri)) {
            decoded = BitmapFactory.decodeStream(in, null, opts);
        }
        if (decoded == null) throw new java.io.IOException("decode failed");

        int side = Math.min(decoded.getWidth(), decoded.getHeight());
        Bitmap square = Bitmap.createBitmap(decoded,
                (decoded.getWidth() - side) / 2, (decoded.getHeight() - side) / 2, side, side);
        Bitmap scaled = Bitmap.createScaledBitmap(square,
                Constants.AVATAR_TARGET_PX, Constants.AVATAR_TARGET_PX, true);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        scaled.compress(Bitmap.CompressFormat.JPEG, Constants.AVATAR_JPEG_QUALITY, out);
        if (scaled != decoded && scaled != square) scaled.recycle();
        if (square != decoded) square.recycle();
        decoded.recycle();
        return out.toByteArray();
    }

    private int dpPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String message) {
        if (message != null && getContext() != null) {
            android.widget.Toast.makeText(requireContext(), message,
                    android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
