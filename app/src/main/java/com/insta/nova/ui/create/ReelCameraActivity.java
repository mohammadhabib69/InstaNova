package com.insta.nova.ui.create;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
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
import com.insta.nova.util.ToastUtil;
import com.insta.nova.util.VideoConcat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Reel recorder: one take at a time, joined later.
 *
 * <p>Instagram's reel camera is not one long recording — pausing keeps what you shot and
 * starts a new take that continues the same timeline, and the last take can be thrown away
 * without losing the rest. That is modelled literally here: every take is its own MP4, the
 * timeline sums their durations, Delete removes the newest take, and Next joins the
 * survivors into the single file the editor and the upload use.
 *
 * <p>The preview is cropped to 9:16 so what is framed is what gets posted; the reel player
 * crops the same way.
 */
public class ReelCameraActivity extends AppCompatActivity {

    private static final String TAG = "ReelCamera";
    private static final int REQ_PERMS = 6301;
    private static final long MAX_TOTAL_MS = 90_000L;

    private PreviewView previewView;
    private FrameLayout previewSlot;
    private View recordButton;
    private View recordRing;
    private TextView tvTimer;
    private TextView tvHint;
    private ImageView ivFlash;
    private ImageView ivDelete;
    private TextView btnNext;
    private LinearLayout timeline;
    private TextView tvEmptyTimeline;

    private ProcessCameraProvider cameraProvider;
    private VideoCapture<Recorder> videoCapture;
    private androidx.camera.core.Camera boundCamera;
    private Recording activeRecording;
    private File activeSegmentFile;

