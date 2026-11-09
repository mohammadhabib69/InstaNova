package com.insta.nova.update;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.insta.nova.BuildConfig;
import com.insta.nova.R;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Collections;

/**
 * Single owner of the update lifecycle: one check per app launch, one download at a time,
 * and the install hand-off.
 *
 * <p>Runs entirely outside the Firebase/Supabase data paths — it only reads a static JSON
 * file from GitHub Releases and writes to its own SharedPreferences entry — so it cannot
 * affect authentication, the realtime database, Firestore, storage or RLS.
 *
 * <p>Nothing here is user-blocking. "Check on every launch" means the check is fired
 * once per process when a real screen resumes; if it fails (offline, no release yet) the
 * state simply returns to idle and the app continues with its normal startup.
 */
public final class UpdateManager {

    private static final String TAG = "UpdateManager";

    public enum State {
        /** No update known, or the check failed. No UI. */
        IDLE,
        CHECKING,
        /** A newer release is published; the dialog is/was shown. */
        UPDATE_AVAILABLE,
        DOWNLOADING,
        VERIFYING,
        /** Verified APK on disk, waiting for the installer. */
        READY,
        /** Install intent handed to the platform. */
        INSTALLING,
        FAILED
    }

    public interface Listener {
        /** Always called on the main thread. */
        void onUpdateState(State state, int progress, String message);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile UpdateManager instance;

    public static UpdateManager get(Context context) {
        UpdateManager local = instance;
        if (local == null) {
            synchronized (UpdateManager.class) {
                local = instance;
                if (local == null) {
                    local = new UpdateManager(context.getApplicationContext());
                    instance = local;
                }
            }
        }
        return local;
    }

    static void postToMain(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            MAIN.post(runnable);
        }
    }

    private final Context app;
    private final UpdateStore store;
    private final UpdateChecker checker = new UpdateChecker();
    private WeakReference<Activity> host = new WeakReference<>(null);

    private Listener listener;
    private UpdateInfo info;
    private State state = State.IDLE;
    private int progress;
    private String message;

    /** One automatic check per process, matching "check every time the app opens". */
    private boolean launchCheckStarted;
    private boolean dialogShowing;
    private boolean dismissedThisSession;
    private boolean awaitingInstallPermission;

    private UpdateManager(Context app) {
        this.app = app;
        this.store = new UpdateStore(app);
    }

    /* ------------------------------------------------------------------ */
    /* Lifecycle                                                           */
    /* ------------------------------------------------------------------ */

    /**
     * Called from {@code InstaApp}'s activity lifecycle callback. Also the point where a
     * "grant install permission, then come back" round trip is completed.
     */
    public void onActivityResumed(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        if (activity instanceof UpdateInstallActivity) return;
        host = new WeakReference<>(activity);

        if (awaitingInstallPermission) {
            if (UpdateInstaller.canInstall(activity)) {
                awaitingInstallPermission = false;
                installPending(activity);
            }
            return;
        }

        if (!launchCheckStarted) {
            launchCheckStarted = true;
            if (UpdateDownloadService.isRunning()) {
                setState(State.DOWNLOADING, progress, null);
                return;
            }
            File pending = store.pendingApk();
            if (pending != null && store.pendingVersionCode() > BuildConfig.VERSION_CODE) {
                // A verified APK from an earlier session never reached the installer.
                info = restoredInfo();
                setState(State.READY, 100, null);
                presentDialog();
                return;
            }
            check();
            return;
        }

        if (state == State.UPDATE_AVAILABLE && !dismissedThisSession) {
            presentDialog();
        }
    }

    /* ------------------------------------------------------------------ */
    /* Check                                                               */
    /* ------------------------------------------------------------------ */

    public void check() {
        if (checker.isInFlight()) return;
        setState(State.CHECKING, 0, null);
        checker.check(result -> {
            store.markChecked();
            switch (result.status) {
                case UPDATE_AVAILABLE:
                    info = result.info;
                    dismissedThisSession = false;
                    setState(State.UPDATE_AVAILABLE, 0, null);
                    presentDialog();
                    break;
                case UP_TO_DATE:
                    info = null;
                    setState(State.IDLE, 0, null);
                    break;
                case FAILED:
                default:
                    // Offline, rate-limited or no release published yet: stay silent.
                    Log.i(TAG, "no update information: " + result.message);
                    setState(State.IDLE, 0, null);
                    break;
            }
        });
    }

