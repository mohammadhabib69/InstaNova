package com.insta.nova.update;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.insta.nova.R;
import com.insta.nova.databinding.DialogUpdateAvailableBinding;

/**
 * The "New Update Available" popup.
 *
 * <p>Native dialog built from the app's own tokens, not a web view and not a custom
 * activity, so it inherits the current theme (light/dark), the font scale set in
 * Settings > Accessibility, and the language chosen in Settings > Language without any
 * extra wiring.
 *
 * <p>Both buttons are non-destructive: "Later" dismisses and leaves the app exactly as it
 * was, which is why the dialog is dismissable by back and by tapping outside.
 */
public final class UpdateDialog implements UpdateManager.Listener {

    private final Activity activity;
    private final UpdateManager manager;
    private final DialogUpdateAvailableBinding binding;
    private final Dialog dialog;

    private boolean dismissed;
    private boolean bound;

    public static void show(Activity activity, UpdateManager manager) {
        if (activity == null || activity.isFinishing() || manager.getInfo() == null) return;
        UpdateDialog instance = new UpdateDialog(activity, manager, manager.getInfo());
        instance.display();
    }

    private UpdateDialog(Activity activity, UpdateManager manager, UpdateInfo info) {
        this.activity = activity;
        this.manager = manager;
        this.binding = DialogUpdateAvailableBinding.inflate(activity.getLayoutInflater());
        this.dialog = new Dialog(activity, R.style.Theme_InstaClone_UpdateDialog);
        dialog.setContentView(binding.getRoot());
        dialog.setCanceledOnTouchOutside(true);
        dialog.setCancelable(true);

        binding.tvUpdateTitle.setText(R.string.update_available_title);
        binding.tvUpdateVersion.setText(
                activity.getString(R.string.update_available_version, info.displayVersion()));
        bindNotes(info);
        bindButtons();

        dialog.setOnDismissListener(d -> release());
    }

