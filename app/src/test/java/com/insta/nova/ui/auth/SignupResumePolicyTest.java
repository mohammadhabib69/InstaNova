package com.insta.nova.ui.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Cold-start routing must never let a local flag stand in for authentication,
 * and must never send an unconfirmed account past the confirmation gate.
 */
public class SignupResumePolicyTest {

    @Test
    public void signedOutAlwaysGoesToLogin() {
        assertEquals(SignupResumePolicy.Destination.LOGIN,
                SignupResumePolicy.decide(false, false, false));
        // A stale flag must not open the wizard without a Firebase user.
        assertEquals(SignupResumePolicy.Destination.LOGIN,
                SignupResumePolicy.decide(false, false, true));
        assertEquals(SignupResumePolicy.Destination.LOGIN,
                SignupResumePolicy.decide(false, true, true));
    }

    @Test
    public void unfinishedSignupResumesAtTheConfirmationGateWhenUnverified() {
        assertEquals(SignupResumePolicy.Destination.RESUME_CODE,
                SignupResumePolicy.decide(true, false, true));
    }

    @Test
    public void unfinishedSignupResumesAtPasswordOnceVerified() {
        assertEquals(SignupResumePolicy.Destination.RESUME_PASSWORD,
                SignupResumePolicy.decide(true, true, true));
    }

    @Test
    public void finishedAccountGoesHome() {
        assertEquals(SignupResumePolicy.Destination.HOME,
                SignupResumePolicy.decide(true, true, false));
        assertEquals(SignupResumePolicy.Destination.HOME,
                SignupResumePolicy.decide(true, false, false));
    }

    @Test
    public void staleFlagDetection() {
        assertTrue(SignupResumePolicy.isStale(false, true));
        assertFalse(SignupResumePolicy.isStale(true, true));
        assertFalse(SignupResumePolicy.isStale(false, false));
    }
}