    private final List<File> segments = new ArrayList<>();
    private long completedMs;
    private long segmentStartedAt;
    private boolean recording;
    private boolean frontCamera;
    private boolean torchOn;
    private boolean deleting;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<PickVisualMediaRequest> pickVideo =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) importPickedVideo(uri);
            });

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!recording) return;
            long elapsed = completedMs + (System.currentTimeMillis() - segmentStartedAt);
            renderTimer(elapsed);
            if (elapsed >= MAX_TOTAL_MS) {
                stopSegment("Recording stopped at 1:30");
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
    }

    // ---------------------------------------------------------------- permissions / camera

    private boolean needsPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQ_PERMS) return;
        boolean camera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
        if (!camera) {
            Toast.makeText(this, R.string.reel_cam_permission, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        startCamera();
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                bindVideo();
            } catch (Exception e) {
                Log.e(TAG, "camera unavailable: " + e.getMessage(), e);
                Toast.makeText(this, R.string.reel_cam_permission, Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindVideo() {
        if (cameraProvider == null) return;
        try {
            cameraProvider.unbindAll();
            Preview preview = new Preview.Builder().build();
            preview.setSurfaceProvider(previewView.getSurfaceProvider());

            Recorder recorder = new Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.HD))
                    .build();
            videoCapture = VideoCapture.withOutput(recorder);

            CameraSelector selector = frontCamera
                    ? CameraSelector.DEFAULT_FRONT_CAMERA
                    : CameraSelector.DEFAULT_BACK_CAMERA;
            boundCamera = cameraProvider.bindToLifecycle(this, selector, preview, videoCapture);
            if (torchOn && boundCamera.getCameraInfo().hasFlashUnit()) {
                boundCamera.getCameraControl().enableTorch(true);
            }
        } catch (Exception e) {
            Log.e(TAG, "bind failed: " + e.getMessage(), e);
            Toast.makeText(this, R.string.reel_cam_permission, Toast.LENGTH_LONG).show();
        }
    }

    // ------------------------------------------------------------------------- recording

    private void onRecordTapped() {
        if (recording) {
            stopSegment(null);
        } else {
            if (completedMs >= MAX_TOTAL_MS) {
                ToastUtil.showPill(this, getString(R.string.reel_cam_limit));
                return;
            }
            startSegment();
        }
    }

    private void startSegment() {
        if (recording || videoCapture == null) return;
        File dir = new File(getCacheDir(), "reel_capture");
        if (!dir.exists() && !dir.mkdirs()) {
            ToastUtil.showPill(this, getString(R.string.reel_cam_failed));
            return;
        }
        activeSegmentFile = new File(dir, "seg_" + System.currentTimeMillis() + ".mp4");

        androidx.camera.video.PendingRecording pending = videoCapture.getOutput()
                .prepareRecording(this, new FileOutputOptions.Builder(activeSegmentFile).build());
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            pending = pending.withAudioEnabled();
        }

        recording = true;
        segmentStartedAt = System.currentTimeMillis();
        paintRecordingState();

        activeRecording = pending.start(ContextCompat.getMainExecutor(this), event -> {
            if (!(event instanceof VideoRecordEvent.Finalize)) return;
            VideoRecordEvent.Finalize fin = (VideoRecordEvent.Finalize) event;
            boolean ok = fin.getError() == VideoRecordEvent.Finalize.ERROR_NONE
                    || fin.getError() == VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA;
            File shot = activeSegmentFile;
            activeSegmentFile = null;
            activeRecording = null;
            recording = false;
            handler.removeCallbacks(ticker);
            paintRecordingState();

            if (ok && shot != null && shot.exists() && shot.length() > 0) {
                addSegment(shot);
            } else if (fin.getError() != VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA) {
                Log.e(TAG, "take failed: " + fin.getError());
                ToastUtil.showPill(ReelCameraActivity.this, getString(R.string.reel_cam_failed));
                refreshTimeline();
            }
        });
        handler.post(ticker);
    }

    private void stopSegment(@Nullable String note) {
        if (!recording) return;
        recording = false;
        handler.removeCallbacks(ticker);
        paintRecordingState();
        if (activeRecording != null) {
            activeRecording.stop();
        }
        if (note != null) ToastUtil.showPill(this, note);
    }

    /** A finished take joins the timeline; its real duration comes from the file. */
    private void addSegment(@NonNull File file) {
        segments.add(file);
        new Thread(() -> {
            long ms = VideoConcat.durationMs(file);
            android.graphics.Bitmap frame = VideoConcat.frameAt(file, Math.min(120, ms / 3));
            handler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                completedMs += ms;
                renderTimer(completedMs);
                refreshTimeline();
                if (frame != null) addTimelineThumb(frame);
            });
        }).start();
    }

    private void deleteLastSegment() {
        if (recording) {
            ToastUtil.showPill(this, getString(R.string.reel_cam_delete_while_recording));
            return;
        }
        if (segments.isEmpty() || deleting) return;
        deleting = true;
        File last = segments.remove(segments.size() - 1);
        new Thread(() -> {
            long ms = VideoConcat.durationMs(last);
            //noinspection ResultOfMethodCallIgnored
            last.delete();
            handler.post(() -> {
                deleting = false;
                completedMs = Math.max(0, completedMs - ms);
                renderTimer(completedMs);
                refreshTimeline();
            });
        }).start();
    }

    // ----------------------------------------------------------------------------- UI

    private void paintRecordingState() {
        GradientDrawable inner = new GradientDrawable();
        inner.setShape(GradientDrawable.RECTANGLE);
        inner.setCornerRadius(dp(recording ? 6 : 34));
        inner.setColor(recording ? 0xFFED4956 : Color.WHITE);
        recordButton.setBackground(inner);
        recordButton.setLayoutParams(new FrameLayout.LayoutParams(
                dp(recording ? 34 : 68), dp(recording ? 34 : 68), Gravity.CENTER));
    }

    private void renderTimer(long ms) {
        tvTimer.setText(String.format(Locale.US, "%d:%02d",
                (ms / 1000) / 60, (ms / 1000) % 60));
        long left = Math.max(0, MAX_TOTAL_MS - ms);
        tvTimer.setTextColor(ms >= MAX_TOTAL_MS - 10_000 ? 0xFFFF6B6B : Color.WHITE);
        tvHint.setText(recording
                ? getString(R.string.reel_cam_remaining, left / 1000)
                : getString(segments.isEmpty() ? R.string.reel_cam_tap : R.string.reel_cam_resume));
    }

    private void refreshTimeline() {
        timeline.removeAllViews();
        boolean empty = segments.isEmpty();
        tvEmptyTimeline.setVisibility(empty ? View.VISIBLE : View.GONE);
        ivDelete.setAlpha(empty || recording ? 0.35f : 1f);
        btnNext.setAlpha(empty ? 0.4f : 1f);
        if (empty) return;
        for (File segment : segments) {
            ImageView box = new ImageView(this);
            box.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0x33FFFFFF);
            bg.setCornerRadius(dp(6));
            box.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(64));
            lp.rightMargin = dp(6);
            timeline.addView(box, lp);
        }
    }

    private void addTimelineThumb(@NonNull android.graphics.Bitmap frame) {
        int index = timeline.getChildCount() - 1;
        if (index < 0) return;
        View slot = timeline.getChildAt(index);
        if (!(slot instanceof ImageView)) return;
        ((ImageView) slot).setImageBitmap(frame);
    }

    private void next() {
        if (segments.isEmpty()) {
            ToastUtil.showPill(this, getString(R.string.reel_cam_need_clip));
            return;
        }
        if (recording) {
            stopSegment(null);
            ToastUtil.showPill(this, getString(R.string.reel_cam_stop_first));
            return;
        }
        btnNext.setEnabled(false);
        tvHint.setText(R.string.reel_cam_joining);
        final List<File> parts = new ArrayList<>(segments);
        new Thread(() -> {
            File dir = new File(getCacheDir(), "reel_capture");
            File joined = new File(dir, "reel_" + UUID.randomUUID() + ".mp4");
            try {
                VideoConcat.concat(parts, joined);
            } catch (Exception e) {
                Log.e(TAG, "join failed: " + e.getMessage(), e);
                handler.post(() -> {
                    btnNext.setEnabled(true);
                    tvHint.setText(R.string.reel_cam_tap);
                    ToastUtil.showPill(ReelCameraActivity.this,
                            getString(R.string.reel_cam_join_failed));
                });
                return;
            }
            handler.post(() -> {
                btnNext.setEnabled(true);
                Intent intent = new Intent(this, ReelEditActivity.class);
                intent.putExtra(ReelEditActivity.EXTRA_VIDEO, joined.getAbsolutePath());
                intent.putStringArrayListExtra(ReelEditActivity.EXTRA_CLIPS, pathsOf(parts));
                startActivity(intent);
                finish();
            });
        }).start();
    }

    @NonNull
    private ArrayList<String> pathsOf(@NonNull List<File> files) {
        ArrayList<String> paths = new ArrayList<>();
        for (File file : files) paths.add(file.getAbsolutePath());
        return paths;
    }

    private void pickFromGallery() {
        if (recording) return;
        pickVideo.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.VideoOnly.INSTANCE)
                .build());
    }

    /** A gallery clip becomes the only take, so the editor flow stays identical. */
    private void importPickedVideo(@NonNull Uri uri) {
        new Thread(() -> {
            File dir = new File(getCacheDir(), "reel_capture");
            if (!dir.exists() && !dir.mkdirs()) return;
            File target = new File(dir, "picked_" + System.currentTimeMillis() + ".mp4");
            try (InputStream in = getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(target)) {
                if (in == null) throw new IllegalStateException("no stream");
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            } catch (Exception e) {
                Log.e(TAG, "import failed: " + e.getMessage(), e);
                handler.post(() -> ToastUtil.showPill(this,
                        getString(R.string.reel_cam_failed)));
                return;
            }
            final long ms = VideoConcat.durationMs(target);
            handler.post(() -> {
                segments.clear();
                completedMs = 0;
                segments.add(target);
                completedMs = Math.min(ms, MAX_TOTAL_MS);
                renderTimer(completedMs);
                refreshTimeline();
                next();
            });
        }).start();
    }

    private void toggleCamera() {
        if (recording) {
            ToastUtil.showPill(this, getString(R.string.reel_cam_stop_first));
            return;
        }
        frontCamera = !frontCamera;
        torchOn = false;
        ivFlash.setImageResource(R.drawable.ic_flash_off);
        bindVideo();
    }

    private void toggleTorch() {
        if (boundCamera == null) return;
        if (!boundCamera.getCameraInfo().hasFlashUnit()) {
            ToastUtil.showPill(this, getString(R.string.reel_cam_no_flash));
            return;
        }
        torchOn = !torchOn;
        ivFlash.setImageResource(torchOn ? R.drawable.ic_flash_on : R.drawable.ic_flash_off);
        boundCamera.getCameraControl().enableTorch(torchOn);
    }

    // ------------------------------------------------------------------------- plumbing

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** A 9:16 window: the reel is cropped to this frame at both ends. */
    private static final class NineSixteen extends FrameLayout {
        NineSixteen(android.content.Context context) {
            super(context);
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec);
            int height = Math.round(width * 16f / 9f);
            super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        previewSlot = new NineSixteen(this);
        FrameLayout.LayoutParams slotLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0);
        slotLp.gravity = Gravity.CENTER;
        root.addView(previewSlot, slotLp);

        previewView = new PreviewView(this);
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        previewSlot.addView(previewView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ---- top bar
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(16), dp(12), dp(16), dp(12));

        ImageView close = icon(R.drawable.ic_close, R.string.reel_cam_close);
        close.setOnClickListener(v -> finish());
        top.addView(close, new LinearLayout.LayoutParams(dp(40), dp(40)));

        tvTimer = text("0:00", 16);
        tvTimer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams timerLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        top.addView(tvTimer, timerLp);

        ivFlash = icon(R.drawable.ic_flash_off, R.string.reel_cam_flash);
        ivFlash.setOnClickListener(v -> toggleTorch());
        top.addView(ivFlash, new LinearLayout.LayoutParams(dp(40), dp(40)));

        ImageView flip = icon(R.drawable.ic_camera_flip, R.string.reel_cam_flip);
        flip.setOnClickListener(v -> toggleCamera());
        LinearLayout.LayoutParams flipLp = new LinearLayout.LayoutParams(dp(40), dp(40));
        flipLp.leftMargin = dp(10);
        top.addView(flip, flipLp);

        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        topLp.gravity = Gravity.TOP;
        root.addView(top, topLp);

        // ---- timeline strip + hint sit above the controls
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setGravity(Gravity.CENTER_HORIZONTAL);
        bottom.setPadding(dp(16), dp(12), dp(16), dp(28));

        tvEmptyTimeline = text(getString(R.string.reel_cam_no_clips), 12);
        tvEmptyTimeline.setAlpha(0.7f);
        bottom.addView(tvEmptyTimeline);

        timeline = new LinearLayout(this);
        timeline.setOrientation(LinearLayout.HORIZONTAL);
        timeline.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams timelineLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(64));
        timelineLp.topMargin = dp(4);
        bottom.addView(timeline, timelineLp);

        tvHint = text(getString(R.string.reel_cam_tap), 12);
        tvHint.setAlpha(0.85f);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = dp(6);
        bottom.addView(tvHint, hintLp);

        // ---- control row: gallery, record, delete, next
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        ImageView gallery = icon(R.drawable.ic_gallery, R.string.reel_cam_gallery);
        gallery.setOnClickListener(v -> pickFromGallery());
        row.addView(gallery, new LinearLayout.LayoutParams(dp(40), dp(40)));

        View spacerA = new View(this);
        row.addView(spacerA, new LinearLayout.LayoutParams(0, 1, 1f));

        FrameLayout recordWrap = new FrameLayout(this);
        recordRing = new View(this);
        GradientDrawable ringBg = new GradientDrawable();
        ringBg.setShape(GradientDrawable.OVAL);
        ringBg.setColor(Color.TRANSPARENT);
        ringBg.setStroke(dp(3), 0x66FFFFFF);
        recordRing.setBackground(ringBg);
        recordWrap.addView(recordRing, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        recordButton = new View(this);
        recordButton.setOnClickListener(v -> onRecordTapped());
        recordButton.setContentDescription(getString(R.string.reel_cam_record));
        recordWrap.addView(recordButton, new FrameLayout.LayoutParams(
                dp(68), dp(68), Gravity.CENTER));
        LinearLayout.LayoutParams recordLp = new LinearLayout.LayoutParams(dp(86), dp(86));
        recordLp.leftMargin = dp(16);
        recordLp.rightMargin = dp(16);
        row.addView(recordWrap, recordLp);

        View spacerB = new View(this);
        row.addView(spacerB, new LinearLayout.LayoutParams(0, 1, 1f));

        ivDelete = icon(R.drawable.ic_trash, R.string.reel_cam_delete);
        ivDelete.setOnClickListener(v -> deleteLastSegment());
        row.addView(ivDelete, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = dp(10);
        bottom.addView(row, rowLp);

        btnNext = text(getString(R.string.reel_cam_next), 14);
        btnNext.setGravity(Gravity.CENTER);
        GradientDrawable nextBg = new GradientDrawable();
        nextBg.setColor(0xFF0095F6);
        nextBg.setCornerRadius(dp(20));
        btnNext.setBackground(nextBg);
        btnNext.setOnClickListener(v -> next());
        LinearLayout.LayoutParams nextLp = new LinearLayout.LayoutParams(dp(120), dp(40));
        nextLp.topMargin = dp(14);
        bottom.addView(btnNext, nextLp);

        FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bottomLp.gravity = Gravity.BOTTOM;
        root.addView(bottom, bottomLp);

        setContentView(root);
        paintRecordingState();
        refreshTimeline();
    }

    private ImageView icon(int drawable, int contentDescription) {
        ImageView view = new ImageView(this);
        view.setImageResource(drawable);
        view.setContentDescription(getString(contentDescription));
        view.setPadding(dp(8), dp(8), dp(8), dp(8));
        return view;
    }

    private TextView text(String value, int sp) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(Color.WHITE);
        view.setTextSize(sp);
        view.setShadowLayer(6f, 0f, 0f, 0x99000000);
        return view;
    }

    @Override
    protected void onPause() {
        if (recording) stopSegment(null);
        super.onPause();
    }
}
