package com.insta.nova.update;

/**
 * Compares version names such as {@code 1.2.0}, {@code 1.2.0-beta.2} or {@code 2.0}.
 *
 * <p>Used only as a fallback when a manifest has no {@code versionCode}: Android's own
 * install decision is driven by the version code, so this must never override it.
 */
public final class VersionName {

    private VersionName() {
    }

    /** @return negative when {@code left} is older, 0 when equal, positive when newer. */
    public static int compare(String left, String right) {
        int[] a = segments(left);
        int[] b = segments(right);
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

    /**
     * Splits on dots and drops a pre-release suffix ({@code -beta}, {@code +build}) so
     * {@code 1.1.0-beta} does not compare as newer than {@code 1.1.0} because of a
     * non-numeric tail. Numeric segments are clamped; a non-numeric segment counts as 0.
     */
    private static int[] segments(String raw) {
        if (raw == null) return new int[0];
        String value = raw.trim();
        if (value.startsWith("v") || value.startsWith("V")) {
            value = value.substring(1);
        }
        int cut = value.length();
        int dash = value.indexOf('-');
        int plus = value.indexOf('+');
        if (dash >= 0) cut = Math.min(cut, dash);
        if (plus >= 0) cut = Math.min(cut, plus);
        value = value.substring(0, cut);

        String[] parts = value.split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            StringBuilder digits = new StringBuilder();
            for (int c = 0; c < parts[i].length(); c++) {
                char ch = parts[i].charAt(c);
                if (ch < '0' || ch > '9') break;
                digits.append(ch);
            }
            if (digits.length() == 0) {
                out[i] = 0;
                continue;
            }
            try {
                long parsed = Long.parseLong(digits.toString());
                out[i] = parsed > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) parsed;
            } catch (NumberFormatException e) {
                out[i] = 0;
            }
        }
        return out;
    }
}
