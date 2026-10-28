package com.insta.nova.hub;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.insta.nova.BuildConfig;
import com.insta.nova.R;
import com.insta.nova.data.NovaSettingsStore;
import com.insta.nova.databinding.SheetNovaAboutBinding;
import com.insta.nova.widget.NovaUi;

/** About Nova: the meaning copy, module list, version, language switch and credits. */
public class NovaAboutSheet extends BottomSheetDialogFragment {

    private SheetNovaAboutBinding binding;
    private NovaSettingsStore settingsStore;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = SheetNovaAboutBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        settingsStore = NovaSettingsStore.get(requireContext());
        binding.tvVersion.setText(getString(R.string.nova_version, BuildConfig.VERSION_NAME));
        binding.btnClose.setOnClickListener(v -> dismiss());

        buildModules();
        styleLanguage();
        binding.btnEnglish.setOnClickListener(v -> setLanguage("en"));
        binding.btnBangla.setOnClickListener(v -> setLanguage("bn"));
    }

    private void buildModules() {
        int[] icons = {
                R.drawable.ic_nova_sos, R.drawable.ic_nova_safe, R.drawable.ic_nova_campus,
                R.drawable.ic_nova_lite, R.drawable.ic_nova_study, R.drawable.ic_nova_insights
        };
        int[] colors = {
                R.color.nova_sos, R.color.nova_safe, R.color.nova_campus,
                R.color.nova_lite, R.color.nova_study, R.color.nova_insights
        };
        int[] names = {
                R.string.nova_module_sos, R.string.nova_module_safe, R.string.nova_module_campus,
                R.string.nova_module_lite, R.string.nova_module_study, R.string.nova_module_insights
        };
        int[] descs = {
                R.string.nova_module_sos_desc, R.string.nova_module_safe_desc,
                R.string.nova_module_campus_desc, R.string.nova_module_lite_desc,
                R.string.nova_module_study_desc, R.string.nova_module_insights_desc
        };

        binding.moduleList.removeAllViews();
        for (int i = 0; i < icons.length; i++) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.topMargin = NovaUi.dp(requireContext(), i == 0 ? 0 : 10);
            row.setLayoutParams(params);

            ImageView icon = new ImageView(requireContext());
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                    NovaUi.dp(requireContext(), 34), NovaUi.dp(requireContext(), 34));
            icon.setLayoutParams(iconParams);
            icon.setPadding(NovaUi.dp(requireContext(), 7), NovaUi.dp(requireContext(), 7),
                    NovaUi.dp(requireContext(), 7), NovaUi.dp(requireContext(), 7));
            icon.setImageResource(icons[i]);
            int accent = ContextCompatColor(colors[i]);
            icon.setColorFilter(accent);
            icon.setBackground(NovaUi.accentCircle(requireContext(), accent));
            row.addView(icon);

            LinearLayout texts = new LinearLayout(requireContext());
            texts.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            textParams.setMarginStart(NovaUi.dp(requireContext(), 12));
            texts.setLayoutParams(textParams);

            TextView name = new TextView(requireContext());
            name.setText(names[i]);
            name.setTextColor(getResources().getColor(R.color.nova_text, null));
            name.setTextSize(14f);
            name.setTypeface(name.getTypeface(), android.graphics.Typeface.BOLD);
            texts.addView(name);

            TextView desc = new TextView(requireContext());
            desc.setText(descs[i]);
            desc.setTextColor(getResources().getColor(R.color.nova_text_2, null));
            desc.setTextSize(12f);
            texts.addView(desc);

            row.addView(texts);
            binding.moduleList.addView(row);
        }
    }

    private int ContextCompatColor(int colorRes) {
        return androidx.core.content.ContextCompat.getColor(requireContext(), colorRes);
    }

    private void styleLanguage() {
        String language = settingsStore.current().language;
        styleChip(binding.btnEnglish, "en".equals(language));
        styleChip(binding.btnBangla, "bn".equals(language));
    }

    private void styleChip(TextView view, boolean selected) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(NovaUi.dpFloat(requireContext(), 16));
        if (selected) {
            drawable.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
            drawable.setColors(new int[]{
                    requireContext().getColor(R.color.nova_cyan),
                    requireContext().getColor(R.color.nova_violet),
                    requireContext().getColor(R.color.nova_magenta)});
            view.setTextColor(0xFFFFFFFF);
        } else {
            drawable.setColor(0x00000000);
            drawable.setStroke(NovaUi.dp(requireContext(), 1),
                    requireContext().getColor(R.color.nova_stroke));
            view.setTextColor(requireContext().getColor(R.color.nova_text));
        }
        view.setBackground(drawable);
    }

    private void setLanguage(String language) {
        if (language.equals(settingsStore.current().language)) return;
        settingsStore.setLanguage(requireContext(), language);
        Toast.makeText(requireContext(), R.string.nova_language_changed, Toast.LENGTH_SHORT).show();
        dismissAllowingStateLoss();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
