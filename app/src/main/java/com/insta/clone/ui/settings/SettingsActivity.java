package com.insta.clone.ui.settings;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.insta.clone.BuildConfig;
import com.insta.clone.R;
import com.insta.clone.data.local.SessionManager;
import com.insta.clone.data.repository.SettingsRepository;
import com.insta.clone.databinding.ActivitySettingsBinding;
import com.insta.clone.databinding.ItemSettingsGapBinding;
import com.insta.clone.databinding.ItemSettingsHeaderBinding;
import com.insta.clone.databinding.ItemSettingsRowBinding;
import com.insta.clone.ui.auth.AuthActivity;
import com.insta.clone.util.Constants;
import com.insta.clone.util.Resource;
import com.insta.clone.util.ToastUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * PAGE 13 — Settings and activity.
 *
 * One scrolling, searchable list. Every section/row from the reference is here;
 * rows that exist only in the design follow the shared decorative contract and
 * the functional ones perform real Supabase work (see the click switch below).
 */
public class SettingsActivity extends AppCompatActivity {

    public static final String PREF_THEME = "app_theme_mode";
    private static final String PREF_FILE = "insta_settings";

    private enum Kind { HEADER, ROW, GAP }

    private static final class Item {
        final Kind kind;
        final String section;
        final int labelRes;
        final int iconRes;
        final String key;
        final boolean twoLine;

        Item(Kind kind, String section, int labelRes, int iconRes, String key, boolean twoLine) {
            this.kind = kind;
            this.section = section;
            this.labelRes = labelRes;
            this.iconRes = iconRes;
            this.key = key;
            this.twoLine = twoLine;
        }
    }

    private ActivitySettingsBinding binding;
    private SettingsRepository repository;
    private SessionManager sessionManager;
    private SettingsAdapter adapter;

    private final List<Item> allItems = new ArrayList<>();
    private String privacyValue = "";
    private String themeValue = "";
    private String archiveValue = "";
    private String scheduledValue = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = new SettingsRepository(this);
        sessionManager = SessionManager.getInstance(this);

