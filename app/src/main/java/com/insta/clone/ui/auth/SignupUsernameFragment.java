package com.insta.clone.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.insta.clone.R;
import com.insta.clone.databinding.FragmentSignupUsernameBinding;
import com.insta.clone.ui.main.MainActivity;
import com.insta.clone.util.KeyboardUtils;
import com.insta.clone.util.Resource;
import com.insta.clone.util.Validators;

/**
 * Step 5 of Signup Wizard: Username validation with 500ms debounce and account creation.
 */
public class SignupUsernameFragment extends Fragment {

    private FragmentSignupUsernameBinding binding;
    private AuthViewModel viewModel;
    private final Handler debounceHandler = new Handler(Looper.getMainLooper());
    private Runnable checkRunnable;
    private boolean isUsernameAvailable = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSignupUsernameBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);

        binding.ivBack.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());
        binding.btnSignUp.setEnabled(false);

        binding.etUsername.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int count, int after) {
                if (checkRunnable != null) {
                    debounceHandler.removeCallbacks(checkRunnable);
                }
                binding.ivCheckMark.setVisibility(View.GONE);
                binding.tvUsernameError.setVisibility(View.GONE);
                isUsernameAvailable = false;
                binding.btnSignUp.setEnabled(false);

                final String username = s != null ? s.toString().trim().toLowerCase() : "";
                if (username.isEmpty()) return;

                if (!Validators.isValidUsername(username)) {
                    binding.tvUsernameError.setText("Usernames can only contain letters, numbers, underscores and periods (3-30 characters).");
                    binding.tvUsernameError.setVisibility(View.VISIBLE);
                    return;
                }

                // Debounce 500ms
                checkRunnable = () -> checkAvailability(username);
                debounceHandler.postDelayed(checkRunnable, 500);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        binding.btnSignUp.setOnClickListener(v -> {
            KeyboardUtils.hideKeyboard(requireActivity());
            String username = binding.etUsername.getText().toString().trim().toLowerCase();
            viewModel.setSignupUsername(username);

            setLoading(true);
            viewModel.completeSignUp().observe(getViewLifecycleOwner(), resource -> {
                setLoading(false);
                if (resource.status == Resource.Status.SUCCESS) {
                    if (resource.data != null && resource.data.getAccessToken() != null) {
                        Intent intent = new Intent(requireContext(), MainActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        requireActivity().finish();
                    } else {
                        Toast.makeText(requireContext(), R.string.check_email_confirm, Toast.LENGTH_LONG).show();
                        Navigation.findNavController(v).popBackStack(R.id.loginFragment, false);
                    }
                } else if (resource.status == Resource.Status.ERROR) {
                    Toast.makeText(requireContext(), resource.message != null ? resource.message : "Registration failed", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void checkAvailability(String username) {
        viewModel.checkUsername(username).observe(getViewLifecycleOwner(), resource -> {
            if (resource.status == Resource.Status.SUCCESS) {
                boolean available = Boolean.TRUE.equals(resource.data);
                if (available) {
                    isUsernameAvailable = true;
                    binding.ivCheckMark.setVisibility(View.VISIBLE);
                    binding.tvUsernameError.setVisibility(View.GONE);
                    binding.btnSignUp.setEnabled(true);
                } else {
                    isUsernameAvailable = false;
                    binding.ivCheckMark.setVisibility(View.GONE);
                    binding.tvUsernameError.setText("The username " + username + " is not available.");
                    binding.tvUsernameError.setVisibility(View.VISIBLE);
                    binding.btnSignUp.setEnabled(false);
                }
            }
        });
    }

    private void setLoading(boolean loading) {
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnSignUp.setEnabled(!loading && isUsernameAvailable);
        binding.etUsername.setEnabled(!loading);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (checkRunnable != null) {
            debounceHandler.removeCallbacks(checkRunnable);
        }
        binding = null;
    }
}
