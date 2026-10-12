package com.insta.clone.ui.auth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import com.insta.clone.R;
import com.insta.clone.databinding.FragmentWizardPasswordBinding;
import com.insta.clone.util.Constants;
import com.insta.clone.util.Resource;

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
        viewModel.updatePassword(password).observe(getViewLifecycleOwner(), resource -> {
            if (resource == null || resource.status == Resource.Status.LOADING) return;
            if (resource.status == Resource.Status.SUCCESS) {
                viewModel.setPasswordSet(true);
                viewModel.applyRememberPreference();
                NavHostFragment.findNavController(this).navigate(R.id.action_password_to_birthday);
                return;
            }
            setLoading(false);
            showError(resource.message != null ? resource.message : getString(R.string.auth_error_generic));
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
