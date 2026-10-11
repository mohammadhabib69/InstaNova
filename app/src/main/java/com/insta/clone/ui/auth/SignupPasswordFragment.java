package com.insta.clone.ui.auth;

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
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.insta.clone.R;
import com.insta.clone.databinding.FragmentSignupPasswordBinding;
import com.insta.clone.util.Validators;

/**
 * Step 2 of Signup Wizard: Password creation.
 */
public class SignupPasswordFragment extends Fragment {

    private FragmentSignupPasswordBinding binding;
    private AuthViewModel viewModel;
    private boolean isPasswordVisible = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSignupPasswordBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);

        binding.ivBack.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());

        binding.ivPasswordToggle.setOnClickListener(v -> {
            isPasswordVisible = !isPasswordVisible;
            if (isPasswordVisible) {
                binding.etPassword.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                binding.ivPasswordToggle.setImageResource(R.drawable.ic_visibility_off);
            } else {
                binding.etPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
                binding.ivPasswordToggle.setImageResource(R.drawable.ic_visibility);
            }
            binding.etPassword.setSelection(binding.etPassword.getText().length());
        });

        binding.etPassword.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int count, int after) {
                binding.btnNext.setEnabled(Validators.isValidPassword(s != null ? s.toString() : ""));
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        binding.btnNext.setEnabled(false);
        binding.btnNext.setOnClickListener(v -> {
            String password = binding.etPassword.getText().toString();
            viewModel.setSignupPassword(password);
            Navigation.findNavController(v).navigate(R.id.action_signupPassword_to_birthday);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
