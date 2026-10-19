package com.insta.nova.data.repository;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Regression tests for the {@code ERROR_REQUIRES_RECENT_LOGIN} classification.
 *
 * <p>The build must retry a password change only when Firebase actually asked for a
 * recent login, and must never treat an unrelated failure as "wrong password".
 * Plain exceptions are used here so the test needs no Firebase runtime.
 */
public class PasswordRecoveryTest {

    @Test
    public void errorCodeIsRecognised() {
        assertTrue(PasswordRecovery.requiresRecentLogin(
                new RuntimeException("ERROR_REQUIRES_RECENT_LOGIN")));
    }

    @Test
    public void sdkSentenceIsRecognised() {
        assertTrue(PasswordRecovery.requiresRecentLogin(new RuntimeException(
                "This operation is sensitive and requires recent authentication. "
                        + "Log in again before retrying this request.")));
    }

    @Test
    public void unrelatedFailuresDoNotAskForReauthentication() {
        assertFalse(PasswordRecovery.requiresRecentLogin(
                new RuntimeException("Network error. Check your connection and try again.")));
        assertFalse(PasswordRecovery.requiresRecentLogin(
                new RuntimeException("ERROR_TOO_MANY_REQUESTS")));
        assertFalse(PasswordRecovery.requiresRecentLogin(null));
    }

    @Test
    public void wrongPasswordIsRecognisedByCode() {
        assertTrue(PasswordRecovery.isWrongPassword(
                new RuntimeException("ERROR_WRONG_PASSWORD")));
        assertTrue(PasswordRecovery.isWrongPassword(
                new RuntimeException("INVALID_LOGIN_CREDENTIALS")));
    }

    @Test
    public void recentLoginFailureIsNotReportedAsWrongPassword() {
        assertFalse(PasswordRecovery.isWrongPassword(
                new RuntimeException("ERROR_REQUIRES_RECENT_LOGIN")));
        assertFalse(PasswordRecovery.isWrongPassword(null));
    }

    @Test
    public void resultFactoriesCarryTheirStatus() {
        assertTrue(PasswordSetupResult.success().isSuccess());
        assertFalse(PasswordSetupResult.needsCurrentPassword().isSuccess());
        assertFalse(PasswordSetupResult.wrongCurrentPassword("x").isSuccess());
        assertFalse(PasswordSetupResult.failed("x").isSuccess());
        assertTrue(PasswordSetupResult.needsCurrentPassword().status
                == PasswordSetupResult.Status.NEEDS_CURRENT_PASSWORD);
        assertTrue(PasswordSetupResult.loading().status
                == PasswordSetupResult.Status.LOADING);
    }
}
