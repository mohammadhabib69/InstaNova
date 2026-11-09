package com.insta.nova.ui.main;

import android.content.Intent;
import android.content.SharedPreferences;
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
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
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
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.insta.nova.R;
import com.insta.nova.data.NovaBadgeStore;
import com.insta.nova.data.NovaDataSaver;
import com.insta.nova.data.NovaRepository;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.model.Profile;
import com.insta.nova.data.repository.ActivityRepository;
import com.insta.nova.data.repository.ProfileRepository;
import com.insta.nova.databinding.ActivityMainBinding;
import com.insta.nova.ui.home.HomeFragment;
import com.insta.nova.util.Constants;
import com.insta.nova.util.Resource;
import com.insta.nova.widget.GlowView;
import com.insta.nova.widget.NovaUi;

/**
 * Main application container with the shared bottom navigation.
 *
 * Behavioral contract is unchanged: real nav destinations (Home, Search,
 * Reels, Profile) and the Create action (opens the New post picker) keep their
 * existing targets. This class only adds Page-0 presentation: edge-to-edge
 * insets, the Reels black-bar treatment, and the profile-avatar tab icon.
 */
public class MainActivity extends AppCompatActivity {

    private static final String PREF_APP = "insta_settings";
    private static final String KEY_NOTIFICATION_ASKED = "notification_permission_asked";

    /** Lets other screens open an existing destination (e.g. Settings > Saved). */
    public static final String EXTRA_NAV_DESTINATION = "nav_destination";
    /** Reel to open when the Reels destination is selected from Profile. */
    public static final String EXTRA_REEL_ID = "reel_id";

    private ActivityMainBinding binding;
    private NavController navController;
    private ActivityRepository activityRepository;
    private ProfileRepository profileRepository;
    private SessionManager sessionManager;
    private final Handler badgePollHandler = new Handler(Looper.getMainLooper());
    private Runnable badgePollRunnable;
    private final Handler novaPollHandler = new Handler(Looper.getMainLooper());
    private Runnable novaPollRunnable;
    private ObjectAnimator novaBreathAnimator;
    private NovaRepository novaRepository;
    private boolean novaSelected;

    private boolean profileSelected = false;
    private int bottomNavBaseHeight;
    private String loadedAvatarUrl;
    private boolean avatarLoadInFlight;
    private boolean avatarProfileFetchStarted;
    private Bitmap loadedAvatarBitmap;

    /** Repaints the nav avatar after Edit Profile / avatar upload changes the cached URL. */
    private final SharedPreferences.OnSharedPreferenceChangeListener avatarPrefListener =
            (prefs, key) -> {
                if ("avatar_url".equals(key)) {
                    loadedAvatarUrl = null;
                    loadedAvatarBitmap = null;
                    loadProfileAvatarIcon();
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // A row can only be left in "uploading" by a process that died mid-upload, so reclaim
        // those before anything else can read the queue.
        com.insta.nova.data.NovaPostQueue.reclaimStuck(this);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Settings > Saved reuses the existing 'Saved' destination of this graph.
        // Captured here, navigated after navController exists (it is created below).
        final int destination = getIntent() != null
                ? getIntent().getIntExtra(EXTRA_NAV_DESTINATION, 0) : 0;

        activityRepository = new ActivityRepository(this);
        novaRepository = new NovaRepository(this);
        profileRepository = new ProfileRepository(this);
        sessionManager = SessionManager.getInstance(this);
        // Record who is signed in: the switcher needs the row, and a later username
        // login needs the address (Supabase has no auth users to look it up from).
        com.insta.nova.data.AccountSync.sync(this);

        applyBottomInsets();

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_main);
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
            NavigationUI.setupWithNavController(binding.bottomNavigation, navController);
            if (destination != 0) {
                navController.navigate(destination);
            }
        }

