package com.insta.nova.ui.auth;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.insta.nova.R;
import com.insta.nova.databinding.FragmentWizardCodeBinding;
import com.insta.nova.util.Constants;
import com.insta.nova.util.Resource;

import java.util.Map;

/**
 * Screen 3 — "Confirm your email".
 *
 * <p>Firebase Authentication issues a verification <em>link</em>, not a 6-digit email
 * OTP, so this step shows the live state of that link instead of accepting digits that
 * nothing could ever check. It polls {@code FirebaseUser.reload()} every few seconds,
 * and re-checks whenever the app comes back to the foreground after the user taps the
 * link in their mail app. Nothing advances unless Firebase reports the address as
 * genuinely verified.
 */
public class WizardCodeFragment extends WizardStepFragment {

    private static final String TAG_API = "InstaApi";
    private static final long POLL_INTERVAL_MS = 4000L;

    private FragmentWizardCodeBinding binding;
    private RegistrationViewModel viewModel;
    private boolean submitting;
    private boolean advanced;
    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private Runnable pollRunnable;
    private final Handler countdownHandler = new Handler(Looper.getMainLooper());
    private Runnable countdownRunnable;
    private TextView resendRow;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentWizardCodeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(RegistrationViewModel.class);

        wireBack(binding.topbar.btnWizardBack);
        String email = viewModel.getEmail();
        if (email == null || email.isEmpty()) {
            com.google.firebase.auth.FirebaseUser user =
                    com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            if (user != null && user.getEmail() != null) {
                email = user.getEmail();
                viewModel.setEmail(email);
            }
        }
        binding.tvSubtitle.setText(getString(R.string.auth_verify_subtitle, email));

