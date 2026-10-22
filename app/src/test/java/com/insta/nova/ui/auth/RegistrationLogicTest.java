package com.insta.nova.ui.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.insta.nova.data.repository.AuthRepository;
import com.insta.nova.util.Constants;

import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * JVM tests for the pure logic behind the signup wizard. These need no device and no
 * network: they cover the rules that are easy to regress when the auth backend changes.
 */
public class RegistrationLogicTest {

    /** The placeholder username must satisfy the profiles_username_fmt / length checks. */
    @Test
    public void temporaryUsernameMatchesDatabaseConstraints() {
        for (int i = 0; i < 200; i++) {
            String candidate = AuthRepository.temporaryUsername();
            assertTrue("format: " + candidate, candidate.matches("^[a-z0-9._]+$"));
            assertTrue("length: " + candidate,
                    candidate.length() >= 3 && candidate.length() <= 30);
            assertTrue("temp pattern: " + candidate, Constants.isTemporaryUsername(candidate));
        }
    }

    /** A real chosen username must never be mistaken for the placeholder. */
    @Test
    public void chosenUsernameIsNotTemporary() {
        assertFalse(Constants.isTemporaryUsername("habib.dev"));
        assertFalse(Constants.isTemporaryUsername("instanova11"));
        assertTrue(Constants.isTemporaryUsername(null));
        assertTrue(Constants.isTemporaryUsername(""));
    }

    @Test
    public void ageBoundaryIsExactlyThirteen() {
        Calendar now = Calendar.getInstance();
        Calendar thirteen = (Calendar) now.clone();
        thirteen.add(Calendar.YEAR, -13);
        String iso = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(thirteen.getTime());
        assertEquals(13, RegistrationViewModel.ageFromIso(iso));

        Calendar twelve = (Calendar) now.clone();
        twelve.add(Calendar.YEAR, -12);
        String tooYoung = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(twelve.getTime());
        assertEquals(12, RegistrationViewModel.ageFromIso(tooYoung));

        assertEquals(-1, RegistrationViewModel.ageFromIso("not-a-date"));
    }

    @Test
    public void resendCountdownFormatting() {
        assertEquals("1:05", RegistrationViewModel.formatCountdown(65));
        assertEquals("0:59", RegistrationViewModel.formatCountdown(59));
        assertEquals("0:00", RegistrationViewModel.formatCountdown(0));
    }

    @Test
    public void usernameSuggestionsStayInsideTheRules() {
        String suggestion = RegistrationViewModel.buildUsernameSuggestion("Habib  Rahman!");
        assertEquals("habibrahman", suggestion);
        assertTrue(suggestion.length() >= 3);

        String withDigits = RegistrationViewModel.withRandomDigits("habibrahman");
        assertTrue(withDigits.length() <= 30);
        assertTrue(withDigits.matches("^[a-z0-9._]+$"));
        assertTrue(Constants.isTemporaryUsername(
                RegistrationViewModel.buildUsernameSuggestion("ab")) == false);
    }
}
