package com.insta.nova.data.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A missing role claim must be detected, never assumed away: Supabase silently treats
 * such a token as anon, and a rejected write must not look like a successful one.
 */
public class TokenClaimsTest {

    @Test
    public void detectsAuthenticatedRole() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", "authenticated");
        assertTrue(TokenClaims.hasAuthenticatedRole(claims));
        assertEquals("authenticated", TokenClaims.roleOf(claims));
    }

    @Test
    public void treatsMissingClaimAsAnon() {
        assertFalse(TokenClaims.hasAuthenticatedRole(new HashMap<>()));
        assertFalse(TokenClaims.hasAuthenticatedRole(null));
        assertNull(TokenClaims.roleOf(null));
    }

    @Test
    public void doesNotAcceptOtherRoles() {
        Map<String, Object> anon = Collections.singletonMap("role", "anon");
        assertFalse(TokenClaims.hasAuthenticatedRole(anon));

        Map<String, Object> weird = Collections.singletonMap("role", 42);
        assertFalse(TokenClaims.hasAuthenticatedRole(weird));
    }
}
