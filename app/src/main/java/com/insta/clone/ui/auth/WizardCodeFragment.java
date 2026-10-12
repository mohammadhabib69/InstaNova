package com.insta.clone.ui.auth;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.insta.clone.R;
import com.insta.clone.databinding.FragmentWizardCodeBinding;
import com.insta.clone.util.Constants;
import com.insta.clone.util.Resource;
import java.util.Map;

/**
 * Screen 3 — "Enter the confirmation code" (reference images 1 and 10).
 *
 * Six boxes span the width and are driven by one transparent EditText laid over
 * them, so the numeric keyboard opens on tap, backspace moves back and pasting six
 * digits fills every box. Values beyond six are ignored by maxLength.
 */
public class WizardCodeFragment extends WizardStepFragment {

    private static final String TAG_API = "InstaApi";

    private FragmentWizardCodeBinding binding;
    private RegistrationViewModel viewModel;
    private final TextView[] boxes = new TextView[Constants.CONFIRMATION_CODE_LENGTH];
    private boolean submitting;
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

        boxes[0] = binding.box1;
        boxes[1] = binding.box2;
        boxes[2] = binding.box3;
        boxes[3] = binding.box4;
        boxes[4] = binding.box5;
        boxes[5] = binding.box6;

        wireBack(binding.topbar.btnWizardBack);
        binding.tvSubtitle.setText(getString(R.string.auth_code_subtitle, viewModel.getEmail()));

        binding.etCodeInput.addTextChangedListener(new AuthFieldView.SimpleWatcher(this::render));
        binding.etCodeInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                verify();
                return true;
            }
            return false;
        });
        binding.layoutCodeBoxes.setOnClickListener(v -> focusInput());
        binding.btnPrimary.setOnClickListener(v -> verify());
        binding.btnSecondary.setOnClickListener(v -> showResendSheet());

        render();
        focusInput();
        startCountdownIfNeeded();
    }

    private void focusInput() {
        if (binding == null) return;
        binding.etCodeInput.requestFocus();
        InputMethodManager imm = (InputMethodManager) requireContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(binding.etCodeInput, InputMethodManager.SHOW_IMPLICIT);
    }

    /* ------------------------------------------------------------------ */
    /* Boxes                                                               */
    /* ------------------------------------------------------------------ */

    private void render() {
        String code = binding.etCodeInput.getText().toString();
        for (int i = 0; i < boxes.length; i++) {
            boxes[i].setText(i < code.length() ? String.valueOf(code.charAt(i)) : "");
        }
        int active = Math.min(code.length(), boxes.length - 1);
        for (int i = 0; i < boxes.length; i++) {
            boxes[i].setSelected(i == active && code.length() < boxes.length);
        }
        clearError();
        setPrimaryEnabled(binding.btnPrimary, code.length() == Constants.CONFIRMATION_CODE_LENGTH);
    }

    /** Any keystroke clears the red state, per the brief. */
    private void clearError() {
        if (binding.tvError.getVisibility() != View.VISIBLE) return;
        binding.tvError.setVisibility(View.GONE);
        for (TextView box : boxes) box.setBackgroundResource(R.drawable.bg_auth_code_box);
    }

    private void showCodeError(String message) {
        for (TextView box : boxes) box.setBackgroundResource(R.drawable.bg_auth_code_box_error);
        binding.tvError.setText(message == null || message.isEmpty()
                ? getString(R.string.auth_code_wrong) : message);
        binding.tvError.setVisibility(View.VISIBLE);
    }

    /* ------------------------------------------------------------------ */
    /* Verify                                                              */
    /* ------------------------------------------------------------------ */

    private void verify() {
        if (submitting) return;
        final String code = binding.etCodeInput.getText().toString();
        if (code.length() != Constants.CONFIRMATION_CODE_LENGTH) return;

        setLoading(true);
        viewModel.verifyOtp(viewModel.getEmail(), code).observe(getViewLifecycleOwner(), resource -> {
            if (resource == null || resource.status == Resource.Status.LOADING) return;
            setLoading(false);
            if (resource.status != Resource.Status.SUCCESS || resource.data == null) {
                showCodeError(resource.message);
                return;
            }
            String uid = resource.data.getUserId();
            viewModel.setUserId(uid);
            viewModel.setAccessToken(resource.data.getAccessToken());
            Log.d(TAG_API, "OTP verified for uid=" + uid);
            inspectProfile(uid);
        });
    }

    /**
     * A profile whose username still matches the trigger's temporary pattern is a
     * brand-new account; anything else means this email already has a real account.
     */
    private void inspectProfile(String uid) {
        viewModel.fetchProfileRow(uid).observe(getViewLifecycleOwner(), resource -> {
            String username = null;
            if (resource != null && resource.status == Resource.Status.SUCCESS && resource.data != null) {
                Object v = ((Map<?, ?>) resource.data).get("username");
                username = v == null ? null : String.valueOf(v);
            }
            if (username != null && !Constants.isTemporaryUsername(username)) {
                showAlreadyRegistered();
                return;
            }
            // New account confirmed: carry on to Create a password.
            NavHostFragment.findNavController(this).navigate(R.id.action_code_to_password);
        });
    }

    private void showAlreadyRegistered() {
        if (binding == null) return;
        viewModel.setAlreadyRegistered(true);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.auth_already_registered_title)
                .setPositiveButton(R.string.auth_log_in, (d, w) -> {
                    viewModel.reset();
                    viewModel.clearSession();
                    NavHostFragment.findNavController(this).navigate(R.id.action_wizard_to_login);
                })
                .setNegativeButton(R.string.auth_use_another_email, (d, w) -> {
                    viewModel.setAlreadyRegistered(true);
                    NavHostFragment.findNavController(this).popBackStack();
                })
                .show();
    }

    private void setLoading(boolean loading) {
        submitting = loading;
        binding.progressPrimary.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnPrimary.setText(loading ? "" : getString(R.string.auth_next));
        binding.btnPrimary.setEnabled(!loading);
        binding.btnPrimary.setAlpha(loading ? 1f : 0.4f);
        if (!loading) render();
        binding.etCodeInput.setEnabled(!loading);
    }

    /* ------------------------------------------------------------------ */
    /* "I didn't get the code" sheet                                       */
    /* ------------------------------------------------------------------ */

    private void showResendSheet() {
        final BottomSheetDialog sheet =
                new BottomSheetDialog(requireContext(), R.style.InstagramBottomSheetDialog);
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
            viewModel.requestOtp(viewModel.getEmail()).observe(getViewLifecycleOwner(), resource -> {
                if (resource == null || resource.status == Resource.Status.LOADING) return;
                if (resource.status == Resource.Status.SUCCESS) {
                    viewModel.markCodeSent();
                    toast(getString(R.string.auth_code_resent, viewModel.getEmail()));
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
        updateResendRow();
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
            @Override
            public void run() {
                int left = viewModel.secondsUntilResend();
                updateResendRow();
                if (left > 0) {
                    countdownHandler.postDelayed(this, 1000L);
                }
            }
        };
        countdownHandler.postDelayed(countdownRunnable, 1000L);
        updateResendRow();
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

    private void stopCountdown() {
        if (countdownRunnable != null) {
            countdownHandler.removeCallbacks(countdownRunnable);
            countdownRunnable = null;
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
        stopCountdown();
        resendRow = null;
        binding = null;
    }
}
