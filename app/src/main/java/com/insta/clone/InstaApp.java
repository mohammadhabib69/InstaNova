package com.insta.clone;

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
    }

    public static InstaApp getInstance() {
        return instance;
    }

    /**
     * Returns the cursive Instagram wordmark typeface.
     * Falls back to system cursive/serif italic if custom font asset is not bundled.
     */
    public static synchronized Typeface getWordmarkTypeface(Context context) {
        if (wordmarkTypeface == null) {
            try {
                wordmarkTypeface = Typeface.createFromAsset(context.getAssets(), "fonts/grand_hotel.ttf");
            } catch (Exception ignored) {
                try {
                    wordmarkTypeface = Typeface.create("cursive", Typeface.BOLD);
                } catch (Exception e) {
                    wordmarkTypeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC);
                }
            }
        }
        return wordmarkTypeface;
    }
}
