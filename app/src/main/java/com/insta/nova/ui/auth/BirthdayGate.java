package com.insta.nova.ui.auth;

import com.insta.nova.util.Constants;

/**
 * Decides whether the Birthday step may advance.
 *
 * <p>Kept free of Android types so the rule can be unit-tested on the JVM: the
 * fragment only maps {@link State} onto a message and the Next button.
 *
 * <p>Three outcomes matter on this screen:
 * <ul>
 *   <li>{@link State#MISSING} — nothing picked yet. Next stays disabled and the
 *       screen shows an instruction rather than failing silently.</li>
 *   <li>{@link State#TOO_YOUNG} — a real date below {@link Constants#MIN_AGE}.
 *       Next stays disabled with an explicit validation message.</li>
 *   <li>{@link State#VALID} — the minimum age is met, so Next is enabled
 *       immediately after the date is chosen.</li>
 * </ul>
 */
public final class BirthdayGate {

    public enum State {
        /** No date chosen, or a value that cannot be parsed as a date. */
        MISSING,
        /** A parsed date that is younger than {@link Constants#MIN_AGE}. */
        TOO_YOUNG,
        /** A parsed date that satisfies the minimum age. */
        VALID
    }

    private BirthdayGate() {
    }

    /** Classifies an ISO {@code yyyy-MM-dd} birthday. */
    public static State evaluate(String iso) {
        if (iso == null || iso.trim().isEmpty()) return State.MISSING;
        int age = RegistrationViewModel.ageFromIso(iso.trim());
        if (age < 0) return State.MISSING;
        return age >= Constants.MIN_AGE ? State.VALID : State.TOO_YOUNG;
    }

    /** True only when the wizard may leave this step. */
    public static boolean canContinue(String iso) {
        return evaluate(iso) == State.VALID;
    }

    /** Whole years for display; never negative, so "0 years old" is the floor. */
    public static int displayAge(String iso) {
        return Math.max(RegistrationViewModel.ageFromIso(iso), 0);
    }
}
