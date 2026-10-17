package com.insta.nova.util;

import android.content.Context;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import com.insta.nova.R;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text spanning utility providing clickable #hashtags and @mentions styling.
 */
public final class SpanUtils {

    private static final Pattern HASHTAG_PATTERN = Pattern.compile("#(\\w+)");
    private static final Pattern MENTION_PATTERN = Pattern.compile("@([a-zA-Z0-9._]+)");

    public interface OnSpanClickListener {
        void onTagClick(String tag);
        void onMentionClick(String mentionUsername);
    }

    private SpanUtils() {}

    public static Spanned formatHashtagsAndMentions(Context context, String text, OnSpanClickListener listener) {
        if (text == null) return new SpannableString("");

        SpannableString spannable = new SpannableString(text);
        int linkColor = ContextCompat.getColor(context, R.color.color_primary);

        // Match #hashtags
        Matcher tagMatcher = HASHTAG_PATTERN.matcher(text);
        while (tagMatcher.find()) {
            final String tag = tagMatcher.group(1);
            int start = tagMatcher.start();
            int end = tagMatcher.end();

            spannable.setSpan(new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    if (listener != null && tag != null) {
                        listener.onTagClick(tag);
                    }
                }

                @Override
                public void updateDrawState(@NonNull TextPaint ds) {
                    super.updateDrawState(ds);
                    ds.setColor(linkColor);
                    ds.setUnderlineText(false);
                }
            }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // Match @mentions
        Matcher mentionMatcher = MENTION_PATTERN.matcher(text);
        while (mentionMatcher.find()) {
            final String mention = mentionMatcher.group(1);
            int start = mentionMatcher.start();
            int end = mentionMatcher.end();

            spannable.setSpan(new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    if (listener != null && mention != null) {
                        listener.onMentionClick(mention);
                    }
                }

                @Override
                public void updateDrawState(@NonNull TextPaint ds) {
                    super.updateDrawState(ds);
                    ds.setColor(linkColor);
                    ds.setUnderlineText(false);
                }
            }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        return spannable;
    }
}
