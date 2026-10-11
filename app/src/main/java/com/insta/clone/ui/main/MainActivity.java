package com.insta.clone.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;
import com.insta.clone.R;
import com.insta.clone.data.repository.ActivityRepository;
import com.insta.clone.databinding.ActivityMainBinding;
import com.insta.clone.ui.create.CreatePostActivity;
import com.insta.clone.ui.home.HomeFragment;
import com.insta.clone.util.Constants;
import com.insta.clone.util.Resource;

/**
 * Main application container with 5-tab bottom navigation, badge polling, and create action.
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private NavController navController;
    private ActivityRepository activityRepository;
    private final Handler badgePollHandler = new Handler(Looper.getMainLooper());
    private Runnable badgePollRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        activityRepository = new ActivityRepository(this);

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_main);
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
            NavigationUI.setupWithNavController(binding.bottomNavigation, navController);
        }

        setupBottomNavigation();
        startBadgePolling();
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

    private void startBadgePolling() {
        badgePollRunnable = new Runnable() {
            @Override
            public void run() {
                activityRepository.getUnreadCounts().observe(MainActivity.this, resource -> {
                    if (resource.status == Resource.Status.SUCCESS && resource.data != null) {
                        // Badge state can be accessed by fragments or notification dots
                    }
                });
                badgePollHandler.postDelayed(this, Constants.POLLING_BADGES_MS);
            }
        };
        badgePollHandler.post(badgePollRunnable);
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
