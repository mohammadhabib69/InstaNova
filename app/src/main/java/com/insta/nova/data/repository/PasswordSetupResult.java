package com.insta.nova.data.repository;

/**
 * Outcome of a password change attempted during signup.
 *
 * <p>{@link Resource} cannot express "the user must confirm the current password
 * before we can retry", so the wizard gets its own small result type instead of
 * parsing a string.
 */
public final class PasswordSetupResult {

    public enum Status {
        LOADING,
        SUCCESS,
        /** Firebase wants the current password before the change can be saved. */
        NEEDS_CURRENT_PASSWORD,
        /** Reauthentication ran and the supplied current password was incorrect. */
        WRONG_CURRENT_PASSWORD,
        /** Anything else, including a second recent-login refusal after reauth. */
        FAILED
    }

    public final Status status;
    public final String message;

    private PasswordSetupResult(Status status, String message) {
        this.status = status;
        this.message = message;
    }

    public static PasswordSetupResult loading() {
        return new PasswordSetupResult(Status.LOADING, null);
    }

    public static PasswordSetupResult success() {
        return new PasswordSetupResult(Status.SUCCESS, null);
    }

    public static PasswordSetupResult needsCurrentPassword() {
        return new PasswordSetupResult(Status.NEEDS_CURRENT_PASSWORD, null);
    }

    public static PasswordSetupResult wrongCurrentPassword(String message) {
        return new PasswordSetupResult(Status.WRONG_CURRENT_PASSWORD, message);
    }

    public static PasswordSetupResult failed(String message) {
        return new PasswordSetupResult(Status.FAILED, message);
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }
}
