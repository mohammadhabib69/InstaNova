package com.insta.clone.ui.auth;

import android.app.Application;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import com.insta.clone.data.remote.dto.TokenResponse;
import com.insta.clone.data.repository.AuthRepository;
import com.insta.clone.data.repository.ProfileRepository;
import com.insta.clone.util.Constants;
import com.insta.clone.util.Resource;
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
    private String accessToken = "";
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

    public String getUsername() { return username; }
    public void setUsername(String v) { username = v == null ? "" : v.trim().toLowerCase(Locale.US); }

    public String getBirthdayIso() { return birthdayIso; }
    public void setBirthdayIso(String v) { birthdayIso = v == null ? "" : v; }

    public String getUserId() { return userId; }
    public void setUserId(String v) { userId = v == null ? "" : v; }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String v) { accessToken = v == null ? "" : v; }

    public String getAvatarUrl() { return avatarUrl; }
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
            if (now.get(Calendar.DAY_OF_YEAR) < born.get(Calendar.DAY_OF_YEAR)) age--;
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

    public LiveData<Resource<Void>> requestOtp(String targetEmail) {
        return authRepository.requestOtp(targetEmail);
    }

    public LiveData<Resource<TokenResponse>> verifyOtp(String targetEmail, String token) {
        return authRepository.verifyOtp(targetEmail, token);
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

    /** Writes username / full_name / birthday once the user accepts the terms. */
    public LiveData<Resource<Void>> saveRegistrationDetails() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("username", username);
        fields.put("full_name", fullName);
        if (birthdayIso != null && !birthdayIso.isEmpty()) fields.put("birthday", birthdayIso);
        return authRepository.patchProfile(userId, fields);
    }

    public LiveData<Resource<Void>> patchProfileFields(Map<String, Object> fields) {
        return authRepository.patchProfile(userId, fields);
    }

    public LiveData<Resource<String>> uploadImage(String bucket, byte[] bytes, String ext) {
        return authRepository.uploadImage(bucket, bytes, userId, ext);
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
        accessToken = "";
        avatarUrl = "";
        bio = "";
        avatarUri = null;
        rememberLogin = true;
        passwordSet = false;
        lastCodeSentAt = 0;
        alreadyRegistered = false;
    }
}
