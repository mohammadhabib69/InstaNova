package com.insta.nova.util;

import java.util.Locale;

/**
 * Helper for formatting numerical counts across InstaNova.
 * - Below 10,000: full number with comma separators (e.g. 1,234).
 * - From 10,000: e.g. 10K, 12.3K, 12.5K.
 * - From 1,000,000: e.g. 1.2M.
 * - Localized digits in Bangla (bn locale).
 * - Zero counts hidden next to icons, shown in profile stats.
 */
public final class NumberFormat {

    private NumberFormat() {}

    public static boolean isBangla(Locale locale) {
        if (locale == null) {
            locale = Locale.getDefault();
        }
        return "bn".equalsIgnoreCase(locale.getLanguage());
    }

    public static String toBanglaDigits(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c >= '0' && c <= '9') {
                sb.append((char) ('\u09E6' + (c - '0')));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Formats count according to current default locale rules.
     */
    public static String format(long count) {
        return format(count, Locale.getDefault());
    }

    /**
     * Formats count according to specified locale.
     */
    public static String format(long count, Locale locale) {
        if (count < 0) {
            return "-" + format(-count, locale);
        }

        String raw;
        if (count < 10_000) {
            raw = String.format(Locale.US, "%,d", count);
        } else if (count < 1_000_000) {
            raw = formatWithSuffix(count / 1000.0) + "K";
        } else {
            raw = formatWithSuffix(count / 1_000_000.0) + "M";
        }

        if (isBangla(locale)) {
            return toBanglaDigits(raw);
        }
        return raw;
    }

    private static String formatWithSuffix(double value) {
        String s = String.format(Locale.US, "%.1f", value);
        if (s.endsWith(".0")) {
            return s.substring(0, s.length() - 2);
        }
        return s;
    }

    /**
     * For profile stat header counters (0 is shown as "0" / "০").
     */
    public static String formatProfileStat(long count) {
        return format(count, Locale.getDefault());
    }

    public static String formatProfileStat(long count, Locale locale) {
        return format(count, locale);
    }

    /**
     * For action counts next to icons (likes, comments, reposts).
     * 0 counts return empty string to hide them next to icons.
     */
    public static String formatIconCount(long count) {
        return formatIconCount(count, Locale.getDefault());
    }

    public static String formatIconCount(long count, Locale locale) {
        if (count <= 0) {
            return "";
        }
        return format(count, locale);
    }
}
