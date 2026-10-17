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
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
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
import com.insta.nova.BuildConfig;
import com.insta.nova.R;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.remote.RetrofitClient;
import com.insta.nova.data.remote.api.RestApi;
import com.insta.nova.data.remote.api.StorageApi;
import com.insta.nova.data.remote.dto.PostDto;
import com.insta.nova.util.ColorMatrixPresets;
import com.insta.nova.util.Constants;
import com.insta.nova.util.ImageUtils;
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
    public static void start(Context context) {
        if (context == null) return;
        Intent intent = new Intent(context, CreatePostActivity.class);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    private static final String TAG_API = "InstaApi";
    private static final String TAG_GLIDE = "InstaGlide";

    private static final int STEP_PICKER = 0;
    private static final int STEP_EDITOR = 1;
    private static final int STEP_DETAILS = 2;
    private static final int STEP_CAPTION = 3;

    private static final int GALLERY_LIMIT = 60;
    private static final Pattern HASHTAG_PATTERN = Pattern.compile("#(\\w+)");

    private final List<Uri> selectedUris = new ArrayList<>();
    private final List<Uri> galleryUris = new ArrayList<>();
    /** Set once the posts row exists, so a later failure can remove it again. */
    private String createdPostId;
    private final List<String> uploadedPaths = new ArrayList<>();
    private final List<String> uploadedPublicUrls = new ArrayList<>();
    private final List<ColorMatrixPresets.FilterPreset> filterPresets = ColorMatrixPresets.getPresets();

    private StorageApi storageApi;
    private RestApi restApi;
    private okhttp3.OkHttpClient okHttpClient;
    private String currentUserId;

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

    // Details / caption
    private RecyclerView rvDetailsPhotos;
    private DetailsPhotoAdapter detailsPhotoAdapter;
    private android.widget.EditText etCaption;
    private android.widget.EditText etCaptionScreen;
    private View btnPublish;
    private View tvLocationValue;

    private View progress;

    private ActivityResultLauncher<PickVisualMediaRequest> pickMediaLauncher;
    private ActivityResultLauncher<PickVisualMediaRequest> pickMultipleMediaLauncher;
    private ActivityResultLauncher<String> mediaPermissionLauncher;
    /** Guards against re-prompting in a loop when the user declines. */
    private boolean mediaPermissionRequested;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_post);

        currentUserId = SessionManager.getInstance(this).getUserId();
        RetrofitClient client = RetrofitClient.getInstance(this);
        storageApi = client.getStorageApi();
        restApi = client.getRestApi();
        okHttpClient = client.getOkHttpClient();

        bindViews();
        setupPickers();
        setupPickerStep();
        setupEditorStep();
        setupDetailsStep();
        setupCaptionStep();

        showStep(STEP_PICKER);
        loadDeviceGallery();
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
    }

    private void bindClick(int id, View.OnClickListener listener) {
        View v = findViewById(id);
        if (v != null) v.setOnClickListener(listener);
    }

    /** Swaps the four steps of the create flow. */
    private void showStep(int step) {
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
        bindClick(R.id.btn_picker_album, v -> pickMediaLauncher.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
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

        // Bottom POST / STORY / REEL switch
        // PAGE 11: the STORY tab opens the story editor (with the picked image when there is one)
        bindClick(R.id.tv_mode_story, v -> {
            pickerMode = PICKER_MODE_STORY;
            openStoryEditor(selectedUris.isEmpty() ? null : selectedUris.get(0));
        });
        bindClick(R.id.tv_mode_reel, v -> {
            pickerMode = PICKER_MODE_REEL;
            startActivity(new Intent(this, CreateReelActivity.class));
        });

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
        CreateStoryActivity.start(this, uri == null ? null : uri.toString());
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
        bindClick(R.id.btn_editor_aa, v -> ToastUtil.showNotAvailable(this));

        View.OnClickListener addMore = v -> pickMultipleMediaLauncher.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build());
        bindClick(R.id.btn_editor_gallery, addMore);
        bindClick(R.id.btn_editor_gallery_thumb, addMore);

        // Filter | Edit | Crop
        bindClick(R.id.btn_editor_tab_filter, v -> selectEditorTab(0));
        bindClick(R.id.btn_editor_tab_edit, v -> ToastUtil.showNotAvailable(this));
        bindClick(R.id.btn_editor_tab_crop, v -> ToastUtil.showNotAvailable(this));

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
    }

    private void refreshEditor() {
        editorCardAdapter.notifyDataSetChanged();
        filterAdapter.notifyDataSetChanged();
        if (!selectedUris.isEmpty()) {
            Glide.with(this).load(selectedUris.get(0)).centerCrop().into(ivEditorGalleryThumb);
        }
        selectEditorTab(0);
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
                R.id.row_tag_people, R.id.row_add_music, R.id.row_ai_label,
                R.id.row_audience, R.id.row_also_share, R.id.row_more_options,
                R.id.btn_chip_poll, R.id.btn_chip_prompt, R.id.switch_ai_label,
                R.id.row_add_location
        };
        for (int id : decorativeRows) {
            final View row = findViewById(id);
            if (row == null) continue;
            if (Constants.DECORATIVE_UI) {
                row.setVisibility(View.VISIBLE);
                row.setOnClickListener(v -> ToastUtil.showNotAvailable(this));
            } else {
                row.setVisibility(View.GONE);
            }
        }
        if (tvLocationValue != null) tvLocationValue.setVisibility(View.GONE);
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
        bindClick(R.id.btn_caption_edit, v -> ToastUtil.showNotAvailable(this));

        int[] decorative = {R.id.btn_caption_chip_poll, R.id.btn_caption_chip_prompt};
        for (int id : decorative) {
            final View chip = findViewById(id);
            if (chip == null) continue;
            if (Constants.DECORATIVE_UI) {
                chip.setVisibility(View.VISIBLE);
                chip.setOnClickListener(v -> ToastUtil.showNotAvailable(this));
            } else {
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
        Matcher matcher = HASHTAG_PATTERN.matcher(editable.toString());
        while (matcher.find()) {
            editable.setSpan(new ForegroundColorSpan(color), matcher.start(), matcher.end(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
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
            Glide.with(CreatePostActivity.this).load(uri).centerCrop().into(holder.card);
            if (selectedFilter != 0 && selectedFilter < filterPresets.size()) {
                holder.card.setColorFilter(filterPresets.get(selectedFilter).getColorFilter(1f));
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
                    // The intensity slider does not exist in this codebase
                    ToastUtil.showNotAvailable(CreatePostActivity.this);
                    return;
                }
                selectedFilter = position;
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
            if (selectedFilter != 0 && selectedFilter < filterPresets.size()) {
                holder.photo.setColorFilter(filterPresets.get(selectedFilter).getColorFilter(1f));
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

    private void publishPost() {
        if (selectedUris.isEmpty() || currentUserId == null) {
            toast("Please choose a photo after signing in.");
            return;
        }

        setLoading(true);
        uploadedPaths.clear();
        uploadedPublicUrls.clear();

        uploadMediaSequentially(0);
    }

    private void uploadMediaSequentially(int index) {
        if (index >= selectedUris.size()) {
            // All images uploaded; now create post
            createPostRecord();
            return;
        }

        Uri uri = selectedUris.get(index);
        byte[] jpegBytes;
        try {
            jpegBytes = ImageUtils.processUriToJpegBytes(this, uri);
        } catch (IOException e) {
            Log.e(TAG_API, "Failed to process image: " + e.getMessage(), e);
            fail("Failed to process image: " + e.getMessage());
            return;
        }

        if (jpegBytes == null || jpegBytes.length == 0) {
            fail("Image processing produced empty byte array");
            return;
        }

        String path = currentUserId + "/" + UUID.randomUUID() + ".jpg";
        RequestBody body = RequestBody.create(MediaType.parse("image/jpeg"), jpegBytes);
        Log.d(TAG_API, "uploaded " + jpegBytes.length + " bytes to posts/" + path);

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
                                + " Content-Length=" + contentLength + " expected=" + jpegBytes.length + " for " + publicUrl);

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
                fail("Network failure uploading image: " + t.getMessage());
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

    private void createPostRecord() {
        if (uploadedPublicUrls.isEmpty()) {
            fail("No uploaded images found.");
            return;
        }

        String caption = etCaption != null ? etCaption.getText().toString().trim() : "";
        Map<String, Object> body = new HashMap<>();
        body.put("user_id", currentUserId);
        body.put("caption", caption);
        body.put("thumbnail_url", uploadedPublicUrls.get(0));
        body.put("is_reel", false);

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
                createdPostId = postId;
                insertPostMedia(postId);
            }

            @Override
            public void onFailure(Call<List<PostDto>> call, Throwable t) {
                Log.e(TAG_API, "Network failure creating post: " + t.getMessage(), t);
                fail("Network error creating post: " + t.getMessage());
            }
        });
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
                fail("Network error attaching media: " + t.getMessage());
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

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}
