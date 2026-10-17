package com.insta.nova.data.repository;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GetTokenResult;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.insta.nova.BuildConfig;
import com.insta.nova.R;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.remote.FirebaseTokens;
import com.insta.nova.data.remote.ProfileColumns;
import com.insta.nova.data.remote.RetrofitClient;
import com.insta.nova.data.remote.RoleClaimPolicy;
import com.insta.nova.data.remote.TokenClaims;
import com.insta.nova.data.remote.api.RestApi;
import com.insta.nova.data.remote.api.StorageApi;
import com.insta.nova.data.remote.dto.ProfileDto;
import com.insta.nova.data.remote.dto.RpcEmailRequest;
import com.insta.nova.data.remote.dto.TokenResponse;
import com.insta.nova.util.Resource;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Firebase-only authentication repository.
 *
 * <p>FirebaseAuth owns credentials, sessions, password resets, password changes and
 * email verification. Supabase is only a data backend: it is reached with the current
 * Firebase ID token (see {@link FirebaseTokens} / {@code AuthInterceptor}) and never
 * through any Supabase Auth endpoint.
 *
 * <p>A Firebase user has no {@code auth.users} row, so the old {@code handle_new_user}
 * trigger never runs for them. This class writes {@code profiles} explicitly, with
 * {@code firebase_uid} set and a UUID of its own for the primary key.
 */
public class AuthRepository {

    private static final String TAG = "InstaApi";

    /** Number of hex characters used by the wizard's placeholder username. */
    private static final int TEMP_USERNAME_DIGITS = 10;

    /** Minimal callback so internal chains never need observeForever (which leaks). */
    private interface Cb<T> {
        void on(Resource<T> resource);
    }

    private final FirebaseAuth firebaseAuth;
    private final RestApi restApi;
    private final StorageApi storageApi;
    private final SessionManager sessionManager;
    private final Context appContext;

    public AuthRepository(Context context) {
        Context app = context.getApplicationContext();
        this.appContext = app;
        RetrofitClient client = RetrofitClient.getInstance(app);
        restApi = client.getRestApi();
        storageApi = client.getStorageApi();
        sessionManager = SessionManager.getInstance(app);
        firebaseAuth = FirebaseAuth.getInstance();
    }

    /* ================================================================== */
    /* Login                                                               */
    /* ================================================================== */

    public LiveData<Resource<TokenResponse>> login(String identifier, String password) {
        MutableLiveData<Resource<TokenResponse>> result = new MutableLiveData<>();
        result.postValue(Resource.loading());
        String value = identifier == null ? "" : identifier.trim();
        if (value.isEmpty() || password == null || password.isEmpty()) {
            result.postValue(Resource.error("Please enter your username or email and password."));
            return result;
        }
        if (value.contains("@")) {
            signInEmail(value, password, result);
            return result;
        }
        // Username login: resolve the address through the existing read-only RPC.
        restApi.getEmailByUsername(new RpcEmailRequest(value)).enqueue(new Callback<JsonElement>() {
            @Override public void onResponse(@NonNull Call<JsonElement> call, @NonNull Response<JsonElement> response) {
                String email = response.body() == null || response.body().isJsonNull()
                        ? "" : response.body().getAsString();
                if (!response.isSuccessful() || email.trim().isEmpty()) {
                    result.postValue(Resource.error("The username you entered doesn't belong to an account. Please check your username and try again."));
                    return;
                }
                signInEmail(email.trim(), password, result);
            }

            @Override public void onFailure(@NonNull Call<JsonElement> call, @NonNull Throwable t) {
                result.postValue(Resource.error("Unable to look up that username: " + safe(t)));
            }
        });
        return result;
    }

