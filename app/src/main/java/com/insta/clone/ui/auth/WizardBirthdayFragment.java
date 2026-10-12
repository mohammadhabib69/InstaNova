package com.insta.clone.ui.auth;

import android.app.DatePickerDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import com.insta.clone.R;
import com.insta.clone.databinding.FragmentWizardBirthdayBinding;
import com.insta.clone.util.Constants;
import java.util.Calendar;

/**
 * Screen 5 — "What's your birthday?" (reference image 5).
 *
 * The field is a tap target that opens the system DatePickerDialog capped at
 * today; the label shows the live age and the date is kept as ISO yyyy-MM-dd.
 */
public class WizardBirthdayFragment extends WizardStepFragment {

    private FragmentWizardBirthdayBinding binding;
    private RegistrationViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentWizardBirthdayBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(RegistrationViewModel.class);

        wireBack(binding.topbar.btnWizardBack);
        applyBottomInset(binding.btnBottomLink);

        // Default to today, matching the reference's "October 12, 2026" / 0 years old.
        if (!notEmpty(viewModel.getBirthdayIso())) {
            viewModel.setBirthdayIso(RegistrationViewModel.todayIso());
        }

        binding.fieldBirthday.setReadOnly(this::showDatePicker);
        binding.btnPrimary.setOnClickListener(v -> submit());
        binding.btnBottomLink.setOnClickListener(v -> discardAndLogIn());

        buildSubtitle();
        render();
    }

    /** Subtitle + a bold, tappable 'Why do I need to provide my birthday?' link. */
    private void buildSubtitle() {
        String prefix = getString(R.string.auth_birthday_subtitle);
        String link = getString(R.string.auth_birthday_why);
        SpannableStringBuilder sb = new SpannableStringBuilder(prefix + link);
        int start = prefix.length();
        int end = sb.length();
        sb.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.auth_link)),
                start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ClickableSpan() {
            @Override public void onClick(@NonNull View widget) {
                showInfo(R.string.auth_info_birthday_title, R.string.auth_info_birthday_body);
            }
        }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        binding.tvSubtitle.setText(sb);
        binding.tvSubtitle.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void render() {
        String iso = viewModel.getBirthdayIso();
        int age = RegistrationViewModel.ageFromIso(iso);
        binding.fieldBirthday.setLabel(getString(R.string.auth_field_birthday, Math.max(age, 0)));
        binding.fieldBirthday.setValueText(RegistrationViewModel.formatIsoLong(iso));

        boolean tooYoung = age >= 0 && age < Constants.MIN_AGE;
        setPrimaryEnabled(binding.btnPrimary, age >= Constants.MIN_AGE);
        binding.fieldBirthday.setErrorState(tooYoung);
        if (tooYoung) {
            binding.tvError.setText(R.string.auth_birthday_too_young);
            binding.tvError.setVisibility(View.VISIBLE);
        } else {
            binding.tvError.setVisibility(View.GONE);
        }
    }

    private void showDatePicker() {
        Calendar current = Calendar.getInstance();
        String iso = viewModel.getBirthdayIso();
        try {
            String[] p = iso.split("-");
            current.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]));
        } catch (Exception ignored) {
            // keep today
        }
        DatePickerDialog dialog = new DatePickerDialog(requireContext(),
                (picker, year, month, day) -> {
                    viewModel.setBirthdayIso(String.format(java.util.Locale.US,
                            "%04d-%02d-%02d", year, month + 1, day));
                    render();
                },
                current.get(Calendar.YEAR), current.get(Calendar.MONTH), current.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        dialog.show();
    }

    private void submit() {
        if (RegistrationViewModel.ageFromIso(viewModel.getBirthdayIso()) < Constants.MIN_AGE) {
            render();
            return;
        }
        NavHostFragment.findNavController(this).navigate(R.id.action_birthday_to_name);
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
