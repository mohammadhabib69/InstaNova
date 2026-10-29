package com.insta.nova.ui.create;

import android.graphics.Bitmap;
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
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.insta.nova.BuildConfig;
import com.insta.nova.R;
import com.insta.nova.data.local.LocalSocialStore;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.model.Post;
import com.insta.nova.data.remote.RetrofitClient;
import com.insta.nova.data.remote.api.RestApi;
import com.insta.nova.data.remote.api.StorageApi;
import com.insta.nova.data.remote.dto.PostDto;
import com.insta.nova.util.ToastUtil;
import com.insta.nova.util.VideoConcat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Reel editor: 9:16 looping preview, the clip strip, a cover picker, the caption and the
 * "Also share to Feed" choice, then the upload.
 *
 * <p>The recorded takes arrive as separate files and as the already-joined MP4. The strip is
 * clickable while scrubbing is not implemented: tapping a clip jumps the preview to where
 * that clip starts in the joined file, which is enough to check the join.
 */
public class ReelEditActivity extends AppCompatActivity {

    public static final String EXTRA_VIDEO = "extra_video";
    public static final String EXTRA_CLIPS = "extra_clips";

    private static final String TAG = "ReelEdit";

    private String videoPath;
    private ArrayList<String> clipPaths = new ArrayList<>();

    private VideoView videoView;
    private EditText etCaption;
    private Switch switchAlsoShare;
    private TextView tvCoverHint;
    private LinearLayout clipStrip;

    private Bitmap cover;
    private long coverMs;
    private long totalMs;
    private boolean publishing;

