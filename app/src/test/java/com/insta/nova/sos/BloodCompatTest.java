package com.insta.nova.sos;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** All 64 donor→recipient pairs, the 90-day window, and badge levels. */
public class BloodCompatTest {

    private static final Map<String, Set<String>> TABLE = new HashMap<>();

    static {
        TABLE.put("A+", set("A+", "AB+"));
        TABLE.put("A-", set("A+", "A-", "AB+", "AB-"));
        TABLE.put("B+", set("B+", "AB+"));
        TABLE.put("B-", set("B+", "B-", "AB+", "AB-"));
        TABLE.put("AB+", set("AB+"));
        TABLE.put("AB-", set("AB+", "AB-"));
        TABLE.put("O+", set("A+", "B+", "AB+", "O+"));
        TABLE.put("O-", set("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"));
    }

    private static Set<String> set(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }

    @Test
    public void allSixtyFourPairsMatchTheRuleTable() {
        int pairs = 0;
        for (String donor : BloodCompat.GROUPS) {
            for (String recipient : BloodCompat.GROUPS) {
                boolean expected = TABLE.get(donor).contains(recipient);
                assertEquals(donor + " -> " + recipient,
                        expected, BloodCompat.canDonate(donor, recipient));
                pairs++;
            }
        }
        assertEquals(64, pairs);
    }

    @Test
    public void universalDonorIsONegative() {
        for (String recipient : BloodCompat.GROUPS) {
            assertTrue(BloodCompat.canDonate("O-", recipient));
        }
    }

    @Test
    public void universalRecipientIsABPositive() {
        for (String donor : BloodCompat.GROUPS) {
            assertTrue(BloodCompat.canDonate(donor, "AB+"));
        }
    }

    @Test
    public void oPositiveCannotGiveToANegative() {
        assertFalse(BloodCompat.canDonate("O+", "A-"));
    }

    @Test
    public void compatibleDonorsForOPositive() {
        String[] donors = BloodCompat.compatibleDonorsFor("O+");
        assertEquals(2, donors.length);
        assertTrue(Arrays.asList(donors).contains("O-"));
        assertTrue(Arrays.asList(donors).contains("O+"));
    }

    @Test
    public void compatibleDonorsForABNegative() {
        assertEquals(4, BloodCompat.compatibleDonorsFor("AB-").length);
    }

    @Test
    public void unknownGroupNeverMatches() {
        assertFalse(BloodCompat.canDonate("X+", "A+"));
        assertFalse(BloodCompat.canDonate("A+", "X+"));
        assertFalse(BloodCompat.canDonate(null, "A+"));
    }

    /* ----------------------------- eligibility ------------------------------ */

    private static Date daysAgo(int days) {
        return new Date(System.currentTimeMillis() - days * 86400_000L);
    }

    @Test
    public void noDonationIsEligible() {
        assertTrue(BloodCompat.isEligible(null, new Date()));
    }

    @Test
    public void eightyNineDaysIsNotEligible() {
        Date last = daysAgo(89);
        String iso = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(last);
        assertFalse(BloodCompat.isEligible(iso, new Date()));
    }

    @Test
    public void ninetyDaysIsEligible() {
        Date last = daysAgo(90);
        String iso = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(last);
        assertTrue(BloodCompat.isEligible(iso, new Date()));
    }

    @Test
    public void nextEligibleIsExactlyNinetyDays() {
        Date last = daysAgo(30);
        Date next = BloodCompat.nextEligibleDate(last);
        long deltaDays = Math.round((next.getTime() - last.getTime()) / 86400_000.0);
        assertEquals(90, deltaDays);
    }

    @Test
    public void daysUntilEligiblePositive() {
        Date last = daysAgo(30);
        String iso = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(last);
        int days = BloodCompat.daysUntilEligible(iso, new Date());
        assertTrue(days >= 59 && days <= 61);
    }

    @Test
    public void daysUntilEligibleZeroWhenPast() {
        Date last = daysAgo(200);
        String iso = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(last);
        assertEquals(0, BloodCompat.daysUntilEligible(iso, new Date()));
    }

    @Test
    public void malformedDateTreatedAsEligible() {
        assertTrue(BloodCompat.isEligible("not-a-date", new Date()));
    }

    @Test
    public void isoWithTimeIsParsed() {
        assertTrue(BloodCompat.parseDate("2026-01-02T10:20:30+06:00") != null);
    }

    /* -------------------------------- levels -------------------------------- */

    @Test public void levelZero() { assertEquals("", BloodCompat.levelName(0)); }
    @Test public void levelBronze() { assertEquals("Bronze", BloodCompat.levelName(1)); }
    @Test public void levelBronzeUpper() { assertEquals("Bronze", BloodCompat.levelName(2)); }
    @Test public void levelSilver() { assertEquals("Silver", BloodCompat.levelName(3)); }
    @Test public void levelSilverUpper() { assertEquals("Silver", BloodCompat.levelName(4)); }
    @Test public void levelGold() { assertEquals("Gold", BloodCompat.levelName(5)); }
    @Test public void levelGoldUpper() { assertEquals("Gold", BloodCompat.levelName(9)); }
    @Test public void levelPlatinum() { assertEquals("Platinum", BloodCompat.levelName(10)); }
    @Test public void levelPlatinumHigher() { assertEquals("Platinum", BloodCompat.levelName(42)); }
}
