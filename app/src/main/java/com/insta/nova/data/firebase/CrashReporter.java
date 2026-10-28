package com.insta.nova.data.firebase;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.crashlytics.FirebaseCrashlytics;

/**
 * Thin Crashlytics wrapper that can never crash the app.
 *
 * <p>Exists because the pre-migration code swallowed Firebase initialisation failures
 * (audit finding S11). Any failure inside the reporting path itself must stay a log line,
 * not a second crash.
 *
 * <p>Never pass tokens, passwords or message bodies into {@link #record} — parameter
 * values end up in the Crashlytics console.
 */
public final class CrashReporter {

    private static final String TAG = "InstaCrash";

    private CrashReporter() {
    }

    public static void record(@Nullable Throwable error, @NonNull String context) {
        if (error != null) Log.e(TAG, context, error);
        try {
            FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
            crashlytics.setCustomKey("last_context", context);
            if (error != null) crashlytics.recordException(error);
        } catch (Throwable ignored) {
            // Crashlytics not initialised (or disabled): the log line above is the fallback.
        }
    }

    public static void log(@NonNull String message) {
        Log.i(TAG, message);
        try {
            FirebaseCrashlytics.getInstance().log(message);
        } catch (Throwable ignored) {
        }
    }

    /** Ties subsequent non-fatals to a user without exposing the UID as a public field. */
    public static void setUser(@Nullable String uid) {
        try {
            FirebaseCrashlytics.getInstance().setUserId(uid == null ? "" : uid);
        } catch (Throwable ignored) {
        }
    }
}
