package com.insta.nova.data.firebase;

import android.content.Context;
import android.util.Log;

import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;

/**
 * Release-variant App Check provider (task P0-10).
 *
 * <p>Play Integrity is registered for this app in the Firebase console. In a release build
 * the verdict comes from the Play Store, which is what makes the token meaningful: it is the
 * only provider a caller cannot forge on a rooted device or an emulator.
 *
 * <p>Enforcement stays OFF until the token round-trip has been observed in the console. With
 * enforcement off, a failed verdict changes nothing for the user — it only means the request
 * is not counted as verified.
 */
public final class AppCheckInstaller {

    private static final String TAG = "AppCheckInstaller";

    private AppCheckInstaller() {
    }

    public static void install(Context context) {
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance());
        Log.i(TAG, "App Check provider installed: play-integrity");
    }
}
