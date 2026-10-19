package com.insta.nova.ui.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The splash gate is the only place that decides whether an activity needing a
 * profiles.id may be started. These cases pin the four distinct outcomes so a future
 * refactor cannot collapse "no profile" into "retry" (which would strand the user in a
 * spinner) or "network error" into "no profile" (which would tell a valid user that
 * their account does not exist).
 */
public class SessionIdentityPolicyTest {

    @Test
    public void foundRoutesHomeRegardlessOfCache() {
        assertEquals(SessionIdentityPolicy.Route.HOME,
                SessionIdentityPolicy.decide(SessionIdentityPolicy.Lookup.FOUND, false));
        assertEquals(SessionIdentityPolicy.Route.HOME,
                SessionIdentityPolicy.decide(SessionIdentityPolicy.Lookup.FOUND, true));
    }

    @Test
    public void missingProfileRoutesToRecoverableScreen() {
        assertEquals(SessionIdentityPolicy.Route.MISSING_PROFILE,
                SessionIdentityPolicy.decide(SessionIdentityPolicy.Lookup.MISSING, false));
        // Even with a cache, a confirmed MISSING must not fall back to a stale UUID.
        assertEquals(SessionIdentityPolicy.Route.MISSING_PROFILE,
                SessionIdentityPolicy.decide(SessionIdentityPolicy.Lookup.MISSING, true));
    }

    @Test
    public void networkErrorWithoutCacheAsksForRetry() {
        assertEquals(SessionIdentityPolicy.Route.RETRY,
                SessionIdentityPolicy.decide(SessionIdentityPolicy.Lookup.ERROR, false));
    }

    @Test
    public void networkErrorWithVerifiedCacheKeepsUserSignedIn() {
        assertEquals(SessionIdentityPolicy.Route.HOME_WITH_CACHED_ID,
                SessionIdentityPolicy.decide(SessionIdentityPolicy.Lookup.ERROR, true));
    }

    @Test
    public void unknownLookupResolvesOnlyWhenNothingIsCached() {
        assertEquals(SessionIdentityPolicy.Route.RESOLVE,
                SessionIdentityPolicy.decide(SessionIdentityPolicy.Lookup.UNKNOWN, false));
        assertEquals(SessionIdentityPolicy.Route.HOME_WITH_CACHED_ID,
                SessionIdentityPolicy.decide(SessionIdentityPolicy.Lookup.UNKNOWN, true));
    }

    @Test
    public void nullLookupIsTreatedAsUnknown() {
        assertEquals(SessionIdentityPolicy.Route.RESOLVE,
                SessionIdentityPolicy.decide(null, false));
    }

    @Test
    public void cacheIsOnlyTrustedForTheSameFirebaseUser() {
        assertTrue(SessionIdentityPolicy.cacheMatches("uid-a", "3c96a7cc-5c8b-4a79-9138-8c6c9491aed7", "uid-a"));
        // Account switch: the cached UUID belongs to somebody else.
        assertFalse(SessionIdentityPolicy.cacheMatches("uid-a", "3c96a7cc-5c8b-4a79-9138-8c6c9491aed7", "uid-b"));
    }

    @Test
    public void emptyCacheOrUidNeverMatches() {
        assertFalse(SessionIdentityPolicy.cacheMatches("uid-a", "", "uid-a"));
        assertFalse(SessionIdentityPolicy.cacheMatches("uid-a", null, "uid-a"));
        assertFalse(SessionIdentityPolicy.cacheMatches(null, "3c96a7cc", "uid-a"));
        assertFalse(SessionIdentityPolicy.cacheMatches("uid-a", "3c96a7cc", ""));
        assertFalse(SessionIdentityPolicy.cacheMatches("uid-a", "3c96a7cc", null));
    }
}
