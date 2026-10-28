package com.insta.nova.safe;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.insta.nova.R;
import com.insta.nova.data.NovaDtos;
import com.insta.nova.data.NovaRepository;
import com.insta.nova.databinding.DialogNovaConfirmBinding;
import com.insta.nova.databinding.SheetNovaMuteBinding;
import com.insta.nova.databinding.SheetNovaReportBinding;
import com.insta.nova.util.Resource;
import com.insta.nova.widget.NovaUi;

/**
 * The one implementation of Block / Mute / Report used by every surface: profile
 * menus, post menus, comment long-press, chat headers, story viewers, reels and
 * Campus. Each action calls the real RPC and refreshes {@link com.insta.nova.data.BlockMuteCache}.
 */
public final class SafeActions {

    public interface Done {
        void onDone();
    }

    private static final String[] REASONS = {
            "spam", "harassment", "hate", "nudity", "violence",
            "scam", "false_info", "self_harm", "other"
    };

    private static final int[] REASON_LABELS = {
            R.string.nova_reason_spam, R.string.nova_reason_harassment, R.string.nova_reason_hate,
            R.string.nova_reason_nudity, R.string.nova_reason_violence, R.string.nova_reason_scam,
            R.string.nova_reason_false_info, R.string.nova_reason_self_harm, R.string.nova_reason_other
    };

    private SafeActions() {
    }

    /* ------------------------------------------------------------------ */
    /* Block                                                               */
    /* ------------------------------------------------------------------ */

    public static void confirmBlock(Context context, String userId, String username, Done done) {
        Activity activity = activity(context);
        if (activity == null || userId == null) return;
        DialogNovaConfirmBinding binding = DialogNovaConfirmBinding.inflate(LayoutInflater.from(activity));
        binding.confirmTitle.setText(activity.getString(R.string.nova_block_title, username));
        binding.confirmMessage.setText(R.string.nova_block_body);
        binding.btnConfirm.setText(R.string.nova_block);

        Dialog dialog = new Dialog(activity);
        dialog.setContentView(binding.getRoot());
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        binding.btnCancel.setOnClickListener(v -> dialog.dismiss());
        binding.btnConfirm.setOnClickListener(v -> {
            dialog.dismiss();
            NovaUi.haptic(v, false);
            NovaRepository repository = new NovaRepository(activity);
            once(repository.block(userId), resource -> {
                if (resource.status == Resource.Status.SUCCESS) {
                    toast(activity, R.string.nova_block_done);
                    if (done != null) done.onDone();
                } else {
                    toast(activity, R.string.nova_error);
                }
            });
        });
        dialog.show();
    }

    public static void unblock(Context context, String userId, Done done) {
        Activity activity = activity(context);
        if (activity == null || userId == null) return;
        NovaRepository repository = new NovaRepository(activity);
        once(repository.unblock(userId), resource -> {
            if (resource.status == Resource.Status.SUCCESS) {
                toast(activity, R.string.nova_unblock_done);
                if (done != null) done.onDone();
            } else {
                toast(activity, R.string.nova_error);
            }
        });
    }

    /* ------------------------------------------------------------------ */
    /* Mute                                                                */
    /* ------------------------------------------------------------------ */

    public static void mute(Context context, String userId, String username,
                            boolean posts, boolean stories, Done done) {
        Activity activity = activity(context);
        if (activity == null || userId == null) return;
        SheetNovaMuteBinding binding = SheetNovaMuteBinding.inflate(LayoutInflater.from(activity));
        BottomSheetDialog dialog = new BottomSheetDialog(activity, R.style.Theme_Nova_Sheet);
        dialog.setContentView(binding.getRoot());
        binding.tvMuteTitle.setText(activity.getString(R.string.nova_mute_title, username));
        binding.switchPosts.setChecked(posts);
        binding.switchStories.setChecked(stories);
        tintSwitch(binding.switchPosts);
        tintSwitch(binding.switchStories);

        binding.btnCancelMute.setOnClickListener(v -> dialog.dismiss());
        binding.btnSaveMute.setOnClickListener(v -> {
            boolean mutePosts = binding.switchPosts.isChecked();
            boolean muteStories = binding.switchStories.isChecked();
            dialog.dismiss();
            NovaRepository repository = new NovaRepository(activity);
            once(repository.mute(userId, mutePosts, muteStories), resource -> {
                if (resource.status == Resource.Status.SUCCESS) {
                    toast(activity, R.string.nova_mute_done);
                    if (done != null) done.onDone();
                } else {
                    toast(activity, R.string.nova_error);
                }
            });
        });
        dialog.show();
    }

