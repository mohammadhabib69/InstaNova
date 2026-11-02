package com.insta.nova;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;

/**
 * Custom Application class initializing app-wide resources and fonts.
 */
public class InstaApp extends Application {

    private static InstaApp instance;
    private static Typeface wordmarkTypeface;

    /** Below this much free space, telemetry that writes to disk is paused. */
    private static final long LOW_STORAGE_BYTES = 100L * 1024 * 1024;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        // Appearance must be resolved before any activity inflates its theme. The settings screen
        // used to call setDefaultNightMode for the running process only, so "Light" fell back to
        // the manifest's dark theme on the next launch and looked ignored.
        applySavedTheme();
        applySavedLocale();
        registerLargerTextCallbacks();
        // Phase 0 (P0-08/P0-09): observability first — every later phase is verified
        // through these two. Firebase init failure used to be swallowed (audit S11),
        // which turned a configuration error into an unexplainable auth failure.
        try {
            com.google.firebase.FirebaseApp.initializeApp(this);
        } catch (Exception e) {
            com.insta.nova.data.firebase.CrashReporter.record(e, "firebase_init");
        }
        com.insta.nova.data.firebase.AppAnalytics.init(this);
        applyStorageAwarePerformanceCollection();
        // P0-10: App Check, before any other Firebase product is used so the first
        // Firestore/FCM call already carries a token. Wrapped: a missing Play Integrity
        // binding must not turn into a startup crash (audit S11 — a config error that
        // presents as something else entirely).
        try {
            com.insta.nova.data.firebase.AppCheckInstaller.install(this);
        } catch (Throwable t) {
            com.insta.nova.data.firebase.CrashReporter.record(t, "appcheck_init");
        }
        // Initialize Remote Config
        com.insta.nova.data.firebase.RemoteConfigManager.getInstance().fetchAndActivate();
        // Calling system: notification channel, incoming call listener, and FCM registration
        com.insta.nova.calling.CallNotificationHelper.createNotificationChannel(this);
        com.insta.nova.calling.CallManager.getInstance(this).startIncomingCallListener();
        com.insta.nova.calling.InstaFirebaseMessagingService.registerTokenWithBackend(this);

