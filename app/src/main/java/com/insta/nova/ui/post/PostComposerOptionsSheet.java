package com.insta.nova.ui.post;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.insta.nova.R;
import com.insta.nova.databinding.SheetPostOptionsBinding;

/**
 * The shared "Post Options" module.
 *
 * <p>One component, two surfaces. The caller says whether it is composing a
 * Nova post ({@link #SURFACE_NOVA}) or an Instagram post
 * ({@link #SURFACE_INSTAGRAM}); the sheet looks identical either way and the
 * values it produces are written to the table of the active surface:
 * {@code nova_posts} for Nova, {@code posts} for Instagram.
 *
 * <p>Two options:
 * <ul>
 *   <li><b>Post anonymously</b> — the server keeps the real author, the public
 *       view hides it and publishes a random alias instead. The author still
 *       finds the post under "Mine" with an Anonymous badge, and the post can
 *       never be edited back into a named post.</li>
 *   <li><b>Expires after</b> — one hour, six hours, 24 hours, seven days or
 *       never. The card fades with the time that is left and the row is
 *       removed when the moment passes.</li>
 * </ul>
 */
public final class PostComposerOptionsSheet {

    /** The options belong to a Nova post: they are written to nova_posts. */
    public static final int SURFACE_NOVA = 1;
    /** The options belong to an Instagram post: they are written to posts. */
    public static final int SURFACE_INSTAGRAM = 2;

    /** The four timed windows and the "never" option. */
    public static final int EXPIRY_ONE_HOUR = 1;
    public static final int EXPIRY_SIX_HOURS = 2;
    public static final int EXPIRY_ONE_DAY = 3;
    public static final int EXPIRY_SEVEN_DAYS = 4;
    /** Anything the presets do not cover: the length lives in {@code Options.customSeconds}. */
    public static final int EXPIRY_CUSTOM = 5;
    public static final int EXPIRY_NEVER = 0;

    /** One hour in seconds. */
    public static final long SECONDS_ONE_HOUR = 3600L;
    public static final long SECONDS_SIX_HOURS = 6L * 3600L;
    public static final long SECONDS_ONE_DAY = 24L * 3600L;
    public static final long SECONDS_SEVEN_DAYS = 7L * 24L * 3600L;

    private PostComposerOptionsSheet() {
    }

    /** The values the sheet produces. */
    public static final class Options {
        public boolean anonymous;
        /** One of the EXPIRY_ constants. */
        public int expiry = EXPIRY_NEVER;
        /** Window length when expiry == EXPIRY_CUSTOM. */
        public long customSeconds = 0L;
        /** Written to posts.hide_like_count — the totals stay private. */
        public boolean hideLikeCount;
        /** Written to posts.comments_disabled — the comment box is closed. */
        public boolean commentsDisabled;

        public long expiresAtMillis(long nowMillis) {
            long seconds = durationSeconds();
            if (seconds <= 0L) return 0L;
            return nowMillis + seconds * 1000L;
        }

        public long durationSeconds() {
            switch (expiry) {
                case EXPIRY_ONE_HOUR: return SECONDS_ONE_HOUR;
                case EXPIRY_SIX_HOURS: return SECONDS_SIX_HOURS;
                case EXPIRY_ONE_DAY: return SECONDS_ONE_DAY;
                case EXPIRY_SEVEN_DAYS: return SECONDS_SEVEN_DAYS;
                case EXPIRY_CUSTOM: return Math.max(customSeconds, 0L);
                default: return 0L;
            }
        }

        /** The length of the window, or null when the post never expires. */
        public Integer durationSecondsOrNull() {
            long seconds = durationSeconds();
            return seconds <= 0L ? null : Integer.valueOf((int) seconds);
        }

        public boolean expires() {
            return expiry != EXPIRY_NEVER;
        }

        public Options copy() {
            Options copy = new Options();
            copy.anonymous = anonymous;
            copy.expiry = expiry;
            copy.customSeconds = customSeconds;
            copy.hideLikeCount = hideLikeCount;
            copy.commentsDisabled = commentsDisabled;
            return copy;
        }
    }

    /** Called with the chosen options. */
    public interface Callback {
        void onApply(Options options);
    }

    /* ------------------------------------------------------------------ */
    /* Showing the sheet                                                    */
    /* ------------------------------------------------------------------ */

    public static void show(@NonNull Fragment host, int surface, Options current,
                             @NonNull Callback callback) {
        if (!host.isAdded()) return;
        show(host.requireContext(), surface, current, callback);
    }

