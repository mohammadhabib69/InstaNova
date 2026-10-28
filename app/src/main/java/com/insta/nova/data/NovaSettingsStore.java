package com.insta.nova.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.remote.RetrofitClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Single source for the caller's nova_settings row.
 *
 * <p>Reads/patches the server and mirrors every field into SharedPreferences so that
 * Nova Safe can make instant, offline decisions (hide_toxic / warn_before_post). A new
 * account gets its row created once with the documented defaults.
 */
public class NovaSettingsStore {

    private static final String TAG = "NovaSettings";
    private static final String PREFS = "nova_settings_cache";
    private static volatile NovaSettingsStore instance;

    private final NovaApi api;
    private final SessionManager session;
    private final SharedPreferences prefs;
    private final MutableLiveData<NovaDtos.Settings> live;
    private final AtomicBoolean loading = new AtomicBoolean(false);

    private NovaSettingsStore(Context context) {
        Context app = context.getApplicationContext();
        api = RetrofitClient.getInstance(app).getNovaApi();
        session = SessionManager.getInstance(app);
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        live = new MutableLiveData<>(readCache());
    }

    public static synchronized NovaSettingsStore get(Context context) {
        if (instance == null) instance = new NovaSettingsStore(context);
        return instance;
    }

    public LiveData<NovaDtos.Settings> settings() {
        return live;
    }

    public NovaDtos.Settings current() {
        NovaDtos.Settings value = live.getValue();
        return value != null ? value : defaults();
    }

    public boolean isLoaded() {
        return prefs.getBoolean("loaded", false);
    }

    /* ------------------------------- server -------------------------------- */

    public void load() {
        String me = session.getUserId();
        if (me == null || me.isEmpty()) return;
        if (!loading.compareAndSet(false, true)) return;
        Map<String, String> q = new HashMap<>();
        q.put("user_id", "eq." + me);
        q.put("select", "*");
        api.getSettings(q).enqueue(new Callback<List<NovaDtos.Settings>>() {
            @Override
            public void onResponse(Call<List<NovaDtos.Settings>> call, Response<List<NovaDtos.Settings>> response) {
                loading.set(false);
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    apply(response.body().get(0), true);
                } else if (response.isSuccessful()) {
                    createDefault(me);
                } else if (response.code() == 404 || response.code() == 400) {
                    Log.w(TAG, "nova_settings unavailable (HTTP " + response.code() + ")");
                }
            }

            @Override
            public void onFailure(Call<List<NovaDtos.Settings>> call, Throwable t) {
                loading.set(false);
                Log.w(TAG, "Settings read failed, using cache: " + t.getMessage());
            }
        });
    }

    private void createDefault(String me) {
        NovaDtos.Settings s = defaults();
        s.userId = me;
        Map<String, Object> body = toBody(s);
        api.upsertSettings("user_id", body).enqueue(new Callback<List<NovaDtos.Settings>>() {
            @Override
            public void onResponse(Call<List<NovaDtos.Settings>> call, Response<List<NovaDtos.Settings>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    apply(response.body().get(0), true);
                } else {
                    apply(s, true);
                }
            }

            @Override
            public void onFailure(Call<List<NovaDtos.Settings>> call, Throwable t) {
                apply(s, true);
            }
        });
    }

    private void apply(NovaDtos.Settings s, boolean persist) {
        if (s == null) return;
        if (s.dataSaver == null) s.dataSaver = "auto";
        if (s.language == null) s.language = "en";
        live.postValue(s);
        if (persist) {
            prefs.edit()
                    .putBoolean("hide_toxic", s.hideToxic)
                    .putBoolean("warn_before_post", s.warnBeforePost)
                    .putBoolean("teen_safe", s.teenSafe)
                    .putString("data_saver", s.dataSaver)
                    .putString("language", s.language)
                    .putBoolean("intro_seen", s.introSeen)
                    .putBoolean("loaded", true)
                    .apply();
        }
    }

    private NovaDtos.Settings readCache() {
        NovaDtos.Settings s = defaults();
        s.hideToxic = prefs.getBoolean("hide_toxic", false);
        s.warnBeforePost = prefs.getBoolean("warn_before_post", true);
        s.teenSafe = prefs.getBoolean("teen_safe", false);
        s.dataSaver = prefs.getString("data_saver", "auto");
        s.language = prefs.getString("language", "en");
        s.introSeen = prefs.getBoolean("intro_seen", false);
        return s;
    }

    private NovaDtos.Settings defaults() {
        NovaDtos.Settings s = new NovaDtos.Settings();
        s.dataSaver = "auto";
        s.language = "en";
        s.warnBeforePost = true;
        s.hideToxic = false;
        s.teenSafe = false;
        s.introSeen = false;
        return s;
    }

    private Map<String, Object> toBody(NovaDtos.Settings s) {
        Map<String, Object> body = new HashMap<>();
        body.put("user_id", s.userId);
        body.put("hide_toxic", s.hideToxic);
        body.put("warn_before_post", s.warnBeforePost);
        body.put("teen_safe", s.teenSafe);
        body.put("data_saver", s.dataSaver);
        body.put("language", s.language);
        body.put("intro_seen", s.introSeen);
        return body;
    }

    /* ------------------------------- writes -------------------------------- */

    public void update(String field, Object value) {
        NovaDtos.Settings s = current().copy();
        switch (field) {
            case "hide_toxic": s.hideToxic = Boolean.TRUE.equals(value); break;
            case "warn_before_post": s.warnBeforePost = Boolean.TRUE.equals(value); break;
            case "teen_safe": s.teenSafe = Boolean.TRUE.equals(value); break;
            case "data_saver": s.dataSaver = String.valueOf(value); break;
            case "language": s.language = String.valueOf(value); break;
            case "intro_seen": s.introSeen = Boolean.TRUE.equals(value); break;
            default: return;
        }
        live.postValue(s);
        apply(s, true);
        String me = session.getUserId();
        if (me == null || me.isEmpty()) return;
        Map<String, Object> body = new HashMap<>();
        body.put(field, value);
        api.patchSettings("eq." + me, body).enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> call, Response<Void> response) {
                if (!response.isSuccessful()) Log.w(TAG, "Settings patch HTTP " + response.code());
            }
            @Override public void onFailure(Call<Void> call, Throwable t) {
                Log.w(TAG, "Settings patch failed: " + t.getMessage());
            }
        });
    }

    public void setHideToxic(boolean value) { update("hide_toxic", value); }
    public void setWarnBeforePost(boolean value) { update("warn_before_post", value); }
    public void setTeenSafe(boolean value) { update("teen_safe", value); }
    public void setDataSaver(String mode) { update("data_saver", mode); }
    public void setIntroSeen() { update("intro_seen", true); }

    public void setLanguage(Context context, String language) {
        update("language", language);
        applyLanguage(language);
    }

    /** Applies a language immediately, without recreating the process. */
    public static void applyLanguage(String language) {
        LocaleListCompat locales = "bn".equals(language)
                ? LocaleListCompat.forLanguageTags("bn-BD")
                : LocaleListCompat.forLanguageTags("en");
        AppCompatDelegate.setApplicationLocales(locales);
    }
}
