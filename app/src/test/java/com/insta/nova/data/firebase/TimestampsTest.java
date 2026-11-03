package com.insta.nova.data.firebase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Story lifetime + null-safety contract (task P2-09). */
public class TimestampsTest {

    @Test
    public void storyExpiryIs24HoursAfterCreation() {
        long created = 1_700_000_000_000L;
        assertEquals(created + 86_400_000L, Timestamps.storyExpiry(created));
    }

    @Test
    public void missingCreationTimeFallsBackToNow() {
        long before = System.currentTimeMillis();
        long expiry = Timestamps.storyExpiry(null);
        assertTrue(expiry >= before + Timestamps.STORY_TTL_MILLIS);
    }

    @Test
    public void expiryBoundaryIsInclusive() {
        long created = 1_700_000_000_000L;
        long expiry = Timestamps.storyExpiry(created);
        assertFalse(Timestamps.isExpired(created, expiry - 1));
        assertTrue("exactly at TTL counts as expired", Timestamps.isExpired(created, expiry));
    }

    @Test
    public void nullTimestampIsTreatedAsExpired() {
        assertTrue(Timestamps.isExpired(null, System.currentTimeMillis()));
    }
}
