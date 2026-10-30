package com.insta.nova.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * One place that decides what the user is told when a request fails.
 *
 * <p>The repositories used to post {@code Throwable.getMessage()} straight into
 * {@code Resource.error}, so an offline device showed the raw OkHttp sentence
 * <em>"Unable to resolve host \"poqdemxiuoocbzkwbyhy.supabase.co\": No address
 * associated with hostname"</em> in the feed banner. That is a developer message
 * leaking into the UI: it names the backend, tells the user nothing actionable,
 * and reads like a crash. Everything transport-shaped now collapses to one
 * sentence; anything the server actually said is passed through untouched.
 */
public final class NetworkErrors {

    /** Shown for anything that is really "the network, not the app". */
    public static final String OFFLINE = "You're offline. Check your connection and try again.";

    private NetworkErrors() {
    }

    /** True when this failure is a transport/DNS problem rather than a server answer. */
    public static boolean isTransport(@Nullable Throwable t) {
        return t instanceof UnknownHostException
                || t instanceof SocketTimeoutException
                || t instanceof ConnectException
                || (t instanceof java.io.IOException && t.getCause() instanceof UnknownHostException);
    }

    /**
     * True when a *message string* is a leaked transport error. Needed because some
     * call sites only kept the message, not the throwable, before this helper existed
     * and still post whatever they were handed.
     */
    public static boolean looksLikeTransport(@Nullable String message) {
        if (message == null) return false;
        String m = message.toLowerCase(Locale.US);
        return m.contains("unable to resolve host")
                || m.contains("no address associated with hostname")
                || m.contains("failed to connect to")
                || m.contains("timeout")
                || m.contains("connection reset")
                || m.contains("network is unreachable")
                || m.contains("no route to host");
    }

    /** A message safe to show in the UI for this throwable. */
    @NonNull
    public static String friendly(@Nullable Throwable t, @NonNull String fallback) {
        if (t == null) return fallback;
        if (isTransport(t) || looksLikeTransport(t.getMessage())) return OFFLINE;
        String message = t.getMessage();
        if (message == null || message.trim().isEmpty()) return fallback;
        return message.trim();
    }

    /** A message safe to show in the UI when only a string survived. */
    @NonNull
    public static String sanitize(@Nullable String message, @NonNull String fallback) {
        if (message == null || message.trim().isEmpty()) return fallback;
        if (looksLikeTransport(message)) return OFFLINE;
        return message.trim();
    }
}
