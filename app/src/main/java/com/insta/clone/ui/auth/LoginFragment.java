package com.insta.clone.ui.auth;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.insta.clone.InstaApp;
import com.insta.clone.R;
import com.insta.clone.databinding.FragmentLoginBinding;
import com.insta.clone.ui.main.MainActivity;
import com.insta.clone.util.Constants;
import com.insta.clone.util.Resource;

/** Pixel-focused login screen with email-or-username resolution through Supabase RPC. */
public class LoginFragment extends Fragment {
    private FragmentLoginBinding binding; private AuthViewModel viewModel; private boolean passwordVisible;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle state) { binding = FragmentLoginBinding.inflate(inflater, parent, false); return binding.getRoot(); }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state); viewModel = new ViewModelProvider(requireActivity()).get(AuthViewModel.class); binding.tvWordmark.setTypeface(InstaApp.getWordmarkTypeface(requireContext()));
        if (!Constants.SHOW_FACEBOOK_BUTTON) { binding.btnFacebook.setVisibility(View.GONE); binding.facebookDivider.setVisibility(View.GONE); }
        binding.tvLanguage.setOnClickListener(v -> new AlertDialog.Builder(requireContext()).setItems(new String[]{"English (United States)"}, null).show());
        binding.btnFacebook.setOnClickListener(v -> new AlertDialog.Builder(requireContext()).setMessage("Facebook login isn't available in this app yet. Please log in with your username or email.").setPositiveButton("OK", null).show());
        binding.ivPasswordToggle.setOnClickListener(v -> { passwordVisible = !passwordVisible; binding.etPassword.setTransformationMethod(passwordVisible ? HideReturnsTransformationMethod.getInstance() : PasswordTransformationMethod.getInstance()); binding.ivPasswordToggle.setImageResource(passwordVisible ? R.drawable.ic_visibility_off : R.drawable.ic_visibility); binding.etPassword.setSelection(binding.etPassword.length()); });
        TextWatcher watcher = new TextWatcher() { @Override public void beforeTextChanged(CharSequence s,int st,int c,int a){} @Override public void onTextChanged(CharSequence s,int st,int b,int c){ updateState(); } @Override public void afterTextChanged(Editable e){} };
        binding.etIdentifier.addTextChangedListener(watcher); binding.etPassword.addTextChangedListener(watcher); updateState();
        binding.btnLogin.setOnClickListener(v -> submit());
        binding.tvForgotPassword.setOnClickListener(v -> Navigation.findNavController(v).navigate(R.id.action_login_to_forgotPassword));
        binding.tvSignup.setOnClickListener(v -> Navigation.findNavController(v).navigate(R.id.action_login_to_signupEmail));
    }
    private void updateState() { boolean valid = binding.etIdentifier.length() > 0 && binding.etPassword.length() > 0; binding.btnLogin.setEnabled(valid); binding.btnLogin.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), valid ? R.color.color_primary : R.color.color_disabled_blue)); binding.ivPasswordToggle.setVisibility(binding.etPassword.length() > 0 ? View.VISIBLE : View.GONE); }
    private void submit() {
        binding.progressBar.setVisibility(View.VISIBLE); binding.btnLogin.setEnabled(false); binding.tvError.setVisibility(View.GONE);
        viewModel.login(binding.etIdentifier.getText().toString().trim(), binding.etPassword.getText().toString()).observe(getViewLifecycleOwner(), r -> {
            if (r.status == Resource.Status.ERROR) { binding.progressBar.setVisibility(View.GONE); binding.tvError.setText(r.message); binding.tvError.setVisibility(View.VISIBLE); updateState(); }
            else if (r.status == Resource.Status.SUCCESS) { Intent i = new Intent(requireContext(), MainActivity.class); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK); startActivity(i); requireActivity().finish(); }
        });
    }
    @Override public void onDestroyView() { super.onDestroyView(); binding = null; }
}
