package com.insta.nova.ui.create;

import android.content.ContentUris;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.gson.JsonElement;
import com.insta.nova.BuildConfig;
import com.insta.nova.R;
import com.insta.nova.data.NovaApi;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.remote.RetrofitClient;
import com.insta.nova.data.remote.api.RestApi;
import com.insta.nova.data.remote.api.StorageApi;
import com.insta.nova.data.remote.dto.PostDto;
import com.insta.nova.data.repository.NovaPostRepository;
import com.insta.nova.ui.post.PostComposerOptionsSheet;
import com.insta.nova.util.ColorMatrixPresets;
import com.insta.nova.util.Constants;
import com.insta.nova.util.ImageUtils;
import com.insta.nova.util.Resource;
import com.insta.nova.util.ToastUtil;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * The create flow: picker (PAGE 8B) → post editor (PAGE 9) → new post details
 * (PAGE 10A) → caption (PAGE 10B), all inside this one activity so no new
 * navigation target is introduced.
 *
 * Presentation only: the upload/publish path (StorageApi + RestApi +
 * ImageUtils), the picker launchers and every view id used below are
 * unchanged. Everything that had no backing feature in this codebase is wired
 * through the shared Constants.DECORATIVE_UI contract.
 */
public class CreatePostActivity extends AppCompatActivity {

    /**
     * Single entry point for the multi-step new-post flow (media -> edit -> details ->
     * caption). Used by the bottom-navigation "+" item and by the Create sheet's "Post"
     * row so both open exactly the same sequence.
     */
    /** Extra: which posting space the flow belongs to ("insta" or "nova"). */
    public static final String EXTRA_SPACE = "extra_space";

    /** A ready-made image (for example a rendered "Made for you" card) to start from. */
    public static final String EXTRA_INITIAL_MEDIA = "extra_initial_media";

    public static void start(Context context) {
        start(context, com.insta.nova.data.model.Post.SPACE_INSTA);
    }

    /**
     * Same flow, but the finished post is written into {@code space}.
     * Nova posts never reach the Insta feed and vice versa.
     */
    public static void start(Context context, String space) {
        if (context == null) return;
        Intent intent = new Intent(context, CreatePostActivity.class);
        intent.putExtra(EXTRA_SPACE, space == null
                ? com.insta.nova.data.model.Post.SPACE_INSTA : space);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    private static final String TAG_API = "InstaApi";
    private static final String TAG_GLIDE = "InstaGlide";

    /** Posting space for the row this flow creates (insta by default). */
    private String postSpace = com.insta.nova.data.model.Post.SPACE_INSTA;

    private static final int STEP_PICKER = 0;
    private static final int STEP_EDITOR = 1;
    private static final int STEP_DETAILS = 2;
    private static final int STEP_CAPTION = 3;

    private static final int GALLERY_LIMIT = 60;
    private static final Pattern HASHTAG_PATTERN = Pattern.compile("#(\\w+)");
    /** An @handle, but not the domain half of an e-mail address. */
    private static final Pattern MENTION_PATTERN =
            Pattern.compile("(?<![\\w@.])@([A-Za-z0-9._]{1,30})");

    private final List<Uri> selectedUris = new ArrayList<>();
    private final List<Uri> galleryUris = new ArrayList<>();
    /** Set once the posts row exists, so a later failure can remove it again. */
    private String createdPostId;
    private final List<String> uploadedPaths = new ArrayList<>();
    private final List<String> uploadedPublicUrls = new ArrayList<>();
    private final List<ColorMatrixPresets.FilterPreset> filterPresets = ColorMatrixPresets.getPresets();

    private StorageApi storageApi;
    private RestApi restApi;
    private NovaApi novaApi;
    private okhttp3.OkHttpClient okHttpClient;
    private String currentUserId;

    /**
     * The shared Post Options values for this post. Empty (anonymous off, never
     * expires) is the default, and it keeps the original write path alive.
     */
    private final PostComposerOptionsSheet.Options postOptions =
            new PostComposerOptionsSheet.Options();
    /** "Also share to Nova", off by default. */
    private boolean shareToNova;
    /** Where the post was taken, free text, null when the row is empty. */
    private String postLocation;
    /** The audio file picked off the device, and the title shown on the post. */
    private android.net.Uri musicUri;
    private String musicTitle;
    /** Public URL of the uploaded track, null when nothing was attached. */
    private String uploadedMusicUrl;

    /* ------------------------------------------------------------------ */
    /* Single-video post                                                   */
    /* ------------------------------------------------------------------ */

    /** True when the selection is one clip instead of one or more photos. */
    private boolean isVideoPost;
    private Uri videoUri;
    private String uploadedVideoUrl;
    private long videoDurationMs;
    private long videoTrimStartMs;
    private long videoTrimEndMs;
    private boolean videoMuted;
    /** Frame used as the poster; -1 means "the first frame of the trimmed range". */
    private long videoCoverMs = -1L;
    private android.widget.FrameLayout videoScreen;
    private android.widget.VideoView videoPreview;
    private android.media.MediaPlayer videoPlayer;
    private android.widget.TextView videoTrimLabel;
    private android.widget.TextView videoMuteButton;

    // Steps
    private View stepPicker;
    private View stepEditor;
    private View stepDetails;
    private View stepCaption;

    // Picker
    private ImageView ivPreview;
    private TextView btnPickerNext;
    private View btnPickerMulti;
    private ImageView ivMultiIcon;
    private TextView tvMultiLabel;
    private View layoutPickerGridLines;
    private RecyclerView rvPickerGrid;
    private PickerGridAdapter pickerAdapter;
    private Uri previewUri;
    private boolean multiSelect = false;
    private boolean previewFill = true;

    // Editor
    private RecyclerView rvEditorCards;
    private RecyclerView rvFilterStrip;
    private EditorCardAdapter editorCardAdapter;
    private FilterAdapter filterAdapter;
    private View underlineFilter;
    private View underlineEdit;
    private View underlineCrop;
    private ImageView ivEditorGalleryThumb;
    private int selectedFilter = 0;
    /** 0..1 blend of the selected preset against the original. */
    private float filterIntensity = 1f;
    /** Brightness, contrast, saturation, warmth, fade, highlights, shadows, vignette (-1..1). */
    private final float[] adjustments = new float[8];
    private int activeAdjustTool = 0;
    private View layoutIntensity;
    private View layoutAdjustPanel;
    private TextView tvIntensityLabel;
    private TextView tvAdjustLabel;
    private SeekBar seekAdjust;

    // Details / caption
    private RecyclerView rvDetailsPhotos;
    private DetailsPhotoAdapter detailsPhotoAdapter;
    private android.widget.EditText etCaption;
    private android.widget.EditText etCaptionScreen;
    private View btnPublish;
    private TextView tvLocationValue;
    private TextView tvCaptionOptionsSummary;
    private SwitchMaterial switchShareToNova;

    private View progress;
    private TextView tvUploadProgress;

    private ActivityResultLauncher<PickVisualMediaRequest> pickMediaLauncher;
    private ActivityResultLauncher<PickVisualMediaRequest> pickMultipleMediaLauncher;
    private ActivityResultLauncher<String> mediaPermissionLauncher;
    /** Guards against re-prompting in a loop when the user declines. */
    private boolean mediaPermissionRequested;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_post);

        String requestedSpace = getIntent() != null
                ? getIntent().getStringExtra(EXTRA_SPACE) : null;
        if (com.insta.nova.data.model.Post.SPACE_NOVA.equalsIgnoreCase(
                requestedSpace == null ? "" : requestedSpace)) {
            postSpace = com.insta.nova.data.model.Post.SPACE_NOVA;
        }
        currentUserId = SessionManager.getInstance(this).getUserId();
        RetrofitClient client = RetrofitClient.getInstance(this);
        storageApi = client.getStorageApi();
        restApi = client.getRestApi();
        novaApi = client.getNovaApi();
        okHttpClient = client.getOkHttpClient();

        bindViews();
        setupPickers();
        setupPickerStep();
        setupEditorStep();
        setupDetailsStep();
        setupCaptionStep();

        showStep(STEP_PICKER);
        loadDeviceGallery();

