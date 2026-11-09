package com.insta.nova.update;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.util.Log;

/**
 * Starts the update check the first time a real screen is resumed in this process.
 *
 * <p>Registered once from {@code InstaApp}. The splash screen is skipped because it is
 * finishing within a couple of seconds and must not be blocked by a dialog; the very next
 * screen (Login or Home) carries the prompt, which is what makes every installed build —
 * signed in or not — see the update.
 */
public final class UpdateLaunchWatcher implements Application.ActivityLifecycleCallbacks {

    private static final String TAG = "UpdateLaunchWatcher";

    @Override
    public void onActivityResumed(Activity activity) {
        if (activity == null) return;
        if (activity instanceof com.insta.nova.ui.auth.SplashActivity) return;
        if (activity instanceof UpdateInstallActivity) return;
        try {
            UpdateManager.get(activity).onActivityResumed(activity);
        } catch (RuntimeException e) {
            // An update check must never be the reason a screen fails to work.
            Log.w(TAG, "update resume hook failed: " + e);
        }
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityStarted(Activity activity) {
    }

    @Override
    public void onActivityPaused(Activity activity) {
    }

    @Override
    public void onActivityStopped(Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
    }
}
