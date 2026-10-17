package com.insta.nova.ui.auth;

import android.app.Application;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import com.insta.nova.data.repository.AuthRepository;
import com.insta.nova.data.repository.PasswordSetupResult;
import com.insta.nova.data.repository.ProfileRepository;
import com.insta.nova.util.Constants;
import com.insta.nova.util.Resource;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Shared state for the whole registration wizard. Scoped to AuthActivity, so
 * every step sees the same values and going back with the arrow keeps whatever
 * was already entered.
 */
public class RegistrationViewModel extends AndroidViewModel {

    private final AuthRepository authRepository;
    private final ProfileRepository profileRepository;

    /* ---------- wizard state ---------- */
    private String phone = "";
    private String email = "";
    private String fullName = "";
    private String username = "";
    private String birthdayIso = "";
    private String userId = "";
    private String profileId = "";
    private String avatarUrl = "";
    private String bio = "";
    private Uri avatarUri;
    private boolean rememberLogin = true;
    private boolean passwordSet;
    private long lastCodeSentAt;
    /** Set when the Code step discovers this email already has a finished account. */
    private boolean alreadyRegistered;

    public RegistrationViewModel(@NonNull Application application) {
        super(application);
        this.authRepository = new AuthRepository(application);
        this.profileRepository = new ProfileRepository(application);
    }

    /* ---------- simple accessors ---------- */

    public String getPhone() { return phone; }
    public void setPhone(String v) { phone = v == null ? "" : v; }

    public String getEmail() { return email; }
    public void setEmail(String v) { email = v == null ? "" : v.trim().toLowerCase(Locale.US); }

    public String getFullName() { return fullName; }
    public void setFullName(String v) { fullName = v == null ? "" : v.trim(); }

    public String getUsername() {
        if (username == null || username.isEmpty()) {
            com.insta.nova.data.local.SessionManager sm =
                    com.insta.nova.data.local.SessionManager.getInstance(getApplication());
            if (sm.getUsername() != null && !sm.getUsername().isEmpty()) {
                username = sm.getUsername();
            }
        }
        return username == null ? "" : username;
    }
    public void setUsername(String v) { username = v == null ? "" : v.trim().toLowerCase(Locale.US); }

    public String getBirthdayIso() { return birthdayIso; }
    public void setBirthdayIso(String v) { birthdayIso = v == null ? "" : v; }

    public String getUserId() {
        if (userId == null || userId.isEmpty()) {
            com.insta.nova.data.local.SessionManager sm =
                    com.insta.nova.data.local.SessionManager.getInstance(getApplication());
            if (sm.getFirebaseUid() != null && !sm.getFirebaseUid().isEmpty()) {
                userId = sm.getFirebaseUid();
            } else {
                com.google.firebase.auth.FirebaseUser u =
                        com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
                if (u != null && u.getUid() != null) {
                    userId = u.getUid();
                } else if (sm.getUserId() != null && !sm.getUserId().isEmpty()) {
                    userId = sm.getUserId();
                }
            }
        }
        return userId == null ? "" : userId;
    }
    public void setUserId(String v) { userId = v == null ? "" : v; }

    /** Supabase profiles.id, resolved when the profile row is created/found. */
    public String getProfileId() {
        if (profileId == null || profileId.isEmpty()) {
            com.insta.nova.data.local.SessionManager sm =
                    com.insta.nova.data.local.SessionManager.getInstance(getApplication());
            if (sm.getUserId() != null && !sm.getUserId().isEmpty()) {
                profileId = sm.getUserId();
            }
        }
        return profileId == null ? "" : profileId;
    }
    public void setProfileId(String v) { profileId = v == null ? "" : v; }

    public String getAvatarUrl() {
        if (avatarUrl == null || avatarUrl.isEmpty()) {
            com.insta.nova.data.local.SessionManager sm =
                    com.insta.nova.data.local.SessionManager.getInstance(getApplication());
            if (sm.getAvatarUrl() != null && !sm.getAvatarUrl().isEmpty()) {
                avatarUrl = sm.getAvatarUrl();
            }
        }
        return avatarUrl == null ? "" : avatarUrl;
    }
    public void setAvatarUrl(String v) { avatarUrl = v == null ? "" : v; }

    public String getBio() { return bio; }
    public void setBio(String v) { bio = v == null ? "" : v; }

    public Uri getAvatarUri() { return avatarUri; }
    public void setAvatarUri(Uri v) { avatarUri = v; }

    public boolean isRememberLogin() { return rememberLogin; }
    public void setRememberLogin(boolean v) { rememberLogin = v; }

    public boolean isPasswordSet() { return passwordSet; }
    public void setPasswordSet(boolean v) { passwordSet = v; }

    public boolean isAlreadyRegistered() { return alreadyRegistered; }
    public void setAlreadyRegistered(boolean v) { alreadyRegistered = v; }

    /* ---------- confirmation-code resend cooldown ---------- */

    public void markCodeSent() {
        lastCodeSentAt = System.currentTimeMillis();
    }

    /** Whole seconds left before a resend is allowed; 0 means it is allowed now. */
    public int secondsUntilResend() {
        if (lastCodeSentAt == 0) return 0;
        long elapsed = (System.currentTimeMillis() - lastCodeSentAt) / 1000L;
        long left = Constants.CODE_RESEND_COOLDOWN_SECONDS - elapsed;
        return left > 0 ? (int) left : 0;
    }

