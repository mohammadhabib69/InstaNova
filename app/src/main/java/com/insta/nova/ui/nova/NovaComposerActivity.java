package com.insta.nova.ui.nova;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.insta.nova.R;
import com.insta.nova.data.NovaDtos;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.repository.NovaPostRepository;
import com.insta.nova.data.repository.StorageRepository;
import com.insta.nova.databinding.ActivityNovaComposerBinding;
import com.insta.nova.ui.post.PostComposerOptionsSheet;
import com.insta.nova.util.ImageUtils;
import com.insta.nova.util.Resource;
import com.insta.nova.util.ToastUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * The Nova composer.
 *
 * <p>A Nova post is written to {@code nova_posts} and nowhere else. The
 * destination row makes that explicit: "Nova only" is the default, and an
 * Instagram item is only created when the author turns "Also share to
 * Instagram" on and picks a type. That shared item is created by the server
 * and carries the id of the Nova post it came from.
 */
public class NovaComposerActivity extends AppCompatActivity {

    /** The share types the composer can create on Instagram. */
    private static final String SHARE_POST = "post";
    private static final String SHARE_STORY = "story";
    private static final String SHARE_REEL = "reel";

    private static final int MAX_POLL_OPTIONS = 4;

    private ActivityNovaComposerBinding binding;
    private NovaPostRepository repository;
    private StorageRepository storage;

    private ActivityResultLauncher<PickVisualMediaRequest> pickMediaLauncher;

    private final PostComposerOptionsSheet.Options options =
            new PostComposerOptionsSheet.Options();

    private final List<EditText> pollOptions = new ArrayList<>();
    private Uri selectedPhoto;
    private boolean pollMode;
    private boolean shareToInstagram;
    private String shareType = SHARE_POST;
    private boolean publishing;

    /** The entry point used by the Nova sheet and the empty state. */
    public static void start(Context context) {
        if (context == null) return;
        Intent intent = new Intent(context, NovaComposerActivity.class);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNovaComposerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = new NovaPostRepository(this);
        storage = new StorageRepository(this);

        pickMediaLauncher = registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(), uri -> {
                    if (uri == null) return;
                    selectedPhoto = uri;
                    showPhoto();
                });

        binding.btnNovaComposerClose.setOnClickListener(v -> finish());
        binding.btnNovaComposerPhoto.setOnClickListener(v ->
                pickMediaLauncher.launch(new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()));
        binding.btnNovaComposerRemovePhoto.setOnClickListener(v -> {
            selectedPhoto = null;
            binding.imgNovaComposerPhoto.setVisibility(View.GONE);
            binding.btnNovaComposerRemovePhoto.setVisibility(View.GONE);
        });
        binding.btnNovaComposerPoll.setOnClickListener(v -> togglePoll());
        binding.btnNovaComposerOptions.setOnClickListener(v -> openOptions());
        binding.btnNovaComposerPost.setOnClickListener(v -> publish());
        // The switch toggles itself on tap, so it must only *record* the state.
        // Inverting it here as well cancelled the tap out: the row worked but
        // tapping the switch itself left it off.
        binding.switchNovaShareInstagram.setOnCheckedChangeListener((v, checked) -> {
            shareToInstagram = checked;
            paintDestination();
        });
        binding.rowNovaShareInstagram.setOnClickListener(v ->
                binding.switchNovaShareInstagram.toggle());

        binding.btnNovaSharePost.setText(R.string.nova_composer_share_post);
        binding.btnNovaShareStory.setText(R.string.nova_composer_share_story);
        binding.btnNovaShareReel.setText(R.string.nova_composer_share_reel);
        binding.btnNovaSharePost.setOnClickListener(v -> chooseShareType(SHARE_POST));
        binding.btnNovaShareStory.setOnClickListener(v -> chooseShareType(SHARE_STORY));
        binding.btnNovaShareReel.setOnClickListener(v -> chooseShareType(SHARE_REEL));

