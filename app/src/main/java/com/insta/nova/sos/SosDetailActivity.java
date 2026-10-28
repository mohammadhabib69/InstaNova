package com.insta.nova.sos;

import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.insta.nova.R;
import com.insta.nova.data.NovaDtos;
import com.insta.nova.data.SosRepository;
import com.insta.nova.databinding.ActivitySosDetailBinding;
import com.insta.nova.hub.NovaScreen;
import com.insta.nova.util.Resource;
import com.insta.nova.util.TimeUtils;
import com.insta.nova.widget.NovaUi;

import java.util.List;

/**
 * One blood request: who needs what, who is helping, and the single action that applies
 * to the viewer (pledge, reveal contact, confirm a donor, or close the request).
 */
public class SosDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ID = "extra_id";
    public static final String EXTRA_GROUP = "extra_group";
    public static final String EXTRA_UNITS = "extra_units";
    public static final String EXTRA_HOSPITAL = "extra_hospital";
    public static final String EXTRA_DISTRICT = "extra_district";
    public static final String EXTRA_AREA = "extra_area";
    public static final String EXTRA_PATIENT = "extra_patient";
    public static final String EXTRA_NEEDED_BY = "extra_needed_by";
    public static final String EXTRA_STATUS = "extra_status";
    public static final String EXTRA_MINE = "extra_mine";
    public static final String EXTRA_CAN_DONATE = "extra_can_donate";
    public static final String EXTRA_PLEDGED = "extra_pledged";
    public static final String EXTRA_PHOTO = "extra_photo";

    private ActivitySosDetailBinding binding;
    private SosRepository repository;
    private String requestId;
    private boolean mine;
    private boolean pledged;
    private boolean canDonate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySosDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        NovaScreen.bindHeader(this, R.drawable.ic_nova_sos,
                R.string.nova_sos_title, R.color.nova_sos);

        repository = new SosRepository(this);
        Intent intent = getIntent();
        requestId = intent.getStringExtra(EXTRA_ID);
        mine = intent.getBooleanExtra(EXTRA_MINE, false);
        pledged = intent.getBooleanExtra(EXTRA_PLEDGED, false);
        canDonate = intent.getBooleanExtra(EXTRA_CAN_DONATE, false);

        bindHeaderInfo(intent);
        bindPhoto(intent.getStringExtra(EXTRA_PHOTO));
        bindActions();
        loadPledges();
    }

    private void bindHeaderInfo(Intent intent) {
        binding.tvGroup.setText(value(intent, EXTRA_GROUP, "?"));
        binding.tvUnits.setText(getString(R.string.nova_sos_units,
                intent.getIntExtra(EXTRA_UNITS, 1)));
        binding.tvHospital.setText(value(intent, EXTRA_HOSPITAL, ""));
        String area = value(intent, EXTRA_AREA, "");
        String district = value(intent, EXTRA_DISTRICT, "");
        long needed = TimeUtils.parseTimestamp(value(intent, EXTRA_NEEDED_BY, ""));
        StringBuilder where = new StringBuilder(area.isEmpty() ? district : area + ", " + district);
        if (needed > 0) {
            long left = needed - System.currentTimeMillis();
            where.append(" · ").append(left < 0
                    ? getString(R.string.nova_sos_overdue)
                    : getString(R.string.nova_sos_needed_in, NovaSosActivity.countdown(left)));
        }
        binding.tvWhere.setText(where);
        binding.tvStatus.setText(value(intent, EXTRA_STATUS, "open"));
        String patient = value(intent, EXTRA_PATIENT, "");
        binding.tvPatient.setVisibility(patient.isEmpty() ? View.GONE : View.VISIBLE);
        binding.tvPatient.setText(patient);
        binding.tvPledgesTitle.setText(R.string.nova_sos_pledges);
    }

    /**
     * The requester's optional photo. The feed does not carry it, so a missing extra is
     * resolved from the row instead of leaving the detail blank.
     */
    private void bindPhoto(String photoUrl) {
        if (photoUrl != null && !photoUrl.trim().isEmpty()) {
            showPhoto(photoUrl);
            return;
        }
        if (requestId == null || requestId.isEmpty()) return;
        repository.requestPhoto(requestId).observe(this, resource -> {
            if (binding == null || resource == null || resource.data == null) return;
            if (resource.data.trim().isEmpty()) return;
            showPhoto(resource.data);
        });
    }

    private void showPhoto(String photoUrl) {
        binding.ivSosPhoto.setVisibility(View.VISIBLE);
        com.insta.nova.util.GlideUtils.load(binding.ivSosPhoto, "nova", photoUrl,
                R.drawable.ic_image, R.drawable.ic_image);
        binding.ivSosPhoto.setOnClickListener(v -> {
            Intent viewer = new Intent(this, com.insta.nova.ui.chat.ImageViewerActivity.class);
            viewer.putExtra(com.insta.nova.ui.chat.ImageViewerActivity.EXTRA_IMAGE_URL, photoUrl);
            viewer.putExtra(com.insta.nova.ui.chat.ImageViewerActivity.EXTRA_BUCKET, "nova");
            startActivity(viewer);
        });
    }

    private String value(Intent intent, String key, String fallback) {
        String raw = intent.getStringExtra(key);
        return raw == null || raw.isEmpty() ? fallback : raw;
    }

    private void bindActions() {
        if (mine) {
            binding.btnPrimary.setText(R.string.nova_sos_mark_fulfilled);
            binding.btnPrimary.setOnClickListener(v -> close());
            binding.btnSecondary.setVisibility(View.GONE);
            return;
        }
        // The contact stays reachable either way: the request detail is useless without a
        // way to call, and hiding the button whenever the viewer was not a donor left the
        // screen with no contact at all. The RPC still decides whether it may be handed out.
        binding.btnSecondary.setVisibility(View.VISIBLE);
        binding.btnSecondary.setText(R.string.nova_sos_phone);
        binding.btnSecondary.setOnClickListener(v -> revealPhone());
        binding.tvPhone.setOnClickListener(v -> dial(binding.tvPhone.getText().toString()));

        if (!canDonate && !pledged) {
            binding.btnPrimary.setText(R.string.nova_sos_become_donor_help);
            binding.btnPrimary.setOnClickListener(v -> startActivity(
                    new Intent(this, SosDonorActivity.class)));
            return;
        }
        renderPledgeButton();
    }

    private void renderPledgeButton() {
        binding.btnPrimary.setText(pledged ? R.string.nova_sos_undo : R.string.nova_sos_can_donate);
        binding.btnPrimary.setBackgroundResource(pledged
                ? R.drawable.bg_nova_chip : R.drawable.bg_nova_pill);
        binding.btnPrimary.setTextColor(getColor(pledged
                ? R.color.nova_text : R.color.nova_on_gradient));
        binding.btnPrimary.setOnClickListener(v -> togglePledge());
    }

    private void togglePledge() {
        if (pledged) {
            repository.unpledge(requestId).observe(this, resource -> done(resource, false));
        } else {
            repository.pledge(requestId).observe(this, resource -> done(resource, true));
        }
    }

    private void done(Resource<?> resource, boolean nowPledged) {
        if (resource == null || resource.status == Resource.Status.LOADING) return;
        if (resource.status == Resource.Status.SUCCESS) {
            pledged = nowPledged;
            renderPledgeButton();
            NovaUi.haptic(binding.getRoot(), true);
            loadPledges();
        } else {
            error(resource.message);
        }
    }

    private void revealPhone() {
        repository.contact(requestId).observe(this, resource -> {
            if (resource == null || resource.status == Resource.Status.LOADING) return;
            if (resource.status == Resource.Status.SUCCESS && resource.data != null) {
                binding.tvPhone.setVisibility(View.VISIBLE);
                binding.tvPhone.setText(resource.data);
                binding.tvPhone.setTextColor(getColor(R.color.nova_cyan));
            } else {
                error(resource.message);
            }
        });
    }

    /** Dial pad, not a direct call: the user still presses the green button. */
    private void dial(String number) {
        String clean = number == null ? "" : number.replaceAll("[^0-9+]", "");
        if (clean.isEmpty()) return;
        Intent dial = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + clean));
        startActivity(dial);
    }

    private void close() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.nova_sos_mark_fulfilled)
                .setMessage(R.string.nova_sos_pledges)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.nova_sos_mark_fulfilled, (d, w) ->
                        repository.setStatus(requestId, "fulfilled").observe(this, resource -> {
                            if (resource != null && resource.status == Resource.Status.SUCCESS) {
                                finish();
                            } else if (resource != null) {
                                error(resource.message);
                            }
                        }))
                .show();
    }

    private void loadPledges() {
        repository.pledges(requestId).observe(this, resource -> {
            if (binding == null || resource == null) return;
            if (resource.status != Resource.Status.SUCCESS || resource.data == null) return;
            renderPledges(resource.data);
        });
    }

    private void renderPledges(List<NovaDtos.Pledge> pledges) {
        binding.pledgeList.removeAllViews();
        binding.tvNoPledges.setVisibility(pledges.isEmpty() ? View.VISIBLE : View.GONE);
        binding.tvNoPledges.setText(R.string.nova_sos_no_pledges);
        for (NovaDtos.Pledge pledge : pledges) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(R.drawable.bg_nova_card);
            row.setPadding(NovaUi.dp(this, 14), NovaUi.dp(this, 12),
                    NovaUi.dp(this, 14), NovaUi.dp(this, 12));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = NovaUi.dp(this, 8);
            row.setLayoutParams(params);

            TextView name = new TextView(this);
            name.setText((pledge.username == null ? "" : "@" + pledge.username)
                    + " · " + (pledge.bloodGroup == null ? "" : pledge.bloodGroup));
            name.setTextColor(getColor(R.color.nova_text));
            name.setTextSize(13f);
            name.setTypeface(null, Typeface.BOLD);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            name.setLayoutParams(nameParams);
            row.addView(name);

            if (mine) {
                TextView confirm = new TextView(this);
                confirm.setText(R.string.nova_sos_confirm_donation);
                confirm.setTextColor(getColor(R.color.nova_cyan));
                confirm.setTextSize(12f);
                confirm.setPadding(NovaUi.dp(this, 8), NovaUi.dp(this, 6),
                        NovaUi.dp(this, 8), NovaUi.dp(this, 6));
                confirm.setOnClickListener(v -> confirmDonation(pledge));
                row.addView(confirm);
            } else {
                TextView status = new TextView(this);
                status.setText(pledge.status == null ? "" : pledge.status);
                status.setTextColor(getColor(R.color.nova_text_3));
                status.setTextSize(11f);
                row.addView(status);
            }
            binding.pledgeList.addView(row);
        }
    }

    private void confirmDonation(NovaDtos.Pledge pledge) {
        repository.confirmDonation(requestId, pledge.donorId).observe(this, resource -> {
            if (resource != null && resource.status == Resource.Status.SUCCESS) {
                loadPledges();
            } else if (resource != null) {
                error(resource.message);
            }
        });
    }

    private void error(String message) {
        if (binding == null) return;
        binding.tvDetailError.setText(getString(R.string.nova_error)
                + (message == null ? "" : " (" + message + ")"));
        binding.tvDetailError.setVisibility(View.VISIBLE);
        NovaUi.haptic(binding.getRoot(), false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
