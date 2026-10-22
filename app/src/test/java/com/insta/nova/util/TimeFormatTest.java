package com.insta.nova.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;
import java.util.Locale;

public class TimeFormatTest {

    private static final Locale EN = Locale.US;
    private static final Locale BN = new Locale("bn");

    @Test
    public void testFeedTimeEnglish() {
        long now = 1735689600000L; // 2025-01-01 00:00:00 UTC

        // Seconds diff (<60s) -> JUST NOW
        assertEquals("JUST NOW", TimeFormat.formatFeedTime(now - 30_000L, now, EN));

        // 1 minute ago -> 1 MINUTE AGO
        assertEquals("1 MINUTE AGO", TimeFormat.formatFeedTime(now - 60_000L, now, EN));

        // 5 minutes ago -> 5 MINUTES AGO
        assertEquals("5 MINUTES AGO", TimeFormat.formatFeedTime(now - 5 * 60_000L, now, EN));

        // 1 hour ago -> 1 HOUR AGO
        assertEquals("1 HOUR AGO", TimeFormat.formatFeedTime(now - 3600_000L, now, EN));

        // 3 hours ago -> 3 HOURS AGO
        assertEquals("3 HOURS AGO", TimeFormat.formatFeedTime(now - 3 * 3600_000L, now, EN));

        // 1 day ago -> 1 DAY AGO
        assertEquals("1 DAY AGO", TimeFormat.formatFeedTime(now - 86400_000L, now, EN));

        // 3 days ago -> 3 DAYS AGO
        assertEquals("3 DAYS AGO", TimeFormat.formatFeedTime(now - 3 * 86400_000L, now, EN));

        // Older within same year (e.g. 20 days ago in same year)
        Calendar calNow = Calendar.getInstance();
        calNow.set(2026, Calendar.NOVEMBER, 12, 12, 0, 0);
        long fixedNow = calNow.getTimeInMillis();

        Calendar calOlderSameYear = Calendar.getInstance();
        calOlderSameYear.set(2026, Calendar.NOVEMBER, 1, 12, 0, 0);
        assertEquals("NOVEMBER 1", TimeFormat.formatFeedTime(calOlderSameYear.getTimeInMillis(), fixedNow, EN));

        // Different year
        Calendar calDiffYear = Calendar.getInstance();
        calDiffYear.set(2025, Calendar.NOVEMBER, 12, 12, 0, 0);
        assertEquals("NOVEMBER 12, 2025", TimeFormat.formatFeedTime(calDiffYear.getTimeInMillis(), fixedNow, EN));
    }

    @Test
    public void testFeedTimeBangla() {
        long now = 1735689600000L;

        assertEquals("এইমাত্র", TimeFormat.formatFeedTime(now - 20_000L, now, BN));
        assertEquals("১ মিনিট আগে", TimeFormat.formatFeedTime(now - 60_000L, now, BN));
        assertEquals("৫ মিনিট আগে", TimeFormat.formatFeedTime(now - 5 * 60_000L, now, BN));
        assertEquals("১ ঘণ্টা আগে", TimeFormat.formatFeedTime(now - 3600_000L, now, BN));
        assertEquals("৩ ঘণ্টা আগে", TimeFormat.formatFeedTime(now - 3 * 3600_000L, now, BN));
        assertEquals("১ দিন আগে", TimeFormat.formatFeedTime(now - 86400_000L, now, BN));
        assertEquals("৩ দিন আগে", TimeFormat.formatFeedTime(now - 3 * 86400_000L, now, BN));
    }

    @Test
    public void testCompactTimeEnglish() {
        long now = 1735689600000L;

        // 1m, 45m, 2h, 3d, 2w
        assertEquals("1m", TimeFormat.formatCompactTime(now - 60_000L, now, EN));
        assertEquals("45m", TimeFormat.formatCompactTime(now - 45 * 60_000L, now, EN));
        assertEquals("2h", TimeFormat.formatCompactTime(now - 2 * 3600_000L, now, EN));
        assertEquals("3d", TimeFormat.formatCompactTime(now - 3 * 86400_000L, now, EN));
        assertEquals("2w", TimeFormat.formatCompactTime(now - 14 * 86400_000L, now, EN));
    }

    @Test
    public void testCompactTimeBangla() {
        long now = 1735689600000L;

        assertEquals("১m", TimeFormat.formatCompactTime(now - 60_000L, now, BN));
        assertEquals("৪৫m", TimeFormat.formatCompactTime(now - 45 * 60_000L, now, BN));
        assertEquals("২h", TimeFormat.formatCompactTime(now - 2 * 3600_000L, now, BN));
        assertEquals("৩d", TimeFormat.formatCompactTime(now - 3 * 86400_000L, now, BN));
        assertEquals("২w", TimeFormat.formatCompactTime(now - 14 * 86400_000L, now, BN));
    }
}
