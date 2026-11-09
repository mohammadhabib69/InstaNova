package com.insta.nova.update;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.io.File;

/**
 * Runs once in the freshly installed build, right after the update replaced this app.
 *
 * <p>Its only job is cleanup: the APK that was just installed and the "pending install"
 * record are no longer relevant, and leaving them behind would keep a stale, large file in
 * internal storage for the life of the install. Declared for
 * {@code ACTION_MY_PACKAGE_REPLACED} only, which is delivered to this app and nothing else.
 */
public class PackageReplacedReceiver extends BroadcastReceiver {

    private static final String TAG = "PackageReplaced";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            return;
        }
        try {
            new UpdateStore(context).clearPendingApk();
            UpdateNotifications.cancel(context);
            File dir = new File(context.getFilesDir(), "updates");
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (!file.delete()) {
                        Log.w(TAG, "could not delete " + file.getName());
                    }
                }
            }
            Log.i(TAG, "app replaced; update artefacts cleared");
        } catch (RuntimeException e) {
            Log.w(TAG, "cleanup failed: " + e);
        }
    }
}
