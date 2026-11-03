package com.insta.nova.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/** Number, streak, time and byte formatting used across Nova screens. */
public class NovaFormatTest {

    @Test public void minutesZero() { assertEquals("0m", NovaFormat.minutes(0)); }
    @Test public void minutesPlain() { assertEquals("45m", NovaFormat.minutes(45)); }
    @Test public void minutesExactHour() { assertEquals("1h", NovaFormat.minutes(60)); }
    @Test public void minutesMixed() { assertEquals("1h 35m", NovaFormat.minutes(95)); }
    @Test public void minutesNegative() { assertEquals("0m", NovaFormat.minutes(-5)); }

    @Test public void secondsZero() { assertEquals("00:00", NovaFormat.seconds(0)); }
    @Test public void secondsMinute() { assertEquals("01:00", NovaFormat.seconds(60)); }
    @Test public void secondsMixed() { assertEquals("25:09", NovaFormat.seconds(1509)); }

    @Test public void streakSingular() { assertEquals("1 day streak", NovaFormat.streak(1)); }
    @Test public void streakPlural() { assertEquals("5 day streak", NovaFormat.streak(5)); }
    @Test public void streakZero() { assertEquals("0 day streak", NovaFormat.streak(0)); }

    @Test public void percentZeroWhole() { assertEquals(0, NovaFormat.percent(10, 0)); }
    @Test public void percentHalf() { assertEquals(50, NovaFormat.percent(50, 100)); }
    @Test public void percentRounds() { assertEquals(67, NovaFormat.percent(2, 3)); }
    @Test public void percentFull() { assertEquals(100, NovaFormat.percent(9, 9)); }

    @Test public void megabytesSmall() { assertEquals("0.50 MB", NovaFormat.megabytes(524288)); }
    @Test public void megabytesMedium() { assertEquals("12.0 MB", NovaFormat.megabytes(12582912)); }
    @Test public void megabytesLarge() { assertEquals("200 MB", NovaFormat.megabytes(209715200)); }

    @Test
    public void neededByOverdue() {
        long now = 1_700_000_000_000L;
        assertEquals("Overdue", NovaFormat.neededBy("2023-11-14T00:00:00+06:00", now));
    }

    @Test
    public void neededBySameDay() {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Dhaka"));
        calendar.set(2026, Calendar.OCTOBER, 22, 18, 0, 0);
        String iso = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)
                .format(calendar.getTime());
        Calendar now = (Calendar) calendar.clone();
        now.add(Calendar.HOUR_OF_DAY, -2);
        assertTrue(NovaFormat.neededBy(iso, now.getTimeInMillis()).startsWith("Needed today"));
    }

    @Test
    public void neededByHours() {
        // Fixed clock: "+8h from now" landed on the same day before 16:00 and flipped the
        // branch, so this test failed in the morning and passed in the evening.
        long now = NovaFormat.parseIso("2026-10-22T20:00:00+06:00").getTime();
        assertEquals("Needed in 14h 0m",
                NovaFormat.neededBy("2026-10-23T10:00:00+06:00", now));
    }

    @Test
    public void timeAgoNow() {
        long now = System.currentTimeMillis();
        String iso = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)
                .format(new Date(now));
        assertEquals("now", NovaFormat.timeAgo(iso, now));
    }

    @Test
    public void dayLabelInvalidIsEmpty() {
        assertEquals("", NovaFormat.dayLabel("garbage"));
    }

    @Test
    public void parseIsoDate() {
        assertTrue(NovaFormat.parseIso("2026-10-22") != null);
    }

    @Test
    public void parseIsoInvalid() {
        assertEquals(null, NovaFormat.parseIso("22/10/2026"));
    }
}
