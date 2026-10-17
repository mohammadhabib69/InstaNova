package com.insta.nova.ui.auth;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import com.insta.nova.R;
import com.insta.nova.databinding.FragmentWizardWelcomeBinding;
import com.insta.nova.util.Constants;

/**
 * Screen 9 — Welcome (reference image 8). Family B, no Skip: it advances by itself
 * after 2.5s, and a tap advances immediately.
 */
public class WizardWelcomeFragment extends WizardStepFragment {

    private FragmentWizardWelcomeBinding binding;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean advanced;

    private final Runnable autoAdvance = this::goNext;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentWizardWelcomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        RegistrationViewModel viewModel =
                new ViewModelProvider(requireActivity()).get(RegistrationViewModel.class);

        String username = viewModel.getUsername();
        binding.tvWelcomeTitle.setText(getString(R.string.vibe_welcome_title,
                username == null || username.isEmpty() ? Constants.APP_NAME : username));

        binding.getRoot().setOnClickListener(v -> goNext());
        handler.postDelayed(autoAdvance, Constants.WELCOME_AUTO_ADVANCE_MS);
    }

    /** Guarded so an early tap and the timer cannot both navigate. */
    private void goNext() {
        if (advanced || binding == null) return;
        advanced = true;
        handler.removeCallbacks(autoAdvance);
        NavHostFragment.findNavController(this).navigate(R.id.action_welcome_to_vibe);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacks(autoAdvance);
        binding = null;
    }
}
