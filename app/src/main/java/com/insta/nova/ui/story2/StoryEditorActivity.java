package com.insta.nova.ui.story2;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.insta.nova.BuildConfig;
import com.insta.nova.R;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.remote.RetrofitClient;
import com.insta.nova.data.remote.api.RestApi;
import com.insta.nova.data.remote.api.StorageApi;
import com.insta.nova.data.remote.dto.StoryDto;
import com.insta.nova.util.MediaUrl;
import com.insta.nova.util.ToastUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Story editor: the hub between capture/pick and publish.
 *
 * <p>Photo segments are flattened to a 1080x1920 JPEG on publish, which is why everything
 * the user sees (filter, drawing, text) can live in one bitmap pipeline. Video segments keep
 * their original file — the trim range and mute flag travel in {@code editor_meta} and the
 * drawing layer is exported as a transparent PNG.
 */
public class StoryEditorActivity extends AppCompatActivity {

    private static final String TAG = "StoryEditor";
    private static final String EXTRA_SEGMENTS = "segments";
    private static final int OUT_W = 1080;
    private static final int OUT_H = 1920;

    public static void start(Context context, List<String> segmentUris) {
        Intent i = new Intent(context, StoryEditorActivity.class);
        i.putStringArrayListExtra(EXTRA_SEGMENTS, new ArrayList<>(segmentUris));
        context.startActivity(i);
    }

    public static void startCreate(Context context) {
        Intent i = new Intent(context, StoryEditorActivity.class);
        i.putStringArrayListExtra(EXTRA_SEGMENTS, new ArrayList<>());
        context.startActivity(i);
    }

    /* ------------------------------------------------------------------ */
    /* Model                                                               */
    /* ------------------------------------------------------------------ */

    static final class El {
        String text = "";
        float cx = 0.5f, cy = 0.4f;
        float scale = 1f, rot = 0f;
        int color = Color.WHITE;
        int bg;               // 0 none, 1 solid, 2 translucent
        int font;             // 0..4
        int sp = 22;
        /** Bundled GIF asset name, null for a text element. */
        String gif;
        View view;
    }

    private final class Segment {
        String uri;            // null for the Create-mode gradient canvas
        boolean video;
        boolean fit;
        int filter;
        long trimStart, trimEnd;
        boolean muted;
        final List<El> elements = new ArrayList<>();
        final List<Stroke> strokes = new ArrayList<>();
        int gradientIndex;
        /** 2-4 photos laid out as one 9:16 collage; empty for a normal single-photo segment. */
        final List<android.graphics.Bitmap> collage = new ArrayList<>();
        String pollQuestion;
        final List<String> pollOptions = new ArrayList<>();
    }

    private static final class Stroke {
        final Path path = new Path();
        int color;
        float width;
        int tool;              // 0 pen, 1 marker, 2 neon, 3 eraser
    }

    /* ------------------------------------------------------------------ */
    /* Views                                                               */
    /* ------------------------------------------------------------------ */

    private FrameLayout canvas;
    private FrameLayout rootView;
    private FrameLayout stage;          // exactly 9:16, this is what gets baked
    private ImageView backdrop;
    private ImageView media;
    private VideoView video;
    private DrawLayer drawLayer;
    private FrameLayout elementHost;
    private LinearLayout filmstrip;
    private LinearLayout trimBar;
    private TextView filterToast;
    private View trash;
    private TextView pollPreview;
    private TextView nextButton;
    private TextView shuffleButton;
    private CollageView collageView;
    private static final int REQ_STORY_LAYOUT = 6411;
    private static final int REQ_STORY_PHOTO_STICKER = 6412;
    /** Track chosen for this story; uploaded once, before the first segment is published. */
    private Uri musicUri;
    private String musicTitle;
    private String uploadedStoryMusicUrl;
    private static final int REQ_STORY_MUSIC = 6410;

    private final List<Segment> segments = new ArrayList<>();
    private int current = 0;
    private boolean busy;
    private String overlayError = "none";
    private boolean shareAfterPublish;
    private String firstStoryUrl;
    private boolean firstStoryVideo;
    private String firstStoryId;
    private android.media.MediaPlayer storyPlayer;

    private final Handler handler = new Handler(Looper.getMainLooper());

