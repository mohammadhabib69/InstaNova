package com.insta.nova.alerts;

import android.content.Context;
import android.content.SharedPreferences;

import com.insta.nova.data.NovaDtos;
import com.insta.nova.data.NovaRepository;
import com.insta.nova.data.remote.RetrofitClient;
import com.insta.nova.data.NovaApi;
import com.insta.nova.util.Resource;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Polls nova_alerts for the signed-in user and raises a system notification once per
 * alert id. IDs already announced are remembered locally, so a restart does not
 * re-notify the same blood request.
 */
public final class AlertsPoller {

    private static final String PREFS = "nova_alerts_seen";
    private static final String KEY_SEEN = "seen_ids";
    private static final int LIMIT = 8;

    private AlertsPoller() {
    }

    public static void tick(Context context) {
        final Context app = context.getApplicationContext();
        NovaNotifications.ensureChannels(app);

        NovaRepository repository = new NovaRepository(app);
        String me = repository.me();
        if (me == null || me.isEmpty()) return;

        NovaApi api = RetrofitClient.getInstance(app).getNovaApi();
        Map<String, String> q = new HashMap<>();
        q.put("user_id", "eq." + me);
        q.put("is_read", "eq.false");
        q.put("order", "created_at.desc");
        q.put("limit", String.valueOf(LIMIT));
        api.getAlerts(q).enqueue(new Callback<List<NovaDtos.Alert>>() {
            @Override
            public void onResponse(Call<List<NovaDtos.Alert>> call,
                                   Response<List<NovaDtos.Alert>> response) {
                if (!response.isSuccessful() || response.body() == null) return;
                announce(app, response.body());
            }

            @Override
            public void onFailure(Call<List<NovaDtos.Alert>> call, Throwable t) {
                // Offline: the next tick will try again.
            }
        });
    }

    private static void announce(Context app, List<NovaDtos.Alert> alerts) {
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> seen = new HashSet<>(prefs.getStringSet(KEY_SEEN, new HashSet<>()));
        boolean changed = false;
        int slot = 1000;
        for (NovaDtos.Alert alert : alerts) {
            if (alert == null || alert.id == null || seen.contains(alert.id)) continue;
            NovaNotifications.show(app, alert, slot++);
            seen.add(alert.id);
            changed = true;
        }
        if (changed) {
            // Keep the memory bounded: only the most recent 200 ids matter.
            if (seen.size() > 200) {
                Set<String> trimmed = new HashSet<>();
                int keep = 0;
                for (String id : seen) {
                    if (keep++ >= 200) break;
                    trimmed.add(id);
                }
                seen = trimmed;
            }
            prefs.edit().putStringSet(KEY_SEEN, seen).apply();
        }
    }
}
