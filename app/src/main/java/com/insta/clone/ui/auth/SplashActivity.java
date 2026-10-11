package com.insta.clone.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;
import androidx.appcompat.app.AlertDialog;
import com.insta.clone.BuildConfig;
import com.insta.clone.data.local.SessionManager;
import com.insta.clone.ui.main.MainActivity;

/**
 * Launcher SplashActivity checking active login credentials before routing.
 */
public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Install AndroidX SplashScreen
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);

        if (BuildConfig.SUPABASE_URL == null || BuildConfig.SUPABASE_URL.trim().isEmpty()
                || BuildConfig.SUPABASE_ANON_KEY == null || BuildConfig.SUPABASE_ANON_KEY.trim().isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Supabase configuration required")
                    .setMessage("SUPABASE_URL and SUPABASE_ANON_KEY are empty. Copy local.properties.example to local.properties and fill them in.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        SessionManager sessionManager = SessionManager.getInstance(this);

        if (sessionManager.isLoggedIn()) {
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
        } else {
            Intent intent = new Intent(this, AuthActivity.class);
            startActivity(intent);
        }

        finish();
    }
}
