package com.insta.clone.ui.auth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import com.insta.clone.R;
import com.insta.clone.databinding.FragmentBioEditBinding;
import com.insta.clone.util.Constants;
import com.insta.clone.util.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * Bio editor, reached from 'Add bio' on the Profile-vibe screen.
 * Family B chrome: back arrow, centred 'Bio', blue 'Done', 150-character counter.
 */
public class BioEditFragment extends WizardStepFragment {

    private FragmentBioEditBinding binding;
    private RegistrationViewModel viewModel;
    private boolean saving;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentBioEditBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(RegistrationViewModel.class);

        wireBack(binding.btnBioBack);

        binding.etBio.setText(viewModel.getBio());
        binding.etBio.setSelection(binding.etBio.length());
        binding.etBio.addTextChangedListener(new AuthFieldView.SimpleWatcher(this::updateCounter));
        binding.btnBioDone.setOnClickListener(v -> save());

        updateCounter();
    }

    private void updateCounter() {
        binding.tvBioCounter.setText(getString(R.string.vibe_bio_counter, binding.etBio.length()));
    }

    private void save() {
        if (saving) return;
        final String bio = binding.etBio.getText().toString().trim();
        if (bio.length() > Constants.BIO_MAX_LENGTH) return;

        saving = true;
        binding.btnBioDone.setAlpha(0.5f);
        Map<String, Object> fields = new HashMap<>();
        fields.put("bio", bio);
        viewModel.patchProfileFields(fields).observe(getViewLifecycleOwner(), resource -> {
            if (resource == null || resource.status == Resource.Status.LOADING) return;
            saving = false;
            binding.btnBioDone.setAlpha(1f);
            if (resource.status == Resource.Status.SUCCESS) {
                viewModel.setBio(bio);
                NavHostFragment.findNavController(this).popBackStack();
                return;
            }
            android.widget.Toast.makeText(requireContext(),
                    resource.message == null ? getString(R.string.auth_error_generic) : resource.message,
                    android.widget.Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
