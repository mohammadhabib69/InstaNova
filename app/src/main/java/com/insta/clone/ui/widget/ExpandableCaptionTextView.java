package com.insta.clone.ui.widget;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;
import com.insta.clone.R;
import com.insta.clone.util.SpanUtils;

/**
 * Custom TextView for post captions displaying bold username, inline expandable "... more",
 * and clickable #hashtags and @mentions.
 */
public class ExpandableCaptionTextView extends AppCompatTextView {

    private static final int MAX_COLLAPSED_LENGTH = 90;
    private boolean isExpanded = false;
    private String username = "";
    private String caption = "";
    private SpanUtils.OnSpanClickListener spanClickListener;
    private OnUsernameClickListener usernameClickListener;

    public interface OnUsernameClickListener {
        void onUsernameClick(String username);
    }

    public ExpandableCaptionTextView(@NonNull Context context) {
        super(context);
        init();
    }

    public ExpandableCaptionTextView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ExpandableCaptionTextView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setMovementMethod(LinkMovementMethod.getInstance());
        setHighlightColor(ContextCompat.getColor(getContext(), android.R.color.transparent));
        setTextSize(14f);
        setLineSpacing(0f, 1.25f);
    }

    public void setCaption(String username, String caption,
                           SpanUtils.OnSpanClickListener spanListener,
                           OnUsernameClickListener userListener) {
        this.username = username != null ? username : "";
        this.caption = caption != null ? caption : "";
        this.spanClickListener = spanListener;
        this.usernameClickListener = userListener;
        this.isExpanded = false;

        renderText();
    }

    private void renderText() {
        if (username.isEmpty() && caption.isEmpty()) {
            setText("");
            setVisibility(GONE);
            return;
        }

        setVisibility(VISIBLE);
        SpannableStringBuilder builder = new SpannableStringBuilder();

        // 1. Username (14sp Medium / Bold)
        if (!username.isEmpty()) {
            int start = builder.length();
            builder.append(username).append(" ");
            int end = builder.length();

            builder.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.setSpan(new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    if (usernameClickListener != null) {
                        usernameClickListener.onUsernameClick(username);
                    }
                }

                @Override
                public void updateDrawState(@NonNull TextPaint ds) {
                    super.updateDrawState(ds);
                    ds.setColor(getCurrentTextColor());
                    ds.setUnderlineText(false);
                }
            }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // 2. Caption Text
        if (!caption.isEmpty()) {
            boolean needsEllipsize = !isExpanded && caption.length() > MAX_COLLAPSED_LENGTH;
            String textToDisplay = needsEllipsize ? caption.substring(0, MAX_COLLAPSED_LENGTH) : caption;

            // Format #tags and @mentions
            Spanned formattedCaption = SpanUtils.formatHashtagsAndMentions(getContext(), textToDisplay, spanClickListener);
            builder.append(formattedCaption);

            // 3. Inline "... more" button
            if (needsEllipsize) {
                String moreText = " " + getContext().getString(R.string.more);
                int moreStart = builder.length();
                builder.append(moreText);
                int moreEnd = builder.length();

                int secondaryColor = ContextCompat.getColor(getContext(), R.color.color_text_secondary);
                builder.setSpan(new ClickableSpan() {
                    @Override
                    public void onClick(@NonNull View widget) {
                        isExpanded = true;
                        renderText();
                    }

                    @Override
                    public void updateDrawState(@NonNull TextPaint ds) {
                        super.updateDrawState(ds);
                        ds.setColor(secondaryColor);
                        ds.setUnderlineText(false);
                    }
                }, moreStart, moreEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        setText(builder);
    }
}
