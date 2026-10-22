package com.insta.nova.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Locale;

public class NumberFormatTest {

    private static final Locale EN = Locale.US;
    private static final Locale BN = new Locale("bn");

    private static final long[] TEST_CASES = {
            0L, 1L, 2L, 11L, 999L, 1000L, 9999L, 10000L, 12500L, 1200000L
    };

    @Test
    public void testEnglishNumberFormatting() {
        assertEquals("0", NumberFormat.format(0L, EN));
        assertEquals("1", NumberFormat.format(1L, EN));
        assertEquals("2", NumberFormat.format(2L, EN));
        assertEquals("11", NumberFormat.format(11L, EN));
        assertEquals("999", NumberFormat.format(999L, EN));
        assertEquals("1,000", NumberFormat.format(1000L, EN));
        assertEquals("9,999", NumberFormat.format(9999L, EN));
        assertEquals("10K", NumberFormat.format(10000L, EN));
        assertEquals("12.5K", NumberFormat.format(12500L, EN));
        assertEquals("1.2M", NumberFormat.format(1200000L, EN));
    }

    @Test
    public void testBanglaNumberFormatting() {
        assertEquals("০", NumberFormat.format(0L, BN));
        assertEquals("১", NumberFormat.format(1L, BN));
        assertEquals("২", NumberFormat.format(2L, BN));
        assertEquals("১১", NumberFormat.format(11L, BN));
        assertEquals("৯৯৯", NumberFormat.format(999L, BN));
        assertEquals("১,০০০", NumberFormat.format(1000L, BN));
        assertEquals("৯,৯৯৯", NumberFormat.format(9999L, BN));
        assertEquals("১০K", NumberFormat.format(10000L, BN));
        assertEquals("১২.৫K", NumberFormat.format(12500L, BN));
        assertEquals("১.২M", NumberFormat.format(1200000L, BN));
    }

    @Test
    public void testIconCountsHidingZero() {
        assertEquals("", NumberFormat.formatIconCount(0L, EN));
        assertEquals("", NumberFormat.formatIconCount(0L, BN));
        assertEquals("1", NumberFormat.formatIconCount(1L, EN));
        assertEquals("১", NumberFormat.formatIconCount(1L, BN));
        assertEquals("12.5K", NumberFormat.formatIconCount(12500L, EN));
        assertEquals("১২.৫K", NumberFormat.formatIconCount(12500L, BN));
    }

    @Test
    public void testProfileStatsRetainingZero() {
        assertEquals("0", NumberFormat.formatProfileStat(0L, EN));
        assertEquals("০", NumberFormat.formatProfileStat(0L, BN));
        assertEquals("1,000", NumberFormat.formatProfileStat(1000L, EN));
        assertEquals("১,০০০", NumberFormat.formatProfileStat(1000L, BN));
    }
}
