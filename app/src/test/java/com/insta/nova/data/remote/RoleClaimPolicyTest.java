package com.insta.nova.data.remote;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * The claim gate must be bounded: one refresh, one provisioning call, then report.
 * A regression here means either an infinite retry loop or a write that is sent with
 * a token Supabase will reject as anon.
 */
public class RoleClaimPolicyTest {

    @Test
    public void tokenThatAlreadyHasTheClaimGoesStraightThrough() {
        assertEquals(RoleClaimPolicy.Step.PROCEED,
                RoleClaimPolicy.decide(true, false, true, false));
        // Even with a provisioner configured, a good token must not trigger any call.
        assertEquals(RoleClaimPolicy.Step.PROCEED,
                RoleClaimPolicy.decide(true, true, false, true));
    }

    @Test
    public void missingClaimRefreshesOnceBeforeGivingUp() {
        assertEquals(RoleClaimPolicy.Step.FORCE_REFRESH,
                RoleClaimPolicy.decide(false, false, false, false));
        assertEquals(RoleClaimPolicy.Step.FORCE_REFRESH,
                RoleClaimPolicy.decide(false, false, true, false));
    }

    @Test
    public void stillMissingAfterRefreshUsesTheProvisionerExactlyOnce() {
        assertEquals(RoleClaimPolicy.Step.PROVISION,
                RoleClaimPolicy.decide(false, true, true, false));
    }

    @Test
    public void stillMissingWithNoProvisionerIsReportedNotRetried() {
        assertEquals(RoleClaimPolicy.Step.BLOCKED,
                RoleClaimPolicy.decide(false, true, false, false));
    }

    @Test
    public void provisionerIsNeverCalledTwiceInOneAttempt() {
        assertEquals(RoleClaimPolicy.Step.BLOCKED,
                RoleClaimPolicy.decide(false, true, true, true));
    }
}
