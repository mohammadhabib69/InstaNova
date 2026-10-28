package com.insta.nova.data.firebase;

import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Field validation that mirrors the Postgres constraints the app relies on today
 * (task P2-10): {@code profiles_username_fmt}, {@code profiles_username_len}.
 *
 * <p>Firestore has no CHECK constraints, so the same rules must be enforced in three
 * places — UI, this class, and {@code firestore.rules}. Keep them in sync; the unit test
 * pins the boundaries.
 */
public final class Validation {

    public static final int USERNAME_MIN = 3;
    public static final int USERNAME_MAX = 30;
    public static final int FULL_NAME_MAX = 60;
    public static final int BIO_MAX = 200;

    /** Lowercase start, then letters/digits/underscore/dot — matches the existing DB check. */
    private static final Pattern USERNAME =
            Pattern.compile("^[a-z][a-z0-9._]{2,29}$");

    private Validation() {
    }

    public static boolean isValidUsername(@Nullable String username) {
        return username != null && USERNAME.matcher(username).matches();
    }

    public static boolean isValidFullName(@Nullable String fullName) {
        if (fullName == null) {
            return false;
        }
        String trimmed = fullName.trim();
        return !trimmed.isEmpty() && trimmed.length() <= FULL_NAME_MAX;
    }

    public static boolean isValidBio(@Nullable String bio) {
        return bio == null || bio.length() <= BIO_MAX;
    }

    /** Usernames are stored lowercased so lookups are case-insensitive without a collation. */
    public static String normalizeUsername(@Nullable String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    /** First problem to show the user, or null when the input is acceptable. */
    @Nullable
    public static String usernameError(@Nullable String username) {
        if (username == null || username.trim().isEmpty()) {
            return "Username is required";
        }
        if (!isValidUsername(normalizeUsername(username))) {
            return "3-30 chars: start with a letter, then letters, numbers, . or _";
        }
        return null;
    }
}
