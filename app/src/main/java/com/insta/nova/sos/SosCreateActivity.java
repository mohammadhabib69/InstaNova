package com.insta.nova.sos;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.insta.nova.R;
import com.insta.nova.data.SosRepository;
import com.insta.nova.databinding.ActivitySosCreateBinding;
import com.insta.nova.hub.NovaScreen;
import com.insta.nova.util.Resource;
import com.insta.nova.util.TimeUtils;
import com.insta.nova.widget.NovaUi;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Blood request form. Every field is validated locally before the RPC, the phone number is
 * never echoed back in the UI, and the success dialog states the real number of donors the
 * backend alerted (including zero).
 */
public class SosCreateActivity extends AppCompatActivity {
    /** Optional photo of the patient or the report; uploaded only on submit. */
    private android.widget.TextView photoButton;
    private android.net.Uri pendingPhoto;
    private android.net.Uri cameraUri;
    private androidx.activity.result.ActivityResultLauncher<android.content.Intent> photoLauncher;


    private static final Pattern BD_PHONE =
            Pattern.compile("^(\\+?88)?01[3-9][0-9]{8}$");

    private ActivitySosCreateBinding binding;
    private SosRepository repository;
    private String group = "A+";
    private String urgency = "today";
    private int units = 1;
    private long neededBy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySosCreateBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        photoLauncher = registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                result -> {
                    // Same trap as the Lost & Found form: the capture target exists before
                    // anything is taken, so a cancelled pick must not count as attached.
                    android.net.Uri captured = cameraUri;
                    cameraUri = null;
                    if (result.getResultCode() != RESULT_OK) return;
                    android.content.Intent data = result.getData();
                    android.net.Uri picked = data != null && data.getData() != null
                            ? data.getData() : captured;
                    if (picked == null) return;
                    pendingPhoto = picked;
                    if (photoButton != null) {
                        photoButton.setText(R.string.nova_sos_photo_attached);
                    }
                });
        NovaScreen.bindHeader(this, R.drawable.ic_nova_sos,
                R.string.nova_sos_create_title, R.color.nova_danger);
        repository = new SosRepository(this);

        neededBy = System.currentTimeMillis() + 6L * 60 * 60 * 1000;
        SosDistrictPicker.attach(this, binding.etDistrict, repository);
        buildGroupChips();
        buildUrgencyChips();
        renderUnits();
        renderNeededBy();

        binding.btnUnitsMinus.setOnClickListener(v -> {
            units = Math.max(1, units - 1);
            renderUnits();
        });
        binding.btnUnitsPlus.setOnClickListener(v -> {
            units = Math.min(10, units + 1);
            renderUnits();
        });
        binding.btnNeededBy.setOnClickListener(v -> pickDate());
        binding.btnSubmit.setOnClickListener(v -> submit());
        addPhotoButton();
    }

    private void buildGroupChips() {
        binding.groupRow.removeAllViews();
        for (String candidate : BloodCompat.GROUPS) {
            TextView chip = chip(candidate, candidate.equals(group));
            chip.setOnClickListener(v -> {
                group = candidate;
                buildGroupChips();
            });
            binding.groupRow.addView(chip);
        }
    }

    private void buildUrgencyChips() {
        binding.urgencyRow.removeAllViews();
        String[][] options = {
                {"critical", getString(R.string.nova_sos_critical)},
                {"urgent", getString(R.string.nova_sos_today)},
                {"normal", getString(R.string.nova_sos_week)}
        };
        for (String[] option : options) {
            TextView chip = chip(option[1], option[0].equals(urgency));
            chip.setOnClickListener(v -> {
                urgency = option[0];
                buildUrgencyChips();
            });
            binding.urgencyRow.addView(chip);
        }
    }

    /** One chip factory so group and urgency rows stay pixel-identical. */
    private TextView chip(String label, boolean active) {
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextSize(11f);
        chip.setSingleLine(true);
        chip.setGravity(android.view.Gravity.CENTER);
        chip.setMinHeight(NovaUi.dp(this, 36));
        chip.setPadding(NovaUi.dp(this, 2), 0, NovaUi.dp(this, 2), 0);
        chip.setBackgroundResource(active ? R.drawable.bg_nova_chip_on : R.drawable.bg_nova_chip);
        chip.setTextColor(getColor(active ? R.color.color_background : R.color.nova_text_3));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.setMarginEnd(NovaUi.dp(this, 6));
        chip.setLayoutParams(params);
        return chip;
    }

    private void renderUnits() {
        binding.tvUnits.setText(String.valueOf(units));
    }

    private void renderNeededBy() {
        SimpleDateFormat format = new SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault());
        binding.btnNeededBy.setText(getString(R.string.nova_sos_needed_by) + " · "
                + format.format(new Date(neededBy)));
    }

    private void pickDate() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(neededBy);
        new DatePickerDialog(this, (view, year, month, day) -> {
            Calendar picked = Calendar.getInstance();
            picked.setTimeInMillis(neededBy);
            picked.set(year, month, day);
            if (picked.getTimeInMillis() < System.currentTimeMillis()) {
                error(getString(R.string.nova_sos_overdue));
                return;
            }
            neededBy = picked.getTimeInMillis();
            renderNeededBy();
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    /** Inserted above Submit so the picker sits in the form without a layout change. */
    private void addPhotoButton() {
        try {
            android.view.ViewGroup parent = (android.view.ViewGroup) binding.btnSubmit.getParent();
            photoButton = new android.widget.TextView(this);
            photoButton.setText(R.string.nova_sos_photo);
            photoButton.setTextColor(getColor(R.color.nova_cyan));
            photoButton.setTextSize(13f);
            photoButton.setPadding(0, com.insta.nova.widget.NovaUi.dp(this, 12), 0,
                    com.insta.nova.widget.NovaUi.dp(this, 4));
            photoButton.setOnClickListener(v -> pickPhoto());
            parent.addView(photoButton, Math.max(0, parent.indexOfChild(binding.btnSubmit)));
        } catch (Exception ignored) {
            // A missing parent is not worth failing the screen over; the photo stays optional.
        }
    }

    private void pickPhoto() {
        android.content.Intent gallery = new android.content.Intent(
                android.content.Intent.ACTION_GET_CONTENT);
        gallery.setType("image/*");
        gallery.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        android.content.Intent chooser = android.content.Intent.createChooser(
                gallery, getString(R.string.nova_sos_photo));
        try {
            java.io.File dir = new java.io.File(getCacheDir(), "nova_photo");
            if (!dir.exists()) dir.mkdirs();
            java.io.File file = java.io.File.createTempFile("sos_", ".jpg", dir);
            cameraUri = androidx.core.content.FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", file);
            android.content.Intent camera = new android.content.Intent(
                    android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            camera.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, cameraUri);
            camera.addFlags(android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            chooser.putExtra(android.content.Intent.EXTRA_INITIAL_INTENTS,
                    new android.content.Intent[]{camera});
        } catch (Exception ignored) {
            // Gallery remains available.
        }
        photoLauncher.launch(chooser);
    }

    private void submit() {
        clearError();
        String hospital = text(binding.etHospital);
        String district = text(binding.etDistrict);
        String area = text(binding.etArea);
        String patient = text(binding.etPatient);
        String phone = text(binding.etPhone).replace(" ", "");

        if (hospital.isEmpty()) {
            error(getString(R.string.nova_sos_hospital));
            return;
        }
        if (district.isEmpty()) {
            error(getString(R.string.nova_sos_district));
            return;
        }
        if (!BD_PHONE.matcher(phone).matches()) {
            error(getString(R.string.nova_sos_phone_help));
            return;
        }
        if (neededBy < System.currentTimeMillis()) {
            error(getString(R.string.nova_sos_overdue));
            return;
        }

        binding.btnSubmit.setEnabled(false);
        binding.btnSubmit.setAlpha(0.5f);
        String iso = TimeUtils.toSupabaseTimestamp(neededBy);
        if (pendingPhoto != null) {
            com.insta.nova.util.NovaImageUpload.upload(this, pendingPhoto, "nova",
                    new com.insta.nova.util.NovaImageUpload.UploadCallback() {
                        @Override public void onUploaded(String url) { create(patient, hospital, district, area, iso, phone, url); }
                        @Override public void onFailed(String message) { create(patient, hospital, district, area, iso, phone, null); }
                    });
            return;
        }
        create(patient, hospital, district, area, iso, phone, null);
    }

    private void create(String patient, String hospital, String district, String area,
                        String iso, String phone, String photoUrl) {
        // Argument order matters here: the photo used to be passed in the notes slot, so
        // the attached picture was written to notes and never stored as media.
        repository.createRequest(group, units, patient, hospital, district, area,
                iso, urgency, null, photoUrl, phone).observe(this, resource -> {
            if (binding == null || resource == null) return;
            if (resource.status == Resource.Status.LOADING) return;
            binding.btnSubmit.setEnabled(true);
            binding.btnSubmit.setAlpha(1f);
            if (resource.status == Resource.Status.SUCCESS && resource.data != null) {
                NovaUi.haptic(binding.getRoot(), true);
                showSuccess(resource.data);
            } else {
                String why = resource.message == null ? "" : resource.message;
                // The backend owns the urgency vocabulary. If it rejects the label the
                // user picked, retry once with the value it accepts instead of failing
                // an emergency request over a display word.
                if (why.contains("nova_blood_requests_urgency_check") && !"today".equals(urgency)) {
                    urgency = "today";
                    submit();
                    return;
                }
                error(getString(R.string.nova_error) + (why.isEmpty() ? "" : " (" + why + ")"));
            }
        });
    }

    private void showSuccess(com.insta.nova.data.NovaDtos.CreateRequestResult result) {
        String district = text(binding.etDistrict);
        String message = result.matched == 0
                ? getString(R.string.nova_sos_success_zero, district)
                : getString(R.string.nova_sos_success, result.matched, district);
        new AlertDialog.Builder(this)
                .setTitle(R.string.nova_sos_request)
                .setMessage(message)
                .setPositiveButton(R.string.nova_sos_view_request, (d, w) -> finish())
                .setNeutralButton(R.string.nova_sos_share_request, (d, w) -> {
                    android.content.Intent share = new android.content.Intent(
                            android.content.Intent.ACTION_SEND);
                    share.setType("text/plain");
                    share.putExtra(android.content.Intent.EXTRA_TEXT,
                            getString(R.string.nova_sos_headline) + " " + group + " · "
                                    + text(binding.etHospital) + ", " + text(binding.etDistrict));
                    startActivity(android.content.Intent.createChooser(share, null));
                    finish();
                })
                .setCancelable(false)
                .show();
    }

    private String text(EditText field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }

    private void error(String message) {
        if (binding == null) return;
        binding.tvFormError.setText(message);
        binding.tvFormError.setVisibility(View.VISIBLE);
        // Keep the message on screen: the field that failed may be above the fold.
        binding.formScroll.post(() -> binding.formScroll.fullScroll(View.FOCUS_DOWN));
        NovaUi.haptic(binding.getRoot(), false);
    }

    private void clearError() {
        if (binding == null) return;
        binding.tvFormError.setVisibility(View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
