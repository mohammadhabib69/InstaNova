package com.insta.nova.data;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/**
 * Off / Auto / Always data saver.
 *
 * <p>Auto means: the active network is metered, or the system Data Saver restricts
 * background data. The state is a single LiveData so any screen can react without
 * polling; {@link #peekActive(Context)} is the cheap synchronous check used on the
 * image-loading hot path.
 */
public class NovaDataSaver {

    private static volatile NovaDataSaver instance;

    private final Context app;
    private final ConnectivityManager connectivity;
    private final MutableLiveData<Boolean> active = new MutableLiveData<>(false);
    private volatile boolean current;

    private NovaDataSaver(Context context) {
        app = context.getApplicationContext();
        connectivity = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
        NovaSettingsStore.get(app).settings().observeForever(settings -> refresh());
        registerNetworkCallback();
        refresh();
    }

    public static synchronized NovaDataSaver get(Context context) {
        if (instance == null) instance = new NovaDataSaver(context);
        return instance;
    }

    public LiveData<Boolean> active() {
        return active;
    }

    public boolean isActive() {
        return current;
    }

    /** Synchronous check for Glide/interceptors; never builds a new singleton. */
    public static boolean peekActive(Context context) {
        NovaDataSaver local = instance;
        if (local != null) return local.current;
        String mode = NovaSettingsStore.get(context).current().dataSaver;
        return compute(context, mode);
    }

    public void refresh() {
        String mode = NovaSettingsStore.get(app).current().dataSaver;
        boolean value = compute(app, mode);
        current = value;
        active.postValue(value);
    }

    private static boolean compute(Context context, String mode) {
        if ("always".equals(mode)) return true;
        if ("off".equals(mode) || mode == null) return false;
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        try {
            if (cm.isActiveNetworkMetered()) return true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                return cm.getRestrictBackgroundStatus() == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED;
            }
        } catch (RuntimeException ignored) {
            // Missing ACCESS_NETWORK_STATE or a vendor bug: treat as unmetered.
        }
        return false;
    }

    private void registerNetworkCallback() {
        if (connectivity == null) return;
        try {
            connectivity.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    refresh();
                }

                @Override
                public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
                    refresh();
                }

                @Override
                public void onLost(Network network) {
                    refresh();
                }
            });
        } catch (RuntimeException ignored) {
            // Some OEM builds reject the callback without permission; live refresh still works.
        }
    }
}
