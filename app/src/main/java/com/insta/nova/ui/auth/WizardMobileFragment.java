package com.insta.nova.ui.auth;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import com.insta.nova.R;
import com.insta.nova.databinding.FragmentWizardMobileBinding;
import com.insta.nova.util.Constants;

/**
 * Screen 1 — "What's your mobile number?" (reference image 9).
 *
 * Phone sign-up needs an SMS provider this project does not have, so 'Next'
 * explains that and offers 'Use email'; both that and 'Sign up with email' open
 * the Email screen. The real path is email + 6-digit code.
 */
public class WizardMobileFragment extends WizardStepFragment {

    private FragmentWizardMobileBinding binding;
    private RegistrationViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentWizardMobileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(RegistrationViewModel.class);

        wireBack(binding.topbar.btnWizardBack);
        applyBottomInset(binding.btnBottomLink);

        binding.fieldMobile.setLabelRes(R.string.auth_field_mobile);
        binding.fieldMobile.setInputTypeRaw(InputType.TYPE_CLASS_PHONE);
        binding.fieldMobile.setImeOptionsRaw(EditorInfo.IME_ACTION_DONE);
        binding.fieldMobile.enableClearWhenNotEmpty();
        if (notEmpty(viewModel.getPhone())) binding.fieldMobile.setValueText(viewModel.getPhone());

        binding.tvLearnMore.setOnClickListener(v ->
                showInfo(R.string.auth_info_learn_more_title, R.string.auth_info_learn_more_body));
        binding.btnSecondary.setOnClickListener(v -> openEmail());
        binding.btnPrimary.setOnClickListener(v -> onNext());
        binding.btnBottomLink.setOnClickListener(v -> discardAndLogIn());
        binding.fieldMobile.addTextChangedListener(new AuthFieldView.SimpleWatcher(this::updateState));
        binding.fieldMobile.input().setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                onNext();
                return true;
            }
            return false;
        });

        updateState();
    }

    /** 'Next' is enabled from 7 digits, matching the reference's disabled state. */
    private void updateState() {
        String digits = binding.fieldMobile.text().replaceAll("[^0-9]", "");
        setPrimaryEnabled(binding.btnPrimary, digits.length() >= 7);
    }

    private void onNext() {
        if (viewModel == null) return;
        viewModel.setPhone(binding.fieldMobile.text());
        if (!Constants.PHONE_SIGNUP_ENABLED) {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.auth_phone_unavailable_title)
                    .setMessage(R.string.auth_phone_unavailable_message)
                    .setPositiveButton(R.string.auth_use_email, (d, w) -> openEmail())
                    .setNegativeButton(R.string.auth_cancel, null)
                    .show();
            return;
        }
        // An SMS provider is configured: the real phone-OTP hand-off goes here.
        openEmail();
    }

    private void openEmail() {
        viewModel.setPhone(binding.fieldMobile.text());
        NavHostFragment.findNavController(this).navigate(R.id.action_mobile_to_email);
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
