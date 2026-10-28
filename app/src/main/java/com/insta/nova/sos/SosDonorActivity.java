package com.insta.nova.sos;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.insta.nova.R;
import com.insta.nova.data.NovaDtos;
import com.insta.nova.data.NovaRepository;
import com.insta.nova.data.SosRepository;
import com.insta.nova.databinding.ActivitySosDonorBinding;
import com.insta.nova.hub.NovaScreen;
import com.insta.nova.util.Resource;
import com.insta.nova.widget.NovaUi;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Donor registration and eligibility. The 90-day rule is evaluated locally from the last
 * donation date so the answer is instant and identical to the backend's own check.
 */
public class SosDonorActivity extends AppCompatActivity {

    private ActivitySosDonorBinding binding;
    private SosRepository repository;
    private String group = "A+";
    private String lastDonation;
    private boolean registered;
    private long lastDonationMillis;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySosDonorBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        NovaScreen.bindHeader(this, R.drawable.ic_nova_sos,
                R.string.nova_sos_donor_title, R.color.nova_sos);

        repository = new SosRepository(this);
        SosDistrictPicker.attach(this, binding.etDistrict, repository);
        buildGroupChips();
        renderLastDonation();
        renderEligibility();

        binding.btnLastDonation.setOnClickListener(v -> pickDate());
        binding.swAvailable.setOnCheckedChangeListener((b, checked) -> renderEligibility());
        binding.btnSave.setOnClickListener(v -> save());
        binding.btnLeave.setOnClickListener(v -> leave());
        load();
    }

    private void load() {
        repository.myDonor().observe(this, resource -> {
            if (binding == null || resource == null) return;
            if (resource.status != Resource.Status.SUCCESS || resource.data == null) return;
            NovaDtos.Donor donor = resource.data;
            registered = true;
            if (donor.bloodGroup != null) group = donor.bloodGroup;
            if (donor.district != null) binding.etDistrict.setText(donor.district);
            lastDonation = donor.lastDonationDate;
            lastDonationMillis = 0;
            buildGroupChips();
            renderLastDonation();
            renderEligibility();
            binding.btnLeave.setVisibility(View.VISIBLE);
            binding.swAvailable.setChecked(donor.available);
            // The main action is Save; "Available to donate" is now the switch above it.
            binding.btnSave.setText(R.string.nova_save);
            loadBadge(donor.donationsCount);
        });
    }

    private void loadBadge(int fallback) {
        NovaRepository nova = new NovaRepository(this);
        // nova_donor_badge takes a target user id; passing null drops the argument and
        // PostgREST then answers 404 instead of the badge.
        nova.donorBadge(nova.me(), true).observe(this, resource -> {
            if (binding == null || resource == null) return;
            if (resource.status != Resource.Status.SUCCESS || resource.data == null) return;
            int donations = resource.data.donations;
            String level = BloodCompat.levelName(donations);
            String count = getString(R.string.nova_sos_donations, donations);
            // A brand-new donor has no level yet; do not print a dangling separator.
            binding.tvBadge.setText(level == null || level.isEmpty() ? count : level + " · " + count);
        });
    }

    private void buildGroupChips() {
        binding.groupRow.removeAllViews();
        for (String candidate : BloodCompat.GROUPS) {
            boolean active = candidate.equals(group);
            TextView chip = new TextView(this);
            chip.setText(candidate);
            chip.setTextSize(11f);
            chip.setSingleLine(true);
            chip.setGravity(android.view.Gravity.CENTER);
            chip.setMinHeight(NovaUi.dp(this, 36));
            chip.setBackgroundResource(active
                    ? R.drawable.bg_nova_chip_on : R.drawable.bg_nova_chip);
            chip.setTextColor(getColor(active
                    ? R.color.color_background : R.color.nova_text_3));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            params.setMarginEnd(NovaUi.dp(this, 6));
            chip.setLayoutParams(params);
            chip.setOnClickListener(v -> {
                group = candidate;
                buildGroupChips();
            });
            binding.groupRow.addView(chip);
        }
    }

    private void pickDate() {
        Calendar calendar = Calendar.getInstance();
        if (lastDonationMillis > 0) calendar.setTimeInMillis(lastDonationMillis);
        new DatePickerDialog(this, (view, year, month, day) -> {
            Calendar picked = Calendar.getInstance();
            picked.set(year, month, day);
            if (picked.after(Calendar.getInstance())) {
                error(getString(R.string.nova_sos_overdue));
                return;
            }
            lastDonationMillis = picked.getTimeInMillis();
            lastDonation = new SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    .format(new Date(lastDonationMillis));
            renderLastDonation();
            renderEligibility();
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void renderLastDonation() {
        if (lastDonation == null || lastDonation.isEmpty()) {
            binding.btnLastDonation.setText(R.string.nova_sos_last_donation);
            return;
        }
        binding.btnLastDonation.setText(getString(R.string.nova_sos_last_donation) + " · "
                + lastDonation);
    }

    private void renderEligibility() {
        // Medical eligibility and availability are two different things. With the switch
        // off, "Eligible now" was still shown, so a donor who had stepped back still read
        // as ready to be matched.
        if (binding.swAvailable != null && !binding.swAvailable.isChecked()) {
            binding.tvEligibility.setText(R.string.nova_sos_unavailable);
            binding.tvEligibility.setTextColor(getColor(R.color.nova_text_3));
            return;
        }
        if (lastDonation == null || lastDonation.isEmpty()) {
            binding.tvEligibility.setText(R.string.nova_sos_eligible_now);
            binding.tvEligibility.setTextColor(getColor(R.color.nova_cyan));
            return;
        }
        int days = BloodCompat.daysUntilEligible(lastDonation, new Date());
        if (days <= 0) {
            binding.tvEligibility.setText(R.string.nova_sos_eligible_now);
            binding.tvEligibility.setTextColor(getColor(R.color.nova_cyan));
        } else {
            Date next = BloodCompat.nextEligibleDate(BloodCompat.parseDate(lastDonation));
            String formatted = new SimpleDateFormat("d MMM yyyy", Locale.getDefault())
                    .format(next);
            binding.tvEligibility.setText(getString(R.string.nova_sos_next_eligible, formatted));
            binding.tvEligibility.setTextColor(getColor(R.color.nova_danger));
        }
    }

    private void save() {
        clearError();
        String district = binding.etDistrict.getText().toString().trim();
        if (district.isEmpty()) {
            error(getString(R.string.nova_sos_district));
            return;
        }
        binding.btnSave.setEnabled(false);
        // Already registered for donation means this is an edit, so the row is
        // patched. Inserting again would collide with the existing donor row
        // and the save would fail.
        final String last = lastDonation == null ? "" : lastDonation;
        // The switch is the source of truth. Both calls used to pass a literal true,
        // so a donor could never mark themselves unavailable.
        final boolean available = binding.swAvailable.isChecked();
        androidx.lifecycle.LiveData<Resource<Boolean>> request = registered
                ? repository.updateDonor(group, district, null, last, available)
                : repository.createDonor(group, district, null, last, available);
        request.observe(this, resource -> {
            if (binding == null || resource == null) return;
            if (resource.status == Resource.Status.LOADING) return;
            binding.btnSave.setEnabled(true);
            if (resource.status == Resource.Status.SUCCESS) {
                registered = true;
                binding.btnLeave.setVisibility(View.VISIBLE);
                NovaUi.haptic(binding.getRoot(), true);
                load();
            } else {
                error(getString(R.string.nova_error)
                        + (resource.message == null ? "" : " (" + resource.message + ")"));
            }
        });
    }

    private void leave() {
        repository.leaveDonor().observe(this, resource -> {
            if (resource != null && resource.status == Resource.Status.SUCCESS) {
                finish();
            } else if (resource != null && resource.status == Resource.Status.ERROR) {
                error(resource.message);
            }
        });
    }

    private void error(String message) {
        if (binding == null) return;
        binding.tvDonorError.setText(message);
        binding.tvDonorError.setVisibility(View.VISIBLE);
        NovaUi.haptic(binding.getRoot(), false);
    }

    private void clearError() {
        if (binding != null) binding.tvDonorError.setVisibility(View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
