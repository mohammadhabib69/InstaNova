package com.insta.nova.data.remote;

import java.util.Map;

/**
 * Reads the Firebase ID-token claims that matter to the Supabase integration.
 *
 * <p>Supabase picks a Postgres role from the token's {@code role} claim. Firebase ID
 * tokens do not carry one by default, so a token minted before the custom claim was
 * added still runs as {@code anon}. These helpers only inspect claims — they never
 * build, sign, print or persist a token.
 */
public final class TokenClaims {

    /** Role Supabase needs before any {@code to authenticated} policy applies. */
    public static final String ROLE_AUTHENTICATED = "authenticated";

    private TokenClaims() {
    }

    /** The {@code role} claim, or null when the token has none. */
    public static String roleOf(Map<String, Object> claims) {
        if (claims == null) return null;
        Object role = claims.get("role");
        return role instanceof String ? (String) role : null;
    }

    /** True when the token will be treated as an authenticated Supabase session. */
    public static boolean hasAuthenticatedRole(Map<String, Object> claims) {
        return ROLE_AUTHENTICATED.equals(roleOf(claims));
    }
}