    /* ------------------------------------------------------------------ */
    /* Lifecycle                                                           */
    /* ------------------------------------------------------------------ */

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ArrayList<String> uris = getIntent().getStringArrayListExtra(EXTRA_SEGMENTS);
        if (uris == null || uris.isEmpty()) {
            Segment s = new Segment();
            s.gradientIndex = 0;
            segments.add(s);
        } else {
            for (String u : uris) {
                Segment s = new Segment();
                s.uri = u;
                s.video = isVideoUri(u);
                s.trimStart = 0;
                s.trimEnd = s.video ? probeDuration(u) : 0;
                segments.add(s);
            }
        }
        buildUi();
        showSegment(0);
        if (segments.get(0).uri == null) {
            // Create mode opens straight into the text tool on the gradient.
            handler.postDelayed(this::openTextTool, 350);
        }
    }

    /* ------------------------------------------------------------------ */
    /* UI                                                                  */
    /* ------------------------------------------------------------------ */

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        rootView = root;

        canvas = new FrameLayout(this);
        root.addView(canvas, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        stage = new FrameLayout(this);
        stage.setBackgroundColor(Color.BLACK);

        backdrop = new ImageView(this);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setVisibility(View.GONE);
        stage.addView(backdrop, match());

        media = new ImageView(this);
        media.setScaleType(ImageView.ScaleType.CENTER_CROP);
        stage.addView(media, match());

        // Two-finger resize (and one-finger reposition) for the photo. The stage is drawn into
        // the exported bitmap with stage.draw(), so these view transforms are baked for free.
        installBaseGestures(media);

        video = new VideoView(this);
        video.setVisibility(View.GONE);
        stage.addView(video, match());

        drawLayer = new DrawLayer(this);
        stage.addView(drawLayer, match());

        elementHost = new FrameLayout(this);
        stage.addView(elementHost, match());

        FrameLayout.LayoutParams stageLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        stageLp.gravity = Gravity.CENTER;
        canvas.addView(stage, stageLp);

        canvas.post(() -> layoutStage());

        // top bar
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(14), dp(36), dp(14), 0);
        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        topLp.gravity = Gravity.TOP;

        TextView close = glyph("✕", 24);
        close.setContentDescription(getString(R.string.story_ed_close));
        close.setOnClickListener(v -> confirmDiscard());
        top.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));
        View sp = new View(this);
        top.addView(sp, new LinearLayout.LayoutParams(0, 1, 1f));
        TextView fitToggle = glyph("⤢", 20);
        fitToggle.setContentDescription(getString(R.string.story_ed_fit));
        fitToggle.setOnClickListener(v -> toggleFit());
        FrameLayout.LayoutParams fitLp = new FrameLayout.LayoutParams(dp(44), dp(44));
        fitLp.gravity = Gravity.TOP | Gravity.END;
        fitLp.topMargin = dp(36);
        fitLp.rightMargin = dp(14);
        root.addView(fitToggle, fitLp);

        root.addView(top, topLp);

        // right tool column
        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.VERTICAL);
        tools.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams toolsLp = new FrameLayout.LayoutParams(
                dp(74), ViewGroup.LayoutParams.WRAP_CONTENT);
        toolsLp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        toolsLp.rightMargin = dp(8);

        tools.addView(toolButton("Aa", R.string.story_ed_text, v -> openTextTool()));
        tools.addView(toolButton(getString(R.string.story_ed_sticker), R.string.story_ed_sticker,
                v -> openStickerTray()));
        tools.addView(toolButton(getString(R.string.story_ed_draw), R.string.story_ed_draw,
                v -> openDrawPanel()));
        tools.addView(toolButton(getString(R.string.story_ed_filters), R.string.story_ed_filters,
                v -> cycleFilter()));
        // Only the gradient canvas has a background to shuffle; a photo or clip keeps its own.
        shuffleButton = toolButton(getString(R.string.story_ed_shuffle),
                R.string.story_ed_shuffle, v -> shuffleGradient());
        tools.addView(shuffleButton);
        tools.addView(toolButton(getString(R.string.story_ed_music),
                R.string.story_ed_music, v -> pickStoryMusic()));
        tools.addView(toolButton(getString(R.string.story_ed_layout),
                R.string.story_ed_layout, v -> pickStoryLayout()));
        tools.addView(toolButton("•••", R.string.story_ed_more, v -> openMoreMenu()));

        root.addView(tools, toolsLp);

        filterToast = glyph("", 20);
        filterToast.setAlpha(0f);
        FrameLayout.LayoutParams ftLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ftLp.gravity = Gravity.CENTER;
        root.addView(filterToast, ftLp);

        trash = new View(this);
        GradientDrawable trashBg = new GradientDrawable();
        trashBg.setShape(GradientDrawable.OVAL);
        trashBg.setColor(0xCC000000);
        trash.setBackground(trashBg);
        trash.setAlpha(0f);
        FrameLayout.LayoutParams trashLp = new FrameLayout.LayoutParams(dp(56), dp(56));
        trashLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        trashLp.bottomMargin = dp(150);
        root.addView(trash, trashLp);

        // bottom: filmstrip, trim bar, action row
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setGravity(Gravity.CENTER_HORIZONTAL);
        bottom.setPadding(0, 0, 0, dp(20));
        FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bottomLp.gravity = Gravity.BOTTOM;

        filmstrip = new LinearLayout(this);
        filmstrip.setOrientation(LinearLayout.HORIZONTAL);
        filmstrip.setVisibility(View.GONE);
        bottom.addView(filmstrip);

        trimBar = new LinearLayout(this);
        trimBar.setOrientation(LinearLayout.VERTICAL);
        trimBar.setVisibility(View.GONE);
        trimBar.setPadding(dp(16), dp(6), dp(16), dp(6));
        bottom.addView(trimBar);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(dp(12), dp(8), dp(12), 0);

        TextView yourStory = pill(getString(R.string.story_ed_your_story), 0xFF1E88FF);
        yourStory.setOnClickListener(v -> publish("public"));
        actions.addView(yourStory, new LinearLayout.LayoutParams(0, dp(44), 1f));

        TextView cf = pill(getString(R.string.story_ed_close_friends), 0x33FFFFFF);
        LinearLayout.LayoutParams cfLp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        cfLp.leftMargin = dp(8);
        cf.setOnClickListener(v -> publish("close_friends"));
        actions.addView(cf, cfLp);

        nextButton = pill("›", 0xFF1E88FF);
        LinearLayout.LayoutParams nextLp = new LinearLayout.LayoutParams(dp(48), dp(44));
        nextLp.leftMargin = dp(8);
        nextButton.setOnClickListener(v -> openShareSheet());
        actions.addView(nextButton, nextLp);

        bottom.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(bottom, bottomLp);

        setContentView(root);
    }

    private void layoutStage() {
        int w = canvas.getWidth();
        int h = canvas.getHeight();
        if (w <= 0 || h <= 0) return;
        int targetW = w;
        int targetH = (int) (w * 16f / 9f);
        if (targetH > h) {
            targetH = h;
            targetW = (int) (h * 9f / 16f);
        }
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) stage.getLayoutParams();
        lp.width = targetW;
        lp.height = targetH;
        stage.setLayoutParams(lp);
    }

    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private TextView glyph(String text, int sp) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(sp);
        tv.setTextColor(Color.WHITE);
        tv.setGravity(Gravity.CENTER);
        tv.setShadowLayer(6f, 0, 0, 0x99000000);
        return tv;
    }

    private TextView toolButton(String glyph, int descRes, View.OnClickListener click) {
        TextView tv = glyph(glyph, 15);
        tv.setSingleLine(true);
        tv.setContentDescription(getString(descRes));
        tv.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(44));
        lp.topMargin = dp(10);
        tv.setLayoutParams(lp);
        return tv;
    }

    private TextView pill(String label, int color) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(14);
        tv.setTextColor(Color.WHITE);
        tv.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(22));
        bg.setColor(color);
        tv.setBackground(bg);
        return tv;
    }

    /* ------------------------------------------------------------------ */
    /* Segment rendering                                                   */
    /* ------------------------------------------------------------------ */

    private Segment seg() {
        return segments.get(current);
    }

    private void showSegment(int index) {
        current = Math.max(0, Math.min(index, segments.size() - 1));
        Segment s = seg();

        elementHost.removeAllViews();
        for (El el : s.elements) attachElement(el);

        drawLayer.setStrokes(s.strokes);
        applyFilter(s.filter, s.uri != null);
        nextButton.setAlpha(s.uri == null ? 0.4f : 1f);
        if (shuffleButton != null) {
            shuffleButton.setVisibility(s.uri == null ? View.VISIBLE : View.GONE);
        }

        if (!s.collage.isEmpty()) {
            media.setVisibility(View.GONE);
            video.setVisibility(View.GONE);
            backdrop.setVisibility(View.GONE);
            stage.setBackgroundColor(0xFF000000);
            if (collageView == null) {
                collageView = new CollageView(this);
                stage.addView(collageView, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
            }
            collageView.setVisibility(View.VISIBLE);
            collageView.setPhotos(s.collage);
            nextButton.setAlpha(1f);
        } else if (s.uri == null) {
            if (collageView != null) collageView.setVisibility(View.GONE);
            media.setVisibility(View.GONE);
            video.setVisibility(View.GONE);
            backdrop.setVisibility(View.GONE);
            stage.setBackground(gradientDrawable(s.gradientIndex));
        } else if (s.video) {
            media.setVisibility(View.GONE);
            backdrop.setVisibility(View.GONE);
            video.setVisibility(View.VISIBLE);
            video.setVideoURI(Uri.parse(s.uri));
            video.setOnPreparedListener(mp -> {
                storyPlayer = mp;
                mp.setLooping(true);
                mp.setVolume(s.muted ? 0f : 1f, s.muted ? 0f : 1f);
                video.seekTo((int) s.trimStart);
                video.start();
            });
        } else {
            if (collageView != null) collageView.setVisibility(View.GONE);
            video.setVisibility(View.GONE);
            media.setVisibility(View.VISIBLE);
            loadMediaInto(media, s.uri);
            applyFit(s);
        }

        showPollPreview(s);
        buildTrimBar(s);
        buildFilmstrip();
    }

    /**
     * The expand control from the spec: FILL crops the media to 9:16, FIT shows the whole
     * frame over a blurred copy of itself. The choice is per segment and is baked into the
     * published image.
     */
    private void toggleFit() {
        Segment s = seg();
        if (s.video) {
            Toast.makeText(this, R.string.story_ed_fit_photo_only, Toast.LENGTH_SHORT).show();
            return;
        }
        if (s.uri == null) return;
        s.fit = !s.fit;
        applyFit(s);
    }

    private void applyFit(Segment s) {
        media.setScaleType(s.fit ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_CROP);
        if (s.fit) {
            backdrop.setVisibility(View.VISIBLE);
            blurInto(backdrop, s.uri);
        } else {
            backdrop.setVisibility(View.GONE);
        }
    }

    private void loadMediaInto(ImageView target, String uriString) {
        try {
            Glide.with(this).asBitmap().load(Uri.parse(uriString)).into(new CustomTarget<Bitmap>() {
                @Override public void onResourceReady(@NonNull Bitmap resource,
                                                     @Nullable Transition<? super Bitmap> t) {
                    target.setImageBitmap(resource);
                }

                @Override public void onLoadCleared(@Nullable android.graphics.drawable.Drawable p) { }
            });
        } catch (Exception e) {
            Log.e(TAG, "media load failed: " + e.getMessage(), e);
        }
    }

    /** Downscale/upscale blur: cheap, real, and keeps the fill behind a FIT photo soft. */
    private void blurInto(ImageView target, String uriString) {
        try {
            Glide.with(this).asBitmap().load(Uri.parse(uriString)).into(new CustomTarget<Bitmap>() {
                @Override public void onResourceReady(@NonNull Bitmap resource,
                                                     @Nullable Transition<? super Bitmap> t) {
                    int w = Math.max(24, resource.getWidth() / 24);
                    int h = Math.max(24, resource.getHeight() / 24);
                    Bitmap small = Bitmap.createScaledBitmap(resource, w, h, true);
                    Bitmap blurred = Bitmap.createScaledBitmap(small,
                            resource.getWidth(), resource.getHeight(), true);
                    target.setImageBitmap(blurred);
                }

                @Override public void onLoadCleared(@Nullable android.graphics.drawable.Drawable p) { }
            });
        } catch (Exception e) {
            Log.w(TAG, "blur failed: " + e.getMessage());
        }
    }

    /* ------------------------------------------------------------------ */
    /* Filters                                                             */
    /* ------------------------------------------------------------------ */

    private static final String[] FILTER_NAMES = {
            "Normal", "Paris", "Oslo", "Tokyo", "Noir", "Vivid", "Warm"};

    private static final float[][] FILTER_MATRICES = {
            null,
            {1.10f, 0, 0, 0, 12, 0, 1.02f, 0, 0, 4, 0, 0, 1.06f, 0, 18, 0, 0, 0, 1, 0},
            {0.98f, 0, 0, 0, 6, 0, 1.00f, 0, 0, 10, 0, 0, 1.10f, 0, 16, 0, 0, 0, 1, 0},
            {1.06f, 0, 0, 0, 10, 0, 1.06f, 0, 0, 0, 0, 0, 0.94f, 0, 14, 0, 0, 0, 1, 0},
            {0.30f, 0.59f, 0.11f, 0, 0, 0.30f, 0.59f, 0.11f, 0, 0, 0.30f, 0.59f, 0.11f, 0, 0, 0, 0, 0, 1, 0},
            {1.25f, 0, 0, 0, -18, 0, 1.25f, 0, 0, -18, 0, 0, 1.25f, 0, -18, 0, 0, 0, 1, 0},
            {1.14f, 0, 0, 0, 16, 0, 1.04f, 0, 0, 6, 0, 0, 0.92f, 0, -6, 0, 0, 0, 1, 0},
    };

    private void cycleFilter() {
        Segment s = seg();
        if (s.video) {
            // Real-time video filtering would need a GL pass; rather than fake it the
            // control reports that it is photo-only.
            Toast.makeText(this, R.string.story_ed_filter_photo_only, Toast.LENGTH_SHORT).show();
            return;
        }
        s.filter = (s.filter + 1) % FILTER_NAMES.length;
        applyFilter(s.filter, true);
        flashFilterName(FILTER_NAMES[s.filter]);
    }

    private void applyFilter(int index, boolean hasMedia) {
        if (!hasMedia || index < 0 || index >= FILTER_MATRICES.length
                || FILTER_MATRICES[index] == null) {
            media.clearColorFilter();
            return;
        }
        ColorMatrix cm = new ColorMatrix(FILTER_MATRICES[index]);
        media.setColorFilter(new ColorMatrixColorFilter(cm));
    }

    private void flashFilterName(String name) {
        filterToast.setText(name);
        filterToast.animate().alpha(1f).setDuration(120).withEndAction(() ->
                filterToast.animate().alpha(0f).setDuration(400)
                        .setStartDelay(600).start()).start();
    }

    /* ------------------------------------------------------------------ */
    /* Text tool                                                           */
    /* ------------------------------------------------------------------ */

    private static final int[] PALETTE = {
            0xFFFFFFFF, 0xFF000000, 0xFFFF3B30, 0xFFFF9500, 0xFFFFCC00, 0xFF34C759,
            0xFF00C7BE, 0xFF32ADE6, 0xFF5856D6, 0xFFAF52DE, 0xFFFF2D55, 0xFF8E8E93};

    private static final String[] FONT_LABELS = {"Strong", "Modern", "Neon", "Type", "Classic"};

    private int pendingColor = Color.WHITE;
    private int pendingFont = 0;
    private int pendingBg = 0;

    private void openTextTool() {
        Segment s = seg();
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(0xF2000000);
        panel.setPadding(dp(12), dp(40), dp(12), dp(12));

        LinearLayout topRow = new LinearLayout(this);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView bgBtn = glyph("A", 18);
        bgBtn.setContentDescription(getString(R.string.story_ed_text_bg));
        bgBtn.setBackgroundColor(0x33FFFFFF);
        topRow.addView(bgBtn, new LinearLayout.LayoutParams(dp(40), dp(40)));

        View spacer = new View(this);
        topRow.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        TextView done = glyph("Done", 15);
        topRow.addView(done, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)));
        panel.addView(topRow);

        EditText input = new EditText(this);
        input.setHint(R.string.story_ed_text_hint);
        input.setHintTextColor(0x88FFFFFF);
        input.setTextColor(Color.WHITE);
        input.setTextSize(22);
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        panel.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // font carousel
        HorizontalScrollView fontScroll = new HorizontalScrollView(this);
        LinearLayout fonts = new LinearLayout(this);
        fonts.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < FONT_LABELS.length; i++) {
            final int idx = i;
            TextView chip = glyph(FONT_LABELS[i], 14);
            chip.setTypeface(fontFor(i));
            chip.setPadding(dp(14), dp(8), dp(14), dp(8));
            chip.setBackgroundColor(i == pendingFont ? 0x33FFFFFF : Color.TRANSPARENT);
            chip.setOnClickListener(v -> {
                pendingFont = idx;
                applyInputStyle(input, bgBtn);
                for (int k = 0; k < fonts.getChildCount(); k++) {
                    fonts.getChildAt(k).setBackgroundColor(
                            k == idx ? 0x33FFFFFF : Color.TRANSPARENT);
                }
                input.setTypeface(fontFor(idx));
            });
            fonts.addView(chip);
        }
        fontScroll.addView(fonts);
        panel.addView(fontScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // palette
        HorizontalScrollView colorScroll = new HorizontalScrollView(this);
        LinearLayout colors = new LinearLayout(this);
        colors.setOrientation(LinearLayout.HORIZONTAL);
        for (int color : PALETTE) {
            View dot = new View(this);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(color);
            bg.setStroke(dp(1), 0x55FFFFFF);
            dot.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(30), dp(30));
            lp.setMargins(dp(5), dp(10), dp(5), dp(10));
            dot.setOnClickListener(v -> {
                pendingColor = color;
                input.setTextColor(color);
                applyInputStyle(input, bgBtn);
            });
            colors.addView(dot, lp);
        }
        colorScroll.addView(colors);
        panel.addView(colorScroll);

        bgBtn.setOnClickListener(v -> {
            pendingBg = (pendingBg + 1) % 3;
            applyInputStyle(input, bgBtn);
        });
        applyInputStyle(input, bgBtn);

        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        panelLp.gravity = Gravity.BOTTOM;
        rootView.addView(panel, panelLp);

        input.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);

        done.setOnClickListener(v -> {
            String value = input.getText() == null ? "" : input.getText().toString().trim();
            if (!value.isEmpty()) {
                El el = new El();
                el.text = value;
                el.color = pendingColor;
                el.font = pendingFont;
                el.bg = pendingBg;
                el.sp = 22;
                s.elements.add(el);
                attachElement(el);
            }
            rootView.removeView(panel);
            if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        });

        // typing updates the live style preview only; the element is added on Done.
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    private void applyInputStyle(TextView target, TextView bgBtn) {
        target.setTextColor(pendingColor);
        target.setTypeface(fontFor(pendingFont));
        if (pendingBg == 1) {
            GradientDrawable solid = new GradientDrawable();
            solid.setColor(0xCC000000);
            solid.setCornerRadius(dp(8));
            target.setBackground(solid);
            target.setPadding(dp(8), dp(4), dp(8), dp(4));
        } else if (pendingBg == 2) {
            GradientDrawable translucent = new GradientDrawable();
            translucent.setColor(0x66000000);
            translucent.setCornerRadius(dp(8));
            target.setBackground(translucent);
            target.setPadding(dp(8), dp(4), dp(8), dp(4));
        } else {
            target.setBackgroundColor(Color.TRANSPARENT);
        }
        if (bgBtn != null) bgBtn.setAlpha(pendingBg == 0 ? 0.5f : 1f);
        target.setShadowLayer(pendingFont == 2 ? 18f : 6f, 0f, 0f,
                pendingFont == 2 ? 0xFF00E5FF : 0x99000000);
    }

    private Typeface fontFor(int index) {
        switch (index) {
            case 0: return Typeface.create("sans-serif-black", Typeface.BOLD);
            case 1: return Typeface.create("sans-serif-light", Typeface.NORMAL);
            case 2: return Typeface.create("sans-serif-condensed", Typeface.BOLD);
            case 3: return Typeface.create("monospace", Typeface.NORMAL);
            default: return Typeface.create("serif", Typeface.ITALIC);
        }
    }

    private void attachElement(El el) {
        if (el.gif != null) {
            attachGifElement(el);
            return;
        }
        TextView tv = new TextView(this);
        tv.setText(el.text);
        tv.setTextSize(el.sp);
        tv.setTextColor(el.color);
        tv.setTypeface(fontFor(el.font));
        tv.setGravity(Gravity.CENTER);
        tv.setShadowLayer(el.font == 2 ? 18f : 6f, 0f, 0f,
                el.font == 2 ? 0xFF00E5FF : 0x99000000);
        if (el.bg == 1) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xCC000000);
            bg.setCornerRadius(dp(8));
            tv.setBackground(bg);
            tv.setPadding(dp(8), dp(4), dp(8), dp(4));
        } else if (el.bg == 2) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0x66000000);
            bg.setCornerRadius(dp(8));
            tv.setBackground(bg);
            tv.setPadding(dp(8), dp(4), dp(8), dp(4));
        }

        stage.post(() -> {
            tv.setX(el.cx * stage.getWidth() - tv.getWidth() / 2f);
            tv.setY(el.cy * stage.getHeight() - tv.getHeight() / 2f);
            tv.setScaleX(el.scale);
            tv.setScaleY(el.scale);
            tv.setRotation(el.rot);
        });

        el.view = tv;
        elementHost.addView(tv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        wireElementGestures(el, tv);
        tv.setOnClickListener(v -> openTextTool());
    }

    /* ------------------------------------------------------------------ */
    /* Gestures                                                            */
    /* ------------------------------------------------------------------ */

    private void wireElementGestures(El el, View tv) {
        final float[] startX = {0}, startY = {0};
        final float[] startDist = {0}, startScale = {1}, startRot = {0}, startAngle = {0};

        tv.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX[0] = v.getX() - e.getRawX();
                        startY[0] = v.getY() - e.getRawY();
                        startScale[0] = v.getScaleX();
                        startRot[0] = v.getRotation();
                        trash.animate().alpha(1f).setDuration(120).start();
                        return false;
                    case MotionEvent.ACTION_POINTER_DOWN:
                        startDist[0] = spacing(e);
                        startScale[0] = v.getScaleX();
                        startRot[0] = v.getRotation();
                        startAngle[0] = angle(e);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (e.getPointerCount() > 1) {
                            float d = spacing(e);
                            if (startDist[0] > 0) {
                                float scale = Math.max(0.35f, Math.min(4f,
                                        startScale[0] * (d / startDist[0])));
                                v.setScaleX(scale);
                                v.setScaleY(scale);
                            }
                            v.setRotation(startRot[0] + (angle(e) - startAngle[0]));
                        } else {
                            v.setX(startX[0] + e.getRawX());
                            v.setY(startY[0] + e.getRawY());
                        }
                        el.cx = (v.getX() + v.getWidth() / 2f) / Math.max(1, stage.getWidth());
                        el.cy = (v.getY() + v.getHeight() / 2f) / Math.max(1, stage.getHeight());
                        el.scale = v.getScaleX();
                        el.rot = v.getRotation();
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        trash.animate().alpha(0f).setDuration(120).start();
                        if (overTrash(v)) {
                            elementHost.removeView(v);
                            seg().elements.remove(el);
                        }
                        return true;
                }
                return false;
            }
        });
    }


    /** Pinch to scale and drag to move the base photo inside the 9:16 stage. */
    private void installBaseGestures(final ImageView view) {
        final float[] lastX = {0f};
        final float[] lastY = {0f};
        final float[] startSpan = {-1f};
        final float[] startScale = {1f};
        view.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastX[0] = e.getRawX();
                    lastY[0] = e.getRawY();
                    startSpan[0] = -1f;
                    // Keep the gesture here: a parent pager otherwise swallows the second pointer
                    // and the pinch is seen as a plain drag.
                    if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_POINTER_DOWN:
                    startSpan[0] = spacing(e);
                    startScale[0] = v.getScaleX();
                    if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (e.getPointerCount() >= 2 && startSpan[0] > 0f) {
                        float span = spacing(e);
                        float next = startScale[0] * (span / startSpan[0]);
                        // Shrinking below 1x is allowed (the stage shows black around the
                        // photo); clamping at 1x was why pinch-in appeared to do nothing.
                        next = Math.max(0.4f, Math.min(4f, next));
                        v.setScaleX(next);
                        v.setScaleY(next);
                    } else if (e.getPointerCount() == 1) {
                        float dx = e.getRawX() - lastX[0];
                        float dy = e.getRawY() - lastY[0];
                        v.setTranslationX(v.getTranslationX() + dx);
                        v.setTranslationY(v.getTranslationY() + dy);
                        lastX[0] = e.getRawX();
                        lastY[0] = e.getRawY();
                    }
                    return true;
                case MotionEvent.ACTION_POINTER_UP:
                    // Re-anchor so the remaining finger does not jump.
                    startSpan[0] = -1f;
                    lastX[0] = e.getRawX();
                    lastY[0] = e.getRawY();
                    return true;
                default:
                    return true;
            }
        });
    }

    private float spacing(MotionEvent e) {
        if (e.getPointerCount() < 2) return 0f;
        float dx = e.getX(0) - e.getX(1);
        float dy = e.getY(0) - e.getY(1);
        return (float) Math.hypot(dx, dy);
    }

    private float angle(MotionEvent e) {
        if (e.getPointerCount() < 2) return 0f;
        return (float) Math.toDegrees(Math.atan2(e.getY(1) - e.getY(0), e.getX(1) - e.getX(0)));
    }

    private boolean overTrash(View v) {
        int[] t = new int[2];
        int[] o = new int[2];
        trash.getLocationOnScreen(t);
        v.getLocationOnScreen(o);
        float cx = o[0] + v.getWidth() / 2f;
        float cy = o[1] + v.getHeight() / 2f;
        return Math.hypot(cx - (t[0] + trash.getWidth() / 2f),
                cy - (t[1] + trash.getHeight() / 2f)) < dp(70);
    }

    /* ------------------------------------------------------------------ */
    /* Draw tool                                                           */
    /* ------------------------------------------------------------------ */

    private int drawTool = 0;
    private int drawColor = 0xFFFF3B30;
    private int drawWidth = 8;

    private void openDrawPanel() {
        // Video strokes travel as a transparent PNG overlay (uploadOverlay), so the tool is
        // valid for both kinds of segment.
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(0xF2000000);
        panel.setPadding(dp(12), dp(20), dp(12), dp(12));

        LinearLayout tools = new LinearLayout(this);
        String[] labels = {"Pen", "Marker", "Neon", "Eraser"};
        for (int i = 0; i < labels.length; i++) {
            final int tool = i;
            TextView chip = glyph(labels[i], 13);
            chip.setPadding(dp(10), dp(8), dp(10), dp(8));
            chip.setBackgroundColor(i == drawTool ? 0x33FFFFFF : Color.TRANSPARENT);
            chip.setOnClickListener(v -> {
                drawTool = tool;
                for (int k = 0; k < tools.getChildCount(); k++) {
                    tools.getChildAt(k).setBackgroundColor(
                            k == tool ? 0x33FFFFFF : Color.TRANSPARENT);
                }
            });
            tools.addView(chip);
        }
        panel.addView(tools);

        LinearLayout colors = new LinearLayout(this);
        for (int color : PALETTE) {
            View dot = new View(this);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(color);
            dot.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(26), dp(26));
            lp.setMargins(dp(4), dp(10), dp(4), dp(10));
            dot.setOnClickListener(v -> drawColor = color);
            colors.addView(dot, lp);
        }
        panel.addView(colors);

        SeekBar size = new SeekBar(this);
        size.setMax(40);
        size.setProgress(drawWidth);
        size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int value, boolean fromUser) {
                drawWidth = Math.max(2, value);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { }
            @Override public void onStopTrackingTouch(SeekBar sb) { }
        });
        panel.addView(size);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.END);
        TextView undo = glyph("Undo", 14);
        TextView done = glyph("Done", 14);
        row.addView(undo, new LinearLayout.LayoutParams(dp(70), dp(40)));
        row.addView(done, new LinearLayout.LayoutParams(dp(70), dp(40)));
        panel.addView(row);

        drawLayer.setDrawing(true);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM;
        rootView.addView(panel, lp);

        undo.setOnClickListener(v -> {
            if (!seg().strokes.isEmpty()) {
                seg().strokes.remove(seg().strokes.size() - 1);
                drawLayer.invalidate();
            }
        });
        done.setOnClickListener(v -> {
            rootView.removeView(panel);
            drawLayer.setDrawing(false);
        });
    }

    /* ------------------------------------------------------------------ */
    /* Stickers (hashtag / location / time)                                */
    /* ------------------------------------------------------------------ */

    private void openStickerTray() {
        String[] options = {
                getString(R.string.story_st_hashtag),
                getString(R.string.story_st_location),
                getString(R.string.story_st_time),
                getString(R.string.story_st_poll),
                getString(R.string.story_st_mention),
                getString(R.string.story_st_link),
                getString(R.string.story_st_photo),
                getString(R.string.story_st_countdown),
                getString(R.string.story_st_slider),
                getString(R.string.story_st_eyedropper),
                getString(R.string.story_st_gif)};
        new AlertDialog.Builder(this)
                .setTitle(R.string.story_ed_sticker)
                .setItems(options, (d, which) -> {
                    if (which == 0) promptSticker(R.string.story_st_hashtag_hint, "#");
                    else if (which == 1) promptSticker(R.string.story_st_location_hint, "📍");
                    else if (which == 2) addSticker(getString(R.string.story_st_time_now), 0.5f, 0.24f);
                    else if (which == 3) promptPoll();
                    else if (which == 4) promptSticker(R.string.story_st_mention_hint, "@");
                    else if (which == 5) promptSticker(R.string.story_st_link_hint, "https://");
                    else if (which == 6) pickPhotoSticker();
                    else if (which == 7) promptCountdown();
                    else if (which == 8) promptEmojiSlider();
                    else if (which == 9) pickStageColour();
                    else showGifPicker();
                })
                .show();
    }

    /**
     * Drops a photo onto the story as a sticker.
     *
     * <p>It is added to the stage as a view, so the normal bake puts it into the published
     * JPEG and the viewer needs no special case for it.
     */
    private void pickPhotoSticker() {
        android.content.Intent intent = new android.content.Intent(
                android.content.Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        try {
            startActivityForResult(intent, REQ_STORY_PHOTO_STICKER);
        } catch (Exception e) {
            ToastUtil.showPill(this, getString(R.string.story_st_photo_failed));
        }
    }

    private void addPhotoSticker(Uri uri) {
        android.graphics.Bitmap bitmap = decodeForCollage(uri);
        if (bitmap == null) {
            ToastUtil.showPill(this, getString(R.string.story_st_photo_failed));
            return;
        }
        ImageView sticker = new ImageView(this);
        sticker.setImageBitmap(bitmap);
        sticker.setAdjustViewBounds(true);
        float density = getResources().getDisplayMetrics().density;
        sticker.setPadding((int) (2 * density), (int) (2 * density),
                (int) (2 * density), (int) (2 * density));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                (int) (stage.getWidth() * 0.38f), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        sticker.setLayoutParams(lp);
        // Dragging reuses the same gesture the text stickers have: once placed, the user can
        // move it anywhere on the stage and the bake follows the view.
        sticker.setOnTouchListener(new View.OnTouchListener() {
            float downX;
            float downY;
            float startX;
            float startY;

            @Override public boolean onTouch(View v, android.view.MotionEvent event) {
                switch (event.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();
                        startX = v.getTranslationX();
                        startY = v.getTranslationY();
                        return true;
                    case android.view.MotionEvent.ACTION_MOVE:
                        v.setTranslationX(startX + event.getRawX() - downX);
                        v.setTranslationY(startY + event.getRawY() - downY);
                        return true;
                    default:
                        return false;
                }
            }
        });
        stage.addView(sticker);
        ToastUtil.showPill(this, getString(R.string.story_st_photo_added));
    }

    /** Countdown sticker: a stage view that keeps ticking, so the bake shows the real time. */
    private void promptCountdown() {
        String[] spans = {getString(R.string.story_st_day1), getString(R.string.story_st_day3),
                getString(R.string.story_st_day7)};
        int[] days = {1, 3, 7};
        new AlertDialog.Builder(this)
                .setTitle(R.string.story_st_countdown)
                .setItems(spans, (d, which) -> addCountdown(days[which]))
                .show();
    }

    private void addCountdown(int days) {
        final long target = System.currentTimeMillis() + days * 24L * 60L * 60L * 1000L;
        final TextView view = new TextView(this);
        view.setTextColor(android.graphics.Color.WHITE);
        view.setTextSize(26);
        view.setShadowLayer(8f, 0f, 2f, 0xAA000000);
        view.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xB3000000);
        bg.setCornerRadius(18 * getResources().getDisplayMetrics().density);
        view.setBackground(bg);
        int pad = (int) (14 * getResources().getDisplayMetrics().density);
        view.setPadding(pad, pad / 2, pad, pad / 2);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        view.setLayoutParams(lp);

        final Runnable tick = new Runnable() {
            @Override public void run() {
                long left = Math.max(0, target - System.currentTimeMillis());
                long d = left / 86_400_000L;
                long h = (left % 86_400_000L) / 3_600_000L;
                long m = (left % 3_600_000L) / 60_000L;
                view.setText(d > 0 ? getString(R.string.story_st_countdown_dh, d, h)
                        : getString(R.string.story_st_countdown_hm, h, m));
                view.postDelayed(this, 30_000L);
            }
        };
        tick.run();
        stage.addView(view);
    }

    /** Emoji slider sticker: an emoji whose size the user picks before placing it. */
    private void promptEmojiSlider() {
        String[] emojis = {"\u2764\uFE0F", "\uD83D\uDD25", "\uD83D\uDE02", "\uD83D\uDC4D", "\u2B50"};
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        panel.setPadding(pad, pad, pad, pad);

        TextView preview = new TextView(this);
        preview.setText(emojis[0]);
        preview.setTextSize(64);
        preview.setGravity(Gravity.CENTER);
        panel.addView(preview);

        final android.widget.SeekBar seek = new android.widget.SeekBar(this);
        seek.setMax(96);
        seek.setProgress(64);
        seek.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar bar, int value,
                                                    boolean fromUser) {
                preview.setTextSize(Math.max(24, value));
            }

            @Override public void onStartTrackingTouch(android.widget.SeekBar bar) {
            }

            @Override public void onStopTrackingTouch(android.widget.SeekBar bar) {
            }
        });
        panel.addView(seek);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        for (int i = 0; i < emojis.length; i++) {
            final String emoji = emojis[i];
            TextView chip = new TextView(this);
            chip.setText(emoji);
            chip.setTextSize(24);
            chip.setPadding(pad / 2, pad / 2, pad / 2, pad / 2);
            chip.setOnClickListener(v -> preview.setText(emoji));
            row.addView(chip);
        }
        panel.addView(row);

        new AlertDialog.Builder(this)
                .setTitle(R.string.story_st_slider)
                .setView(panel)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    TextView sticker = new TextView(this);
                    sticker.setText(preview.getText());
                    sticker.setTextSize(Math.max(24, seek.getProgress()));
                    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
                    lp.gravity = Gravity.CENTER;
                    sticker.setLayoutParams(lp);
                    stage.addView(sticker);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Eyedropper: reads the colour under the centre of the stage for the next sticker. */
    private void pickStageColour() {
        try {
            int w = Math.max(1, stage.getWidth());
            int h = Math.max(1, stage.getHeight());
            android.graphics.Bitmap sample = android.graphics.Bitmap.createBitmap(1, 1,
                    android.graphics.Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(sample);
            canvas.translate(-w / 2f, -h / 2f);
            stage.draw(canvas);
            int colour = sample.getPixel(0, 0);
            sample.recycle();
            String hex = String.format(java.util.Locale.US, "#%06X", 0xFFFFFF & colour);
            ToastUtil.showPill(this, getString(R.string.story_st_picked, hex));
        } catch (Exception e) {
            Log.w("StoryEditor", "eyedropper failed: " + e.getMessage());
        }
    }

    private void promptSticker(int hintRes, String prefix) {
        EditText input = new EditText(this);
        input.setHint(hintRes);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this)
                .setTitle(R.string.story_ed_sticker)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String value = input.getText() == null ? "" : input.getText().toString().trim();
                    if (value.isEmpty()) return;
                    if (!value.startsWith(prefix)) value = prefix + value;
                    addSticker(value, 0.5f, 0.72f);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Poll sticker: question plus two to four options, stored in editor_meta. */
    private void promptPoll() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(16), dp(8), dp(16), dp(8));
        EditText question = new EditText(this);
        question.setHint(R.string.story_poll_question);
        EditText o1 = new EditText(this);
        o1.setHint(R.string.story_poll_option1);
        EditText o2 = new EditText(this);
        o2.setHint(R.string.story_poll_option2);
        EditText o3 = new EditText(this);
        o3.setHint(R.string.story_poll_option3);
        EditText o4 = new EditText(this);
        o4.setHint(R.string.story_poll_option4);
        form.addView(question);
        form.addView(o1);
        form.addView(o2);
        form.addView(o3);
        form.addView(o4);

        new AlertDialog.Builder(this)
                .setTitle(R.string.story_st_poll)
                .setView(form)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String q = text(question);
                    List<String> opts = new ArrayList<>();
                    for (EditText field : new EditText[]{o1, o2, o3, o4}) {
                        String value = text(field);
                        if (!value.isEmpty() && opts.size() < 4) opts.add(value);
                    }
                    if (q.isEmpty() || opts.size() < 2) {
                        Toast.makeText(this, R.string.story_poll_incomplete,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Segment s = seg();
                    s.pollQuestion = q;
                    s.pollOptions.clear();
                    s.pollOptions.addAll(opts);
                    showPollPreview(s);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private String text(EditText field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }

    /**
     * The poll is interactive, so it is not baked into the JPEG: this preview lives on the
     * canvas layer, which the renderer never draws, and the viewer rebuilds it from meta.
     */
    private void showPollPreview(Segment s) {
        if (pollPreview == null) {
            pollPreview = new TextView(this);
            pollPreview.setTextColor(0xFF111111);
            pollPreview.setTextSize(15);
            pollPreview.setGravity(Gravity.CENTER);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xFFFFFFFF);
            bg.setCornerRadius(dp(16));
            pollPreview.setBackground(bg);
            pollPreview.setPadding(dp(16), dp(12), dp(16), dp(12));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(240),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.CENTER;
            canvas.addView(pollPreview, lp);
        }
        if (s.pollQuestion == null) {
            pollPreview.setVisibility(View.GONE);
            return;
        }
        StringBuilder sb = new StringBuilder(s.pollQuestion);
        for (String option : s.pollOptions) sb.append("\n• ").append(option);
        pollPreview.setText(sb.toString());
        pollPreview.setVisibility(View.VISIBLE);
    }

    /** Places a bundled GIF on the stage. It animates in the editor and in the viewer. */
    private void addGifSticker(String name, float cx, float cy) {
        El el = new El();
        el.gif = name;
        el.cx = cx;
        el.cy = cy;
        el.scale = 1f;
        seg().elements.add(el);
        attachElement(el);
    }

    private void attachGifElement(El el) {
        ImageView view = new ImageView(this);
        view.setAdjustViewBounds(true);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        try {
            com.bumptech.glide.Glide.with(this)
                    .load("file:///android_asset/gifs/" + el.gif)
                    .into(view);
        } catch (Exception e) {
            Log.w(TAG, "gif sticker failed: " + e.getMessage());
        }
        stage.post(() -> {
            view.setX(el.cx * stage.getWidth() - view.getWidth() / 2f);
            view.setY(el.cy * stage.getHeight() - view.getHeight() / 2f);
            view.setScaleX(el.scale);
            view.setScaleY(el.scale);
            view.setRotation(el.rot);
        });
        el.view = view;
        elementHost.addView(view, new FrameLayout.LayoutParams(dp(120), dp(120)));
        wireElementGestures(el, view);
    }

    /** Bundled GIF catalogue: no network key needed, the assets ship with the app. */
    private void showGifPicker() {
        String[] names = {"lava", "maze", "count", "pulse", "sweep", "bounce"};
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(this);
        scroll.addView(row);
        final AlertDialog[] holder = new AlertDialog[1];
        for (String name : names) {
            ImageView cell = new ImageView(this);
            com.bumptech.glide.Glide.with(this)
                    .load("file:///android_asset/gifs/" + name + ".gif")
                    .into(cell);
            cell.setContentDescription(name);
            cell.setOnClickListener(v -> {
                addGifSticker(name, 0.5f, 0.45f);
                if (holder[0] != null) holder[0].dismiss();
            });
            row.addView(cell, new LinearLayout.LayoutParams(dp(72), dp(72)));
        }
        holder[0] = new AlertDialog.Builder(this)
                .setTitle(R.string.story_st_gif)
                .setView(scroll)
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void addSticker(String text, float cx, float cy) {
        El el = new El();
        el.text = text;
        el.cx = cx;
        el.cy = cy;
        el.sp = 18;
        el.bg = 2;
        seg().elements.add(el);
        attachElement(el);
    }

    /* ------------------------------------------------------------------ */
    /* Filmstrip + trim + menu                                             */
    /* ------------------------------------------------------------------ */

    private void buildFilmstrip() {
        filmstrip.removeAllViews();
        filmstrip.setVisibility(segments.size() > 1 ? View.VISIBLE : View.GONE);
        if (segments.size() <= 1) return;
        for (int i = 0; i < segments.size(); i++) {
            final int index = i;
            ImageView thumb = new ImageView(this);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            if (segments.get(i).uri != null) {
                GlideUtilsBind(thumb, segments.get(i).uri);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(46), dp(46));
            lp.setMargins(dp(4), dp(6), dp(4), dp(6));
            thumb.setAlpha(i == current ? 1f : 0.55f);
            thumb.setOnClickListener(v -> showSegment(index));
            filmstrip.addView(thumb, lp);
        }
    }

    private void GlideUtilsBind(ImageView view, String uri) {
        try {
            Glide.with(this).load(Uri.parse(uri)).into(view);
        } catch (Exception e) {
            Log.w(TAG, "thumb failed: " + e.getMessage());
        }
    }

    private void buildTrimBar(Segment s) {
        trimBar.removeAllViews();
        trimBar.setVisibility(s.video ? View.VISIBLE : View.GONE);
        if (!s.video) return;

        TextView mute = glyph(s.muted ? "🔇" : "🔊", 16);
        mute.setContentDescription(getString(R.string.story_ed_mute));
        mute.setOnClickListener(v -> {
            s.muted = !s.muted;
            if (storyPlayer != null) {
                storyPlayer.setVolume(s.muted ? 0f : 1f, s.muted ? 0f : 1f);
            }
            buildTrimBar(s);
        });

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView startLabel = glyph("0:00", 11);
        SeekBar startBar = new SeekBar(this);
        startBar.setMax((int) Math.max(1, s.trimEnd));
        startBar.setProgress((int) s.trimStart);
        startBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int value, boolean fromUser) {
                if (!fromUser) return;
                s.trimStart = Math.min(value, s.trimEnd - 1000);
                startLabel.setText(fmt(s.trimStart));
                video.seekTo((int) s.trimStart);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { }
            @Override public void onStopTrackingTouch(SeekBar sb) { }
        });

        SeekBar endBar = new SeekBar(this);
        endBar.setMax((int) Math.max(1, s.trimEnd));
        endBar.setProgress((int) s.trimEnd);
        endBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int value, boolean fromUser) {
                if (!fromUser) return;
                s.trimEnd = Math.max(value, s.trimStart + 1000);
                s.trimEnd = Math.min(s.trimEnd, s.trimStart + 60_000);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { }
            @Override public void onStopTrackingTouch(SeekBar sb) { }
        });

        TextView range = glyph(fmt(0) + " – " + fmt(s.trimEnd), 11);
        row.addView(mute, new LinearLayout.LayoutParams(dp(40), dp(32)));
        row.addView(startBar, new LinearLayout.LayoutParams(0, dp(32), 1f));
        row.addView(endBar, new LinearLayout.LayoutParams(0, dp(32), 1f));
        trimBar.addView(row);
        trimBar.addView(range);
    }

    private void openMoreMenu() {
        String[] options = {getString(R.string.story_ed_save_device), getString(R.string.story_ed_discard)};
        new AlertDialog.Builder(this)
                .setItems(options, (d, which) -> {
                    if (which == 0) saveToDevice();
                    else confirmDiscard();
                })
                .show();
    }

    @Override
    public void onBackPressed() {
        confirmDiscard();
    }

    private void confirmDiscard() {
        boolean edited = false;
        for (Segment s : segments) {
            if (!s.elements.isEmpty() || !s.strokes.isEmpty() || s.filter != 0) edited = true;
        }
        if (!edited) {
            finish();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.story_ed_discard_title)
                .setPositiveButton(R.string.story_ed_discard, (d, w) -> finish())
                .setNegativeButton(R.string.story_ed_keep, null)
                .show();
    }

    private void saveToDevice() {
        try {
            Bitmap bmp = renderStage();
            File dir = new File(getExternalFilesDir(null), "stories");
            if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, "story_" + System.currentTimeMillis() + ".jpg");
            try (FileOutputStream fos = new FileOutputStream(out)) {
                bmp.compress(Bitmap.CompressFormat.JPEG, 92, fos);
            }
            ToastUtil.showPill(this, getString(R.string.story_ed_saved) + ": " + out.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "save failed: " + e.getMessage(), e);
            ToastUtil.showPill(this, getString(R.string.story_ed_save_failed));
        }
    }

    /* ------------------------------------------------------------------ */
    /* Publish                                                             */
    /* ------------------------------------------------------------------ */

    /** "›" publishes straight away — there is no post-publish share screen. */
    private void openShareSheet() {
        if (seg().uri == null && seg().elements.isEmpty()) {
            ToastUtil.showPill(this, getString(R.string.story_ed_nothing));
            return;
        }
        shareAfterPublish = false;
        firstStoryUrl = null;
        firstStoryId = null;
        publish("public");
    }

    /** Picks 2-4 photos that share the 9:16 frame as one collage. */
    private void pickStoryLayout() {
        android.content.Intent intent = new android.content.Intent(
                android.content.Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            startActivityForResult(intent, REQ_STORY_LAYOUT);
        } catch (Exception e) {
            ToastUtil.showPill(this, getString(R.string.story_ed_layout_failed));
        }
    }

    private void applyStoryLayout(@NonNull android.content.Intent data) {
        java.util.List<Uri> picked = new ArrayList<>();
        android.content.ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) picked.add(clip.getItemAt(i).getUri());
        } else if (data.getData() != null) {
            picked.add(data.getData());
        }
        if (picked.size() < 2 || picked.size() > 4) {
            ToastUtil.showPill(this, getString(R.string.story_ed_layout_failed));
            return;
        }
        Segment segment = currentSegment();
        if (segment == null) return;
        segment.collage.clear();
        for (Uri uri : picked) {
            android.graphics.Bitmap bitmap = decodeForCollage(uri);
            if (bitmap != null) segment.collage.add(bitmap);
        }
        if (segment.collage.size() < 2) {
            segment.collage.clear();
            ToastUtil.showPill(this, getString(R.string.story_ed_layout_failed));
            return;
        }
        showSegment(current);
    }

    /** Decodes straight from the provider: the stage is baked synchronously, so Glide's
     *  asynchronous load would be baked as an empty cell. */
    private android.graphics.Bitmap decodeForCollage(Uri uri) {
        try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
            android.graphics.BitmapFactory.Options bounds =
                    new android.graphics.BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeStream(in, null, bounds);
            int sample = 1;
            while (bounds.outWidth / (sample * 2) > 1080) sample *= 2;
            android.graphics.BitmapFactory.Options options =
                    new android.graphics.BitmapFactory.Options();
            options.inSampleSize = sample;
            try (java.io.InputStream again = getContentResolver().openInputStream(uri)) {
                return android.graphics.BitmapFactory.decodeStream(again, null, options);
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** Opens the system audio picker for the story's track. */
    private void pickStoryMusic() {
        android.content.Intent intent = new android.content.Intent(
                android.content.Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        try {
            startActivityForResult(intent, REQ_STORY_MUSIC);
        } catch (Exception e) {
            ToastUtil.showPill(this, getString(R.string.story_ed_music_failed));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_STORY_PHOTO_STICKER) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                addPhotoSticker(data.getData());
            }
            return;
        }
        if (requestCode == REQ_STORY_LAYOUT) {
            if (resultCode == RESULT_OK && data != null) applyStoryLayout(data);
            return;
        }
        if (requestCode != REQ_STORY_MUSIC || resultCode != RESULT_OK || data == null
                || data.getData() == null) {
            return;
        }
        musicUri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(musicUri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }
        musicTitle = displayNameFor(musicUri);
        uploadedStoryMusicUrl = null;
        ToastUtil.showPill(this, getString(R.string.story_ed_music_set,
                musicTitle == null ? "" : musicTitle));
    }

    /** A readable name for a picked document, falling back to the last path segment. */
    private String displayNameFor(Uri uri) {
        try (android.database.Cursor cursor = getContentResolver().query(uri,
                new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null && !name.trim().isEmpty()) return name;
            }
        } catch (Exception ignored) {
        }
        String path = uri.getLastPathSegment();
        return path == null ? "track" : path;
    }

    /**
     * Uploads the chosen track once, then starts the normal publish chain.
     *
     * <p>The bytes go to the stories bucket so the viewer can fetch them with the same
     * signed-in access the story media needs; a failure is reported instead of publishing a
     * story that claims to have music it does not have.
     */
    private void uploadStoryMusicThenPublish(String audience, String myId) {
        if (musicUri == null || uploadedStoryMusicUrl != null) {
            publishNext(new ArrayList<>(segments), 0, audience, myId);
            return;
        }
        final byte[] bytes;
        try {
            bytes = readBytes(musicUri);
        } catch (Exception e) {
            failPublish(getString(R.string.story_ed_music_failed));
            return;
        }
        String name = musicTitle == null ? "track.mp3" : musicTitle;
        int dot = name.lastIndexOf('.');
        String ext = dot > 0 ? name.substring(dot + 1).toLowerCase(java.util.Locale.US) : "mp3";
        String path = myId + "/music-" + UUID.randomUUID() + "." + ext;
        RequestBody body = RequestBody.create(MediaType.parse("audio/" + ext), bytes);
        RetrofitClient.getInstance(this).getStorageApi().uploadFile("stories", path, body)
                .enqueue(new Callback<Void>() {
                    @Override public void onResponse(Call<Void> c, Response<Void> r) {
                        if (!r.isSuccessful()) {
                            failPublish(getString(R.string.story_ed_music_failed));
                            return;
                        }
                        uploadedStoryMusicUrl = MediaUrl.normalize("stories",
                                supabaseBase() + "storage/v1/object/public/stories/" + path);
                        publishNext(new ArrayList<>(segments), 0, audience, myId);
                    }

                    @Override public void onFailure(Call<Void> c, Throwable t) {
                        failPublish(getString(R.string.story_ed_music_failed));
                    }
                });
    }

    /** Adds the story's track to the editor_meta record the viewer reads. */
    private String withMusic(String metaJson) {
        if (uploadedStoryMusicUrl == null) return metaJson;
        try {
            org.json.JSONObject root = new org.json.JSONObject(metaJson);
            root.put("music_url", uploadedStoryMusicUrl);
            root.put("music_title", musicTitle == null ? "" : musicTitle);
            return root.toString();
        } catch (Exception e) {
            return metaJson;
        }
    }

    private void publish(String audience) {
        if (busy) return;
        String myId = new SessionManager(this).getUserId();
        if (myId == null || myId.isEmpty()) {
            ToastUtil.showPill(this, getString(R.string.story_ed_sign_in));
            return;
        }
        busy = true;
        ToastUtil.showPill(this, getString(R.string.story_ed_publishing));
        uploadStoryMusicThenPublish(audience, myId);
    }

    private void publishNext(List<Segment> list, int index, String audience, String myId) {
        if (index >= list.size()) {
            runOnUiThread(() -> {
                busy = false;
                // Publishing ends here: the share sheet that used to open next is gone, so
                // a posted story returns straight to the tray with a confirmation only.
                ToastUtil.showPill(this, getString(R.string.story_ed_published));
                finish();
            });
            return;
        }

        Segment s = list.get(index);

        // Retrofit delivers its callbacks on the main thread, so uploadOverlay()'s blocking
        // call ran on the UI thread and threw NetworkOnMainThreadException -- caught, logged
        // and turned into a null overlay. Video publishing therefore hops to a worker first;
        // photo publishing stays on the main thread because it renders views.
        if (s.video && android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            new Thread(() -> publishNext(list, index, audience, myId)).start();
            return;
        }

        Log.d("StoryDraw", "publish segment video=" + s.video + " strokes=" + s.strokes.size());
        StorageApi storage = RetrofitClient.getInstance(this).getStorageApi();
        RestApi rest = RetrofitClient.getInstance(this).getRestApi();

        try {
            if (s.video) {
                String path = myId + "/" + UUID.randomUUID() + ".mp4";
                File file = new File(Uri.parse(s.uri).getPath() == null
                        ? "" : Uri.parse(s.uri).getPath());
                byte[] bytes = readBytes(Uri.parse(s.uri));
                RequestBody body = RequestBody.create(MediaType.parse("video/mp4"), bytes);
                // Overlay first, on this worker thread: the upload callback below runs on the
                // main thread, where a blocking request throws NetworkOnMainThreadException.
                String overlayUrl = s.strokes.isEmpty() ? null : uploadOverlay(s, myId);
                storage.uploadFile("stories", path, body).enqueue(new Callback<Void>() {
                    @Override public void onResponse(Call<Void> c, Response<Void> r) {
                        if (!r.isSuccessful()) {
                            failPublish("upload HTTP " + r.code());
                            return;
                        }
                        String url = MediaUrl.normalize("stories",
                                supabaseBase() + "storage/v1/object/public/stories/" + path);
                        String meta = withMusic(StoryMeta.forVideo(s.trimStart, s.trimEnd,
                                s.muted, s.fit, s.filter, s.elements, overlayUrl,
                                s.pollQuestion, s.pollOptions));

                        insertStory(rest, myId, url, "video", audience, meta,
                                () -> publishNext(list, index + 1, audience, myId));
                    }

                    @Override public void onFailure(Call<Void> c, Throwable t) {
                        failPublish("network: " + t.getMessage());
                    }
                });
            } else {
                Bitmap bitmap = renderSegment(s);
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out);
                byte[] jpeg = out.toByteArray();
                String path = myId + "/" + UUID.randomUUID() + ".jpg";
                RequestBody body = RequestBody.create(MediaType.parse("image/jpeg"), jpeg);
                storage.uploadFile("stories", path, body).enqueue(new Callback<Void>() {
                    @Override public void onResponse(Call<Void> c, Response<Void> r) {
                        if (!r.isSuccessful()) {
                            failPublish("upload HTTP " + r.code());
                            return;
                        }
                        String url = MediaUrl.normalize("stories",
                                supabaseBase() + "storage/v1/object/public/stories/" + path);
                        String meta = withMusic(StoryMeta.forPhoto(s.fit, s.filter, s.elements,
                                s.pollQuestion, s.pollOptions));
                        insertStory(rest, myId, url, "image", audience, meta,
                                () -> publishNext(list, index + 1, audience, myId));
                    }

                    @Override public void onFailure(Call<Void> c, Throwable t) {
                        failPublish("network: " + t.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "publish failed: " + e.getMessage(), e);
            failPublish(e.getMessage());
        }
    }

    /**
     * Exports the drawing layer of a video segment as a transparent PNG. The viewer draws it
     * over the player, which is why the strokes do not have to be re-created from metadata.
     */
    private String uploadOverlay(Segment s, String myId) {
        try {
            // Drawing a view from the Retrofit callback thread fails silently, which is why
            // the first overlay upload produced no PNG at all. Render on the UI thread.
            Bitmap bmp = renderOverlayBitmap();
            if (bmp == null) { overlayError = "render-null"; return null; }
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            String path = myId + "/overlay_" + UUID.randomUUID() + ".png";
            RequestBody body = RequestBody.create(MediaType.parse("image/png"), out.toByteArray());
            retrofit2.Response<Void> response = RetrofitClient.getInstance(this)
                    .getStorageApi().uploadFile("stories", path, body).execute();
            if (!response.isSuccessful()) {
                overlayError = "http" + response.code();
                Log.e(TAG, "overlay upload HTTP " + response.code());
                return null;
            }
            overlayError = "ok";
            return MediaUrl.normalize("stories",
                    supabaseBase() + "storage/v1/object/public/stories/" + path);
        } catch (Exception e) {
            overlayError = "exc:" + e.getClass().getSimpleName() + ":" + e.getMessage();
            Log.e(TAG, "overlay export failed: " + e.getMessage(), e);
            return null;
        }
    }

    /** Draws the stroke layer into a transparent bitmap, on the UI thread. */
    private Bitmap renderOverlayBitmap() {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            return drawOverlayNow();
        }
        final Bitmap[] holder = new Bitmap[1];
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        runOnUiThread(() -> {
            holder[0] = drawOverlayNow();
            latch.countDown();
        });
        try {
            latch.await(3, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return holder[0];
    }

    private Bitmap drawOverlayNow() {
        try {
            int w = Math.max(1, stage.getWidth());
            int h = Math.max(1, stage.getHeight());
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            drawLayer.draw(canvas);
            return bmp;
        } catch (Exception e) {
            Log.e(TAG, "overlay render failed: " + e.getMessage(), e);
            return null;
        }
    }

    private void failPublish(String message) {
        runOnUiThread(() -> {
            busy = false;
            Log.e(TAG, "publish failed: " + message);
            ToastUtil.showPill(this, getString(R.string.story_ed_publish_failed, message));
        });
    }

    private void insertStory(RestApi rest, String userId, String mediaUrl, String mediaType,
                             String audience, String metaJson, Runnable next) {
        Map<String, Object> body = new HashMap<>();
        body.put("user_id", userId);
        body.put("media_url", mediaUrl);
        body.put("media_type", mediaType);
        body.put("audience", audience);
        // PostgREST stores a Java String as a jsonb *string*, which makes every
        // editor_meta->>'field' query return NULL. Send a real object instead.
        body.put("editor_meta", new com.google.gson.Gson().fromJson(metaJson,
                com.google.gson.JsonObject.class));
        rest.createStory(body).enqueue(new Callback<List<StoryDto>>() {
            @Override public void onResponse(Call<List<StoryDto>> c, Response<List<StoryDto>> r) {
                if (firstStoryUrl == null) {
                    firstStoryUrl = mediaUrl;
                    firstStoryVideo = "video".equalsIgnoreCase(mediaType);
                    // return=representation hands back the inserted row, so the share page can
                    // carry a real story id instead of a bare media URL.
                    if (r.body() != null && !r.body().isEmpty()) {
                        firstStoryId = r.body().get(0).getId();
                    }
                }
                if (!r.isSuccessful()) {
                    Log.e(TAG, "story insert HTTP " + r.code());
                    failPublish("story insert HTTP " + r.code());
                    return;
                }
                next.run();
            }

            @Override public void onFailure(Call<List<StoryDto>> c, Throwable t) {
                failPublish("story insert: " + t.getMessage());
            }
        });
    }

    private String supabaseBase() {
        String base = BuildConfig.SUPABASE_URL.trim();
        return base.endsWith("/") ? base : base + "/";
    }

    private byte[] readBytes(Uri uri) throws Exception {
        try (java.io.InputStream in = getContentResolver().openInputStream(uri);
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            if (in == null) throw new IllegalStateException("cannot open " + uri);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    /* ------------------------------------------------------------------ */
    /* Bitmap rendering                                                    */
    /* ------------------------------------------------------------------ */

    private Bitmap renderSegment(Segment s) {
        int previous = current;
        showSegment(segments.indexOf(s));
        Bitmap bmp = renderStage();
        if (previous != current) showSegment(previous);
        return bmp;
    }

    private Bitmap renderStage() {
        Bitmap bmp = Bitmap.createBitmap(OUT_W, OUT_H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(Color.BLACK);

        Segment seg = currentSegment();
        boolean collage = seg != null && !seg.collage.isEmpty();
        // The collage is painted straight into the bitmap instead of through the view. A child
        // added during the same frame has no measured size yet, which is why the view route
        // published a black story even though the editor looked right.
        if (collage) drawCollageCells(c, seg.collage, OUT_W, OUT_H);

        int stageW = stage.getWidth() > 0 ? stage.getWidth() : OUT_W;
        int stageH = stage.getHeight() > 0 ? stage.getHeight() : OUT_H;
        float scale = Math.min(OUT_W / (float) stageW, OUT_H / (float) stageH);
        c.save();
        c.scale(scale, scale);

        android.graphics.drawable.Drawable background = null;
        int collageVisibility = View.GONE;
        if (collage) {
            // The stage paints its own black background and the collage view on top; both are
            // already on the bitmap, so they are muted for this pass and only the overlays
            // (text, stickers, drawing) are drawn over it.
            background = stage.getBackground();
            stage.setBackground(null);
            if (collageView != null) {
                collageVisibility = collageView.getVisibility();
                collageView.setVisibility(View.INVISIBLE);
            }
        }
        // GIF stickers are animated, so they are not baked: a single frame would freeze them.
        // They travel in editor_meta and the viewer plays them over the photo.
        java.util.List<View> hiddenGifs = new java.util.ArrayList<>();
        if (elementHost != null) {
            for (int i = 0; i < elementHost.getChildCount(); i++) {
                View child = elementHost.getChildAt(i);
                if (child instanceof ImageView && child.getVisibility() == View.VISIBLE) {
                    child.setVisibility(View.INVISIBLE);
                    hiddenGifs.add(child);
                }
            }
        }
        stage.draw(c);
        for (View child : hiddenGifs) child.setVisibility(View.VISIBLE);
        if (collage) {
            stage.setBackground(background);
            if (collageView != null) collageView.setVisibility(collageVisibility);
        }
        c.restore();
        return bmp;
    }

    /** Paints 2-4 photos as the collage template, in the canvas' own coordinate space. */
    private static void drawCollageCells(Canvas canvas, List<android.graphics.Bitmap> photos,
                                         int width, int height) {
        android.graphics.Paint paint = new android.graphics.Paint(
                android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setFilterBitmap(true);
        android.graphics.Rect src = new android.graphics.Rect();
        int count = photos.size();
        float half = width / 2f;
        float top = height / 2f;
        for (int i = 0; i < count; i++) {
            android.graphics.Bitmap bitmap = photos.get(i);
            if (bitmap == null) continue;
            android.graphics.RectF cell;
            if (count == 2) {
                cell = i == 0 ? new android.graphics.RectF(0, 0, width, top)
                        : new android.graphics.RectF(0, top, width, height);
            } else if (count == 3) {
                cell = i == 0 ? new android.graphics.RectF(0, 0, width, top)
                        : (i == 1 ? new android.graphics.RectF(0, top, half, height)
                        : new android.graphics.RectF(half, top, width, height));
            } else {
                float x = i % 2 == 0 ? 0 : half;
                float y = i < 2 ? 0 : top;
                cell = new android.graphics.RectF(x, y, x + half, y + top);
            }
            float scale = Math.max(cell.width() / bitmap.getWidth(),
                    cell.height() / bitmap.getHeight());
            float cropW = cell.width() / scale;
            float cropH = cell.height() / scale;
            float left = (bitmap.getWidth() - cropW) / 2f;
            float above = (bitmap.getHeight() - cropH) / 2f;
            src.set(Math.round(left), Math.round(above),
                    Math.round(left + cropW), Math.round(above + cropH));
            canvas.drawBitmap(bitmap, src, cell, paint);
        }
    }

    /**
     * Randomises the Create-mode background.
     *
     * <p>A new index is forced to differ from the current one: consecutive taps on a four
     * pair palette would otherwise land on the same colour often enough to look broken.
     */
    private void shuffleGradient() {
        Segment segment = currentSegment();
        if (segment == null) return;
        int pairs = 4;
        int next = segment.gradientIndex;
        for (int guard = 0; guard < 8 && next == segment.gradientIndex; guard++) {
            next = (int) (Math.random() * pairs);
        }
        segment.gradientIndex = next;
        if (stage != null) stage.setBackground(gradientDrawable(next));
    }

    /**
     * Draws 2-4 photos as one 9:16 frame.
     *
     * <p>It paints into the stage, so {@link #renderStage()} bakes the collage with everything
     * else — no second compositing pass is needed for publishing.
     */
    private static final class CollageView extends View {
        private final List<android.graphics.Bitmap> photos = new ArrayList<>();
        private final android.graphics.Paint paint = new android.graphics.Paint(
                android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Rect src = new android.graphics.Rect();
        private final android.graphics.RectF dst = new android.graphics.RectF();

        CollageView(android.content.Context context) {
            super(context);
            paint.setFilterBitmap(true);
        }

        void setPhotos(List<android.graphics.Bitmap> bitmaps) {
            photos.clear();
            photos.addAll(bitmaps);
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            if (photos.isEmpty()) return;
            int w = getWidth();
            int h = getHeight();
            int count = photos.size();
            for (int i = 0; i < count; i++) {
                android.graphics.RectF cell = cellFor(i, count, w, h);
                drawCropped(canvas, photos.get(i), cell);
            }
        }

        /** 2 = split, 3 = one wide above two, 4 = quad. */
        private android.graphics.RectF cellFor(int index, int count, int w, int h) {
            float half = w / 2f;
            float top = h / 2f;
            if (count == 2) {
                return index == 0 ? new android.graphics.RectF(0, 0, w, top)
                        : new android.graphics.RectF(0, top, w, h);
            }
            if (count == 3) {
                if (index == 0) return new android.graphics.RectF(0, 0, w, top);
                return index == 1 ? new android.graphics.RectF(0, top, half, h)
                        : new android.graphics.RectF(half, top, w, h);
            }
            float x = index % 2 == 0 ? 0 : half;
            float y = index < 2 ? 0 : top;
            return new android.graphics.RectF(x, y, x + half, y + top);
        }

        /** Center-crop, so a portrait shot does not squash into a square cell. */
        private void drawCropped(Canvas canvas, android.graphics.Bitmap bitmap,
                                 android.graphics.RectF cell) {
            if (bitmap == null) return;
            float scale = Math.max(cell.width() / bitmap.getWidth(),
                    cell.height() / bitmap.getHeight());
            float cropW = cell.width() / scale;
            float cropH = cell.height() / scale;
            float left = (bitmap.getWidth() - cropW) / 2f;
            float above = (bitmap.getHeight() - cropH) / 2f;
            src.set(Math.round(left), Math.round(above),
                    Math.round(left + cropW), Math.round(above + cropH));
            canvas.drawBitmap(bitmap, src, cell, paint);
        }
    }

    /** The segment currently on the stage; null only before the first one is built. */
    private Segment currentSegment() {
        return segments.isEmpty() ? null : segments.get(Math.min(current, segments.size() - 1));
    }

    private GradientDrawable gradientDrawable(int index) {
        int[][] pairs = {
                {0xFFFF6A88, 0xFF7A5CFA}, {0xFF00C6FF, 0xFF0072FF},
                {0xFFFFB199, 0xFFFF0844}, {0xFF43E97B, 0xFF38F9D7}};
        int[] pair = pairs[index % pairs.length];
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{pair[0], pair[1]});
    }

    private boolean isVideoUri(String uri) {
        String lower = uri.toLowerCase();
        if (lower.startsWith("content://")) {
            String type = getContentResolver().getType(Uri.parse(uri));
            return type != null && type.startsWith("video");
        }
        return lower.endsWith(".mp4") || lower.endsWith(".mov")
                || lower.endsWith(".3gp") || lower.endsWith(".mkv");
    }

    private long probeDuration(String uri) {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(this, Uri.parse(uri));
            String ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            long value = ms == null ? 0 : Long.parseLong(ms);
            return Math.min(value, 60_000L);
        } catch (Exception e) {
            return 60_000L;
        } finally {
            try { r.release(); } catch (Exception ignored) { }
        }
    }

    private String fmt(long ms) {
        long total = ms / 1000;
        return String.format(java.util.Locale.US, "%d:%02d", total / 60, total % 60);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /* ------------------------------------------------------------------ */
    /* Drawing layer                                                       */
    /* ------------------------------------------------------------------ */

    class DrawLayer extends View {
        private final List<Stroke> strokes = new ArrayList<>();
        private Stroke active;
        private boolean drawing;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        DrawLayer(Context context) {
            super(context);
            setLayerType(LAYER_TYPE_SOFTWARE, null);
        }

        void setStrokes(List<Stroke> value) {
            strokes.clear();
            strokes.addAll(value);
            invalidate();
        }

        void setDrawing(boolean value) {
            drawing = value;
            // The element host sits above this layer, so a stroke that starts over an empty
            // part of it never reaches the path. While the tool is active the drawing layer
            // takes the top slot instead.
            if (value) {
                bringToFront();
                requestLayout();
            }
        }

        @Override protected void onDraw(Canvas canvas) {
            for (Stroke s : strokes) drawStroke(canvas, s);
            if (active != null) drawStroke(canvas, active);
        }

        private void drawStroke(Canvas canvas, Stroke s) {
            paint.reset();
            paint.setAntiAlias(true);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(s.width);
            paint.setColor(s.color);
            if (s.tool == 3) {
                paint.setXfermode(new android.graphics.PorterDuffXfermode(
                        android.graphics.PorterDuff.Mode.CLEAR));
            } else if (s.tool == 1) {
                paint.setAlpha(110);
            } else if (s.tool == 2) {
                paint.setShadowLayer(12f, 0f, 0f, s.color);
            }
            canvas.drawPath(s.path, paint);
            paint.setXfermode(null);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (!drawing) return false;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    active = new Stroke();
                    active.color = drawTool == 3 ? 0xFF000000 : drawColor;
                    active.width = drawWidth * (drawTool == 1 ? 2.2f : 1f);
                    active.tool = drawTool;
                    active.path.moveTo(e.getX(), e.getY());
                    invalidate();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (active != null) {
                        active.path.lineTo(e.getX(), e.getY());
                        invalidate();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (active != null) {
                        seg().strokes.add(active);
                        Log.d("StoryDraw", "commit stroke, segment strokes=" + seg().strokes.size());
                        strokes.clear();
                        strokes.addAll(seg().strokes);
                        active = null;
                        invalidate();
                    }
                    return true;
            }
            return false;
        }
    }
}
