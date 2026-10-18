package com.insta.nova.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.insta.nova.R;
import com.insta.nova.data.local.SessionManager;

/**
 * Shown when Firebase authentication succeeded but no {@code public.profiles} row exists
 * for this Firebase UID.
 *
 * <p>This is a real state, not an error to paper over: 20 legacy profiles exist whose
 * {@code firebase_uid} is NULL because they predate the Firebase migration. Guessing a
 * link from an email or username would attach a stranger's account to somebody else's
 * posts, so nothing is created or linked here. The screen explains the situation and
 * offers only safe exits: retry the lookup, or sign out.
 */
public class ProfileMissingActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_missing);

        Button retry = findViewById(R.id.btn_missing_retry);
        Button signOut = findViewById(R.id.btn_missing_sign_out);

        retry.setOnClickListener(v -> {
            // Re-run the whole gate: if a profile has since been created or linked, the
            // splash will route to Home instead of back here.
            startActivity(new Intent(this, SplashActivity.class));
            finish();
        });

        signOut.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            SessionManager.getInstance(getApplicationContext()).clearSession();
            startActivity(new Intent(this, AuthActivity.class));
            finish();
        });
    }

    @Override
    public void onBackPressed() {
        // Back must not drop the user into Home without an identity.
        moveTaskToBack(true);
    }
}
