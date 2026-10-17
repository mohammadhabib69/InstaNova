package com.insta.nova.util;

/**
 * Global constant parameters and configuration values.
 */
public final class Constants {

    private Constants() {}

    public static final String WORDMARK = "InstaNova";
    public static final String FOOTER_FROM = "from";
    public static final String FOOTER_BRAND = "Velkyn";

    /** Product name used in the registration flow copy. Matches @string/app_name. */
    public static final String APP_NAME = "InstaNova";
    /** PAGE 18 — profile links shared from the three-dot sheet. */
    public static final String PROFILE_URL_BASE = "https://www.instanova.app/";

    /**
     * Phone sign-up needs an SMS provider, which this Supabase project does not
     * have (auth/v1/settings reports external.phone = false). The Mobile number
     * screen is built to the reference; its "Next" explains this and offers
     * "Use email" instead. Flip to true once an SMS provider is configured.
     */
    public static final boolean PHONE_SIGNUP_ENABLED = false;

    /* ---------- Registration wizard ---------- */

    /** Prefs file for onboarding flags + the not-yet-finished signup draft. */
    public static final String PREF_ONBOARDING = "insta_onboarding_prefs";
    public static final String KEY_ONBOARDING_DONE = "onboarding_complete";
    public static final String KEY_SIZE_TOOLTIP_DONE = "registration_size_tooltip_done";

    /**
     * The placeholder username the DB trigger assigns at sign-up, before the
     * wizard's Username step runs. An account whose profile still matches this
     * is "unfinished" -> SplashActivity resumes the wizard at the Password step
     * and the Code step refuses to treat the email as already registered.
     *
     * NEW = what migrations/2026-10-12_auth_wizard_handle_new_user.sql writes.
     * LEGACY = what the pre-wizard schema.sql wrote ("user" + 8 hex, no
     * underscore). Both are accepted so the flow works whether or not that
     * migration has been applied.
     */
    public static final String TEMP_USERNAME_PATTERN = "^user_[0-9a-f]{10}$";
    public static final String TEMP_USERNAME_PATTERN_LEGACY = "^user[0-9a-f]{8}$";

    /** Password rules enforced by the Create-a-password step. */
    public static final int PASSWORD_MIN_LENGTH = 6;
    public static final int MIN_AGE = 13;
    public static final int BIO_MAX_LENGTH = 150;
    public static final int CONFIRMATION_CODE_LENGTH = 6;
    /** Codes can be re-sent after this many seconds. */
    public static final int CODE_RESEND_COOLDOWN_SECONDS = 60;
    /** How long the Welcome screen shows before it advances on its own. */
    public static final long WELCOME_AUTO_ADVANCE_MS = 2500L;
    /** Avatar is resized to this square before upload. */
    public static final int AVATAR_TARGET_PX = 400;
    public static final int AVATAR_JPEG_QUALITY = 85;

    /**
     * When true, purely decorative UI elements (present in the design but not
     * backed by a real feature) are shown; tapping them surfaces a "Not
     * available" toast. When false, those elements are hidden entirely.
     */
    public static final boolean DECORATIVE_UI = true;

    /** When false, hide Facebook button + OR divider on Login and Sign-up. */
    public static final boolean SHOW_FACEBOOK_BUTTON = true;

    /** Debug-only switch for entering the main UI while authentication is being developed. */
    public static final boolean BYPASS_LOGIN_FOR_DEVELOPMENT = false;

    public static final long POLLING_BADGES_MS = 30000L;
    public static final int PAGE_SIZE_FEED = 10;
    public static final int PAGE_SIZE_EXPLORE = 21;
    public static final int MAX_CAROUSEL_IMAGES = 10;
    public static final long USERNAME_DEBOUNCE_MS = 500L;

    /** true when the profile username is still the wizard's placeholder. */
    public static boolean isTemporaryUsername(String username) {
        if (username == null || username.isEmpty()) return true;
        return username.matches(TEMP_USERNAME_PATTERN)
                || username.matches(TEMP_USERNAME_PATTERN_LEGACY);
    }
}
