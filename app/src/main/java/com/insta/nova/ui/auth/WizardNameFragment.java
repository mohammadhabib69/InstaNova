package com.insta.nova.ui.auth;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import com.insta.nova.R;
import com.insta.nova.databinding.FragmentWizardNameBinding;

/** Screen 6 — "What's your name?" (reference image 4). 1-50 characters, trimmed. */
public class WizardNameFragment extends WizardStepFragment {

    private static final int MAX_NAME_LENGTH = 50;

    private FragmentWizardNameBinding binding;
    private RegistrationViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentWizardNameBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(RegistrationViewModel.class);

        wireBack(binding.topbar.btnWizardBack);
        applyBottomInset(binding.btnBottomLink);

        binding.fieldName.setLabelRes(R.string.auth_field_full_name);
        binding.fieldName.setInputTypeRaw(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        binding.fieldName.setImeOptionsRaw(EditorInfo.IME_ACTION_DONE);
        binding.fieldName.enableClearWhenNotEmpty();
        if (notEmpty(viewModel.getFullName())) binding.fieldName.setValueText(viewModel.getFullName());

        binding.btnPrimary.setOnClickListener(v -> submit());
        binding.btnBottomLink.setOnClickListener(v -> discardAndLogIn());
        binding.fieldName.addTextChangedListener(new AuthFieldView.SimpleWatcher(this::updateState));
        binding.fieldName.input().setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit();
                return true;
            }
            return false;
        });

        updateState();
    }

    private void updateState() {
        int length = binding.fieldName.text().length();
        setPrimaryEnabled(binding.btnPrimary, length >= 1 && length <= MAX_NAME_LENGTH);
    }

    private void submit() {
        String name = binding.fieldName.text();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) return;
        viewModel.setFullName(name);
        // Seed the username step with a suggestion built from the name.
        if (!notEmpty(viewModel.getUsername())) {
            viewModel.setUsername(RegistrationViewModel.buildUsernameSuggestion(name));
        }
        NavHostFragment.findNavController(this).navigate(R.id.action_name_to_username);
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
