package com.insta.nova.data.firebase;

/**
 * Client-side mirror of the visibility decisions Firestore rules enforce (task P3-10).
 *
 * <p>Rules decide what the server will return; this decides what the UI should ask for and
 * what it should hide. Keeping both in one small class means the "private account" matrix
 * can be unit-tested instead of being re-derived in every screen.
 *
 * <p>Deliberately conservative: when a flag is unknown, deny.
 */
public final class PrivacyPolicy {

    private PrivacyPolicy() {
    }

    /** Posts, reels, stories and highlights of the target profile. */
    public static boolean canViewContent(boolean isSelf, boolean targetIsPrivate, boolean followsTarget) {
        return isSelf || !targetIsPrivate || followsTarget;
    }

    /** Follower / following lists. Private accounts keep these closed to non-followers. */
    public static boolean canViewFollowerList(boolean isSelf, boolean targetIsPrivate, boolean followsTarget) {
        return isSelf || !targetIsPrivate || followsTarget;
    }

    /** A private account can still be followed — via a request. */
    public static boolean canRequestFollow(boolean isSelf) {
        return !isSelf;
    }

    /** Direct messages: private accounts only accept them from people they follow back. */
    public static boolean canStartConversation(boolean isSelf, boolean targetIsPrivate, boolean targetFollowsMe) {
        return !isSelf && (!targetIsPrivate || targetFollowsMe);
    }

    /** Profiles stay discoverable by username even when private; only their content closes. */
    public static boolean isDiscoverable(boolean targetIsPrivate) {
        return true;
    }

    /** A private profile's counters are still public (Instagram behaviour). */
    public static boolean showsCounters(boolean targetIsPrivate) {
        return true;
    }
}
