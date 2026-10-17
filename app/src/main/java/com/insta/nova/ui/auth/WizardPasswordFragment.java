package com.insta.nova.ui.auth;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import com.insta.nova.R;
import com.insta.nova.data.repository.PasswordSetupResult;
import com.insta.nova.databinding.FragmentWizardPasswordBinding;
import com.insta.nova.util.Constants;
import com.insta.nova.util.Resource;

/**
 * Screen 4 — "Create a password" (reference image 6).
 *
 * Also decides session persistence: checked (the default) keeps the session on
 * disk, unchecked moves it into memory so the next cold start opens Login.
 */
public class WizardPasswordFragment extends WizardStepFragment {

    private FragmentWizardPasswordBinding binding;
    private RegistrationViewModel viewModel;
    private boolean submitting;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentWizardPasswordBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(RegistrationViewModel.class);

        wireBack(binding.topbar.btnWizardBack);
        applyBottomInset(binding.btnBottomLink);

        binding.fieldPassword.setLabelRes(R.string.auth_field_password);
        binding.fieldPassword.enablePasswordToggle();
        binding.fieldPassword.setImeOptionsRaw(EditorInfo.IME_ACTION_DONE);

        binding.cbRemember.setChecked(viewModel.isRememberLogin());
        // Tapping the label toggles the box, so the whole row behaves like one control.
        binding.tvRememberLabel.setOnClickListener(v -> binding.cbRemember.toggle());
        binding.tvRememberLearnMore.setOnClickListener(v ->
                showInfo(R.string.auth_info_remember_title, R.string.auth_info_remember_body));

        binding.btnPrimary.setOnClickListener(v -> submit());
        binding.btnBottomLink.setOnClickListener(v -> discardAndLogIn());
        binding.fieldPassword.addTextChangedListener(new AuthFieldView.SimpleWatcher(this::updateState));
        binding.fieldPassword.input().setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit();
                return true;
            }
            return false;
        });

        updateState();
    }

    private void updateState() {
        if (submitting) return;
        setPrimaryEnabled(binding.btnPrimary,
                binding.fieldPassword.rawText().length() >= Constants.PASSWORD_MIN_LENGTH);
        if (binding.tvError.getVisibility() == View.VISIBLE
                && binding.fieldPassword.rawText().length() >= Constants.PASSWORD_MIN_LENGTH) {
            clearError();
        }
    }

    private void submit() {
        if (submitting) return;
        final String password = binding.fieldPassword.rawText();
        if (password.length() < Constants.PASSWORD_MIN_LENGTH) {
            showError(getString(R.string.auth_password_too_short));
            return;
        }
        viewModel.setRememberLogin(binding.cbRemember.isChecked());

        setLoading(true);
        observePasswordResult(viewModel.setPasswordForSignup(password, null), password, true);
    }

    /**
     * @param allowPrompt true only for the first attempt. Passed as false for the
     *                    reauthentication retry so a second recent-login refusal can
     *                    never reopen the prompt.
     */
    private void observePasswordResult(LiveData<PasswordSetupResult> live,
                                       final String newPassword, final boolean allowPrompt) {
        live.observe(getViewLifecycleOwner(), outcome -> {
            if (outcome == null || outcome.status == PasswordSetupResult.Status.LOADING) return;

            if (outcome.status == PasswordSetupResult.Status.SUCCESS) {
                viewModel.setPasswordSet(true);
                viewModel.applyRememberPreference();
                NavHostFragment.findNavController(this).navigate(R.id.action_password_to_birthday);
                return;
            }

            setLoading(false);
            switch (outcome.status) {
                case NEEDS_CURRENT_PASSWORD:
                    if (allowPrompt) {
                        showCurrentPasswordDialog(newPassword);
                    } else {
                        showError(getString(R.string.auth_password_reauth_required));
                    }
                    return;
                case WRONG_CURRENT_PASSWORD:
                    showError(outcome.message != null
                            ? outcome.message : getString(R.string.auth_reauth_wrong));
                    return;
                case FAILED:
                default:
                    showError(outcome.message != null
                            ? outcome.message : getString(R.string.auth_error_generic));
            }
        });
    }

    /**
     * Shown once, only when Firebase reports ERROR_REQUIRES_RECENT_LOGIN.
     *
     * <p>The copy is deliberately honest: signup creates the account with a throwaway
     * password, so a user who has never set one cannot supply it. The dialog therefore
     * offers the supported route (Firebase's password-reset email) instead of asking
     * for a password the account may not have.
     */
    private void showCurrentPasswordDialog(final String newPassword) {
        if (binding == null || !isAdded()) return;

        final EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint(R.string.auth_reauth_field);
        input.setSingleLine(true);

        int pad = Math.round(24 * getResources().getDisplayMetrics().density);
        FrameLayout container = new FrameLayout(requireContext());
        container.setPadding(pad, 0, pad, 0);
        container.addView(input);

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.auth_reauth_title)
                .setMessage(R.string.auth_reauth_body)
                .setView(container)
                .setPositiveButton(R.string.auth_reauth_continue, (dialog, which) -> {
                    String current = input.getText().toString();
                    if (current.isEmpty()) {
                        showError(getString(R.string.auth_reauth_field));
                        return;
                    }
                    setLoading(true);
                    observePasswordResult(
                            viewModel.setPasswordForSignup(newPassword, current), newPassword, false);
                })
                .setNeutralButton(R.string.auth_reauth_send_reset, (dialog, which) -> sendResetLink())
                .setNegativeButton(R.string.auth_reauth_cancel, null)
                .show();
    }

    /** Supported recovery route when the current password cannot be supplied. */
    private void sendResetLink() {
        final String email = viewModel.getAccountEmail();
        if (email.isEmpty()) {
            showError(getString(R.string.auth_email_invalid));
            return;
        }
        viewModel.recoverPassword(email).observe(getViewLifecycleOwner(), resource -> {
            if (resource == null || resource.status == Resource.Status.LOADING) return;
            if (resource.status == Resource.Status.SUCCESS) {
                Toast.makeText(requireContext(), getString(R.string.email_sent_to, email),
                        Toast.LENGTH_LONG).show();
            } else {
                showError(resource.message != null
                        ? resource.message : getString(R.string.auth_error_generic));
            }
        });
    }

    private void setLoading(boolean loading) {
        submitting = loading;
        binding.progressPrimary.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnPrimary.setText(loading ? "" : getString(R.string.auth_next));
        binding.btnPrimary.setEnabled(!loading);
        binding.btnPrimary.setAlpha(loading ? 1f : 0.4f);
        if (!loading) updateState();
    }

    private void showError(String message) {
        binding.tvError.setText(message);
        binding.tvError.setVisibility(View.VISIBLE);
        binding.fieldPassword.setErrorState(true);
    }

    private void clearError() {
        binding.tvError.setVisibility(View.GONE);
        binding.fieldPassword.setErrorState(false);
    }

    private void discardAndLogIn() {
        viewModel.reset();
        viewModel.clearSession();
        NavHostFragment.findNavController(this).navigate(R.id.action_wizard_to_login);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
