package com.insta.nova.sos;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.insta.nova.R;
import com.insta.nova.data.NovaDtos;
import com.insta.nova.data.SosRepository;
import com.insta.nova.databinding.ActivityNovaSosBinding;
import com.insta.nova.databinding.ItemBloodRequestBinding;
import com.insta.nova.hub.NovaScreen;
import com.insta.nova.util.Resource;
import com.insta.nova.util.TimeUtils;
import com.insta.nova.widget.NovaUi;

import java.util.ArrayList;
import java.util.List;

/**
 * Nova SOS feed. Real rows from nova_blood_feed, a group filter, and one working action
 * per card: pledge, undo a pledge, or open your own request.
 */
public class NovaSosActivity extends AppCompatActivity {

    private ActivityNovaSosBinding binding;
    private SosRepository repository;
    private final List<NovaDtos.BloodRequest> items = new ArrayList<>();
    private Adapter adapter;
    private String groupFilter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNovaSosBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        NovaScreen.bindHeader(this, R.drawable.ic_nova_sos,
                R.string.nova_sos_title, R.color.nova_danger);

        repository = new SosRepository(this);
        adapter = new Adapter();
        binding.rvRequests.setLayoutManager(new LinearLayoutManager(this));
        binding.rvRequests.setAdapter(adapter);
        binding.btnCreate.setOnClickListener(v -> toastPendingCreate());
        binding.btnDonor.setOnClickListener(v ->
                startActivity(new android.content.Intent(this, SosDonorActivity.class)));
        binding.btnMy.setOnClickListener(v ->
                startActivity(new android.content.Intent(this, SosMyActivity.class)));

