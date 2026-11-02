package com.insta.nova;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

import com.insta.nova.ui.settings.SettingsActivity;

/**
 * Applies the saved appearance choice before any activity resolves its theme.
 *
 * <p>The setting screen stored the value and called {@code setDefaultNightMode} for the running
 * process only. Nothing read it back on launch, so "Light" reverted to the manifest's dark theme
 * on every restart — the choice looked like it had been ignored.
 */
public class NovaApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        AppCompatDelegate.setDefaultNightMode(readThemeMode());
    }

    /** Falls back to dark, which is the theme the app ships with. */
    private int readThemeMode() {
        SharedPreferences sp = getSharedPreferences("insta_settings", Context.MODE_PRIVATE);
        return sp.getInt(SettingsActivity.PREF_THEME, AppCompatDelegate.MODE_NIGHT_YES);
    }
}
