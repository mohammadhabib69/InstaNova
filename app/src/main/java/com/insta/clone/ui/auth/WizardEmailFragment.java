package com.insta.clone.ui.auth;

import android.os.Bundle;
import android.text.InputType;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import com.insta.clone.R;
import com.insta.clone.databinding.FragmentWizardEmailBinding;
import com.insta.clone.util.Resource;

/**
 * Screen 2 — "What's your email address?" (no reference screenshot; same Family A
 * style as the surrounding steps).
 *
 * 'Next' posts to /auth/v1/otp with create_user, which emails the 6-digit code and
 * brings the account into existence, then moves on to the Confirmation code step.
 */
public class WizardEmailFragment extends WizardStepFragment {

    private FragmentWizardEmailBinding binding;
    private RegistrationViewModel viewModel;
    private boolean submitting;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentWizardEmailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(RegistrationViewModel.class);

        wireBack(binding.topbar.btnWizardBack);
        applyBottomInset(binding.btnBottomLink);

        binding.fieldEmail.setLabelRes(R.string.auth_field_email);
        binding.fieldEmail.setInputTypeRaw(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        binding.fieldEmail.setImeOptionsRaw(EditorInfo.IME_ACTION_DONE);
        binding.fieldEmail.enableClearWhenNotEmpty();
        if (notEmpty(viewModel.getEmail())) binding.fieldEmail.setValueText(viewModel.getEmail());

        binding.btnPrimary.setOnClickListener(v -> submit());
        binding.btnSecondary.setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());
        binding.btnBottomLink.setOnClickListener(v -> discardAndLogIn());
        binding.fieldEmail.addTextChangedListener(new AuthFieldView.SimpleWatcher(this::updateState));
        binding.fieldEmail.input().setOnEditorActionListener((v, actionId, event) -> {
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
        String value = binding.fieldEmail.text();
        setPrimaryEnabled(binding.btnPrimary, Patterns.EMAIL_ADDRESS.matcher(value).matches());
    }

    private void submit() {
        if (submitting) return;
        final String email = binding.fieldEmail.text();
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) return;

        setLoading(true);
        viewModel.requestOtp(email).observe(getViewLifecycleOwner(), resource -> {
            if (resource == null || resource.status == Resource.Status.LOADING) return;
            setLoading(false);
            if (resource.status == Resource.Status.SUCCESS) {
                viewModel.setEmail(email);
                viewModel.markCodeSent();
                NavHostFragment.findNavController(this).navigate(R.id.action_email_to_code);
                return;
            }
            showError(resource.message != null ? resource.message
                    : getString(R.string.auth_error_generic));
            updateState();
        });
    }

    private void setLoading(boolean loading) {
        submitting = loading;
        binding.progressPrimary.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnPrimary.setText(loading ? "" : getString(R.string.auth_next));
        binding.btnPrimary.setEnabled(!loading);
        binding.btnPrimary.setAlpha(loading ? 1f : (binding.btnPrimary.isEnabled() ? 1f : 0.4f));
        if (!loading) updateState();
    }

    private void showError(String message) {
        binding.tvError.setText(message);
        binding.tvError.setVisibility(View.VISIBLE);
        binding.fieldEmail.setErrorState(true);
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