    public static void show(@NonNull Context context, int surface, Options current,
                             @NonNull Callback callback) {
        final Options options = current == null ? new Options() : current.copy();

        BottomSheetDialog dialog = new BottomSheetDialog(context, R.style.InstaNovaBottomSheetDialog);
        SheetPostOptionsBinding sheet = SheetPostOptionsBinding.inflate(
                android.view.LayoutInflater.from(context));
        dialog.setContentView(sheet.getRoot());

        sheet.tvPostOptionsSurface.setText(surfaceLabel(context, surface));
        sheet.switchPostAnonymous.setChecked(options.anonymous);
        sheet.tvPostAnonymousHint.setText(R.string.post_options_anonymous_hint);

        // The check marks are painted from the theme so the sheet reads the same
        // in the dark Nova theme as the rest of the app.
        final int visible = themeText(context);
        final int hidden = Color.TRANSPARENT;
        paintChecks(sheet, options.expiry, visible, hidden);

        sheet.rowPostAnonymous.setOnClickListener(v ->
                sheet.switchPostAnonymous.setChecked(!sheet.switchPostAnonymous.isChecked()));
        sheet.switchPostAnonymous.setOnClickListener(v -> { /* handled by the row */ });

        // The two share settings read the same way: the row is the tap target and
        // the switch follows it, so they can never disagree.
        sheet.switchPostHideLikes.setChecked(options.hideLikeCount);
        sheet.switchPostCommentsOff.setChecked(options.commentsDisabled);
        sheet.rowPostHideLikes.setOnClickListener(v ->
                sheet.switchPostHideLikes.setChecked(!sheet.switchPostHideLikes.isChecked()));
        sheet.rowPostCommentsOff.setOnClickListener(v ->
                sheet.switchPostCommentsOff.setChecked(!sheet.switchPostCommentsOff.isChecked()));
        sheet.switchPostHideLikes.setOnClickListener(v -> { /* handled by the row */ });
        sheet.switchPostCommentsOff.setOnClickListener(v -> { /* handled by the row */ });

        sheet.rowExpiryHour.setOnClickListener(v -> pick(sheet, options, EXPIRY_ONE_HOUR, visible, hidden));
        sheet.rowExpirySixHours.setOnClickListener(v -> pick(sheet, options, EXPIRY_SIX_HOURS, visible, hidden));
        sheet.rowExpiryDay.setOnClickListener(v -> pick(sheet, options, EXPIRY_ONE_DAY, visible, hidden));
        sheet.rowExpiryWeek.setOnClickListener(v -> pick(sheet, options, EXPIRY_SEVEN_DAYS, visible, hidden));
        sheet.rowExpiryNever.setOnClickListener(v -> pick(sheet, options, EXPIRY_NEVER, visible, hidden));
        sheet.rowExpiryCustom.setOnClickListener(v ->
                askCustomExpiry(context, sheet, options, visible, hidden));

        sheet.btnPostOptionsApply.setOnClickListener(v -> {
            options.anonymous = sheet.switchPostAnonymous.isChecked();
            options.hideLikeCount = sheet.switchPostHideLikes.isChecked();
            options.commentsDisabled = sheet.switchPostCommentsOff.isChecked();
            dialog.dismiss();
            callback.onApply(options);
        });

        dialog.show();
    }

    private static void pick(SheetPostOptionsBinding sheet, Options options, int expiry,
                             int visible, int hidden) {
        options.expiry = expiry;
        paintChecks(sheet, expiry, visible, hidden);
    }

