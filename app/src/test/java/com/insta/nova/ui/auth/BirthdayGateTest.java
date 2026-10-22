package com.insta.nova.ui.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.insta.nova.util.Constants;

import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Regression tests for the Birthday step gate.
 *
 * <p>The screen is only allowed to advance on {@link BirthdayGate.State#VALID}; these
 * cases pin the three states the user sees, including the two that previously left
 * Next disabled with no explanation.
 */
public class BirthdayGateTest {

    private static String isoYearsAgo(int years) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.YEAR, -years);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.getTime());
    }

    @Test
    public void noDateChosenIsMissing() {
        assertEquals(BirthdayGate.State.MISSING, BirthdayGate.evaluate(null));
        assertEquals(BirthdayGate.State.MISSING, BirthdayGate.evaluate(""));
        assertEquals(BirthdayGate.State.MISSING, BirthdayGate.evaluate("   "));
        assertFalse(BirthdayGate.canContinue(null));
        assertFalse(BirthdayGate.canContinue(""));
    }

    @Test
    public void unparsableDateIsTreatedAsMissing() {
        assertEquals(BirthdayGate.State.MISSING, BirthdayGate.evaluate("not-a-date"));
        assertEquals(BirthdayGate.State.MISSING, BirthdayGate.evaluate("2020/01/01"));
        assertFalse(BirthdayGate.canContinue("not-a-date"));
    }

    @Test
    public void todayIsTooYoung() {
        assertEquals(BirthdayGate.State.TOO_YOUNG, BirthdayGate.evaluate(RegistrationViewModel.todayIso()));
        assertFalse(BirthdayGate.canContinue(RegistrationViewModel.todayIso()));
    }

    @Test
    public void justUnderTheMinimumIsTooYoung() {
        String twelve = isoYearsAgo(Constants.MIN_AGE - 1);
        assertEquals(BirthdayGate.State.TOO_YOUNG, BirthdayGate.evaluate(twelve));
        assertFalse(BirthdayGate.canContinue(twelve));
    }

    @Test
    public void exactlyTheMinimumIsValid() {
        String thirteen = isoYearsAgo(Constants.MIN_AGE);
        assertEquals(BirthdayGate.State.VALID, BirthdayGate.evaluate(thirteen));
        assertTrue(BirthdayGate.canContinue(thirteen));
    }

    @Test
    public void olderDatesRemainValid() {
        String thirty = isoYearsAgo(30);
        assertEquals(BirthdayGate.State.VALID, BirthdayGate.evaluate(thirty));
        assertTrue(BirthdayGate.canContinue(thirty));
        assertEquals(30, BirthdayGate.displayAge(thirty));
    }

    @Test
    public void displayAgeNeverGoesNegative() {
        assertEquals(0, BirthdayGate.displayAge(null));
        assertEquals(0, BirthdayGate.displayAge(""));
        assertEquals(0, BirthdayGate.displayAge("not-a-date"));
    }

    /**
     * A date exactly N years before today must report age N for every N.
     *
     * <p>This is the regression guard for the old DAY_OF_YEAR comparison, which
     * under-reported the age by one for anyone born after 28 February in a leap
     * year — and could therefore keep a real 13-year-old off the next step.
     */
    @Test
    public void wholeYearsBackAlwaysReportThatAge() {
        for (int years = 1; years <= 40; years++) {
            String iso = isoYearsAgo(years);
            assertEquals("years back=" + years, years, RegistrationViewModel.ageFromIso(iso));
        }
    }

    /** A trailing space must not turn a real date into MISSING. */
    @Test
    public void surroundingWhitespaceIsIgnored() {
        String thirteen = " " + isoYearsAgo(Constants.MIN_AGE) + " ";
        assertEquals(BirthdayGate.State.VALID, BirthdayGate.evaluate(thirteen));
    }
}
