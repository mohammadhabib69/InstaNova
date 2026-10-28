package com.insta.nova.sos;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.insta.nova.R;
import com.insta.nova.data.NovaDtos;
import com.insta.nova.data.SosRepository;
import com.insta.nova.databinding.ActivitySosMyBinding;
import com.insta.nova.hub.NovaScreen;
import com.insta.nova.util.Resource;
import com.insta.nova.util.TimeUtils;
import com.insta.nova.widget.NovaUi;

import java.util.List;

/**
 * Everything the signed-in user owns in Nova SOS: the requests they opened and the
 * requests they promised blood for. Both lists come from the server, never from a cache.
 */
public class SosMyActivity extends AppCompatActivity {

    private ActivitySosMyBinding binding;
    private SosRepository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySosMyBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        NovaScreen.bindHeader(this, R.drawable.ic_nova_sos,
                R.string.nova_sos_my_requests, R.color.nova_sos);

        repository = new SosRepository(this);
        load();
    }

    private void load() {
        repository.myRequests().observe(this, resource -> {
            if (binding == null || resource == null) return;
            if (resource.status == Resource.Status.ERROR) {
                showLoadError();
                return;
            }
            if (resource.status != Resource.Status.SUCCESS) return;
            binding.tvSosMyError.setVisibility(View.GONE);
            renderRequests(resource.data == null
                    ? java.util.Collections.emptyList() : resource.data);
        });
        repository.myPledges().observe(this, resource -> {
            if (binding == null || resource == null) return;
            if (resource.status == Resource.Status.ERROR) {
                showLoadError();
                return;
            }
            if (resource.status != Resource.Status.SUCCESS) return;
            binding.tvSosMyError.setVisibility(View.GONE);
            renderPledges(resource.data == null
                    ? java.util.Collections.emptyList() : resource.data);
        });
    }

    /**
     * A failed read used to leave both lists empty, which reads exactly like
     * "you have no requests". Say what actually happened instead.
     */
    private void showLoadError() {
        binding.tvSosMyError.setVisibility(View.VISIBLE);
        binding.tvNoRequests.setVisibility(View.GONE);
        binding.tvNoMyPledges.setVisibility(View.GONE);
    }

    private void renderRequests(List<NovaDtos.BloodRequest> requests) {
        binding.requestList.removeAllViews();
        binding.tvNoRequests.setVisibility(requests.isEmpty() ? View.VISIBLE : View.GONE);
        for (NovaDtos.BloodRequest request : requests) {
            String subtitle = (request.status == null ? "open" : request.status)
                    + " · " + units(request);
            binding.requestList.addView(row(groupLabel(request), hospitalLine(request), subtitle,
                    v -> openDetail(request)));
        }
    }

    private void renderPledges(List<NovaDtos.BloodRequest> requests) {
        binding.pledgeList.removeAllViews();
        binding.tvNoMyPledges.setVisibility(requests.isEmpty() ? View.VISIBLE : View.GONE);
        for (NovaDtos.BloodRequest request : requests) {
            long needed = TimeUtils.parseTimestamp(request.neededBy);
            String subtitle = needed > 0
                    ? getString(R.string.nova_sos_needed_in,
                    NovaSosActivity.countdown(needed - System.currentTimeMillis()))
                    : units(request);
            binding.pledgeList.addView(row(groupLabel(request), hospitalLine(request), subtitle,
                    v -> openDetail(request)));
        }
    }

    private String groupLabel(NovaDtos.BloodRequest request) {
        return request.bloodGroup == null ? "?" : request.bloodGroup;
    }

    private String hospitalLine(NovaDtos.BloodRequest request) {
        String hospital = request.hospital == null ? "" : request.hospital;
        String district = request.district == null ? "" : request.district;
        return district.isEmpty() ? hospital : hospital + " · " + district;
    }

    private String units(NovaDtos.BloodRequest request) {
        return getString(R.string.nova_sos_units, request.unitsNeeded);
    }

    private View row(String group, String title, String subtitle, View.OnClickListener click) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackgroundResource(R.drawable.bg_nova_card);
        card.setPadding(NovaUi.dp(this, 14), NovaUi.dp(this, 12),
                NovaUi.dp(this, 14), NovaUi.dp(this, 12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = NovaUi.dp(this, 8);
        card.setLayoutParams(params);
        card.setOnClickListener(click);

        TextView badge = new TextView(this);
        badge.setText(group);
        badge.setTextSize(12f);
        badge.setGravity(Gravity.CENTER);
        badge.setBackgroundResource(R.drawable.bg_nova_badge);
        badge.setTextColor(getColor(R.color.nova_text));
        badge.setLayoutParams(new LinearLayout.LayoutParams(NovaUi.dp(this, 42),
                NovaUi.dp(this, 42)));
        card.addView(badge);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        columnParams.setMarginStart(NovaUi.dp(this, 12));
        column.setLayoutParams(columnParams);

        TextView main = new TextView(this);
        main.setText(title);
        main.setTextColor(getColor(R.color.nova_text));
        main.setTextSize(14f);
        column.addView(main);

        TextView sub = new TextView(this);
        sub.setText(subtitle);
        sub.setTextColor(getColor(R.color.nova_text_3));
        sub.setTextSize(12f);
        column.addView(sub);

        card.addView(column);
        return card;
    }

    private void openDetail(NovaDtos.BloodRequest request) {
        Intent intent = new Intent(this, SosDetailActivity.class);
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
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
