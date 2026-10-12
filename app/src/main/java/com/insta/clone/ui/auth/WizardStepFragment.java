package com.insta.clone.ui.auth;

import android.view.View;
import android.widget.Button;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import com.insta.clone.R;

/**
 * Shared chrome for every Family A wizard step: back handling, the bottom-link
 * gesture inset, and the primary-button enabled/loading presentation.
 */
public abstract class WizardStepFragment extends Fragment {

    /** Back arrow = previous step, values preserved by RegistrationViewModel. */
    protected void wireBack(View backButton) {
        if (backButton != null) {
            backButton.setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());
        }
    }

    /**
     * Lifts the bottom 'I already have an account' link clear of the gesture bar,
     * matching the 24dp gap in the reference.
     */
    protected void applyBottomInset(final View anchor) {
        if (anchor == null) return;
        final int basePaddingBottom = anchor.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(anchor, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(),
                    basePaddingBottom + bars.bottom + dp(R.dimen.auth_bottom_link_gap));
            return insets;
        });
        ViewCompat.requestApplyInsets(anchor);
    }

    protected int dp(int dimenRes) {
        return getResources().getDimensionPixelSize(dimenRes);
    }

    /** Disabled primary buttons sit at 40% alpha per the design. */
    protected void setPrimaryEnabled(Button button, boolean enabled) {
        if (button == null) return;
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.4f);
    }

    protected void showInfo(int titleRes, int bodyRes) {
        InfoBottomSheet.show(this, titleRes, bodyRes);
    }

    protected boolean notEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
