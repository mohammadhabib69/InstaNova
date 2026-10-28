package com.insta.nova.safe;

import android.content.Context;

import com.insta.nova.data.NovaRepository;
import com.insta.nova.data.NovaSettingsStore;

/**
 * One helper shared by every posting surface (comment, caption, confession, Q&amp;A,
 * chat). Screens call {@link #evaluate} and then present the result; this class owns
 * the "should we warn / block" policy and the moderation-event log so behaviour stays
 * identical everywhere.
 */
public final class NovaGuard {

    public static final int ACTION_WARNED = 0;
    public static final int ACTION_EDITED = 1;
    public static final int ACTION_BLOCKED = 2;
    public static final int ACTION_POSTED_ANYWAY = 3;
    public static final int ACTION_HIDDEN = 4;

    private NovaGuard() {
    }

    public static NovaModerator.Result evaluate(Context context, String text) {
        return NovaModerator.get(context).moderate(text);
    }

    /** Severity 3 always blocks; strict surfaces (confessions) block severity >= 2. */
    public static boolean blocks(NovaModerator.Result result, int strictFromSeverity) {
        return result != null && result.severity >= Math.max(2, strictFromSeverity);
    }

    public static boolean warns(Context context, NovaModerator.Result result) {
        if (result == null || result.isClean()) return false;
        return NovaSettingsStore.get(context).current().warnBeforePost;
    }

    public static void log(Context context, String kind, NovaModerator.Result result, int action) {
        if (result == null || result.isClean()) return;
        String label;
        switch (action) {
            case ACTION_WARNED: label = "warned"; break;
            case ACTION_EDITED: label = "edited"; break;
            case ACTION_BLOCKED: label = "blocked"; break;
            case ACTION_POSTED_ANYWAY: label = "posted_anyway"; break;
            case ACTION_HIDDEN: label = "hidden"; break;
            default: return;
        }
        new NovaRepository(context).logModeration(kind, result.category, result.severity, label);
    }

    /** Short, non-judgemental reason shown on the blocked sheet. */
    public static String reasonFor(NovaModerator.Result result) {
        if (result == null || result.category == null) return "";
        switch (result.category) {
            case "threat": return "a threat";
            case "hate": return "hate speech";
            case "sexual": return "sexual harassment";
            case "harassment": return "harassment";
            case "insult": return "an insult";
            case "spam": return "spam";
            default: return "harmful language";
        }
    }
}
