package com.insta.nova.ui.auth;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.insta.nova.data.remote.dto.TokenResponse;
import com.insta.nova.data.repository.AuthRepository;
import com.insta.nova.data.repository.ProfileRepository;
import com.insta.nova.util.Resource;

import java.util.Map;

/**
 * Shared ViewModel preserving state across multi-step signup wizard and auth requests.
 */
public class AuthViewModel extends AndroidViewModel {

    private final AuthRepository authRepository;
    private final ProfileRepository profileRepository;

    // Temporary wizard registration fields
    private String signupEmail = "";
    private String signupPassword = "";
    private String signupBirthday = "";
    private String signupFullName = "";
    private String signupUsername = "";

    public AuthViewModel(@NonNull Application application) {
        super(application);
        this.authRepository = new AuthRepository(application);
        this.profileRepository = new ProfileRepository(application);
    }

    public void setSignupEmail(String email) { this.signupEmail = email; }
    public String getSignupEmail() { return signupEmail; }

    public void setSignupPassword(String password) { this.signupPassword = password; }
    public String getSignupPassword() { return signupPassword; }

    public void setSignupBirthday(String birthday) { this.signupBirthday = birthday; }
    public String getSignupBirthday() { return signupBirthday; }

    public void setSignupFullName(String fullName) { this.signupFullName = fullName; }
    public String getSignupFullName() { return signupFullName; }

    public void setSignupUsername(String username) { this.signupUsername = username; }
    public String getSignupUsername() { return signupUsername; }

    public LiveData<Resource<TokenResponse>> login(String identifier, String password) {
        return authRepository.login(identifier, password);
    }

    public LiveData<Resource<Boolean>> checkUsername(String username) {
        return profileRepository.checkUsernameAvailability(username);
    }

    public LiveData<Resource<Void>> recoverPassword(String email) {
        return authRepository.recoverPassword(email);
    }

    /** Reads the signed-in user's profile row (Firebase identity -> Supabase profiles). */
    public LiveData<Resource<Map<String, Object>>> loadProfile(String firebaseUid) {
        return authRepository.fetchProfileByFirebaseUid(firebaseUid);
    }
}
