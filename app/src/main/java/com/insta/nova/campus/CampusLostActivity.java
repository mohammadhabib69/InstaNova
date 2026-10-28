package com.insta.nova.campus;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.insta.nova.R;
import com.insta.nova.data.CampusRepository;
import com.insta.nova.data.NovaDtos;
import com.insta.nova.databinding.ActivityCampusLostBinding;
import com.insta.nova.hub.NovaScreen;
import com.insta.nova.util.Resource;
import com.insta.nova.util.NovaImageUpload;
import com.insta.nova.util.TimeUtils;
import com.insta.nova.widget.NovaUi;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Campus lost and found: two tabs, a report form, and a resolve toggle on your own posts. */
public class CampusLostActivity extends AppCompatActivity {
    /** Optional photo chosen for the next report; uploaded only when the report is submitted. */
    private android.net.Uri pendingPhoto;
    private android.net.Uri cameraUri;
    private android.widget.TextView photoButton;
    private androidx.activity.result.ActivityResultLauncher<android.content.Intent> photoLauncher;


    private ActivityCampusLostBinding binding;
    private CampusRepository repository;
    private String kind = "lost";
    private int universityId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        photoLauncher = registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                result -> {
                    // A capture target is prepared before the chooser opens, so backing out
                    // of the camera used to leave that empty file "attached" even though
                    // nothing had been taken.
                    android.net.Uri captured = cameraUri;
                    cameraUri = null;
                    if (result.getResultCode() != RESULT_OK) return;
                    android.content.Intent data = result.getData();
                    android.net.Uri picked = data != null && data.getData() != null
                            ? data.getData() : captured;
                    if (picked == null) return;
                    pendingPhoto = picked;
                    if (photoButton != null) {
                        photoButton.setText(R.string.nova_campus_lf_photo_attached);
                    }
                });
        binding = ActivityCampusLostBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        NovaScreen.bindHeader(this, R.drawable.ic_nova_campus,
                R.string.nova_campus_lost_title, R.color.nova_campus);

        repository = new CampusRepository(this);
        binding.btnLost.setOnClickListener(v -> select("lost"));
        binding.btnFound.setOnClickListener(v -> select("found"));
        binding.btnNew.setOnClickListener(v -> report());

        renderTabs();
        loadMembershipThenItems();
    }

    private void loadMembershipThenItems() {
        repository.membership().observe(this, resource -> {
            if (resource != null && resource.status == Resource.Status.SUCCESS
                    && resource.data != null && !resource.data.isEmpty()) {
                universityId = resource.data.get(0).universityId;
            }
            load();
        });
    }

    private void select(String next) {
        kind = next;
        renderTabs();
        load();
    }

    private void renderTabs() {
        boolean lost = "lost".equals(kind);
        binding.btnLost.setBackgroundResource(lost
                ? R.drawable.bg_nova_chip_on : R.drawable.bg_nova_chip);
        binding.btnLost.setTextColor(getColor(lost ? R.color.color_background : R.color.nova_text_3));
        binding.btnFound.setBackgroundResource(lost
                ? R.drawable.bg_nova_chip : R.drawable.bg_nova_chip_on);
        binding.btnFound.setTextColor(getColor(lost ? R.color.nova_text_3 : R.color.color_background));
    }

    private void load() {
        repository.lostFound(kind, null).observe(this, resource -> {
            if (binding == null || resource == null) return;
            if (resource.status != Resource.Status.SUCCESS || resource.data == null) return;
            render(resource.data);
        });
    }

    private void render(List<NovaDtos.LostFoundItem> items) {
        binding.itemList.removeAllViews();
        binding.tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        for (NovaDtos.LostFoundItem item : items) {
            binding.itemList.addView(row(item));
        }
    }

    private View row(NovaDtos.LostFoundItem item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_nova_card);
        card.setPadding(NovaUi.dp(this, 16), NovaUi.dp(this, 14),
                NovaUi.dp(this, 16), NovaUi.dp(this, 14));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = NovaUi.dp(this, 10);
        card.setLayoutParams(params);

        if (item.photoUrl != null && !item.photoUrl.trim().isEmpty()) {
            android.widget.ImageView photo = new android.widget.ImageView(this);
            LinearLayout.LayoutParams photoParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, NovaUi.dp(this, 180));
            photoParams.bottomMargin = NovaUi.dp(this, 10);
            photo.setLayoutParams(photoParams);
            photo.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            com.insta.nova.util.GlideUtils.load(photo, "nova", item.photoUrl,
                    R.drawable.ic_image, R.drawable.ic_image);
            photo.setOnClickListener(v -> {
                android.content.Intent intent = new android.content.Intent(this,
                        com.insta.nova.ui.chat.ImageViewerActivity.class);
                intent.putExtra(com.insta.nova.ui.chat.ImageViewerActivity.EXTRA_IMAGE_URL,
                        item.photoUrl);
                intent.putExtra(com.insta.nova.ui.chat.ImageViewerActivity.EXTRA_BUCKET, "nova");
                startActivity(intent);
            });
            card.addView(photo);
        }

        TextView title = new TextView(this);
        title.setText(item.title == null ? "" : item.title);
        title.setTextColor(getColor(R.color.nova_text));
        title.setTextSize(15f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(title);

        StringBuilder meta = new StringBuilder();
        if (item.place != null && !item.place.isEmpty()) meta.append(item.place);
        long when = TimeUtils.parseTimestamp(item.eventDate);
        if (when > 0) {
            if (meta.length() > 0) meta.append(" · ");
            meta.append(android.text.format.DateFormat.getDateFormat(this)
                    .format(new java.util.Date(when)));
        }
        if (item.details != null && !item.details.isEmpty()) {
            if (meta.length() > 0) meta.append("\n");
            meta.append(item.details);
        }
        TextView sub = new TextView(this);
        sub.setText(meta);
        sub.setTextColor(getColor(R.color.nova_text_3));
        sub.setTextSize(12f);
        card.addView(sub);

        if (item.contact != null && !item.contact.trim().isEmpty()) {
            TextView call = new TextView(this);
            call.setText(getString(R.string.nova_campus_lf_call, item.contact));
            call.setTextColor(getColor(R.color.nova_cyan));
            call.setTextSize(13f);
            call.setPadding(0, NovaUi.dp(this, 8), 0, 0);
            call.setOnClickListener(v -> dial(item.contact));
            card.addView(call);
        }

        TextView foot = new TextView(this);
        String status = item.status == null ? "open" : item.status;
        foot.setText((item.authorUsername == null ? "" : "@" + item.authorUsername)
                + " · " + status);
        foot.setTextColor(getColor(R.color.nova_campus));
        foot.setTextSize(11f);
        foot.setPadding(0, NovaUi.dp(this, 8), 0, 0);
        card.addView(foot);

        if (item.isMine) {
            TextView toggle = new TextView(this);
            boolean open = !"resolved".equals(status);
            toggle.setText(open ? R.string.nova_campus_lost_resolved
                    : R.string.nova_campus_lost_reopen);
            toggle.setTextColor(getColor(R.color.nova_cyan));
            toggle.setTextSize(12f);
            toggle.setGravity(Gravity.END);
            toggle.setPadding(0, NovaUi.dp(this, 8), 0, 0);
            toggle.setOnClickListener(v -> setStatus(item, open ? "resolved" : "open"));
            card.addView(toggle);
        }
        return card;
    }

    private void setStatus(NovaDtos.LostFoundItem item, String status) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("status", status);
        repository.updateLostFound(item.id, fields).observe(this, resource -> {
            if (resource != null && resource.status == Resource.Status.SUCCESS) load();
        });
    }

    private void report() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = NovaUi.dp(this, 20);
        form.setPadding(pad, pad, pad, 0);

        // A lost-item report reused the event dialog's copy, so it asked for an "Event
        // title" and a "Venue" — words that make no sense for a missing phone.
        EditText title = field(form, getString(R.string.nova_campus_lf_item));
        EditText place = field(form, getString(R.string.nova_campus_lf_place));
        EditText details = field(form, getString(R.string.nova_campus_details));
        // Required: a lost/found notice is useless without a way to reach the person.
        EditText contact = field(form, getString(R.string.nova_campus_lf_contact));
        contact.setInputType(android.text.InputType.TYPE_CLASS_PHONE);

        // Photo is optional by design: a missing-item notice with a number is still useful.
        photoButton = new TextView(this);
        photoButton.setText(R.string.nova_campus_lf_photo);
        photoButton.setTextColor(getColor(R.color.nova_cyan));
        photoButton.setTextSize(13f);
        photoButton.setPadding(0, NovaUi.dp(this, 10), 0, NovaUi.dp(this, 4));
        photoButton.setOnClickListener(v -> pickPhoto());
        form.addView(photoButton);

        new AlertDialog.Builder(this)
                .setTitle(R.string.nova_campus_lost_new)
                .setView(form)
                .setNegativeButton(R.string.cancel, (d, w) -> {
                    // An unsubmitted pick must not leak into the next report.
                    pendingPhoto = null;
                    cameraUri = null;
                })
                .setPositiveButton(R.string.nova_campus_lost_new, (d, w) -> {
                    String titleText = title.getText().toString().trim();
                    if (titleText.isEmpty()) {
                        error(getString(R.string.nova_campus_lf_item));
                        return;
                    }
                    String contactText = contact.getText().toString().trim();
                    if (contactText.replaceAll("[^0-9]", "").length() < 6) {
                        error(getString(R.string.nova_campus_lf_contact_required));
                        return;
                    }
                    String detailsText = details.getText().toString().trim();
                    String placeText = place.getText().toString().trim();
                    String when = TimeUtils.toSupabaseTimestamp(System.currentTimeMillis());
                    if (pendingPhoto == null) {
                        submitReport(kind, titleText, detailsText, placeText, when,
                                null, contactText);
                        return;
                    }
                    NovaImageUpload.upload(this, pendingPhoto, "nova",
                            new NovaImageUpload.UploadCallback() {
                                @Override public void onUploaded(String url) {
                                    submitReport(kind, titleText, detailsText, placeText, when,
                                            url, contactText);
                                }

                                @Override public void onFailed(String message) {
                                    // The notice still goes out; only the photo is lost.
                                    submitReport(kind, titleText, detailsText, placeText, when,
                                            null, contactText);
                                }
                            });
                })
                .show();
    }

    private void submitReport(String kind, String title, String details, String place,
                              String when, String photoUrl, String contact) {
        repository.createLostFound(kind, title, details, place, when,
                        photoUrl, universityId, contact)
                .observe(this, resource -> {
                    if (resource != null && resource.status == Resource.Status.SUCCESS) {
                        pendingPhoto = null;
                        cameraUri = null;
                        load();
                    } else if (resource != null && resource.status == Resource.Status.ERROR) {
                        error(getString(R.string.nova_error) + " (" + resource.message + ")");
                    }
                });
    }

    /** Camera + gallery chooser. The capture target is a cache file exposed by FileProvider. */
    private void pickPhoto() {
        android.content.Intent gallery = new android.content.Intent(
                android.content.Intent.ACTION_GET_CONTENT);
        gallery.setType("image/*");
        gallery.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        android.content.Intent chooser = android.content.Intent.createChooser(
                gallery, getString(R.string.nova_campus_lf_photo));
        try {
            java.io.File dir = new java.io.File(getCacheDir(), "nova_photo");
            if (!dir.exists()) dir.mkdirs();
            java.io.File file = java.io.File.createTempFile("lost_", ".jpg", dir);
            cameraUri = androidx.core.content.FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", file);
            android.content.Intent camera = new android.content.Intent(
                    android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            camera.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, cameraUri);
            camera.addFlags(android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            chooser.putExtra(android.content.Intent.EXTRA_INITIAL_INTENTS,
                    new android.content.Intent[]{camera});
        } catch (Exception ignored) {
            // No camera available: the chooser still offers the gallery.
        }
        photoLauncher.launch(chooser);
    }

    private EditText field(LinearLayout parent, String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextSize(14f);
        input.setTextColor(getColor(R.color.nova_text));
        input.setHintTextColor(getColor(R.color.nova_text_3));
        parent.addView(input);
        return input;
    }

    /** Dial pad pre-filled with the reporter's number; the call still needs a green press. */
    private void dial(String number) {
        String clean = number == null ? "" : number.replaceAll("[^0-9+]", "");
        if (clean.isEmpty()) return;
        startActivity(new android.content.Intent(android.content.Intent.ACTION_DIAL,
                android.net.Uri.parse("tel:" + clean)));
    }

    private void error(String text) {
        if (binding == null) return;
        binding.tvLostError.setText(text);
        binding.tvLostError.setVisibility(View.VISIBLE);
        NovaUi.haptic(binding.getRoot(), false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
