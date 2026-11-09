package com.insta.nova.update;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.io.File;

/**
 * Durable state for the update flow: the APK that was downloaded and verified, and the
 * release it belongs to.
 *
 * <p>Persisted because the flow spans process boundaries: a download can finish, the
 * installer can send the user to system settings for "install unknown apps", and the app
 * can be recreated before the install intent is finally launched. Keeping the path in
 * SharedPreferences means that resume works instead of silently losing the file.
 */
public final class UpdateStore {

    private static final String PREFS = "insta_update";
    private static final String KEY_APK_PATH = "pending_apk_path";
    private static final String KEY_APK_VERSION_CODE = "pending_apk_version_code";
    private static final String KEY_APK_VERSION_NAME = "pending_apk_version_name";
    private static final String KEY_APK_SHA256 = "pending_apk_sha256";
    private static final String KEY_LAST_CHECK_AT = "last_check_at";

    private final SharedPreferences prefs;

    public UpdateStore(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Records a verified, ready-to-install APK. */
    public void setPendingApk(File apk, UpdateInfo info) {
        if (apk == null || !apk.exists()) return;
        prefs.edit()
                .putString(KEY_APK_PATH, apk.getAbsolutePath())
                .putInt(KEY_APK_VERSION_CODE, info == null ? 0 : info.versionCode)
                .putString(KEY_APK_VERSION_NAME, info == null ? "" : info.displayVersion())
                .putString(KEY_APK_SHA256, info == null ? "" : info.sha256)
                .apply();
    }

    /** @return the APK awaiting installation, or {@code null} when there is none on disk. */
    public File pendingApk() {
        String path = prefs.getString(KEY_APK_PATH, null);
        if (TextUtils.isEmpty(path)) return null;
        File file = new File(path);
        if (!file.isFile() || file.length() == 0L) {
            clearPendingApk();
            return null;
        }
        return file;
    }

    public int pendingVersionCode() {
        return prefs.getInt(KEY_APK_VERSION_CODE, 0);
    }

    public String pendingVersionName() {
        return prefs.getString(KEY_APK_VERSION_NAME, "");
    }

    public String pendingSha256() {
        return prefs.getString(KEY_APK_SHA256, "");
    }

    public void clearPendingApk() {
        prefs.edit()
                .remove(KEY_APK_PATH)
                .remove(KEY_APK_VERSION_CODE)
                .remove(KEY_APK_VERSION_NAME)
                .remove(KEY_APK_SHA256)
                .apply();
    }

    public long lastCheckAt() {
        return prefs.getLong(KEY_LAST_CHECK_AT, 0L);
    }

    public void markChecked() {
        prefs.edit().putLong(KEY_LAST_CHECK_AT, System.currentTimeMillis()).apply();
    }
}
