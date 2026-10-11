package com.insta.clone.util;

import android.text.TextUtils;
import android.util.Patterns;
import java.util.Calendar;

/**
 * Validation utility for emails, passwords, usernames, and age requirements.
 */
public final class Validators {

    private Validators() {}

    public static boolean isValidEmail(String email) {
        return !TextUtils.isEmpty(email) && Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches();
    }

    public static boolean isValidPassword(String password) {
        return password != null && password.length() >= 6;
    }

    public static boolean isValidUsername(String username) {
        if (username == null) return false;
        String trimmed = username.trim();
        // Usernames can contain letters, numbers, underscores, and periods (3 to 30 characters)
        return trimmed.matches("^[a-zA-Z0-9._]{3,30}$");
    }

    public static boolean isAtLeast13YearsOld(int year, int month, int day) {
        Calendar dob = Calendar.getInstance();
        dob.set(year, month, day);

        Calendar minAgeDate = Calendar.getInstance();
        minAgeDate.add(Calendar.YEAR, -13);

        return !dob.after(minAgeDate);
    }
}
