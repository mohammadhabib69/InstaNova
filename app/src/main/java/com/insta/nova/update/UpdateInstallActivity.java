package com.insta.nova.update;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import java.io.File;

/**
 * Invisible trampoline behind the "Update ready to install" notification.
 *
 * <p>Android 10+ forbids starting an activity from the background, so a completed download
 * cannot open the package installer directly if the app is not in front. A notification
 * tap is a user action, which makes this one-shot activity the legal place to raise the
 * installer intent. It shows nothing and finishes immediately.
 */
public class UpdateInstallActivity extends Activity {

    public static final String EXTRA_APK_PATH = "apk_path";
    public static final String EXTRA_VERSION_CODE = "version_code";
    public static final String EXTRA_VERSION_NAME = "version_name";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent source = getIntent();
        String path = source == null ? null : source.getStringExtra(EXTRA_APK_PATH);
        if (!TextUtils.isEmpty(path)) {
            File apk = new File(path);
            if (apk.isFile()) {
                UpdateInstaller.install(this, apk);
            }
        }
        finish();
        overridePendingTransition(0, 0);
    }
}
