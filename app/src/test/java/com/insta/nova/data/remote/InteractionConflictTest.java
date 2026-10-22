package com.insta.nova.data.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.insta.nova.data.remote.api.RestApi;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Map;

import retrofit2.http.Headers;
import retrofit2.http.Query;

/**
 * A duplicate like used to reach Postgres as a plain INSERT and come back as 23505,
 * which the UI reported as a failure even though the desired row already existed.
 * The fix only works if both halves travel together: the conflict target names the
 * composite primary key in order, and the request carries
 * {@code Prefer: resolution=ignore-duplicates}. Either one alone is a no-op.
 */
public class InteractionConflictTest {

    @Test
    public void conflictTargetsMatchTheCompositePrimaryKeys() {
        assertEquals("post_id,user_id", InteractionConflict.LIKE);
        assertEquals("follower_id,following_id", InteractionConflict.FOLLOW);
        assertEquals("post_id,user_id", InteractionConflict.SAVE);
    }

    @Test
    public void conflictTargetsHaveNoDuplicatesOrSpaces() {
        for (String target : new String[]{InteractionConflict.LIKE, InteractionConflict.FOLLOW,
                InteractionConflict.SAVE}) {
            assertTrue(target + " must not contain spaces", !target.contains(" "));
            String[] parts = target.split(",");
            assertEquals("two key columns expected in " + target, 2, parts.length);
            assertTrue("duplicate column in " + target, !parts[0].equals(parts[1]));
        }
    }

    @Test
    public void likeEndpointSendsConflictTargetAndPreference() {
        Method m = method("likePost", String.class, Map.class);
        assertNotNull(m);
        assertNotNull("likePost must declare @Query(\"on_conflict\")", queryValue(m, "on_conflict"));
        assertTrue(headers(m).toLowerCase().contains("resolution=ignore-duplicates"));
    }

    @Test
    public void followEndpointSendsConflictTargetAndPreference() {
        Method m = method("followUser", String.class, Map.class);
        assertNotNull(m);
        assertNotNull("followUser must declare @Query(\"on_conflict\")", queryValue(m, "on_conflict"));
        assertTrue(headers(m).toLowerCase().contains("resolution=ignore-duplicates"));
    }

    @Test
    public void saveEndpointSendsConflictTargetAndPreference() {
        Method m = method("savePost", String.class, Map.class);
        assertNotNull("savePost must take the conflict target", m);
        assertNotNull("savePost must declare @Query(\"on_conflict\")", queryValue(m, "on_conflict"));
        assertTrue(headers(m).toLowerCase().contains("resolution=ignore-duplicates"));
    }

    private static Method method(String name, Class<?>... params) {
        try {
            return RestApi.class.getMethod(name, params);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static String queryValue(Method m, String key) {
        java.lang.annotation.Annotation[][] all = m.getParameterAnnotations();
        for (java.lang.annotation.Annotation[] perParam : all) {
            for (java.lang.annotation.Annotation a : perParam) {
                if (a instanceof Query && key.equals(((Query) a).value())) {
                    return key;
                }
            }
        }
        return null;
    }

    private static String headers(Method m) {
        Headers h = m.getAnnotation(Headers.class);
        return h == null ? "" : String.join(",", h.value());
    }
}
