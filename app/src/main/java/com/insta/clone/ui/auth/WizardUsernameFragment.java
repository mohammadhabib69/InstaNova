package com.insta.clone.ui.auth;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import com.insta.clone.R;
import com.insta.clone.databinding.FragmentWizardUsernameBinding;
import com.insta.clone.util.Constants;
import com.insta.clone.util.Resource;

/**
 * Screen 7 — "Create a username" (no reference screenshot; same Family A style).
 *
 * Availability is checked live with a 500 ms debounce, showing a green tick when
 * free and a red glyph plus red text when taken. 'Next' unlocks only when free.
 */
public class WizardUsernameFragment extends WizardStepFragment {

    private static final long DEBOUNCE_MS = Constants.USERNAME_DEBOUNCE_MS;

    private FragmentWizardUsernameBinding binding;
    private RegistrationViewModel viewModel;
    private final Handler debounceHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingCheck;
    private boolean available;
    private boolean checking;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentWizardUsernameBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(RegistrationViewModel.class);

        wireBack(binding.topbar.btnWizardBack);
        applyBottomInset(binding.btnBottomLink);

        binding.fieldUsername.setLabelRes(R.string.auth_field_username);
        binding.fieldUsername.setInputTypeRaw(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        binding.fieldUsername.setImeOptionsRaw(EditorInfo.IME_ACTION_DONE);
        if (notEmpty(viewModel.getUsername())) binding.fieldUsername.setValueText(viewModel.getUsername());

        binding.btnPrimary.setOnClickListener(v -> submit());
        binding.btnBottomLink.setOnClickListener(v -> discardAndLogIn());
        binding.fieldUsername.addTextChangedListener(new AuthFieldView.SimpleWatcher(this::onTyped));
        binding.fieldUsername.input().setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit();
                return true;
            }
            return false;
        });

        // Seed a suggestion derived from the name, then check it.
        if (binding.fieldUsername.isEmpty()) {
            binding.fieldUsername.setValueText(
                    RegistrationViewModel.buildUsernameSuggestion(viewModel.getFullName()));
        }
        onTyped();
    }

    private void onTyped() {
        available = false;
        checking = false;
        setPrimaryEnabled(binding.btnPrimary, false);
        binding.tvError.setVisibility(View.GONE);
        binding.fieldUsername.setErrorState(false);
        binding.fieldUsername.setTrailingMode(AuthFieldView.Trailing.NONE);

        final String candidate = binding.fieldUsername.text();
        if (candidate.isEmpty()) return;

        if (!isWellFormed(candidate)) {
            binding.tvError.setText(R.string.auth_username_rules);
            binding.tvError.setVisibility(View.VISIBLE);
            binding.fieldUsername.setErrorState(true);
            return;
        }

        cancelPending();
        pendingCheck = () -> runAvailabilityCheck(candidate);
        debounceHandler.postDelayed(pendingCheck, DEBOUNCE_MS);
    }

    /** a-z 0-9 . _ and 3-30 characters. */
    private boolean isWellFormed(String value) {
        return value.matches("^[a-z0-9._]{3,30}$");
    }

    private void runAvailabilityCheck(final String candidate) {
        checking = true;
        viewModel.checkUsername(candidate).observe(getViewLifecycleOwner(), resource -> {
            if (resource == null || resource.status == Resource.Status.LOADING) return;
            // A newer keystroke has already superseded this answer.
            if (!candidate.equals(binding.fieldUsername.text())) return;
            checking = false;

            boolean free = resource.status == Resource.Status.SUCCESS
                    && Boolean.TRUE.equals(resource.data);
            available = free;
            if (free) {
                binding.fieldUsername.setTrailingMode(AuthFieldView.Trailing.CHECK);
                binding.tvError.setVisibility(View.GONE);
                binding.fieldUsername.setErrorState(false);
            } else {
                binding.fieldUsername.setErrorState(true);
                binding.tvError.setText(R.string.auth_username_taken);
                binding.tvError.setVisibility(View.VISIBLE);
            }
            setPrimaryEnabled(binding.btnPrimary, free);
        });
    }

    private void submit() {
        String value = binding.fieldUsername.text();
        if (!available || !isWellFormed(value)) return;
        viewModel.setUsername(value);
        NavHostFragment.findNavController(this).navigate(R.id.action_username_to_terms);
    }

    private void cancelPending() {
        if (pendingCheck != null) {
            debounceHandler.removeCallbacks(pendingCheck);
            pendingCheck = null;
        }
    }

    private void discardAndLogIn() {
        viewModel.reset();
        viewModel.clearSession();
        NavHostFragment.findNavController(this).navigate(R.id.action_wizard_to_login);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cancelPending();
        binding = null;
    }
}
