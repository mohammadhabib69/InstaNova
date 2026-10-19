package com.insta.nova.data.repository;

import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException;

import java.util.Locale;

/**
 * Classifies Firebase Auth failures around a password change.
 *
 * <p>Firebase refuses {@code updatePassword} with {@code ERROR_REQUIRES_RECENT_LOGIN}
 * when the sign-in is older than its reauthentication window. That is a recoverable
 * state — the user can confirm the current password and the call is retried once —
 * but it must never be confused with a wrong password, and it must never be
 * "worked around" by hiding the requirement.
 *
 * <p>The checks accept both the typed exceptions and the raw message text, because
 * the SDK reports the same condition under either shape depending on call site.
 * No token, password or credential is ever read here.
 */
public final class PasswordRecovery {

    private PasswordRecovery() {
    }

    /** True when Firebase wants the user to reauthenticate before this operation. */
    public static boolean requiresRecentLogin(Exception e) {
        if (e == null) return false;
        if (e instanceof FirebaseAuthRecentLoginRequiredException) return true;
        String message = message(e);
        return message.contains("requires_recent_login")
                || message.contains("requires recent authentication")
                || message.contains("log in again before retrying");
    }

    /** True when reauthentication was rejected because the password was wrong. */
    public static boolean isWrongPassword(Exception e) {
        if (e == null) return false;
        if (e instanceof FirebaseAuthInvalidCredentialsException) return true;
        String message = message(e);
        return message.contains("wrong_password")
                || message.contains("invalid_login_credentials")
                || message.contains("password is invalid");
    }

    private static String message(Exception e) {
        return e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.US);
    }
}
