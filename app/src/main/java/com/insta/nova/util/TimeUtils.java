package com.insta.nova.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Utility for formatting post timestamps into concise Instagram-style relative labels.
 */
public final class TimeUtils {

    private TimeUtils() {}

    /**
     * Parses the ISO-8601 timestamps returned by Supabase without requiring
     * java.time, which is unavailable on Android API 24 and 25.
     */
    public static long parseTimestamp(String value) {
        if (value == null) {
            return 0L;
        }

        String normalized = value.trim();
        try {
            return Long.parseLong(normalized);
        } catch (NumberFormatException ignored) {
            // Continue with ISO-8601 parsing.
        }

        normalized = normalized.replace(' ', 'T');
        if (normalized.endsWith("Z")) {
            normalized = normalized.substring(0, normalized.length() - 1) + "+0000";
        } else {
            int timezoneStart = Math.max(normalized.lastIndexOf('+'), normalized.lastIndexOf('-'));
            if (timezoneStart > normalized.indexOf('T')) {
                String timezone = normalized.substring(timezoneStart).replace(":", "");
                normalized = normalized.substring(0, timezoneStart) + timezone;
            }
        }

        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
                "yyyy-MM-dd'T'HH:mm:ssZ",
                "yyyy-MM-dd'T'HH:mm:ss.SSS",
                "yyyy-MM-dd'T'HH:mm:ss"
        };
        for (String pattern : patterns) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
                format.setLenient(false);
                Date parsed = format.parse(normalized);
                if (parsed != null) {
                    return parsed.getTime();
                }
            } catch (Exception ignored) {
                // Try the next supported timestamp shape.
            }
        }
        return 0L;
    }

    public static String formatRelativeTime(long timestamp) {
        return TimeFormat.formatFeedTime(timestamp);
    }

    /**
     * Compact age: "1m", "45m", "2h", "3d", "2w".
     * Used by comments sheet, inbox, activity feed, and stories.
     */
    public static String formatCompactRelativeTime(long timestamp) {
        return TimeFormat.formatCompactTime(timestamp);
    }

    /**
     * Formats a parsed timestamp for Supabase/PostgREST keyset pagination.
     * PostgREST expects a timestamp literal here, not epoch milliseconds.
     */
    public static String toSupabaseTimestamp(long timestamp) {
        if (timestamp <= 0L) {
            return "";
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(timestamp));
    }
}
