package com.insta.nova.data.remote;

/**
 * Decides what to do when an ID token does not yet carry {@code role: "authenticated"}.
 *
 * <p>Supabase reads that claim to pick a Postgres role. Firebase never mints it on its
 * own, so a brand-new account's first token is treated as {@code anon} and every
 * {@code to authenticated} policy (including the profile insert) rejects the request.
 *
 * <p>The order is fixed and bounded — inspect, refresh once, ask a trusted provisioner
 * once, then stop. There is no polling loop: a claim that still is not there after one
 * refresh plus one provisioning call is reported to the caller rather than retried
 * blindly, because retrying cannot change the outcome.
 */
public final class RoleClaimPolicy {

    public enum Step {
        /** Token already carries the claim; continue with the request. */
        PROCEED,
        /** Token may predate the claim; mint a fresh one exactly once. */
        FORCE_REFRESH,
        /** Refreshing did not help and a provisioner is configured; call it once. */
        PROVISION,
        /** Nothing left to try; report the missing claim instead of sending the write. */
        BLOCKED
    }

    private RoleClaimPolicy() {
    }

    /**
     * @param hasRole            the token in hand carries {@code role: "authenticated"}
     * @param refreshed          a forced token refresh already happened in this attempt
     * @param provisionConfigured a trusted provisioning endpoint is compiled in
     * @param provisionTried     that endpoint was already called in this attempt
     */
    public static Step decide(boolean hasRole, boolean refreshed,
                              boolean provisionConfigured, boolean provisionTried) {
        if (hasRole) return Step.PROCEED;
        if (!refreshed) return Step.FORCE_REFRESH;
        if (provisionConfigured && !provisionTried) return Step.PROVISION;
        return Step.BLOCKED;
    }
}
