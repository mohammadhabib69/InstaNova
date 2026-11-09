package com.insta.nova.update;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.insta.nova.BuildConfig;
import com.insta.nova.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Foreground download of the release APK, with a native progress notification.
 *
 * <p>Runs as a {@code dataSync} foreground service so the transfer survives the user
 * leaving the dialog or switching apps; Android 14 requires that type to be declared in
 * the manifest and requested at {@code startForeground}. When the bytes are on disk the
 * file is verified by {@link PackageVerifier} and only then handed to
 * {@link UpdateManager}, so a truncated or tampered download can never reach the
 * installer.
 */
public class UpdateDownloadService extends Service {

    private static final String TAG = "UpdateDownloadService";

    public static final String ACTION_START = "com.insta.nova.update.action.START";
    public static final String ACTION_CANCEL = "com.insta.nova.update.action.CANCEL";

    private static final String EXTRA_URL = "url";
    private static final String EXTRA_SHA256 = "sha256";
    private static final String EXTRA_SIZE = "size";
    private static final String EXTRA_VERSION_CODE = "version_code";
    private static final String EXTRA_VERSION_NAME = "version_name";

    private static final int NOTIFICATION_ID = UpdateNotifications.ID_UPDATE;
    private static final long PROGRESS_INTERVAL_MS = 250L;
    private static final long MIN_FREE_SPACE_SLACK = 8L * 1024 * 1024;
    private static final int BUFFER = 64 * 1024;

    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ota-download");
        thread.setPriority(Thread.NORM_PRIORITY);
        return thread;
    });

    private OkHttpClient client;
    private volatile Call call;
    private volatile boolean cancelled;

    public static boolean isRunning() {
        return RUNNING.get();
    }

    /** Starts (or ignores, when already running) one download for {@code info}. */
    public static void start(Context context, UpdateInfo info) {
        if (info == null) return;
        Intent intent = new Intent(context, UpdateDownloadService.class)
                .setAction(ACTION_START)
                .putExtra(EXTRA_URL, info.apkUrl)
                .putExtra(EXTRA_SHA256, info.sha256)
                .putExtra(EXTRA_SIZE, info.sizeBytes)
                .putExtra(EXTRA_VERSION_CODE, info.versionCode)
                .putExtra(EXTRA_VERSION_NAME, info.displayVersion());
        try {
            ContextCompat.startForegroundService(context, intent);
        } catch (RuntimeException e) {
            Log.w(TAG, "could not start download service: " + e);
            UpdateManager.get(context).onDownloadFailed(
                    withDetail(context.getString(R.string.update_failed_body), e));
        }
    }

    public static void cancel(Context context) {
        try {
            context.startService(new Intent(context, UpdateDownloadService.class)
                    .setAction(ACTION_CANCEL));
        } catch (RuntimeException e) {
            Log.w(TAG, "cancel failed: " + e);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        UpdateNotifications.ensureChannel(this);
        client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_CANCEL.equals(action)) {
            cancelled = true;
            Call active = call;
            if (active != null) active.cancel();
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(action) || intent == null) {
            return START_NOT_STICKY;
        }
        if (!RUNNING.compareAndSet(false, true)) {
            Log.i(TAG, "download already running; ignoring duplicate start");
            return START_NOT_STICKY;
        }

        final String url = intent.getStringExtra(EXTRA_URL);
        final String sha256 = intent.getStringExtra(EXTRA_SHA256);
        final long size = intent.getLongExtra(EXTRA_SIZE, 0L);
        final int versionCode = intent.getIntExtra(EXTRA_VERSION_CODE, 0);
        final String versionName = intent.getStringExtra(EXTRA_VERSION_NAME);

        startForegroundCompat(versionName);
        // Release builds only ever download over HTTPS. Debug builds also accept the
        // local HTTP server used by the update-flow test harness.
        String scheme = url == null ? "" : url.toLowerCase(java.util.Locale.US);
        boolean secure = scheme.startsWith("https://");
        boolean debugInsecure = BuildConfig.DEBUG && scheme.startsWith("http://");
        if (!secure && !debugInsecure) {
            finishWithFailure(getString(R.string.update_failed_body));
            return START_NOT_STICKY;
        }

        executor.execute(() -> download(url, sha256, size, versionCode, versionName));
        return START_NOT_STICKY;
    }

    private void download(String url, String sha256, long size, int versionCode, String versionName) {
        File dir = new File(getFilesDir(), "updates");
        File target = new File(dir, "InstaNova-" + versionCode + ".apk");
        File part = new File(dir, target.getName() + ".part");
        try {
            if (!dir.isDirectory() && !dir.mkdirs()) {
                finishWithFailure(withDetail(getString(R.string.update_failed_body),
                        new IllegalStateException("mkdirs " + dir)));
                return;
            }
            clearDirectory(dir);

            if (size > 0 && dir.getUsableSpace() < size + MIN_FREE_SPACE_SLACK) {
                finishWithFailure(getString(R.string.update_no_space));
                return;
            }

            Request request = new Request.Builder()
                    .url(url)
                    .header("Accept", "application/octet-stream")
                    .header("User-Agent", "InstaNova/" + versionName)
                    .get()
                    .build();
            Call activeCall = client.newCall(request);
            call = activeCall;

            long total;
            long written = 0L;
            long lastUpdate = 0L;
            int lastPercent = -1;

            try (Response response = activeCall.execute()) {
                if (!response.isSuccessful()) {
                    finishWithFailure(getString(R.string.update_failed_body));
                    return;
                }
                ResponseBody body = response.body();
                if (body == null) {
                    finishWithFailure(getString(R.string.update_failed_body));
                    return;
                }
                total = body.contentLength() > 0 ? body.contentLength() : size;

                try (InputStream input = body.byteStream();
                     FileOutputStream output = new FileOutputStream(part)) {
                    byte[] buffer = new byte[BUFFER];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        if (cancelled) throw new InterruptedIOException("cancelled");
                        output.write(buffer, 0, read);
                        written += read;

                        int percent = total > 0
                                ? (int) Math.min(100L, written * 100L / total)
                                : -1;
                        long now = System.currentTimeMillis();
                        if (percent != lastPercent
                                && (now - lastUpdate >= PROGRESS_INTERVAL_MS || percent == 100)) {
                            lastPercent = percent;
                            lastUpdate = now;
                            publishProgress(percent, versionName);
                        }
                    }
                    output.flush();
                    output.getFD().sync();
                }
            }

            if (total > 0 && written != total) {
                part.delete();
                finishWithFailure(getString(R.string.update_failed_body));
                return;
            }

            publishProgress(100, versionName);
            UpdateManager.get(this).onVerifying();

            if (target.exists() && !target.delete()) {
                Log.w(TAG, "could not replace existing apk");
            }
            if (!part.renameTo(target)) {
                part.delete();
                finishWithFailure(getString(R.string.update_failed_body));
                return;
            }

            UpdateInfo info = new UpdateInfo(versionCode, versionName, url, sha256,
                    size, "", java.util.Collections.emptyList());
            PackageVerifier.Result verified = PackageVerifier.verify(this, target, info);
            if (!verified.ok) {
                target.delete();
                finishWithFailure(verified.reason == null
                        ? getString(R.string.update_verify_failed) : verified.reason);
                return;
            }

            new UpdateStore(this).setPendingApk(target, info);
            // Leave foreground mode BEFORE posting the final card: the service shares its
            // notification id with the progress card, and STOP_FOREGROUND_REMOVE would
            // delete the "ready to install" notification we just posted.
            stopForegroundQuietly();
            UpdateNotifications.showReady(this, target, info);
            UpdateManager.get(this).onDownloadComplete(target);
            finish();
        } catch (InterruptedIOException e) {
            part.delete();
            stopForegroundQuietly();
            UpdateNotifications.cancel(this);
            UpdateManager.get(this).onDownloadCancelled();
            finish();
        } catch (Exception e) {
            Log.w(TAG, "download failed: " + e);
            part.delete();
            finishWithFailure(withDetail(getString(R.string.update_failed_body), e));
        }
    }

    private void publishProgress(int percent, String versionName) {
        if (percent < 0) {
            UpdateNotifications.showProgress(this, 0, true, versionName);
            UpdateManager.get(this).onDownloadProgress(-1);
            return;
        }
        UpdateNotifications.showProgress(this, percent, false, versionName);
        UpdateManager.get(this).onDownloadProgress(percent);
    }

    private void startForegroundCompat(String versionName) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 14 rejects a foreground service whose declared type is not passed
                // here, so the type is explicit rather than left to the manifest default.
                startForeground(NOTIFICATION_ID,
                        UpdateNotifications.progress(this, 0, true, versionName),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NOTIFICATION_ID,
                        UpdateNotifications.progress(this, 0, true, versionName));
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "startForeground failed: " + e);
        }
    }

    private void stopForegroundQuietly() {
        try {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } catch (RuntimeException e) {
            Log.w(TAG, "stopForeground failed: " + e);
        }
    }

    private void finishWithFailure(String reason) {
        stopForegroundQuietly();
        UpdateNotifications.showFailed(this);
        UpdateManager.get(this).onDownloadFailed(reason);
        finish();
    }

    private void finish() {
        RUNNING.set(false);
        call = null;
        stopSelf();
    }

    /**
     * Debug builds append the technical cause to the user-facing sentence. Release builds
     * keep the plain, localized message; the detail still reaches logcat.
     */
    private static String withDetail(String message, Throwable throwable) {
        if (!BuildConfig.DEBUG || throwable == null) return message;
        String detail = throwable.getClass().getSimpleName()
                + (throwable.getMessage() == null ? "" : ": " + throwable.getMessage());
        return message + " [" + detail + "]";
    }

    /** Old APKs would otherwise accumulate in internal storage after every release. */
    private void clearDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (!file.delete()) {
                Log.w(TAG, "could not delete " + file.getName());
            }
        }
    }

    @Override
    public void onDestroy() {
        cancelled = true;
        Call active = call;
        if (active != null) active.cancel();
        executor.shutdownNow();
        RUNNING.set(false);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
