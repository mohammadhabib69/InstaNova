package com.insta.nova.data.firebase;

import java.util.HashMap;
import java.util.Map;

/**
 * Defines Remote Config parameter keys and default values.
 */
public class ConfigDefaults {

    // Parameter Key: Indicates whether the beta feed layout is enabled
    public static final String KEY_BETA_FEED_ENABLED = "beta_feed_enabled";

    /**
     * Returns the default values for all Remote Config parameters.
     */
    public static Map<String, Object> getDefaults() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put(KEY_BETA_FEED_ENABLED, false); // Default: beta feed is disabled
        return defaults;
    }
}
