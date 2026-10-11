package com.insta.clone.ui.auth;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.insta.clone.InstaApp;
import com.insta.clone.R;
import com.insta.clone.data.remote.dto.AuthResponse;
import com.insta.clone.databinding.FragmentSignupEmailBinding;
import com.insta.clone.ui.main.MainActivity;
import com.insta.clone.util.Constants;
import com.insta.clone.util.Resource;
import java.util.regex.Pattern;

/** Single-page sign-up matching the reference screen and backed by Supabase GoTrue. */
public class SignupEmailFragment extends Fragment {
    private FragmentSignupEmailBinding binding; private AuthViewModel viewModel; private boolean passwordVisible;
    private final Handler handler = new Handler(Looper.getMainLooper()); private Runnable usernameCheck;
    private boolean usernameAvailable;
    private static final Pattern USERNAME = Pattern.compile("[a-z0-9._]{3,30}");

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle state) { binding = FragmentSignupEmailBinding.inflate(inflater, parent, false); return binding.getRoot(); }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state); viewModel = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);
        binding.tvWordmark.setTypeface(InstaApp.getWordmarkTypeface(requireContext()));
        if (!Constants.SHOW_FACEBOOK_BUTTON) { binding.btnFacebook.setVisibility(View.GONE); binding.facebookDivider.setVisibility(View.GONE); }
        binding.btnFacebook.setOnClickListener(v -> facebookDialog());
        binding.ivPasswordToggle.setOnClickListener(v -> { passwordVisible = !passwordVisible; binding.etPassword.setTransformationMethod(passwordVisible ? HideReturnsTransformationMethod.getInstance() : PasswordTransformationMethod.getInstance()); binding.ivPasswordToggle.setImageResource(passwordVisible ? R.drawable.ic_visibility_off : R.drawable.ic_visibility); binding.etPassword.setSelection(binding.etPassword.length()); });
        TextWatcher watcher = new TextWatcher() { @Override public void beforeTextChanged(CharSequence s,int st,int c,int a){} @Override public void onTextChanged(CharSequence s,int st,int b,int c){ validate(); } @Override public void afterTextChanged(Editable e){} };
        binding.etEmail.addTextChangedListener(watcher); binding.etFullName.addTextChangedListener(watcher); binding.etUsername.addTextChangedListener(watcher); binding.etPassword.addTextChangedListener(watcher);
        binding.etUsername.addTextChangedListener(new TextWatcher() { @Override public void beforeTextChanged(CharSequence s,int st,int c,int a){} @Override public void onTextChanged(CharSequence s,int st,int b,int c){ debounceUsername(s == null ? "" : s.toString()); } @Override public void afterTextChanged(Editable e){} });
        binding.btnSignUp.setOnClickListener(v -> submit());
        binding.tvLogin.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());
        binding.tvInfo.setOnClickListener(v -> infoDialog("Learn More", "People you know may have shared contact information with this service."));
        binding.tvTerms.setOnClickListener(v -> infoDialog("Terms and policies", "Use this app respectfully. Our Terms, Privacy Policy, and Cookies Policy explain how your account and content are handled."));
        validate();
    }

    private void debounceUsername(String raw) {
        if (usernameCheck != null) handler.removeCallbacks(usernameCheck);
        usernameAvailable = false; String value = raw.trim().toLowerCase();
        if (!USERNAME.matcher(value).matches()) { binding.tvUsernameError.setText("Use 3–30 lowercase letters, numbers, periods, or underscores."); binding.tvUsernameError.setVisibility(value.isEmpty() ? View.GONE : View.VISIBLE); validate(); return; }
        binding.tvUsernameError.setVisibility(View.GONE);
        usernameCheck = () -> viewModel.checkUsername(value).observe(getViewLifecycleOwner(), r -> { usernameAvailable = r.status == Resource.Status.SUCCESS && Boolean.TRUE.equals(r.data); if (!usernameAvailable) { binding.tvUsernameError.setText("That username is already taken."); binding.tvUsernameError.setVisibility(View.VISIBLE); } validate(); });
        handler.postDelayed(usernameCheck, Constants.USERNAME_DEBOUNCE_MS);
    }

    private void validate() {
        String email = binding.etEmail == null ? "" : binding.etEmail.getText().toString().trim();
        boolean emailValid = email.contains("@") && email.indexOf('@') > 0 && email.indexOf('.', email.indexOf('@')) > 0;
        if (!email.isEmpty() && !emailValid) { binding.tvEmailError.setText(email.matches(".*\\d{7,}.*") ? "Phone sign-up isn't available. Please use an email address." : "Enter a valid email address."); binding.tvEmailError.setVisibility(View.VISIBLE); } else binding.tvEmailError.setVisibility(View.GONE);
        boolean valid = emailValid && !binding.etFullName.getText().toString().trim().isEmpty() && usernameAvailable && binding.etPassword.length() >= 6;
        binding.btnSignUp.setEnabled(valid); binding.btnSignUp.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), valid ? R.color.color_primary : R.color.color_disabled_blue));
    }

    private void submit() {
        viewModel.setSignupEmail(binding.etEmail.getText().toString().trim()); viewModel.setSignupFullName(binding.etFullName.getText().toString().trim()); viewModel.setSignupUsername(binding.etUsername.getText().toString().trim().toLowerCase()); viewModel.setSignupPassword(binding.etPassword.getText().toString());
        binding.progressBar.setVisibility(View.VISIBLE); binding.btnSignUp.setEnabled(false);
        viewModel.completeSignUp().observe(getViewLifecycleOwner(), r -> { if (r.status == Resource.Status.ERROR) { binding.progressBar.setVisibility(View.GONE); binding.tvEmailError.setText(r.message); binding.tvEmailError.setVisibility(View.VISIBLE); validate(); } else if (r.status == Resource.Status.SUCCESS) { binding.progressBar.setVisibility(View.GONE); AuthResponse auth = r.data; if (auth != null && auth.hasSession()) { android.content.Intent i = new android.content.Intent(requireContext(), MainActivity.class); i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK); startActivity(i); requireActivity().finish(); } else new AlertDialog.Builder(requireContext()).setMessage("Check your email to confirm your account.").setPositiveButton("OK", null).show(); } });
    }

    private void facebookDialog() { new AlertDialog.Builder(requireContext()).setMessage("Facebook login isn't available in this app yet. Please log in with your username or email.").setPositiveButton("OK", null).show(); }
    private void infoDialog(String title, String body) { new AlertDialog.Builder(requireContext()).setTitle(title).setMessage(body).setPositiveButton("OK", null).show(); }
    @Override public void onDestroyView() { if (usernameCheck != null) handler.removeCallbacks(usernameCheck); super.onDestroyView(); binding = null; }
}