        // PAGE 17: presence heartbeat — touch_active() on start and every 60 s while
        // the app is in the foreground. No-ops if the RPC is missing.
        com.insta.nova.data.repository.ChatRepository.startPresenceHeartbeat(this);
    }


    /**
     * Turns Firebase Performance collection off while the device is short of space.
     *
     * <p>Firebase Perf keeps its own queue in SQLite and retries every couple of seconds.
     * On a full disk that produced a failed insert and an `E SQLiteDatabase:
     * SQLiteFullException` line at that cadence for as long as space stayed short — all of
     * it from the SDK, none of it ours (measured: MIGRATION_LOG (86)). The write is
     * harmless to user data, but it hammers the disk exactly when the disk is the problem.
     *
     * <p>Nothing here disables perf permanently: the check runs again on every start and
     * whenever the app comes to the foreground, so collection resumes once space is back.
     * Wrapped, because a telemetry preference must never be a reason the app fails to
     * start.
     */
    public static void applyStorageAwarePerformanceCollection() {
        try {
            android.os.StatFs stat = new android.os.StatFs(
                    android.os.Environment.getDataDirectory().getAbsolutePath());
            long freeBytes = stat.getAvailableBytes();
            boolean enabled = freeBytes > LOW_STORAGE_BYTES;
            com.google.firebase.perf.FirebasePerformance perf =
                    com.google.firebase.perf.FirebasePerformance.getInstance();
            boolean was = perf.isPerformanceCollectionEnabled();
            if (was != enabled) {
                perf.setPerformanceCollectionEnabled(enabled);
                android.util.Log.i("InstaApp", "performance collection " + (enabled ? "resumed" : "paused")
                        + " (" + (freeBytes / (1024 * 1024)) + " MB free)");
            }
            // Logged even when nothing changes: without this the check is indistinguishable
            // from never having run, which is exactly how it looked the first time.
            android.util.Log.i("InstaApp", "perf storage check: enabled=" + enabled + " was=" + was
                    + " free=" + (freeBytes / (1024 * 1024)) + "MB");
        } catch (Throwable t) {
            android.util.Log.w("InstaApp", "perf storage check skipped: " + t.getMessage());
        }
    }

    /**
     * Applies Settings > Accessibility > Larger text.
     *
     * <p>The switch stored {@code larger_text} and nothing ever read it, so it was a
     * toggle that only changed its own appearance. Scaling is applied to each Activity's
     * resources in {@code onActivityPreCreated} — the last hook that still runs before
     * {@code onCreate} inflates anything — so text is already the right size on the first
     * frame rather than jumping after a relayout.
     *
     * <p>{@code onActivityPreCreated} arrived in API 29. Below that the preference simply
     * has no effect; nothing crashes and the device's own font-size setting still applies.
     */
    private void registerLargerTextCallbacks() {
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle state) {}
            @Override public void onActivityStarted(Activity activity) {}
            @Override public void onActivityResumed(Activity activity) {}
            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
            @Override public void onActivityDestroyed(Activity activity) {}

            @Override
            public void onActivityPreCreated(Activity activity, Bundle state) {
                applyLargerText(activity);
            }
        });
    }

    /** Multiplier applied on top of the device font scale when the switch is on. */
    private static final float LARGER_TEXT_SCALE = 1.18f;

    private void applyLargerText(Activity activity) {
        try {
            boolean enabled = new com.insta.nova.data.local.AppSettingsStore(this).largerText();
            android.content.res.Configuration config =
                    new android.content.res.Configuration(activity.getResources().getConfiguration());
            // Always normalise against 1.0 rather than the current value, otherwise every
            // Activity creation would compound the scale (1.18 -> 1.39 -> ...).
            config.fontScale = enabled ? LARGER_TEXT_SCALE : 1.0f;
            activity.getResources().updateConfiguration(
                    config, activity.getResources().getDisplayMetrics());
        } catch (RuntimeException e) {
            com.insta.nova.data.firebase.CrashReporter.record(e, "larger_text_init");
        }
    }

    /**
     * Re-applies the language chosen in Settings > Language.
     *
     * <p>AppCompat restores a stored locale on its own once the
     * {@code AppLocalesMetadataHolderService} is declared in the manifest, but a build
     * that predates that declaration has the choice sitting in {@code AppSettingsStore}
     * and nowhere else. Reading it here makes the first launch after the upgrade honour
     * the setting instead of silently starting in English.
     */
    private void applySavedLocale() {
        try {
            String tag = new com.insta.nova.data.local.AppSettingsStore(this).language();
            if (tag == null || tag.trim().isEmpty()) return;

            // Treat our own store as the source of truth rather than trusting
            // AppCompat's stored copy. Declaring AppLocalesMetadataHolderService was
            // not enough on this build: the choice persisted to AppSettingsStore but
            // no AppCompat locale file was ever written, so the app came back in
            // English on the next cold start.
            androidx.core.os.LocaleListCompat desired =
                    androidx.core.os.LocaleListCompat.forLanguageTags(tag);
            androidx.core.os.LocaleListCompat current =
                    androidx.appcompat.app.AppCompatDelegate.getApplicationLocales();
            if (current.equals(desired)) return;
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(desired);
        } catch (RuntimeException e) {
            com.insta.nova.data.firebase.CrashReporter.record(e, "locale_init");
        }
    }

    private void applySavedTheme() {
        try {
            android.content.SharedPreferences sp =
                    getSharedPreferences("insta_settings", Context.MODE_PRIVATE);
            int mode = sp.getInt(com.insta.nova.ui.settings.SettingsActivity.PREF_THEME,
                    androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(mode);
        } catch (RuntimeException e) {
            com.insta.nova.data.firebase.CrashReporter.record(e, "theme_init");
        }
    }

    public static InstaApp getInstance() {
        return instance;
    }
    /**
     * Returns the InstaNova wordmark typeface.
     * Falls back to sans-serif-medium if custom font asset is not bundled.
     */
    public static synchronized Typeface getWordmarkTypeface(Context context) {
        if (wordmarkTypeface == null) {
            try {
                wordmarkTypeface = Typeface.createFromAsset(context.getAssets(), "fonts/wordmark.ttf");
            } catch (Exception ignored) {
                wordmarkTypeface = Typeface.create("sans-serif-medium", Typeface.NORMAL);
            }
        }
        return wordmarkTypeface;
    }
}
