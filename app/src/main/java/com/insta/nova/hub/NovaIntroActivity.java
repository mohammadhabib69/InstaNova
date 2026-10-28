package com.insta.nova.hub;

import android.animation.ObjectAnimator;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.insta.nova.R;
import com.insta.nova.data.NovaSettingsStore;
import com.insta.nova.databinding.ActivityNovaIntroBinding;
import com.insta.nova.databinding.ItemNovaIntroPageBinding;
import com.insta.nova.widget.NovaUi;

/** Three-page first-run intro; shown once, then stored in nova_settings.intro_seen. */
public class NovaIntroActivity extends AppCompatActivity {

    private ActivityNovaIntroBinding binding;
    private NovaSettingsStore settingsStore;
    private ImageView[] dots;

    private final int[] titles = {
            R.string.nova_intro_1_title, R.string.nova_intro_2_title, R.string.nova_intro_3_title
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNovaIntroBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        settingsStore = NovaSettingsStore.get(this);
        NovaUi.styleStatusBar(this, NovaUi.isNight(this));

        binding.pager.setAdapter(new IntroAdapter());
        binding.pager.setPageTransformer((page, position) -> {
            page.setAlpha(1f - Math.min(0.65f, Math.abs(position) * 0.55f));
            page.setTranslationX(-position * page.getWidth() * 0.12f);
        });
        binding.pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateDots(position);
                binding.btnNext.setText(position == 2 ? R.string.nova_enter : R.string.nova_next);
            }
        });

        buildDots();
        binding.btnSkip.setOnClickListener(v -> finishIntro());
        binding.btnNext.setOnClickListener(v -> {
            int page = binding.pager.getCurrentItem();
            if (page < 2) {
                binding.pager.setCurrentItem(page + 1, true);
            } else {
                finishIntro();
            }
        });
    }

    private int themeColor(int attr, int fallback) {
        android.util.TypedValue value = new android.util.TypedValue();
        if (getTheme().resolveAttribute(attr, value, true)) {
            if (value.resourceId != 0) return androidx.core.content.ContextCompat.getColor(this, value.resourceId);
            return value.data;
        }
        return fallback;
    }

    private void buildDots() {
        dots = new ImageView[3];
        binding.dots.removeAllViews();
        for (int i = 0; i < 3; i++) {
            ImageView dot = new ImageView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    NovaUi.dp(this, 8), NovaUi.dp(this, 8));
            if (i > 0) params.setMarginStart(NovaUi.dp(this, 6));
            dot.setLayoutParams(params);
            binding.dots.addView(dot);
            dots[i] = dot;
        }
        updateDots(0);
    }

    private void updateDots(int selected) {
        for (int i = 0; i < dots.length; i++) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.RECTANGLE);
            drawable.setCornerRadius(NovaUi.dpFloat(this, 4));
            if (i == selected) {
                drawable.setColors(new int[]{
                        getColor(R.color.nova_cyan), getColor(R.color.nova_violet),
                        getColor(R.color.nova_magenta)});
                drawable.setGradientType(GradientDrawable.LINEAR_GRADIENT);
                drawable.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
                dots[i].setLayoutParams(new LinearLayout.LayoutParams(
                        NovaUi.dp(this, 22), NovaUi.dp(this, 8)));
            } else {
                drawable.setColor(themeColor(R.attr.themeTextSecondary, 0xFF737373));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        NovaUi.dp(this, 8), NovaUi.dp(this, 8));
                if (i > 0) params.setMarginStart(NovaUi.dp(this, 6));
                dots[i].setLayoutParams(params);
            }
            dots[i].setImageDrawable(drawable);
        }
    }

    private void finishIntro() {
        settingsStore.setIntroSeen();
        finish();
        overridePendingTransition(0, android.R.anim.fade_out);
    }

    /* ------------------------------------------------------------------ */

    private final class IntroAdapter extends RecyclerView.Adapter<IntroAdapter.Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemNovaIntroPageBinding page = ItemNovaIntroPageBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new Holder(page);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            ItemNovaIntroPageBinding page = holder.binding;
            page.introTitle.setText(titles[position]);
            page.ivIntroMark.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
            page.orbitContainer.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
            page.bigIcon.setVisibility(position == 2 ? View.VISIBLE : View.GONE);

            if (position == 0) {
                page.introBody.setText(R.string.nova_meaning);
            }
            if (position == 1) {
                page.introBody.setText(R.string.nova_intro_2_body);
                buildOrbit(page.orbitContainer);
            }
            if (position == 2) {
                page.introBody.setText(R.string.nova_intro_3_body);
                page.bigIcon.setImageResource(R.drawable.ic_nova_lite);
                page.bigIcon.setColorFilter(getColor(R.color.nova_lite));
            }
        }

        @Override
        public int getItemCount() {
            return 3;
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ItemNovaIntroPageBinding binding;

            Holder(ItemNovaIntroPageBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }

    private void buildOrbit(FrameLayout container) {
        container.removeAllViews();
        int[] icons = {
                R.drawable.ic_nova_sos, R.drawable.ic_nova_safe, R.drawable.ic_nova_campus,
                R.drawable.ic_nova_lite, R.drawable.ic_nova_study, R.drawable.ic_nova_insights
        };
        int[] colors = {
                R.color.nova_sos, R.color.nova_safe, R.color.nova_campus,
                R.color.nova_lite, R.color.nova_study, R.color.nova_insights
        };
        float radius = NovaUi.dpFloat(this, 88);
        for (int i = 0; i < icons.length; i++) {
            ImageView icon = new ImageView(this);
            int size = NovaUi.dp(this, 44);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size, Gravity.CENTER);
            icon.setLayoutParams(params);
            icon.setPadding(NovaUi.dp(this, 10), NovaUi.dp(this, 10), NovaUi.dp(this, 10), NovaUi.dp(this, 10));
            icon.setImageResource(icons[i]);
            icon.setColorFilter(getColor(colors[i]));
            icon.setBackground(NovaUi.accentCircle(this, getColor(colors[i])));
            icon.setContentDescription(getString(R.string.nova_cd_module,
                    getString(moduleName(i))));
            double angle = Math.toRadians(i * 60);
            icon.setTranslationX((float) Math.cos(angle) * radius);
            icon.setTranslationY((float) Math.sin(angle) * radius);
            container.addView(icon);
        }
        if (NovaUi.animationsEnabled(this)) {
            ObjectAnimator spin = ObjectAnimator.ofFloat(container, View.ROTATION, 0f, 360f);
            spin.setDuration(26_000);
            spin.setRepeatCount(ObjectAnimator.INFINITE);
            spin.start();
        }
    }

    private int moduleName(int index) {
        switch (index) {
            case 0: return R.string.nova_module_sos;
            case 1: return R.string.nova_module_safe;
            case 2: return R.string.nova_module_campus;
            case 3: return R.string.nova_module_lite;
            case 4: return R.string.nova_module_study;
            default: return R.string.nova_module_insights;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
