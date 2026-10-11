package com.insta.clone.util;

/**
 * Global constant parameters and configuration values.
 */
public final class Constants {

    private Constants() {}

    public static final String WORDMARK = "Instagram";
    public static final String FOOTER_FROM = "from";
    public static final String FOOTER_BRAND = "FACEBOOK";

    /** When false, hide Facebook button + OR divider on Login and Sign-up. */
    public static final boolean SHOW_FACEBOOK_BUTTON = true;

    /** Debug-only switch for entering the main UI while authentication is being developed. */
    public static final boolean BYPASS_LOGIN_FOR_DEVELOPMENT = false;

    public static final long POLLING_BADGES_MS = 30000L;
    public static final int PAGE_SIZE_FEED = 10;
    public static final int PAGE_SIZE_EXPLORE = 21;
    public static final int MAX_CAROUSEL_IMAGES = 10;
    public static final long USERNAME_DEBOUNCE_MS = 500L;
}
