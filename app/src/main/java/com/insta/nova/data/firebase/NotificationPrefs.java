package com.insta.nova.data.firebase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;

/**
 * Typed read access to the notification preferences stored on {@code users/{uid}.settings}
 * (task P7-05).
 *
 * <p>{@link com.insta.nova.data.repository.FirestoreSettingsRepository} owns writing the map;
 * this class owns <em>interpreting</em> it. The split matters because the map is
 * client-written JSON: a value can be a string, a number, or missing entirely, and every
 * consumer must agree on what that means. If the dispatcher and the settings screen
 * disagreed, a user would toggle a switch and still get the notification — the kind of bug
 * that is invisible until someone complains.
 *
 * <p><b>Fail-open by design.</b> Anything unreadable counts as "enabled". A malformed
 * preference must not silently mute a user's messages; the cost of an unwanted notification
 * is lower than the cost of a missed one.
 */
public final class NotificationPrefs {

    /** A post the user owns was liked. */
    public static final String LIKES = "notifyLikes";
    /** Someone commented on the user's post, or replied to their comment. */
    public static final String COMMENTS = "notifyComments";
    /** A new follower, or a follow request on a private account. */
    public static final String FOLLOWS = "notifyFollows";
    /** The user's @username appeared in a caption or comment. */
    public static final String MENTIONS = "notifyMentions";
    /** A direct message arrived while the chat screen was closed. */
    public static final String MESSAGES = "notifyMessages";
    /** An incoming audio/video call. */
    public static final String CALLS = "notifyCalls";
    /** Someone the user follows posted a story. */
    public static final String STORIES = "notifyStories";
    /** A followed account started a live session. */
    public static final String LIVE = "notifyLive";

    /** Inclusive hour (0–23) at which quiet hours begin, or absent for "no quiet hours". */
    public static final String QUIET_START = "quietStartHour";
    /** Exclusive hour (0–23) at which quiet hours end. May be lower than start (overnight). */
    public static final String QUIET_END = "quietEndHour";

    private NotificationPrefs() {
    }

    /**
     * True when a notification of {@code type} should be delivered.
     *
     * @param settings the stored settings map, or {@code null} when the document is missing
     * @param type     one of the constants above
     */
    public static boolean allows(@Nullable Map<String, Object> settings, @NonNull String type) {
        if (settings == null || type.isEmpty()) return true;
        return asBoolean(settings.get(type), true);
    }

    /**
     * True when {@code hourOfDay} falls inside the user's quiet window.
     *
     * <p>Quiet hours are only active when both endpoints are present and differ. A window
     * like 22 → 7 crosses midnight, so the test is a wrap-around rather than a simple
     * comparison — the off-by-one here is why this lives in a tested helper and not inline
     * at the call site.
     */
    public static boolean inQuietHours(@Nullable Map<String, Object> settings, int hourOfDay) {
        if (settings == null || hourOfDay < 0 || hourOfDay > 23) return false;
        Integer start = asHour(settings.get(QUIET_START));
        Integer end = asHour(settings.get(QUIET_END));
        if (start == null || end == null || start.equals(end)) return false;
        if (start < end) return hourOfDay >= start && hourOfDay < end;
        return hourOfDay >= start || hourOfDay < end;
    }

    /**
     * Convenience for the dispatcher: a type is deliverable when the user enabled it
     * <em>or</em> it is a call (calls bypass quiet hours — a missed call is not recoverable
     * the way a delayed like is).
     */
    public static boolean deliverNow(@Nullable Map<String, Object> settings,
                                     @NonNull String type,
                                     int hourOfDay) {
        if (CALLS.equals(type)) return allows(settings, type);
        return allows(settings, type) && !inQuietHours(settings, hourOfDay);
    }

    /** Accepts Boolean, the strings "true"/"false", and 1/0. Everything else falls back. */
    private static boolean asBoolean(@Nullable Object raw, boolean fallback) {
        if (raw instanceof Boolean) return (Boolean) raw;
        if (raw instanceof Number) return ((Number) raw).intValue() != 0;
        if (raw instanceof String) {
            String s = ((String) raw).trim();
            if ("true".equalsIgnoreCase(s)) return true;
            if ("false".equalsIgnoreCase(s)) return false;
        }
        return fallback;
    }

    @Nullable
    private static Integer asHour(@Nullable Object raw) {
        int value;
        if (raw instanceof Number) {
            value = ((Number) raw).intValue();
        } else if (raw instanceof String) {
            try {
                value = Integer.parseInt(((String) raw).trim());
            } catch (NumberFormatException e) {
                return null;
            }
        } else {
            return null;
        }
        return value >= 0 && value <= 23 ? value : null;
    }
}
