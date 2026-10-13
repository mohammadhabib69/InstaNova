package com.insta.clone.ui.main;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.insta.clone.R;
import com.insta.clone.data.local.SessionManager;
import com.insta.clone.data.repository.ActivityRepository;
import com.insta.clone.databinding.ActivityMainBinding;
import com.insta.clone.ui.create.CreatePostActivity;
import com.insta.clone.ui.home.HomeFragment;
import com.insta.clone.util.Constants;
import com.insta.clone.util.Resource;

/**
 * Main application container with the shared bottom navigation.
 *
 * Behavioral contract is unchanged: real nav destinations (Home, Search,
 * Reels, Profile) and the Create action (opens the New post picker) keep their
 * existing targets. This class only adds Page-0 presentation: edge-to-edge
 * insets, the Reels black-bar treatment, and the profile-avatar tab icon.
 */
public class MainActivity extends AppCompatActivity {

    /** Lets other screens open an existing destination (e.g. Settings > Saved). */
    public static final String EXTRA_NAV_DESTINATION = "nav_destination";

    private ActivityMainBinding binding;
    private NavController navController;
    private ActivityRepository activityRepository;
    private SessionManager sessionManager;
    private final Handler badgePollHandler = new Handler(Looper.getMainLooper());
    private Runnable badgePollRunnable;

    private boolean profileSelected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Settings > Saved reuses the existing 'Saved' destination of this graph.
        int destination = getIntent() != null
                ? getIntent().getIntExtra(EXTRA_NAV_DESTINATION, 0) : 0;
        if (destination != 0 && navController != null) {
            navController.navigate(destination);
        }

        activityRepository = new ActivityRepository(this);
        sessionManager = SessionManager.getInstance(this);

