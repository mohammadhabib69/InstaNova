package com.insta.nova.ui.widget;

import android.content.Context;
import android.util.AttributeSet;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;
import com.insta.nova.R;

/**
 * 12dp verified seal badge displayed after username.
 */
public class VerifiedBadgeView extends AppCompatImageView {

    public VerifiedBadgeView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public VerifiedBadgeView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public VerifiedBadgeView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_verified));
        setContentDescription("Verified Account");
    }

    public void setVerified(boolean isVerified) {
        setVisibility(isVerified ? VISIBLE : GONE);
    }
}
