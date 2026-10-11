package com.insta.clone.ui.auth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.insta.clone.R;
import com.insta.clone.databinding.FragmentSignupBirthdayBinding;
import com.insta.clone.util.Validators;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Step 3 of Signup Wizard: Birthday and age validation (>= 13 years old).
 */
public class SignupBirthdayFragment extends Fragment {

    private FragmentSignupBirthdayBinding binding;
    private AuthViewModel viewModel;
    private final Calendar selectedCalendar = Calendar.getInstance();
    private final SimpleDateFormat displayFormat = new SimpleDateFormat("MMMM d, yyyy", Locale.US);
    private final SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSignupBirthdayBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);

        binding.ivBack.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());

        // Default to 18 years ago
        selectedCalendar.add(Calendar.YEAR, -18);
        int year = selectedCalendar.get(Calendar.YEAR);
        int month = selectedCalendar.get(Calendar.MONTH);
        int day = selectedCalendar.get(Calendar.DAY_OF_MONTH);

        binding.datePicker.init(year, month, day, (view1, y, m, d) -> {
            selectedCalendar.set(y, m, d);
            updateBirthdayDisplay(y, m, d);
        });

        updateBirthdayDisplay(year, month, day);

        binding.btnNext.setOnClickListener(v -> {
            viewModel.setSignupBirthday(isoFormat.format(selectedCalendar.getTime()));
            Navigation.findNavController(v).navigate(R.id.action_signupBirthday_to_name);
        });
    }

    private void updateBirthdayDisplay(int y, int m, int d) {
        binding.tvBirthdayDisplay.setText(displayFormat.format(selectedCalendar.getTime()));
        boolean isEligible = Validators.isAtLeast13YearsOld(y, m, d);

        if (!isEligible) {
            binding.tvAgeError.setVisibility(View.VISIBLE);
            binding.btnNext.setEnabled(false);
        } else {
            binding.tvAgeError.setVisibility(View.GONE);
            binding.btnNext.setEnabled(true);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