    /* ------------------------------------------------------------------ */
    /* Download / install                                                  */
    /* ------------------------------------------------------------------ */

    /** User tapped "Update Now". Safe to call again after a failure (retry). */
    public void startDownload() {
        if (state == State.DOWNLOADING || state == State.VERIFYING) return;
        if (info == null) {
            File pending = store.pendingApk();
            Activity activity = host.get();
            if (pending != null && activity != null) {
                installPending(activity);
            }
            return;
        }
        setState(State.DOWNLOADING, 0, null);
        UpdateDownloadService.start(app, info);
    }

    public void cancelDownload() {
        if (state != State.DOWNLOADING && state != State.VERIFYING) return;
        UpdateDownloadService.cancel(app);
    }

    /** Verified download finished; open the system installer when a screen is available. */
    void onDownloadComplete(File apk) {
        if (apk != null) {
            UpdateInfo current = info;
            if (current != null) store.setPendingApk(apk, current);
        }
        setState(State.READY, 100, null);
        Activity activity = host.get();
        if (activity != null) {
            installPending(activity);
        }
    }

    void onDownloadProgress(int percent) {
        if (state != State.DOWNLOADING) setStateSilently(State.DOWNLOADING);
        progress = percent;
        notifyListener();
    }

    void onVerifying() {
        setState(State.VERIFYING, 100, null);
    }

    void onDownloadFailed(String reason) {
        setState(State.FAILED, 0, TextUtils.isEmpty(reason)
                ? app.getString(R.string.update_failed_body) : reason);
    }

    void onDownloadCancelled() {
        UpdateNotifications.cancel(app);
        setState(State.UPDATE_AVAILABLE, 0, app.getString(R.string.update_cancelled));
    }

    /** Hands the stored APK to {@link UpdateInstaller}, asking for permission if needed. */
    public void installPending(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        File apk = store.pendingApk();
        if (apk == null) {
            setState(State.FAILED, 0, app.getString(R.string.update_failed_body));
            return;
        }
        if (!UpdateInstaller.canInstall(activity)) {
            awaitingInstallPermission = true;
            setState(State.READY, 100, app.getString(R.string.update_permission_status));
            UpdateInstaller.openInstallPermissionSettings(activity);
            return;
        }
        if (UpdateInstaller.install(activity, apk)) {
            setState(State.INSTALLING, 100, null);
        } else {
            setState(State.FAILED, 0, app.getString(R.string.update_install_failed));
        }
    }

    /* ------------------------------------------------------------------ */
    /* Listener plumbing                                                   */
    /* ------------------------------------------------------------------ */

    public void bind(Listener listener) {
        this.listener = listener;
        if (listener != null) {
            listener.onUpdateState(state, progress, message);
        }
    }

    public void unbind(Listener listener) {
        if (this.listener == listener) {
            this.listener = null;
        }
    }

    void onDialogDismissed() {
        dialogShowing = false;
        dismissedThisSession = true;
    }

    public UpdateInfo getInfo() {
        return info;
    }

    public State getState() {
        return state;
    }

    public int getProgress() {
        return progress;
    }

    public String getMessage() {
        return message;
    }

    public boolean isBusy() {
        return state == State.DOWNLOADING || state == State.VERIFYING;
    }

    /* ------------------------------------------------------------------ */
    /* Internals                                                           */
    /* ------------------------------------------------------------------ */

    private void presentDialog() {
        Activity activity = host.get();
        if (activity == null || activity.isFinishing() || dialogShowing) return;
        dialogShowing = true;
        UpdateDialog.show(activity, this);
    }

    private void setState(State next, int progressValue, String detail) {
        state = next;
        progress = progressValue;
        message = detail;
        notifyListener();
    }

    private void setStateSilently(State next) {
        state = next;
    }

    private void notifyListener() {
        final Listener target = listener;
        if (target == null) return;
        final State stateSnapshot = state;
        final int progressSnapshot = progress;
        final String messageSnapshot = message;
        postToMain(() -> target.onUpdateState(stateSnapshot, progressSnapshot, messageSnapshot));
    }

    private UpdateInfo restoredInfo() {
        return new UpdateInfo(
                store.pendingVersionCode(),
                store.pendingVersionName(),
                "",
                store.pendingSha256(),
                0L,
                "",
                Collections.emptyList());
    }
}