        binding.btnBack.setOnClickListener(v -> finish());
        binding.rvSettings.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SettingsAdapter();
        binding.rvSettings.setAdapter(adapter);

        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                applyFilter(s == null ? "" : s.toString());
            }
        });

        themeValue = themeLabel(readThemeMode());
        archiveValue = "";
        scheduledValue = getString(R.string.settings_value_zero);

        buildItems();
        adapter.submit(allItems);
        loadPrivacyValue();
        loadArchiveCount();
    }

    @Override
    protected void onResume() {
        super.onResume();
        themeValue = themeLabel(readThemeMode());
        loadPrivacyValue();
    }

    /* ------------------------------------------------------------------ */
    /* Item model                                                          */
    /* ------------------------------------------------------------------ */

    private void buildItems() {
        allItems.clear();
        String sectionAccount = getString(R.string.settings_section_account);
        String sectionHow = getString(R.string.settings_section_how_you_use, com.insta.clone.util.Constants.WORDMARK);
        String sectionWho = getString(R.string.settings_section_who_can_see);
        String sectionPro = getString(R.string.settings_section_professionals);
        String sectionMore = getString(R.string.settings_section_more_info);
        String sectionLogin = getString(R.string.settings_section_login);

        // Your account
        allItems.add(new Item(Kind.HEADER, sectionAccount, 0, 0, null, false));
        allItems.add(new Item(Kind.ROW, sectionAccount, R.string.settings_accounts_center,
                R.drawable.ic_accounts_center, "accounts_center", true));
        allItems.add(new Item(Kind.GAP, sectionAccount, 0, 0, null, false));

        // How you use Instagram
        allItems.add(new Item(Kind.HEADER, sectionHow, 0, 0, null, false));
        allItems.add(new Item(Kind.ROW, sectionHow, R.string.settings_saved, R.drawable.ic_bookmark, "saved", false));
        allItems.add(new Item(Kind.ROW, sectionHow, R.string.settings_archive, R.drawable.ic_archive, "archive", false));
        allItems.add(new Item(Kind.ROW, sectionHow, R.string.settings_your_activity, R.drawable.ic_activity, "your_activity", false));
        allItems.add(new Item(Kind.ROW, sectionHow, R.string.settings_notifications, R.drawable.ic_bell, "notifications", false));
        allItems.add(new Item(Kind.ROW, sectionHow, R.string.settings_time_management, R.drawable.ic_clock, "time_management", false));
        allItems.add(new Item(Kind.GAP, sectionHow, 0, 0, null, false));

        // Who can see your content
        allItems.add(new Item(Kind.HEADER, sectionWho, 0, 0, null, false));
        allItems.add(new Item(Kind.ROW, sectionWho, R.string.settings_account_privacy, R.drawable.ic_lock, "account_privacy", false));
        allItems.add(new Item(Kind.ROW, sectionWho, R.string.settings_close_friends, R.drawable.ic_star_circle, "close_friends", false));
        allItems.add(new Item(Kind.GAP, sectionWho, 0, 0, null, false));

        // For professionals
        allItems.add(new Item(Kind.HEADER, sectionPro, 0, 0, null, false));
        allItems.add(new Item(Kind.ROW, sectionPro, R.string.settings_insights, R.drawable.ic_insights, "insights", false));
        allItems.add(new Item(Kind.ROW, sectionPro, R.string.settings_verified, R.drawable.ic_verified_outline, "verified", false));
        allItems.add(new Item(Kind.ROW, sectionPro, R.string.settings_scheduled_content, R.drawable.ic_schedule, "scheduled", false));
        allItems.add(new Item(Kind.GAP, sectionPro, 0, 0, null, false));

        // More info and support
        allItems.add(new Item(Kind.HEADER, sectionMore, 0, 0, null, false));
        allItems.add(new Item(Kind.ROW, sectionMore, R.string.settings_theme, R.drawable.ic_moon, "theme", false));
        allItems.add(new Item(Kind.ROW, sectionMore, R.string.settings_help, R.drawable.ic_help, "help", false));
        allItems.add(new Item(Kind.ROW, sectionMore, R.string.settings_about, R.drawable.ic_info, "about", false));
        if (BuildConfig.DEBUG) {
            allItems.add(new Item(Kind.ROW, sectionMore, R.string.settings_diagnostics, R.drawable.ic_bug, "diagnostics", false));
        }
        allItems.add(new Item(Kind.GAP, sectionMore, 0, 0, null, false));

        // Login
        allItems.add(new Item(Kind.HEADER, sectionLogin, 0, 0, null, false));
        allItems.add(new Item(Kind.ROW, sectionLogin, R.string.settings_logout, 0, "logout", false));
    }

    /* ------------------------------------------------------------------ */
    /* Search                                                             */
    /* ------------------------------------------------------------------ */

    private void applyFilter(String rawQuery) {
        String query = rawQuery.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            binding.tvNoResults.setVisibility(View.GONE);
            binding.rvSettings.setVisibility(View.VISIBLE);
            adapter.submit(allItems);
            return;
        }

        List<Item> filtered = new ArrayList<>();
        String currentSection = null;
        List<Item> pending = new ArrayList<>();
        for (Item item : allItems) {
            if (item.kind == Kind.HEADER) {
                if (headerMatches(item, query) || sectionHasMatch(item.section, query)) {
                    currentSection = item.section;
                    pending.add(item);
                } else {
                    currentSection = null;
                }
            } else if (item.kind == Kind.ROW) {
                if (currentSection != null && labelOf(item).toLowerCase(Locale.ROOT).contains(query)) {
                    pending.add(item);
                }
            }
        }
        filtered.addAll(pending);

        binding.tvNoResults.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        binding.rvSettings.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
        adapter.submit(filtered);
    }

    private boolean headerMatches(Item header, String query) {
        return header.labelRes != 0 && getString(header.labelRes).toLowerCase(Locale.ROOT).contains(query);
    }

    private boolean sectionHasMatch(String section, String query) {
        for (Item item : allItems) {
            if (item.kind == Kind.ROW && section.equals(item.section)
                    && labelOf(item).toLowerCase(Locale.ROOT).contains(query)) {
                return true;
            }
        }
        return false;
    }

    private String labelOf(Item item) {
        return item.labelRes == 0 ? "" : getString(item.labelRes);
    }

    /* ------------------------------------------------------------------ */
    /* Values                                                             */
    /* ------------------------------------------------------------------ */

    private int readThemeMode() {
        SharedPreferences sp = getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
        return sp.getInt(PREF_THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    private String themeLabel(int mode) {
        if (mode == AppCompatDelegate.MODE_NIGHT_NO) return getString(R.string.settings_theme_light);
        if (mode == AppCompatDelegate.MODE_NIGHT_YES) return getString(R.string.settings_theme_dark);
        return getString(R.string.settings_theme_system);
    }

    private void loadPrivacyValue() {
        repository.isPrivate().observe(this, resource -> {
            if (resource == null || resource.status != Resource.Status.SUCCESS || resource.data == null) return;
            privacyValue = resource.data
                    ? getString(R.string.settings_value_private)
                    : getString(R.string.settings_value_public);
            adapter.notifyDataSetChanged();
        });
    }

    private void loadArchiveCount() {
        repository.getArchive().observe(this, resource -> {
            if (resource == null || resource.status != Resource.Status.SUCCESS || resource.data == null) return;
            archiveValue = String.valueOf(resource.data.size());
            adapter.notifyDataSetChanged();
        });
    }

    private String valueFor(String key) {
        if ("account_privacy".equals(key)) return privacyValue;
        if ("theme".equals(key)) return themeValue;
        if ("close_friends".equals(key)) return getString(R.string.settings_value_zero);
        if ("verified".equals(key)) return getString(R.string.settings_value_not_subscribed);
        if ("scheduled".equals(key)) return scheduledValue;
        return "";
    }

    /* ------------------------------------------------------------------ */
    /* Row actions                                                        */
    /* ------------------------------------------------------------------ */

    private boolean isDecorative(String key) {
        return "your_activity".equals(key) || "notifications".equals(key) || "time_management".equals(key)
                || "close_friends".equals(key) || "insights".equals(key) || "verified".equals(key)
                || "scheduled".equals(key);
    }

    private void onRowClick(String key) {
        if (key == null) return;
        if (isDecorative(key)) {
            if (Constants.DECORATIVE_UI) ToastUtil.showNotAvailable(this);
            return;
        }
        switch (key) {
            case "accounts_center":
                startActivity(new Intent(this, AccountCenterActivity.class));
                break;
            case "saved":
                openSaved();
                break;
            case "archive":
                startActivity(new Intent(this, ArchiveActivity.class));
                break;
            case "account_privacy":
                startActivity(new Intent(this, AccountPrivacyActivity.class));
                break;
            case "theme":
                showThemeDialog();
                break;
            case "help":
                showTextDialog(getString(R.string.settings_help), getString(R.string.settings_help_body));
                break;
            case "about":
                showTextDialog(getString(R.string.settings_about),
                        getString(R.string.settings_about_body, getString(R.string.app_name), BuildConfig.VERSION_NAME));
                break;
            case "diagnostics":
                openDiagnostics();
                break;
            case "logout":
                showLogoutDialog();
                break;
            default:
                break;
        }
    }

    /** The Saved destination already exists in the main navigation graph. */
    private void openSaved() {
        Intent intent = new Intent(this, com.insta.clone.ui.main.MainActivity.class);
        intent.putExtra(com.insta.clone.ui.main.MainActivity.EXTRA_NAV_DESTINATION, R.id.navigation_shop);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    private void openDiagnostics() {
        try {
            Class<?> cls = Class.forName("com.insta.clone.ui.debug.SyncTestActivity");
            startActivity(new Intent(this, cls));
        } catch (ClassNotFoundException e) {
            Toast.makeText(this, "Diagnostics unavailable in this build", Toast.LENGTH_SHORT).show();
        }
    }

    private void showTextDialog(String title, String body) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(body)
                .setPositiveButton("OK", null)
                .show();
    }

    private void showThemeDialog() {
        final int[] modes = {
                AppCompatDelegate.MODE_NIGHT_NO,
                AppCompatDelegate.MODE_NIGHT_YES,
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM};
        String[] labels = {
                getString(R.string.settings_theme_light),
                getString(R.string.settings_theme_dark),
                getString(R.string.settings_theme_system)};

        int current = readThemeMode();
        int checked = 2;
        for (int i = 0; i < modes.length; i++) {
            if (modes[i] == current) checked = i;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_theme_dialog_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    int mode = modes[which];
                    getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
                            .edit().putInt(PREF_THEME, mode).apply();
                    AppCompatDelegate.setDefaultNightMode(mode);
                    themeValue = themeLabel(mode);
                    adapter.notifyDataSetChanged();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.settings_cancel, null)
                .show();
    }

    private void showLogoutDialog() {
        String username = sessionManager.getUsername() == null ? "" : sessionManager.getUsername();
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.settings_logout_confirm, username))
                .setPositiveButton(R.string.settings_logout, (dialog, which) -> {
                    // revoke the refresh token server-side, then clear the local session
                    repository.revokeSession().observe(this, resource ->
                            android.util.Log.d("InstaApi", "Token revoke: "
                                    + (resource != null && resource.status == Resource.Status.SUCCESS ? "ok" : "failed")));
                    sessionManager.clearSession();
                    Intent intent = new Intent(this, AuthActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton(R.string.settings_cancel, null)
                .show();
    }

    /* ------------------------------------------------------------------ */
    /* Adapter                                                            */
    /* ------------------------------------------------------------------ */

    private final class SettingsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private final List<Item> items = new ArrayList<>();

        void submit(List<Item> newItems) {
            items.clear();
            items.addAll(newItems);
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            Item item = items.get(position);
            switch (item.kind) {
                case HEADER: return 0;
                case GAP: return 2;
                default: return 1;
            }
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == 0) {
                return new HeaderHolder(ItemSettingsHeaderBinding.inflate(inflater, parent, false));
            }
            if (viewType == 2) {
                return new GapHolder(ItemSettingsGapBinding.inflate(inflater, parent, false));
            }
            return new RowHolder(ItemSettingsRowBinding.inflate(inflater, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Item item = items.get(position);
            if (holder instanceof HeaderHolder) {
                ((HeaderHolder) holder).binding.tvSection.setText(item.section);
                return;
            }
            if (holder instanceof GapHolder) return;

            RowHolder rowHolder = (RowHolder) holder;
            ItemSettingsRowBinding b = rowHolder.binding;

            b.tvLabel.setText(item.labelRes);
            if (item.iconRes != 0) {
                b.ivIcon.setImageResource(item.iconRes);
                b.ivIcon.setVisibility(View.VISIBLE);
            } else {
                b.ivIcon.setVisibility(View.GONE);
            }

            if (item.twoLine) {
                b.tvDescription.setText(R.string.settings_accounts_center_desc);
                b.tvDescription.setVisibility(View.VISIBLE);
                ViewGroup.LayoutParams lp = b.layoutSettingsRow.getLayoutParams();
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                b.layoutSettingsRow.setMinimumHeight(getResources().getDimensionPixelSize(R.dimen.settings_row_two_line_height));
                b.layoutSettingsRow.setLayoutParams(lp);
            } else {
                b.tvDescription.setVisibility(View.GONE);
                b.layoutSettingsRow.setMinimumHeight(0);
            }

            String value = valueFor(item.key);
            if (value != null && !value.isEmpty()) {
                b.tvValue.setText(value);
                b.tvValue.setVisibility(View.VISIBLE);
            } else {
                b.tvValue.setVisibility(View.GONE);
            }

            boolean isLogout = "logout".equals(item.key);
            b.ivChevron.setVisibility(isLogout ? View.GONE : View.VISIBLE);
            if (isLogout) {
                b.tvLabel.setTextAppearance(R.style.TextAppearance_Insta_SettingsLogout);
            } else {
                b.tvLabel.setTextAppearance(R.style.TextAppearance_Insta_SettingsLabel);
            }

            b.layoutSettingsRow.setOnClickListener(v -> onRowClick(item.key));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private static final class HeaderHolder extends RecyclerView.ViewHolder {
        final ItemSettingsHeaderBinding binding;
        HeaderHolder(ItemSettingsHeaderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    private static final class GapHolder extends RecyclerView.ViewHolder {
        final ItemSettingsGapBinding binding;
        GapHolder(ItemSettingsGapBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    private static final class RowHolder extends RecyclerView.ViewHolder {
        final ItemSettingsRowBinding binding;
        RowHolder(ItemSettingsRowBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
