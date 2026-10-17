package com.insta.nova.ui.auth;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import com.insta.nova.R;

/**
 * Outlined wizard field shared by every Family A screen: a label pinned at the
 * top-left (16sp, secondary) with the typed value (20sp, text colour) beneath it,
 * plus an optional 24dp trailing icon.
 *
 * Border states come from @drawable/bg_auth_field (default 1.5dp, focused 2dp) and
 * are swapped for @drawable/bg_auth_field_error by {@link #setErrorState(boolean)}.
 */
public class AuthFieldView extends FrameLayout {

    /** What sits in the 24dp trailing slot. */
    public enum Trailing {
        /** Nothing. */
        NONE,
        /** X that clears the field. */
        CLEAR,
        /** Eye that toggles password visibility. */
        EYE,
        /** Green tick (username available). */
        CHECK,
        /** Red error glyph (username taken). */
        ERROR
    }

    private final TextView label;
    private final EditText value;
    private final ImageView trailing;

    private Trailing trailingMode = Trailing.NONE;
    private boolean passwordVisible;
    private boolean errorState;
    private Runnable onTrailingClick;

    public AuthFieldView(@NonNull Context context) {
        this(context, null);
    }

    public AuthFieldView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AuthFieldView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        LayoutInflater.from(context).inflate(R.layout.view_auth_field, this, true);
        label = findViewById(R.id.tv_field_label);
        value = findViewById(R.id.et_field_value);
        trailing = findViewById(R.id.iv_field_trailing);

        trailing.setOnClickListener(v -> handleTrailingClick());
        // The whole box focuses the input, so tapping the label or the padding works.
        setOnClickListener(v -> {
            if (value.isFocusable()) value.requestFocus();
        });
    }

    /* ------------------------------------------------------------------ */
    /* Configuration                                                       */
    /* ------------------------------------------------------------------ */

    public void setLabel(CharSequence text) {
        label.setText(text);
    }

    public void setLabelRes(@StringRes int resId) {
        label.setText(resId);
    }

    public void setValueText(CharSequence text) {
        value.setText(text == null ? "" : text);
    }

    public void setHintRes(@StringRes int resId) {
        value.setHint(resId);
    }

    public void setInputTypeRaw(int type) {
        value.setInputType(type);
    }

    public void setImeOptionsRaw(int options) {
        value.setImeOptions(options);
    }

    /** Makes the field a non-editable tap target (used by the Birthday picker). */
    public void setReadOnly(Runnable onTap) {
        value.setFocusable(false);
        value.setClickable(false);
        value.setCursorVisible(false);
        setFocusable(true);
        setClickable(true);
        setOnClickListener(v -> {
            if (onTap != null) onTap.run();
        });
    }

    public void setTrailingMode(Trailing mode) {
        trailingMode = mode;
        switch (mode) {
            case CLEAR:
                trailing.setVisibility(VISIBLE);
                trailing.setImageResource(R.drawable.ic_close);
                trailing.setImageTintList(ContextCompat.getColorStateList(getContext(), R.color.auth_text_secondary));
                break;
            case EYE:
                trailing.setVisibility(VISIBLE);
                applyEyeIcon();
                break;
            case CHECK:
                trailing.setVisibility(VISIBLE);
                trailing.setImageResource(R.drawable.ic_check);
                trailing.setImageTintList(ContextCompat.getColorStateList(getContext(), R.color.auth_success));
                break;
            case ERROR:
                trailing.setVisibility(VISIBLE);
                trailing.setImageResource(R.drawable.ic_error);
                trailing.setImageTintList(ContextCompat.getColorStateList(getContext(), R.color.auth_error_text));
                break;
            case NONE:
            default:
                trailing.setVisibility(GONE);
                break;
        }
    }

    public void setOnTrailingClick(Runnable r) {
        onTrailingClick = r;
    }

    /** Red border + red trailing glyph, or back to the normal focused/default border. */
    public void setErrorState(boolean isError) {
        errorState = isError;
        setBackgroundResource(isError ? R.drawable.bg_auth_field_error : R.drawable.bg_auth_field);
        if (isError && trailingMode != Trailing.EYE) {
            setTrailingMode(Trailing.ERROR);
        } else if (!isError && trailingMode == Trailing.ERROR) {
            setTrailingMode(Trailing.NONE);
        }
    }

    public boolean isErrorState() {
        return errorState;
    }

    /* ------------------------------------------------------------------ */
    /* Accessors                                                           */
    /* ------------------------------------------------------------------ */

    public EditText input() {
        return value;
    }

    /** Trimmed contents. */
    public String text() {
        return value.getText().toString().trim();
    }

    /** Raw contents (passwords must not be trimmed). */
    public String rawText() {
        return value.getText().toString();
    }

    public void setText(CharSequence text) {
        value.setText(text);
        value.setSelection(value.length());
    }

    public void clearText() {
        value.setText("");
    }

    public void addTextChangedListener(TextWatcher watcher) {
        value.addTextChangedListener(watcher);
    }

    public boolean isEmpty() {
        return value.getText().toString().trim().isEmpty();
    }

    /* ------------------------------------------------------------------ */
    /* Internals                                                           */
    /* ------------------------------------------------------------------ */

    private void handleTrailingClick() {
        switch (trailingMode) {
            case CLEAR:
                value.setText("");
                value.requestFocus();
                break;
            case EYE:
                passwordVisible = !passwordVisible;
                value.setTransformationMethod(passwordVisible
                        ? HideReturnsTransformationMethod.getInstance()
                        : PasswordTransformationMethod.getInstance());
                applyEyeIcon();
                value.setSelection(value.length());
                break;
            default:
                break;
        }
        if (onTrailingClick != null) onTrailingClick.run();
    }

    private void applyEyeIcon() {
        trailing.setImageResource(passwordVisible ? R.drawable.ic_visibility : R.drawable.ic_visibility_off);
        trailing.setImageTintList(ContextCompat.getColorStateList(getContext(), R.color.auth_text_secondary));
    }

    /** Convenience for the password field. */
    public void enablePasswordToggle() {
        setTrailingMode(Trailing.EYE);
        value.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    }

    /** Shows the CLEAR glyph only while the field has content. */
    public void enableClearWhenNotEmpty() {
        setTrailingMode(Trailing.CLEAR);
        trailing.setVisibility(GONE);
        value.addTextChangedListener(new SimpleWatcher(() -> {
            if (trailingMode == Trailing.CLEAR) {
                trailing.setVisibility(value.length() > 0 ? VISIBLE : GONE);
            }
        }));
    }

    /** Minimal TextWatcher so callers do not repeat the three-method boilerplate. */
    public static class SimpleWatcher implements TextWatcher {
        private final Runnable after;
        public SimpleWatcher(Runnable after) { this.after = after; }
        @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
        @Override public void onTextChanged(CharSequence s, int a, int b, int c) { if (after != null) after.run(); }
        @Override public void afterTextChanged(Editable s) {}
    }
}
