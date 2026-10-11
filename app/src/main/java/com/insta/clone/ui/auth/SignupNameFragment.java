package com.insta.clone.ui.auth;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.insta.clone.R;
import com.insta.clone.databinding.FragmentSignupNameBinding;

/**
 * Step 4 of Signup Wizard: Full Name entry.
 */
public class SignupNameFragment extends Fragment {

    private FragmentSignupNameBinding binding;
    private AuthViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSignupNameBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);

        binding.ivBack.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());

        binding.etFullName.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int count, int after) {
                binding.btnNext.setEnabled(s != null && !s.toString().trim().isEmpty());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        binding.btnNext.setEnabled(false);
        binding.btnNext.setOnClickListener(v -> {
            String name = binding.etFullName.getText().toString().trim();
            viewModel.setSignupFullName(name);
            Navigation.findNavController(v).navigate(R.id.action_signupName_to_username);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