    /** Timed text lines: {text, startMs, endMs}. Stored with the reel, never burned in. */
    private final java.util.List<java.util.Map<String, Object>> textLines =
            new java.util.ArrayList<>();
    private LinearLayout textList;
    private float originalVolume = 1f;
    private float musicVolume = 0.6f;
    private Uri musicUri;
    private String musicTitle;
    private String uploadedMusicUrl;
    private TextView tvMusicHint;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        videoPath = getIntent().getStringExtra(EXTRA_VIDEO);
        ArrayList<String> clips = getIntent().getStringArrayListExtra(EXTRA_CLIPS);
        if (clips != null) clipPaths = clips;
        if (videoPath == null) {
            finish();
            return;
        }
        buildUi();
        preparePreview();
        new Thread(() -> {
            totalMs = VideoConcat.durationMs(new File(videoPath));
            Bitmap first = VideoConcat.frameAt(new File(videoPath), 0);
            handler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (first != null) setCover(first, 0);
            });
        }).start();
    }

    // ---------------------------------------------------------------------------- publish

    /** Adds a text line that is shown between two points of the clip. */
    private void promptTimedText() {
        EditText input = new EditText(this);
        input.setHint(R.string.reel_text_hint);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(0xFF888888);
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.reel_text_action)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String text = input.getText().toString().trim();
                    if (text.isEmpty()) return;
                    long start = Math.max(0L, videoView.getCurrentPosition());
                    long end = Math.min(totalMs > 0 ? totalMs : start + 3000L, start + 3000L);
                    if (end <= start) end = start + 1000L;
                    java.util.Map<String, Object> line = new java.util.HashMap<>();
                    line.put("text", text);
                    line.put("startMs", start);
                    line.put("endMs", end);
                    textLines.add(line);
                    rebuildTextList();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void rebuildTextList() {
        if (textList == null) return;
        textList.removeAllViews();
        for (int i = 0; i < textLines.size(); i++) {
            final int index = i;
            java.util.Map<String, Object> line = textLines.get(i);
            TextView chip = new TextView(this);
            chip.setText(fmtMs((Long) line.get("startMs")) + "–"
                    + fmtMs((Long) line.get("endMs")) + "  " + line.get("text"));
            chip.setTextColor(Color.WHITE);
            chip.setTextSize(12);
            GradientDrawable chipBg = new GradientDrawable();
            chipBg.setColor(0x33FFFFFF);
            chipBg.setCornerRadius(dp(10));
            chip.setBackground(chipBg);
            chip.setPadding(dp(10), dp(4), dp(10), dp(4));
            chip.setOnClickListener(v -> {
                textLines.remove(index);
                rebuildTextList();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(4);
            textList.addView(chip, lp);
        }
    }

    private String fmtMs(long ms) {
        long seconds = Math.max(0L, ms) / 1000L;
        return String.format(java.util.Locale.US, "%d:%02d", seconds / 60, seconds % 60);
    }

    private static final int REQ_REEL_MUSIC = 6421;

    private void pickMusic() {
        android.content.Intent intent = new android.content.Intent(
                android.content.Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        try {
            startActivityForResult(intent, REQ_REEL_MUSIC);
        } catch (Exception e) {
            ToastUtil.showPill(this, getString(R.string.story_ed_music_failed));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_REEL_MUSIC && resultCode == RESULT_OK && data != null
                && data.getData() != null) {
            musicUri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(musicUri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
            }
            musicTitle = nameOf(musicUri);
            uploadedMusicUrl = null;
            if (tvMusicHint != null) {
                tvMusicHint.setText(getString(R.string.reel_music_hint, musicTitle));
            }
        }
    }

    private String nameOf(Uri uri) {
        String name = uri.getLastPathSegment();
        return name == null ? "track" : name;
    }

    /** Uploads the picked track, then continues on the UI thread. */
    private void uploadMusicThen(Runnable next) {
        if (musicUri == null || uploadedMusicUrl != null) {
            next.run();
            return;
        }
        byte[] bytes;
        String mime = getContentResolver().getType(musicUri);
        try (java.io.InputStream in = getContentResolver().openInputStream(musicUri);
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            if (in == null) throw new java.io.IOException("cannot open the track");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
            bytes = out.toByteArray();
        } catch (Exception e) {
            ToastUtil.showPill(this, getString(R.string.story_ed_music_failed));
            next.run();
            return;
        }
        String userId = SessionManager.getInstance(this).getUserId();
        String object = userId + "/music-" + UUID.randomUUID()
                + (mime != null && mime.contains("wav") ? ".wav" : ".m4a");
        RetrofitClient.getInstance(this).getStorageApi()
                .uploadFile("posts", object, RequestBody.create(
                        MediaType.parse(mime == null ? "audio/mp4" : mime), bytes))
                .enqueue(new Callback<Void>() {
                    @Override public void onResponse(Call<Void> call, Response<Void> response) {
                        if (response.isSuccessful()) {
                            uploadedMusicUrl = publicUrl(object);
                        } else {
                            ToastUtil.showPill(ReelEditActivity.this,
                                    getString(R.string.story_ed_music_failed));
                        }
                        next.run();
                    }

                    @Override public void onFailure(Call<Void> call, Throwable t) {
                        ToastUtil.showPill(ReelEditActivity.this,
                                getString(R.string.story_ed_music_failed));
                        next.run();
                    }
                });
    }

    private void publish() {
        if (publishing) return;
        String userId = SessionManager.getInstance(this).getUserId();
        if (userId == null || userId.isEmpty()) {
            ToastUtil.showPill(this, getString(R.string.reel_publish_failed));
            return;
        }
        publishing = true;
        setBusy(true);

        final String caption = etCaption.getText().toString().trim();
        final boolean alsoShare = switchAlsoShare.isChecked();
        final byte[] videoBytes;
        final byte[] coverBytes;
        try {
            videoBytes = readFile(new File(videoPath));
            coverBytes = cover == null ? null : jpegOf(cover);
        } catch (Exception e) {
            publishing = false;
            setBusy(false);
            ToastUtil.showPill(this, getString(R.string.reel_publish_failed));
            return;
        }
        if (videoBytes.length == 0) {
            publishing = false;
            setBusy(false);
            ToastUtil.showPill(this, getString(R.string.reel_publish_failed));
            return;
        }

        uploadMusicThen(() -> startClipUpload(userId, caption, alsoShare, videoBytes, coverBytes));
    }

    private void startClipUpload(String userId, String caption, boolean alsoShare,
                                 byte[] videoBytes, byte[] coverBytes) {
        RetrofitClient client = RetrofitClient.getInstance(this);
        StorageApi storageApi = client.getStorageApi();
        final com.insta.nova.data.NovaApi novaApi = client.getNovaApi();
        final String videoObject = userId + "/" + UUID.randomUUID() + ".mp4";
        storageApi.uploadFile("posts", videoObject,
                RequestBody.create(MediaType.parse("video/mp4"), videoBytes))
                .enqueue(new Callback<Void>() {
                    @Override public void onResponse(Call<Void> call, Response<Void> response) {
                        if (!response.isSuccessful()) {
                            failed("video HTTP " + response.code());
                            return;
                        }
                        final String videoUrl = publicUrl(videoObject);
                        if (coverBytes == null) {
                            createRows(novaApi, userId, caption, videoUrl, videoUrl, alsoShare);
                            return;
                        }
                        final String coverObject = userId + "/" + UUID.randomUUID() + ".jpg";
                        storageApi.uploadFile("posts", coverObject,
                                RequestBody.create(MediaType.parse("image/jpeg"), coverBytes))
                                .enqueue(new Callback<Void>() {
                                    @Override public void onResponse(Call<Void> c,
                                                                     Response<Void> r) {
                                        if (!r.isSuccessful()) {
                                            failed("cover HTTP " + r.code());
                                            return;
                                        }
                                        createRows(novaApi, userId, caption, videoUrl,
                                                publicUrl(coverObject), alsoShare);
                                    }

                                    @Override public void onFailure(Call<Void> c, Throwable t) {
                                        failed("cover " + t.getMessage());
                                    }
                                });
                    }

                    @Override public void onFailure(Call<Void> call, Throwable t) {
                        failed("video " + t.getMessage());
                    }
                });
    }

    /**
     * Writes the reel row, and the feed row too when the author asked for both.
     *
     * <p>Both go through create_instagram_post: it inserts the post_media rows and stamps
     * the owner itself, whereas a raw insert into posts has no media_urls column and came
     * back as HTTP 400.
     */
    private void createRows(@NonNull com.insta.nova.data.NovaApi novaApi,
                            @NonNull String userId, @NonNull String typedCaption,
                            @NonNull String videoUrl, @NonNull String coverUrl,
                            boolean alsoShare) {
        // Timed text and the volume levels have no column of their own: they ride at the end
        // of the caption and are stripped again by PostDto.toPost().
        java.util.Map<String, Object> meta = new java.util.HashMap<>();
        meta.put("elements", new java.util.ArrayList<>(textLines));
        meta.put("originalVolume", originalVolume);
        meta.put("musicVolume", musicVolume);
        meta.put("musicUrl", uploadedMusicUrl);
        meta.put("musicTitle", musicTitle);
        final String caption = com.insta.nova.util.ReelMetaCodec.encode(typedCaption, meta);

        Map<String, Object> reel = new HashMap<>();
        reel.put("p_caption", caption);
        reel.put("p_media_urls", new ArrayList<>(java.util.Collections.singletonList(coverUrl)));
        reel.put("p_is_reel", true);
        reel.put("p_video_url", videoUrl);
        reel.put("p_thumbnail_url", coverUrl);
        reel.put("p_is_anonymous", false);
        reel.put("p_expires_at", null);
        reel.put("p_duration_seconds", totalMs > 0 ? (int) (totalMs / 1000) : null);
        reel.put("p_hide_like_count", false);
        reel.put("p_comments_disabled", false);
        reel.put("p_location", null);
        reel.put("p_music_url", uploadedMusicUrl);
        reel.put("p_music_title", musicTitle);

        novaApi.createInstagramPost(reel).enqueue(new Callback<com.google.gson.JsonElement>() {
            @Override public void onResponse(Call<com.google.gson.JsonElement> call,
                                             Response<com.google.gson.JsonElement> response) {
                if (!response.isSuccessful()) {
                    failed("reel row HTTP " + response.code());
                    return;
                }
                saveEditorMeta(idOf(response.body()));
                rememberLocally(userId, caption, videoUrl, coverUrl);
                if (!alsoShare) {
                    done();
                    return;
                }
                Map<String, Object> feedCopy = new HashMap<>(reel);
                feedCopy.put("p_is_reel", false);
                novaApi.createInstagramPost(feedCopy)
                        .enqueue(new Callback<com.google.gson.JsonElement>() {
                            @Override public void onResponse(
                                    Call<com.google.gson.JsonElement> c,
                                    Response<com.google.gson.JsonElement> r) {
                                if (r.isSuccessful()) {
                                    saveEditorMeta(idOf(r.body()));
                                    done();
                                } else {
                                    failed("feed row HTTP " + r.code());
                                }
                            }

                            @Override public void onFailure(
                                    Call<com.google.gson.JsonElement> c, Throwable t) {
                                failed("feed row " + t.getMessage());
                            }
                        });
            }

            @Override public void onFailure(Call<com.google.gson.JsonElement> call,
                                            Throwable t) {
                failed("reel row " + t.getMessage());
            }
        });
    }

    /**
     * Writes the clip's editor state next to the post.
     *
     * <p>Timed text and the two volume levels need somewhere to live, and the Supabase
     * {@code posts} table has no jsonb column, so they ride in the Firestore post document the
     * app already keeps. The clip is untouched — nothing is re-encoded.
     */
    private void saveEditorMeta(@Nullable String postId) {
        if (postId == null || postId.isEmpty()) return;
        java.util.Map<String, Object> meta = new java.util.HashMap<>();
        meta.put("elements", new java.util.ArrayList<>(textLines));
        meta.put("originalVolume", originalVolume);
        meta.put("musicVolume", musicVolume);
        meta.put("musicUrl", uploadedMusicUrl);
        meta.put("musicTitle", musicTitle);
        String uid = SessionManager.getInstance(this).getUserId();
        com.insta.nova.data.reel.ReelMetaStore.save(postId, uid, meta);
    }

    private String idOf(@Nullable com.google.gson.JsonElement body) {
        try {
            if (body != null && body.isJsonObject()) {
                com.google.gson.JsonElement id = body.getAsJsonObject().get("id");
                if (id != null && !id.isJsonNull()) return id.getAsString();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** The local reel record keeps the Reels pager showing it before the next fetch. */
    private void rememberLocally(String userId, String caption, String videoUrl,
                                 String coverUrl) {
        SessionManager session = SessionManager.getInstance(this);
        String username = session.getUsername();
        if (username == null || username.trim().isEmpty()) username = "you";
        ArrayList<String> media = new ArrayList<>();
        media.add(coverUrl);
        Post reel = new Post("local_reel_" + UUID.randomUUID(), userId, username,
                session.getAvatarUrl(), false, caption, "", media, "reel", 0, 0,
                false, false, System.currentTimeMillis());
        new LocalSocialStore(this).addReel(reel);
    }

    private void done() {
        publishing = false;
        setBusy(false);
        ToastUtil.showPill(this, getString(R.string.reel_shared));
        setResult(RESULT_OK);
        finish();
    }

    private void failed() {
        failed("unknown");
    }

    private void failed(String detail) {
        publishing = false;
        setBusy(false);
        Log.e(TAG, "publish failed: " + detail);
        ToastUtil.showPill(this, getString(R.string.reel_publish_failed));
    }

    private void setBusy(boolean busy) {
        if (switchAlsoShare != null) switchAlsoShare.setEnabled(!busy);
        if (etCaption != null) etCaption.setEnabled(!busy);
        View share = findViewById(R.id.btn_reel_share);
        if (share != null) {
            share.setAlpha(busy ? 0.5f : 1f);
            share.setEnabled(!busy);
        }
    }

    // ------------------------------------------------------------------------------- cover

    private void openCoverPicker() {
        if (totalMs <= 0) return;
        final int samples = 6;
        final Bitmap[] frames = new Bitmap[samples];
        final long[] times = new long[samples];
        new Thread(() -> {
            File file = new File(videoPath);
            for (int i = 0; i < samples; i++) {
                times[i] = totalMs * i / samples;
                frames[i] = VideoConcat.frameAt(file, times[i]);
            }
            handler.post(() -> showCoverDialog(frames, times));
        }).start();
    }

    private void showCoverDialog(Bitmap[] frames, long[] times) {
        if (isFinishing() || isDestroyed()) return;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(8), dp(8), dp(8), dp(8));
        final AlertDialog[] dialogRef = new AlertDialog[1];
        for (int i = 0; i < frames.length; i++) {
            ImageView thumb = new ImageView(this);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            if (frames[i] != null) thumb.setImageBitmap(frames[i]);
            final int index = i;
            thumb.setOnClickListener(v -> {
                if (frames[index] != null) setCover(frames[index], times[index]);
                if (dialogRef[0] != null) dialogRef[0].dismiss();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(120), 1f);
            lp.rightMargin = dp(4);
            row.addView(thumb, lp);
        }
        dialogRef[0] = new AlertDialog.Builder(this)
                .setTitle(R.string.reel_cover_title)
                .setView(row)
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void setCover(@NonNull Bitmap bitmap, long atMs) {
        cover = bitmap;
        coverMs = atMs;
        tvCoverHint.setText(getString(R.string.reel_cover_selected, (atMs / 1000) % 60));
    }

    // --------------------------------------------------------------------------- preview

    private void preparePreview() {
        videoView.setVideoURI(Uri.fromFile(new File(videoPath)));
        videoView.setOnPreparedListener(mp -> {
            mp.setLooping(true);
            videoView.start();
        });
        videoView.setOnClickListener(v -> {
            if (videoView.isPlaying()) videoView.pause(); else videoView.start();
        });
    }

    private void buildClipStrip() {
        clipStrip.removeAllViews();
        if (clipPaths.isEmpty()) {
            clipStrip.setVisibility(View.GONE);
            return;
        }
        long startMs = 0;
        for (String path : clipPaths) {
            File file = new File(path);
            final long seekTo = startMs;
            long duration = VideoConcat.durationMs(file);
            startMs += duration;

            FrameLayout cell = new FrameLayout(this);
            ImageView frame = new ImageView(this);
            frame.setScaleType(ImageView.ScaleType.CENTER_CROP);
            cell.addView(frame, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            TextView label = new TextView(this);
            label.setText(String.format(java.util.Locale.US, "%.1fs", duration / 1000f));
            label.setTextColor(Color.WHITE);
            label.setTextSize(10);
            label.setShadowLayer(4f, 0f, 0f, 0xCC000000);
            FrameLayout.LayoutParams labelLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            labelLp.gravity = Gravity.BOTTOM | Gravity.END;
            labelLp.setMargins(0, 0, dp(4), dp(2));
            cell.addView(label, labelLp);

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0x22FFFFFF);
            bg.setCornerRadius(dp(4));
            cell.setBackground(bg);
            cell.setOnClickListener(v -> {
                videoView.seekTo((int) seekTo);
                videoView.start();
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(52), dp(68));
            lp.rightMargin = dp(6);
            clipStrip.addView(cell, lp);

            new Thread(() -> {
                Bitmap thumb = VideoConcat.frameAt(file, Math.min(300, duration / 3));
                handler.post(() -> {
                    if (thumb != null) frame.setImageBitmap(thumb);
                });
            }).start();
        }
    }

    // ------------------------------------------------------------------------------ misc

    private static byte[] readFile(File file) throws IOException {
        try (InputStream in = new java.io.FileInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            return out.toByteArray();
        }
    }

    private static byte[] jpegOf(Bitmap bitmap) {
        int max = 1080;
        Bitmap scaled = bitmap;
        if (bitmap.getWidth() > max) {
            int height = Math.round(bitmap.getHeight() * (max / (float) bitmap.getWidth()));
            scaled = Bitmap.createScaledBitmap(bitmap, max, height, true);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        scaled.compress(Bitmap.CompressFormat.JPEG, 88, out);
        return out.toByteArray();
    }

    private String publicUrl(String objectPath) {
        String base = BuildConfig.SUPABASE_URL == null ? "" : BuildConfig.SUPABASE_URL.trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + "/storage/v1/object/public/posts/" + objectPath;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        // 9:16 preview at the top, cropped the same way the reel player crops.
        FrameLayout previewSlot = new FrameLayout(this);
        previewSlot.setBackgroundColor(Color.BLACK);
        LinearLayout.LayoutParams slotLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(previewSlot, slotLp);

        videoView = new VideoView(this);
        previewSlot.addView(videoView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), dp(8), dp(8), dp(8));

        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.ic_arrow_back);
        back.setContentDescription(getString(R.string.reel_cam_close));
        back.setPadding(dp(8), dp(8), dp(8), dp(8));
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(dp(40), dp(40)));

        TextView coverBtn = new TextView(this);
        coverBtn.setText(R.string.reel_cover_action);
        coverBtn.setTextColor(Color.WHITE);
        coverBtn.setTextSize(13);
        coverBtn.setGravity(Gravity.CENTER);
        GradientDrawable coverBg = new GradientDrawable();
        coverBg.setColor(0x66000000);
        coverBg.setCornerRadius(dp(16));
        coverBtn.setBackground(coverBg);
        coverBtn.setPadding(dp(12), dp(6), dp(12), dp(6));
        coverBtn.setOnClickListener(v -> openCoverPicker());
        top.addView(coverBtn);

        tvCoverHint = new TextView(this);
        tvCoverHint.setTextColor(0xFFB0B0B0);
        tvCoverHint.setTextSize(11);
        tvCoverHint.setPadding(dp(8), 0, 0, 0);
        top.addView(tvCoverHint);

        TextView textBtn = new TextView(this);
        textBtn.setText(R.string.reel_text_action);
        textBtn.setTextColor(Color.WHITE);
        textBtn.setTextSize(13);
        textBtn.setGravity(Gravity.CENTER);
        GradientDrawable textBg = new GradientDrawable();
        textBg.setColor(0x66000000);
        textBg.setCornerRadius(dp(16));
        textBtn.setBackground(textBg);
        textBtn.setPadding(dp(12), dp(6), dp(12), dp(6));
        textBtn.setOnClickListener(v -> promptTimedText());
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textLp.leftMargin = dp(6);
        top.addView(textBtn, textLp);

        TextView musicBtn = new TextView(this);
        musicBtn.setText(R.string.reel_music_action);
        musicBtn.setTextColor(Color.WHITE);
        musicBtn.setTextSize(13);
        musicBtn.setGravity(Gravity.CENTER);
        GradientDrawable musicBg = new GradientDrawable();
        musicBg.setColor(0x66000000);
        musicBg.setCornerRadius(dp(16));
        musicBtn.setBackground(musicBg);
        musicBtn.setPadding(dp(12), dp(6), dp(12), dp(6));
        musicBtn.setOnClickListener(v -> pickMusic());
        LinearLayout.LayoutParams musicLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        musicLp.leftMargin = dp(6);
        top.addView(musicBtn, musicLp);

        previewSlot.addView(top, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Controls under the preview.
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(12), dp(16), dp(20));

        clipStrip = new LinearLayout(this);
        clipStrip.setOrientation(LinearLayout.HORIZONTAL);
        panel.addView(clipStrip, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(68)));

        textList = new LinearLayout(this);
        textList.setOrientation(LinearLayout.VERTICAL);
        panel.addView(textList);

        LinearLayout originalRow = new LinearLayout(this);
        originalRow.setOrientation(LinearLayout.HORIZONTAL);
        originalRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView originalLabel = new TextView(this);
        originalLabel.setText(R.string.reel_original_volume);
        originalLabel.setTextColor(0xFFB0B0B0);
        originalLabel.setTextSize(12);
        originalRow.addView(originalLabel, new LinearLayout.LayoutParams(dp(72),
                ViewGroup.LayoutParams.WRAP_CONTENT));
        android.widget.SeekBar originalBar = new android.widget.SeekBar(this);
        originalBar.setMax(100);
        originalBar.setProgress(Math.round(originalVolume * 100));
        originalBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar sb, int value, boolean fromUser) {
                originalVolume = Math.max(0f, Math.min(1f, value / 100f));
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar sb) { }
            @Override public void onStopTrackingTouch(android.widget.SeekBar sb) { }
        });
        originalRow.addView(originalBar, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        panel.addView(originalRow);

        LinearLayout musicRow = new LinearLayout(this);
        musicRow.setOrientation(LinearLayout.HORIZONTAL);
        musicRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView musicLabel = new TextView(this);
        musicLabel.setText(R.string.reel_music_volume);
        musicLabel.setTextColor(0xFFB0B0B0);
        musicLabel.setTextSize(12);
        musicRow.addView(musicLabel, new LinearLayout.LayoutParams(dp(72),
                ViewGroup.LayoutParams.WRAP_CONTENT));
        android.widget.SeekBar musicBar = new android.widget.SeekBar(this);
        musicBar.setMax(100);
        musicBar.setProgress(Math.round(musicVolume * 100));
        musicBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar sb, int value, boolean fromUser) {
                musicVolume = Math.max(0f, Math.min(1f, value / 100f));
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar sb) { }
            @Override public void onStopTrackingTouch(android.widget.SeekBar sb) { }
        });
        musicRow.addView(musicBar, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        panel.addView(musicRow);

        tvMusicHint = new TextView(this);
        tvMusicHint.setTextColor(0xFFB0B0B0);
        tvMusicHint.setTextSize(11);
        panel.addView(tvMusicHint);

        etCaption = new EditText(this);
        etCaption.setHint(R.string.reel_caption_hint);
        etCaption.setTextColor(Color.WHITE);
        etCaption.setHintTextColor(0xFF888888);
        etCaption.setTextSize(14);
        etCaption.setMaxLines(3);
        LinearLayout.LayoutParams capLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        capLp.topMargin = dp(10);
        panel.addView(etCaption, capLp);

        switchAlsoShare = new Switch(this);
        switchAlsoShare.setText(R.string.reel_also_share);
        switchAlsoShare.setTextColor(Color.WHITE);
        switchAlsoShare.setTextSize(14);
        LinearLayout.LayoutParams shareLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        shareLp.topMargin = dp(8);
        panel.addView(switchAlsoShare, shareLp);

        TextView share = new TextView(this);
        share.setId(R.id.btn_reel_share);
        share.setText(R.string.reel_share_action);
        share.setTextColor(Color.WHITE);
        share.setTextSize(15);
        share.setGravity(Gravity.CENTER);
        GradientDrawable shareBg = new GradientDrawable();
        shareBg.setColor(0xFF0095F6);
        shareBg.setCornerRadius(dp(20));
        share.setBackground(shareBg);
        share.setOnClickListener(v -> publish());
        LinearLayout.LayoutParams shareBtnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        shareBtnLp.topMargin = dp(14);
        panel.addView(share, shareBtnLp);

        root.addView(panel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        buildClipStrip();
    }

    @Override
    protected void onPause() {
        if (videoView != null) videoView.pause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        try {
            videoView.stopPlayback();
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }
}
