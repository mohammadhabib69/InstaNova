package com.insta.nova.safe;

import android.app.Activity;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.insta.nova.R;
import com.insta.nova.databinding.SheetNovaGuardBinding;
import com.insta.nova.widget.NovaUi;

/**
 * The "Pause for a second" / "Nova Safe can't post this" sheets.
 *
 * <p>Returns true when the caller must stop (blocked or warned), false when the text is
 * clean and the caller may continue immediately. Every branch logs the outcome through
 * {@link NovaGuard}.
 */
public final class NovaModerationDialog {

    private NovaModerationDialog() {
    }

    public static boolean guard(Activity activity, String kind, String text,
                                int strictFromSeverity, boolean warnOnly,
                                Runnable proceed, Runnable edit) {
        if (activity == null) {
            if (proceed != null) proceed.run();
            return false;
        }
        NovaModerator.Result result = NovaGuard.evaluate(activity, text);
        if (result.isClean()) {
            if (proceed != null) proceed.run();
            return false;
        }
        boolean blocked = !warnOnly && NovaGuard.blocks(result, strictFromSeverity);
        boolean warn = !blocked && NovaGuard.warns(activity, result);
        if (!blocked && !warn) {
            if (proceed != null) proceed.run();
            return false;
        }

        SheetNovaGuardBinding binding = SheetNovaGuardBinding.inflate(LayoutInflater.from(activity));
        BottomSheetDialog dialog = new BottomSheetDialog(activity, R.style.Theme_Nova_Sheet);
        dialog.setContentView(binding.getRoot());
        binding.guardPreview.setText(highlight(activity, text, result));

        if (blocked) {
            NovaGuard.log(activity, kind, result, NovaGuard.ACTION_BLOCKED);
            binding.guardTitle.setText(R.string.nova_blocked_title);
            binding.guardMessage.setText(activity.getString(R.string.nova_blocked_reason,
                    NovaGuard.reasonFor(result)));
            binding.btnPostAnyway.setVisibility(android.view.View.GONE);
            binding.btnEdit.setText(R.string.nova_edit);
            NovaUi.haptic(binding.getRoot(), false);
        } else {
            NovaGuard.log(activity, kind, result, NovaGuard.ACTION_WARNED);
            binding.guardTitle.setText(R.string.nova_pause_title);
            binding.guardMessage.setText(R.string.nova_pause_body);
            binding.btnPostAnyway.setVisibility(android.view.View.VISIBLE);
            binding.btnEdit.setText(R.string.nova_edit);
        }

        binding.btnEdit.setOnClickListener(v -> {
            dialog.dismiss();
            if (blocked) {
                NovaGuard.log(activity, kind, result, NovaGuard.ACTION_EDITED);
            } else {
                NovaGuard.log(activity, kind, result, NovaGuard.ACTION_EDITED);
            }
            if (edit != null) edit.run();
        });
        binding.btnPostAnyway.setOnClickListener(v -> {
            dialog.dismiss();
            NovaGuard.log(activity, kind, result, NovaGuard.ACTION_POSTED_ANYWAY);
            if (proceed != null) proceed.run();
        });
        dialog.setOnCancelListener(d -> {
            if (edit != null) edit.run();
        });
        dialog.show();
        return true;
    }

    /** Highlights the exact spans the moderator matched. */
    public static CharSequence highlight(Activity activity, String text, NovaModerator.Result result) {
        SpannableString span = new SpannableString(text == null ? "" : text);
        int danger = activity.getColor(R.color.nova_danger);
        for (NovaModerator.Span match : result.spans) {
            int start = Math.max(0, Math.min(span.length(), match.start));
            int end = Math.max(start, Math.min(span.length(), match.end));
            if (start == end) continue;
            span.setSpan(new BackgroundColorSpan(NovaUi.withAlpha(danger, 0.24f)),
                    start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            span.setSpan(new ForegroundColorSpan(danger), start, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            span.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), start, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return span;
    }
}
