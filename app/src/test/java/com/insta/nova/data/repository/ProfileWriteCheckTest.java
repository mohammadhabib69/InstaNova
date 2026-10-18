package com.insta.nova.data.repository;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.insta.nova.data.remote.dto.ProfileDto;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * PostgREST reports 204/201 even when RLS filtered the row, so "no row returned" must be
 * treated as a failed write. These tests pin that rule.
 */
public class ProfileWriteCheckTest {

    private static ProfileDto row(String id) {
        return new Gson().fromJson("{\"id\":\"" + id + "\"}", ProfileDto.class);
    }

    @Test
    public void singleMatchingRowIsConfirmed() {
        List<ProfileDto> rows = Collections.singletonList(row("abc"));
        assertTrue(ProfileWriteCheck.confirmed(rows, "abc"));
    }

    @Test
    public void zeroRowsIsFailure() {
        // The exact shape of a write filtered by RLS: HTTP success, empty representation.
        assertFalse(ProfileWriteCheck.confirmed(Collections.emptyList(), "abc"));
    }

    @Test
    public void nullBodyIsFailure() {
        assertFalse(ProfileWriteCheck.confirmed(null, "abc"));
    }

    @Test
    public void wrongRowIsFailure() {
        List<ProfileDto> rows = Collections.singletonList(row("other"));
        assertFalse(ProfileWriteCheck.confirmed(rows, "abc"));
    }

    @Test
    public void multipleRowsIsFailure() {
        List<ProfileDto> rows = Arrays.asList(row("abc"), row("abc"));
        assertFalse(ProfileWriteCheck.confirmed(rows, "abc"));
    }

    @Test
    public void nullExpectedIdAcceptsTheSingleReturnedRow() {
        assertTrue(ProfileWriteCheck.confirmed(Collections.singletonList(row("abc")), null));
    }

    @Test
    public void messageExplainsThatNothingWasSaved() {
        assertTrue(ProfileWriteCheck.ZERO_ROWS_MESSAGE.toLowerCase().contains("not updated"));
    }
}
