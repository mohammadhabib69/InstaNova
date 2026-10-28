package com.insta.nova.lite;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.insta.nova.R;
import com.insta.nova.data.NovaDataSaver;
import com.insta.nova.data.NovaDb;
import com.insta.nova.data.NovaSettingsStore;
import com.insta.nova.data.entity.NetUsage;
import com.insta.nova.databinding.ActivityNovaLiteBinding;
import com.insta.nova.hub.NovaScreen;
import com.insta.nova.util.NovaFormat;
import com.insta.nova.widget.NovaUi;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Nova Lite: the data saver switch, what it actually saved (measured, not estimated),
 * and a benchmark computed from the same measured rows the meter records.
 */
public class NovaLiteActivity extends AppCompatActivity {

    private ActivityNovaLiteBinding binding;
    private NovaSettingsStore settings;
    private String mode = "auto";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNovaLiteBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        NovaScreen.bindHeader(this, R.drawable.ic_nova_lite,
                R.string.nova_lite_title, R.color.nova_lite);

        settings = NovaSettingsStore.get(this);
        mode = settings.current().dataSaver == null ? "auto" : settings.current().dataSaver;
        binding.modeOff.setOnClickListener(v -> setMode("off"));
        binding.modeAuto.setOnClickListener(v -> setMode("auto"));
        binding.modeAlways.setOnClickListener(v -> setMode("always"));
        binding.btnExport.setOnClickListener(v -> exportCsv());
        binding.btnBenchmark.setOnClickListener(v -> benchmark());

        renderModes();
        renderUsage();
        renderSaverEffect();
    }

    /**
     * What the data saver does, in words.
     *
     * <p>The Nova Lite screen never shows Instagram state: the Instagram post
     * outbox used to live here and was a second, accidental surface.
     */
    private void renderSaverEffect() {
        if (binding == null) return;
        binding.tvSaverEffect.setText(getString(R.string.nova_lite_saver_effect));
    }

    private void setMode(String next) {
        mode = next;
        settings.setDataSaver(next);
        NovaDataSaver.get(this).refresh();
        renderModes();
        NovaUi.haptic(binding.getRoot(), true);
    }

    private void renderModes() {
        style(binding.modeOff, "off".equals(mode));
        style(binding.modeAuto, "auto".equals(mode));
        style(binding.modeAlways, "always".equals(mode));
        binding.tvState.setText(getString(NovaDataSaver.peekActive(this)
                ? R.string.nova_lite_on : R.string.nova_lite_auto_hint));
    }

    private void style(TextView chip, boolean active) {
        chip.setBackgroundResource(active
                ? R.drawable.bg_nova_chip_on : R.drawable.bg_nova_chip);
        chip.setTextColor(getColor(active ? R.color.color_background : R.color.nova_text_3));
    }

    private void renderUsage() {
        // Room refuses main-thread reads; every number below is computed off the UI thread.
        new Thread(() -> {
            NovaDb db = NovaDb.get(this);
            String today = day(0);
            long todayBytes = db.netUsageDao().bytesForDay(today);
            String weekAgo = day(-6);
            long actual = db.netUsageDao().bytesSince(weekAgo);
            long full = db.netUsageDao().fullBytesSince(weekAgo);
            int percent = full <= 0 ? 0 : (int) Math.max(0, Math.min(100,
                    Math.round((full - actual) * 100.0 / full)));

            List<String> labels = new ArrayList<>();
            float[] perDay = new float[7];
            SimpleDateFormat key = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            SimpleDateFormat label = new SimpleDateFormat("EEE", Locale.getDefault());
            Calendar calendar = Calendar.getInstance();
            for (int i = 6; i >= 0; i--) {
                Calendar cursor = (Calendar) calendar.clone();
                cursor.add(Calendar.DAY_OF_YEAR, -i);
                labels.add(label.format(cursor.getTime()));
                perDay[6 - i] = (float) (db.netUsageDao().bytesForDay(key.format(cursor.getTime()))
                        / 1024.0);
            }
            final long todayValue = todayBytes;
            final long savedValue = Math.max(0, full - actual);
            final int percentValue = percent;
            final String[] labelArray = labels.toArray(new String[0]);

            runOnUiThread(() -> {
                if (binding == null) return;
                binding.tvToday.setText(getString(R.string.nova_lite_today,
                        NovaFormat.megabytes(todayValue)));
                binding.tvSaved.setText(getString(R.string.nova_lite_saved,
                        NovaFormat.megabytes(savedValue), percentValue));
                binding.chart.setEmptyText(getString(R.string.nova_lite_no_usage));
                binding.chart.setData(perDay, labelArray);
                binding.tvBenchmark.setText(full <= 0
                        ? getString(R.string.nova_lite_no_usage)
                        : getString(R.string.nova_lite_benchmark_measured, percentValue,
                        NovaFormat.megabytes(actual)));
            });
        }).start();
    }

    /**
     * The benchmark reports the same measured numbers the meter stored: actual bytes
     * against the full-quality equivalent for every recorded response this week.
     */
    private void benchmark() {
        new Thread(() -> {
            NovaDb db = NovaDb.get(this);
            String weekAgo = day(-6);
            long actual = db.netUsageDao().bytesSince(weekAgo);
            long full = db.netUsageDao().fullBytesSince(weekAgo);
            int percent = full <= 0 ? 0 : (int) Math.round((full - actual) * 100.0 / full);
            runOnUiThread(() -> {
                if (binding == null) return;
                binding.tvBenchmark.setText(full <= 0
                        ? getString(R.string.nova_lite_no_usage)
                        : getString(R.string.nova_lite_benchmark_measured, percent,
                        NovaFormat.megabytes(actual)));
                NovaUi.haptic(binding.getRoot(), true);
            });
        }).start();
    }

    private void exportCsv() {
        new Thread(() -> {
            NovaDb db = NovaDb.get(this);
            List<NetUsage> rows = db.netUsageDao().since(day(-30));
            File file = new File(getCacheDir(), "nova_lite_usage.csv");
            try (FileWriter writer = new FileWriter(file)) {
                writer.write("day,category,bytes,est_full_bytes,saver_active,created_at\n");
                for (NetUsage row : rows) {
                    writer.write(row.day + "," + row.category + "," + row.bytes + ","
                            + row.estFullBytes + "," + row.saverActive + ","
                            + row.createdAt + "\n");
                }
            } catch (IOException e) {
                runOnUiThread(() -> error(getString(R.string.nova_error)));
                return;
            }
            runOnUiThread(() -> {
                Uri uri = FileProvider.getUriForFile(this,
                        getPackageName() + ".fileprovider", file);
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("text/csv");
                share.putExtra(Intent.EXTRA_STREAM, uri);
                share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(share, getString(R.string.nova_lite_export)));
            });
        }).start();
    }

    private void error(String message) {
        binding.tvBenchmark.setText(message);
    }

    private static String day(int offset) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, offset);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.getTime());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
