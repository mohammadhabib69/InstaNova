package com.insta.clone.ui.auth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.navigation.fragment.NavHostFragment;
import com.insta.clone.R;
import com.insta.clone.databinding.FragmentLegalTextBinding;

/**
 * In-app, scrollable policy text in the wizard's own style, so Terms, Privacy
 * Policy and Cookies Policy are never dead links.
 */
public class LegalTextFragment extends WizardStepFragment {

    /** Callers pass the destination action id alongside the document to show. */
    public static final String ARG_TITLE = "legal_title_res";
    public static final String ARG_BODY = "legal_body_res";

    private FragmentLegalTextBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentLegalTextBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        wireBack(binding.topbar.btnWizardBack);

        Bundle args = getArguments();
        int titleRes = args == null ? R.string.auth_legal_terms_title
                : args.getInt(ARG_TITLE, R.string.auth_legal_terms_title);
        int bodyRes = args == null ? R.string.auth_legal_terms_body
                : args.getInt(ARG_BODY, R.string.auth_legal_terms_body);

        binding.tvLegalTitle.setText(titleRes);
        binding.tvLegalBody.setText(bodyRes);
    }

    /** Convenience for the Terms screen's inline links. */
    public static void open(WizardStepFragment host, @StringRes int titleRes, @StringRes int bodyRes) {
        Bundle args = new Bundle();
        args.putInt(ARG_TITLE, titleRes);
        args.putInt(ARG_BODY, bodyRes);
        NavHostFragment.findNavController(host).navigate(R.id.action_to_legal, args);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
