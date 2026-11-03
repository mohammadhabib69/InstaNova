package com.insta.nova.data.firebase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Pins the boundaries that must match {@code firestore.rules} (task P2-10). */
public class ValidationTest {

    @Test
    public void acceptsValidUsernames() {
        assertTrue(Validation.isValidUsername("habib"));
        assertTrue(Validation.isValidUsername("habib.rahman_99"));
        assertTrue(Validation.isValidUsername("a123"));
    }

    @Test
    public void rejectsInvalidUsernames() {
        assertFalse("must start with a letter", Validation.isValidUsername("1habib"));
        assertFalse("no leading dot", Validation.isValidUsername(".habib"));
        assertFalse("too short", Validation.isValidUsername("ab"));
        assertFalse("too long", Validation.isValidUsername("a" + "b".repeat(30)));
        assertFalse("no spaces", Validation.isValidUsername("ha bib"));
        assertFalse("no uppercase", Validation.isValidUsername("Habib"));
        assertFalse("no symbols", Validation.isValidUsername("habib!"));
        assertFalse(Validation.isValidUsername(null));
        assertFalse(Validation.isValidUsername(""));
    }

    @Test
    public void normalizesToLowerCase() {
        assertEquals("habib", Validation.normalizeUsername("  HaBiB  "));
        assertEquals("", Validation.normalizeUsername(null));
    }

    @Test
    public void bioAndFullNameLimits() {
        assertTrue(Validation.isValidBio(null));
        assertTrue(Validation.isValidBio("x".repeat(200)));
        assertFalse(Validation.isValidBio("x".repeat(201)));

        assertTrue(Validation.isValidFullName("Habib Rahman"));
        assertFalse(Validation.isValidFullName("   "));
        assertFalse(Validation.isValidFullName("x".repeat(61)));
        assertFalse(Validation.isValidFullName(null));
    }

    @Test
    public void usernameErrorPrefersEmptyMessage() {
        assertNull(Validation.usernameError("habib"));
        assertEquals("Username is required", Validation.usernameError("  "));
        assertNotNull(Validation.usernameError("ab"));
    }
}
