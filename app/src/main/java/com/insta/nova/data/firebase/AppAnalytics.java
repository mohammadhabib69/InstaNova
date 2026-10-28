package com.insta.nova.data.firebase;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.analytics.FirebaseAnalytics;

/**
 * Firebase Analytics facade (task P0-09).
 *
 * <p>Only the events that already exist as product behaviour are logged — sign-in,
 * sign-up completion, profile creation, and the currently active screen from the existing
 * navigation. No new tracking dimension is invented, and no personal data (username,
 * email, message text) is put in parameters: Firebase policy + Play data-safety both
 * forbid it here.
 *
 * <p>Every call is failure-tolerant: analytics must never break a user flow.
 */
public final class AppAnalytics {

    public static final String EVENT_SIGN_IN = "login";
    public static final String EVENT_SIGN_UP = "sign_up";
    public static final String EVENT_PROFILE_COMPLETE = "profile_complete";

    private static FirebaseAnalytics analytics;

    private AppAnalytics() {
    }

    private static FirebaseAnalytics get(@Nullable Context context) {
        if (analytics != null) return analytics;
        if (context == null) return null;
        try {
            analytics = FirebaseAnalytics.getInstance(context.getApplicationContext());
        } catch (Throwable t) {
            CrashReporter.record(t, "analytics_init");
        }
        return analytics;
    }

    public static void init(@NonNull Context context) {
        get(context);
    }

    public static void logSignIn(@NonNull String method) {
        logEvent(EVENT_SIGN_IN, "method", method);
    }

    public static void logSignUp(@NonNull String method) {
        logEvent(EVENT_SIGN_UP, "method", method);
    }

    public static void logProfileCompleted() {
        logEvent(EVENT_PROFILE_COMPLETE, null, null);
    }

    public static void setUser(@Nullable String uid) {
        FirebaseAnalytics firebaseAnalytics = get(null);
        if (firebaseAnalytics == null) return;
        try {
            firebaseAnalytics.setUserId(uid == null ? null : uid);
        } catch (Throwable ignored) {
        }
    }

    public static void clearUser() {
        setUser(null);
        CrashReporter.setUser(null);
    }

    private static void logEvent(@NonNull String name, @Nullable String key, @Nullable String value) {
        FirebaseAnalytics firebaseAnalytics = get(null);
        if (firebaseAnalytics == null) return;
        try {
            Bundle bundle = new Bundle();
            if (key != null && value != null) bundle.putString(key, value);
            firebaseAnalytics.logEvent(name, bundle);
        } catch (Throwable t) {
            CrashReporter.record(t, "analytics_event:" + name);
        }
    }
}
