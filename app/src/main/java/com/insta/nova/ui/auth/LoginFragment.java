package com.insta.nova.ui.auth;

import android.content.Intent;
import android.os.Bundle;
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
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.insta.nova.R;
import com.insta.nova.data.local.SavedAccountsStore;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.model.SavedAccount;
import com.insta.nova.databinding.FragmentLoginBinding;
import com.insta.nova.ui.main.MainActivity;
import com.insta.nova.util.Constants;
import com.insta.nova.util.Resource;

import java.util.Map;
import java.util.regex.Pattern;

/** PAGE 16 — Screen B. Login form backed by FirebaseAuth. */
public class LoginFragment extends Fragment {

    private static final Pattern TEMP_USERNAME = Pattern.compile("^user_[0-9a-f]{10}$");

    private FragmentLoginBinding binding;
    private AuthViewModel viewModel;
    private boolean passwordVisible;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle state) {
        binding = FragmentLoginBinding.inflate(inflater, parent, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        viewModel = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);

        binding.tvFooter.setText(Constants.FOOTER_BRAND);

        String prefill = getArguments() == null ? null : getArguments().getString("prefillUsername");
        if (prefill != null && !prefill.isEmpty()) {
            binding.etIdentifier.setText(prefill);
            binding.etIdentifier.setSelection(prefill.length());
            binding.etPassword.requestFocus();
        }

        binding.languageRow.setOnClickListener(v -> new AlertDialog.Builder(requireContext())
                .setTitle(R.string.login_language)
                .setSingleChoiceItems(new String[]{getString(R.string.login_language)}, 0, null)
                .setPositiveButton("OK", null)
                .show());

        binding.ivPasswordToggle.setOnClickListener(v -> {
            passwordVisible = !passwordVisible;
            binding.etPassword.setTransformationMethod(passwordVisible
                    ? HideReturnsTransformationMethod.getInstance()
                    : PasswordTransformationMethod.getInstance());
            binding.ivPasswordToggle.setImageResource(passwordVisible
                    ? R.drawable.ic_visibility_off : R.drawable.ic_visibility);
            binding.etPassword.setSelection(binding.etPassword.length());
        });

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { clearErrors(); }
            @Override public void afterTextChanged(Editable s) { }
        };
        binding.etIdentifier.addTextChangedListener(watcher);
        binding.etPassword.addTextChangedListener(watcher);
        binding.ivPasswordToggle.setVisibility(View.GONE);
        binding.etPassword.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                binding.ivPasswordToggle.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        binding.btnLogin.setOnClickListener(v -> submit());
        binding.tvForgotPassword.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.action_login_to_forgotPassword));
        binding.btnCreateAccount.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.action_login_to_signupEmail));
    }

    private void clearErrors() {
        binding.tvIdentifierError.setVisibility(View.GONE);
        binding.tvPasswordError.setVisibility(View.GONE);
        binding.etIdentifier.setBackgroundResource(R.drawable.bg_auth_field);
        binding.etPassword.setBackgroundResource(R.drawable.bg_auth_field);
    }

    private void submit() {
        clearErrors();
        String identifier = binding.etIdentifier.getText().toString().trim();
        String password = binding.etPassword.getText().toString();

        boolean bad = false;
        if (identifier.isEmpty()) {
            binding.tvIdentifierError.setText(R.string.login_err_identifier);
            binding.tvIdentifierError.setVisibility(View.VISIBLE);
            binding.etIdentifier.setBackgroundResource(R.drawable.bg_auth_field_error);
            bad = true;
        }
        if (password.isEmpty()) {
            binding.tvPasswordError.setText(R.string.login_err_password);
            binding.tvPasswordError.setVisibility(View.VISIBLE);
            binding.etPassword.setBackgroundResource(R.drawable.bg_auth_field_error);
            bad = true;
        }
        if (bad) return;

        if (!identifier.contains("@") && identifier.matches("^[+0-9 ()-]{7,}$")
                && identifier.replaceAll("[^0-9]", "").length() >= 7) {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.login_mobile_title)
                    .setMessage(R.string.login_mobile_body)
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        setLoading(true);
        viewModel.login(identifier, password).observe(getViewLifecycleOwner(), resource -> {
            if (resource == null || resource.status == Resource.Status.LOADING) return;
            if (resource.status == Resource.Status.ERROR) {
                setLoading(false);
                showLoginError(resource.message, identifier);
                return;
            }
            onLoggedIn();
        });
    }

    private void showLoginError(String message, String identifier) {
        String lower = message == null ? "" : message.toLowerCase();
        if (lower.contains("password was incorrect") || lower.contains("invalid_credential")) {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.login_incorrect_title)
                    .setMessage(R.string.login_incorrect_body)
                    .setNegativeButton(R.string.login_try_again, null)
                    .setPositiveButton(R.string.login_forgot_link, (d, w) ->
                            Navigation.findNavController(binding.getRoot())
                                    .navigate(R.id.action_login_to_forgotPassword))
                    .show();
            return;
        }
        if (lower.contains("no account") || lower.contains("doesn't belong")) {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.login_cant_find_title)
                    .setMessage(getString(R.string.login_cant_find_body, identifier))
                    .setNegativeButton(R.string.login_try_again, null)
                    .setPositiveButton(R.string.login_sign_up, (d, w) ->
                            Navigation.findNavController(binding.getRoot())
                                    .navigate(R.id.action_login_to_signupEmail))
                    .show();
            return;
        }
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
        android.util.Log.w("InstaApi", "login failed: " + message);
    }

    private void onLoggedIn() {
        SessionManager session = SessionManager.getInstance(requireContext());
        String uid = session.getFirebaseUid();
        String username = session.getUsername();
        String avatar = session.getAvatarUrl();

        SavedAccountsStore.getInstance(requireContext()).upsert(
                new SavedAccount(uid, username, "", avatar, System.currentTimeMillis()));

        viewModel.loadProfile(uid).observe(getViewLifecycleOwner(), resource -> {
            String profileUsername = null;
            if (resource != null && resource.status == Resource.Status.SUCCESS && resource.data != null) {
                Object v = ((Map<?, ?>) resource.data).get("username");
                profileUsername = v == null ? null : String.valueOf(v);
            }
            setLoading(false);
            if (profileUsername != null && TEMP_USERNAME.matcher(profileUsername).matches()) {
                // Sign-up never finished: resume the wizard at the password step.
                Intent intent = new Intent(requireContext(), AuthActivity.class);
                intent.putExtra(AuthActivity.EXTRA_RESUME_AT_PASSWORD, true);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                requireActivity().finish();
                return;
            }
            Intent intent = new Intent(requireContext(), MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            requireActivity().finish();
        });
    }

    private void setLoading(boolean loading) {
        binding.loginSpinner.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.tvLoginLabel.setVisibility(loading ? View.INVISIBLE : View.VISIBLE);
        binding.btnLogin.setClickable(!loading);
        binding.etIdentifier.setEnabled(!loading);
        binding.etPassword.setEnabled(!loading);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
