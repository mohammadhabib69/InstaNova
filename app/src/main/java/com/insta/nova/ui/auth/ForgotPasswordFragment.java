package com.insta.nova.ui.auth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.insta.nova.R;
import com.insta.nova.databinding.FragmentForgotPasswordBinding;
import com.insta.nova.util.KeyboardUtils;
import com.insta.nova.util.Resource;
import com.insta.nova.util.Validators;

/**
 * Password recovery screen sending magic recovery reset link.
 */
public class ForgotPasswordFragment extends Fragment {

    private FragmentForgotPasswordBinding binding;
    private AuthViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentForgotPasswordBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);

        binding.ivBack.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());

        binding.btnSendLink.setOnClickListener(v -> {
            KeyboardUtils.hideKeyboard(requireActivity());
            String email = binding.etEmail.getText().toString().trim();

            if (!Validators.isValidEmail(email)) {
                Toast.makeText(requireContext(), "Please enter a valid email address", Toast.LENGTH_SHORT).show();
                return;
            }

            binding.progressBar.setVisibility(View.VISIBLE);
            binding.btnSendLink.setEnabled(false);

            viewModel.recoverPassword(email).observe(getViewLifecycleOwner(), resource -> {
                binding.progressBar.setVisibility(View.GONE);
                binding.btnSendLink.setEnabled(true);

                if (resource.status == Resource.Status.SUCCESS) {
                    binding.tvSuccessMessage.setText(getString(R.string.email_sent_to, email));
                    binding.tvSuccessMessage.setVisibility(View.VISIBLE);
                } else if (resource.status == Resource.Status.ERROR) {
                    Toast.makeText(requireContext(), resource.message != null ? resource.message : "Error sending link", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
