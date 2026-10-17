package com.insta.nova.ui.auth;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.animation.DecelerateInterpolator;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.insta.nova.BuildConfig;
import com.insta.nova.R;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.remote.TokenClaims;
import com.insta.nova.data.repository.AuthRepository;
import com.insta.nova.ui.main.MainActivity;
import com.insta.nova.ui.widget.WordmarkView;
import com.insta.nova.util.Constants;
import com.insta.nova.util.Resource;

/**
 * Launcher splash. FirebaseAuth decides whether anybody is signed in; the local
 * session store only supplies non-sensitive display state and the
 * "signup in progress" flag.
 */
public class SplashActivity extends AppCompatActivity {

    private SessionManager sessionManager;
    private WordmarkView wordmarkView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        wordmarkView = findViewById(R.id.wordmarkView);

        if (!areAnimationsEnabled()) {
            if (wordmarkView != null) {
                wordmarkView.setAlpha(1.0f);
                wordmarkView.setRevealProgress(1.0f);
            }
            splashScreen.setOnExitAnimationListener(view -> {
                view.remove();
                dispatchRoute();
            });
            return;
        }

        if (wordmarkView != null) {
            wordmarkView.setAlpha(0.0f);
            wordmarkView.setRevealProgress(0.0f);
        }

        splashScreen.setOnExitAnimationListener(splashScreenView -> {
            splashScreenView.remove();

            if (wordmarkView == null) {
                dispatchRoute();
                return;
            }

            ValueAnimator animator = ValueAnimator.ofFloat(0.0f, 1.0f);
            animator.setDuration(700);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(animation -> {
                float val = (float) animation.getAnimatedValue();
                wordmarkView.setAlpha(val);
                wordmarkView.setRevealProgress(val);
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    dispatchRoute();
                }
            });
            animator.start();
        });
    }

    private boolean areAnimationsEnabled() {
        try {
            float durationScale = Settings.Global.getFloat(
                    getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1.0f);
            if (durationScale == 0.0f) {
                return false;
            }
        } catch (Exception ignored) {}
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return ValueAnimator.areAnimatorsEnabled();
        }
        return true;
    }

    private void dispatchRoute() {
        if (isFinishing() || isDestroyed()) return;

        if (BuildConfig.SUPABASE_URL == null || BuildConfig.SUPABASE_URL.trim().isEmpty()
                || BuildConfig.SUPABASE_ANON_KEY == null || BuildConfig.SUPABASE_ANON_KEY.trim().isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Supabase configuration required")
                    .setMessage("SUPABASE_URL and SUPABASE_ANON_KEY are empty. Copy local.properties.example to local.properties and fill them in.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        // Development-only bypass. Release builds always keep the normal auth gate.
        if (BuildConfig.DEBUG && Constants.BYPASS_LOGIN_FOR_DEVELOPMENT) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        sessionManager = SessionManager.getInstance(this);

        // Firebase owns the credential. Honour "Remember login info": when it was
        // unchecked, drop the persisted Firebase session so the next cold start opens Login.
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser != null && !sessionManager.isRememberLogin()) {
            FirebaseAuth.getInstance().signOut();
            sessionManager.clearSession();
            firebaseUser = null;
        }

        boolean hasUser = firebaseUser != null;
        boolean verified = hasUser && firebaseUser.isEmailVerified();
        boolean inProgress = sessionManager.isSignupInProgress();

        // A flag left behind by a session that no longer exists is stale: drop it so the
        // next sign-in is not routed into a wizard that has nothing to resume.
        if (SignupResumePolicy.isStale(hasUser, inProgress)) {
            sessionManager.setSignupInProgress(false);
            inProgress = false;
        }

        switch (SignupResumePolicy.decide(hasUser, verified, inProgress)) {
            case RESUME_PASSWORD:
                startActivity(new Intent(this, AuthActivity.class)
                        .putExtra(AuthActivity.EXTRA_RESUME_AT_PASSWORD, true));
                break;
            case RESUME_CODE:
                startActivity(new Intent(this, AuthActivity.class)
                        .putExtra(AuthActivity.EXTRA_RESUME_AT_CODE, true));
                break;
            case HOME:
                // Never enter a screen that needs an internal UUID until the profile row
                // for *this* Firebase user has been resolved, or a cache written for the
                // same UID exists. Previously this branch entered Home with profileId=null,
                // which left every identity-keyed screen querying with an empty id.
                startIdentityGate(firebaseUser);
                return;
            case LOGIN:
            default:
                startActivity(new Intent(this, AuthActivity.class));
                break;
        }

        finish();
    }

    /* ================================================================== */
    /* Identity gate                                                       */
    /* ================================================================== */

    /**
     * Resolves the Firebase user to exactly one Supabase profile before Home is started.
     * A cached UUID is reused only when it was written for the same Firebase UID, so an
     * account switch can never inherit the previous account's identity.
     */
    private void startIdentityGate(FirebaseUser user) {
        // Supabase chooses its Postgres role from the token's `role` claim. A token minted
        // before the account was provisioned still runs as `anon`, so every write would be
        // rejected by RLS with no useful explanation. Force one refresh; if the refresh
        // itself fails (offline) fall through and let the profile lookup decide.
        user.getIdToken(true).addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null
                    && !TokenClaims.hasAuthenticatedRole(task.getResult().getClaims())) {
                showClaimMissing();
                return;
            }
            continueIdentityGate(user);
        });
    }

    private void continueIdentityGate(FirebaseUser user) {
        boolean cacheOk = SessionIdentityPolicy.cacheMatches(
                sessionManager.getFirebaseUid(),
                sessionManager.getUserId(),
                user.getUid());

        if (SessionIdentityPolicy.decide(SessionIdentityPolicy.Lookup.UNKNOWN, cacheOk)
                == SessionIdentityPolicy.Route.HOME_WITH_CACHED_ID) {
            goHome(user);
            return;
        }
        resolveProfile(user, cacheOk);
    }

    private void resolveProfile(FirebaseUser user, boolean cacheOk) {
        AuthRepository repository = new AuthRepository(this);
        repository.fetchProfileByFirebaseUid(user.getUid()).observe(this, resource -> {
            if (resource == null || resource.status == Resource.Status.LOADING) {
                return;
            }

            SessionIdentityPolicy.Lookup lookup;
            if (resource.status == Resource.Status.SUCCESS) {
                lookup = resource.data == null
                        ? SessionIdentityPolicy.Lookup.MISSING
                        : SessionIdentityPolicy.Lookup.FOUND;
            } else {
                lookup = SessionIdentityPolicy.Lookup.ERROR;
            }

            SessionIdentityPolicy.Route route = SessionIdentityPolicy.decide(lookup, cacheOk);
            switch (route) {
                case HOME:
                    applyResolvedProfile(user, resource.data);
                    goHome(user);
                    break;
                case HOME_WITH_CACHED_ID:
                    // Offline, but a UUID verified for this UID on an earlier launch.
                    goHome(user);
                    break;
                case MISSING_PROFILE:
                    startActivity(new Intent(this, ProfileMissingActivity.class));
                    finish();
                    break;
                case RESOLVE:
                case RETRY:
                default:
                    showIdentityRetry(resource.message);
                    break;
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void applyResolvedProfile(FirebaseUser user, Object data) {
        if (!(data instanceof java.util.Map)) {
            return;
        }
        java.util.Map<String, Object> row = (java.util.Map<String, Object>) data;
        String profileId = stringValue(row.get("id"));
        if (profileId.isEmpty()) {
            return;
        }
        sessionManager.saveIdentity(
                user.getUid(),
                profileId,
                stringValue(row.get("username")),
                stringValue(row.get("avatar_url")));
        // The row exists, so any leftover "signup in progress" marker is stale.
        sessionManager.setSignupInProgress(false);
    }

    private void goHome(FirebaseUser user) {
        if (sessionManager.getFirebaseUid() == null) {
            sessionManager.saveIdentity(user.getUid(), sessionManager.getUserId(),
                    stringValue(user.getDisplayName()), sessionManager.getAvatarUrl());
        }
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    /**
     * The Firebase session is valid but the token has no {@code role} claim, so Supabase
     * will treat it as anonymous and reject writes. Nothing the client can do mints that
     * claim — it is provisioned server-side — so this offers retry (after provisioning)
     * or sign-out, and never bypasses RLS.
     */
    private void showClaimMissing() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.identity_claim_title)
                .setMessage(R.string.identity_claim_message)
                .setCancelable(false)
                .setPositiveButton(R.string.identity_retry_action, (dialog, which) -> {
                    FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
                    if (current == null) {
                        startActivity(new Intent(this, AuthActivity.class));
                        finish();
                        return;
                    }
                    startIdentityGate(current);
                })
                .setNegativeButton(R.string.identity_sign_out_action, (dialog, which) -> {
                    FirebaseAuth.getInstance().signOut();
                    sessionManager.clearSession();
                    startActivity(new Intent(this, AuthActivity.class));
                    finish();
                })
                .show();
    }

    /** Lookup failed and nothing trustworthy is cached: offer retry, never a fake id. */
    private void showIdentityRetry(String message) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.identity_retry_title)
                .setMessage(message == null || message.trim().isEmpty()
                        ? getString(R.string.identity_retry_message)
                        : message)
                .setCancelable(false)
                .setPositiveButton(R.string.identity_retry_action, (dialog, which) -> {
                    FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
                    if (current == null) {
                        startActivity(new Intent(this, AuthActivity.class));
                        finish();
                        return;
                    }
                    resolveProfile(current, false);
                })
                .setNegativeButton(R.string.identity_sign_out_action, (dialog, which) -> {
                    FirebaseAuth.getInstance().signOut();
                    sessionManager.clearSession();
                    startActivity(new Intent(this, AuthActivity.class));
                    finish();
                })
                .show();
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
