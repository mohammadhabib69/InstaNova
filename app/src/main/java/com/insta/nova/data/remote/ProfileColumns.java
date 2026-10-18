package com.insta.nova.data.remote;

/**
 * Explicit PostgREST projections for {@code public.profiles}.
 *
 * <p>Phase 1 of the profile-privacy remediation. PostgREST answers
 * {@code GET /rest/v1/profiles} with every column when no {@code select} parameter is
 * sent, and {@code Prefer: return=representation} compiles to {@code RETURNING *}. Both
 * paths currently expose {@code birthday} and {@code firebase_uid} to any caller that can
 * read the row.
 *
 * <p>Every client request therefore names its columns. The database grants are the
 * enforcement point; these constants are what keep the app working once a column-level
 * grant is in place, because a projection that omits a column cannot be refused by that
 * grant.
 *
 * <p>Only real columns of the table may appear here. {@code is_following} is exposed by
 * {@code get_suggested_users(uuid)} but is <b>not</b> a column of {@code profiles};
 * selecting it would fail with 42703 exactly like the removed {@code email} /
 * {@code is_private} projections did.
 *
 * <p>Pure Java on purpose: the rule is unit-tested without a device or a network.
 */
public final class ProfileColumns {

    /**
     * Everything any signed-in viewer may read. Matches the {@code ProfileDto} surface
     * minus the two sensitive columns.
     */
    public static final String PUBLIC = "id,username,full_name,bio,avatar_url,website,"
            + "is_verified,posts_count,followers_count,following_count";

    /** Rows rendered by search results and the inbox suggestion list. */
    public static final String SEARCH = "id,username,full_name,avatar_url,is_verified";

    /**
     * Echo of a POST/PATCH response. {@code ProfileWriteCheck} only inspects {@code id};
     * the other columns keep debug logs readable without widening the response.
     */
    public static final String WRITE_ECHO = "id,username,full_name,avatar_url";

    /**
     * Phase 1 only: signup resolves an existing row by {@code firebase_uid}. Kept until
     * Phase 2 introduces a {@code current_profile()} RPC that removes the client's need
     * for the column entirely.
     */
    public static final String IDENTITY = "id,username,full_name,firebase_uid";

    /** Column named in every projection: without it no row can be identified. */
    public static final String ID = "id";

    /** Never requested outside {@link #IDENTITY}; the future grant narrows access. */
    public static final String BIRTHDAY = "birthday";
    public static final String FIREBASE_UID = "firebase_uid";

    private ProfileColumns() {
    }

    /**
     * True when {@code projection} names {@code column} as a whole token.
     *
     * @param projection comma-separated PostgREST column list, may be null
     * @param column     column name to look for
     */
    public static boolean containsColumn(String projection, String column) {
        if (projection == null || column == null || column.isEmpty()) return false;
        for (String token : projection.split(",")) {
            if (token.trim().equals(column)) return true;
        }
        return false;
    }
}