        applyBottomInsets();

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_main);
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
            NavigationUI.setupWithNavController(binding.bottomNavigation, navController);
        }

        setupBottomNavigation();
        setupReelsBarTreatment();
        loadProfileAvatarIcon();
        startBadgePolling();
    }

    /** Pads the bottom bar by the system gesture inset so icons clear the handle. */
    private void applyBottomInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigation, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), bars.bottom);
            return insets;
        });
    }

    private void setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            // Create opens the New post picker and never stays selected.
            if (itemId == R.id.navigation_create) {
                startActivity(new Intent(this, CreatePostActivity.class));
                return false;
            }

            if (navController != null) {
                return NavigationUI.onNavDestinationSelected(item, navController);
            }
            return false;
        });

        // Re-selection on Home scrolls feed to top
        binding.bottomNavigation.setOnItemReselectedListener(item -> {
            if (item.getItemId() == R.id.navigation_home) {
                NavHostFragment navHost = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_main);
                if (navHost != null) {
                    Fragment current = navHost.getChildFragmentManager().getPrimaryNavigationFragment();
                    if (current instanceof HomeFragment) {
                        ((HomeFragment) current).scrollToTop();
                    }
                }
            }
        });
    }

    /**
     * PAGE 5 — the Reels bar is black in both themes, so every menu icon is
     * forced white for as long as that destination is selected. Leaving the
     * destination restores the theme-driven icons.
     */
    private void applyReelsIconTint(boolean onReels) {
        if (binding == null) return;
        binding.bottomNavigation.setItemIconTintList(
                onReels ? ColorStateList.valueOf(Color.WHITE) : null);
    }

    /** On Reels the whole bar is black with white icons and no top divider. */
    private void setupReelsBarTreatment() {
        if (navController == null) return;
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            boolean onReels = destination.getId() == R.id.navigation_reels;
            applyReelsStyling(onReels);
            applyReelsIconTint(onReels);
            profileSelected = destination.getId() == R.id.navigation_profile;
            loadProfileAvatarIcon();
        });
    }

    private void applyReelsStyling(boolean onReels) {
        if (binding == null) return;
        if (onReels) {
            binding.bottomNavigation.setBackgroundColor(Color.BLACK);
            binding.bottomNavDivider.setVisibility(android.view.View.GONE);
        } else {
            binding.bottomNavigation.setBackgroundColor(resolveThemeColor(R.attr.themeBackground, Color.WHITE));
            binding.bottomNavDivider.setVisibility(android.view.View.VISIBLE);
        }
    }

    /**
     * Swaps the Profile tab icon to the user's circular avatar when an
     * avatar_url exists; a 1.5dp textPrimary ring is drawn when selected.
     * Falls back to the person-circle vector otherwise.
     */
    private void loadProfileAvatarIcon() {
        if (binding == null || sessionManager == null) return;
        final String avatarUrl = sessionManager.getAvatarUrl();
        if (avatarUrl == null || avatarUrl.trim().isEmpty()) {
            return; // menu selector handles the person-circle fallback
        }
        final int sizePx = dp(R.dimen.avatar_nav, 28);
        final boolean ringed = profileSelected;
        try {
            Glide.with(this)
                    .asBitmap()
                    .load(avatarUrl)
                    .circleCrop()
                    .into(new CustomTarget<Bitmap>(sizePx, sizePx) {
                        @Override
                        public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                            Drawable d = buildAvatarDrawable(resource, ringed);
                            setProfileIcon(d);
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {
                        }
                    });
        } catch (Exception e) {
            Log.w("MainActivity", "Avatar icon load failed", e);
        }
    }

    private void setProfileIcon(Drawable d) {
        if (binding == null || d == null) return;
        try {
            binding.bottomNavigation.getMenu().findItem(R.id.navigation_profile)
                    .setIcon(new UntintedDrawable(d));
        } catch (Exception ignored) {
        }
    }

    /**
     * PAGE 5 — on the Reels destination every tab icon is forced white so it
     * reads on the black bar. The profile tab holds the user's real avatar, so
     * that one drawable ignores the tint and keeps its colours.
     */
    private static final class UntintedDrawable extends Drawable {
        private final Drawable wrapped;

        UntintedDrawable(Drawable wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            wrapped.setBounds(getBounds());
            wrapped.draw(canvas);
        }

        @Override
        public void setAlpha(int alpha) {
            wrapped.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            wrapped.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return wrapped.getOpacity();
        }

        @Override
        public int getIntrinsicWidth() {
            return wrapped.getIntrinsicWidth();
        }

        @Override
        public int getIntrinsicHeight() {
            return wrapped.getIntrinsicHeight();
        }

        // The navigation bar icon tint is deliberately ignored for the avatar.
        @Override
        public void setTintList(ColorStateList tint) {
        }

        @Override
        public void setTint(int tintColor) {
        }
    }

    private Drawable buildAvatarDrawable(Bitmap circleBitmap, boolean ringed) {
        int size = circleBitmap.getWidth();
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        float radius = size / 2f;

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setShader(new BitmapShader(circleBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));

        if (ringed) {
            float stroke = dp(R.dimen.nav_avatar_ring_stroke, 2);
            float inner = radius - stroke - dp(R.dimen.story_ring_gap, 3) / 2f;
            // Avatar inset to leave room for ring + gap.
            float scale = (inner * 2f) / size;
            canvas.save();
            canvas.translate(radius - inner, radius - inner);
            canvas.scale(scale, scale);
            canvas.drawCircle(radius, radius, radius, fill);
            canvas.restore();

            Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(stroke);
            ring.setColor(resolveThemeColor(R.attr.themeTextPrimary, Color.BLACK));
            canvas.drawCircle(radius, radius, radius - stroke / 2f, ring);
        } else {
            canvas.drawCircle(radius, radius, radius, fill);
        }
        return new BitmapDrawable(getResources(), out);
    }

    private int dp(int dimenRes, int fallbackDp) {
        try {
            return getResources().getDimensionPixelSize(dimenRes);
        } catch (Exception e) {
            return Math.round(fallbackDp * getResources().getDisplayMetrics().density);
        }
    }

    private int resolveThemeColor(int attr, int fallback) {
        try {
            TypedValue tv = new TypedValue();
            if (getTheme().resolveAttribute(attr, tv, true)) {
                if (tv.resourceId != 0) {
                    return ContextCompat.getColor(this, tv.resourceId);
                }
                return tv.data;
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private void startBadgePolling() {
        badgePollRunnable = new Runnable() {
            @Override
            public void run() {
                pollUnreadCounts();
                badgePollHandler.postDelayed(this, Constants.POLLING_BADGES_MS);
            }
        };
        badgePollHandler.post(badgePollRunnable);
    }

    private void pollUnreadCounts() {
        // The development build can enter Home without an auth session. The
        // unread-count RPC is authenticated-only, so avoid a repeated 401
        // request and leave badges unchanged until a real session exists.
        if (sessionManager == null || sessionManager.getAccessToken() == null
                || sessionManager.getAccessToken().trim().isEmpty()) {
            return;
        }
        final LiveData<Resource<ActivityRepository.UnreadCounts>> request;
        try {
            request = activityRepository.getUnreadCounts();
        } catch (RuntimeException e) {
            Log.e("MainActivity", "Unable to start badge request", e);
            return;
        }

        request.observe(this, new Observer<Resource<ActivityRepository.UnreadCounts>>() {
            @Override
            public void onChanged(Resource<ActivityRepository.UnreadCounts> resource) {
                if (resource == null) return;
                if (resource.status == Resource.Status.SUCCESS && resource.data != null) {
                    // Badge state can be accessed by fragments or notification dots.
                }
                if (resource.status == Resource.Status.SUCCESS || resource.status == Resource.Status.ERROR) {
                    request.removeObserver(this);
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (badgePollRunnable != null) {
            badgePollHandler.removeCallbacks(badgePollRunnable);
        }
        binding = null;
    }
}
