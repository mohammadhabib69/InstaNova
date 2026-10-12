package com.insta.clone.ui.auth;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Build;
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
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import com.insta.clone.R;
import com.insta.clone.databinding.FragmentWizardAllowBinding;
import com.insta.clone.ui.main.MainActivity;
import com.insta.clone.util.Constants;

/**
 * Screen 12 — "Allow Instagram to access your device?" (reference image 3).
 *
 * 'Next' asks for POST_NOTIFICATIONS on Android 13+ and then opens Home either
 * way; granting or denying must never crash. The Contacts row is visual only:
 * this app never reads or uploads contacts.
 */
public class WizardAllowAccessFragment extends WizardStepFragment {

    private FragmentWizardAllowBinding binding;
    private ActivityResultLauncher<String> notificationPermission;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        notificationPermission = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> openHome());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentWizardAllowBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        RegistrationViewModel viewModel =
                new ViewModelProvider(requireActivity()).get(RegistrationViewModel.class);

        binding.tvTitle.setText(getString(R.string.vibe_allow_title, Constants.APP_NAME));
        binding.tvHelper.setText(getString(R.string.vibe_allow_helper, Constants.APP_NAME));
        buildContactsDescription();

        // Contacts is design-only: it claims more than the app does.
        if (Constants.DECORATIVE_UI) {
            binding.rowContacts.setVisibility(View.VISIBLE);
            binding.rowContacts.setOnClickListener(v ->
                    com.insta.clone.util.ToastUtil.showNotAvailable(requireContext()));
        } else {
            binding.rowContacts.setVisibility(View.GONE);
        }

        binding.btnSkip.setOnClickListener(v -> openHome());
        binding.btnNext.setOnClickListener(v -> requestNotificationsThenFinish());

        applyBottomInset(binding.btnNext);
    }

    /** 'Contacts on this device will be… ' + a Learn more link that opens the info sheet. */
    private void buildContactsDescription() {
        String prefix = getString(R.string.vibe_contacts_description);
        String link = getString(R.string.auth_learn_more);
        SpannableStringBuilder sb = new SpannableStringBuilder(prefix + link);
        int start = prefix.length();
        int end = sb.length();
        sb.setSpan(new ClickableSpan() {
            @Override public void onClick(@NonNull View widget) {
                showInfo(R.string.auth_info_contacts_title, R.string.auth_info_contacts_body);
            }
        }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.vibe_accent_link)),
                start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        binding.tvContactsDesc.setText(sb);
        binding.tvContactsDesc.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void requestNotificationsThenFinish() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            openHome();
        }
    }

    /** Marks onboarding done before leaving, so it never shows again. */
    private void openHome() {
        if (getContext() == null) return;
        SharedPreferences prefs = requireContext()
                .getSharedPreferences(Constants.PREF_ONBOARDING, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(Constants.KEY_ONBOARDING_DONE, true).apply();

        Intent intent = new Intent(requireContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
