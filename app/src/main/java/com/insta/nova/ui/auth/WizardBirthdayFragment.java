package com.insta.nova.ui.auth;

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
import com.insta.nova.R;
import com.insta.nova.databinding.FragmentWizardBirthdayBinding;
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

        // No date is prefilled. Pre-selecting today made the screen open at "0 years
        // old" with Next already disabled, which reads as a broken button rather than
        // as a step that still needs input.
        binding.fieldBirthday.setHintRes(R.string.auth_birthday_hint);
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

    /**
     * Single place that turns the current birthday into what the screen shows.
     * Called on entry, and again from the picker callback, so choosing a date
     * validates and enables Next without any further interaction.
     */
    private void render() {
        String iso = viewModel.getBirthdayIso();
        BirthdayGate.State state = BirthdayGate.evaluate(iso);

        if (state == BirthdayGate.State.MISSING) {
            binding.fieldBirthday.setLabel(getString(R.string.auth_field_birthday_empty));
            binding.fieldBirthday.setValueText("");
        } else {
            binding.fieldBirthday.setLabel(
                    getString(R.string.auth_field_birthday, BirthdayGate.displayAge(iso)));
            binding.fieldBirthday.setValueText(RegistrationViewModel.formatIsoLong(iso));
        }

        // A disabled button swallows taps, so every disabled state carries its reason.
        setPrimaryEnabled(binding.btnPrimary, state == BirthdayGate.State.VALID);
        binding.fieldBirthday.setErrorState(state == BirthdayGate.State.TOO_YOUNG);

        binding.tvHint.setVisibility(state == BirthdayGate.State.MISSING ? View.VISIBLE : View.GONE);
        if (state == BirthdayGate.State.TOO_YOUNG) {
            binding.tvError.setText(R.string.auth_birthday_too_young);
            binding.tvError.setVisibility(View.VISIBLE);
        } else {
            binding.tvError.setVisibility(View.GONE);
        }
    }

    private void showDatePicker() {
        Calendar current = Calendar.getInstance();
        String iso = viewModel.getBirthdayIso();
        boolean hasDate = !iso.isEmpty();
        if (hasDate) {
            try {
                String[] p = iso.split("-");
                current.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]));
            } catch (Exception ignored) {
                current.setTimeInMillis(System.currentTimeMillis());
            }
        } else {
            // Open near a plausible birth year instead of today, which is 13+ years
            // of scrolling away from any valid answer.
            current.add(Calendar.YEAR, -18);
        }
        DatePickerDialog dialog = new DatePickerDialog(requireContext(),
                (picker, year, month, day) -> onBirthdayPicked(year, month, day),
                current.get(Calendar.YEAR), current.get(Calendar.MONTH), current.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        dialog.show();
    }

    /** Picker callback: store the ISO date, then re-run validation immediately. */
    private void onBirthdayPicked(int year, int monthZeroBased, int day) {
        viewModel.setBirthdayIso(String.format(java.util.Locale.US,
                "%04d-%02d-%02d", year, monthZeroBased + 1, day));
        render();
    }

    private void submit() {
        if (!BirthdayGate.canContinue(viewModel.getBirthdayIso())) {
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
