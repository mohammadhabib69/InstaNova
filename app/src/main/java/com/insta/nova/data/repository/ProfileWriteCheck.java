package com.insta.nova.data.repository;

import com.insta.nova.data.remote.dto.ProfileDto;

import java.util.List;

/**
 * Decides whether a profile POST/PATCH actually wrote a row.
 *
 * <p>PostgREST answers <b>204 No Content</b> (or 201 with an empty body) when RLS filters
 * every candidate row, so a write that changed nothing is indistinguishable from a
 * successful one unless the returned representation is inspected. Both endpoints already
 * send {@code Prefer: return=representation}; this class is the check that makes the
 * response meaningful.
 *
 * <p>Pure Java on purpose: the rule is unit-tested without a device or a network.
 */
public final class ProfileWriteCheck {

    /** Shown whenever Supabase accepted the request but reported no written row. */
    public static final String ZERO_ROWS_MESSAGE =
            "Supabase accepted the request but saved no row. Your profile was not updated.";

    private ProfileWriteCheck() {
    }

    /**
     * True only when the response carries exactly one row whose id matches the row we
     * meant to write. Zero rows (filtered by RLS), several rows (ambiguous filter) and a
     * mismatched id are all failures.
     *
     * @param rows       representation returned by Supabase, may be null
     * @param expectedId id the caller asked to create/update; null accepts any id
     */
    public static boolean confirmed(List<ProfileDto> rows, String expectedId) {
        if (rows == null || rows.size() != 1) return false;
        String returnedId = rows.get(0) == null ? null : rows.get(0).getId();
        if (returnedId == null) return false;
        return expectedId == null || expectedId.equals(returnedId);
    }
}