        // A caller can hand us an already-rendered image; skip straight to editing it.
        String preset = getIntent() != null
                ? getIntent().getStringExtra(EXTRA_INITIAL_MEDIA) : null;
        if (preset != null && !preset.isEmpty()) {
            Uri uri = Uri.parse(preset);
            selectedUris.clear();
            selectedUris.add(uri);
            setPreview(uri);
            updatePickerActions();
            if (pickerAdapter != null) pickerAdapter.notifyDataSetChanged();
            updateShareState();
            showStep(STEP_EDITOR);
        }
    }

    // ------------------------------------------------------------------
    // Wiring
    // ------------------------------------------------------------------

    private void bindViews() {
        stepPicker = findViewById(R.id.step_picker);
        stepEditor = findViewById(R.id.step_editor);
        stepDetails = findViewById(R.id.step_details);
        stepCaption = findViewById(R.id.step_caption);
        progress = findViewById(R.id.progress);
        tvUploadProgress = findViewById(R.id.tv_upload_progress);

        ivPreview = findViewById(R.id.iv_preview);
        btnPickerNext = findViewById(R.id.btn_picker_next);
        btnPickerMulti = findViewById(R.id.btn_picker_multi);
        ivMultiIcon = findViewById(R.id.iv_multi_icon);
        tvMultiLabel = findViewById(R.id.tv_multi_label);
        layoutPickerGridLines = findViewById(R.id.layout_picker_grid_lines);
        rvPickerGrid = findViewById(R.id.rv_picker_grid);

        rvEditorCards = findViewById(R.id.rv_editor_cards);
        rvFilterStrip = findViewById(R.id.rv_filter_strip);
        underlineFilter = findViewById(R.id.view_tab_underline_filter);
        underlineEdit = findViewById(R.id.view_tab_underline_edit);
        underlineCrop = findViewById(R.id.view_tab_underline_crop);
        ivEditorGalleryThumb = findViewById(R.id.iv_editor_gallery_thumb);

        rvDetailsPhotos = findViewById(R.id.rv_details_photos);
        etCaption = findViewById(R.id.et_caption);
        etCaptionScreen = findViewById(R.id.et_caption_screen);
        btnPublish = findViewById(R.id.btn_publish);
        tvLocationValue = findViewById(R.id.tv_location_value);
        tvCaptionOptionsSummary = findViewById(R.id.tv_caption_post_options_summary);
        switchShareToNova = findViewById(R.id.switch_caption_share_to_nova);
    }

    private void bindClick(int id, View.OnClickListener listener) {
        View v = findViewById(id);
        if (v != null) v.setOnClickListener(listener);
    }

    /** Swaps the four steps of the create flow. */
    private void showStep(int step) {
        if (videoScreen != null) {
            videoScreen.setVisibility(step == STEP_EDITOR && isVideoPost ? View.VISIBLE : View.GONE);
            if (step != STEP_EDITOR) stopVideoPreview();
        }
        stepPicker.setVisibility(step == STEP_PICKER ? View.VISIBLE : View.GONE);
        stepEditor.setVisibility(step == STEP_EDITOR ? View.VISIBLE : View.GONE);
        stepDetails.setVisibility(step == STEP_DETAILS ? View.VISIBLE : View.GONE);
        stepCaption.setVisibility(step == STEP_CAPTION ? View.VISIBLE : View.GONE);
    }

    // ------------------------------------------------------------------
    // PAGE 8B — picker
    // ------------------------------------------------------------------

    private void setupPickerStep() {
        bindClick(R.id.btn_picker_close, v -> finish());
        bindClick(R.id.btn_picker_next, v -> {
            if (selectedUris.isEmpty()) return;
            if (pickerMode == PICKER_MODE_STORY) {
                openStoryEditor(selectedUris.get(0));
                return;
            }
            refreshEditor();
            showStep(STEP_EDITOR);
        });

        // The 'Recents' control opens the system photo picker: a second, permission-free
        // way into the gallery when the MediaStore permission was declined.
        // The system picker is the only entry that offers clips: the in-app grid and the
        // carousel flow are photo-only, so a video arrives here as a single selection.
        bindClick(R.id.btn_picker_album, v -> pickMediaLauncher.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageAndVideo.INSTANCE)
                        .build()));

        btnPickerMulti.setOnClickListener(v -> setMultiSelect(!multiSelect));

        // Existing system photo picker stays the selection entry point
        bindClick(R.id.btn_pick_image, v -> pickMultipleMediaLauncher.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()));

        // Fit / fill toggle on the preview
        bindClick(R.id.btn_picker_expand, v -> {
            previewFill = !previewFill;
            ivPreview.setScaleType(previewFill
                    ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
        });

        // Rule-of-thirds guides while dragging / zooming
        ivPreview.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                layoutPickerGridLines.setVisibility(View.VISIBLE);
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                layoutPickerGridLines.setVisibility(View.GONE);
            }
            return false;
        });

        pickerAdapter = new PickerGridAdapter();
        rvPickerGrid.setLayoutManager(new GridLayoutManager(this, 4));
        rvPickerGrid.addItemDecoration(new GridGapDecoration(4,
                Math.round(getResources().getDisplayMetrics().density * 1f)));
        rvPickerGrid.setAdapter(pickerAdapter);

        // The bottom label is POST only: Story and Reel have their own entries in
        // the Home sheet and their own screens, so this picker no longer advertises
        // a mode it does not implement.

        updatePickerActions();
    }

    /** 'SELECT MULTIPLE' pill ⇄ 32dp blue circle with a white icon. */
    private void setMultiSelect(boolean multi) {
        multiSelect = multi;
        int circle = getResources().getDimensionPixelSize(R.dimen.picker_circle_button);
        ViewGroup.LayoutParams lp = btnPickerMulti.getLayoutParams();
        if (multi) {
            lp.width = circle;
            lp.height = circle;
            btnPickerMulti.setBackgroundResource(R.drawable.bg_picker_multi_active);
            btnPickerMulti.setPadding(0, 0, 0, 0);
            ivMultiIcon.setImageTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.white)));
            tvMultiLabel.setVisibility(View.GONE);
        } else {
            lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.height = getResources().getDimensionPixelSize(R.dimen.picker_select_pill_height);
            btnPickerMulti.setBackgroundResource(R.drawable.bg_picker_pill);
            int pad = getResources().getDimensionPixelSize(R.dimen.picker_select_pill_padding_horizontal);
            btnPickerMulti.setPadding(pad, 0, pad, 0);
            ivMultiIcon.setImageTintList(null);
            tvMultiLabel.setVisibility(View.VISIBLE);
        }
        btnPickerMulti.setLayoutParams(lp);
        pickerAdapter.notifyDataSetChanged();
    }

    /** 'POST' / 'STORY' / 'REEL' are the picker's three modes. */
    private static final int PICKER_MODE_POST = 0;
    private static final int PICKER_MODE_STORY = 1;
    private static final int PICKER_MODE_REEL = 2;
    private int pickerMode = PICKER_MODE_POST;

    /** Opens the PAGE 11 story editor, handing over the picked image when available. */
    private void openStoryEditor(@androidx.annotation.Nullable android.net.Uri uri) {
        startActivity(new android.content.Intent(this, com.insta.nova.ui.story2.StoryCameraActivity.class));
    }

    private void updatePickerActions() {
        boolean hasSelection = !selectedUris.isEmpty();
        btnPickerNext.setEnabled(hasSelection);
    }

    private void setPreview(Uri uri) {
        previewUri = uri;
        if (uri == null) {
            Glide.with(this).clear(ivPreview);
            return;
        }
        Glide.with(this)
                .load(uri)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        Log.e(TAG_GLIDE, "Failed to load local preview with Glide: " + e);
                        return false;
                    }
                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        return false;
                    }
                })
                .into(ivPreview);
    }

    /** Reads the device gallery. Declared READ_MEDIA_IMAGES only, no prompt. */
    private void loadDeviceGallery() {
        galleryUris.clear();
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED) {
            if (!mediaPermissionRequested) {
                mediaPermissionRequested = true;
                mediaPermissionLauncher.launch(android.Manifest.permission.READ_MEDIA_IMAGES);
            }
            pickerAdapter.notifyDataSetChanged();
            return;
        }
        String[] projection = {MediaStore.Images.Media._ID};
        try (Cursor cursor = getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, null, null,
                MediaStore.Images.Media.DATE_ADDED + " DESC")) {
            if (cursor != null) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                while (cursor.moveToNext() && galleryUris.size() < GALLERY_LIMIT) {
                    galleryUris.add(ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(idColumn)));
                }
            }
        } catch (Exception ignored) {
        }
        pickerAdapter.notifyDataSetChanged();
    }

    private void onThumbClick(Uri uri) {
        if (multiSelect) {
            if (selectedUris.contains(uri)) {
                selectedUris.remove(uri);
            } else if (selectedUris.size() < Constants.MAX_CAROUSEL_IMAGES) {
                selectedUris.add(uri);
            }
        } else {
            selectedUris.clear();
            selectedUris.add(uri);
        }
        setPreview(selectedUris.isEmpty() ? null : selectedUris.get(0));
        updatePickerActions();
        updateShareState();
        pickerAdapter.notifyDataSetChanged();
    }

    // ------------------------------------------------------------------
    // PAGE 9 — editor
    // ------------------------------------------------------------------

    private void setupEditorStep() {
        bindClick(R.id.btn_editor_close, v -> finish());
        bindClick(R.id.btn_editor_filters, v -> selectEditorTab(0));
        View btnAa = findViewById(R.id.btn_editor_aa);
        if (btnAa != null) btnAa.setVisibility(View.GONE);

        View.OnClickListener addMore = v -> pickMultipleMediaLauncher.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build());
        bindClick(R.id.btn_editor_gallery, addMore);
        bindClick(R.id.btn_editor_gallery_thumb, addMore);

        // Filter | Edit — the label inside each tab also takes the tap, so a touch that
        // lands on the text is never swallowed by the row's padding.
        View tabFilter = findViewById(R.id.btn_editor_tab_filter);
        if (tabFilter != null) {
            tabFilter.setOnClickListener(v -> selectEditorTab(0));
            View label = findViewById(R.id.tv_editor_filter);
            if (label != null) label.setOnClickListener(v -> selectEditorTab(0));
        }
        View tabEdit = findViewById(R.id.btn_editor_tab_edit);
        if (tabEdit != null) {
            tabEdit.setVisibility(View.VISIBLE);
            tabEdit.setOnClickListener(v -> selectEditorTab(1));
            View editLabel = findViewById(R.id.tv_editor_edit);
            if (editLabel != null) editLabel.setOnClickListener(v -> selectEditorTab(1));
        }
        setupAdjustPanel();
        View tabCrop = findViewById(R.id.btn_editor_tab_crop);
        if (tabCrop != null) tabCrop.setVisibility(View.GONE);

        bindClick(R.id.btn_editor_next, v -> {
            refreshDetails();
            showStep(STEP_DETAILS);
        });

        int cardWidth = Math.round(getResources().getDisplayMetrics().widthPixels * 0.88f);
        int cardHeight = Math.round(cardWidth * 5f / 4f);
        editorCardAdapter = new EditorCardAdapter(cardWidth, cardHeight);
        LinearLayoutManager cardsManager = new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false);
        rvEditorCards.setLayoutManager(cardsManager);
        rvEditorCards.addItemDecoration(new GapDecoration(
                getResources().getDimensionPixelSize(R.dimen.editor_card_gap)));
        new PagerSnapHelper().attachToRecyclerView(rvEditorCards);
        rvEditorCards.setAdapter(editorCardAdapter);

        filterAdapter = new FilterAdapter();
        rvFilterStrip.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        rvFilterStrip.setAdapter(filterAdapter);

    }

    private void selectEditorTab(int index) {
        underlineFilter.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        underlineEdit.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        underlineCrop.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        rvFilterStrip.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        if (layoutAdjustPanel != null) {
            layoutAdjustPanel.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        }
        if (index != 0 && layoutIntensity != null) layoutIntensity.setVisibility(View.GONE);
    }

    /** Filter strip + intensity slider + the eight tool chips. */
    private void setupAdjustPanel() {
        layoutIntensity = findViewById(R.id.layout_filter_intensity);
        layoutAdjustPanel = findViewById(R.id.layout_adjust_panel);
        tvIntensityLabel = findViewById(R.id.tv_intensity_label);
        tvAdjustLabel = findViewById(R.id.tv_adjust_label);
        seekAdjust = findViewById(R.id.seek_adjust);

        SeekBar seekIntensity = findViewById(R.id.seek_intensity);
        if (seekIntensity != null) {
            seekIntensity.setOnSeekBarChangeListener(new SimpleSeekListener() {
                @Override
                public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                    filterIntensity = progress / 100f;
                    tvIntensityLabel.setText("Intensity " + progress);
                    refreshPreviews();
                }
            });
        }

        LinearLayout tools = findViewById(R.id.layout_adjust_tools);
        if (tools != null) {
            float density = getResources().getDisplayMetrics().density;
            for (int i = 0; i < com.insta.nova.util.PhotoEffects.TOOLS.length; i++) {
                final int index = i;
                TextView chip = new TextView(this);
                chip.setText(com.insta.nova.util.PhotoEffects.TOOLS[i]);
                chip.setTextSize(12f);
                chip.setTextColor(getColorCompat(i == 0 ? R.color.white : R.color.chat_text_secondary));
                chip.setPadding((int) (12 * density), (int) (6 * density),
                        (int) (12 * density), (int) (6 * density));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.setMarginEnd((int) (8 * density));
                chip.setLayoutParams(lp);
                chip.setOnClickListener(v -> selectAdjustTool(index));
                tools.addView(chip);
            }
        }

        if (seekAdjust != null) {
            seekAdjust.setOnSeekBarChangeListener(new SimpleSeekListener() {
                @Override
                public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                    adjustments[activeAdjustTool] = (progress - 100) / 100f;
                    tvAdjustLabel.setText(com.insta.nova.util.PhotoEffects.labelFor(
                            com.insta.nova.util.PhotoEffects.TOOLS[activeAdjustTool], progress));
                    refreshPreviews();
                }
            });
        }
        selectAdjustTool(0);
    }

    private void selectAdjustTool(int index) {
        activeAdjustTool = index;
        View tools = findViewById(R.id.layout_adjust_tools);
        if (tools instanceof LinearLayout) {
            LinearLayout row = (LinearLayout) tools;
            for (int i = 0; i < row.getChildCount(); i++) {
                TextView chip = (TextView) row.getChildAt(i);
                chip.setTextColor(getColorCompat(i == index ? R.color.white : R.color.chat_text_secondary));
            }
        }
        if (seekAdjust != null) {
            int progress = Math.round(100 + adjustments[index] * 100);
            seekAdjust.setProgress(progress);
        }
        if (tvAdjustLabel != null) {
            tvAdjustLabel.setText(com.insta.nova.util.PhotoEffects.labelFor(
                    com.insta.nova.util.PhotoEffects.TOOLS[index], 100));
        }
    }

    private int getColorCompat(int res) {
        return androidx.core.content.ContextCompat.getColor(this, res);
    }

    /** One filter for every preview surface, so filter + intensity + tools always agree. */
    private android.graphics.ColorMatrixColorFilter currentFilter() {
        if (selectedFilter <= 0 || selectedFilter >= filterPresets.size()) {
            if (hasAdjustments()) {
                return com.insta.nova.util.PhotoEffects.build(null, 0f, adjustments);
            }
            return null;
        }
        return com.insta.nova.util.PhotoEffects.build(
                filterPresets.get(selectedFilter), filterIntensity, adjustments);
    }

    private boolean hasAdjustments() {
        for (float v : adjustments) if (v != 0f) return true;
        return false;
    }

    private void refreshPreviews() {
        if (editorCardAdapter != null) editorCardAdapter.notifyDataSetChanged();
        filterAdapter.notifyDataSetChanged();
        if (detailsPhotoAdapter != null) detailsPhotoAdapter.notifyDataSetChanged();
    }

    /** SeekBar adapter: Android has no default no-op implementation in this API level. */
    private abstract static class SimpleSeekListener
            implements android.widget.SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(android.widget.SeekBar bar) { }
        @Override public void onStopTrackingTouch(android.widget.SeekBar bar) { }
    }

    private void refreshEditor() {
        if (isVideoPost) {
            showVideoEditor();
            return;
        }
        editorCardAdapter.notifyDataSetChanged();
        filterAdapter.notifyDataSetChanged();
        if (!selectedUris.isEmpty()) {
            Glide.with(this).load(selectedUris.get(0)).centerCrop().into(ivEditorGalleryThumb);
        }
        selectEditorTab(0);
    }

    // ------------------------------------------------------------------
    // Single-video post: preview, trim, mute, cover
    // ------------------------------------------------------------------

    private boolean isVideoUri(Uri uri) {
        if (uri == null) return false;
        String type = getContentResolver().getType(uri);
        if (type != null && type.startsWith("video/")) return true;
        if (type != null && type.startsWith("image/")) return false;
        String path = uri.getLastPathSegment();
        return path != null && path.toLowerCase(java.util.Locale.US)
                .matches(".*\\.(mp4|mov|m4v|3gp|mkv|webm)$");
    }

    /** Reads the clip's duration and seeds the trim range to the whole file. */
    private void prepareVideoSegment(Uri uri) {
        videoUri = uri;
        videoDurationMs = 0L;
        try (android.content.res.AssetFileDescriptor afd =
                     getContentResolver().openAssetFileDescriptor(uri, "r")) {
            long size = afd != null ? afd.getLength() : 0L;
            if (size > 120L * 1024 * 1024) {
                toast(getString(R.string.post_video_too_big));
            }
        } catch (Exception ignored) {
        }
        try (android.content.res.AssetFileDescriptor afd =
                     getContentResolver().openAssetFileDescriptor(uri, "r")) {
            android.media.MediaMetadataRetriever retriever = new android.media.MediaMetadataRetriever();
            try {
                retriever.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                String ms = retriever.extractMetadata(
                        android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
                if (ms != null) videoDurationMs = Long.parseLong(ms);
            } finally {
                retriever.release();
            }
        } catch (Exception e) {
            Log.w(TAG_API, "video duration failed: " + e.getMessage());
        }
        if (videoDurationMs <= 0L) videoDurationMs = 1L;
        videoTrimStartMs = 0L;
        videoTrimEndMs = videoDurationMs;
        videoCoverMs = -1L;
        videoMuted = false;
        uploadedVideoUrl = null;
    }

    private String fmtVideoTime(long ms) {
        long totalSeconds = Math.max(0L, ms) / 1000L;
        return String.format(java.util.Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    private void showVideoEditor() {
        if (videoScreen == null) buildVideoEditor();
        if (videoScreen == null) return;
        videoScreen.setVisibility(View.VISIBLE);
        startVideoPreview();
    }

    /**
     * Full-screen clip editor. It replaces the photo editor for a single video: the
     * carousel, filter strip and crop tools all work on bitmaps, so they have nothing to
     * offer here.
     */
    private void buildVideoEditor() {
        if (videoUri == null) return;
        ViewGroup host = findViewById(android.R.id.content);
        if (host == null) return;

        videoScreen = new android.widget.FrameLayout(this);
        videoScreen.setBackgroundColor(android.graphics.Color.BLACK);
        host.addView(videoScreen, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Top bar: back + Next
        android.widget.LinearLayout top = new android.widget.LinearLayout(this);
        top.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        top.setGravity(android.view.Gravity.CENTER_VERTICAL);
        top.setPadding(dp(16), dp(36), dp(16), 0);

        android.widget.TextView back = glyph("\u2715", 22);
        back.setContentDescription(getString(R.string.post_video_back));
        back.setOnClickListener(v -> {
            stopVideoPreview();
            showStep(STEP_PICKER);
        });
        top.addView(back, new android.widget.LinearLayout.LayoutParams(dp(44), dp(44)));

        View spacer = new View(this);
        top.addView(spacer, new android.widget.LinearLayout.LayoutParams(0, 1, 1f));

        android.widget.TextView next = glyph(getString(R.string.post_video_next), 14);
        next.setOnClickListener(v -> {
            stopVideoPreview();
            showStep(STEP_DETAILS);
        });
        top.addView(next, new android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)));
        videoScreen.addView(top, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // The clip itself, letterboxed in the middle of the screen.
        android.widget.FrameLayout media = new android.widget.FrameLayout(this);
        media.setBackgroundColor(android.graphics.Color.BLACK);
        android.widget.FrameLayout.LayoutParams mediaLp = new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mediaLp.gravity = android.view.Gravity.CENTER;
        mediaLp.topMargin = dp(56);
        mediaLp.bottomMargin = dp(190);
        videoScreen.addView(media, mediaLp);

        videoPreview = new android.widget.VideoView(this);
        media.addView(videoPreview, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        videoPreview.setOnPreparedListener(mp -> {
            videoPlayer = mp;
            mp.setLooping(true);
            mp.setVolume(videoMuted ? 0f : 1f, videoMuted ? 0f : 1f);
            videoPreview.seekTo((int) videoTrimStartMs);
            videoPreview.start();
        });

        // Bottom controls: mute, cover, trim range.
        android.widget.LinearLayout controls = new android.widget.LinearLayout(this);
        controls.setOrientation(android.widget.LinearLayout.VERTICAL);
        controls.setPadding(dp(16), dp(10), dp(16), dp(30));
        android.widget.FrameLayout.LayoutParams controlsLp =
                new android.widget.FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        controlsLp.gravity = android.view.Gravity.BOTTOM;
        videoScreen.addView(controls, controlsLp);

        android.widget.LinearLayout actions = new android.widget.LinearLayout(this);
        actions.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        actions.setGravity(android.view.Gravity.CENTER_VERTICAL);

        videoMuteButton = glyph(getString(R.string.post_video_mute_off), 13);
        videoMuteButton.setOnClickListener(v -> {
            videoMuted = !videoMuted;
            videoMuteButton.setText(getString(videoMuted
                    ? R.string.post_video_mute_on : R.string.post_video_mute_off));
            if (videoPlayer != null) {
                videoPlayer.setVolume(videoMuted ? 0f : 1f, videoMuted ? 0f : 1f);
            }
        });
        actions.addView(videoMuteButton, new android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)));

        View gap = new View(this);
        actions.addView(gap, new android.widget.LinearLayout.LayoutParams(0, 1, 1f));

        android.widget.TextView cover = glyph(getString(R.string.post_video_cover), 13);
        cover.setOnClickListener(v -> {
            // The poster is taken from wherever the playhead is, which is the frame the
            // author is looking at.
            videoCoverMs = videoPreview != null ? videoPreview.getCurrentPosition() : -1L;
            toast(getString(R.string.post_video_cover_picked, fmtVideoTime(videoCoverMs)));
        });
        actions.addView(cover, new android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)));
        controls.addView(actions);

        videoTrimLabel = glyph(fmtVideoTime(videoTrimStartMs) + " – "
                + fmtVideoTime(videoTrimEndMs), 12);
        controls.addView(videoTrimLabel);

        android.widget.SeekBar startBar = new android.widget.SeekBar(this);
        startBar.setMax((int) Math.max(1L, videoDurationMs));
        startBar.setProgress((int) videoTrimStartMs);
        startBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar sb, int value, boolean fromUser) {
                if (!fromUser) return;
                videoTrimStartMs = Math.min(value, videoTrimEndMs - 500L);
                updateTrimLabel();
                if (videoPreview != null) videoPreview.seekTo((int) videoTrimStartMs);
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar sb) { }
            @Override public void onStopTrackingTouch(android.widget.SeekBar sb) { }
        });
        controls.addView(startBar);

        android.widget.SeekBar endBar = new android.widget.SeekBar(this);
        endBar.setMax((int) Math.max(1L, videoDurationMs));
        endBar.setProgress((int) videoTrimEndMs);
        endBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar sb, int value, boolean fromUser) {
                if (!fromUser) return;
                videoTrimEndMs = Math.max(value, videoTrimStartMs + 500L);
                updateTrimLabel();
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar sb) { }
            @Override public void onStopTrackingTouch(android.widget.SeekBar sb) { }
        });
        controls.addView(endBar);
    }

    private void updateTrimLabel() {
        if (videoTrimLabel != null) {
            videoTrimLabel.setText(fmtVideoTime(videoTrimStartMs) + " – "
                    + fmtVideoTime(videoTrimEndMs));
        }
    }

    private void startVideoPreview() {
        if (videoPreview == null || videoUri == null) return;
        try {
            videoPreview.setVideoURI(videoUri);
            videoPreview.seekTo((int) videoTrimStartMs);
            videoPreview.start();
        } catch (Exception e) {
            Log.w(TAG_API, "video preview failed: " + e.getMessage());
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** Small text button used by the clip editor. */
    private android.widget.TextView glyph(String text, int sp) {
        android.widget.TextView view = new android.widget.TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(android.graphics.Color.WHITE);
        view.setGravity(android.view.Gravity.CENTER);
        view.setPadding(dp(8), 0, dp(8), 0);
        return view;
    }

    private void stopVideoPreview() {
        if (videoPreview == null) return;
        try {
            if (videoPreview.isPlaying()) videoPreview.pause();
        } catch (Exception ignored) {
        }
    }

    /**
     * Copies the chosen clip (trimmed when the range was narrowed) into the cache and
     * uploads it, then builds a poster JPEG for the feed.
     */
    private void uploadVideoThenContinue() {
        final byte[] videoBytes;
        final String mime;
        final long durationMs;
        try {
            File source = copyToCache(videoUri, "post_clip_src.mp4");
            boolean narrowed = videoTrimStartMs > 250L
                    || (videoDurationMs - videoTrimEndMs) > 250L;
            File toUpload = source;
            // A fresh name every time: reusing one path left a stale 3 KB stub behind when
            // the previous muxer had not fully released the file, and the fallback guard then
            // silently published the whole clip.
            File trimmed = new File(getCacheDir(),
                    "post_clip_trim_" + System.currentTimeMillis() + ".mp4");
            // Muting is a copy too: the audio samples are simply left out, so the toggle is
            // honoured without re-encoding the clip.
            if (narrowed || videoMuted) {
                boolean ok = com.insta.nova.util.VideoTrim.trim(
                        source, trimmed, videoTrimStartMs * 1000L, videoTrimEndMs * 1000L,
                        videoMuted);
                // A copy that lost most of the clip is worse than the untrimmed original.
                if (ok && trimmed.length() > source.length() * 0.15) {
                    toUpload = trimmed;
                } else {
                    Log.w(TAG_API, "trim produced nothing, uploading the full clip");
                }
            }
            videoBytes = readFileBytes(toUpload);
            // Only report the trimmed length when the cut actually produced the file that
            // goes up; otherwise the row would claim a duration the clip does not have.
            // A copy-only cut can only start on a key frame, so the clip may begin earlier
            // than the requested point; the row records what the file actually contains.
            durationMs = toUpload == trimmed
                    ? Math.max(1L, videoTrimEndMs
                            - com.insta.nova.util.VideoTrim.lastCutUs / 1000L)
                    : videoDurationMs;
            mime = "video/mp4";
        } catch (Exception e) {
            fail("Could not read the clip: " + e.getMessage());
            return;
        }

        final String path = currentUserId + "/" + UUID.randomUUID() + ".mp4";
        RequestBody body = RequestBody.create(MediaType.parse(mime), videoBytes);
        storageApi.uploadFile("posts", path, body).enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> call, Response<Void> response) {
                if (!response.isSuccessful()) {
                    fail("Clip upload failed (HTTP " + response.code() + "): " + parseError(response));
                    return;
                }
                uploadedVideoUrl = com.insta.nova.util.MediaUrl.normalize("posts",
                        getPublicUrl("posts", path));
                Log.d(TAG_API, "clip uploaded: " + uploadedVideoUrl + " (" + durationMs + " ms)");
                uploadVideoCover(durationMs);
            }

            @Override public void onFailure(Call<Void> call, Throwable t) {
                failNetwork(t);
            }
        });
    }

    /** Grabs the poster frame and stores it, then hands over to the row creation. */
    private void uploadVideoCover(long durationMs) {
        byte[] jpeg;
        try {
            long atMs = videoCoverMs >= 0 ? videoCoverMs : videoTrimStartMs;
            jpeg = extractFrameJpeg(videoUri, atMs);
        } catch (Exception e) {
            Log.w(TAG_API, "cover extraction failed: " + e.getMessage());
            jpeg = null;
        }
        if (jpeg == null || jpeg.length == 0) {
            // A missing poster is better than a failed post: the clip is already up, so the
            // row is created without a thumbnail_url.
            Log.w(TAG_API, "no poster frame; publishing without a thumbnail");
            createVideoPostRecord(durationMs);
            return;
        }
        final byte[] bytes = jpeg;
        final String path = currentUserId + "/" + UUID.randomUUID() + "_cover.jpg";
        RequestBody body = RequestBody.create(MediaType.parse("image/jpeg"), bytes);
        storageApi.uploadFile("posts", path, body).enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    String url = com.insta.nova.util.MediaUrl.normalize("posts",
                            getPublicUrl("posts", path));
                    uploadedPublicUrls.clear();
                    uploadedPublicUrls.add(url);
                    uploadedPaths.add(path);
                } else {
                    Log.w(TAG_API, "cover upload HTTP " + response.code());
                }
                createVideoPostRecord(durationMs);
            }

            @Override public void onFailure(Call<Void> call, Throwable t) {
                Log.w(TAG_API, "cover upload failed: " + t.getMessage());
                createVideoPostRecord(durationMs);
            }
        });
    }

    private byte[] extractFrameJpeg(Uri uri, long atMs) throws IOException {
        android.media.MediaMetadataRetriever retriever = new android.media.MediaMetadataRetriever();
        try {
            try (android.content.res.AssetFileDescriptor afd =
                         getContentResolver().openAssetFileDescriptor(uri, "r")) {
                if (afd == null) return null;
                retriever.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                android.graphics.Bitmap frame =
                        retriever.getFrameAtTime(atMs * 1000L,
                                android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                if (frame == null) {
                    frame = retriever.getFrameAtTime();
                }
                if (frame == null) return null;
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                frame.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out);
                frame.recycle();
                return out.toByteArray();
            }
        } finally {
            retriever.release();
        }
    }

    private File copyToCache(Uri uri, String name) throws IOException {
        File out = new File(getCacheDir(), name);
        try (java.io.InputStream in = getContentResolver().openInputStream(uri);
             java.io.OutputStream os = new java.io.FileOutputStream(out)) {
            if (in == null) throw new IOException("cannot open " + uri);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) os.write(buffer, 0, read);
        }
        return out;
    }

    private byte[] readFileBytes(File file) throws IOException {
        try (java.io.InputStream in = new java.io.FileInputStream(file);
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
            return out.toByteArray();
        }
    }

    /** Creates the video row through the RPC, which is the only write that carries a clip. */
    private void createVideoPostRecord(long durationMs) {
        String caption = etCaption != null ? etCaption.getText().toString().trim() : "";
        final long expiresAtMillis = postOptions.expires()
                ? postOptions.expiresAtMillis(System.currentTimeMillis()) : 0L;
        Integer optionsDuration = postOptions.durationSecondsOrNull();
        final Integer durationSeconds = optionsDuration != null
                ? optionsDuration : (int) Math.max(1L, Math.round(durationMs / 1000.0));

        Map<String, Object> body = new HashMap<>();
        body.put("p_caption", caption);
        body.put("p_media_urls", new ArrayList<>(uploadedPublicUrls));
        body.put("p_is_reel", false);
        body.put("p_video_url", uploadedVideoUrl);
        body.put("p_thumbnail_url",
                uploadedPublicUrls.isEmpty() ? null : uploadedPublicUrls.get(0));
        body.put("p_is_anonymous", postOptions.anonymous);
        body.put("p_expires_at", expiresAtMillis > 0L
                ? com.insta.nova.util.TimeUtils.toSupabaseTimestamp(expiresAtMillis) : null);
        body.put("p_duration_seconds", durationSeconds);
        body.put("p_hide_like_count", postOptions.hideLikeCount);
        body.put("p_comments_disabled", postOptions.commentsDisabled);
        body.put("p_location", postLocation == null || postLocation.trim().isEmpty()
                ? null : postLocation.trim());
        body.put("p_music_url", uploadedMusicUrl);
        body.put("p_music_title", musicTitle);

        novaApi.createInstagramPost(body).enqueue(new Callback<JsonElement>() {
            @Override public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                if (!response.isSuccessful()) {
                    fail("Failed to create post (HTTP " + response.code() + "): "
                            + parseError(response));
                    return;
                }
                com.insta.nova.data.NovaPostQueue.complete(CreatePostActivity.this, pendingQueueId);
                pendingQueueId = null;
                String newPostId = null;
                if (response.body() != null && response.body().isJsonObject()) {
                    JsonElement idEl = response.body().getAsJsonObject().get("id");
                    if (idEl != null && !idEl.isJsonNull()) newPostId = idEl.getAsString();
                }
                if (newPostId != null) applyTags(newPostId);
                toast("Post shared!");
                finishPostCreated();
            }

            @Override public void onFailure(Call<JsonElement> call, Throwable t) {
                failNetwork(t);
            }
        });
    }

    // ------------------------------------------------------------------
    // PAGE 10 — details + caption
    // ------------------------------------------------------------------

    private void setupDetailsStep() {
        bindClick(R.id.btn_details_back, v -> {
            refreshEditor();
            showStep(STEP_EDITOR);
        });

        // Tapping the caption area opens the caption screen
        if (etCaption != null) {
            etCaption.setOnClickListener(v -> {
                etCaptionScreen.setText(etCaption.getText());
                showStep(STEP_CAPTION);
                etCaptionScreen.requestFocus();
                etCaptionScreen.post(() -> {
                    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (imm != null) imm.showSoftInput(etCaptionScreen, InputMethodManager.SHOW_IMPLICIT);
                });
            });
        }

        detailsPhotoAdapter = new DetailsPhotoAdapter();
        rvDetailsPhotos.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        rvDetailsPhotos.setAdapter(detailsPhotoAdapter);

        btnPublish.setOnClickListener(v -> publishPost());

        // Design-only rows
        int[] decorativeRows = {
                R.id.row_ai_label,
                R.id.row_audience, R.id.row_also_share, R.id.row_more_options,
                R.id.btn_chip_poll, R.id.btn_chip_prompt, R.id.switch_ai_label
        };
        for (int id : decorativeRows) {
            final View row = findViewById(id);
            if (row != null) {
                row.setVisibility(View.GONE);
            }
        }

        // Tag people is a real row now — its setting (Tags and mentions) was
        // otherwise controlling a feature that did not exist.
        View tagRow = findViewById(R.id.row_tag_people);
        if (tagRow != null) {
            tagRow.setVisibility(View.VISIBLE);
            tagRow.setOnClickListener(v -> openTagPicker());
        }
        // Add music: there is no licensed catalogue in the app, so the row takes an
        // audio file off the device and uploads it with the post.
        View musicRow = findViewById(R.id.row_add_music);
        if (musicRow != null) {
            musicRow.setVisibility(View.VISIBLE);
            musicRow.setOnClickListener(v -> pickMusic());
        }
        paintMusicValue();

        // Add location is a real row: the text is written to posts.location in
        // the same statement that creates the post.
        View locationRow = findViewById(R.id.row_add_location);
        if (locationRow != null) {
            locationRow.setVisibility(View.VISIBLE);
            locationRow.setOnClickListener(v -> askForLocation());
        }
        paintLocationValue();
    }

    /** The track name on the "Add music" row. */
    private void paintMusicValue() {
        TextView tv = findViewById(R.id.tv_music_value);
        if (tv == null) return;
        if (musicTitle == null || musicTitle.trim().isEmpty()) {
            tv.setText("");
            tv.setVisibility(View.GONE);
            return;
        }
        tv.setText(musicTitle);
        tv.setVisibility(View.VISIBLE);
    }

    /** Any audio file the device can open; the system picker grants read access. */
    private void pickMusic() {
        android.content.Intent intent = new android.content.Intent(
                android.content.Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQ_MUSIC);
        } catch (android.content.ActivityNotFoundException e) {
            toast("No file picker on this device");
        }
    }

    /** Opens a document uri and keeps the read grant across the process. */
    private String displayNameFor(android.net.Uri uri) {
        try (android.database.Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String name = c.getString(idx);
                    if (name != null && !name.trim().isEmpty()) return name.trim();
                }
            }
        } catch (Exception ignored) {
        }
        String last = uri.getLastPathSegment();
        return last == null ? "Audio" : last;
    }

    /** The value line on the "Add location" row. */
    private void paintLocationValue() {
        if (tvLocationValue == null) return;
        if (postLocation == null || postLocation.trim().isEmpty()) {
            tvLocationValue.setText("");
            tvLocationValue.setVisibility(View.GONE);
            return;
        }
        tvLocationValue.setText(postLocation.trim());
        tvLocationValue.setVisibility(View.VISIBLE);
    }

    /** Free-text location, the same shape Instagram stores in posts.location. */
    private void askForLocation() {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint(R.string.details_add_location);
        input.setSingleLine(true);
        if (postLocation != null) input.setText(postLocation);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout wrap = new android.widget.FrameLayout(this);
        wrap.setPadding(pad, pad / 2, pad, 0);
        wrap.addView(input);

        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.details_add_location)
                .setView(wrap)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String value = input.getText().toString().trim();
                    postLocation = value.isEmpty() ? null : value;
                    paintLocationValue();
                    paintOptionsSummary();
                })
                .setNeutralButton(com.insta.nova.R.string.post_options_remove,
                        (d, w) -> {
                            postLocation = null;
                            paintLocationValue();
                            paintOptionsSummary();
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void refreshDetails() {
        detailsPhotoAdapter.notifyDataSetChanged();
    }

    private void setupCaptionStep() {
        bindClick(R.id.btn_caption_ok, v -> {
            if (etCaption != null && etCaptionScreen != null) {
                etCaption.setText(etCaptionScreen.getText());
            }
            // Drop the field focus and the keyboard before returning
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null && etCaptionScreen != null) {
                imm.hideSoftInputFromWindow(etCaptionScreen.getWindowToken(), 0);
                etCaptionScreen.clearFocus();
            }
            showStep(STEP_DETAILS);
        });
        View btnCapEdit = findViewById(R.id.btn_caption_edit);
        if (btnCapEdit != null) btnCapEdit.setVisibility(View.GONE);

        // The shared Post Options module, opened for the Instagram surface. The
        // values it returns are the post's own anonymous / expiry values.
        bindClick(R.id.row_caption_post_options, v ->
                PostComposerOptionsSheet.show(this, PostComposerOptionsSheet.SURFACE_INSTAGRAM,
                        postOptions, chosen -> {
                            postOptions.anonymous = chosen.anonymous;
                            postOptions.expiry = chosen.expiry;
                            // The custom window lives here; without it a Custom choice had
                            // no length and the summary showed nothing.
                            postOptions.customSeconds = chosen.customSeconds;
                            postOptions.hideLikeCount = chosen.hideLikeCount;
                            postOptions.commentsDisabled = chosen.commentsDisabled;
                            paintOptionsSummary();
                        }));

        // The Nova copy is opt-in and starts off.
        if (switchShareToNova != null) {
            switchShareToNova.setChecked(false);
            switchShareToNova.setOnCheckedChangeListener((button, checked) ->
                    shareToNova = checked);
        }
        // Tapping anywhere on the row flips the switch, so the row and the
        // control never disagree.
        bindClick(R.id.row_caption_share_to_nova, v -> {
            if (switchShareToNova != null) {
                switchShareToNova.setChecked(!switchShareToNova.isChecked());
            }
        });

        paintOptionsSummary();

        int[] decorative = {R.id.btn_caption_chip_poll, R.id.btn_caption_chip_prompt};
        for (int id : decorative) {
            final View chip = findViewById(id);
            if (chip != null) {
                chip.setVisibility(View.GONE);
            }
        }

        // #hashtags typed in the caption use the mention colour
        etCaptionScreen.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                styleHashtags(s);
            }
        });
    }

    private void styleHashtags(Editable editable) {
        if (editable == null) return;
        for (ForegroundColorSpan span : editable.getSpans(0, editable.length(), ForegroundColorSpan.class)) {
            editable.removeSpan(span);
        }
        int color = ContextCompat.getColor(this, R.color.color_mention);
        String text = editable.toString();
        Matcher matcher = HASHTAG_PATTERN.matcher(text);
        while (matcher.find()) {
            editable.setSpan(new ForegroundColorSpan(color), matcher.start(), matcher.end(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        // @mentions take the same accent colour, so the caption reads the way the
        // feed will render it. An e-mail address is not a mention.
        matcher = MENTION_PATTERN.matcher(text);
        while (matcher.find()) {
            editable.setSpan(new ForegroundColorSpan(color), matcher.start(), matcher.end(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    /** True when the sheet has actually chosen something worth writing. */
    private boolean hasPostOptions() {
        return postOptions.anonymous || postOptions.expires()
                || postOptions.hideLikeCount || postOptions.commentsDisabled
                || (postLocation != null && !postLocation.trim().isEmpty())
                || musicUri != null;
    }

    /** The one-line summary under the "Post options" row; hidden when empty. */
    private void paintOptionsSummary() {
        if (tvCaptionOptionsSummary == null) return;
        List<String> parts = new ArrayList<>();
        if (postOptions.anonymous) {
            parts.add(getString(R.string.post_options_ig_summary_anonymous));
        }
        if (postOptions.hideLikeCount) {
            parts.add(getString(R.string.post_options_ig_summary_hide_likes));
        }
        if (postOptions.commentsDisabled) {
            parts.add(getString(R.string.post_options_ig_summary_comments_off));
        }
        if (postLocation != null && !postLocation.trim().isEmpty()) {
            parts.add(postLocation.trim());
        }
        switch (postOptions.expiry) {
            case PostComposerOptionsSheet.EXPIRY_ONE_HOUR:
                parts.add(getString(R.string.post_options_ig_summary_1h));
                break;
            case PostComposerOptionsSheet.EXPIRY_SIX_HOURS:
                parts.add(getString(R.string.post_options_ig_summary_6h));
                break;
            case PostComposerOptionsSheet.EXPIRY_ONE_DAY:
                parts.add(getString(R.string.post_options_ig_summary_24h));
                break;
            case PostComposerOptionsSheet.EXPIRY_SEVEN_DAYS:
                parts.add(getString(R.string.post_options_ig_summary_7d));
                break;
            default:
                break;
        }
        if (parts.isEmpty()) {
            tvCaptionOptionsSummary.setVisibility(View.GONE);
            return;
        }
        tvCaptionOptionsSummary.setVisibility(View.VISIBLE);
        tvCaptionOptionsSummary.setText(TextUtils.join(" \u00b7 ", parts));
    }

    // ------------------------------------------------------------------
    // Adapters
    // ------------------------------------------------------------------

    private class PickerGridAdapter extends RecyclerView.Adapter<PickerGridAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_picker_thumb, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Uri uri = galleryUris.get(position);
            Glide.with(CreatePostActivity.this).load(uri).centerCrop().into(holder.thumb);

            int index = selectedUris.indexOf(uri);
            boolean selected = index >= 0;

            holder.dim.setVisibility(!multiSelect && selected ? View.VISIBLE : View.GONE);
            holder.index.setVisibility(multiSelect && selected ? View.VISIBLE : View.GONE);
            holder.ring.setVisibility(multiSelect && !selected ? View.VISIBLE : View.GONE);
            if (multiSelect && selected) {
                holder.index.setText(String.valueOf(index + 1));
            }
            holder.itemView.setOnClickListener(v -> onThumbClick(uri));
        }

        @Override
        public int getItemCount() {
            return galleryUris.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final ImageView thumb;
            final View dim;
            final View ring;
            final TextView index;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                thumb = itemView.findViewById(R.id.iv_thumb);
                dim = itemView.findViewById(R.id.view_thumb_dim);
                ring = itemView.findViewById(R.id.view_thumb_ring);
                index = itemView.findViewById(R.id.tv_thumb_index);
            }
        }
    }

    private class EditorCardAdapter extends RecyclerView.Adapter<EditorCardAdapter.ViewHolder> {
        private final int cardWidth;
        private final int cardHeight;

        EditorCardAdapter(int cardWidth, int cardHeight) {
            this.cardWidth = cardWidth;
            this.cardHeight = cardHeight;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_editor_card, parent, false);
            v.setLayoutParams(new RecyclerView.LayoutParams(cardWidth, cardHeight));
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            if (position < 0 || position >= selectedUris.size()) return;
            Uri uri = selectedUris.get(position);
            // The edit preview must show the whole frame: a centre-crop here cut the sides off
            // tall/wide images (a square template card lost its margins) while the filter strip
            // below showed the same image complete.
            Glide.with(CreatePostActivity.this).load(uri).fitCenter().into(holder.card);
            android.graphics.ColorMatrixColorFilter preview = currentFilter();
            if (preview != null) {
                holder.card.setColorFilter(preview);
            } else {
                holder.card.clearColorFilter();
            }
            holder.remove.setOnClickListener(v -> {
                selectedUris.remove(uri);
                if (selectedUris.isEmpty()) {
                    showStep(STEP_PICKER);
                }
                notifyDataSetChanged();
                refreshEditor();
                return;
            });
            holder.expand.setOnClickListener(v -> {
                holder.fill = !holder.fill;
                holder.card.setScaleType(holder.fill
                        ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
            });
        }

        @Override
        public int getItemCount() {
            return selectedUris.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final ImageView card;
            final View remove;
            final View expand;
            boolean fill = true;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                card = itemView.findViewById(R.id.iv_editor_card);
                remove = itemView.findViewById(R.id.btn_editor_remove);
                expand = itemView.findViewById(R.id.btn_editor_expand);
            }
        }
    }

    private class FilterAdapter extends RecyclerView.Adapter<FilterAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_filter_preset, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ColorMatrixPresets.FilterPreset preset = filterPresets.get(position);
            holder.name.setText(preset.name);
            if (!selectedUris.isEmpty()) {
                Glide.with(CreatePostActivity.this).load(selectedUris.get(0))
                        .centerCrop().into(holder.thumb);
            }
            if (position == 0) {
                holder.thumb.clearColorFilter();
            } else {
                holder.thumb.setColorFilter(preset.getColorFilter(1f));
            }
            holder.border.setVisibility(position == selectedFilter ? View.VISIBLE : View.GONE);

            holder.itemView.setOnClickListener(v -> {
                if (position == selectedFilter) {
                    // Second tap on the active filter opens its intensity, Instagram-style.
                    if (position > 0 && layoutIntensity != null) {
                        layoutIntensity.setVisibility(
                                layoutIntensity.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                    }
                    return;
                }
                selectedFilter = position;
                filterIntensity = 1f;
                if (layoutIntensity != null) {
                    layoutIntensity.setVisibility(position > 0 ? View.VISIBLE : View.GONE);
                }
                SeekBar bar = findViewById(R.id.seek_intensity);
                if (bar != null) bar.setProgress(100);
                notifyDataSetChanged();
                editorCardAdapter.notifyDataSetChanged();
            });
        }

        @Override
        public int getItemCount() {
            return filterPresets.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final ImageView thumb;
            final TextView name;
            final View border;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                thumb = itemView.findViewById(R.id.iv_filter_thumb);
                name = itemView.findViewById(R.id.tv_filter_name);
                border = itemView.findViewById(R.id.view_filter_border);
            }
        }
    }

    private class DetailsPhotoAdapter extends RecyclerView.Adapter<DetailsPhotoAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_details_photo, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Uri uri = selectedUris.get(position);
            Glide.with(CreatePostActivity.this).load(uri).centerCrop().into(holder.photo);
            android.graphics.ColorMatrixColorFilter preview = currentFilter();
            if (preview != null) {
                holder.photo.setColorFilter(preview);
            } else {
                holder.photo.clearColorFilter();
            }
        }

        @Override
        public int getItemCount() {
            return selectedUris.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final ImageView photo;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                photo = itemView.findViewById(R.id.iv_details_photo);
            }
        }
    }

    /** Keeps the gallery columns equal while leaving a gap between the cells. */
    private static class GridGapDecoration extends RecyclerView.ItemDecoration {
        private final int spanCount;
        private final int gap;

        GridGapDecoration(int spanCount, int gap) {
            this.spanCount = spanCount;
            this.gap = gap;
        }

        @Override
        public void getItemOffsets(@NonNull android.graphics.Rect outRect, @NonNull View view,
                                   @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            int position = parent.getChildAdapterPosition(view);
            if (position == RecyclerView.NO_POSITION) return;
            int column = position % spanCount;
            outRect.left = gap * column / spanCount;
            outRect.right = gap * (spanCount - 1 - column) / spanCount;
            outRect.bottom = gap;
        }
    }

    /** Fixed gap between the editor cards. */
    private static class GapDecoration extends RecyclerView.ItemDecoration {
        private final int gap;

        GapDecoration(int gap) {
            this.gap = gap;
        }

        @Override
        public void getItemOffsets(@NonNull android.graphics.Rect outRect, @NonNull View view,
                                   @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            outRect.right = gap;
        }
    }

    // ------------------------------------------------------------------
    // Existing pick / upload path (unchanged)
    // ------------------------------------------------------------------

    private void setupPickers() {
        // The gallery grid reads MediaStore, which needs the runtime permission on
        // API 33+. Without asking for it the grid is silently empty.
        mediaPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> loadDeviceGallery());

        pickMultipleMediaLauncher = registerForActivityResult(
                new ActivityResultContracts.PickMultipleVisualMedia(Constants.MAX_CAROUSEL_IMAGES),
                uris -> {
                    if (uris != null && !uris.isEmpty()) {
                        selectedUris.clear();
                        selectedUris.addAll(uris);
                        onMediaSelected();
                    }
                }
        );

        pickMediaLauncher = registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(),
                uri -> {
                    if (uri != null) {
                        selectedUris.clear();
                        selectedUris.add(uri);
                        onMediaSelected();
                    }
                }
        );
    }

    private void onMediaSelected() {
        if (selectedUris.isEmpty()) return;
        isVideoPost = selectedUris.size() == 1 && isVideoUri(selectedUris.get(0));
        if (isVideoPost) {
            prepareVideoSegment(selectedUris.get(0));
        } else {
            videoUri = null;
        }
        setPreview(selectedUris.get(0));
        updatePickerActions();
        pickerAdapter.notifyDataSetChanged();
        updateShareState();
    }

    /** Share is live as soon as there is at least one photo and nothing is uploading. */
    private void updateShareState() {
        boolean enabled = !selectedUris.isEmpty();
        btnPublish.setEnabled(enabled);
        btnPublish.setAlpha(enabled ? 1f : 0.4f);
    }

    /** Row id of the write-ahead queue entry for the upload currently in flight. */
    private String pendingQueueId;

    private void publishPost() {
        if (selectedUris.isEmpty() || currentUserId == null) {
            toast("Please choose a photo after signing in.");
            return;
        }

        String caption = etCaption != null ? etCaption.getText().toString().trim() : "";
        if (!isOnline()) {
            // No connection: keep the post and its local copies, and let the worker replay it.
            java.util.List<String> paths = new java.util.ArrayList<>();
            for (android.net.Uri uri : selectedUris) paths.add(uri.toString());
            com.insta.nova.data.NovaPostQueue.enqueue(this, caption, "", paths);
            toast("Saved to your offline queue — it will upload when you are back online.");
            finish();
            return;
        }
        // Write the queue row before the first byte leaves the device. If this process dies mid
        // upload the row survives, and the next launch reclaims it — otherwise the post vanishes
        // with no error at all (proved by killing the app 0.4 s after Share).
        if (pendingQueueId == null) {
            java.util.List<String> queuePaths = new java.util.ArrayList<>();
            for (android.net.Uri uri : selectedUris) queuePaths.add(uri.toString());
            pendingQueueId = com.insta.nova.data.NovaPostQueue.enqueueUpload(this, caption, "", queuePaths);
        }
        if (!caption.isEmpty()) {
            // Nova Safe caption check before any bytes leave the device.
            com.insta.nova.safe.NovaModerationDialog.guard(this, "caption", caption, 3, false,
                    this::startUpload,
                    () -> {
                        if (etCaption != null) etCaption.requestFocus();
                    });
            return;
        }
        startUpload();
    }

    private void startUpload() {
        if (selectedUris.isEmpty() || currentUserId == null) return;
        setLoading(true);
        uploadedPaths.clear();
        uploadedPublicUrls.clear();
        uploadedMusicUrl = null;

        uploadMediaSequentially(0);
    }

    private void uploadMediaSequentially(int index) {
        if (isVideoPost) {
            uploadVideoThenContinue();
            return;
        }
        if (index >= selectedUris.size()) {
            // All images are up; the soundtrack goes next, then the row is created.
            if (musicUri != null && uploadedMusicUrl == null) {
                uploadMusic();
                return;
            }
            createPostRecord();
            return;
        }

        Uri uri = selectedUris.get(index);
        byte[] jpegBytes;
        try {
            jpegBytes = ImageUtils.processUriToJpegBytes(this, uri);
            // The picker's bytes are untouched by the preview; bake the look in so the
            // uploaded file matches what the editor showed.
            android.graphics.ColorMatrixColorFilter bakeFilter = currentFilter();
            if (bakeFilter != null || adjustments[com.insta.nova.util.PhotoEffects.VIGNETTE_INDEX] > 0.01f) {
                jpegBytes = com.insta.nova.util.PhotoEffects.bake(
                        jpegBytes, bakeFilter,
                        adjustments[com.insta.nova.util.PhotoEffects.VIGNETTE_INDEX]);
            }
        } catch (IOException e) {
            Log.e(TAG_API, "Failed to process image: " + e.getMessage(), e);
            fail("Failed to process image: " + e.getMessage());
            return;
        }

        if (jpegBytes == null || jpegBytes.length == 0) {
            fail("Image processing produced empty byte array");
            return;
        }

        final byte[] uploadBytes = jpegBytes;
        final String path = currentUserId + "/" + UUID.randomUUID() + ".jpg";
        final MediaType jpegType = MediaType.parse("image/jpeg");
        final int fileIndex = index;
        final int fileTotal = selectedUris.size();
        // The spinner alone never said whether a 4-photo post was moving, so the
        // bytes are reported as they leave the device.
        RequestBody body = new ProgressBody(uploadBytes, jpegType, pct ->
                runOnUiThread(() -> setUploadProgress(pct, fileIndex, fileTotal)));
        Log.d(TAG_API, "uploaded " + uploadBytes.length + " bytes to posts/" + path);

        storageApi.uploadFile("posts", path, body).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (!response.isSuccessful()) {
                    String err = parseError(response);
                    Log.e(TAG_API, "HTTP " + response.code() + " on uploadFile 'posts/" + path + "': " + err);
                    String msg;
                    if (response.code() == 403) msg = "403 Forbidden: Bucket policy error or invalid path.";
                    else if (response.code() == 404) msg = "404 Not Found: Bucket 'posts' does not exist.";
                    else if (response.code() == 401) msg = "401 Unauthorized: Invalid token.";
                    else msg = "Upload failed (HTTP " + response.code() + "): " + err;
                    fail(msg);
                    return;
                }

                String publicUrl = com.insta.nova.util.MediaUrl.normalize("posts", getPublicUrl("posts", path));
                Log.d(TAG_API, "HTTP 200 uploadFile posts/" + path + ". Verifying via HEAD...");

                okhttp3.Request headReq = new okhttp3.Request.Builder()
                        .url(publicUrl)
                        .head()
                        .build();

                okHttpClient.newCall(headReq).enqueue(new okhttp3.Callback() {
                    @Override
                    public void onResponse(@NonNull okhttp3.Call c, @NonNull okhttp3.Response headResponse) throws IOException {
                        int headCode = headResponse.code();
                        String contentType = headResponse.header("Content-Type");
                        String lenStr = headResponse.header("Content-Length");
                        long contentLength = -1;
                        try {
                            if (lenStr != null) {
                                contentLength = Long.parseLong(lenStr);
                            }
                        } catch (NumberFormatException ignored) {}
                        headResponse.close();

                        Log.d(TAG_API, "HEAD status=" + headCode + " Content-Type=" + contentType
                                + " Content-Length=" + contentLength + " expected=" + uploadBytes.length + " for " + publicUrl);

                        if (headCode == 200) {
                            runOnUiThread(() -> {
                                uploadedPaths.add(path);
                                uploadedPublicUrls.add(publicUrl);
                                verifyUrlWithGlide(publicUrl);
                                uploadMediaSequentially(index + 1);
                            });
                        } else {
                            runOnUiThread(() -> fail("HEAD verification failed (HTTP " + headCode + ") for " + publicUrl));
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
                Log.e(TAG_API, "Network failure uploading image: " + t.getMessage(), t);
                failNetwork(t);
            }
        });
    }

    /** Uploads the picked audio file and then continues to the post row. */
    private void uploadMusic() {
        byte[] bytes;
        try (java.io.InputStream in = getContentResolver().openInputStream(musicUri)) {
            if (in == null) throw new java.io.IOException("cannot open the audio file");
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            bytes = out.toByteArray();
        } catch (Exception e) {
            Log.e(TAG_API, "Reading the audio file failed: " + e.getMessage(), e);
            fail("Could not read the audio file: " + e.getMessage());
            return;
        }
        if (bytes.length == 0) {
            fail("The audio file is empty.");
            return;
        }

        String name = musicTitle == null ? "track.mp3" : musicTitle;
        int dot = name.lastIndexOf('.');
        String ext = dot > 0 ? name.substring(dot + 1).toLowerCase(java.util.Locale.US) : "mp3";
        final String path = currentUserId + "/music-" + UUID.randomUUID() + "." + ext;
        // A concrete type, not audio/*: Supabase stores the header it is given, and a
        // wildcard makes the object's contentType useless to every later consumer.
        String mime = android.webkit.MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(ext);
        if (mime == null || !mime.startsWith("audio/")) mime = "audio/" + ext;
        RequestBody body = RequestBody.create(MediaType.parse(mime), bytes);

        storageApi.uploadFile("posts", path, body).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (!response.isSuccessful()) {
                    Log.e(TAG_API, "HTTP " + response.code() + " uploading music");
                    runOnUiThread(() -> fail("Music upload failed (HTTP " + response.code() + ")"));
                    return;
                }
                uploadedMusicUrl = com.insta.nova.util.MediaUrl.normalize(
                        "posts", getPublicUrl("posts", path));
                Log.d(TAG_API, "Music uploaded: " + uploadedMusicUrl);
                runOnUiThread(CreatePostActivity.this::createPostRecord);
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e(TAG_API, "Network failure uploading music: " + t.getMessage(), t);
                runOnUiThread(() -> fail("Music upload failed: " + t.getMessage()));
            }
        });
    }

    private void verifyUrlWithGlide(String url) {
        Glide.with(getApplicationContext())
                .load(url)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        Log.e(TAG_GLIDE, "Failed to load public image from storage: " + url + " - " + e);
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        Log.d(TAG_GLIDE, "Successfully loaded public image with Glide: " + url);
                        return false;
                    }
                })
                .preload();
    }

    private boolean isOnline() {
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
        if (cm == null) return true;
        android.net.NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return caps != null && caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    /* ------------------------------------------------------------------ */
    /* Tagging — Settings > Tags and mentions governs this                  */
    /* ------------------------------------------------------------------ */

    private static final int REQ_TAG_PEOPLE = 4711;
    private static final int REQ_TAG_PLACE = 4712;
    private static final int REQ_MUSIC = 4713;
    private final java.util.List<String> taggedIds = new java.util.ArrayList<>();
    private final java.util.List<String> taggedNames = new java.util.ArrayList<>();
    /** Normalised positions on the first photo, parallel to taggedIds. */
    private final java.util.List<TagPlacementActivity.Point> taggedPoints = new java.util.ArrayList<>();

    /** Opens the picker. Nothing here is "design only" any more: the choice is
     *  persisted through set_post_tags once the post exists. */
    private void openTagPicker() {
        android.content.Intent intent = new android.content.Intent(this,
                com.insta.nova.ui.create.TagPeopleActivity.class);
        intent.putStringArrayListExtra(
                com.insta.nova.ui.create.TagPeopleActivity.EXTRA_PRESELECTED,
                new java.util.ArrayList<>(taggedIds));
        startActivityForResult(intent, REQ_TAG_PEOPLE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_TAG_PLACE) {
            if (resultCode == RESULT_OK && data != null) {
                float[] xs = data.getFloatArrayExtra(TagPlacementActivity.EXTRA_XS);
                float[] ys = data.getFloatArrayExtra(TagPlacementActivity.EXTRA_YS);
                if (xs != null && ys != null) {
                    for (int i = 0; i < taggedPoints.size() && i < xs.length; i++) {
                        taggedPoints.get(i).x = xs[i];
                        taggedPoints.get(i).y = ys[i];
                    }
                }
            }
            return;
        }
        if (requestCode == REQ_MUSIC) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                musicUri = data.getData();
                try {
                    getContentResolver().takePersistableUriPermission(musicUri,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (SecurityException ignored) {
                }
                musicTitle = displayNameFor(musicUri);
                paintMusicValue();
                paintOptionsSummary();
            }
            return;
        }
        if (requestCode != REQ_TAG_PEOPLE || resultCode != RESULT_OK || data == null) return;
        java.util.ArrayList<String> ids =
                data.getStringArrayListExtra(com.insta.nova.ui.create.TagPeopleActivity.EXTRA_SELECTED_IDS);
        java.util.ArrayList<String> names =
                data.getStringArrayListExtra(com.insta.nova.ui.create.TagPeopleActivity.EXTRA_SELECTED_NAMES);
        taggedIds.clear();
        taggedNames.clear();
        if (ids != null) taggedIds.addAll(ids);
        if (names != null) taggedNames.addAll(names);

        // The accounts are chosen, now they get a place on the photo. Without a
        // position a tag cannot be drawn on the post.
        taggedPoints.clear();
        if (ids != null) {
            java.util.List<TagPlacementActivity.Point> points = new java.util.ArrayList<>();
            for (int i = 0; i < ids.size(); i++) {
                String name = names != null && i < names.size() && names.get(i) != null
                        ? names.get(i) : "";
                points.add(new TagPlacementActivity.Point(ids.get(i), name, -1f, -1f));
            }
            // Hold the placeholders now: the placement screen only sends back the
            // coordinates, so without this the list is empty when the result arrives.
            taggedPoints.addAll(points);
            if (!selectedUris.isEmpty() && !points.isEmpty()) {
                // The placement runs on its own screen: an overlay lost the photo and
                // a dialog could not size it, so this is a normal Activity round trip.
                android.content.Intent place = new android.content.Intent(this,
                        TagPlacementActivity.class);
                place.putExtra(TagPlacementActivity.EXTRA_IMAGE_URI, selectedUris.get(0).toString());
                place.putStringArrayListExtra(TagPlacementActivity.EXTRA_IDS, new java.util.ArrayList<>(taggedIds));
                place.putStringArrayListExtra(TagPlacementActivity.EXTRA_NAMES, new java.util.ArrayList<>(taggedNames));
                startActivityForResult(place, REQ_TAG_PLACE);
            }
        }

        TextView label = findViewById(R.id.tvTagPeopleLabel);
        if (label != null) {
            if (taggedIds.isEmpty()) {
                label.setText(R.string.details_tag_people);
            } else {
                StringBuilder sb = new StringBuilder();
                for (String n : taggedNames) {
                    if (n == null || n.isEmpty()) continue;
                    if (sb.length() > 0) sb.append(", ");
                    sb.append("@").append(n);
                }
                label.setText(sb.length() == 0
                        ? getString(R.string.tag_people_count, taggedIds.size())
                        : sb.toString());
            }
        }
    }

    /** Attaches the chosen accounts to a freshly created post. */
    private void applyTags(String postId) {
        if (postId == null || taggedIds.isEmpty()) return;
        Map<String, Object> body = new HashMap<>();
        body.put("p_post", postId);
        body.put("p_users", new com.google.gson.Gson().toJsonTree(taggedIds));
        // Parallel to p_users: {x,y} in 0..1, or null inside the object when the
        // account was never placed on the photo.
        java.util.List<Map<String, Object>> coords = new java.util.ArrayList<>();
        for (int i = 0; i < taggedIds.size(); i++) {
            TagPlacementActivity.Point p = i < taggedPoints.size() ? taggedPoints.get(i) : null;
            Map<String, Object> c = new HashMap<>();
            c.put("x", p != null && p.placed() ? Double.valueOf(p.x) : null);
            c.put("y", p != null && p.placed() ? Double.valueOf(p.y) : null);
            coords.add(c);
        }
        body.put("p_coords", new com.google.gson.Gson().toJsonTree(coords));
        restApi.setPostTags(body).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (!response.isSuccessful()) {
                    Log.e(TAG_API, "setPostTags failed HTTP " + response.code());
                    Toast.makeText(CreatePostActivity.this,
                            getString(R.string.tag_people_failed,
                                    String.valueOf(response.code())),
                            Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e(TAG_API, "setPostTags network failure: " + t.getMessage(), t);
            }
        });
    }

    private void createPostRecord() {
        if (uploadedPublicUrls.isEmpty()) {
            fail("No uploaded images found.");
            return;
        }

        String caption = etCaption != null ? etCaption.getText().toString().trim() : "";

        // Post options or a Nova copy: the post needs a server-side identity pass
        // and its own expiry, so it goes through create_instagram_post instead.
        if (hasPostOptions() || shareToNova) {
            createPostRecordWithOptions(caption);
            return;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("user_id", currentUserId);
        body.put("caption", caption);
        body.put("thumbnail_url", uploadedPublicUrls.get(0));
        body.put("is_reel", false);
        body.put("space", postSpace);

        restApi.createPost(body).enqueue(new Callback<List<PostDto>>() {
            @Override
            public void onResponse(Call<List<PostDto>> call, Response<List<PostDto>> response) {
                if (!response.isSuccessful() || response.body() == null || response.body().isEmpty()) {
                    String err = parseError(response);
                    Log.e(TAG_API, "HTTP " + response.code() + " on createPost: " + err);
                    fail("Failed to create post (HTTP " + response.code() + "): " + err);
                    return;
                }

                String postId = response.body().get(0).getId();
                Log.d(TAG_API, "Created post with ID: " + postId);
                com.insta.nova.data.NovaPostQueue.complete(CreatePostActivity.this, pendingQueueId);
                pendingQueueId = null;
                createdPostId = postId;
                applyTags(postId);
                insertPostMedia(postId);
            }

            @Override
            public void onFailure(Call<List<PostDto>> call, Throwable t) {
                Log.e(TAG_API, "Network failure creating post: " + t.getMessage(), t);
                failNetwork(t);
            }
        });
    }

    /**
     * The second write path: the shared Post Options module is in play, so the post
     * is created by {@code create_instagram_post} instead of a plain insert.
     *
     * <p>The RPC inserts every post_media row itself, so {@link #insertPostMedia(String)}
     * is deliberately never called from here, and it creates the row under the
     * caller's own identity — which is what makes the optional Nova copy belong to
     * the signed-in user.
     */
    private void createPostRecordWithOptions(String caption) {
        final long expiresAtMillis = postOptions.expires()
                ? postOptions.expiresAtMillis(System.currentTimeMillis()) : 0L;
        final Integer durationSeconds = postOptions.durationSecondsOrNull();
        final String thumbnailUrl = uploadedPublicUrls.isEmpty() ? null : uploadedPublicUrls.get(0);

        Map<String, Object> body = new HashMap<>();
        body.put("p_caption", caption);
        body.put("p_media_urls", new ArrayList<>(uploadedPublicUrls));
        body.put("p_is_reel", false);
        body.put("p_video_url", null);
        body.put("p_thumbnail_url", thumbnailUrl);
        body.put("p_is_anonymous", postOptions.anonymous);
        body.put("p_expires_at", expiresAtMillis > 0L
                ? com.insta.nova.util.TimeUtils.toSupabaseTimestamp(expiresAtMillis) : null);
        body.put("p_duration_seconds", durationSeconds);
        body.put("p_hide_like_count", postOptions.hideLikeCount);
        body.put("p_comments_disabled", postOptions.commentsDisabled);
        body.put("p_location", postLocation == null || postLocation.trim().isEmpty()
                ? null : postLocation.trim());
        body.put("p_music_url", uploadedMusicUrl);
        body.put("p_music_title", musicTitle);

        novaApi.createInstagramPost(body).enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                if (!response.isSuccessful()) {
                    String err = parseError(response);
                    Log.e(TAG_API, "HTTP " + response.code() + " on createInstagramPost: " + err);
                    fail("Failed to create post (HTTP " + response.code() + "): " + err);
                    return;
                }

                Log.d(TAG_API, "Created post through create_instagram_post");
                com.insta.nova.data.NovaPostQueue.complete(CreatePostActivity.this, pendingQueueId);
                pendingQueueId = null;
                // The RPC owns the media rows, so there is no half-created post to roll back.
                // It does return the new id now, which is what the tag write needs: without
                // it every choice made on the Tag people screen was silently dropped.
                createdPostId = null;
                String newPostId = null;
                if (response.body() != null && response.body().isJsonObject()) {
                    JsonElement idEl = response.body().getAsJsonObject().get("id");
                    if (idEl != null && !idEl.isJsonNull()) newPostId = idEl.getAsString();
                }
                if (newPostId != null) {
                    applyTags(newPostId);
                } else if (!taggedIds.isEmpty()) {
                    Log.e(TAG_API, "create_instagram_post returned no id; tags were not written");
                }
                if (shareToNova) {
                    createNovaCopy(caption, expiresAtMillis, durationSeconds);
                    return;
                }
                toast("Post shared!");
                finishPostCreated();
            }

            @Override
            public void onFailure(Call<JsonElement> call, Throwable t) {
                Log.e(TAG_API, "Network failure creating post: " + t.getMessage(), t);
                failNetwork(t);
            }
        });
    }

    /**
     * The optional Nova copy. It is a second, independent post: when it fails the
     * Instagram post above is kept as it is and only the error is reported.
     */
    private void createNovaCopy(String caption, long expiresAtMillis, Integer durationSeconds) {
        String mediaUrl = uploadedPublicUrls.isEmpty() ? null : uploadedPublicUrls.get(0);
        String kind = mediaUrl == null ? "text" : "photo";
        new NovaPostRepository(this)
                .createPost(caption, mediaUrl, kind, kind, null, postOptions.anonymous,
                        expiresAtMillis, durationSeconds, null)
                .observe(this, resource -> {
                    if (resource == null || resource.status == Resource.Status.LOADING) return;
                    if (resource.status == Resource.Status.SUCCESS) {
                        toast("Shared to Instagram and Nova!");
                        finishPostCreated();
                        return;
                    }
                    String reason = resource.message == null ? "" : resource.message;
                    Log.e(TAG_API, "Nova copy failed: " + reason);
                    toast(getString(R.string.share_to_nova_failed, reason));
                    finishPostCreated();
                });
    }

    /** The shared tail of both write paths. */
    private void finishPostCreated() {
        setLoading(false);
        setResult(RESULT_OK);
        finish();
    }

    private void insertPostMedia(String postId) {
        List<Map<String, Object>> mediaList = new ArrayList<>();
        for (int i = 0; i < uploadedPublicUrls.size(); i++) {
            Map<String, Object> m = new HashMap<>();
            m.put("post_id", postId);
            m.put("url", uploadedPublicUrls.get(i));
            m.put("position", i);
            mediaList.add(m);
        }

        restApi.insertPostMedia(mediaList).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (!response.isSuccessful()) {
                    String err = parseError(response);
                    Log.e(TAG_API, "HTTP " + response.code() + " on insertPostMedia: " + err);
                    // The post row exists but has no media; leaving it would put an empty
                    // post in the feed forever, so remove it before reporting the failure.
                    deleteOrphanPost();
                    fail("Failed to attach post media (HTTP " + response.code() + "): " + err);
                    return;
                }

                Log.d(TAG_API, "HTTP 200: Post media attached successfully.");
                createdPostId = null;
                setLoading(false);
                toast("Post shared!");
                setResult(RESULT_OK);
                finish();
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e(TAG_API, "Network failure attaching media: " + t.getMessage(), t);
                failNetwork(t);
            }
        });
    }

    private String getPublicUrl(String bucket, String path) {
        String base = BuildConfig.SUPABASE_URL.trim();
        if (!base.endsWith("/")) base += "/";
        return base + "storage/v1/object/public/" + bucket + "/" + path;
    }

    /** Removes the half-created posts row (media attach failed). Best effort. */
    private void deleteOrphanPost() {
        String postId = createdPostId;
        createdPostId = null;
        if (postId == null || postId.isEmpty()) return;
        restApi.deletePost("eq." + postId).enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> c, Response<Void> r) {
                Log.d(TAG_API, "Rolled back orphan post " + postId + " HTTP " + r.code());
            }
            @Override public void onFailure(Call<Void> c, Throwable t) {
                Log.e(TAG_API, "Orphan post rollback failed for " + postId + ": " + t.getMessage());
            }
        });
    }

    /**
     * Network failure while publishing: keep the post instead of losing it.
     *
     * <p>Only the pre-flight {@code isOnline()} check used to enqueue. If the connection
     * dropped between that check and the upload — or was reported stale by
     * ConnectivityManager, which is exactly what happens the moment airplane mode is
     * enabled — the post fell into {@code fail()} and was dropped. Any transport failure
     * now goes to the same offline queue the pre-flight path uses.
     */
    private void failNetwork(@Nullable Throwable t) {
        if (com.insta.nova.util.NetworkErrors.isTransport(t)) {
            queueAndFinish();
            return;
        }
        fail(com.insta.nova.util.NetworkErrors.friendly(t, "Upload failed. Please try again."));
    }

    /** Saves the current selection to the offline queue and leaves the composer. */
    private void queueAndFinish() {
        java.util.List<String> paths = new java.util.ArrayList<>();
        for (android.net.Uri uri : selectedUris) paths.add(uri.toString());
        String caption = etCaption != null ? etCaption.getText().toString().trim() : "";
        // The write-ahead row is replaced by a plain queued row so the retry is not counted twice.
        com.insta.nova.data.NovaPostQueue.complete(this, pendingQueueId);
        pendingQueueId = null;
        com.insta.nova.data.NovaPostQueue.enqueue(this, caption, "", paths);
        setLoading(false);
        toast("Saved to your offline queue \u2014 it will upload when you are back online.");
        finish();
    }

    private void fail(String message) {
        setLoading(false);
        // Clean up leftovers
        for (String p : uploadedPaths) {
            storageApi.deleteFile("posts", p).enqueue(new Callback<Void>() {
                @Override public void onResponse(Call<Void> c, Response<Void> r) {}
                @Override public void onFailure(Call<Void> c, Throwable t) {}
            });
        }
        uploadedPaths.clear();
        uploadedPublicUrls.clear();
        toast(message);
    }

    private void setLoading(boolean loading) {
        if (progress != null) {
            progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (tvUploadProgress != null) {
            if (!loading) {
                tvUploadProgress.setText("");
                tvUploadProgress.setVisibility(View.GONE);
            } else {
                tvUploadProgress.setVisibility(View.VISIBLE);
                tvUploadProgress.setText(R.string.upload_preparing);
            }
        }
        // 40% alpha while uploading
        boolean enabled = !loading && !selectedUris.isEmpty();
        btnPublish.setEnabled(enabled);
        btnPublish.setAlpha(enabled ? 1f : 0.4f);
        View pickImage = findViewById(R.id.btn_pick_image);
        if (pickImage != null) pickImage.setEnabled(!loading);
    }

    private String parseError(Response<?> response) {
        try {
            if (response.errorBody() != null) return response.errorBody().string();
        } catch (Exception ignored) {}
        return "Unknown error";
    }

    /** "Uploading 2 of 4 · 63%" while the bytes are on the wire. */
    private void setUploadProgress(int percent, int zeroBasedIndex, int total) {
        if (tvUploadProgress == null) return;
        int clamped = Math.max(0, Math.min(100, percent));
        tvUploadProgress.setText(getString(R.string.upload_progress, zeroBasedIndex + 1,
                Math.max(1, total), clamped));
        tvUploadProgress.setVisibility(View.VISIBLE);
    }

    /**
     * A request body that reports how much of the file has been written. Without it
     * the upload is one opaque call and a slow connection is indistinguishable from
     * a hang.
     */
    private static final class ProgressBody extends RequestBody {
        private final byte[] data;
        private final MediaType type;
        private final java.util.function.IntConsumer listener;

        ProgressBody(byte[] data, MediaType type, java.util.function.IntConsumer listener) {
            this.data = data;
            this.type = type;
            this.listener = listener;
        }

        @Override public MediaType contentType() { return type; }
        @Override public long contentLength() { return data.length; }

        @Override
        public void writeTo(okio.BufferedSink sink) throws java.io.IOException {
            final int chunk = 16 * 1024;
            int sent = 0;
            int lastPercent = -1;
            while (sent < data.length) {
                int len = Math.min(chunk, data.length - sent);
                sink.write(data, sent, len);
                sent += len;
                int percent = (int) ((long) sent * 100L / Math.max(1L, data.length));
                if (percent != lastPercent) {
                    lastPercent = percent;
                    listener.accept(percent);
                }
            }
            sink.flush();
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}