    private void display() {
        manager.bind(this);
        bound = true;
        render(manager.getState(), manager.getProgress(), manager.getMessage());
        try {
            dialog.show();
        } catch (RuntimeException e) {
            release();
            return;
        }
        Window window = dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams params = window.getAttributes();
        params.dimAmount = 0.6f;
        params.gravity = Gravity.CENTER;
        window.setAttributes(params);
        window.setLayout(dialogWidth(), ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dialogWidth() {
        int screen = activity.getResources().getDisplayMetrics().widthPixels;
        int max = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 420,
                activity.getResources().getDisplayMetrics()));
        int margin = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48,
                activity.getResources().getDisplayMetrics()));
        return Math.min(Math.max(screen - margin, margin), max);
    }

    /* ------------------------------------------------------------------ */
    /* Content                                                             */
    /* ------------------------------------------------------------------ */

    private void bindNotes(UpdateInfo info) {
        binding.notesContainer.removeAllViews();
        if (info == null || info.releaseNotes.isEmpty()) {
            binding.tvWhatsNew.setVisibility(View.GONE);
            binding.notesScroll.setVisibility(View.GONE);
            return;
        }
        binding.tvWhatsNew.setVisibility(View.VISIBLE);
        binding.notesScroll.setVisibility(View.VISIBLE);

        int color = resolveColor(R.attr.themeTextSecondary, R.color.color_text_secondary);
        for (String note : info.releaseNotes) {
            if (TextUtils.isEmpty(note)) continue;
            TextView row = new TextView(activity);
            row.setText("•  " + note);
            row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            row.setTextColor(color);
            row.setLineSpacing(0f, 1.25f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(6);
            row.setLayoutParams(lp);
            binding.notesContainer.addView(row);
        }

        // Dialogs are wrap_content; without a cap a long changelog would push the buttons
        // off the screen on a small device.
        if (info.releaseNotes.size() > 5) {
            ViewGroup.LayoutParams lp = binding.notesScroll.getLayoutParams();
            lp.height = dp(168);
            binding.notesScroll.setLayoutParams(lp);
        }
    }

    private void bindButtons() {
        binding.btnUpdateNow.setOnClickListener(v -> onPrimaryAction());
        binding.btnLater.setOnClickListener(v -> dismiss());
    }

    private void onPrimaryAction() {
        UpdateManager.State state = manager.getState();
        if (state == UpdateManager.State.FAILED || state == UpdateManager.State.UPDATE_AVAILABLE) {
            manager.startDownload();
            return;
        }
        if (state == UpdateManager.State.READY) {
            manager.installPending(activity);
        }
    }

    /* ------------------------------------------------------------------ */
    /* State rendering                                                     */
    /* ------------------------------------------------------------------ */

    @Override
    public void onUpdateState(UpdateManager.State state, int progress, String message) {
        render(state, progress, message);
    }

    private void render(UpdateManager.State state, int progress, String message) {
        if (dismissed || binding == null) return;

        boolean showProgress = state == UpdateManager.State.DOWNLOADING
                || state == UpdateManager.State.VERIFYING
                || state == UpdateManager.State.INSTALLING
                || state == UpdateManager.State.READY;

        binding.updateProgress.setVisibility(showProgress ? View.VISIBLE : View.GONE);
        binding.tvUpdateStatus.setVisibility(
                TextUtils.isEmpty(message) && !showProgress ? View.GONE : View.VISIBLE);

        switch (state) {
            case DOWNLOADING:
                binding.updateProgress.setIndeterminate(progress < 0);
                if (progress >= 0) binding.updateProgress.setProgress(progress);
                binding.tvUpdateStatus.setText(progress < 0
                        ? activity.getString(R.string.update_downloading_title)
                        : activity.getString(R.string.update_progress, progress));
                binding.btnUpdateNow.setVisibility(View.GONE);
                binding.btnLater.setText(R.string.update_later);
                binding.btnLater.setVisibility(View.VISIBLE);
                break;

            case VERIFYING:
                binding.updateProgress.setIndeterminate(true);
                binding.tvUpdateStatus.setText(R.string.update_verifying);
                binding.btnUpdateNow.setVisibility(View.GONE);
                binding.btnLater.setVisibility(View.VISIBLE);
                binding.btnLater.setText(R.string.update_later);
                break;

            case READY:
                binding.updateProgress.setIndeterminate(false);
                binding.updateProgress.setProgress(100);
                binding.tvUpdateStatus.setVisibility(View.VISIBLE);
                binding.tvUpdateStatus.setText(TextUtils.isEmpty(message)
                        ? activity.getString(R.string.update_ready_status) : message);
                binding.btnUpdateNow.setVisibility(
                        TextUtils.isEmpty(message) ? View.VISIBLE : View.GONE);
                binding.btnUpdateNow.setText(R.string.update_now);
                binding.btnLater.setVisibility(View.VISIBLE);
                break;

            case INSTALLING:
                binding.updateProgress.setIndeterminate(false);
                binding.updateProgress.setProgress(100);
                binding.tvUpdateStatus.setVisibility(View.VISIBLE);
                binding.tvUpdateStatus.setText(R.string.update_ready_status);
                binding.btnUpdateNow.setVisibility(View.GONE);
                binding.btnLater.setVisibility(View.VISIBLE);
                dismiss();
                break;

            case FAILED:
                binding.tvUpdateStatus.setVisibility(View.VISIBLE);
                binding.tvUpdateStatus.setText(TextUtils.isEmpty(message)
                        ? activity.getString(R.string.update_failed_body) : message);
                binding.btnUpdateNow.setVisibility(View.VISIBLE);
                binding.btnUpdateNow.setText(R.string.update_retry);
                binding.btnLater.setVisibility(View.VISIBLE);
                break;

            case UPDATE_AVAILABLE:
            case CHECKING:
            case IDLE:
            default:
                binding.tvUpdateStatus.setVisibility(
                        TextUtils.isEmpty(message) ? View.GONE : View.VISIBLE);
                if (!TextUtils.isEmpty(message)) binding.tvUpdateStatus.setText(message);
                binding.btnUpdateNow.setVisibility(View.VISIBLE);
                binding.btnUpdateNow.setText(R.string.update_now);
                binding.btnLater.setVisibility(View.VISIBLE);
                break;
        }
    }

    /* ------------------------------------------------------------------ */
    /* Teardown                                                            */
    /* ------------------------------------------------------------------ */

    private void dismiss() {
        try {
            dialog.dismiss();
        } catch (RuntimeException ignored) {
            release();
        }
    }

    private void release() {
        if (dismissed) return;
        dismissed = true;
        if (bound) {
            manager.unbind(this);
            bound = false;
        }
        manager.onDialogDismissed();
    }

    private int resolveColor(int attr, int fallback) {
        TypedValue value = new TypedValue();
        if (activity.getTheme().resolveAttribute(attr, value, true)
                && value.resourceId != 0) {
            return ContextCompat.getColor(activity, value.resourceId);
        }
        return ContextCompat.getColor(activity, fallback);
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                activity.getResources().getDisplayMetrics()));
    }
}
