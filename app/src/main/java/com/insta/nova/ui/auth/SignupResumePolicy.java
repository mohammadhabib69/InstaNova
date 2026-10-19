package com.insta.nova.ui.auth;

/**
 * Pure decision table for cold-start routing. Kept free of Android types so the
 * signup-resume state machine can be unit tested.
 *
 * <p>FirebaseAuth is the only source of truth for "is somebody signed in". The
 * local signup flag never grants access on its own: it only decides which wizard
 * step is shown once Firebase confirms a user exists.
 */
public final class SignupResumePolicy {

    public enum Destination {
        /** No Firebase user: the normal Login screen. */
        LOGIN,
        /** Signed in, unfinished signup, address not confirmed: the confirmation gate. */
        RESUME_CODE,
        /** Signed in, unfinished signup, address confirmed: the password step. */
        RESUME_PASSWORD,
        /** Signed in with a finished account: the app. */
        HOME
    }

    private SignupResumePolicy() {
    }

    /**
     * @param hasFirebaseUser  FirebaseAuth.getCurrentUser() != null
     * @param emailVerified    firebaseUser.isEmailVerified()
     * @param signupInProgress local flag written when the account is created and
     *                         cleared when the profile is finalised
     */
    public static Destination decide(boolean hasFirebaseUser, boolean emailVerified,
                                     boolean signupInProgress) {
        if (!hasFirebaseUser) return Destination.LOGIN;
        if (!signupInProgress) return Destination.HOME;
        return emailVerified ? Destination.RESUME_PASSWORD : Destination.RESUME_CODE;
    }

    /** True when a stored flag is stale because Firebase no longer has a user. */
    public static boolean isStale(boolean hasFirebaseUser, boolean signupInProgress) {
        return signupInProgress && !hasFirebaseUser;
    }
}