    /** mm:ss for the disabled resend row. */
    public static String formatCountdown(int seconds) {
        return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60);
    }

    /* ---------- derived values ---------- */

    /** Age in whole years for an ISO yyyy-MM-dd date, or -1 if unparsable. */
    public static int ageFromIso(String iso) {
        if (iso == null || iso.isEmpty()) return -1;
        try {
            String[] p = iso.split("-");
            Calendar born = Calendar.getInstance();
            born.clear();
            born.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]));
            Calendar now = Calendar.getInstance();
            int age = now.get(Calendar.YEAR) - born.get(Calendar.YEAR);
            // Compare month/day, not DAY_OF_YEAR: the day-of-year of a date shifts by
            // one in leap years, which used to under-report the age of anyone born
            // after 28 February in a leap year (and wrongly block 13-year-olds).
            int monthDelta = now.get(Calendar.MONTH) - born.get(Calendar.MONTH);
            if (monthDelta < 0
                    || (monthDelta == 0 && now.get(Calendar.DAY_OF_MONTH) < born.get(Calendar.DAY_OF_MONTH))) {
                age--;
            }
            return age;
        } catch (Exception e) {
            return -1;
        }
    }

    /** "October 12, 2026" for the Birthday field. */
    public static String formatIsoLong(String iso) {
        if (iso == null || iso.isEmpty()) return "";
        try {
            String[] p = iso.split("-");
            Calendar c = Calendar.getInstance();
            c.clear();
            c.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]));
            return new SimpleDateFormat("MMMM d, yyyy", Locale.US).format(c.getTime());
        } catch (Exception e) {
            return iso;
        }
    }

    public static String todayIso() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().getTime());
    }

    /** Lowercased a-z0-9._ suggestion derived from the full name. */
    public static String buildUsernameSuggestion(String name) {
        String base = name == null ? "" : name.toLowerCase(Locale.US).replaceAll("[^a-z0-9._]", "");
        base = base.replaceAll("^\\.+|\\.+$", "");
        if (base.length() < 3) base = "user" + base;
        if (base.length() > 26) base = base.substring(0, 26);
        if (base.length() < 3) base = "user";
        return base;
    }

    /** Appends 2-3 random digits, staying within the 30-character limit. */
    public static String withRandomDigits(String base) {
        String stem = base == null ? "user" : base;
        Random random = new Random();
        int digits = 2 + random.nextInt(2);
        int keep = Math.max(3, Math.min(stem.length(), 30 - digits));
        StringBuilder sb = new StringBuilder(stem.substring(0, keep));
        for (int i = 0; i < digits; i++) sb.append(random.nextInt(10));
        return sb.toString();
    }

    /* ---------- repository passthroughs ---------- */

    /** Creates the Firebase account for this address and emails the confirmation link. */
    public LiveData<Resource<Void>> startEmailVerification(String targetEmail) {
        return authRepository.startEmailVerification(targetEmail);
    }

    /** Re-checks whether the confirmation link has been opened. */
    public LiveData<Resource<Boolean>> checkEmailVerified() {
        return authRepository.checkEmailVerified();
    }

    public LiveData<Resource<Void>> resendVerificationEmail() {
        return authRepository.resendVerificationEmail();
    }

    public LiveData<Resource<Boolean>> checkUsername(String candidate) {
        return profileRepository.checkUsernameAvailability(candidate);
    }

    public LiveData<Resource<Map<String, Object>>> fetchProfileRow(String uid) {
        return authRepository.fetchProfileRow(uid);
    }

    public LiveData<Resource<Void>> updatePassword(String password) {
        return authRepository.updatePassword(password);
    }

    /**
     * Password change that can ask for the current password when Firebase requires a
     * recent login. Used by the Create-a-password step during signup.
     */
    public LiveData<PasswordSetupResult> setPasswordForSignup(String newPassword,
                                                              String currentPassword) {
        return authRepository.setPasswordForSignup(newPassword, currentPassword);
    }

    /** Sends the Firebase password-reset email (the supported recovery route). */
    public LiveData<Resource<Void>> recoverPassword(String targetEmail) {
        return authRepository.recoverPassword(targetEmail);
    }

    /** Address for this signup, falling back to the signed-in Firebase user. */
    public String getAccountEmail() {
        if (email != null && !email.isEmpty()) return email;
        com.google.firebase.auth.FirebaseUser user =
                com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        return user == null || user.getEmail() == null ? "" : user.getEmail();
    }

    /** Creates/updates the profile row with username / full_name / birthday. */
    public LiveData<Resource<String>> saveRegistrationDetails() {
        // A resumed wizard (Splash -> Password) starts with empty wizard state, so the
        // Firebase uid is re-read here rather than being trusted to survive the process.
        if (userId == null || userId.isEmpty()) {
            com.google.firebase.auth.FirebaseUser user =
                    com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) userId = user.getUid();
        }
        return authRepository.finalizeProfile(userId, username, fullName, birthdayIso);
    }

    public LiveData<Resource<Void>> patchProfileFields(Map<String, Object> fields) {
        String pid = getProfileId();
        return authRepository.patchProfile(pid, fields);
    }

    public LiveData<Resource<String>> uploadImage(String bucket, byte[] bytes, String ext) {
        String uid = getProfileId();
        if (uid.isEmpty()) {
            uid = getUserId();
        }
        return authRepository.uploadImage(bucket, bytes, uid, ext);
    }

    public void clearSession() {
        authRepository.clearSession();
    }

    /** Writes the "Remember login info" choice through to storage. */
    public void applyRememberPreference() {
        authRepository.setRememberLogin(rememberLogin);
    }

    /** Drops every in-progress wizard value ("I already have an account"). */
    public void reset() {
        phone = "";
        email = "";
        fullName = "";
        username = "";
        birthdayIso = "";
        userId = "";
        profileId = "";
        avatarUrl = "";
        bio = "";
        avatarUri = null;
        rememberLogin = true;
        passwordSet = false;
        lastCodeSentAt = 0;
        alreadyRegistered = false;
    }
}
