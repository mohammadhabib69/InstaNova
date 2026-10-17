package com.insta.nova;

import android.app.Application;
import android.content.Context;
import android.graphics.Typeface;

/**
 * Custom Application class initializing app-wide resources and fonts.
 */
public class InstaApp extends Application {

    private static InstaApp instance;
    private static Typeface wordmarkTypeface;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        try {
            com.google.firebase.FirebaseApp.initializeApp(this);
        } catch (Exception ignored) {}
        // Calling system: notification channel, incoming call listener, and FCM registration
        com.insta.nova.calling.CallNotificationHelper.createNotificationChannel(this);
        com.insta.nova.calling.CallManager.getInstance(this).startIncomingCallListener();
        com.insta.nova.calling.InstaFirebaseMessagingService.registerTokenWithBackend(this);

        // PAGE 17: presence heartbeat — touch_active() on start and every 60 s while
        // the app is in the foreground. No-ops if the RPC is missing.
        com.insta.nova.data.repository.ChatRepository.startPresenceHeartbeat(this);
    }

    public static InstaApp getInstance() {
        return instance;
    }

    /**
     * Returns the InstaNova wordmark typeface.
     * Falls back to sans-serif-medium if custom font asset is not bundled.
     */
    public static synchronized Typeface getWordmarkTypeface(Context context) {
        if (wordmarkTypeface == null) {
            try {
                wordmarkTypeface = Typeface.createFromAsset(context.getAssets(), "fonts/wordmark.ttf");
            } catch (Exception ignored) {
                wordmarkTypeface = Typeface.create("sans-serif-medium", Typeface.NORMAL);
            }
        }
        return wordmarkTypeface;
    }
}