    private void signInEmail(String email, String password, MutableLiveData<Resource<TokenResponse>> result) {
        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        result.postValue(Resource.error(friendlyAuthError(task.getException())));
                        return;
                    }
                    FirebaseUser user = firebaseAuth.getCurrentUser();
                    if (user == null) {
                        result.postValue(Resource.error("Sign-in succeeded but no Firebase user is available."));
                        return;
                    }
                    // Firebase allows an unconfirmed address to sign in; this product does not.
                    if (!user.isEmailVerified()) {
                        user.sendEmailVerification();
                        firebaseAuth.signOut();
                        result.postValue(Resource.error(
                                "Please confirm your email address. We've sent you a new confirmation link — open it, then log in again."));
                        return;
                    }
                    refreshTokenThenContinue(user, result);
                });
    }

    /* ================================================================== */
    /* Password reset                                                      */
    /* ================================================================== */

    public LiveData<Resource<Void>> recoverPassword(String email) {
        MutableLiveData<Resource<Void>> result = new MutableLiveData<>();
        result.postValue(Resource.loading());
        String value = email == null ? "" : email.trim();
        if (value.isEmpty()) {
            result.postValue(Resource.error(appContext.getString(R.string.auth_email_invalid), null));
            return result;
        }
        firebaseAuth.sendPasswordResetEmail(value).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                result.postValue(Resource.success(null));
            } else {
                result.postValue(Resource.error(friendlyAuthError(task.getException()), null));
            }
        });
        return result;
    }

    /* ================================================================== */
    /* Signup — Firebase email verification                                */
    /* ================================================================== */

    /**
     * Creates the Firebase account for the wizard's email step and sends the
     * confirmation email.
     *
     * <p>The account is created with a random throwaway password which the
     * Create-a-password step replaces, so the address is reserved for this user while
     * they finish the wizard. On success the wizard is flagged as "in progress" so
     * Splash can resume it after a process death.
     */
    public LiveData<Resource<Void>> startEmailVerification(String email) {
        MutableLiveData<Resource<Void>> result = new MutableLiveData<>();
        result.postValue(Resource.loading());
        String value = email == null ? "" : email.trim();
        if (value.isEmpty()) {
            result.postValue(Resource.error(appContext.getString(R.string.auth_email_invalid), null));
            return result;
        }

        FirebaseUser existing = firebaseAuth.getCurrentUser();
        if (existing != null && value.equalsIgnoreCase(existing.getEmail())) {
            if (existing.isEmailVerified()) {
                result.postValue(Resource.error(appContext.getString(R.string.auth_already_registered_title), null));
                return result;
            }
            existing.sendEmailVerification().addOnCompleteListener(send -> {
                if (!send.isSuccessful()) {
                    result.postValue(Resource.error(friendlyAuthError(send.getException()), null));
                    return;
                }
                persistIdentity(existing, null);
                sessionManager.setSignupInProgress(true);
                result.postValue(Resource.success(null));
            });
            return result;
        }

        firebaseAuth.createUserWithEmailAndPassword(value, randomPassword())
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        result.postValue(Resource.error(friendlyAuthError(task.getException()), null));
                        return;
                    }
                    FirebaseUser user = firebaseAuth.getCurrentUser();
                    if (user == null) {
                        result.postValue(Resource.error("Account was created but no Firebase user is available.", null));
                        return;
                    }
                    user.sendEmailVerification().addOnCompleteListener(send -> {
                        if (!send.isSuccessful()) {
                            result.postValue(Resource.error(friendlyAuthError(send.getException()), null));
                            return;
                        }
                        persistIdentity(user, null);
                        sessionManager.setSignupInProgress(true);
                        result.postValue(Resource.success(null));
                    });
                });
        return result;
    }

    /**
     * Re-checks the confirmation state of the signed-in Firebase user; {@code true}
     * once the address is confirmed.
     */
    public LiveData<Resource<Boolean>> checkEmailVerified() {
        MutableLiveData<Resource<Boolean>> result = new MutableLiveData<>();
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            result.postValue(Resource.error("Your session expired. Go back and re-enter your email address.", false));
            return result;
        }
        user.reload().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                result.postValue(Resource.error(friendlyAuthError(task.getException()), false));
                return;
            }
            FirebaseUser refreshed = firebaseAuth.getCurrentUser();
            boolean verified = refreshed != null && refreshed.isEmailVerified();
            if (!verified || refreshed == null) {
                result.postValue(Resource.success(false));
                return;
            }
            persistIdentity(refreshed, null);
            sessionManager.setSignupInProgress(true);
            // A cached ID token can predate the account's current custom claims, so the
            // first Supabase write must not go out on it. Mint a fresh token here, once,
            // and only then report the verification as complete.
            refreshTokenAfterVerification(refreshed, result);
        });
        return result;
    }

    /**
     * Forces one ID-token refresh the moment the address is confirmed.
     *
     * <p>The wizard's first Supabase write is the profile insert on the Terms step.
     * Supabase reads the {@code role} claim out of the token to choose a Postgres
     * role, and claims only appear in a newly minted token, so a token cached from
     * before the claim existed would run as {@code anon} and be rejected by RLS.
     *
     * <p>A failed refresh is reported instead of being swallowed: the caller must not
     * continue with a stale token. The token itself is never logged, stored or
     * returned — only whether the expected claim is present.
     */
    private void refreshTokenAfterVerification(FirebaseUser user,
                                               MutableLiveData<Resource<Boolean>> result) {
        user.getIdToken(true).addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Exception e = task.getException();
                Log.w(TAG, "Forced ID-token refresh after email verification failed: "
                        + (e == null ? "unknown" : e.getClass().getSimpleName()));
                result.postValue(Resource.error(
                        appContext.getString(R.string.auth_session_refresh_failed), false));
                return;
            }
            GetTokenResult token = task.getResult();
            if (token == null) {
                Log.w(TAG, "Forced ID-token refresh returned no token after email verification.");
                result.postValue(Resource.error(
                        appContext.getString(R.string.auth_session_refresh_failed), false));
                return;
            }
            if (TokenClaims.hasAuthenticatedRole(token.getClaims())) {
                Log.i(TAG, "Fresh Firebase ID token after verification carries role=authenticated.");
            } else {
                // Not fatal for the wizard: reads still work. Logged loudly because
                // protected writes will be rejected until the claim is provisioned.
                Log.w(TAG, "Fresh Firebase ID token has no role=\"authenticated\" claim; "
                        + "Supabase will treat this session as anon and RLS will reject writes. "
                        + "Run tools/firebase-claims/set-supabase-role.mjs for this user.");
            }
            result.postValue(Resource.success(true));
        });
    }

    /** Re-sends the Firebase confirmation email (used by the resend sheet). */
    public LiveData<Resource<Void>> resendVerificationEmail() {
        MutableLiveData<Resource<Void>> result = new MutableLiveData<>();
        result.postValue(Resource.loading());
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            result.postValue(Resource.error("Your session expired. Go back and re-enter your email address.", null));
            return result;
        }
        user.sendEmailVerification().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                result.postValue(Resource.success(null));
            } else {
                result.postValue(Resource.error(friendlyAuthError(task.getException()), null));
            }
        });
        return result;
    }

    /* ================================================================== */
    /* Profile / Supabase data                                             */
    /* ================================================================== */

    /** GET /rest/v1/profiles?firebase_uid=eq.{uid} — data is null when no row exists. */
    public LiveData<Resource<Map<String, Object>>> fetchProfileByFirebaseUid(String firebaseUid) {
        MutableLiveData<Resource<Map<String, Object>>> result = new MutableLiveData<>();
        loadProfileRow(firebaseUid, result::postValue);
        return result;
    }

    /** Kept for the wizard's existing call sites. */
    public LiveData<Resource<Map<String, Object>>> fetchProfileRow(String firebaseUid) {
        return fetchProfileByFirebaseUid(firebaseUid);
    }

    private void loadProfileRow(String firebaseUid, Cb<Map<String, Object>> cb) {
        if (TextUtils.isEmpty(firebaseUid)) {
            cb.on(Resource.success(null));
            return;
        }
        Map<String, String> q = new HashMap<>();
        q.put("firebase_uid", "eq." + firebaseUid);
        // Only columns that exist on public.profiles. The previous projection asked for
        // email and is_private, which the table does not have, so PostgREST answered
        // 400 / 42703 and every profile write aborted before it could start.
        // firebase_uid stays Phase 1 only: signup still resolves a row by it.
        q.put("select", ProfileColumns.IDENTITY);
        q.put("limit", "1");
        restApi.getProfilesRaw(q).enqueue(new Callback<List<Map<String, Object>>>() {
            @Override public void onResponse(@NonNull Call<List<Map<String, Object>>> call,
                                             @NonNull Response<List<Map<String, Object>>> response) {
                if (!response.isSuccessful()) {
                    cb.on(Resource.error(mapAuthError(response), null));
                    return;
                }
                List<Map<String, Object>> rows = response.body();
                cb.on(Resource.success(rows == null || rows.isEmpty() ? null : rows.get(0)));
            }

            @Override public void onFailure(@NonNull Call<List<Map<String, Object>>> call, @NonNull Throwable t) {
                cb.on(Resource.error("Unable to reach Supabase: " + safe(t), null));
            }
        });
    }

    /** Firebase: changes the signed-in user's password. */
    public LiveData<Resource<Void>> updatePassword(String password) {
        MutableLiveData<Resource<Void>> result = new MutableLiveData<>();
        result.postValue(Resource.loading());
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            result.postValue(Resource.error("Your session expired. Go back and re-enter your email address.", null));
            return result;
        }
        if (password == null || password.length() < 6) {
            result.postValue(Resource.error("Password must be at least 6 characters.", null));
            return result;
        }
        user.updatePassword(password).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                result.postValue(Resource.success(null));
            } else {
                result.postValue(Resource.error(friendlyAuthError(task.getException()), null));
            }
        });
        return result;
    }

    /**
     * Signup variant of {@link #updatePassword(String)} that can recover from
     * {@code ERROR_REQUIRES_RECENT_LOGIN}.
     *
     * <p>Two modes, both bounded:
     * <ul>
     *   <li>{@code currentPassword} empty — try the update. If Firebase asks for a
     *       recent login, report {@code NEEDS_CURRENT_PASSWORD} so the screen can ask
     *       once.</li>
     *   <li>{@code currentPassword} supplied — reauthenticate with
     *       {@link EmailAuthProvider}, then retry the update <em>exactly once</em>.
     *       A second refusal or a wrong password ends the attempt; there is no
     *       loop and no repeated reauthentication.</li>
     * </ul>
     *
     * <p>An unknown current password is not treated as a bug to be papered over:
     * signup creates the account with a throwaway password, so an account whose
     * reauthentication window has expired can only be recovered through the
     * supported password-reset email. The UI says so instead of pretending the
     * old password can be supplied.
     */
    public LiveData<PasswordSetupResult> setPasswordForSignup(String newPassword, String currentPassword) {
        MutableLiveData<PasswordSetupResult> result = new MutableLiveData<>();
        result.postValue(PasswordSetupResult.loading());

        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            result.postValue(PasswordSetupResult.failed(
                    "Your session expired. Go back and re-enter your email address."));
            return result;
        }
        if (newPassword == null || newPassword.length() < 6) {
            result.postValue(PasswordSetupResult.failed("Password must be at least 6 characters."));
            return result;
        }
        if (currentPassword == null || currentPassword.isEmpty()) {
            attemptPasswordUpdate(user, newPassword, false, result);
            return result;
        }

        String email = user.getEmail();
        if (TextUtils.isEmpty(email)) {
            result.postValue(PasswordSetupResult.failed(
                    "This account has no email address, so its password cannot be confirmed."));
            return result;
        }
        user.reauthenticate(EmailAuthProvider.getCredential(email, currentPassword))
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Exception e = task.getException();
                        if (PasswordRecovery.isWrongPassword(e)) {
                            result.postValue(PasswordSetupResult.wrongCurrentPassword(
                                    appContext.getString(R.string.auth_reauth_wrong)));
                        } else {
                            result.postValue(PasswordSetupResult.failed(friendlyAuthError(e)));
                        }
                        return;
                    }
                    Log.i(TAG, "Reauthentication succeeded; retrying the password update once.");
                    attemptPasswordUpdate(user, newPassword, true, result);
                });
        return result;
    }

    /**
     * @param afterReauthentication true when this is the single retry, in which case a
     *                              second recent-login refusal is reported as a failure
     *                              rather than prompting again.
     */
    private void attemptPasswordUpdate(FirebaseUser user, String newPassword,
                                       boolean afterReauthentication,
                                       MutableLiveData<PasswordSetupResult> result) {
        user.updatePassword(newPassword).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                result.postValue(PasswordSetupResult.success());
                return;
            }
            Exception e = task.getException();
            if (!afterReauthentication && PasswordRecovery.requiresRecentLogin(e)) {
                Log.i(TAG, "Password update needs a recent login; asking for the current password once.");
                result.postValue(PasswordSetupResult.needsCurrentPassword());
                return;
            }
            // Log the error type only: never the password, never the credential.
            Log.w(TAG, "Password update failed (reauth=" + afterReauthentication + "): "
                    + (e == null ? "unknown" : e.getClass().getSimpleName()));
            result.postValue(PasswordSetupResult.failed(friendlyAuthError(e)));
        });
    }

    /**
     * Creates the profile row if it is missing, then writes username / full_name /
     * birthday. This is what finalises the account on the Terms step.
     *
     * @return the Supabase {@code profiles.id}, which the rest of the app uses
     */
    public LiveData<Resource<String>> finalizeProfile(String firebaseUid, String username,
                                                      String fullName, String birthdayIso) {
        MutableLiveData<Resource<String>> result = new MutableLiveData<>();
        result.postValue(Resource.loading());
        if (TextUtils.isEmpty(firebaseUid)) {
            result.postValue(Resource.error("Your session expired. Please start again.", null));
            return result;
        }
        // The claim gate runs before the first Supabase request of a signup. Without it
        // the GET below runs as `anon`, RLS answers 42501, and the wizard reports a
        // permission error even though the account itself is perfectly valid.
        ensureSupabaseRole(
                () -> continueFinalizeProfile(firebaseUid, username, fullName, birthdayIso, result),
                blocked -> result.postValue(Resource.error(blocked, null)));
        return result;
    }

    /** Body of {@link #finalizeProfile} once the token is known to carry the claim. */
    private void continueFinalizeProfile(String firebaseUid, String username, String fullName,
                                         String birthdayIso, MutableLiveData<Resource<String>> result) {
        loadProfileRow(firebaseUid, profile -> {
            if (profile.status == Resource.Status.ERROR) {
                result.postValue(Resource.error(profile.message, null));
                return;
            }
            Map<String, Object> row = profile.data;
            if (row != null && row.get("id") != null) {
                String profileId = String.valueOf(row.get("id"));
                patchInternal(profileId, profileFields(username, fullName, birthdayIso), patched -> {
                    result.postValue(patched.status == Resource.Status.SUCCESS
                            ? Resource.success(profileId)
                            : Resource.error(patched.message, null));
                });
                return;
            }
            createProfile(firebaseUid, username, fullName, birthdayIso, result);
        });
    }

    /* ================================================================== */
    /* Supabase role claim gate                                            */
    /* ================================================================== */

    /**
     * Guarantees the current ID token carries {@code role: "authenticated"} before the
     * caller sends a write that RLS restricts to that role.
     *
     * <p>Exactly three things can happen, each at most once: the cached token is
     * inspected, one forced refresh is minted, and — only when a trusted provisioner is
     * compiled in — one provisioning request is made. A token that still lacks the claim
     * afterwards is reported to the caller instead of being sent: the write would fail
     * anyway, and a request known to be rejected is noise, not progress.
     */
    private void ensureSupabaseRole(Runnable onReady, Consumer<String> onBlocked) {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            onBlocked.accept("Your session expired. Please start again.");
            return;
        }
        user.getIdToken(false).addOnCompleteListener(task ->
                resolveClaimStep(task, user, false, false, onReady, onBlocked));
    }

    private void resolveClaimStep(com.google.android.gms.tasks.Task<GetTokenResult> task,
                                  FirebaseUser user, boolean refreshed, boolean provisionTried,
                                  Runnable onReady, Consumer<String> onBlocked) {
        boolean hasRole = task.isSuccessful() && task.getResult() != null
                && TokenClaims.hasAuthenticatedRole(task.getResult().getClaims());
        boolean provisionConfigured = BuildConfig.CLAIM_PROVISION_URL != null
                && !BuildConfig.CLAIM_PROVISION_URL.trim().isEmpty();

        switch (RoleClaimPolicy.decide(hasRole, refreshed, provisionConfigured, provisionTried)) {
            case PROCEED:
                onReady.run();
                return;
            case FORCE_REFRESH:
                Log.i(TAG, "ID token has no role=\"authenticated\" claim; forcing one refresh.");
                user.getIdToken(true).addOnCompleteListener(next ->
                        resolveClaimStep(next, user, true, provisionTried, onReady, onBlocked));
                return;
            case PROVISION:
                Log.i(TAG, "Forced refresh still has no role claim; asking the provisioner once.");
                // Reuse the token the refresh already produced. Fetching it again with
                // FirebaseTokens would block on Tasks.await, which Android refuses on the
                // main thread — and this callback runs there.
                String current = task.isSuccessful() && task.getResult() != null
                        ? task.getResult().getToken() : null;
                if (current == null || current.isEmpty()) {
                    blockMissingClaim(onBlocked);
                    return;
                }
                requestRoleProvisioning(current, granted -> {
                    if (!granted) {
                        blockMissingClaim(onBlocked);
                        return;
                    }
                    user.getIdToken(true).addOnCompleteListener(next ->
                            resolveClaimStep(next, user, true, true, onReady, onBlocked));
                });
                return;
            default:
                blockMissingClaim(onBlocked);
        }
    }

    private void blockMissingClaim(Consumer<String> onBlocked) {
        Log.w(TAG, "Account has no role=\"authenticated\" claim, so Supabase would run the "
                + "request as anon and RLS would reject it. Provision the claim for this "
                + "Firebase user (tools/firebase-claims/set-supabase-role.mjs --uid <uid> "
                + "--apply, or a deployed provisioning endpoint).");
        onBlocked.accept(appContext.getString(R.string.auth_claim_missing));
    }

    /**
     * Calls the configured trusted endpoint with the user's own ID token so it can grant
     * the claim. No service credential is involved on the device: the endpoint authenticates
     * the caller and performs the privileged write itself.
     */
    private void requestRoleProvisioning(String token, Consumer<Boolean> cb) {
        String url = BuildConfig.CLAIM_PROVISION_URL == null
                ? "" : BuildConfig.CLAIM_PROVISION_URL.trim();
        if (url.isEmpty() || token == null || token.isEmpty()) {
            cb.accept(false);
            return;
        }
        // The provisioner reads the token from the JSON body: Apps Script web apps drop
        // custom headers on the 302 they answer with, so the token cannot ride there.
        com.google.gson.JsonObject payload = new com.google.gson.JsonObject();
        payload.addProperty("idToken", token);
        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(url)
                .post(okhttp3.RequestBody.create(
                        okhttp3.MediaType.parse("application/json; charset=utf-8"),
                        payload.toString()))
                .header("Authorization", "Bearer " + token)
                .build();
        RetrofitClient.getInstance(appContext).getOkHttpClient().newCall(request)
                .enqueue(new okhttp3.Callback() {
                    @Override public void onFailure(@NonNull okhttp3.Call call,
                                                    @NonNull java.io.IOException e) {
                        Log.w(TAG, "Claim provisioning call failed: "
                                + e.getClass().getSimpleName());
                        cb.accept(false);
                    }

                    @Override public void onResponse(@NonNull okhttp3.Call call,
                                                     @NonNull okhttp3.Response response) {
                        int code = response.code();
                        boolean granted = false;
                        String body = "";
                        try {
                            okhttp3.ResponseBody rb = response.body();
                            body = rb == null ? "" : rb.string();
                            // Apps Script answers 200 even when doPost reports a failure,
                            // so the verdict comes from the payload, not the status line.
                            granted = response.isSuccessful() && body.contains("\"ok\":true");
                        } catch (Exception e) {
                            granted = false;
                        } finally {
                            response.close();
                        }
                        if (!granted) {
                            Log.w(TAG, "Claim provisioning refused (HTTP " + code + "): "
                                    + (body.isEmpty() ? "no body" : body.substring(0, Math.min(160, body.length()))));
                        }
                        cb.accept(granted);
                    }
                });
    }

    /** POST /rest/v1/profiles — first write for a brand-new Firebase account. */
    private void createProfile(String firebaseUid, String username, String fullName,
                               String birthdayIso, MutableLiveData<Resource<String>> result) {
        Map<String, Object> body = profileFields(username, fullName, birthdayIso);
        final String generatedId = UUID.randomUUID().toString();
        body.put("id", generatedId);
        body.put("firebase_uid", firebaseUid);

        restApi.createProfile(ProfileColumns.WRITE_ECHO, body).enqueue(new Callback<List<ProfileDto>>() {
            @Override public void onResponse(@NonNull Call<List<ProfileDto>> call,
                                             @NonNull Response<List<ProfileDto>> response) {
                if (response.isSuccessful()) {
                    List<ProfileDto> created = response.body();
                    // A 201 with no representation means RLS filtered the insert; never
                    // fall back to the id we wished for.
                    if (!ProfileWriteCheck.confirmed(created, generatedId)) {
                        Log.w(TAG, "Profile insert returned no matching row (HTTP "
                                + response.code() + "); treating as failure.");
                        result.postValue(Resource.error(ProfileWriteCheck.ZERO_ROWS_MESSAGE, null));
                        return;
                    }
                    result.postValue(Resource.success(created.get(0).getId()));
                    return;
                }
                String raw = errorBody(response);
                if (raw.contains("23505") || raw.toLowerCase(Locale.US).contains("duplicate key")) {
                    // The username was taken in the race, or another device already
                    // created this firebase_uid. Re-resolve once before failing.
                    loadProfileRow(firebaseUid, again -> {
                        if (again.status == Resource.Status.SUCCESS && again.data != null
                                && again.data.get("id") != null) {
                            String existingId = String.valueOf(again.data.get("id"));
                            patchInternal(existingId, profileFields(username, fullName, birthdayIso), patched ->
                                    result.postValue(patched.status == Resource.Status.SUCCESS
                                            ? Resource.success(existingId)
                                            : Resource.error(patched.message, null)));
                            return;
                        }
                        result.postValue(Resource.error(
                                appContext.getString(R.string.auth_username_just_taken), null));
                    });
                    return;
                }
                result.postValue(Resource.error(raw, null));
            }

            @Override public void onFailure(@NonNull Call<List<ProfileDto>> call, @NonNull Throwable t) {
                result.postValue(Resource.error("Unable to reach Supabase: " + safe(t), null));
            }
        });
    }

    private Map<String, Object> profileFields(String username, String fullName, String birthdayIso) {
        Map<String, Object> fields = new HashMap<>();
        if (!TextUtils.isEmpty(username)) fields.put("username", username);
        if (fullName != null) fields.put("full_name", fullName);
        if (!TextUtils.isEmpty(birthdayIso)) fields.put("birthday", birthdayIso);
        return fields;
    }

    /** PATCH /rest/v1/profiles?id=eq.{profileId}. */
    public LiveData<Resource<Void>> patchProfile(String profileId, Map<String, Object> fields) {
        MutableLiveData<Resource<Void>> result = new MutableLiveData<>();
        result.postValue(Resource.loading());
        patchInternal(profileId, fields, result::postValue);
        return result;
    }

    private void patchInternal(String profileId, Map<String, Object> fields, Cb<Void> cb) {
        if (TextUtils.isEmpty(profileId)) {
            cb.on(Resource.error("Your account is not ready yet. Please start again.", null));
            return;
        }
        restApi.updateProfile("eq." + profileId, ProfileColumns.WRITE_ECHO, fields).enqueue(new Callback<List<ProfileDto>>() {
            @Override public void onResponse(@NonNull Call<List<ProfileDto>> call,
                                             @NonNull Response<List<ProfileDto>> response) {
                if (response.isSuccessful()) {
                    // PATCH is filtered by RLS without an error, so an empty representation
                    // is the only signal that nothing was written.
                    if (!ProfileWriteCheck.confirmed(response.body(), profileId)) {
                        Log.w(TAG, "Profile patch matched no row (HTTP " + response.code()
                                + "); treating as failure.");
                        cb.on(Resource.error(ProfileWriteCheck.ZERO_ROWS_MESSAGE, null));
                        return;
                    }
                    cb.on(Resource.success(null));
                    return;
                }
                String raw = errorBody(response);
                if (raw.contains("23505") || raw.toLowerCase(Locale.US).contains("duplicate key")) {
                    raw = appContext.getString(R.string.auth_username_just_taken);
                }
                cb.on(Resource.error(raw, null));
            }

            @Override public void onFailure(@NonNull Call<List<ProfileDto>> call, @NonNull Throwable t) {
                cb.on(Resource.error("Unable to reach Supabase: " + safe(t), null));
            }
        });
    }

    /**
     * Uploads raw JPEG bytes and returns the public URL. The caller must HEAD-check
     * the URL before writing it to the database.
     */
    public LiveData<Resource<String>> uploadImage(String bucket, byte[] bytes, String uid, String ext) {
        MutableLiveData<Resource<String>> result = new MutableLiveData<>();
        result.postValue(Resource.loading());
        final String path = uid + "/" + UUID.randomUUID().toString().replace("-", "")
                + "." + (ext == null || ext.isEmpty() ? "jpg" : ext);
        RequestBody body = RequestBody.create(MediaType.parse("image/jpeg"), bytes);
        storageApi.uploadFile(bucket, path, body).enqueue(new Callback<Void>() {
            @Override public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (!response.isSuccessful()) {
                    result.postValue(Resource.error(mapAuthError(response)));
                    return;
                }
                result.postValue(Resource.success(publicUrl(bucket, path)));
            }

            @Override public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                result.postValue(Resource.error("Unable to reach Supabase: " + safe(t)));
            }
        });
        return result;
    }

    /** Public object URL for a stored object (bucket is public per the storage patch). */
    public String publicUrl(String bucket, String path) {
        String base = BuildConfig.SUPABASE_URL == null ? "" : BuildConfig.SUPABASE_URL.trim();
        if (!base.endsWith("/")) base += "/";
        return base + "storage/v1/object/public/" + bucket + "/" + path;
    }

    /* ================================================================== */
    /* Session                                                             */
    /* ================================================================== */

    public void clearSession() {
        try {
            firebaseAuth.signOut();
        } catch (Exception ignored) {
        }
        sessionManager.clearSession();
    }

    public void setRememberLogin(boolean remember) {
        sessionManager.setRememberLogin(remember);
    }

    public boolean hasFirebaseUser() {
        return firebaseAuth.getCurrentUser() != null;
    }

    /** Firebase ID token, or null when signed out. Never persisted. */
    public String currentIdToken(boolean forceRefresh) {
        return FirebaseTokens.current(forceRefresh);
    }

    /* ================================================================== */
    /* Helpers                                                             */
    /* ================================================================== */

    /**
     * Forces one ID-token refresh after login.
     *
     * <p>Supabase reads the {@code role} claim from the ID token to pick a Postgres role.
     * Claims changed on the Firebase user (for example the one-off
     * {@code role=authenticated} script) only appear in a newly minted token, so a cached
     * token can keep the old role for up to an hour. One forced refresh makes the login
     * consistent with the account's current claims without polling or retrying blindly.
     *
     * <p>A failed refresh is reported, not swallowed: the caller must not fall through to
     * protected writes with a stale token. The token itself is never logged or stored.
     */
    private void refreshTokenThenContinue(FirebaseUser user,
                                          MutableLiveData<Resource<TokenResponse>> result) {
        user.getIdToken(true).addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Log.w(TAG, "Forced ID-token refresh failed after login: "
                        + (task.getException() == null ? "unknown" : task.getException().getMessage()));
                result.postValue(Resource.error(
                        "We couldn't refresh your session. Check your connection and try again."));
                return;
            }
            GetTokenResult refreshed = task.getResult();
            if (refreshed == null || !TokenClaims.hasAuthenticatedRole(refreshed.getClaims())) {
                // Do not fail the login: reads still work. Say it loudly in the log so a
                // missing claim is never mistaken for a working protected write.
                Log.w(TAG, "Firebase token has no role=\"authenticated\" claim; Supabase will "
                        + "treat this session as anon and RLS will reject writes. "
                        + "Run tools/firebase-claims/set-supabase-role.mjs for this user.");
            }
            resolveIdentity(user, result);
        });
    }

    /**
     * Resolves the profile id after a successful login. A missing row (or an
     * unreachable backend) is not a login failure: the identity itself is valid.
     */
    private void resolveIdentity(FirebaseUser user, MutableLiveData<Resource<TokenResponse>> result) {
        persistIdentity(user, null);
        loadProfileRow(user.getUid(), profile -> {
            Map<String, Object> row = profile.data;
            if (row != null && row.get("id") != null) {
                String profileId = String.valueOf(row.get("id"));
                String username = row.get("username") == null
                        ? user.getDisplayName() : String.valueOf(row.get("username"));
                sessionManager.saveIdentity(user.getUid(), profileId, username,
                        user.getPhotoUrl() == null ? null : user.getPhotoUrl().toString());
                result.postValue(Resource.success(new TokenResponse(null, null,
                        user.getUid(), username, user.getEmail())));
                return;
            }
            result.postValue(Resource.success(new TokenResponse(null, null,
                    user.getUid(), user.getDisplayName(), user.getEmail())));
        });
    }

    private void persistIdentity(FirebaseUser user, String profileId) {
        String username = user.getDisplayName();
        if (TextUtils.isEmpty(username)) {
            String email = user.getEmail() == null ? "" : user.getEmail();
            username = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        }
        String avatar = user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : null;
        sessionManager.saveIdentity(user.getUid(), profileId, username, avatar);
    }

    /**
     * Placeholder username written at account creation so the row satisfies
     * {@code profiles_username_fmt} (^[a-z0-9._]+$) and the 3–30 length rule.
     */
    public static String temporaryUsername() {
        final String hex = "0123456789abcdef";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder("user_");
        for (int i = 0; i < TEMP_USERNAME_DIGITS; i++) {
            sb.append(hex.charAt(random.nextInt(hex.length())));
        }
        return sb.toString();
    }

    /** 32 random characters; replaced by the real password on the next wizard step. */
    private static String randomPassword() {
        final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(32);
        for (int i = 0; i < 32; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    /** Maps FirebaseAuth failures onto copy the existing screens already show. */
    private String friendlyAuthError(Exception e) {
        if (e == null) return "Authentication failed. Please try again.";
        if (e instanceof FirebaseAuthWeakPasswordException) {
            String reason = ((FirebaseAuthWeakPasswordException) e).getReason();
            return TextUtils.isEmpty(reason) ? "Please choose a stronger password." : reason;
        }
        if (e instanceof FirebaseAuthUserCollisionException) {
            return appContext.getString(R.string.auth_already_registered_title);
        }
        if (e instanceof FirebaseAuthInvalidUserException) {
            return "There is no account with that email address.";
        }
        if (e instanceof FirebaseAuthInvalidCredentialsException) {
            return "Sorry, your password was incorrect. Please double-check your password.";
        }
        if (e instanceof FirebaseNetworkException) {
            return "Network error. Check your connection and try again.";
        }
        String code = e.getMessage() == null ? "" : e.getMessage().toUpperCase(Locale.US);
        if (code.contains("OPERATION_NOT_ALLOWED")) {
            return "Email/password sign-in is disabled for this Firebase project. Enable it in Firebase Console > Authentication > Sign-in method.";
        }
        if (code.contains("TOO_MANY_REQUESTS")) {
            return appContext.getString(R.string.auth_email_rate_limited);
        }
        if (code.contains("BADLY_FORMATTED") || code.contains("INVALID_EMAIL")) {
            return appContext.getString(R.string.auth_email_invalid);
        }
        if (PasswordRecovery.requiresRecentLogin(e)) {
            return appContext.getString(R.string.auth_password_reauth_required);
        }
        return e.getMessage() != null ? e.getMessage() : "Authentication failed. Please try again.";
    }

    /** Turns a failing Supabase data response into friendly copy. */
    private String mapAuthError(Response<?> response) {
        String raw = errorBody(response);
        String lower = raw.toLowerCase(Locale.US);
        if (response.code() == 401 && (lower.contains("no suitable key") || lower.contains("pgrst301"))) {
            return "This Supabase project is not accepting Firebase sign-ins yet (Third-Party Auth is off).";
        }
        // PostgREST puts the SQLSTATE in a separate "code" field, so a 42501 body reads
        // "permission denied for table profiles" with no digits in it. errorBody() now
        // appends the code; "permission denied" is matched as well so an older shape
        // still reaches this branch instead of the generic 401 sentence.
        if (lower.contains("42501") || lower.contains("row-level security")
                || lower.contains("permission denied")) {
            return appContext.getString(R.string.auth_claim_missing);
        }
        if (response.code() == 401 || response.code() == 403) {
            return "You don't have permission to do that.";
        }
        if (lower.contains("23505") || lower.contains("duplicate key")) {
            return appContext.getString(R.string.auth_username_just_taken);
        }
        return raw;
    }

    static String errorBody(Response<?> response) {
        try {
            String raw = response.errorBody() == null ? "" : response.errorBody().string();
            if (raw.isEmpty()) return "Supabase request failed (HTTP " + response.code() + ").";
            JsonObject object = JsonParser.parseString(raw).getAsJsonObject();
            String message;
            if (object.has("message")) {
                message = object.get("message").getAsString();
            } else if (object.has("error_description")) {
                message = object.get("error_description").getAsString();
            } else {
                return raw;
            }
            // Keep the SQLSTATE visible: "permission denied for table profiles" alone
            // does not say why, while "[42501]" plus the log does. Only used for
            // diagnostics and for the unmapped fallback text.
            if (object.has("code") && !object.get("code").isJsonNull()) {
                String code = object.get("code").getAsString();
                if (!code.isEmpty() && !message.contains(code)) {
                    message = message + " [" + code + "]";
                }
            }
            return message;
        } catch (Exception e) {
            return "Supabase request failed (HTTP " + response.code() + ").";
        }
    }

    private static String safe(Throwable t) {
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }
}
