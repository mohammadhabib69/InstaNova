package com.insta.clone.ui.auth;

import android.content.Intent;
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
import com.insta.clone.data.local.SessionManager;
import com.insta.clone.databinding.FragmentWizardTermsBinding;
import com.insta.clone.ui.main.MainActivity;
import com.insta.clone.util.Constants;
import com.insta.clone.util.Resource;

/**
 * Screen 8 — "Agree to Instagram's terms and policies" (reference image 2).
 *
 * 'I agree' performs the real account finalisation: it PATCHes the profile with
 * username / full_name / birthday, which is what turns the trigger's temporary
 * username into the chosen one. A 23505 unique violation sends the user back to
 * the Username step.
 */
public class WizardTermsFragment extends WizardStepFragment {

    private FragmentWizardTermsBinding binding;
    private RegistrationViewModel viewModel;
    private boolean submitting;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentWizardTermsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(RegistrationViewModel.class);

        wireBack(binding.topbar.btnWizardBack);
        applyBottomInset(binding.btnBottomLink);

        binding.tvTitle.setText(getString(R.string.auth_terms_title, Constants.APP_NAME));
        binding.tvTermsBody.setText(buildTermsText());
        binding.tvTermsBody.setMovementMethod(LinkMovementMethod.getInstance());

        binding.btnPrimary.setOnClickListener(v -> submit());
        binding.btnBottomLink.setOnClickListener(v -> discardAndLogIn());
    }

    /** Three paragraphs, 24dp apart (blank line), with the blue inline links. */
    private SpannableStringBuilder buildTermsText() {
        final int linkColor = ContextCompat.getColor(requireContext(), R.color.auth_link);
        SpannableStringBuilder sb = new SpannableStringBuilder();

        // Paragraph 1
        String p1 = getString(R.string.auth_terms_p1, Constants.APP_NAME);
        sb.append(p1);
        appendLink(sb, getString(R.string.auth_learn_more), linkColor,
                () -> showInfo(R.string.auth_info_learn_more_title, R.string.auth_info_learn_more_body));
        sb.append("\n\n");

        // Paragraph 2
        sb.append(getString(R.string.auth_terms_p2_prefix));
        int boldStart = sb.length();
        sb.append(getString(R.string.auth_terms_p2_agree));
        sb.setSpan(new StyleSpan(Typeface.BOLD), boldStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append(getString(R.string.auth_terms_p2_mid, Constants.APP_NAME));
        appendLink(sb, getString(R.string.auth_terms_link_terms), linkColor,
                () -> LegalTextFragment.open(this, R.string.auth_legal_terms_title, R.string.auth_legal_terms_body));
        sb.append(getString(R.string.auth_terms_p2_comma));
        appendLink(sb, getString(R.string.auth_terms_link_privacy), linkColor,
                () -> LegalTextFragment.open(this, R.string.auth_legal_privacy_title, R.string.auth_legal_privacy_body));
        sb.append(getString(R.string.auth_terms_p2_and));
        appendLink(sb, getString(R.string.auth_terms_link_cookies), linkColor,
                () -> LegalTextFragment.open(this, R.string.auth_legal_cookies_title, R.string.auth_legal_cookies_body));
        sb.append(getString(R.string.auth_terms_p2_suffix));
        sb.append("\n\n");

        // Paragraph 3 (', including ads' intentionally omitted)
        sb.append(getString(R.string.auth_terms_p3_prefix));
        appendLink(sb, getString(R.string.auth_terms_link_privacy), linkColor,
                () -> LegalTextFragment.open(this, R.string.auth_legal_privacy_title, R.string.auth_legal_privacy_body));
        sb.append(getString(R.string.auth_terms_p3_suffix));

        return sb;
    }

    private void appendLink(SpannableStringBuilder sb, String label, int color, final Runnable action) {
        int start = sb.length();
        sb.append(label);
        int end = sb.length();
        sb.setSpan(new ClickableSpan() {
            @Override public void onClick(@NonNull View widget) { action.run(); }
        }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void submit() {
        if (submitting) return;
        setLoading(true);
        viewModel.saveRegistrationDetails().observe(getViewLifecycleOwner(), resource -> {
            if (resource == null || resource.status == Resource.Status.LOADING) return;
            if (resource.status == Resource.Status.SUCCESS) {
                finishOnboarding();
                return;
            }
            setLoading(false);
            String message = resource.message;
            boolean usernameTaken = message != null
                    && message.contains(getString(R.string.auth_username_just_taken));
            if (usernameTaken) {
                // Someone else took it in the meantime: go back and pick another.
                android.widget.Toast.makeText(requireContext(), message,
                        android.widget.Toast.LENGTH_LONG).show();
                NavHostFragment.findNavController(this)
                        .popBackStack(R.id.wizardUsernameFragment, false);
                return;
            }
            android.widget.Toast.makeText(requireContext(),
                    message == null ? getString(R.string.auth_error_generic) : message,
                    android.widget.Toast.LENGTH_LONG).show();
        });
    }

    /**
     * The account is finalised. Refresh the stored session with the chosen username
     * and avatar (the token metadata still holds the trigger's temporary one), then
     * continue into the Family B onboarding steps. getAccessToken()/getRefreshToken()
     * fall back to the in-memory tokens, so this is safe when "Remember login info"
     * was unchecked.
     */
    private void finishOnboarding() {
        SessionManager sm = SessionManager.getInstance(requireContext());
        sm.saveSession(sm.getAccessToken(), sm.getRefreshToken(), viewModel.getUserId(),
                viewModel.getUsername(), viewModel.getAvatarUrl());
        NavHostFragment.findNavController(this).navigate(R.id.action_terms_to_welcome);
    }

    private void setLoading(boolean loading) {
        submitting = loading;
        binding.progressPrimary.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnPrimary.setText(loading ? "" : getString(R.string.auth_agree));
        binding.btnPrimary.setEnabled(!loading);
        binding.btnPrimary.setAlpha(loading ? 1f : 1f);
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
