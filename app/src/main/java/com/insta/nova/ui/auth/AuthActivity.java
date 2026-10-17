package com.insta.nova.ui.auth;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.fragment.NavHostFragment;

import com.insta.nova.R;

/**
 * Activity hosting the authentication flow (Login, Signup Wizard, Forgot Password).
 */
public class AuthActivity extends AppCompatActivity {

    /**
     * Opens the registration wizard at the Create-a-password step instead of Login.
     * Set by {@link SplashActivity} when a Firebase account exists but the profile was
     * never finalised, so the user continues where they stopped.
     */
    public static final String EXTRA_RESUME_AT_PASSWORD = "com.insta.nova.extra.RESUME_AT_PASSWORD";

    /** Opens the wizard at the email-confirmation step (account exists, address not yet confirmed). */
    public static final String EXTRA_RESUME_AT_CODE = "com.insta.nova.extra.RESUME_AT_CODE";

    /** Opens the wizard at the welcome step. */
    public static final String EXTRA_RESUME_AT_WELCOME = "com.insta.nova.extra.RESUME_AT_WELCOME";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);

        // Only on a fresh start: a config change must not push a second destination.
        if (savedInstanceState == null && getIntent() != null) {
            NavHostFragment host = (NavHostFragment)
                    getSupportFragmentManager().findFragmentById(R.id.nav_host_auth);
            if (host != null) {
                if (getIntent().getBooleanExtra(EXTRA_RESUME_AT_PASSWORD, false)) {
                    host.getNavController().navigate(R.id.action_login_to_password);
                } else if (getIntent().getBooleanExtra(EXTRA_RESUME_AT_CODE, false)) {
                    host.getNavController().navigate(R.id.action_login_to_code);
                } else if (getIntent().getBooleanExtra(EXTRA_RESUME_AT_WELCOME, false)) {
                    host.getNavController().navigate(R.id.wizardWelcomeFragment);
                }
            }
        }
    }
}