        buildFilters();
        load();
    }

    private void toastPendingCreate() {
        startActivity(new android.content.Intent(this, SosCreateActivity.class));
    }

    private void buildFilters() {
        binding.filterRow.removeAllViews();
        addFilterChip(null, getString(R.string.nova_sos_all_groups));
        for (String group : BloodCompat.GROUPS) addFilterChip(group, group);
    }

    private void addFilterChip(final String group, String label) {
        TextView chip = new TextView(this);
        boolean active = group == null ? groupFilter == null : group.equals(groupFilter);
        chip.setText(label);
        chip.setTextSize(12f);
        chip.setGravity(android.view.Gravity.CENTER);
        chip.setMinHeight(NovaUi.dp(this, 34));
        chip.setPadding(NovaUi.dp(this, 14), 0, NovaUi.dp(this, 14), 0);
        chip.setBackgroundResource(active
                ? R.drawable.bg_nova_chip_on : R.drawable.bg_nova_chip);
        // The selected chip is filled with nova_text, so its label has to be the
        // page background. Using nova_text here painted white on white and the
        // selected blood group looked like an empty chip.
        chip.setTextColor(getColor(active
                ? R.color.color_background : R.color.nova_text_3));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMarginEnd(NovaUi.dp(this, 8));
        chip.setLayoutParams(params);
        chip.setOnClickListener(v -> {
            groupFilter = group;
            buildFilters();
            load();
        });
        binding.filterRow.addView(chip);
    }

    private void load() {
        binding.sosLoading.setVisibility(View.VISIBLE);
        binding.sosError.setVisibility(View.GONE);
        repository.feed(groupFilter, null, 30, 0).observe(this, resource -> {
            if (binding == null || resource == null) return;
            if (resource.status == Resource.Status.LOADING) return;
            binding.sosLoading.setVisibility(View.GONE);
            if (resource.status == Resource.Status.SUCCESS) {
                items.clear();
                if (resource.data != null) items.addAll(resource.data);
                adapter.notifyDataSetChanged();
                binding.sosEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
            } else {
                binding.sosError.setVisibility(View.VISIBLE);
                binding.sosError.setText(errorText(resource.message));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // A request created on the form screen must be visible the moment we come back.
        if (repository != null) load();
    }

    private String errorText(String code) {
        return getString(R.string.nova_error) + (code == null ? "" : " (" + code + ")");
    }

    private void openDetail(NovaDtos.BloodRequest request) {
        android.content.Intent intent = new android.content.Intent(this, SosDetailActivity.class);
        intent.putExtra(SosDetailActivity.EXTRA_ID, request.id);
        intent.putExtra(SosDetailActivity.EXTRA_GROUP, request.bloodGroup);
        intent.putExtra(SosDetailActivity.EXTRA_UNITS, request.unitsNeeded);
        intent.putExtra(SosDetailActivity.EXTRA_HOSPITAL, request.hospital);
        intent.putExtra(SosDetailActivity.EXTRA_DISTRICT, request.district);
        intent.putExtra(SosDetailActivity.EXTRA_AREA, request.area);
        intent.putExtra(SosDetailActivity.EXTRA_PATIENT, request.patientName);
        intent.putExtra(SosDetailActivity.EXTRA_NEEDED_BY, request.neededBy);
        intent.putExtra(SosDetailActivity.EXTRA_STATUS, request.status);
        intent.putExtra(SosDetailActivity.EXTRA_MINE, request.isMine);
        intent.putExtra(SosDetailActivity.EXTRA_CAN_DONATE, request.canDonate);
        intent.putExtra(SosDetailActivity.EXTRA_PLEDGED, request.myPledge);
        if (request.photoUrl != null && !request.photoUrl.isEmpty()) {
            intent.putExtra(SosDetailActivity.EXTRA_PHOTO, request.photoUrl);
        }
        startActivity(intent);
    }

    private void togglePledge(NovaDtos.BloodRequest request, int position) {
        if (request.myPledge) {
            repository.unpledge(request.id).observe(this, resource -> {
                if (resource != null && resource.status == Resource.Status.SUCCESS) {
                    request.myPledge = false;
                    request.pledgedCount = Math.max(0, request.pledgedCount - 1);
                    adapter.notifyItemChanged(position);
                    NovaUi.haptic(binding.rvRequests, true);
                } else if (resource != null && resource.status == Resource.Status.ERROR) {
                    android.widget.Toast.makeText(this, R.string.nova_error,
                            android.widget.Toast.LENGTH_SHORT).show();
                }
            });
            return;
        }
        repository.pledge(request.id).observe(this, resource -> {
            if (resource != null && resource.status == Resource.Status.SUCCESS) {
                request.myPledge = true;
                request.pledgedCount += 1;
                adapter.notifyItemChanged(position);
                NovaUi.haptic(binding.rvRequests, true);
            } else if (resource != null && resource.status == Resource.Status.ERROR) {
                android.widget.Toast.makeText(this, R.string.nova_error,
                        android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }

    private final class Adapter extends RecyclerView.Adapter<Adapter.Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemBloodRequestBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            final NovaDtos.BloodRequest request = items.get(position);
            ItemBloodRequestBinding row = holder.binding;

            row.tvGroup.setText(request.bloodGroup == null ? "?" : request.bloodGroup);
            row.tvGroup.setTextColor(getColor(R.color.nova_text));
            row.tvUnits.setText(getString(R.string.nova_sos_units, request.unitsNeeded));

            String urgency = request.urgency == null ? "normal" : request.urgency;
            row.tvUrgency.setText(urgencyLabel(urgency));
            row.tvUrgency.setTextColor(getColor(urgencyColor(urgency)));

            row.tvHospital.setText(request.hospital == null ? "" : request.hospital);
            StringBuilder where = new StringBuilder();
            if (request.area != null && !request.area.isEmpty()) where.append(request.area).append(", ");
            if (request.district != null) where.append(request.district);
            long needed = TimeUtils.parseTimestamp(request.neededBy);
            if (needed > 0) {
                if (where.length() > 0) where.append(" · ");
                long left = needed - System.currentTimeMillis();
                where.append(left < 0
                        ? getString(R.string.nova_sos_overdue)
                        : getString(R.string.nova_sos_needed_in, countdown(left)));
            }
            row.tvWhere.setText(where.toString());

            row.tvPledged.setText(getString(R.string.nova_sos_pledged,
                    request.pledgedCount, Math.max(request.pledgedCount, request.unitsNeeded)));

            boolean mine = request.isMine;
            row.btnAction.setText(mine ? R.string.nova_sos_your_request
                    : request.myPledge ? R.string.nova_sos_helping
                    : request.canDonate ? R.string.nova_sos_can_donate
                    : R.string.nova_sos_become_donor_help);
            row.btnAction.setBackgroundResource(mine || request.myPledge
                    ? R.drawable.bg_nova_chip : R.drawable.bg_nova_pill);
            row.btnAction.setTextColor(getColor(mine || request.myPledge
                    ? R.color.nova_text : R.color.nova_on_gradient));
            row.btnAction.setTypeface(null, Typeface.BOLD);

            row.getRoot().setOnClickListener(v -> openDetail(request));
            final int pos = holder.getBindingAdapterPosition();
            row.btnAction.setOnClickListener(v -> {
                if (mine) {
                    android.widget.Toast.makeText(NovaSosActivity.this,
                            R.string.nova_sos_manage, android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!request.canDonate && !request.myPledge) {
                    android.widget.Toast.makeText(NovaSosActivity.this,
                            R.string.nova_sos_become_donor_help,
                            android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                togglePledge(request, pos);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ItemBloodRequestBinding binding;

            Holder(ItemBloodRequestBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }

    /** "3h 20m" — the amount of time left, never a relative "ago" string. */
    static String countdown(long millis) {
        long minutes = Math.max(0, millis / 60000L);
        if (minutes >= 24 * 60) return (minutes / (24 * 60)) + "d " + ((minutes / 60) % 24) + "h";
        if (minutes >= 60) return (minutes / 60) + "h " + (minutes % 60) + "m";
        return minutes + "m";
    }

    private String urgencyLabel(String urgency) {
        switch (urgency) {
            case "critical": return getString(R.string.nova_sos_critical);
            case "today":
            case "urgent": return getString(R.string.nova_sos_today);
            case "week":
            case "normal": return getString(R.string.nova_sos_week);
            default: return urgency;
        }
    }

    private int urgencyColor(String urgency) {
        switch (urgency) {
            case "critical": return R.color.nova_danger;
            case "urgent": return R.color.nova_campus;
            default: return R.color.nova_cyan;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