    /* ------------------------------------------------------------------ */
    /* Report                                                              */
    /* ------------------------------------------------------------------ */

    public static void report(Context context, String type, String targetId) {
        Activity activity = activity(context);
        if (activity == null || targetId == null) return;
        SheetNovaReportBinding binding = SheetNovaReportBinding.inflate(LayoutInflater.from(activity));
        BottomSheetDialog dialog = new BottomSheetDialog(activity, R.style.Theme_Nova_Sheet);
        dialog.setContentView(binding.getRoot());

        final String[] selected = {REASONS[1]};
        binding.reasonContainer.removeAllViews();
        for (int i = 0; i < REASONS.length; i++) {
            final int index = i;
            TextView row = new TextView(activity);
            row.setText(REASON_LABELS[i]);
            row.setTextSize(14f);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setMinHeight(NovaUi.dp(activity, 44));
            row.setPadding(NovaUi.dp(activity, 12), NovaUi.dp(activity, 6), NovaUi.dp(activity, 12), NovaUi.dp(activity, 6));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.topMargin = NovaUi.dp(activity, 6);
            row.setLayoutParams(params);
            bindReasonRow(row, activity, index == 1);
            row.setOnClickListener(v -> {
                selected[0] = REASONS[index];
                for (int child = 0; child < binding.reasonContainer.getChildCount(); child++) {
                    bindReasonRow((TextView) binding.reasonContainer.getChildAt(child), activity, child == index);
                }
            });
            binding.reasonContainer.addView(row);
        }

        binding.btnCancelReport.setOnClickListener(v -> dialog.dismiss());
        binding.btnSubmitReport.setOnClickListener(v -> {
            String note = binding.etReportNote.getText().toString().trim();
            dialog.dismiss();
            NovaRepository repository = new NovaRepository(activity);
            once(repository.report(type, targetId, selected[0], note), resource -> {
                if (resource.status == Resource.Status.SUCCESS) {
                    toast(activity, R.string.nova_report_thanks);
                    NovaUi.haptic(activity.getWindow().getDecorView(), true);
                } else {
                    toast(activity, R.string.nova_error);
                }
            });
        });
        dialog.show();
    }

    private static void bindReasonRow(TextView row, Activity activity, boolean selected) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(NovaUi.dpFloat(activity, 14));
        if (selected) {
            drawable.setColor(NovaUi.withAlpha(
                    activity.getColor(R.color.nova_violet), 0.16f));
            drawable.setStroke(NovaUi.dp(activity, 1),
                    NovaUi.withAlpha(activity.getColor(R.color.nova_violet), 0.5f));
            row.setTextColor(activity.getColor(R.color.nova_text));
            row.setTypeface(Typeface.DEFAULT_BOLD);
        } else {
            drawable.setColor(activity.getColor(R.color.nova_card_high));
            drawable.setStroke(NovaUi.dp(activity, 1), activity.getColor(R.color.nova_stroke));
            row.setTextColor(activity.getColor(R.color.nova_text_2));
            row.setTypeface(Typeface.DEFAULT);
        }
        row.setBackground(drawable);
    }

    private static void tintSwitch(android.widget.CompoundButton button) {
        int accent = button.getContext().getColor(R.color.nova_violet);
        androidx.core.widget.CompoundButtonCompat.setButtonTintList(button,
                new android.content.res.ColorStateList(
                        new int[][]{{android.R.attr.state_checked}, {}},
                        new int[]{accent, 0x33FFFFFF}));
    }

    /* ------------------------------------------------------------------ */
    /* Helpers                                                             */
    /* ------------------------------------------------------------------ */

    private static <T> void once(LiveData<Resource<T>> liveData, Observer<Resource<T>> observer) {
        liveData.observeForever(new Observer<Resource<T>>() {
            @Override
            public void onChanged(Resource<T> resource) {
                if (resource == null || resource.status == Resource.Status.LOADING) return;
                liveData.removeObserver(this);
                observer.onChanged(resource);
            }
        });
    }

    public static Activity activity(Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity) return (Activity) current;
            current = ((ContextWrapper) current).getBaseContext();
        }
        return null;
    }

    private static void toast(Activity activity, int message) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }
}
