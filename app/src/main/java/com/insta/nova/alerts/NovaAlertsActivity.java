package com.insta.nova.alerts;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.insta.nova.R;
import com.insta.nova.campus.NovaCampusActivity;
import com.insta.nova.data.NovaDtos;
import com.insta.nova.data.NovaRepository;
import com.insta.nova.databinding.ActivityNovaAlertsBinding;
import com.insta.nova.databinding.ItemNovaAlertBinding;
import com.insta.nova.hub.NovaScreen;
import com.insta.nova.sos.NovaSosActivity;
import com.insta.nova.util.Resource;
import com.insta.nova.util.TimeUtils;
import com.insta.nova.widget.NovaUi;

import java.util.ArrayList;
import java.util.List;

/**
 * Nova alerts: one list for SOS, Campus and safety events. Rows are real rows from
 * nova_alerts; opening one marks it read on the server and deep-links to its module.
 */
public class NovaAlertsActivity extends AppCompatActivity {

    private ActivityNovaAlertsBinding binding;
    private NovaRepository repository;
    private final List<NovaDtos.Alert> items = new ArrayList<>();
    private Adapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNovaAlertsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        NovaScreen.bindHeader(this, R.drawable.ic_bell_nova,
                R.string.nova_alerts_title, R.color.nova_violet);

        repository = new NovaRepository(this);
        adapter = new Adapter();
        binding.rvAlerts.setLayoutManager(new LinearLayoutManager(this));
        binding.rvAlerts.setAdapter(adapter);

        binding.btnMarkAll.setOnClickListener(v -> markAllRead());
        load();
    }

    private void load() {
        binding.alertsLoading.setVisibility(View.VISIBLE);
        binding.alertsError.setVisibility(View.GONE);
        repository.alerts(50, null).observe(this, resource -> {
            if (binding == null || resource == null) return;
            if (resource.status == Resource.Status.LOADING) return;
            binding.alertsLoading.setVisibility(View.GONE);
            if (resource.status == Resource.Status.SUCCESS) {
                items.clear();
                if (resource.data != null) items.addAll(resource.data);
                adapter.notifyDataSetChanged();
                binding.alertsError.setVisibility(View.GONE);
                binding.alertsEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                binding.btnMarkAll.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
            } else {
                // The error label sits in the same slot as the empty label, so
                // leaving the empty one visible drew the two strings on top of
                // each other and neither could be read.
                binding.alertsError.setVisibility(View.VISIBLE);
                binding.alertsEmpty.setVisibility(View.GONE);
                binding.btnMarkAll.setVisibility(View.GONE);
            }
        });
    }

    private void markAllRead() {
        repository.markAllAlertsRead().observe(this, resource -> {
            if (resource == null || resource.status != Resource.Status.SUCCESS) return;
            for (NovaDtos.Alert alert : items) alert.isRead = true;
            adapter.notifyDataSetChanged();
            NovaUi.haptic(binding.getRoot(), true);
        });
    }

    private void open(NovaDtos.Alert alert) {
        if (!alert.isRead) {
            alert.isRead = true;
            adapter.notifyItemChanged(items.indexOf(alert));
            repository.markAlertRead(alert.id);
        }
        String kind = alert.kind == null ? "" : alert.kind;
        if (kind.startsWith("sos") || kind.startsWith("blood")) {
            startActivity(new Intent(this, NovaSosActivity.class));
        } else if (kind.startsWith("campus") || kind.startsWith("event")) {
            startActivity(new Intent(this, NovaCampusActivity.class));
        }
    }

    private final class Adapter extends RecyclerView.Adapter<Adapter.Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemNovaAlertBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            NovaDtos.Alert alert = items.get(position);
            ItemNovaAlertBinding row = holder.binding;
            row.alertTitle.setText(alert.title == null ? "" : alert.title);
            row.alertBody.setText(alert.body == null ? "" : alert.body);
            row.alertBody.setVisibility(alert.body == null || alert.body.isEmpty()
                    ? View.GONE : View.VISIBLE);
            long when = TimeUtils.parseTimestamp(alert.createdAt);
            row.alertTime.setText(when > 0
                    ? TimeUtils.formatCompactRelativeTime(when)
                    : "");
            row.alertDot.setVisibility(alert.isRead ? View.INVISIBLE : View.VISIBLE);
            row.alertTitle.setTypeface(null, alert.isRead ? Typeface.NORMAL : Typeface.BOLD);
            row.getRoot().setAlpha(alert.isRead ? 0.72f : 1f);
            row.getRoot().setOnClickListener(v -> open(alert));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ItemNovaAlertBinding binding;

            Holder(ItemNovaAlertBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
