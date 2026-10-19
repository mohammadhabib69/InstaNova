package com.insta.nova.ui.auth;

/**
 * Pure decision table for the splash screen's identity gate.
 *
 * <p>Firebase authentication and the Supabase profile row are two independent facts:
 * a signed-in Firebase user may or may not have a {@code profiles} row whose
 * {@code firebase_uid} matches. Screens downstream of the splash assume
 * {@link com.insta.nova.data.local.SessionManager#getUserId()} is a real UUID, so the
 * gate has to resolve that before any activity that needs an identity is started.
 *
 * <p>The three failure modes are deliberately distinct:
 * <ul>
 *   <li>{@link Lookup#MISSING} — the network answered and there is no row. Retrying will
 *       not help; the user needs an explanation, not a spinner.</li>
 *   <li>{@link Lookup#ERROR} — the lookup itself failed (offline, 5xx, timeout). A cached
 *       identity from a previous successful launch is still usable, so we do not throw
 *       the session away for a network blip.</li>
 *   <li>{@link Lookup#UNKNOWN} — nothing has been asked yet.</li>
 * </ul>
 */
public final class SessionIdentityPolicy {

    /** Outcome of the {@code profiles?firebase_uid=eq.} lookup. */
    public enum Lookup { UNKNOWN, FOUND, MISSING, ERROR }

    /** What the splash should do next. */
    public enum Route {
        /** Ask the backend for the profile row. */
        RESOLVE,
        /** Identity confirmed for this launch. */
        HOME,
        /** Lookup unavailable, but a previously verified UUID is cached for this UID. */
        HOME_WITH_CACHED_ID,
        /** Authenticated, but no profile row exists. */
        MISSING_PROFILE,
        /** Cannot decide and have nothing trustworthy cached. */
        RETRY
    }

    private SessionIdentityPolicy() {
    }

    public static Route decide(Lookup lookup, boolean hasCachedProfileId) {
        Lookup effective = lookup == null ? Lookup.UNKNOWN : lookup;
        switch (effective) {
            case FOUND:
                return Route.HOME;
            case MISSING:
                return Route.MISSING_PROFILE;
            case ERROR:
                return hasCachedProfileId ? Route.HOME_WITH_CACHED_ID : Route.RETRY;
            case UNKNOWN:
            default:
                return hasCachedProfileId ? Route.HOME_WITH_CACHED_ID : Route.RESOLVE;
        }
    }

    /**
     * A cached UUID is only trustworthy for the Firebase user it was stored for. Comparing
     * the UID prevents an account switch from reusing the previous account's profile id.
     */
    public static boolean cacheMatches(String cachedFirebaseUid, String cachedProfileId, String currentUid) {
        if (isEmpty(cachedProfileId) || isEmpty(currentUid)) {
            return false;
        }
        return currentUid.equals(cachedFirebaseUid);
    }

    private static boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