    /**
     * Number + unit prompt for a window the presets do not cover, so "2 days" or
     * "12 hours" is possible instead of choosing the nearest fixed option.
     */
    private static void askCustomExpiry(Context context, SheetPostOptionsBinding sheet,
                                        Options options, int visible, int hidden) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.HORIZONTAL);
        int pad = Math.round(20 * context.getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, 0);

        final EditText amount = new EditText(context);
        amount.setHint(R.string.post_options_expiry_custom_hint);
        amount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        amount.setSingleLine(true);
        LinearLayout.LayoutParams amountParams =
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        box.addView(amount, amountParams);

        // A Spinner collapsed to its arrow inside this dialog, so the unit was invisible and
        // the choice looked impossible to make. A plain label that cycles minutes → hours →
        // days always renders and needs one tap fewer.
        final String[] unitLabels = {
                context.getString(R.string.post_options_unit_minutes),
                context.getString(R.string.post_options_unit_hours),
                context.getString(R.string.post_options_unit_days)};
        final int[] unitIndex = {0};
        final TextView unit = new TextView(context);
        unit.setText(unitLabels[0]);
        unit.setTextSize(14f);
        unit.setTextColor(themeText(context));
        unit.setPadding(Math.round(12 * context.getResources().getDisplayMetrics().density), 0, 0, 0);
        unit.setGravity(android.view.Gravity.CENTER_VERTICAL);
        unit.setMinWidth(Math.round(84 * context.getResources().getDisplayMetrics().density));
        final Runnable cycle = () -> {
            unitIndex[0] = (unitIndex[0] + 1) % unitLabels.length;
            unit.setText(unitLabels[unitIndex[0]]);
        };
        unit.setOnClickListener(v -> cycle.run());
        box.addView(unit, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT));

        final AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.post_options_expiry_custom)
                .setView(box)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.post_options_apply, null)
                .create();
        // The positive button is wired by hand: the stock one dismisses first, which threw
        // the typed value away whenever the input was empty or not a number.
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String raw = amount.getText().toString().trim();
                    long value;
                    try {
                        value = Long.parseLong(raw);
                    } catch (NumberFormatException e) {
                        amount.setError(context.getString(R.string.post_options_expiry_invalid));
                        return;
                    }
                    if (value <= 0L) {
                        amount.setError(context.getString(R.string.post_options_expiry_invalid));
                        return;
                    }
                    int position = unitIndex[0];
                    long seconds = position == 2
                            ? value * 24L * 3600L
                            : position == 1 ? value * 3600L : value * 60L;
                    options.customSeconds = seconds;
                    options.expiry = EXPIRY_CUSTOM;
                    sheet.tvExpiryCustom.setText(context.getString(
                            R.string.post_options_expiry_custom_value,
                            expiryLabel(context, System.currentTimeMillis() + seconds * 1000L,
                                    System.currentTimeMillis())));
                    paintChecks(sheet, EXPIRY_CUSTOM, visible, hidden);
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private static void paintChecks(SheetPostOptionsBinding sheet, int expiry,
                                    int visible, int hidden) {
        sheet.checkExpiryHour.setImageTintList(
                ColorStateList.valueOf(expiry == EXPIRY_ONE_HOUR ? visible : hidden));
        sheet.checkExpirySixHours.setImageTintList(
                ColorStateList.valueOf(expiry == EXPIRY_SIX_HOURS ? visible : hidden));
        sheet.checkExpiryDay.setImageTintList(
                ColorStateList.valueOf(expiry == EXPIRY_ONE_DAY ? visible : hidden));
        sheet.checkExpiryWeek.setImageTintList(
                ColorStateList.valueOf(expiry == EXPIRY_SEVEN_DAYS ? visible : hidden));
        sheet.checkExpiryNever.setImageTintList(
                ColorStateList.valueOf(expiry == EXPIRY_NEVER ? visible : hidden));
        sheet.checkExpiryCustom.setImageTintList(
                ColorStateList.valueOf(expiry == EXPIRY_CUSTOM ? visible : hidden));
    }

    /* ------------------------------------------------------------------ */
    /* Surface helpers                                                      */
    /* ------------------------------------------------------------------ */

    /** The line under the title that says where the options are written. */
    public static int surfaceLabel(Context context, int surface) {
        return surface == SURFACE_NOVA
                ? R.string.post_options_surface_nova
                : R.string.post_options_surface_instagram;
    }

    /** True when the surface is Nova. */
    public static boolean isNova(int surface) {
        return surface == SURFACE_NOVA;
    }

    /** The "Expires in 3h" line for a post card. */
    public static String expiryLabel(Context context, long expiresAtMillis, long nowMillis) {
        if (expiresAtMillis <= 0L) return null;
        long remaining = expiresAtMillis - nowMillis;
        if (remaining <= 0L) return null;
        long minutes = remaining / 60000L;
        if (minutes < 60L) {
            return context.getString(R.string.nova_expiry_minutes,
                    Math.max(1L, minutes));
        }
        long hours = minutes / 60L;
        if (hours < 24L) {
            return context.getString(R.string.nova_expiry_hours, hours);
        }
        long days = hours / 24L;
        return context.getString(R.string.nova_expiry_days, days);
    }

    private static int themeText(Context context) {
        android.util.TypedValue value = new android.util.TypedValue();
        if (context.getTheme().resolveAttribute(R.attr.themeTextPrimary, value, true)) {
            if (value.resourceId != 0) {
                return androidx.core.content.ContextCompat.getColor(context, value.resourceId);
            }
            return value.data;
        }
        return Color.WHITE;
    }

    /** A no-op click sink so the layout's switch never swallows the row. */
    public static void ignore(View view) {
        if (view != null) view.setOnClickListener(null);
    }
}
