package com.insta.nova.data.firebase;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/** Task P7-05 — the interpretation rules the notification dispatcher will rely on. */
public class NotificationPrefsTest {

    private static Map<String, Object> prefs(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    public void missingSettingsMeansEnabled() {
        assertTrue(NotificationPrefs.allows(null, NotificationPrefs.LIKES));
        assertTrue("empty map must not mute anyone", NotificationPrefs.allows(prefs(), NotificationPrefs.LIKES));
    }

    @Test
    public void explicitFalseMutes() {
        assertFalse(NotificationPrefs.allows(prefs(NotificationPrefs.LIKES, false), NotificationPrefs.LIKES));
        assertTrue("other types unaffected",
                NotificationPrefs.allows(prefs(NotificationPrefs.LIKES, false), NotificationPrefs.COMMENTS));
    }

    @Test
    public void acceptsStringAndNumericForms() {
        assertFalse(NotificationPrefs.allows(prefs(NotificationPrefs.MESSAGES, "false"), NotificationPrefs.MESSAGES));
        assertTrue(NotificationPrefs.allows(prefs(NotificationPrefs.MESSAGES, "true"), NotificationPrefs.MESSAGES));
        assertFalse(NotificationPrefs.allows(prefs(NotificationPrefs.CALLS, 0), NotificationPrefs.CALLS));
        assertTrue(NotificationPrefs.allows(prefs(NotificationPrefs.CALLS, 1), NotificationPrefs.CALLS));
    }

    @Test
    public void garbageFailsOpen() {
        // A client could write anything into its own document; none of it may silently mute.
        assertTrue(NotificationPrefs.allows(prefs(NotificationPrefs.FOLLOWS, "maybe"), NotificationPrefs.FOLLOWS));
        assertTrue(NotificationPrefs.allows(prefs(NotificationPrefs.FOLLOWS, new Object()), NotificationPrefs.FOLLOWS));
        assertTrue(NotificationPrefs.allows(prefs(NotificationPrefs.FOLLOWS, ""), NotificationPrefs.FOLLOWS));
    }

    @Test
    public void noQuietWindowWhenEndpointsMissingOrEqual() {
        assertFalse(NotificationPrefs.inQuietHours(prefs(), 3));
        assertFalse("start only", NotificationPrefs.inQuietHours(prefs(NotificationPrefs.QUIET_START, 22), 3));
        assertFalse("start == end is a zero-width window",
                NotificationPrefs.inQuietHours(prefs(NotificationPrefs.QUIET_START, 8, NotificationPrefs.QUIET_END, 8), 8));
    }

    @Test
    public void quietHoursInsideSameDay() {
        Map<String, Object> s = prefs(NotificationPrefs.QUIET_START, 13, NotificationPrefs.QUIET_END, 15);
        assertFalse("before window", NotificationPrefs.inQuietHours(s, 12));
        assertTrue("start is inclusive", NotificationPrefs.inQuietHours(s, 13));
        assertTrue(NotificationPrefs.inQuietHours(s, 14));
        assertFalse("end is exclusive", NotificationPrefs.inQuietHours(s, 15));
    }

    @Test
    public void quietHoursAcrossMidnight() {
        Map<String, Object> s = prefs(NotificationPrefs.QUIET_START, 22, NotificationPrefs.QUIET_END, 7);
        assertTrue("22:00", NotificationPrefs.inQuietHours(s, 22));
        assertTrue("23:59", NotificationPrefs.inQuietHours(s, 23));
        assertTrue("00:00", NotificationPrefs.inQuietHours(s, 0));
        assertTrue("06:59", NotificationPrefs.inQuietHours(s, 6));
        assertFalse("07:00 is outside", NotificationPrefs.inQuietHours(s, 7));
        assertFalse("noon is outside", NotificationPrefs.inQuietHours(s, 12));
    }

    @Test
    public void quietHoursIgnoreOutOfRangeAndJunk() {
        assertFalse(NotificationPrefs.inQuietHours(prefs(NotificationPrefs.QUIET_START, 22,
                NotificationPrefs.QUIET_END, 7), 24));
        assertFalse(NotificationPrefs.inQuietHours(prefs(NotificationPrefs.QUIET_START, 99,
                NotificationPrefs.QUIET_END, 7), 3));
        assertFalse(NotificationPrefs.inQuietHours(prefs(NotificationPrefs.QUIET_START, "late",
                NotificationPrefs.QUIET_END, 7), 3));
    }

    @Test
    public void callsBypassQuietHoursButNotTheirOwnSwitch() {
        Map<String, Object> night = prefs(NotificationPrefs.QUIET_START, 22, NotificationPrefs.QUIET_END, 7);
        assertTrue("a ringing call must still ring at 03:00",
                NotificationPrefs.deliverNow(night, NotificationPrefs.CALLS, 3));
        assertFalse("but not when the user muted calls",
                NotificationPrefs.deliverNow(prefs(NotificationPrefs.CALLS, false,
                        NotificationPrefs.QUIET_START, 22, NotificationPrefs.QUIET_END, 7),
                        NotificationPrefs.CALLS, 3));
        assertFalse("a like at 03:00 waits",
                NotificationPrefs.deliverNow(night, NotificationPrefs.LIKES, 3));
        assertTrue("the same like at noon goes out",
                NotificationPrefs.deliverNow(night, NotificationPrefs.LIKES, 12));
    }
}
