package com.insta.nova.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Unified relative time formatter for InstaNova.
 * Feed items: 'JUST NOW', '1 MINUTE AGO', '5 MINUTES AGO', '1 HOUR AGO', '3 HOURS AGO',
 *             '1 DAY AGO', '3 DAYS AGO', then 'NOVEMBER 12' (add ', 2025' when the year differs).
 * Compact lists: '1m', '45m', '2h', '3d', '2w'.
 * Respects app language (English and Bangla locale with Bangla digits).
 */
public final class TimeFormat {

    private static final String[] BANGLA_MONTHS = {
            "জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন",
            "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর"
    };

    private TimeFormat() {}

    private static boolean isBangla(Locale locale) {
        if (locale == null) locale = Locale.getDefault();
        return "bn".equalsIgnoreCase(locale.getLanguage());
    }

    private static String digits(long n, Locale locale) {
        String s = String.valueOf(n);
        if (isBangla(locale)) {
            return NumberFormat.toBanglaDigits(s);
        }
        return s;
    }

    /**
     * Formats feed post relative timestamps in uppercase style.
     */
    public static String formatFeedTime(long timestamp) {
        return formatFeedTime(timestamp, System.currentTimeMillis(), Locale.getDefault());
    }

    public static String formatFeedTime(long timestamp, long now, Locale locale) {
        if (timestamp <= 0L) {
            return "";
        }
        if (timestamp < 10_000_000_000L) {
            timestamp *= 1000L;
        }

        long diff = Math.max(0L, now - timestamp);
        long seconds = diff / 1000L;
        long minutes = seconds / 60L;
        long hours = minutes / 60L;
        long days = hours / 24L;

        boolean bn = isBangla(locale);

        if (seconds < 60L) {
            return bn ? "এইমাত্র" : "JUST NOW";
        } else if (minutes < 60L) {
            if (minutes == 1L) {
                return bn ? "১ মিনিট আগে" : "1 MINUTE AGO";
            }
            return bn ? digits(minutes, locale) + " মিনিট আগে" : minutes + " MINUTES AGO";
        } else if (hours < 24L) {
            if (hours == 1L) {
                return bn ? "১ ঘণ্টা আগে" : "1 HOUR AGO";
            }
            return bn ? digits(hours, locale) + " ঘণ্টা আগে" : hours + " HOURS AGO";
        } else if (days < 7L) {
            if (days == 1L) {
                return bn ? "১ দিন আগে" : "1 DAY AGO";
            }
            return bn ? digits(days, locale) + " দিন আগে" : days + " DAYS AGO";
        } else {
            Calendar calEvent = Calendar.getInstance();
            calEvent.setTimeInMillis(timestamp);

            Calendar calNow = Calendar.getInstance();
            calNow.setTimeInMillis(now);

            boolean sameYear = calEvent.get(Calendar.YEAR) == calNow.get(Calendar.YEAR);

            if (bn) {
                int month = calEvent.get(Calendar.MONTH);
                int day = calEvent.get(Calendar.DAY_OF_MONTH);
                int year = calEvent.get(Calendar.YEAR);
                String monthName = (month >= 0 && month < 12) ? BANGLA_MONTHS[month] : "";
                if (sameYear) {
                    return digits(day, locale) + " " + monthName;
                } else {
                    return digits(day, locale) + " " + monthName + ", " + digits(year, locale);
                }
            } else {
                SimpleDateFormat sdf = sameYear
                        ? new SimpleDateFormat("MMMM d", Locale.US)
                        : new SimpleDateFormat("MMMM d, yyyy", Locale.US);
                return sdf.format(new Date(timestamp)).toUpperCase(Locale.US);
            }
        }
    }

    /**
     * Formats compact relative time: '1m', '45m', '2h', '3d', '2w'.
     */
    public static String formatCompactTime(long timestamp) {
        return formatCompactTime(timestamp, System.currentTimeMillis(), Locale.getDefault());
    }

    public static String formatCompactTime(long timestamp, long now, Locale locale) {
        if (timestamp <= 0L) {
            return "";
        }
        if (timestamp < 10_000_000_000L) {
            timestamp *= 1000L;
        }

        long diff = Math.max(0L, now - timestamp);
        long seconds = diff / 1000L;
        long minutes = Math.max(1L, seconds / 60L);
        long hours = minutes / 60L;
        long days = hours / 24L;
        long weeks = days / 7L;

        if (minutes < 60L) {
            return digits(minutes, locale) + "m";
        } else if (hours < 24L) {
            return digits(hours, locale) + "h";
        } else if (days < 7L) {
            return digits(days, locale) + "d";
        } else {
            return digits(weeks, locale) + "w";
        }
    }
}
