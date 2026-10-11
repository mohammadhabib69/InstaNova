package com.insta.clone.util;

import java.util.concurrent.TimeUnit;

/**
 * Utility for formatting post timestamps into concise Instagram-style relative labels.
 */
public final class TimeUtils {

    private TimeUtils() {}

    public static String formatRelativeTime(long timestamp) {
        long now = System.currentTimeMillis();
        // If timestamp is in seconds, convert to milliseconds
        if (timestamp < 10_000_000_000L) {
            timestamp *= 1000L;
        }

        long diff = Math.max(0, now - timestamp);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(diff);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(diff);
        long hours = TimeUnit.MILLISECONDS.toHours(diff);
        long days = TimeUnit.MILLISECONDS.toDays(diff);
        long weeks = days / 7;

        if (seconds < 60) {
            return "JUST NOW";
        } else if (minutes < 60) {
            return minutes + "M AGO";
        } else if (hours < 24) {
            return hours + "H AGO";
        } else if (days < 7) {
            return days + (days == 1 ? " DAY AGO" : " DAYS AGO");
        } else {
            return weeks + (weeks == 1 ? " WEEK AGO" : " WEEKS AGO");
        }
    }
}
