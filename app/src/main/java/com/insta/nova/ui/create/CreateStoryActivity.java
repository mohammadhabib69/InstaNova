package com.insta.nova.ui.create;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.insta.nova.BuildConfig;
import com.insta.nova.R;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.remote.RetrofitClient;
import com.insta.nova.data.remote.api.RestApi;
import com.insta.nova.data.remote.api.StorageApi;
import com.insta.nova.data.remote.dto.StoryDto;
import com.insta.nova.databinding.ActivityCreateStoryBinding;
import com.insta.nova.util.Constants;
import com.insta.nova.util.ToastUtil;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
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
 * PAGE 11 — Story editor / share.
 *
 * The picked photo is shown inside a 9:16 card; the surrounding background is a
 * vertical gradient derived from the photo's average colour, and the bottom 96dp
 * band is solid black. Drawings and text overlays are flattened into a
 * 1080x1920 JPEG and uploaded with the raw-bytes pipeline before the DB row is
 * created. Every colour and size comes from the PAGE 0 / PAGE 11 tokens.
 */
public class CreateStoryActivity extends AppCompatActivity {

    /**
     * Single entry point for the full story flow (media selection -> editor -> share).
     * Passing a pre-picked image skips the picker; null starts at media selection.
     */
    public static void start(Context context, @androidx.annotation.Nullable String imageUri) {
        if (context == null) return;
        Intent intent = new Intent(context, CreateStoryActivity.class);
        if (imageUri != null && !imageUri.isEmpty()) {
            intent.putExtra("image_uri", imageUri);
        }
        if (!(context instanceof android.app.Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    private static final String TAG_API = "InstaApi";
    private static final String TAG_GLIDE = "InstaGlide";
    private static final String PREFS = "story_editor_prefs";
    private static final String KEY_TOOLTIP = "size_tooltip_shown";
    private static final int OUT_W = 1080;
    private static final int OUT_H = 1920;

    /** A draggable / resizable text overlay and its style. */
    private static final class TextOverlay {
        EditText view;
        int color;
        boolean bold;
    }

    private ActivityCreateStoryBinding binding;
    private Uri selectedUri;
    private Bitmap sourceBitmap;
    private String uploadedPath;
    private StorageApi storageApi;
    private RestApi restApi;
    private okhttp3.OkHttpClient okHttpClient;
    private String currentUserId;

    private final List<TextOverlay> textOverlays = new ArrayList<>();
    private TextOverlay activeText;
    private boolean publishing;
    private boolean drawMode;
    private int cardW;
    private int cardH;

    private int[] penColors;
    private ActivityResultLauncher<PickVisualMediaRequest> pickMediaLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateStoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        currentUserId = SessionManager.getInstance(this).getUserId();
        RetrofitClient client = RetrofitClient.getInstance(this);
        storageApi = client.getStorageApi();
        restApi = client.getRestApi();
        okHttpClient = client.getOkHttpClient();

        penColors = new int[]{
                ContextCompat.getColor(this, R.color.color_pen_white),
                ContextCompat.getColor(this, R.color.color_pen_black),
                ContextCompat.getColor(this, R.color.color_pen_red),
                ContextCompat.getColor(this, R.color.color_pen_yellow),
                ContextCompat.getColor(this, R.color.color_pen_green),
                ContextCompat.getColor(this, R.color.color_pen_blue),
        };

        applyInsets();
        sizeCard();
        wireTopRow();
        wireBottomRow();
        wireDrawPanel();
        wireTextPanel();

        String avatar = SessionManager.getInstance(this).getAvatarUrl();
        if (avatar != null && !avatar.isEmpty()) {
            com.insta.nova.util.GlideUtils.loadAvatar(binding.ivEditorAvatar, avatar);
        }

        pickMediaLauncher = registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(),
                uri -> {
                    if (uri != null) setImage(uri);
                }
        );

        String passed = getIntent() != null ? getIntent().getStringExtra("image_uri") : null;
        if (passed != null && !passed.isEmpty()) {
            setImage(Uri.parse(passed));
        } else {
            openPicker();
        }
    }

    /* ------------------------------------------------------------------ */
    /* Layout                                                              */
    /* ------------------------------------------------------------------ */

    private void applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            binding.layoutEditorTop.setPadding(
                    binding.layoutEditorTop.getPaddingLeft(),
                    bars.top + dp(R.dimen.story_ed_top_gap),
                    binding.layoutEditorTop.getPaddingRight(),
                    binding.layoutEditorTop.getPaddingBottom());
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) binding.layoutEditorBottom.getLayoutParams();
            lp.bottomMargin = bars.bottom + dp(R.dimen.story_ed_bottom_margin);
            binding.layoutEditorBottom.setLayoutParams(lp);
            return insets;
        });
        ViewCompat.requestApplyInsets(binding.getRoot());
    }

    /** Card width = screen - 56dp, height = 9:16 limited by the free vertical space. */
    private void sizeCard() {
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        cardW = screenW - dp(R.dimen.story_ed_card_margin);
        int reservedTop = getStatusBarHeight() + dp(R.dimen.story_ed_top_gap) + dp(R.dimen.story_ed_circle) + dp(R.dimen.story_ed_top_gap);
        int reservedBottom = dp(R.dimen.story_ed_bottom_solid) + dp(R.dimen.story_ed_tooltip_gap) + dp(R.dimen.story_ed_tooltip_pad_v) * 2 + dp(R.dimen.story_ed_caret_size) + dp(R.dimen.story_ed_top_gap);
        int available = screenH - reservedTop - reservedBottom;
        int wanted = Math.round(cardW * 16f / 9f);
        cardH = Math.min(wanted, Math.max(available, dp(R.dimen.story_ed_circle)));
        ViewGroup.LayoutParams lp = binding.cardEditor.getLayoutParams();
        lp.width = cardW;
        lp.height = cardH;
        binding.cardEditor.setLayoutParams(lp);
    }

    private int getStatusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : dp(R.dimen.story_ed_top_gap);
    }

    private void wireTopRow() {
        binding.btnEditorClose.setOnClickListener(v -> confirmDiscard());
        binding.btnEditorMore.setOnClickListener(v -> showMoreSheet());

        binding.btnEditorDraw.setOnClickListener(v -> enterDrawMode());
        binding.btnEditorText.setOnClickListener(v -> addTextOverlay());

        // Design-only icons follow the shared decorative contract.
        for (ImageView icon : new ImageView[]{
                binding.btnEditorTag, binding.btnEditorSticker, binding.btnEditorMusic}) {
            if (Constants.DECORATIVE_UI) {
                icon.setVisibility(View.VISIBLE);
                icon.setOnClickListener(v -> ToastUtil.showNotAvailable(this));
            } else {
                icon.setVisibility(View.GONE);
            }
        }
        if (Constants.DECORATIVE_UI) {
            binding.pillCloseFriends.setVisibility(View.VISIBLE);
            binding.pillCloseFriends.setOnClickListener(v -> ToastUtil.showNotAvailable(this));
        } else {
            binding.pillCloseFriends.setVisibility(View.GONE);
        }

        // Tap the image = fit / fill toggle; the first tap also hides the tooltip.
        binding.cardEditor.setOnClickListener(v -> {
            hideTooltip();
            boolean filling = binding.ivPreview.getScaleType() == ImageView.ScaleType.CENTER_CROP;
            binding.ivPreview.setScaleType(filling ? ImageView.ScaleType.CENTER_INSIDE : ImageView.ScaleType.CENTER_CROP);
        });

        SharedPreferences sp = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!sp.getBoolean(KEY_TOOLTIP, false)) {
            binding.layoutTooltip.setVisibility(View.VISIBLE);
        } else {
            binding.layoutTooltip.setVisibility(View.GONE);
        }
    }

    private void hideTooltip() {
        if (binding.layoutTooltip.getVisibility() == View.VISIBLE) {
            binding.layoutTooltip.setVisibility(View.GONE);
            getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_TOOLTIP, true).apply();
        }
    }

    private void wireBottomRow() {
        binding.btnPublish.setOnClickListener(v -> publishStory());
        binding.btnEditorNext.setOnClickListener(v -> publishStory());
    }

    /* ------------------------------------------------------------------ */
    /* Drawing                                                             */
    /* ------------------------------------------------------------------ */

    private void wireDrawPanel() {
        View[] dots = {binding.dotColor1, binding.dotColor2, binding.dotColor3,
                binding.dotColor4, binding.dotColor5, binding.dotColor6};
        for (int i = 0; i < dots.length; i++) {
            final int color = penColors[i];
            tintDot(dots[i], color);
            dots[i].setOnClickListener(v -> binding.overlayDraw.setStrokeColor(color));
        }

        int min = dp(R.dimen.story_ed_stroke_min);
        int max = dp(R.dimen.story_ed_stroke_max);
        binding.seekStroke.setMax(Math.max(1, max - min));
        binding.seekStroke.setProgress(Math.max(0, dp(R.dimen.story_ed_stroke_default) - min));
        binding.overlayDraw.setStrokeWidth(dp(R.dimen.story_ed_stroke_default));
        binding.overlayDraw.setStrokeColor(penColors[0]);
        binding.seekStroke.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                binding.overlayDraw.setStrokeWidth(min + progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        binding.btnDrawUndo.setOnClickListener(v -> binding.overlayDraw.undo());
        binding.btnDrawDone.setOnClickListener(v -> exitDrawMode());
    }

    private void enterDrawMode() {
        drawMode = true;
        binding.panelDraw.setVisibility(View.VISIBLE);
        binding.panelText.setVisibility(View.GONE);
        binding.layoutEditorBottom.setVisibility(View.GONE);
        binding.layoutEditorTop.setVisibility(View.GONE);
        binding.overlayDraw.setVisibility(View.VISIBLE);
        binding.overlayDraw.setClickable(true);
        binding.cardEditor.setOnClickListener(null);
        deselectText();
    }

    private void exitDrawMode() {
        drawMode = false;
        binding.panelDraw.setVisibility(View.GONE);
        binding.layoutEditorBottom.setVisibility(View.VISIBLE);
        binding.layoutEditorTop.setVisibility(View.VISIBLE);
        binding.overlayDraw.setClickable(false);
        binding.cardEditor.setOnClickListener(v -> {
            hideTooltip();
            boolean filling = binding.ivPreview.getScaleType() == ImageView.ScaleType.CENTER_CROP;
            binding.ivPreview.setScaleType(filling ? ImageView.ScaleType.CENTER_INSIDE : ImageView.ScaleType.CENTER_CROP);
        });
    }

    private void tintDot(View dot, int color) {
        Drawable d = ContextCompat.getDrawable(this, R.drawable.bg_story_color_dot);
        if (d == null) return;
        android.graphics.drawable.Drawable wrapper = d.mutate();
        wrapper.setTint(color);
        dot.setBackground(wrapper);
    }

    /* ------------------------------------------------------------------ */
    /* Text overlays                                                       */
    /* ------------------------------------------------------------------ */

    private void wireTextPanel() {
        View[] dots = {binding.textDot1, binding.textDot2, binding.textDot3,
                binding.textDot4, binding.textDot5, binding.textDot6};
        for (int i = 0; i < dots.length; i++) {
            final int color = penColors[i];
            tintDot(dots[i], color);
            dots[i].setOnClickListener(v -> {
                if (activeText != null) {
                    activeText.color = color;
                    activeText.view.setTextColor(color);
                }
            });
        }
        binding.btnTextBold.setOnClickListener(v -> {
            if (activeText != null) {
                activeText.bold = !activeText.bold;
                activeText.view.setTypeface(null, activeText.bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            }
        });
        binding.btnTextDone.setOnClickListener(v -> finishTextEditing());
    }

    private void addTextOverlay() {
        final TextOverlay overlay = new TextOverlay();
        overlay.color = penColors[0];
        EditText et = new EditText(this);
        et.setBackground(null);
        et.setGravity(Gravity.CENTER);
        et.setTextColor(overlay.color);
        et.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.story_ed_text_overlay_size));
        et.setHint(R.string.story_ed_add_text);
        et.setHintTextColor(ContextCompat.getColor(this, R.color.color_story_text_muted));
        et.setPadding(0, 0, 0, 0);
        et.setSingleLine(false);
        overlay.view = et;

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        binding.overlayTextHost.addView(et, lp);
        textOverlays.add(overlay);

        activeText = overlay;
        dragSetup(overlay);
        et.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT);

        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                activeText = overlay;
                binding.panelText.setVisibility(View.VISIBLE);
            }
        });
        binding.panelText.setVisibility(View.VISIBLE);
        binding.layoutEditorBottom.setVisibility(View.GONE);
    }

    /** One finger = move, two fingers = pinch / rotate, drop on the bin = delete. */
    private void dragSetup(final TextOverlay overlay) {
        final float[] startRaw = new float[2];
        final float[] startPos = new float[2];
        final float[] startSpan = new float[1];
        final float[] startAngle = new float[1];
        final float[] startScale = new float[1];
        final float[] startRotation = new float[1];

        overlay.view.setOnTouchListener((v, event) -> {
            activeText = overlay;
            binding.panelText.setVisibility(View.VISIBLE);
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startRaw[0] = event.getRawX();
                    startRaw[1] = event.getRawY();
                    startPos[0] = v.getTranslationX();
                    startPos[1] = v.getTranslationY();
                    startScale[0] = v.getScaleX();
                    startRotation[0] = v.getRotation();
                    binding.ivTrashTarget.setVisibility(View.VISIBLE);
                    return true;
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (event.getPointerCount() >= 2) {
                        startSpan[0] = span(event);
                        startAngle[0] = angle(event);
                        startScale[0] = v.getScaleX();
                        startRotation[0] = v.getRotation();
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (event.getPointerCount() >= 2) {
                        float factor = startSpan[0] > 0 ? span(event) / startSpan[0] : 1f;
                        float scale = Math.max(0.4f, Math.min(4f, startScale[0] * factor));
                        v.setScaleX(scale);
                        v.setScaleY(scale);
                        v.setRotation(startRotation[0] + (angle(event) - startAngle[0]));
                    } else {
                        v.setTranslationX(startPos[0] + (event.getRawX() - startRaw[0]));
                        v.setTranslationY(startPos[1] + (event.getRawY() - startRaw[1]));
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    binding.ivTrashTarget.setVisibility(View.GONE);
                    if (overTrash(event)) {
                        removeTextOverlay(overlay);
                    }
                    return true;
                default:
                    return false;
            }
        });
    }

    private static float span(MotionEvent e) {
        float dx = e.getX(0) - e.getX(1);
        float dy = e.getY(0) - e.getY(1);
        return (float) Math.hypot(dx, dy);
    }

    private static float angle(MotionEvent e) {
        float dx = e.getX(0) - e.getX(1);
        float dy = e.getY(0) - e.getY(1);
        return (float) Math.toDegrees(Math.atan2(dy, dx));
    }

    private boolean overTrash(MotionEvent event) {
        if (binding.ivTrashTarget.getVisibility() != View.VISIBLE) return false;
        int[] loc = new int[2];
        binding.ivTrashTarget.getLocationOnScreen(loc);
        float cx = loc[0] + binding.ivTrashTarget.getWidth() / 2f;
        float cy = loc[1] + binding.ivTrashTarget.getHeight() / 2f;
        float r = binding.ivTrashTarget.getWidth();
        return Math.hypot(event.getRawX() - cx, event.getRawY() - cy) <= r;
    }

    private void removeTextOverlay(TextOverlay overlay) {
        binding.overlayTextHost.removeView(overlay.view);
        textOverlays.remove(overlay);
        if (activeText == overlay) activeText = null;
    }

    private void deselectText() {
        activeText = null;
        binding.panelText.setVisibility(View.GONE);
    }

    private void finishTextEditing() {
        if (activeText != null && activeText.view.getText().toString().trim().isEmpty()) {
            removeTextOverlay(activeText);
        } else if (activeText != null) {
            activeText.view.setKeyListener(null);
            activeText.view.clearFocus();
        }
        deselectText();
        binding.layoutEditorBottom.setVisibility(View.VISIBLE);
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(binding.getRoot().getWindowToken(), 0);
    }

    private boolean hasOverlays() {
        return !binding.overlayDraw.isEmpty() || !textOverlays.isEmpty();
    }

    /* ------------------------------------------------------------------ */
    /* Image + gradient background                                         */
    /* ------------------------------------------------------------------ */

    private void openPicker() {
        pickMediaLauncher.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    private void setImage(Uri uri) {
        selectedUri = uri;
        Glide.with(this)
                .asBitmap()
                .load(uri)
                .listener(new RequestListener<Bitmap>() {
                    @Override public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Bitmap> target, boolean first) {
                        Log.e(TAG_GLIDE, "Failed to load story preview: " + e);
                        toast(getString(R.string.story_vw_open_failed));
                        return false;
                    }
                    @Override public boolean onResourceReady(@NonNull Bitmap resource, Object model, Target<Bitmap> target, DataSource source, boolean first) {
                        sourceBitmap = resource;
                        applyGradient(resource);
                        return false;
                    }
                })
                .into(binding.ivPreview);

        // Average colour is sampled from a tiny downscale so the UI never blocks.
        Glide.with(this)
                .asBitmap()
                .load(uri)
                .override(24, 24)
                .into(new com.bumptech.glide.request.target.CustomTarget<Bitmap>() {
                    @Override public void onResourceReady(@NonNull Bitmap resource, @Nullable com.bumptech.glide.request.transition.Transition<? super Bitmap> transition) {
                        applyGradient(resource);
                    }
                    @Override public void onLoadCleared(@Nullable Drawable placeholder) {}
                });
    }

    /** Top = average colour lightened 15%, bottom = same colour darkened 55%. */
    private void applyGradient(Bitmap bitmap) {
        if (bitmap == null) return;
        long r = 0, g = 0, b = 0;
        int w = bitmap.getWidth(), h = bitmap.getHeight();
        int count = Math.max(1, w * h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = bitmap.getPixel(x, y);
                r += Color.red(p);
                g += Color.green(p);
                b += Color.blue(p);
            }
        }
        int avg = Color.rgb((int) (r / count), (int) (g / count), (int) (b / count));
        int top = lighten(avg, 0.15f);
        int bottom = darken(avg, 0.55f);
        GradientDrawable gradient = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
        binding.viewEditorGradient.setBackground(gradient);
    }

    private static int lighten(int color, float amount) {
        return Color.rgb(
                Math.min(255, Math.round(Color.red(color) + (255 - Color.red(color)) * amount)),
                Math.min(255, Math.round(Color.green(color) + (255 - Color.green(color)) * amount)),
                Math.min(255, Math.round(Color.blue(color) + (255 - Color.blue(color)) * amount)));
    }

    private static int darken(int color, float amount) {
        return Color.rgb(
                Math.round(Color.red(color) * (1f - amount)),
                Math.round(Color.green(color) * (1f - amount)),
                Math.round(Color.blue(color) * (1f - amount)));
    }

    /* ------------------------------------------------------------------ */
    /* Publish                                                             */
    /* ------------------------------------------------------------------ */

    /** Flattens image + overlays into the exported 1080x1920 JPEG bytes. */
    private byte[] flattenToJpeg() {
        Bitmap out = Bitmap.createBitmap(OUT_W, OUT_H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        canvas.drawColor(Color.BLACK);
        float sx = (float) OUT_W / (float) Math.max(1, binding.cardEditor.getWidth());
        float sy = (float) OUT_H / (float) Math.max(1, binding.cardEditor.getHeight());
        canvas.scale(sx, sy);
        binding.cardEditor.draw(canvas);
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        out.compress(Bitmap.CompressFormat.JPEG, 90, outStream);
        out.recycle();
        return outStream.toByteArray();
    }

    private void publishStory() {
        if (publishing) return;
        if (selectedUri == null) {
            toast(getString(R.string.story_ed_no_image));
            openPicker();
            return;
        }
        if (currentUserId == null) {
            toast("Please sign in first.");
            return;
        }

        publishing = true;
        setUploading(true);

        byte[] jpegBytes;
        try {
            jpegBytes = flattenToJpeg();
        } catch (Throwable t) {
            Log.e(TAG_API, "Failed to flatten story: " + t.getMessage(), t);
            publishing = false;
            setUploading(false);
            toast("Failed to render story: " + t.getMessage());
            return;
        }
        if (jpegBytes.length == 0) {
            publishing = false;
            setUploading(false);
            toast("Rendered story was empty");
            return;
        }

        uploadedPath = currentUserId + "/" + UUID.randomUUID() + ".jpg";
        RequestBody body = RequestBody.create(MediaType.parse("image/jpeg"), jpegBytes);
        Log.d(TAG_API, "uploaded " + jpegBytes.length + " bytes to stories/" + uploadedPath);

        storageApi.uploadFile("stories", uploadedPath, body).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (!response.isSuccessful()) {
                    Log.e(TAG_API, "HTTP " + response.code() + " uploading story: " + parseError(response));
                    fail("Upload failed (HTTP " + response.code() + "): " + parseError(response));
                    return;
                }
                String publicUrl = com.insta.nova.util.MediaUrl.normalize("stories", getPublicUrl("stories", uploadedPath));
                Log.d(TAG_API, "HTTP 200 uploadFile stories/" + uploadedPath + ". Verifying via HEAD...");

                okhttp3.Request headReq = new okhttp3.Request.Builder().url(publicUrl).head().build();
                okHttpClient.newCall(headReq).enqueue(new okhttp3.Callback() {
                    @Override
                    public void onResponse(@NonNull okhttp3.Call c, @NonNull okhttp3.Response headResponse) throws IOException {
                        int headCode = headResponse.code();
                        String contentType = headResponse.header("Content-Type");
                        String lenStr = headResponse.header("Content-Length");
                        headResponse.close();
                        Log.d(TAG_API, "HEAD status=" + headCode + " Content-Type=" + contentType
                                + " Content-Length=" + lenStr + " expected=" + jpegBytes.length + " for " + publicUrl);
                        if (headCode == 200) {
                            runOnUiThread(() -> createStoryRecord(publicUrl));
                        } else {
                            runOnUiThread(() -> fail("HEAD verification failed (HTTP " + headCode + ")"));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull okhttp3.Call c, @NonNull IOException e) {
                        Log.e(TAG_API, "HEAD verification failed: " + e.getMessage(), e);
                        runOnUiThread(() -> fail("HEAD verification failed: " + e.getMessage()));
                    }
                });
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e(TAG_API, "Network error uploading story: " + t.getMessage(), t);
                fail("Network error: " + t.getMessage());
            }
        });
    }

    private void createStoryRecord(String publicUrl) {
        Map<String, Object> body = new HashMap<>();
        body.put("user_id", currentUserId);
        body.put("media_url", publicUrl);
        body.put("media_type", "image");

        restApi.createStory(body).enqueue(new Callback<List<StoryDto>>() {
            @Override
            public void onResponse(Call<List<StoryDto>> call, Response<List<StoryDto>> response) {
                if (!response.isSuccessful()) {
                    Log.e(TAG_API, "HTTP " + response.code() + " creating story row: " + parseError(response));
                    fail("Failed to share story (HTTP " + response.code() + "): " + parseError(response));
                    return;
                }
                Log.d(TAG_API, "Story shared successfully: " + publicUrl);
                publishing = false;
                setUploading(false);
                toast("Added to story!");
                setResult(RESULT_OK);
                finish();
            }

            @Override
            public void onFailure(Call<List<StoryDto>> call, Throwable t) {
                Log.e(TAG_API, "Network error creating story: " + t.getMessage(), t);
                fail("Network error: " + t.getMessage());
            }
        });
    }

    private void setUploading(boolean uploading) {
        binding.progress.setVisibility(uploading ? View.VISIBLE : View.GONE);
        binding.btnPublish.setEnabled(!uploading);
        binding.btnEditorNext.setEnabled(!uploading);
    }

    private void fail(String message) {
        publishing = false;
        setUploading(false);
        if (uploadedPath != null) {
            final String path = uploadedPath;
            uploadedPath = null;
            storageApi.deleteFile("stories", path).enqueue(new Callback<Void>() {
                @Override public void onResponse(Call<Void> call, Response<Void> response) {
                    Log.d(TAG_API, "Cleaned up orphaned story object " + path);
                }
                @Override public void onFailure(Call<Void> call, Throwable t) {}
            });
        }
        toast(message);
    }

    /* ------------------------------------------------------------------ */
    /* Save / discard                                                      */
    /* ------------------------------------------------------------------ */

    /** Bottom sheet (PAGE 0 sheet token) with Save and Discard. */
    private void showMoreSheet() {
        final BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.InstaNovaBottomSheetDialog);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundResource(R.drawable.bg_bottom_sheet);

        View handle = new View(this);
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(
                dp(R.dimen.create_sheet_handle_width), dp(R.dimen.create_sheet_handle_height));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        handleLp.topMargin = dp(R.dimen.create_sheet_handle_margin_top);
        handle.setLayoutParams(handleLp);
        handle.setBackgroundResource(R.drawable.bg_drag_handle);
        container.addView(handle);

        container.addView(sheetRow(R.string.story_ed_save, v -> {
            dialog.dismiss();
            saveToGallery();
        }));
        container.addView(sheetRow(R.string.story_ed_discard, v -> {
            dialog.dismiss();
            confirmDiscard();
        }));

        dialog.setContentView(container);
        dialog.show();
    }

    private TextView sheetRow(int textRes, View.OnClickListener listener) {
        TextView row = new TextView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(R.dimen.create_sheet_row_height));
        row.setLayoutParams(lp);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(R.dimen.create_sheet_row_padding_horizontal), 0,
                dp(R.dimen.create_sheet_row_padding_horizontal), 0);
        row.setText(textRes);
        row.setTextAppearance(R.style.TextAppearance_Insta_CreateRow);
        row.setOnClickListener(listener);
        return row;
    }

    private void saveToGallery() {
        if (selectedUri == null) {
            toast(getString(R.string.story_ed_no_image));
            return;
        }
        byte[] bytes;
        try {
            bytes = flattenToJpeg();
        } catch (Throwable t) {
            toast("Could not render the story: " + t.getMessage());
            return;
        }
        String name = "instanova_story_" + System.currentTimeMillis() + ".jpg";
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);
        }
        Uri target = null;
        try {
            target = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (target == null) throw new IOException("MediaStore returned no uri");
            try (OutputStream os = getContentResolver().openOutputStream(target)) {
                if (os == null) throw new IOException("No output stream");
                os.write(bytes);
                os.flush();
            }
            Log.d(TAG_API, "Saved story image to gallery: " + target);
            toast(getString(R.string.story_ed_saved));
        } catch (IOException e) {
            if (target != null) getContentResolver().delete(target, null, null);
            Log.e(TAG_API, "Save to gallery failed: " + e.getMessage(), e);
            toast("Could not save: " + e.getMessage());
        }
    }

    private void confirmDiscard() {
        if (!hasOverlays()) {
            finish();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.story_ed_discard_title)
                .setPositiveButton(R.string.story_ed_discard, (d, w) -> finish())
                .setNegativeButton(R.string.story_ed_keep_editing, null)
                .show();
    }

    /* ------------------------------------------------------------------ */
    /* Helpers                                                             */
    /* ------------------------------------------------------------------ */

    private String getPublicUrl(String bucket, String path) {
        String base = BuildConfig.SUPABASE_URL.trim();
        if (!base.endsWith("/")) base += "/";
        return base + "storage/v1/object/public/" + bucket + "/" + path;
    }

    private String parseError(Response<?> response) {
        try {
            if (response.errorBody() != null) return response.errorBody().string();
        } catch (Exception ignored) {}
        return "Unknown error";
    }

    private int dp(int dimenRes) {
        try {
            return getResources().getDimensionPixelSize(dimenRes);
        } catch (Exception e) {
            return Math.round(getResources().getDisplayMetrics().density);
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onBackPressed() {
        if (drawMode) {
            exitDrawMode();
            return;
        }
        confirmDiscard();
    }
}
