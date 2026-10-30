package com.insta.nova.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Pure formatting helpers shared by SOS, Study and Insights. Kept Android-free so the
 * edge cases (overdue, same-day, 90-day eligibility, streak wording) are unit-tested.
 */
public final class NovaFormat {

    private static final TimeZone DHAKA = TimeZone.getTimeZone("Asia/Dhaka");

    private NovaFormat() {
    }

    /** 95 → "1h 35m", 45 → "45m", 0 → "0m". */
    public static String minutes(int totalMinutes) {
        int safe = Math.max(0, totalMinutes);
        int hours = safe / 60;
        int minutes = safe % 60;
        if (hours == 0) return minutes + "m";
        if (minutes == 0) return hours + "h";
        return hours + "h " + minutes + "m";
    }

    public static String seconds(int totalSeconds) {
        int safe = Math.max(0, totalSeconds);
        int minutes = safe / 60;
        int seconds = safe % 60;
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    public static String streak(int days) {
        return days == 1 ? "1 day streak" : days + " day streak";
    }

    public static int percent(long part, long whole) {
        if (whole <= 0) return 0;
        return (int) Math.round(part * 100.0 / whole);
    }

    public static String megabytes(long bytes) {
        double mb = bytes / (1024.0 * 1024.0);
        if (mb >= 100) return String.format(Locale.US, "%.0f MB", mb);
        if (mb >= 10) return String.format(Locale.US, "%.1f MB", mb);
        return String.format(Locale.US, "%.2f MB", mb);
    }

    /** "Needed in 3h 20m" / "Needed today 6:00 PM" / "Overdue". */
    public static String neededBy(String iso, long nowMillis) {
        Date date = parseIso(iso);
        if (date == null) return "";
        long delta = date.getTime() - nowMillis;
        if (delta < 0) return "Overdue";
        Calendar now = Calendar.getInstance(DHAKA);
        now.setTimeInMillis(nowMillis);
        Calendar target = Calendar.getInstance(DHAKA);
        target.setTime(date);
        boolean sameDay = now.get(Calendar.YEAR) == target.get(Calendar.YEAR)
                && now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR);
        if (sameDay) {
            return "Needed today " + time12(date);
        }
        if (delta < 24L * 3600_000L) {
            long totalMinutes = delta / 60000L;
            long hours = totalMinutes / 60;
            long minutes = totalMinutes % 60;
            return "Needed in " + (hours > 0 ? hours + "h " : "") + minutes + "m";
        }
        SimpleDateFormat format = new SimpleDateFormat("d MMM, h:mm a", Locale.US);
        format.setTimeZone(DHAKA);
        return "Needed " + format.format(date);
    }

    public static String time12(Date date) {
        SimpleDateFormat format = new SimpleDateFormat("h:mm a", Locale.US);
        format.setTimeZone(DHAKA);
        return format.format(date);
    }

    public static String dayLabel(String iso) {
        Date date = parseIso(iso);
        if (date == null) return "";
        SimpleDateFormat format = new SimpleDateFormat("d MMM", Locale.US);
        format.setTimeZone(DHAKA);
        return format.format(date);
    }

    public static String timeAgo(String iso, long nowMillis) {
        Date date = parseIso(iso);
        if (date == null) return "";
        long delta = Math.max(0, nowMillis - date.getTime());
        long minutes = delta / 60000L;
        if (minutes < 1) return "now";
        if (minutes < 60) return minutes + "m";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h";
        long days = hours / 24;
        if (days < 7) return days + "d";
        return dayLabel(iso);
    }

    public static String isoNow() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
        format.setTimeZone(DHAKA);
        return format.format(new Date());
    }

    public static String isoDate(Date date) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setTimeZone(DHAKA);
        return format.format(date);
    }

    public static Date parseIso(String iso) {
        if (iso == null || iso.isEmpty()) return null;
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd"
        };
        for (String pattern : patterns) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
                format.setTimeZone(DHAKA);
                return format.parse(iso);
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
