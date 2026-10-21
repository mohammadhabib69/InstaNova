package com.insta.nova.calling;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Lets the (process-wide) call state machine close UI that lives in another
 * Activity instance.
 *
 * <p>Needed because the terminal status can arrive over Realtime while this
 * device has nothing in the foreground — for example the callee was backgrounded
 * and the caller cancelled. {@code IncomingCallActivity} registers itself here
 * on create and the manager pokes it from the main thread, so the incoming UI
 * disappears immediately instead of leaving a ringing screen behind a
 * notification the user never dismissed.
 *
 * <p>References are weak and pruned on every dispatch: a leaked reference here
 * would pin a destroyed Activity for the life of the process.
 */
public final class CallActivityBridge {

    private static final String TAG = "CallActivityBridge";

    /** Incoming screens that may need closing, newest last. */
    private static final List<WeakReference<IncomingCallActivity>> INCOMING = new ArrayList<>();

    /** Optional hook for the connected call screens, which are not registered. */
    @Nullable
    private static volatile Runnable remoteEndHook;

    private CallActivityBridge() {
    }

    public static void registerIncoming(@NonNull IncomingCallActivity activity) {
        synchronized (INCOMING) {
            prune();
            INCOMING.add(new WeakReference<>(activity));
        }
    }

    public static void unregisterIncoming(@NonNull IncomingCallActivity activity) {
        synchronized (INCOMING) {
            Iterator<WeakReference<IncomingCallActivity>> it = INCOMING.iterator();
            while (it.hasNext()) {
                IncomingCallActivity a = it.next().get();
                if (a == null || a == activity) it.remove();
            }
        }
    }

    /**
     * Closes any incoming-call UI for {@code record} that is still showing.
     *
     * <p>Called from {@link CallManager} when a terminal row arrives for a call
     * this device never entered.
     */
    public static void closeIncomingUi(@NonNull Context context, @NonNull CallRecord record) {
        Runnable hook = remoteEndHook;
        if (hook != null) {
            try {
                hook.run();
            } catch (Exception e) {
                Log.w(TAG, "remote end hook threw: " + e.getMessage());
            }
        }

        List<IncomingCallActivity> alive = new ArrayList<>();
        synchronized (INCOMING) {
            prune();
            for (WeakReference<IncomingCallActivity> ref : INCOMING) {
                IncomingCallActivity a = ref.get();
                if (a == null) continue;
                String showing = a.getShowingCallId();
                // A null call id means the screen has not bound yet; it will
                // read the terminal status itself on resume.
                if (showing == null || showing.equals(record.id)) alive.add(a);
            }
        }

        for (IncomingCallActivity a : alive) {
            try {
                a.finishForRemoteEnd(record.status);
            } catch (Exception e) {
                Log.w(TAG, "could not close incoming UI: " + e.getMessage());
            }
        }

        // A call that ended without ever being shown still needs its ringing
        // notification pulled down.
        try {
            CallNotificationHelper.cancelIncomingCallNotification(context);
        } catch (Exception ignored) {
        }
    }

    public static void setRemoteEndHook(@Nullable Runnable hook) {
        remoteEndHook = hook;
    }

    private static void prune() {
        Iterator<WeakReference<IncomingCallActivity>> it = INCOMING.iterator();
        while (it.hasNext()) {
            Activity a = it.next().get();
            if (a == null || a.isFinishing() || a.isDestroyed()) it.remove();
        }
    }
}