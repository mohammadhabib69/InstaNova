package com.insta.nova.update;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import com.insta.nova.BuildConfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Integrity and identity checks run on a downloaded APK before any install intent exists.
 *
 * <p>Three independent checks, in this order:
 * <ol>
 *   <li><b>Checksum</b> — SHA-256 of the file against the value published with the release.</li>
 *   <li><b>Package identity</b> — the archive's {@code packageName} must be this app, and its
 *       version code must be the one the manifest advertised (and newer than what is
 *       installed).</li>
 *   <li><b>Signing certificate</b> — the archive must be signed by the same certificate as
 *       the installed app. Without this a tampered APK with the right checksum in a
 *       hijacked manifest would still be offered to the installer.</li>
 * </ol>
 * A failure yields a user-safe reason; the technical detail goes to logcat only.
 */
public final class PackageVerifier {

    private static final String TAG = "PackageVerifier";
    private static final int BUFFER = 64 * 1024;

    private PackageVerifier() {
    }

    public static final class Result {
        public final boolean ok;
        /** User-facing explanation when {@link #ok} is false, otherwise {@code null}. */
        public final String reason;
        public final String detail;

        private Result(boolean ok, String reason, String detail) {
            this.ok = ok;
            this.reason = reason;
            this.detail = detail;
        }

        static Result pass() {
            return new Result(true, null, null);
        }

        static Result fail(String reason, String detail) {
            Log.w(TAG, "verification failed: " + reason + " (" + detail + ")");
            return new Result(false, reason, detail);
        }
    }

    public static Result verify(Context context, File apk, UpdateInfo info) {
        if (apk == null || !apk.isFile() || apk.length() == 0L) {
            return Result.fail("The downloaded file is empty.", "missing file");
        }
        if (info != null && info.sizeBytes > 0 && apk.length() != info.sizeBytes) {
            return Result.fail("The download is incomplete. Please try again.",
                    "size " + apk.length() + " != " + info.sizeBytes);
        }

        if (info != null && !TextUtils.isEmpty(info.sha256)) {
            String actual;
            try {
                actual = sha256(apk);
            } catch (Exception e) {
                return Result.fail("Could not verify the download. Please try again.",
                        String.valueOf(e));
            }
            if (!info.sha256.equals(actual)) {
                return Result.fail("The downloaded file failed its integrity check.",
                        "sha256 " + actual + " != " + info.sha256);
            }
        } else {
            // The manifest did not publish a checksum. Identity and signature checks below
            // still apply, so this is a warning rather than a rejection.
            Log.i(TAG, "release published no sha256; relying on package and signature checks");
        }

        PackageManager pm = context.getPackageManager();
        PackageInfo archive = readArchive(pm, apk);
        if (archive == null) {
            return Result.fail("The downloaded file is not a valid app package.", "unreadable archive");
        }
        if (!BuildConfig.APPLICATION_ID.equals(archive.packageName)) {
            return Result.fail("This update belongs to a different app.",
                    "package " + archive.packageName);
        }

        long archiveVersion = longVersionCode(archive);
        if (info != null && info.versionCode > 0 && archiveVersion != info.versionCode) {
            return Result.fail("The downloaded file does not match the published version.",
                    "versionCode " + archiveVersion + " != " + info.versionCode);
        }
        long installedVersion = installedVersionCode(pm, context.getPackageName());
        if (archiveVersion <= installedVersion) {
            return Result.fail("This update is not newer than the installed version.",
                    "archive " + archiveVersion + " <= installed " + installedVersion);
        }

        Signature archiveCert = firstSignature(archive);
        Signature installedCert = firstSignature(readInstalled(pm, context.getPackageName()));
        if (archiveCert == null || installedCert == null) {
            return Result.fail("Could not confirm the update's signature.", "missing signature");
        }
        if (!MessageDigest.isEqual(archiveCert.toByteArray(), installedCert.toByteArray())) {
            return Result.fail("The update was not signed by the same developer.",
                    "signature mismatch");
        }
        return Result.pass();
    }

    /** Streaming SHA-256, lower-case hex. */
    public static String sha256(File file) throws Exception {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[BUFFER];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return toHex(digest.digest());
    }

    static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format(Locale.US, "%02x", b));
        }
        return builder.toString();
    }

    private static PackageInfo readArchive(PackageManager pm, File apk) {
        String path = apk.getAbsolutePath();
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                return pm.getPackageArchiveInfo(path,
                        PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES));
            }
            return pm.getPackageArchiveInfo(path, PackageManager.GET_SIGNATURES);
        } catch (RuntimeException e) {
            Log.w(TAG, "archive read failed: " + e);
            return null;
        }
    }

    private static PackageInfo readInstalled(PackageManager pm, String packageName) {
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                return pm.getPackageInfo(packageName,
                        PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES));
            }
            return pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private static long longVersionCode(PackageInfo info) {
        if (info == null) return 0L;
        if (Build.VERSION.SDK_INT >= 28) return info.getLongVersionCode();
        return info.versionCode;
    }

    private static long installedVersionCode(PackageManager pm, String packageName) {
        return longVersionCode(readInstalled(pm, packageName));
    }

    @SuppressWarnings("deprecation")
    private static Signature firstSignature(PackageInfo info) {
        if (info == null) return null;
        if (Build.VERSION.SDK_INT >= 28 && info.signingInfo != null) {
            Signature[] signers = info.signingInfo.hasMultipleSigners()
                    ? info.signingInfo.getApkContentsSigners()
                    : info.signingInfo.getSigningCertificateHistory();
            if (signers != null && signers.length > 0) return signers[0];
        }
        Signature[] signatures = info.signatures;
        if (signatures != null && signatures.length > 0) return signatures[0];
        return null;
    }
}
