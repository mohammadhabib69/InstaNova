package com.insta.nova.data.firebase;

import android.content.Context;
import android.util.Log;

import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;

/**
 * Debug-variant App Check provider (task P0-10).
 *
 * <p>Debug builds cannot obtain a Play Integrity verdict — the APK is not Play-signed — so
 * they use the debug provider instead. It mints a token locally and prints it to logcat the
 * first time it runs:
 *
 * <pre>
 *   Enter this debug secret into the allow list in the Firebase Console for your project:
 *   &lt;uuid&gt;
 * </pre>
 *
 * <p>That uuid must be registered under App Check → Apps → Manage debug tokens before the
 * token verifies. Until it is, requests are attributed as unverified — which is harmless
 * while enforcement is OFF, and is exactly why enforcement must stay OFF until the token
 * is allow-listed.
 *
 * <p>This class exists per-variant so {@code DebugAppCheckProviderFactory} is never compiled
 * into a release APK.
 */
public final class AppCheckInstaller {

    private static final String TAG = "AppCheckInstaller";

    private AppCheckInstaller() {
    }

    public static void install(Context context) {
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance());
        Log.i(TAG, "App Check provider installed: debug");
    }
}
