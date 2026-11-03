package com.insta.nova.calling;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;

import org.junit.Test;

/**
 * Guards the two mappings that decide what the call history screen can show.
 *
 * <p>Both failures were silent: nothing threw, nothing logged, the screen simply
 * rendered "Unknown", a wrong direction and the refresh time on every row.
 */
public class CallRecordMappingTest {

    /** Verbatim shape emitted by PostgREST for a timestamptz column. */
    private static final String ROW = "{"
            + "\"id\":\"0ec052d8-cd85-4cee-853d-cf05ad3d48af\","
            + "\"caller_id\":\"d9bf8f55-abca-4885-b44d-9501667949de\","
            + "\"callee_id\":\"3c96a7cc-5c8b-4a79-9138-8c6c9491aed7\","
            + "\"call_type\":\"audio\","
            + "\"status\":\"ended\","
            + "\"created_at\":\"2026-11-01T10:49:58.15081+00:00\","
            + "\"connected_at\":\"2026-11-01T10:50:03.2+00:00\","
            + "\"ended_at\":\"2026-11-01T10:50:05.33+00:00\","
            + "\"duration_sec\":2"
            + "}";

    @Test
    public void postgrestRowPopulatesEveryColumnTheHistoryNeeds() {
        CallRecord r = new Gson().fromJson(ROW, CallRecord.class);

        assertEquals("0ec052d8-cd85-4cee-853d-cf05ad3d48af", r.id);
        // Null here meant no peer id, hence no name and no avatar on the row.
        assertEquals("d9bf8f55-abca-4885-b44d-9501667949de", r.callerId);
        assertEquals("3c96a7cc-5c8b-4a79-9138-8c6c9491aed7", r.calleeId);
        assertEquals("audio", r.callType);
        // Null here meant the row was stamped with the refresh time instead.
        assertNotNull(r.createdAt);
        assertEquals(2, r.durationSec);
        assertFalse(r.isVideo());
    }

    @Test
    public void microsecondPrecisionParsesToTheRightInstant() {
        CallRecord r = new CallRecord();
        r.createdAt = "2026-11-01T10:49:58.15081+00:00";
        // 2026-11-01T10:49:58.150Z
        assertEquals(1793530198150L, r.createdAtMillis());
    }

    @Test
    public void shortFractionsAreLeftPaddedNotRightPadded() {
        CallRecord r = new CallRecord();
        r.createdAt = "2026-11-01T10:50:03.2+00:00";
        assertEquals(1793530203200L, r.createdAtMillis());
    }

    @Test
    public void variedFractionWidthsLandInTheSameSecond() {
        long a = at("2026-11-01T10:50:05.3+00:00");
        long b = at("2026-11-01T10:50:05.33+00:00");
        long c = at("2026-11-01T10:50:05.330+00:00");
        long d = at("2026-11-01T10:50:05.330999+00:00");
        assertTrue(a <= b);
        assertEquals(b, c);
        assertEquals(c, d);
        assertEquals(1793530205000L, a / 1000 * 1000);
    }

    @Test
    public void zuluSuffixIsAccepted() {
        assertEquals(at("2026-11-01T10:50:05.330+00:00"), at("2026-11-01T10:50:05.330Z"));
    }

    private static long at(String iso) {
        CallRecord r = new CallRecord();
        r.createdAt = iso;
        long v = r.createdAtMillis();
        assertTrue("failed to parse " + iso, v > 0);
        return v;
    }
}
