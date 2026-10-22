package com.insta.nova.data.remote.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Map;

import retrofit2.http.Headers;
import retrofit2.http.Query;

/**
 * The profile write endpoints must keep both halves of the Phase 1 contract:
 *
 * <ul>
 *   <li>{@code Prefer: return=representation} — without it a write filtered by RLS is
 *       indistinguishable from a success, which {@code ProfileWriteCheck} exists to catch;</li>
 *   <li>an explicit {@code select} query — without it {@code RETURNING *} returns
 *       {@code birthday} and {@code firebase_uid} regardless of the client's projection.</li>
 * </ul>
 *
 * <p>Reflection only: no device, no network, no build variant needed.
 */
public class RestApiProfileSelectTest {

    private static Method method(String name, Class<?>... params) throws NoSuchMethodException {
        return RestApi.class.getMethod(name, params);
    }

    private static String queryName(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (annotation instanceof Query) {
                return ((Query) annotation).value();
            }
        }
        return null;
    }

    private static boolean hasSelectQuery(Method method) {
        Annotation[][] parameterAnnotations = method.getParameterAnnotations();
        for (Annotation[] annotations : parameterAnnotations) {
            if ("select".equals(queryName(annotations))) return true;
        }
        return false;
    }

    private static String preferHeader(Method method) {
        Headers headers = method.getAnnotation(Headers.class);
        return headers == null ? "" : String.join(" ", headers.value());
    }

    @Test
    public void createProfileSelectsItsWriteEcho() throws Exception {
        Method create = method("createProfile", String.class, Map.class);
        assertTrue("createProfile must declare a @Query(\"select\") parameter",
                hasSelectQuery(create));
        assertTrue(preferHeader(create).contains("Prefer: return=representation"));
    }

    @Test
    public void updateProfileSelectsItsWriteEcho() throws Exception {
        // (id query, select query, body)
        Method update = method("updateProfile", String.class, String.class, Map.class);
        assertTrue("updateProfile must declare a @Query(\"select\") parameter",
                hasSelectQuery(update));
        assertTrue(preferHeader(update).contains("Prefer: return=representation"));
    }

    @Test
    public void profileReadsStillTakeAQueryMap() throws Exception {
        // GET projections are supplied through the map so each caller chooses its own
        // column set; the map form is what lets ProfileRepository/SearchRepository name
        // columns without a signature change per call site.
        Method profiles = method("getProfiles", Map.class);
        assertNotNull(profiles);
        assertEquals(retrofit2.Call.class, profiles.getReturnType());
        assertEquals(1, profiles.getParameterAnnotations().length);
    }
}
