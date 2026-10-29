package com.insta.nova.ui.story2;

import android.Manifest;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.insta.nova.R;
import com.insta.nova.util.GlideUtils;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Entry screen of the story flow: a full-screen camera that hands media to the editor.
 *
 * <p>Photo = tap. Video = press and hold, or hands-free (tap to start, tap to stop). Both
 * stop at 60 s, which is the same cap Instagram uses and the one the editor's trim bar
 * assumes. The gallery thumbnail and an upward swipe on the preview go to the picker
 * instead, so the camera is never a dead end.
 */
public class StoryCameraActivity extends AppCompatActivity {

    private static final String TAG = "StoryCamera";
    private static final int REQ_PERMS = 6101;
    private static final long MAX_VIDEO_MS = 60_000L;

    private PreviewView previewView;
    private ImageView ivFlash;
    private ImageView ivLatest;
    private View captureButton;
    private ProgressBar ring;
    private TextView tvTimer;
    private View tvHandsFree;

    private ProcessCameraProvider cameraProvider;
    private ImageCapture imageCapture;
    private VideoCapture<Recorder> videoCapture;
    private Recording activeRecording;
    private File activeVideoFile;

    private boolean frontCamera;
    private boolean recording;
    private boolean handsFree;
    private boolean touchRecording;
    private int flashMode = ImageCapture.FLASH_MODE_OFF;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long recordStartedAt;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!recording) return;
            long elapsed = System.currentTimeMillis() - recordStartedAt;
            ring.setProgress((int) Math.min(elapsed, MAX_VIDEO_MS));
            tvTimer.setText(String.format(java.util.Locale.US, "%d:%02d",
                    elapsed / 60000, (elapsed / 1000) % 60));
            if (elapsed >= MAX_VIDEO_MS) {
                stopRecording();
                return;
            }
            handler.postDelayed(this, 100);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        if (needsPermissions()) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                    REQ_PERMS);
        } else {
            startCamera();
        }
        loadLatestThumbnail();
    }

    /* ------------------------------------------------------------------ */
    /* UI                                                                  */
    /* ------------------------------------------------------------------ */

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        previewView = new PreviewView(this);
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        root.addView(previewView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ---- top row: close, flash ----
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(16), dp(40), dp(16), 0);
        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        topLp.gravity = Gravity.TOP;

        TextView close = glyph("✕", 26);
        close.setContentDescription(getString(R.string.story_cam_close));
        close.setOnClickListener(v -> finish());
        top.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));

        View spacer = new View(this);
        top.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        ivFlash = new ImageView(this);
        ivFlash.setImageResource(R.drawable.ic_flash_off);
        ivFlash.setContentDescription(getString(R.string.story_cam_flash));
        ivFlash.setOnClickListener(v -> cycleFlash());
        top.addView(ivFlash, new LinearLayout.LayoutParams(dp(44), dp(44)));
        root.addView(top, topLp);

        // ---- left rail: create, hands-free ----
        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams railLp = new FrameLayout.LayoutParams(
                dp(56), ViewGroup.LayoutParams.WRAP_CONTENT);
        railLp.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        railLp.leftMargin = dp(12);

        LinearLayout createItem = railItem("Aa", getString(R.string.story_cam_create),
                v -> StoryEditorActivity.startCreate(this));
        rail.addView(createItem, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout handsItem = railItem("◉", getString(R.string.story_cam_handsfree), v -> {
            handsFree = !handsFree;
            tvHandsFree.setAlpha(handsFree ? 1f : 0.7f);
            Toast.makeText(this, handsFree ? R.string.story_cam_handsfree_on
                    : R.string.story_cam_handsfree_off, Toast.LENGTH_SHORT).show();
            if (handsFree) startRecording(); else stopRecording();
        });
        tvHandsFree = handsItem;
        rail.addView(handsItem, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(rail, railLp);

        // ---- bottom bar: thumbnail, capture, flip, STORY ----
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setGravity(Gravity.CENTER_HORIZONTAL);
        bottom.setPadding(0, 0, 0, dp(28));
        FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bottomLp.gravity = Gravity.BOTTOM;

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        ivLatest = new ImageView(this);
        ivLatest.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ivLatest.setBackgroundResource(R.drawable.bg_avatar_ring);
        ivLatest.setContentDescription(getString(R.string.story_cam_gallery));
        ivLatest.setOnClickListener(v -> openPicker());
        LinearLayout.LayoutParams thumbLp = new LinearLayout.LayoutParams(dp(52), dp(52));
        thumbLp.leftMargin = dp(24);
        row.addView(ivLatest, thumbLp);

        View leftSpacer = new View(this);
        row.addView(leftSpacer, new LinearLayout.LayoutParams(0, 1, 1f));

        FrameLayout captureWrap = new FrameLayout(this);
        captureButton = new View(this);
        GradientDrawable captureBg = new GradientDrawable();
        captureBg.setShape(GradientDrawable.OVAL);
        captureBg.setColor(Color.WHITE);
        captureBg.setStroke(dp(4), 0x66FFFFFF);
        captureButton.setBackground(captureBg);
        captureWrap.addView(captureButton, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ring = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        ring.setMax((int) MAX_VIDEO_MS);
        ring.setProgress(0);
        ring.setVisibility(View.INVISIBLE);
        captureWrap.addView(ring, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(4)));

        row.addView(captureWrap, new LinearLayout.LayoutParams(dp(84), dp(84)));

        View rightSpacer = new View(this);
        row.addView(rightSpacer, new LinearLayout.LayoutParams(0, 1, 1f));

        ImageView flip = new ImageView(this);
        flip.setImageResource(R.drawable.ic_camera_flip);
        flip.setContentDescription(getString(R.string.story_cam_flip));
        flip.setOnClickListener(v -> {
            frontCamera = !frontCamera;
            startCamera();
        });
        LinearLayout.LayoutParams flipLp = new LinearLayout.LayoutParams(dp(44), dp(44));
        flipLp.rightMargin = dp(24);
        row.addView(flip, flipLp);

        bottom.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView label = glyph(getString(R.string.story_cam_label), 12);
        label.setLetterSpacing(0.25f);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelLp.topMargin = dp(8);
        bottom.addView(label, labelLp);

        tvTimer = glyph("0:00", 14);
        tvTimer.setVisibility(View.GONE);
        FrameLayout.LayoutParams timerLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        timerLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        timerLp.topMargin = dp(96);
        root.addView(tvTimer, timerLp);

        root.addView(bottom, bottomLp);

        captureButton.setOnClickListener(v -> {
            if (recording) {
                stopRecording();
            } else if (handsFree) {
                startRecording();
            } else {
                takePhoto();
            }
        });
        captureButton.setOnLongClickListener(v -> {
            if (!recording) {
                touchRecording = true;
                startRecording();
            }
            return true;
        });
        captureButton.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP
                    || event.getAction() == MotionEvent.ACTION_CANCEL) {
                if (touchRecording) {
                    touchRecording = false;
                    stopRecording();
                }
            }
            return false;
        });

        // Upward swipe on the preview is the second way into the picker.
        final float[] downY = {0f};
        previewView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                downY[0] = event.getY();
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                if (downY[0] - event.getY() > dp(80)) openPicker();
            }
            return false;
        });

        setContentView(root);
    }

    private LinearLayout railItem(String glyph, String label, View.OnClickListener click) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(0, dp(10), 0, dp(10));

        TextView icon = glyph(glyph, 20);
        icon.setContentDescription(label);
        box.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40)));

        TextView caption = glyph(label, 9);
        caption.setAlpha(0.85f);
        box.addView(caption, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ((LinearLayout.LayoutParams) caption.getLayoutParams()).topMargin = dp(2);
        box.setOnClickListener(click);
        return box;
    }

    private TextView glyph(String text, int sp) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(sp);
        tv.setGravity(Gravity.CENTER);
        tv.setShadowLayer(6f, 0f, 0f, 0x99000000);
        return tv;
    }

    /* ------------------------------------------------------------------ */
    /* Camera                                                              */
    /* ------------------------------------------------------------------ */

    private boolean needsPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_PERMS) return;
        boolean cameraOk = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (cameraOk) {
            startCamera();
        } else {
            Toast.makeText(this, R.string.story_cam_perm, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                bindUseCases();
            } catch (Exception e) {
                Log.e(TAG, "camera bind failed: " + e.getMessage(), e);
                Toast.makeText(this, R.string.story_cam_perm, Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    /**
     * ImageCapture and VideoCapture cannot share one CameraX session, and asking them to
     * (as the first version did) leaves the preview surface without frames instead of
     * failing loudly. So the session is bound per mode: photo first, video only while
     * recording, then back to photo.
     */
    private void bindUseCases() {
        bindUseCases(false);
    }

    private void bindUseCases(boolean forVideo) {
        if (cameraProvider == null) return;
        try {
            cameraProvider.unbindAll();

            Preview preview = new Preview.Builder().build();
            preview.setSurfaceProvider(previewView.getSurfaceProvider());

            CameraSelector selector = frontCamera
                    ? CameraSelector.DEFAULT_FRONT_CAMERA
                    : CameraSelector.DEFAULT_BACK_CAMERA;

            if (forVideo) {
                imageCapture = null;
                Recorder recorder = new Recorder.Builder()
                        .setQualitySelector(QualitySelector.from(Quality.HD))
                        .build();
                videoCapture = VideoCapture.withOutput(recorder);
                cameraProvider.bindToLifecycle(this, selector, preview, videoCapture);
            } else {
                videoCapture = null;
                imageCapture = new ImageCapture.Builder()
                        .setFlashMode(flashMode)
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();
                cameraProvider.bindToLifecycle(this, selector, preview, imageCapture);
            }
        } catch (Exception e) {
            Log.e(TAG, "camera bind failed (video=" + forVideo + "): " + e, e);
            Toast.makeText(this, R.string.story_cam_perm, Toast.LENGTH_LONG).show();
        }
    }

    private void cycleFlash() {
        if (flashMode == ImageCapture.FLASH_MODE_OFF) {
            flashMode = ImageCapture.FLASH_MODE_ON;
            ivFlash.setImageResource(R.drawable.ic_flash_on);
        } else if (flashMode == ImageCapture.FLASH_MODE_ON) {
            flashMode = ImageCapture.FLASH_MODE_AUTO;
            ivFlash.setImageResource(R.drawable.ic_flash_auto);
        } else {
            flashMode = ImageCapture.FLASH_MODE_OFF;
            ivFlash.setImageResource(R.drawable.ic_flash_off);
        }
        if (imageCapture != null) imageCapture.setFlashMode(flashMode);
    }

    private File newStoryFile(String ext) {
        File dir = new File(getCacheDir(), "story_capture");
        if (!dir.exists() && !dir.mkdirs()) Log.w(TAG, "could not create " + dir);
        return new File(dir, "cap_" + System.currentTimeMillis() + "." + ext);
    }

    private void takePhoto() {
        if (imageCapture == null) {
            bindUseCases(false);
            handler.postDelayed(this::takePhoto, 400);
            return;
        }
        final File out = newStoryFile("jpg");
        imageCapture.takePicture(new ImageCapture.OutputFileOptions.Builder(out).build(),
                ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override public void onImageSaved(@NonNull ImageCapture.OutputFileResults r) {
                        openEditor(Uri.fromFile(out));
                    }

                    @Override public void onError(@NonNull ImageCaptureException e) {
                        Log.e(TAG, "photo failed: " + e.getMessage(), e);
                        Toast.makeText(StoryCameraActivity.this,
                                R.string.story_cam_capture_failed, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void startRecording() {
        if (recording) return;
        if (videoCapture == null) {
            bindUseCases(true);
            handler.postDelayed(this::prepareAndStartRecording, 450);
            return;
        }
        prepareAndStartRecording();
    }

    private void prepareAndStartRecording() {
        if (recording || videoCapture == null) return;
        activeVideoFile = newStoryFile("mp4");
        FileOutputOptions options = new FileOutputOptions.Builder(activeVideoFile).build();

        androidx.camera.video.PendingRecording pending =
                videoCapture.getOutput().prepareRecording(this, options);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            pending = pending.withAudioEnabled();
        }

        recording = true;
        recordStartedAt = System.currentTimeMillis();
        ring.setProgress(0);
        ring.setVisibility(View.VISIBLE);
        tvTimer.setVisibility(View.VISIBLE);
        tvTimer.setText("0:00");
        handler.post(ticker);

        activeRecording = pending.start(ContextCompat.getMainExecutor(this), event -> {
            if (event instanceof VideoRecordEvent.Finalize) {
                VideoRecordEvent.Finalize fin = (VideoRecordEvent.Finalize) event;
                boolean ok = fin.getError() == VideoRecordEvent.Finalize.ERROR_NONE;
                recording = false;
                handler.removeCallbacks(ticker);
                ring.setVisibility(View.INVISIBLE);
                tvTimer.setVisibility(View.GONE);
                bindUseCases(false);
                if (ok && activeVideoFile != null && activeVideoFile.exists()
                        && activeVideoFile.length() > 0) {
                    openEditor(Uri.fromFile(activeVideoFile));
                } else if (fin.getError() != VideoRecordEvent.Finalize.ERROR_NONE
                        && fin.getError() != VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA) {
                    Log.e(TAG, "recording finalize error " + fin.getError());
                    Toast.makeText(StoryCameraActivity.this,
                            R.string.story_cam_capture_failed, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void stopRecording() {
        if (!recording) return;
        recording = false;
        handler.removeCallbacks(ticker);
        ring.setVisibility(View.INVISIBLE);
        tvTimer.setVisibility(View.GONE);
        if (activeRecording != null) {
            activeRecording.stop();
            activeRecording = null;
        }
    }

    /* ------------------------------------------------------------------ */
    /* Navigation                                                          */
    /* ------------------------------------------------------------------ */

    private void openEditor(Uri uri) {
        StoryEditorActivity.start(this, java.util.Collections.singletonList(uri.toString()));
        finish();
    }

    private void openPicker() {
        startActivity(new Intent(this, StoryGalleryActivity.class));
        finish();
    }

    private void loadLatestThumbnail() {
        io.execute(() -> {
            Uri latest = null;
            String[] projection = {MediaStore.Images.Media._ID};
            try (Cursor c = getContentResolver().query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection,
                    null, null, MediaStore.Images.Media.DATE_ADDED + " DESC")) {
                if (c != null && c.moveToFirst()) {
                    latest = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            c.getLong(0));
                }
            } catch (Exception e) {
                Log.w(TAG, "latest thumbnail query failed: " + e.getMessage());
            }
            final Uri resolved = latest;
            if (resolved == null) return;
            runOnUiThread(() -> GlideUtils.load(ivLatest, "posts", resolved.toString()));
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (recording) stopRecording();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
        handler.removeCallbacksAndMessages(null);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
