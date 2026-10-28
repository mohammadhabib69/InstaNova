package com.insta.nova.data.firebase;

import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.insta.nova.BuildConfig;

/**
 * Manages Firebase Remote Config fetching and access.
 */
public class RemoteConfigManager {

    private static RemoteConfigManager instance;
    private final FirebaseRemoteConfig remoteConfig;

    private RemoteConfigManager() {
        remoteConfig = FirebaseRemoteConfig.getInstance();

        // Configure fetch interval: 0s for debug builds, 12 hours for production
        long minimumFetchInterval = BuildConfig.DEBUG ? 0 : 43200;

        FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(minimumFetchInterval)
                .build();
        remoteConfig.setConfigSettingsAsync(configSettings);

        // Set default values
        remoteConfig.setDefaultsAsync(ConfigDefaults.getDefaults());
    }

    public static synchronized RemoteConfigManager getInstance() {
        if (instance == null) {
            instance = new RemoteConfigManager();
        }
        return instance;
    }

    /**
     * Fetches and activates Remote Config parameters asynchronously.
     */
    public void fetchAndActivate() {
        remoteConfig.fetchAndActivate().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                // Config successfully fetched and activated
            } else {
                // Fetch failed; app will continue using defaults or previously cached values
            }
        });
    }

    public boolean getBoolean(String key) {
        return remoteConfig.getBoolean(key);
    }

    public String getString(String key) {
        return remoteConfig.getString(key);
    }

    public long getLong(String key) {
        return remoteConfig.getLong(key);
    }
}
