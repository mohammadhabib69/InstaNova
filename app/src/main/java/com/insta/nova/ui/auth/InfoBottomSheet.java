package com.insta.nova.ui.auth;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.insta.nova.R;
import com.insta.nova.databinding.SheetInfoBinding;

/**
 * Reusable explanatory sheet (title + 2-3 sentences + OK). Used by every
 * "Learn more" and info link in the registration wizard so none of them is dead.
 */
public class InfoBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_TITLE = "info_title";
    private static final String ARG_BODY = "info_body";

    private SheetInfoBinding binding;

    public static InfoBottomSheet newInstance(@StringRes int titleRes, @StringRes int bodyRes) {
        InfoBottomSheet sheet = new InfoBottomSheet();
        Bundle args = new Bundle();
        args.putInt(ARG_TITLE, titleRes);
        args.putInt(ARG_BODY, bodyRes);
        sheet.setArguments(args);
        return sheet;
    }

    /** Convenience so callers do not need the FragmentManager dance. */
    public static void show(@NonNull androidx.fragment.app.Fragment host, @StringRes int titleRes, @StringRes int bodyRes) {
        newInstance(titleRes, bodyRes).show(host.getChildFragmentManager(), "info_sheet");
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        return new BottomSheetDialog(requireContext(), R.style.InstaNovaBottomSheetDialog);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = SheetInfoBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            binding.tvInfoTitle.setText(args.getInt(ARG_TITLE, R.string.auth_info_learn_more_title));
            binding.tvInfoBody.setText(args.getInt(ARG_BODY, R.string.auth_info_learn_more_body));
        }
        binding.btnInfoOk.setOnClickListener(v -> dismiss());
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() instanceof BottomSheetDialog) {
            // getBehavior() is BottomSheetBehavior<FrameLayout>, so wildcard it.
            BottomSheetBehavior<?> behavior = ((BottomSheetDialog) getDialog()).getBehavior();
            if (behavior != null) behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
