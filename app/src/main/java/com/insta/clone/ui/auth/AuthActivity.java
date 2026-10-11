package com.insta.clone.ui.auth;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.insta.clone.R;

/**
 * Activity hosting the authentication flow (Login, Signup Wizard, Forgot Password).
 */
public class AuthActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);
    }
}