        setupBottomNavigation();
        setupReelsBarTreatment();
        setupNovaTab();
        loadProfileAvatarIcon();
        startBadgePolling();
        ensureNotificationPermission();
    }

    /**
     * Asks for POST_NOTIFICATIONS if it was never granted.
     *
     * <p>The onboarding wizard asks once, and a denial there is permanent as far as the app
     * is concerned — every later call notification and every missed-call entry is silently
     * dropped by the platform. That is exactly the "no calls or notifications in the status
     * bar" state a user cannot diagnose from inside the app, so the ask is repeated here
     * (from an Activity the user is actually looking at) and the result is logged.
     */
    private void ensureNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return;
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return;
        }
        // After two denials the platform stops showing the dialog, so launch() would
        // return instantly with nothing on screen — a prompt that never appears, on
        // every cold start. Once it has been asked, only ask again while the OS still
        // considers the question open; otherwise leave it to the Notifications screen,
        // which explains the state and links to system settings.
        boolean askedBefore = getSharedPreferences(PREF_APP, MODE_PRIVATE)
                .getBoolean(KEY_NOTIFICATION_ASKED, false);
        boolean stillOpen = shouldShowRequestPermissionRationale(
                android.Manifest.permission.POST_NOTIFICATIONS);
        if (askedBefore && !stillOpen) {
            return;
        }
        getSharedPreferences(PREF_APP, MODE_PRIVATE).edit()
                .putBoolean(KEY_NOTIFICATION_ASKED, true).apply();
        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
    }

    private final androidx.activity.result.ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
                    granted -> Log.i("MainActivity", "POST_NOTIFICATIONS granted=" + granted));

    /**
     * The bar owns the navigation-bar inset once: its height grows by the inset
     * while the existing 52dp content height and icon geometry stay unchanged.
     */
    private void applyBottomInsets() {
        bottomNavBaseHeight = binding.bottomNavigation.getLayoutParams().height;
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigation, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), bars.bottom);
            int targetHeight = bottomNavBaseHeight + bars.bottom;
            if (v.getLayoutParams().height != targetHeight) {
                v.getLayoutParams().height = targetHeight;
                v.requestLayout();
            }
            return insets;
        });
    }

    private void setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (navController != null) {
                return NavigationUI.onNavDestinationSelected(item, navController);
            }
            return false;
        });

        // Re-selection on Home scrolls feed to top
        binding.bottomNavigation.setOnItemReselectedListener(item -> {
            NavHostFragment navHost = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_main);
            if (navHost == null) return;
            Fragment current = navHost.getChildFragmentManager().getPrimaryNavigationFragment();
            if (item.getItemId() == R.id.navigation_home && current instanceof HomeFragment) {
                ((HomeFragment) current).scrollToTop();
            } else if (item.getItemId() == R.id.navigation_nova
                    && current instanceof com.insta.nova.hub.NovaHubFragment) {
                ((com.insta.nova.hub.NovaHubFragment) current).scrollToTop();
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
            updateNovaTabState(destination.getId() == R.id.navigation_nova);
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
        String avatarUrl = sessionManager.getAvatarUrl();
        if (avatarUrl == null || avatarUrl.trim().isEmpty()) {
            ensureProfileAvatarUrl();
            return; // menu selector keeps the person-circle fallback until resolved
        }

        if (avatarUrl.equals(loadedAvatarUrl) && loadedAvatarBitmap != null) {
            setProfileIcon(buildAvatarDrawable(loadedAvatarBitmap, profileSelected));
            return;
        }
        if (avatarLoadInFlight) return;

        avatarLoadInFlight = true;
        final String requestedUrl = avatarUrl;
        final int sizePx = dp(R.dimen.avatar_nav, 28);
        try {
            Glide.with(this)
                    .asBitmap()
                    .load(avatarUrl)
                    .circleCrop()
                    .into(new CustomTarget<Bitmap>(sizePx, sizePx) {
                        @Override
                        public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                            avatarLoadInFlight = false;
                            loadedAvatarUrl = requestedUrl;
                            loadedAvatarBitmap = resource;
                            setProfileIcon(buildAvatarDrawable(resource, profileSelected));
                        }

                        @Override
                        public void onLoadFailed(@Nullable Drawable errorDrawable) {
                            avatarLoadInFlight = false;
                            loadedAvatarUrl = null;
                            loadedAvatarBitmap = null;
                            resetProfileIcon();
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {
                            avatarLoadInFlight = false;
                        }
                    });
        } catch (Exception e) {
            avatarLoadInFlight = false;
            resetProfileIcon();
            Log.w("MainActivity", "Avatar icon load failed", e);
        }
    }

    /**
     * Cold launch after an account restoration can have the profile id but no
     * cached avatar URL. Resolve it once from the same Firebase-mapped profile
     * used by the Profile screen, then cache it for subsequent starts.
     */
    private void ensureProfileAvatarUrl() {
        if (avatarProfileFetchStarted || profileRepository == null || sessionManager == null) return;
        String userId = sessionManager.getUserId();
        if (userId.isEmpty()) return;
        avatarProfileFetchStarted = true;
        profileRepository.getProfile(userId).observe(this, resource -> {
            if (resource == null) return;
            if (resource.status == Resource.Status.SUCCESS && resource.data != null) {
                Profile profile = resource.data;
                String url = profile.getAvatarUrl();
                if (url != null && !url.trim().isEmpty()) {
                    sessionManager.updateAvatar(url);
                    if (profile.getUsername() != null && !profile.getUsername().trim().isEmpty()) {
                        sessionManager.updateUsername(profile.getUsername());
                    }
                    loadProfileAvatarIcon();
                } else {
                    resetProfileIcon();
                }
            } else if (resource.status == Resource.Status.ERROR) {
                avatarProfileFetchStarted = false;
                resetProfileIcon();
            }
        });
    }

    private void resetProfileIcon() {
        if (binding == null) return;
        try {
            binding.bottomNavigation.getMenu().findItem(R.id.navigation_profile)
                    .setIcon(R.drawable.bottom_nav_profile);
        } catch (Exception ignored) {
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
        // FirebaseAuth is the only proof of an authenticated session; the local
        // prefs only cache display state.
        if (sessionManager == null
                || com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() == null) {
            return;
        }
        // Shared stream: the poll only nudges the single owner, which collapses this
        // with any request Home may have just started (see ChatRepository).
        try {
            activityRepository.refreshUnreadCounts(false);
        } catch (RuntimeException e) {
            Log.e("MainActivity", "Unable to start badge request", e);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // #67 — a call can arrive while this app is behind the launcher, where only a
        // notification can be shown. Opening the app from its icon has to bring the
        // incoming screen back, otherwise the call is visible only in the shade.
        com.insta.nova.calling.CallManager.getInstance(this).onAppForegrounded();
        // Re-evaluated on every foregrounding: perf collection resumes on its own once
        // the user has freed space, without needing a restart.
        com.insta.nova.InstaApp.applyStorageAwarePerformanceCollection();
        if (sessionManager != null) {
            sessionManager.registerAvatarListener(avatarPrefListener);
            loadProfileAvatarIcon();
        }
        startNovaPolling();
        if (novaSelected) startNovaBreathing();
    }

    @Override
    protected void onStop() {
        if (sessionManager != null) {
            sessionManager.unregisterAvatarListener(avatarPrefListener);
        }
        stopNovaPolling();
        stopNovaBreathing();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (badgePollRunnable != null) {
            badgePollHandler.removeCallbacks(badgePollRunnable);
        }
        stopNovaPolling();
        stopNovaBreathing();
        binding = null;
    }

    /* ------------------------------------------------------------------ */
    /* NOVA centre tab                                                     */
    /* ------------------------------------------------------------------ */

    private void setupNovaTab() {
        if (binding == null) return;
        binding.bottomNavigation.setClipChildren(false);
        binding.bottomNavigation.setClipToPadding(false);

        // The Nova tab is a stock bottom-navigation item now: no glow, no breathing
        // scale. Only the unread badge is Nova-specific.
        binding.novaTabGlow.setVisibility(View.GONE);

        NovaBadgeStore.get().unread().observe(this, count -> {
            if (binding == null) return;
            int value = count == null ? 0 : count;
            if (value <= 0) {
                binding.novaTabBadge.setVisibility(View.GONE);
            } else {
                binding.novaTabBadge.setText(value > 9 ? "9+" : String.valueOf(value));
                binding.novaTabBadge.setVisibility(View.VISIBLE);
            }
        });
    }

    private void updateNovaTabState(boolean selected) {
        novaSelected = selected;
        if (binding == null) return;
        binding.novaTabGlow.setVisibility(View.GONE);
        stopNovaBreathing();
        NovaUi.styleStatusBar(this, NovaUi.isNight(this));
    }

    private View novaItemView() {
        if (binding == null) return null;
        return binding.bottomNavigation.findViewById(R.id.navigation_nova);
    }

    private void startNovaBreathing() {
        View item = novaItemView();
        if (item == null || novaBreathAnimator != null) return;
        if (!NovaUi.animationsEnabled(this)) {
            item.setScaleX(1f);
            item.setScaleY(1f);
            return;
        }
        item.setPivotX(item.getWidth() / 2f);
        item.setPivotY(item.getHeight() / 2f);
        novaBreathAnimator = ObjectAnimator.ofPropertyValuesHolder(item,
                android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.08f),
                android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.08f));
        novaBreathAnimator.setDuration(4000);
        novaBreathAnimator.setRepeatMode(ValueAnimator.REVERSE);
        novaBreathAnimator.setRepeatCount(ValueAnimator.INFINITE);
        novaBreathAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        novaBreathAnimator.start();
    }

    private void stopNovaBreathing() {
        if (novaBreathAnimator != null) {
            novaBreathAnimator.cancel();
            novaBreathAnimator = null;
        }
        View item = novaItemView();
        if (item != null) {
            item.setScaleX(1f);
            item.setScaleY(1f);
        }
    }

    private void startNovaPolling() {
        if (novaRepository == null) return;
        novaPollRunnable = new Runnable() {
            @Override
            public void run() {
                refreshNovaUnread();
                novaPollHandler.postDelayed(this, 60_000L);
            }
        };
        novaPollHandler.post(novaPollRunnable);
    }

    private void stopNovaPolling() {
        if (novaPollRunnable != null) novaPollHandler.removeCallbacks(novaPollRunnable);
        novaPollRunnable = null;
    }

    private void refreshNovaUnread() {
        if (sessionManager == null || sessionManager.getUserId().isEmpty()) return;
        if (com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() == null) return;
        novaRepository.unreadAlertsOnce(count -> NovaBadgeStore.get().set(count));
        // One poll per minute also raises a system notification for each new alert id.
        com.insta.nova.alerts.AlertsPoller.tick(this);
    }
}
