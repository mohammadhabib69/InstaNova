package com.insta.nova.sos;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Pure-Java blood rules. Kept free of Android types so the 64-pair table and the
 * 90-day eligibility maths are unit-testable.
 */
public final class BloodCompat {

    public static final String[] GROUPS = {"A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"};
    public static final int ELIGIBILITY_DAYS = 90;

    private static final String[][] CAN_DONATE_TO = {
            // A+        A-        B+        B-        AB+       AB-       O+        O-
            {"A+", "AB+"}, {"A+", "A-", "AB+", "AB-"}, {"B+", "AB+"}, {"B+", "B-", "AB+", "AB-"},
            {"AB+"}, {"AB+", "AB-"}, {"A+", "B+", "AB+", "O+"},
            {"A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"}
    };

    private BloodCompat() {
    }

    public static boolean isKnownGroup(String group) {
        if (group == null) return false;
        for (String g : GROUPS) if (g.equals(group)) return true;
        return false;
    }

    /** True when {@code donor} can give red cells to {@code recipient}. */
    public static boolean canDonate(String donor, String recipient) {
        int index = indexOf(donor);
        if (index < 0 || !isKnownGroup(recipient)) return false;
        for (String target : CAN_DONATE_TO[index]) {
            if (target.equals(recipient)) return true;
        }
        return false;
    }

    public static String[] compatibleDonorsFor(String recipient) {
        if (!isKnownGroup(recipient)) return new String[0];
        int count = 0;
        for (String donor : GROUPS) if (canDonate(donor, recipient)) count++;
        String[] out = new String[count];
        int i = 0;
        for (String donor : GROUPS) if (canDonate(donor, recipient)) out[i++] = donor;
        return out;
    }

    private static int indexOf(String group) {
        if (group == null) return -1;
        for (int i = 0; i < GROUPS.length; i++) if (GROUPS[i].equals(group)) return i;
        return -1;
    }

    /* ----------------------------- eligibility ------------------------------ */

    public static Date nextEligibleDate(Date lastDonation) {
        if (lastDonation == null) return null;
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Dhaka"));
        calendar.setTime(lastDonation);
        calendar.add(Calendar.DAY_OF_YEAR, ELIGIBILITY_DAYS);
        return calendar.getTime();
    }

    public static boolean isEligible(String lastDonationIso, Date now) {
        Date last = parseDate(lastDonationIso);
        if (last == null) return true;
        Date next = nextEligibleDate(last);
        return now == null || !now.before(next);
    }

    public static int daysUntilEligible(String lastDonationIso, Date now) {
        Date last = parseDate(lastDonationIso);
        if (last == null) return 0;
        Date next = nextEligibleDate(last);
        long diff = next.getTime() - (now == null ? System.currentTimeMillis() : now.getTime());
        if (diff <= 0) return 0;
        return (int) Math.ceil(diff / 86400000.0);
    }

    /** Accepts ISO-8601 date or date-time (the column is a date). */
    public static Date parseDate(String iso) {
        if (iso == null || iso.isEmpty()) return null;
        String cleaned = iso.length() > 10 ? iso.substring(0, 10) : iso;
        String[] patterns = {"yyyy-MM-dd", "yyyy-MM-dd'T'HH:mm:ss"};
        for (String pattern : patterns) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
                format.setTimeZone(TimeZone.getTimeZone("Asia/Dhaka"));
                return format.parse(cleaned);
            } catch (ParseException ignored) {
            }
        }
        return null;
    }

    /* -------------------------------- levels -------------------------------- */

    public static String levelName(int donations) {
        if (donations >= 10) return "Platinum";
        if (donations >= 5) return "Gold";
        if (donations >= 3) return "Silver";
        if (donations >= 1) return "Bronze";
        return "";
    }

    public static int levelThreshold(int donations) {
        if (donations >= 10) return 10;
        if (donations >= 5) return 5;
        if (donations >= 3) return 3;
        if (donations >= 1) return 1;
        return 0;
    }
}
