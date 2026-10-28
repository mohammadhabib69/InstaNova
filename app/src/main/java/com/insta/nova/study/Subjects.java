package com.insta.nova.study;

import android.content.Context;
import android.content.SharedPreferences;

import com.insta.nova.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Study subjects: the four built-in ones plus any the user adds themselves.
 *
 * <p>Custom subjects are stored on the device and offered everywhere the built-in keys
 * appear, so "Thermodynamics" can be used for a question and then filtered on like any
 * other subject. The key is the display name itself, which keeps the stored question row
 * readable without a lookup table.
 */
public final class Subjects {

    public static final String ALL = "all";
    public static final String[] KEYS = {"general", "math", "physics", "ict"};

    private static final String PREFS = "nova_study_subjects";
    private static final String KEY_CUSTOM = "custom_subjects";

    private Subjects() {
    }

    /** Built-ins first, then whatever the user added, de-duplicated. */
    public static String[] allKeys(Context context) {
        List<String> keys = new ArrayList<>();
        for (String key : KEYS) keys.add(key);
        for (String custom : custom(context)) {
            if (!keys.contains(custom)) keys.add(custom);
        }
        return keys.toArray(new String[0]);
    }

    public static List<String> custom(Context context) {
        List<String> out = new ArrayList<>();
        if (context == null) return out;
        String raw = prefs(context).getString(KEY_CUSTOM, "");
        if (raw == null || raw.trim().isEmpty()) return out;
        for (String part : raw.split("\\n")) {
            String name = part.trim();
            if (!name.isEmpty() && !out.contains(name)) out.add(name);
        }
        return out;
    }

    /** Adds a custom subject and returns its key, or null when the input is unusable. */
    public static String addCustom(Context context, String name) {
        if (context == null) return null;
        String clean = name == null ? "" : name.trim();
        if (clean.length() < 2 || clean.length() > 30) return null;
        for (String builtIn : KEYS) {
            if (label(context, builtIn).equalsIgnoreCase(clean)) return builtIn;
        }
        List<String> existing = custom(context);
        for (String custom : existing) {
            if (custom.equalsIgnoreCase(clean)) return custom;
        }
        existing.add(clean);
        StringBuilder joined = new StringBuilder();
        for (String custom : existing) {
            if (joined.length() > 0) joined.append('\n');
            joined.append(custom);
        }
        prefs(context).edit().putString(KEY_CUSTOM, joined.toString()).apply();
        return clean;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String label(Context context, String key) {
        if (context == null) return key == null ? "" : key;
        if (key == null) return context.getString(R.string.nova_qa_all_subjects);
        switch (key) {
            case ALL: return context.getString(R.string.nova_qa_all_subjects);
            case "general": return context.getString(R.string.nova_study_subject_general);
            case "math": return context.getString(R.string.nova_study_subject_math);
            case "physics": return context.getString(R.string.nova_study_subject_physics);
            case "ict": return context.getString(R.string.nova_study_subject_ict);
            default: return key;
        }
    }
}