        binding.btnPrimary.setOnClickListener(v -> checkNow(true));
        binding.btnSecondary.setOnClickListener(v -> showResendSheet());
    }

    @Override
    public void onResume() {
        super.onResume();
        // Returning from the mail app is the most likely moment for the link to be live.
        if (!advanced) checkNow(false);
        startPolling();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopPolling();
    }

    /* ------------------------------------------------------------------ */
    /* Verification                                                        */
    /* ------------------------------------------------------------------ */

    private void startPolling() {
        stopPolling();
        pollRunnable = new Runnable() {
            @Override
            public void run() {
                if (binding == null || advanced) return;
                checkNow(false);
                pollHandler.postDelayed(this, POLL_INTERVAL_MS);
            }
        };
        pollHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
    }

    private void stopPolling() {
        if (pollRunnable != null) {
            pollHandler.removeCallbacks(pollRunnable);
            pollRunnable = null;
        }
    }

    /**
     * @param userInitiated true when the primary button was tapped, which owns the
     *                      spinner; polling is silent so it never flickers the UI.
     */
    private void checkNow(boolean userInitiated) {
        if (advanced) return;
        if (userInitiated) setLoading(true);

        viewModel.checkEmailVerified().observe(getViewLifecycleOwner(), resource -> {
            if (resource == null || resource.status == Resource.Status.LOADING) return;
            if (userInitiated) setLoading(false);
            if (resource.status == Resource.Status.ERROR) {
                if (userInitiated) showError(resource.message);
                return;
            }
            Boolean verified = resource.data;
            if (verified != null && verified) {
                onVerified();
            } else if (userInitiated) {
                showError(getString(R.string.auth_verify_not_yet));
            }
        });
    }

    private void onVerified() {
        if (advanced) return;
        advanced = true;
        stopPolling();
        String uid = viewModel.getUserId();
        if (uid == null || uid.isEmpty()) {
            // checkEmailVerified persisted the identity; fall back to whatever is stored.
            uid = com.insta.nova.data.local.SessionManager
                    .getInstance(requireContext()).getFirebaseUid();
            viewModel.setUserId(uid);
        }
        final String firebaseUid = uid;

        // A profile whose username is no longer the placeholder belongs to a finished
        // account: this email is already registered.
        viewModel.fetchProfileRow(firebaseUid).observe(getViewLifecycleOwner(), resource -> {
            if (binding == null) return;
            String username = null;
            if (resource != null && resource.status == Resource.Status.SUCCESS && resource.data != null) {
                Object value = ((Map<?, ?>) resource.data).get("username");
                username = value == null ? null : String.valueOf(value);
                Object id = ((Map<?, ?>) resource.data).get("id");
                if (id != null) viewModel.setProfileId(String.valueOf(id));
            }
            if (username != null && !Constants.isTemporaryUsername(username)) {
                showAlreadyRegistered();
                return;
            }
            Log.d(TAG_API, "Email verified for uid=" + firebaseUid + "; continuing to password");
            NavHostFragment.findNavController(this).navigate(R.id.action_code_to_password);
        });
    }

    private void showAlreadyRegistered() {
        if (binding == null || !isAdded()) return;
        viewModel.setAlreadyRegistered(true);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.auth_already_registered_title)
                .setPositiveButton(R.string.auth_log_in, (d, w) -> {
                    viewModel.reset();
                    viewModel.clearSession();
                    NavHostFragment.findNavController(this).navigate(R.id.action_wizard_to_login);
                })
                .setNegativeButton(R.string.auth_use_another_email, (d, w) -> {
                    advanced = false;
                    NavHostFragment.findNavController(this).popBackStack();
                })
                .show();
    }

    private void setLoading(boolean loading) {
        if (binding == null) return;
        submitting = loading;
        binding.progressPrimary.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnPrimary.setText(loading ? "" : getString(R.string.auth_verify_primary));
        binding.btnPrimary.setEnabled(!loading);
        binding.btnPrimary.setAlpha(1f);
    }

    private void showError(String message) {
        if (binding == null) return;
        binding.tvError.setText(message == null || message.isEmpty()
                ? getString(R.string.auth_verify_not_yet) : message);
        binding.tvError.setVisibility(View.VISIBLE);
    }

    /* ------------------------------------------------------------------ */
    /* "Didn't get the email?" sheet                                       */
    /* ------------------------------------------------------------------ */

    private void showResendSheet() {
        final BottomSheetDialog sheet =
                new BottomSheetDialog(requireContext(), R.style.InstaNovaBottomSheetDialog);
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.bg_bottom_sheet);

        View handle = new View(requireContext());
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(dpPx(32), dpPx(4));
        hp.topMargin = dpPx(8);
        hp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        handle.setLayoutParams(hp);
        handle.setBackgroundResource(R.drawable.bg_drag_handle);
        root.addView(handle);

        resendRow = sheetRow(root, getString(R.string.auth_code_resend));
        resendRow.setOnClickListener(v -> {
            if (viewModel.secondsUntilResend() > 0) return;
            resendRow.setEnabled(false);
            viewModel.resendVerificationEmail().observe(getViewLifecycleOwner(), resource -> {
                if (resource == null || resource.status == Resource.Status.LOADING) return;
                if (resource.status == Resource.Status.SUCCESS) {
                    viewModel.markCodeSent();
                    toast(getString(R.string.auth_verify_resent, viewModel.getEmail()));
                    binding.tvError.setVisibility(View.GONE);
                } else if (resource.message != null) {
                    toast(resource.message);
                }
                startCountdownIfNeeded();
            });
            sheet.dismiss();
        });

        TextView changeEmail = sheetRow(root, getString(R.string.auth_code_change_email));
        changeEmail.setOnClickListener(v -> {
            sheet.dismiss();
            NavHostFragment.findNavController(this).popBackStack();
        });

        TextView cancel = sheetRow(root, getString(R.string.auth_cancel));
        cancel.setOnClickListener(v -> sheet.dismiss());

        sheet.setContentView(root);
        sheet.show();
        startCountdownIfNeeded();
    }

    /** 56dp row with 16dp horizontal padding, matching the sheet token style. */
    private TextView sheetRow(LinearLayout parent, String label) {
        TextView row = new TextView(requireContext());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpPx(56));
        row.setLayoutParams(lp);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dpPx(16), 0, dpPx(16), 0);
        row.setText(label);
        row.setTextAppearance(R.style.TextAppearance_Insta_AuthSheetRow);
        row.setBackgroundResource(android.R.drawable.list_selector_background);
        parent.addView(row);
        return row;
    }

    private void startCountdownIfNeeded() {
        stopCountdown();
        if (viewModel.secondsUntilResend() <= 0) {
            updateResendRow();
            return;
        }
        countdownRunnable = new Runnable() {
            @Override public void run() {
                updateResendRow();
                if (viewModel.secondsUntilResend() > 0) {
                    countdownHandler.postDelayed(this, 1000L);
                }
            }
        };
        countdownHandler.postDelayed(countdownRunnable, 1000L);
        updateResendRow();
    }

    private void stopCountdown() {
        if (countdownRunnable != null) {
            countdownHandler.removeCallbacks(countdownRunnable);
            countdownRunnable = null;
        }
    }

    private void updateResendRow() {
        if (resendRow == null) return;
        int left = viewModel.secondsUntilResend();
        if (left > 0) {
            resendRow.setEnabled(false);
            resendRow.setAlpha(0.5f);
            resendRow.setText(getString(R.string.auth_code_resend_in,
                    RegistrationViewModel.formatCountdown(left)));
        } else {
            resendRow.setEnabled(true);
            resendRow.setAlpha(1f);
            resendRow.setText(R.string.auth_code_resend);
        }
    }

    private int dpPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String message) {
        if (message == null || getContext() == null) return;
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopPolling();
        stopCountdown();
        resendRow = null;
        binding = null;
    }
}