        // The default destination is Nova only, and it is the only one that is
        // selected when the composer opens.
        chooseShareType(SHARE_POST);
        paintDestination();
        paintOptionsSummary();
    }

    /* ------------------------------------------------------------------ */
    /* Post options                                                        */
    /* ------------------------------------------------------------------ */

    private void openOptions() {
        PostComposerOptionsSheet.show(this, PostComposerOptionsSheet.SURFACE_NOVA,
                options, chosen -> {
                    // Every field, including the custom window: dropping customSeconds left
                    // EXPIRY_CUSTOM with a zero length, so the summary row stayed blank.
                    options.anonymous = chosen.anonymous;
                    options.expiry = chosen.expiry;
                    options.customSeconds = chosen.customSeconds;
                    options.hideLikeCount = chosen.hideLikeCount;
                    options.commentsDisabled = chosen.commentsDisabled;
                    paintOptionsSummary();
                });
    }

    private void paintOptionsSummary() {
        List<String> parts = new ArrayList<>();
        if (options.anonymous) parts.add(getString(R.string.nova_badge_anonymous));
        if (options.expires()) {
            String label = PostComposerOptionsSheet.expiryLabel(this,
                    options.expiresAtMillis(System.currentTimeMillis()),
                    System.currentTimeMillis());
            if (label != null) parts.add(label);
        }
        if (parts.isEmpty()) {
            binding.tvNovaComposerOptionsSummary.setVisibility(View.GONE);
            return;
        }
        binding.tvNovaComposerOptionsSummary.setVisibility(View.VISIBLE);
        binding.tvNovaComposerOptionsSummary.setText(TextUtils.join(" · ", parts));
    }

    /* ------------------------------------------------------------------ */
    /* Destination                                                         */
    /* ------------------------------------------------------------------ */

    private void chooseShareType(String type) {
        shareType = type;
        paintChoice(binding.btnNovaSharePost, SHARE_POST.equals(type));
        paintChoice(binding.btnNovaShareStory, SHARE_STORY.equals(type));
        paintChoice(binding.btnNovaShareReel, SHARE_REEL.equals(type));
    }

    /**
     * bg_nova_chip_on is filled with nova_text, so the selected choice has to
     * take the page background colour for its label — leaving the style's text
     * colour in place painted white on white.
     */
    private void paintChoice(android.widget.TextView choice, boolean selected) {
        choice.setBackgroundResource(
                selected ? R.drawable.bg_nova_chip_on : R.drawable.bg_nova_chip);
        choice.setTextColor(getColor(
                selected ? R.color.color_background : R.color.nova_text));
    }

    private void paintDestination() {
        boolean shared = binding.switchNovaShareInstagram.isChecked();
        binding.novaShareTypeBox.setVisibility(shared ? View.VISIBLE : View.GONE);
        // "Nova only" is checked while the Instagram switch is off.
        binding.imgNovaDestinationNova.setVisibility(shared ? View.INVISIBLE : View.VISIBLE);
    }

    /* ------------------------------------------------------------------ */
    /* Photo and poll                                                      */
    /* ------------------------------------------------------------------ */

    private void showPhoto() {
        binding.imgNovaComposerPhoto.setVisibility(View.VISIBLE);
        binding.btnNovaComposerRemovePhoto.setVisibility(View.VISIBLE);
        try {
            com.bumptech.glide.Glide.with(this)
                    .load(selectedPhoto)
                    .centerCrop()
                    .into(binding.imgNovaComposerPhoto);
        } catch (RuntimeException ignored) {
            // The preview is a nicety; the upload reads the original bytes.
        }
    }

    private void togglePoll() {
        pollMode = !pollMode;
        binding.novaPollBox.setVisibility(pollMode ? View.VISIBLE : View.GONE);
        if (pollMode) {
            while (pollOptions.size() < 2) addPollOption();
        }
    }

    private void addPollOption() {
        if (pollOptions.size() >= MAX_POLL_OPTIONS) return;
        EditText input = new EditText(this);
        input.setHint(getString(R.string.nova_composer_poll_option, pollOptions.size() + 1));
        input.setTextColor(resolveTextPrimary());
        input.setHintTextColor(resolveTextSecondary());
        input.setSingleLine(false);
        input.setMaxLines(3);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(6);
        binding.novaPollOptionsBox.addView(input, params);
        pollOptions.add(input);
    }

    /* ------------------------------------------------------------------ */
    /* Publishing                                                          */
    /* ------------------------------------------------------------------ */

    private void publish() {
        if (publishing) return;

        String body = text(binding.etNovaComposerBody);
        List<String> choices = pollChoices();

        if (body.isEmpty() && selectedPhoto == null && choices.isEmpty()) {
            showError(getString(R.string.nova_composer_empty));
            return;
        }
        if (pollMode && choices.size() < 2) {
            showError(getString(R.string.nova_composer_poll_needs_options));
            return;
        }
        boolean shared = binding.switchNovaShareInstagram.isChecked();
        if (shared && (SHARE_STORY.equals(shareType) || SHARE_REEL.equals(shareType))
                && selectedPhoto == null) {
            showError(getString(R.string.nova_composer_need_photo));
            return;
        }

        setPublishing(true);
        showError(null);

        // The photo goes to the Nova bucket first; the post row is only
        // written once the media is in place.
        if (selectedPhoto == null) {
            createPost(body, null, choices);
            return;
        }
        byte[] bytes;
        try {
            bytes = ImageUtils.processUriToJpegBytes(this, selectedPhoto);
        } catch (Exception e) {
            setPublishing(false);
            showError(getString(R.string.nova_composer_failed));
            return;
        }
        if (bytes == null || bytes.length == 0) {
            setPublishing(false);
            showError(getString(R.string.nova_composer_failed));
            return;
        }
        storage.uploadNovaMedia(bytes).observe(this, resource -> {
            if (resource == null) return;
            if (resource.status == Resource.Status.SUCCESS && resource.data != null) {
                createPost(body, resource.data, choices);
            } else if (resource.status == Resource.Status.ERROR) {
                setPublishing(false);
                showError(getString(R.string.nova_composer_failed));
            }
        });
    }

    private void createPost(String body, String mediaUrl, List<String> choices) {
        String mediaType;
        String contentType;
        if (!choices.isEmpty()) {
            mediaType = "poll";
            contentType = "poll";
        } else if (mediaUrl != null) {
            mediaType = "photo";
            contentType = "photo";
        } else {
            mediaType = "text";
            contentType = "text";
        }

        long now = System.currentTimeMillis();
        long expiresAt = options.expiresAtMillis(now);
        Integer duration = options.durationSecondsOrNull();

        repository.createPost(body, mediaUrl, mediaType, contentType, null,
                        options.anonymous, expiresAt, duration, choices)
                .observe(this, resource -> {
                    if (resource == null) return;
                    if (resource.status == Resource.Status.SUCCESS && resource.data != null) {
                        onCreated(resource.data, expiresAt);
                    } else if (resource.status == Resource.Status.ERROR) {
                        setPublishing(false);
                        showError(messageFor(resource.message));
                    }
                });
    }

    /**
     * The Nova post exists. Now the explicit Instagram share, and only then.
     */
    private void onCreated(NovaDtos.CreatedRef created, long expiresAt) {
        if (!binding.switchNovaShareInstagram.isChecked()) {
            finishWithSuccess(false);
            return;
        }
        repository.shareToInstagram(created.id, shareType).observe(this, resource -> {
            if (resource == null) return;
            if (resource.status == Resource.Status.SUCCESS) {
                finishWithSuccess(true);
            } else if (resource.status == Resource.Status.ERROR) {
                // The Nova post is already published, so it stays; only the
                // Instagram copy failed and the author is told about it.
                setPublishing(false);
                showError(getString(R.string.nova_share_failed));
            }
        });
    }

    private void finishWithSuccess(boolean shared) {
        ToastUtil.showPill(this, getString(
                shared ? R.string.nova_composer_shared : R.string.nova_composer_published));
        finish();
    }

    /* ------------------------------------------------------------------ */
    /* Helpers                                                             */
    /* ------------------------------------------------------------------ */

    private List<String> pollChoices() {
        List<String> choices = new ArrayList<>();
        for (EditText input : pollOptions) {
            String value = text(input);
            if (!value.isEmpty()) choices.add(value);
        }
        return choices;
    }

    private void setPublishing(boolean value) {
        publishing = value;
        binding.novaComposerProgress.setVisibility(value ? View.VISIBLE : View.GONE);
        binding.btnNovaComposerPost.setEnabled(!value);
        binding.btnNovaComposerPost.setAlpha(value ? 0.5f : 1f);
    }

    private void showError(String message) {
        if (message == null || message.isEmpty()) {
            binding.tvNovaComposerError.setVisibility(View.GONE);
            return;
        }
        binding.tvNovaComposerError.setVisibility(View.VISIBLE);
        binding.tvNovaComposerError.setText(message);
    }

    /** Maps the server's error codes to something the author can act on. */
    private String messageFor(String code) {
        if (code == null) return getString(R.string.nova_composer_failed);
        if (code.contains("anonymous_rate_limit")) {
            return getString(R.string.nova_composer_anonymous_rate);
        }
        if (code.contains("empty_post") || code.contains("empty_comment")) {
            return getString(R.string.nova_composer_empty);
        }
        if (code.contains("network")) {
            return getString(R.string.nova_feed_offline);
        }
        return getString(R.string.nova_composer_failed);
    }

    private static String text(EditText input) {
        return input == null || input.getText() == null ? "" : input.getText().toString().trim();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int resolveTextPrimary() {
        android.util.TypedValue value = new android.util.TypedValue();
        if (getTheme().resolveAttribute(R.attr.themeTextPrimary, value, true)) {
            if (value.resourceId != 0) {
                return androidx.core.content.ContextCompat.getColor(this, value.resourceId);
            }
            return value.data;
        }
        return android.graphics.Color.WHITE;
    }

    private int resolveTextSecondary() {
        android.util.TypedValue value = new android.util.TypedValue();
        if (getTheme().resolveAttribute(R.attr.themeTextSecondary, value, true)) {
            if (value.resourceId != 0) {
                return androidx.core.content.ContextCompat.getColor(this, value.resourceId);
            }
            return value.data;
        }
        return android.graphics.Color.GRAY;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
