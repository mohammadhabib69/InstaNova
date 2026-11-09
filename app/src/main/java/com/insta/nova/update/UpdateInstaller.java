package com.insta.nova.update;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.core.content.FileProvider;

import com.insta.nova.BuildConfig;

import java.io.File;

/**
 * Hands a verified APK to Android's own package installer. No custom install UI: the
 * platform sheet is the only thing the user interacts with, which is what keeps the flow
 * compatible with Play-protect-free distribution and with every OEM skin.
 */
public final class UpdateInstaller {

    private static final String TAG = "UpdateInstaller";
    private static final String MIME_APK = "application/vnd.android.package-archive";

    private UpdateInstaller() {
    }

    /**
     * True when this app may launch an APK install prompt. From API 26 the user grants
     * this per app in system settings; below that there is no gate.
     */
    public static boolean canInstall(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true;
        try {
            return context.getPackageManager().canRequestPackageInstalls();
        } catch (RuntimeException e) {
            Log.w(TAG, "canRequestPackageInstalls failed: " + e);
            return false;
        }
    }

    /** Opens "Install unknown apps" for this app. Falls back to the app details page. */
    public static void openInstallPermissionSettings(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData(Uri.parse("package:" + BuildConfig.APPLICATION_ID));
        try {
            activity.startActivity(intent);
            return;
        } catch (ActivityNotFoundException | SecurityException e) {
            Log.w(TAG, "unknown-sources settings unavailable: " + e);
        }
        try {
            activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:" + BuildConfig.APPLICATION_ID)));
        } catch (ActivityNotFoundException | SecurityException e) {
            Log.w(TAG, "app details settings unavailable: " + e);
        }
    }

    /**
     * Starts the native installer for {@code apk}.
     *
     * @return true when an installer activity accepted the intent.
     */
    public static boolean install(Activity activity, File apk) {
        if (activity == null || activity.isFinishing() || apk == null || !apk.isFile()) {
            return false;
        }
        if (!canInstall(activity)) {
            return false;
        }
        Uri uri;
        try {
            uri = FileProvider.getUriForFile(activity,
                    BuildConfig.APPLICATION_ID + ".fileprovider", apk);
        } catch (RuntimeException e) {
            Log.w(TAG, "FileProvider uri failed: " + e);
            return false;
        }

        // Android's package installer is addressed explicitly. Left implicit, a device
        // whose ROM also ships a vendor installer (and any third-party app that registers
        // for application/vnd.android.package-archive) shows an "Open with" chooser first,
        // which is not the native install sheet the update flow promises.
        for (String installerPackage : INSTALLER_PACKAGES) {
            Intent intent = buildInstallIntent(uri);
            intent.setPackage(installerPackage);
            try {
                activity.startActivity(intent);
                Log.i(TAG, "installer " + installerPackage + " launched for " + apk.getName());
                return true;
            } catch (ActivityNotFoundException | SecurityException e) {
                Log.i(TAG, "installer " + installerPackage + " unavailable: " + e);
            }
        }

        try {
            activity.startActivity(buildInstallIntent(uri));
            Log.i(TAG, "implicit installer launched for " + apk.getName());
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            Log.w(TAG, "installer intent rejected: " + e);
            return false;
        }
    }

    /** Package installers in the order Android devices ship them. */
    private static final String[] INSTALLER_PACKAGES = {
            "com.google.android.packageinstaller",
            "com.android.packageinstaller"
    };

    private static Intent buildInstallIntent(Uri uri) {
        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, MIME_APK)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // Without the clip data the URI grant is dropped on some OEM builds, which then
        // reports "There was a problem parsing the package" for a perfectly good APK.
        intent.setClipData(ClipData.newRawUri("apk", uri));
        return intent;
    }
}
