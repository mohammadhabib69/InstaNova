package com.insta.nova.data.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.insta.nova.data.model.Profile;
import com.insta.nova.data.remote.dto.ProfileDto;

import org.junit.Test;

/**
 * Phase 1 of the profile-privacy remediation relies on every request naming its columns.
 * These tests pin the two properties that make that safe:
 *
 * <ul>
 *   <li>no list/detail projection can carry {@code birthday} or {@code firebase_uid};</li>
 *   <li>every projection names only real table columns, so narrowing cannot itself
 *       produce a 42703 the way the removed {@code email} / {@code is_private} did.</li>
 * </ul>
 */
public class ProfileColumnsTest {

    private static final String[] PROJECTIONS = {
            ProfileColumns.PUBLIC,
            ProfileColumns.SEARCH,
            ProfileColumns.WRITE_ECHO,
            ProfileColumns.IDENTITY
    };

    @Test
    public void everyProjectionIsAnExplicitColumnList() {
        for (String projection : PROJECTIONS) {
            assertNotNull(projection);
            assertFalse(projection.trim().isEmpty());
            assertFalse("no wildcard selects: " + projection, projection.contains("*"));
            assertFalse("no spaces in a query value: " + projection, projection.contains(" "));
            assertFalse("no empty tokens: " + projection, projection.contains(",,"));
            assertFalse("no trailing comma: " + projection, projection.endsWith(","));
        }
    }

    @Test
    public void publicProjectionCarriesNoSensitiveColumn() {
        assertFalse(ProfileColumns.containsColumn(ProfileColumns.PUBLIC, ProfileColumns.BIRTHDAY));
        assertFalse(ProfileColumns.containsColumn(ProfileColumns.PUBLIC, ProfileColumns.FIREBASE_UID));
        assertFalse(ProfileColumns.containsColumn(ProfileColumns.SEARCH, ProfileColumns.BIRTHDAY));
        assertFalse(ProfileColumns.containsColumn(ProfileColumns.SEARCH, ProfileColumns.FIREBASE_UID));
        assertFalse(ProfileColumns.containsColumn(ProfileColumns.WRITE_ECHO, ProfileColumns.BIRTHDAY));
        assertFalse(ProfileColumns.containsColumn(ProfileColumns.WRITE_ECHO, ProfileColumns.FIREBASE_UID));
    }

    /**
     * Phase 1 keeps exactly one client projection that reads firebase_uid: the signup
     * identity lookup. If a second one ever appears, this fails.
     */
    @Test
    public void onlyIdentityProjectionReadsFirebaseUid() {
        assertTrue(ProfileColumns.containsColumn(ProfileColumns.IDENTITY, ProfileColumns.FIREBASE_UID));
        for (String projection : PROJECTIONS) {
            boolean expected = ProfileColumns.IDENTITY.equals(projection);
            assertEquals("firebase_uid in " + projection,
                    expected, ProfileColumns.containsColumn(projection, ProfileColumns.FIREBASE_UID));
        }
    }

    /**
     * {@code is_following} is exposed by get_suggested_users(uuid) but is not a column of
     * public.profiles. Selecting it would fail with 42703.
     */
    @Test
    public void projectionsNameOnlyRealProfileColumns() {
        for (String projection : PROJECTIONS) {
            assertFalse("is_following is not a profiles column: " + projection,
                    ProfileColumns.containsColumn(projection, "is_following"));
            assertFalse("email is not a profiles column: " + projection,
                    ProfileColumns.containsColumn(projection, "email"));
            assertFalse("is_private is not a profiles column: " + projection,
                    ProfileColumns.containsColumn(projection, "is_private"));
        }
    }

    /** ProfileWriteCheck.confirmed compares ids, so the write echo must return id. */
    @Test
    public void writeEchoAlwaysContainsId() {
        assertTrue(ProfileColumns.containsColumn(ProfileColumns.WRITE_ECHO, ProfileColumns.ID));
        assertTrue(ProfileColumns.containsColumn(ProfileColumns.IDENTITY, ProfileColumns.ID));
    }

    @Test
    public void searchAndWriteEchoAreSubsetsOfPublic() {
        for (String token : ProfileColumns.SEARCH.split(",")) {
            assertTrue("SEARCH token not in PUBLIC: " + token,
                    ProfileColumns.containsColumn(ProfileColumns.PUBLIC, token.trim()));
        }
        for (String token : ProfileColumns.WRITE_ECHO.split(",")) {
            assertTrue("WRITE_ECHO token not in PUBLIC: " + token,
                    ProfileColumns.containsColumn(ProfileColumns.PUBLIC, token.trim()));
        }
    }

    @Test
    public void containsColumnMatchesWholeTokensOnly() {
        assertTrue(ProfileColumns.containsColumn("id,username", "id"));
        assertFalse(ProfileColumns.containsColumn("firebase_uid", "id"));
        assertFalse(ProfileColumns.containsColumn("userid", "id"));
        assertFalse(ProfileColumns.containsColumn(null, "id"));
        assertFalse(ProfileColumns.containsColumn("id", ""));
    }

    /**
     * A narrowed write echo still deserialises into a usable DTO: no NPE, id preserved,
     * absent columns fall back to the domain defaults.
     */
    @Test
    public void narrowedWriteEchoStillDeserialises() {
        ProfileDto dto = new Gson().fromJson(
                "{\"id\":\"3c96a7cc\",\"username\":\"qaverify\",\"full_name\":\"QA Verify\","
                        + "\"avatar_url\":null}",
                ProfileDto.class);
        assertNotNull(dto);
        assertEquals("3c96a7cc", dto.getId());
        assertFalse(dto.isVerified());

        Profile profile = dto.toDomain();
        assertEquals("qaverify", profile.getUsername());
        assertEquals("QA Verify", profile.getFullName());
        assertEquals("", profile.getBio());
        assertEquals("", profile.getWebsite());
        assertEquals(0, profile.getFollowersCount());
        assertNull(profile.getBirthday());
    }
}
