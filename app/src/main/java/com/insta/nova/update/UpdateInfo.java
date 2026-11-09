package com.insta.nova.update;

import java.util.Collections;
import java.util.List;

/**
 * One published release, exactly as described by {@code latest.json} on GitHub Releases.
 *
 * <p>Nothing here is invented at runtime: every field comes from the manifest that the
 * release pipeline publishes next to the APK, so the dialog can never show a version or
 * release note that does not correspond to a downloadable file.
 */
public final class UpdateInfo {

    /** Incremented for every release. Primary comparison key. */
    public final int versionCode;
    /** Human readable version shown in the dialog, e.g. {@code 1.1.0}. */
    public final String versionName;
    /** Direct HTTPS URL of the APK asset on GitHub Releases. */
    public final String apkUrl;
    /** Lower-case hex SHA-256 of the APK, or empty when the release did not publish one. */
    public final String sha256;
    /** APK size in bytes, or {@code 0} when unknown. */
    public final long sizeBytes;
    /** ISO-8601 publish time, or an empty string. */
    public final String publishedAt;
    /** "What's new" bullets. Never null; may be empty when the release published none. */
    public final List<String> releaseNotes;

    UpdateInfo(int versionCode, String versionName, String apkUrl, String sha256,
               long sizeBytes, String publishedAt, List<String> releaseNotes) {
        this.versionCode = versionCode;
        this.versionName = versionName;
        this.apkUrl = apkUrl;
        this.sha256 = sha256 == null ? "" : sha256;
        this.sizeBytes = sizeBytes;
        this.publishedAt = publishedAt == null ? "" : publishedAt;
        this.releaseNotes = releaseNotes == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(releaseNotes);
    }

    /**
     * True when this release is newer than what is installed.
     *
     * <p>{@code versionCode} is authoritative because that is what Android itself uses to
     * decide whether an APK may replace the installed one. A manifest that only carries a
     * version name falls back to a dotted-numeric comparison so an older release channel
     * (before version codes were published) still works.
     */
    public boolean isNewerThan(int installedVersionCode, String installedVersionName) {
        if (versionCode > 0 && installedVersionCode > 0) {
            return versionCode > installedVersionCode;
        }
        return VersionName.compare(versionName, installedVersionName) > 0;
    }

    /** Version label used in the dialog and notifications. */
    public String displayVersion() {
        if (versionName != null && !versionName.trim().isEmpty()) {
            return versionName.trim();
        }
        return versionCode > 0 ? String.valueOf(versionCode) : "";
    }

    /** {@code "1.1.0 (2)"} — version name plus code, so support can match an APK. */
    public String displayVersionWithCode() {
        String name = displayVersion();
        if (versionCode <= 0) return name;
        return name.isEmpty() ? String.valueOf(versionCode) : name + " (" + versionCode + ")";
    }
}
