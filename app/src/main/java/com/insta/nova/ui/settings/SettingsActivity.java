package com.insta.nova.ui.settings;

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
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.insta.nova.BuildConfig;
import com.insta.nova.R;
import com.insta.nova.data.local.AppSettingsStore;
import com.insta.nova.data.local.ChatSettingsStore;
import com.insta.nova.data.local.RelationshipStore;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.repository.ChatRepository;
import com.insta.nova.data.repository.RelationshipRepository;
import com.insta.nova.data.repository.SettingsRepository;
import com.insta.nova.databinding.ActivitySettingsBinding;
import com.insta.nova.databinding.ItemSettingsGapBinding;
import com.insta.nova.databinding.ItemSettingsHeaderBinding;
import com.insta.nova.databinding.ItemSettingsRowBinding;
import com.insta.nova.ui.auth.AuthActivity;
import com.insta.nova.ui.chat.ChatTheme;
import com.insta.nova.util.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Prompt N — Settings and activity.
 *
 * <p>One searchable list in the Instagram order: Your account, Notifications,
 * Privacy and safety, Messages and calls, App preferences, Your activity, Creator
 * tools, Help and about, Login. Values on the right are read live from the stores
 * (and from Supabase for account type/privacy), refreshed every time the page is
 * resumed.
 */
public class SettingsActivity extends AppCompatActivity {

    public static final String PREF_THEME = "app_theme_mode";
    private static final String PREF_FILE = "insta_settings";

    private enum Kind { HEADER, ROW, GAP }

    private static final int STYLE_NORMAL = 0;
    private static final int STYLE_BLUE = 1;
    private static final int STYLE_RED = 2;

    private static final class Item {
        final Kind kind;
        final String section;
        final String label;
        final int iconRes;
        final String key;
        final boolean twoLine;
        final String description;
        final int style;

        Item(Kind kind, String section, String label, int iconRes, String key,
             boolean twoLine, String description, int style) {
            this.kind = kind;
            this.section = section;
            this.label = label;
            this.iconRes = iconRes;
            this.key = key;
            this.twoLine = twoLine;
            this.description = description;
            this.style = style;
        }
    }

    private ActivitySettingsBinding binding;
    private SettingsRepository repository;
    private RelationshipRepository relationshipRepository;
    private SessionManager sessionManager;
    private AppSettingsStore settings;
    private RelationshipStore relationships;
    private SettingsAdapter adapter;

    private final List<Item> allItems = new ArrayList<>();
    private boolean privacyPrivate;
    private String accountType = "personal";
    private boolean primedRelationships;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = new SettingsRepository(this);
        relationshipRepository = new RelationshipRepository(this);
        sessionManager = SessionManager.getInstance(this);
        settings = new AppSettingsStore(this);
        relationships = new RelationshipStore(this);

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

        buildItems();
        adapter.submit(allItems);
        loadAccountState();
        primeRelationshipCounts();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAccountState();
        adapter.notifyDataSetChanged();
    }

    /* ------------------------------------------------------------------ */
    /* Items                                                               */
    /* ------------------------------------------------------------------ */

    private void buildItems() {
        allItems.clear();

        String account = getString(R.string.st_sec_account);
        String notifications = getString(R.string.st_sec_notifications);
        String privacy = getString(R.string.st_sec_privacy);
        String messages = getString(R.string.st_sec_messages);
        String app = getString(R.string.st_sec_app);
        String activity = getString(R.string.st_sec_activity);
        String creator = getString(R.string.st_sec_creator);
        String help = getString(R.string.st_sec_help);
        String login = getString(R.string.st_sec_login);

        // Your account
        header(account);
        allItems.add(new Item(Kind.ROW, account, getString(R.string.st_velkyn_account),
                R.drawable.ic_velkyn_v, "accounts_centre", true,
                getString(R.string.st_velkyn_account_desc), STYLE_NORMAL));
        gap();

        // Notifications
        header(notifications);
        row(notifications, R.string.st_notifications_title, R.drawable.ic_bell, "notifications", "");
        gap();

        // Privacy and safety
        header(privacy);
        row(privacy, R.string.st_blocked_accounts, R.drawable.ic_block, "blocked", "");
        row(privacy, R.string.st_restricted_accounts, R.drawable.ic_person_circle, "restricted", "");
        row(privacy, R.string.st_hidden_words, R.drawable.ic_comment, "hidden_words", "");
        row(privacy, R.string.st_story_audience, R.drawable.ic_eye_off, "story_audience", "");
        row(privacy, R.string.st_close_friends, R.drawable.ic_star_circle, "close_friends", "");
        row(privacy, R.string.st_tags_mentions, R.drawable.ic_tag_person, "tags_mentions", "");
        row(privacy, R.string.st_comment_controls, R.drawable.ic_comment, "comment_controls", "");
        row(privacy, R.string.st_message_request_controls, R.drawable.ic_send, "request_controls", "");
        gap();

        // Messages and calls
        header(messages);
        row(messages, R.string.st_chat_theme, R.drawable.ic_palette, "chat_theme", "");
        // Read receipts and Online status drove the same presence flag, so only the one
        // switch remains. Call history already lives inside DM, so it is not repeated here.
        row(messages, R.string.st_online_status, R.drawable.ic_activity, "online_status", "");
        row(messages, R.string.st_call_preferences, R.drawable.ic_phone, "call_prefs", "");
        row(messages, R.string.st_mic_camera, R.drawable.ic_mic, "permissions", "");
        gap();

        // App preferences
        header(app);
        row(app, R.string.st_theme, R.drawable.ic_moon, "theme", "");
        row(app, R.string.st_language, R.drawable.ic_internet, "language", "");
        row(app, R.string.st_data_saver, R.drawable.ic_download, "data_saver", "");
        row(app, R.string.st_video_autoplay, R.drawable.ic_play, "autoplay", "");
        row(app, R.string.st_upload_quality, R.drawable.ic_image, "upload_quality", "");
        row(app, R.string.st_cache, R.drawable.ic_archive, "cache", "");
        row(app, R.string.st_accessibility, R.drawable.ic_person_circle, "accessibility", "");
        gap();

        // Your activity
        header(activity);
        row(activity, R.string.st_saved_posts, R.drawable.ic_bookmark, "saved", "");
        row(activity, R.string.st_archive, R.drawable.ic_archive, "archive", "");
        row(activity, R.string.st_your_likes_comments, R.drawable.ic_favorite_outline, "likes_comments", "");
        row(activity, R.string.st_search_history, R.drawable.ic_search, "search_history", "");
        row(activity, R.string.st_download_data, R.drawable.ic_copy, "download_data", "");
        gap();

        // Creator
        header(creator);
        row(creator, R.string.st_account_type, R.drawable.ic_person_circle, "account_type", "");
        row(creator, R.string.st_insights, R.drawable.ic_insights, "insights", "");
        row(creator, R.string.st_audience, R.drawable.ic_audience, "audience", "");
        gap();

        // Help
        header(help);
        row(help, R.string.st_help_center, R.drawable.ic_help, "help", "");
        row(help, R.string.st_report_problem, R.drawable.ic_report, "report", "");
        row(help, R.string.st_contact_support, R.drawable.ic_mail, "contact", "");
        row(help, R.string.st_community_guidelines, R.drawable.ic_info, "guidelines", "");
        row(help, R.string.st_privacy_policy, R.drawable.ic_lock, "privacy_policy", "");
        row(help, R.string.st_terms, R.drawable.ic_info, "terms", "");
        row(help, R.string.st_about, R.drawable.ic_info, "about", "");
        gap();

        // Login
        header(login);
        allItems.add(new Item(Kind.ROW, login, getString(R.string.st_add_profile), 0,
                "add_profile", false, null, STYLE_BLUE));
        allItems.add(new Item(Kind.ROW, login, getString(R.string.st_log_out), 0,
                "logout", false, null, STYLE_RED));
    }

    private void header(String title) {
        allItems.add(new Item(Kind.HEADER, title, title, 0, null, false, null, STYLE_NORMAL));
    }

    private void gap() {
        allItems.add(new Item(Kind.GAP, "", "", 0, null, false, null, STYLE_NORMAL));
    }

    private void row(String section, int labelRes, int icon, String key, String value) {
        allItems.add(new Item(Kind.ROW, section, getString(labelRes), icon, key, false, null, STYLE_NORMAL));
    }

    /* ------------------------------------------------------------------ */
    /* Live values                                                         */
    /* ------------------------------------------------------------------ */

    private void loadAccountState() {
        repository.isPrivate().observe(this, resource -> {
            if (resource == null || resource.status != Resource.Status.SUCCESS || resource.data == null) return;
            privacyPrivate = resource.data;
            adapter.notifyDataSetChanged();
        });
        repository.getMyProfileRow().observe(this, resource -> {
            if (resource == null || resource.status != Resource.Status.SUCCESS || resource.data == null) return;
            Object type = resource.data.get("account_type");
            accountType = type == null ? "personal" : String.valueOf(type);
            Object priv = resource.data.get("is_private");
            if (priv instanceof Boolean) privacyPrivate = (Boolean) priv;
            adapter.notifyDataSetChanged();
        });
    }

    private void primeRelationshipCounts() {
        if (primedRelationships) return;
        primedRelationships = true;
        relationshipRepository.loadBlocked().observe(this, r -> {
            if (r != null && r.status == Resource.Status.SUCCESS) adapter.notifyDataSetChanged();
        });
        relationshipRepository.loadRestricted().observe(this, r -> {
            if (r != null && r.status == Resource.Status.SUCCESS) adapter.notifyDataSetChanged();
        });
        relationshipRepository.loadStoryHidden().observe(this, r -> {
            if (r != null && r.status == Resource.Status.SUCCESS) adapter.notifyDataSetChanged();
        });
    }

    private String valueFor(String key) {
        if (key == null) return "";
        switch (key) {
            case "blocked":
                return String.valueOf(relationships.blocked().size());
            case "restricted":
                return String.valueOf(relationships.restricted().size());
            case "story_audience":
                return getString(R.string.st_hidden_count, relationships.storyHidden().size());
            case "close_friends":
                return String.valueOf(relationships.closeFriends().size());
            case "tags_mentions":
                return whoLabel(settings.tagWho());
            case "comment_controls":
                return whoLabel(settings.commentWho());
            case "request_controls":
                return whoLabel(settings.requestWho());
            case "online_status":
                return onOff(settings.showActivity());
            case "call_prefs":
                return whoLabel(settings.whoCanCall());
            case "chat_theme":
                return ChatTheme.findById(settings.defaultChatTheme()).name;
            case "theme":
                return themeLabel(readThemeMode());
            case "language":
                return getString("bn".equals(settings.language())
                        ? R.string.st_bangla : R.string.st_english);
            case "data_saver":
                return onOff(settings.dataSaver());
            case "autoplay":
                return autoplayLabel(settings.autoplay());
            case "upload_quality":
                return getString(AppSettingsStore.QUALITY_HIGH.equals(settings.uploadQuality())
                        ? R.string.st_high : R.string.st_standard);
            case "account_type":
                return accountTypeLabel(accountType);
            case "about":
                return BuildConfig.VERSION_NAME;
            case "account_privacy":
                return getString(privacyPrivate ? R.string.st_private : R.string.st_public);
            default:
                return "";
        }
    }

    private String onOff(boolean value) {
        return getString(value ? R.string.st_on : R.string.st_off);
    }

    private String whoLabel(String who) {
        if (AppSettingsStore.WHO_FOLLOWING.equals(who)) return getString(R.string.st_people_i_follow);
        if (AppSettingsStore.WHO_FOLLOWERS.equals(who)) return getString(R.string.st_followers);
        if (AppSettingsStore.WHO_NOBODY.equals(who)) return getString(R.string.st_no_one);
        return getString(R.string.st_everyone);
    }

    private String autoplayLabel(String value) {
        if (AppSettingsStore.AUTOPLAY_ALWAYS.equals(value)) return getString(R.string.st_always);
        if (AppSettingsStore.AUTOPLAY_NEVER.equals(value)) return getString(R.string.st_never);
        return getString(R.string.st_wifi_only);
    }

    private String accountTypeLabel(String type) {
        if ("creator".equals(type)) return getString(R.string.st_creator);
        if ("business".equals(type)) return getString(R.string.st_business);
        return getString(R.string.st_personal);
    }

    private int readThemeMode() {
        SharedPreferences sp = getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
        return sp.getInt(PREF_THEME, AppCompatDelegate.MODE_NIGHT_YES);
    }

    private String themeLabel(int mode) {
        if (mode == AppCompatDelegate.MODE_NIGHT_NO) return getString(R.string.st_light);
        if (mode == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) return getString(R.string.st_system);
        return getString(R.string.st_dark);
    }

    /* ------------------------------------------------------------------ */
    /* Search                                                              */
    /* ------------------------------------------------------------------ */

    private void applyFilter(String rawQuery) {
        String query = rawQuery.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            binding.tvNoResults.setVisibility(View.GONE);
            binding.rvSettings.setVisibility(View.VISIBLE);
            adapter.submit(allItems);
            return;
        }

        // Flattened results: every matching row, no section headers.
        List<Item> filtered = new ArrayList<>();
        for (Item item : allItems) {
            if (item.kind != Kind.ROW) continue;
            String label = item.label == null ? "" : item.label.toLowerCase(Locale.ROOT);
            String value = valueFor(item.key) == null ? "" : valueFor(item.key).toLowerCase(Locale.ROOT);
            if (label.contains(query) || value.contains(query)) filtered.add(item);
        }

        binding.tvNoResults.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        binding.rvSettings.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
        adapter.submit(filtered);
    }

    /* ------------------------------------------------------------------ */
    /* Click routing                                                       */
    /* ------------------------------------------------------------------ */

    private void onRowClick(String key) {
        if (key == null) return;
        Intent intent;
        switch (key) {
            case "accounts_centre":
                startActivity(new Intent(this, AccountCenterActivity.class));
                break;
            case "notifications":
                startActivity(SimpleSettingsActivity.intent(this, SimpleSettingsActivity.PAGE_NOTIFICATIONS));
                break;
            case "blocked":
                startActivity(PeopleListActivity.intent(this, PeopleListActivity.MODE_BLOCKED,
                        getString(R.string.st_blocked_accounts)));
                break;
            case "restricted":
                startActivity(PeopleListActivity.intent(this, PeopleListActivity.MODE_RESTRICTED,
                        getString(R.string.st_restricted_accounts)));
                break;
            case "hidden_words":
                startActivity(new Intent(this, HiddenWordsActivity.class));
                break;
            case "story_audience":
                startActivity(PeopleListActivity.intent(this, PeopleListActivity.MODE_STORY_AUDIENCE,
                        getString(R.string.st_story_audience)));
                break;
            case "close_friends":
                startActivity(PeopleListActivity.intent(this, PeopleListActivity.MODE_CLOSE_FRIENDS,
                        getString(R.string.st_close_friends_title)));
                break;
            case "tags_mentions":
                startActivity(SimpleSettingsActivity.intent(this, SimpleSettingsActivity.PAGE_TAGS));
                break;
            case "comment_controls":
                startActivity(SimpleSettingsActivity.intent(this, SimpleSettingsActivity.PAGE_COMMENT_CONTROLS));
                break;
            case "request_controls":
                startActivity(SimpleSettingsActivity.intent(this, SimpleSettingsActivity.PAGE_REQUEST_CONTROLS));
                break;
            case "chat_theme":
                startActivity(new Intent(this, ChatThemeAppearanceActivity.class));
                break;
            case "online_status":
                startActivity(SimpleSettingsActivity.intent(this, SimpleSettingsActivity.PAGE_MESSAGES));
                break;
            case "call_prefs":
                startActivity(SimpleSettingsActivity.intent(this, SimpleSettingsActivity.PAGE_CALLS));
                break;
            case "permissions":
                startActivity(SimpleSettingsActivity.intent(this, SimpleSettingsActivity.PAGE_PERMISSIONS));
                break;
            case "theme":
                startActivity(SimpleSettingsActivity.intent(this, SimpleSettingsActivity.PAGE_THEME));
                break;
            case "language":
                startActivity(SimpleSettingsActivity.intent(this, SimpleSettingsActivity.PAGE_LANGUAGE));
                break;
            case "data_saver":
                startActivity(SimpleSettingsActivity.intent(this, SimpleSettingsActivity.PAGE_DATA_SAVER));
                break;
            case "autoplay":
                startActivity(SimpleSettingsActivity.intent(this, SimpleSettingsActivity.PAGE_AUTOPLAY));
                break;
            case "upload_quality":
                startActivity(SimpleSettingsActivity.intent(this, SimpleSettingsActivity.PAGE_UPLOAD_QUALITY));
                break;
            case "cache":
                startActivity(new Intent(this, CacheActivity.class));
                break;
            case "accessibility":
                startActivity(SimpleSettingsActivity.intent(this, SimpleSettingsActivity.PAGE_ACCESSIBILITY));
                break;
            case "saved":
                startActivity(new Intent(this, com.insta.nova.ui.saved.SavedActivity.class));
                break;
            case "archive":
                startActivity(new Intent(this, ArchiveActivity.class));
                break;
            case "likes_comments":
                startActivity(new Intent(this, YourActivityActivity.class));
                break;
            case "search_history":
                startActivity(new Intent(this, SearchHistoryActivity.class));
                break;
            case "download_data":
                startActivity(new Intent(this, DownloadDataActivity.class));
                break;
            case "account_type":
                startActivity(SimpleSettingsActivity.intent(this, SimpleSettingsActivity.PAGE_ACCOUNT_TYPE));
                break;
            case "insights":
                startActivity(new Intent(this, InsightsActivity.class));
                break;
            case "audience":
                startActivity(new Intent(this, AudienceAnalyticsActivity.class));
                break;
            case "help":
                startActivity(new Intent(this, HelpCenterActivity.class));
                break;
            case "report":
                startActivity(new Intent(this, ReportProblemActivity.class));
                break;
            case "contact":
                startActivity(LegalActivity.contactIntent(this));
                break;
            case "guidelines":
                startActivity(LegalActivity.docIntent(this, LegalActivity.DOC_GUIDELINES));
                break;
            case "privacy_policy":
                startActivity(LegalActivity.docIntent(this, LegalActivity.DOC_PRIVACY));
                break;
            case "terms":
                startActivity(LegalActivity.docIntent(this, LegalActivity.DOC_TERMS));
                break;
            case "about":
                startActivity(new Intent(this, AboutActivity.class));
                break;
            case "add_profile":
                addProfile();
                break;
            case "logout":
                showLogoutDialog();
                break;
            default:
                break;
        }
    }

    /** Adding a profile opens the login flow; the saved-accounts store remembers it. */
    private void addProfile() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(R.string.st_add_profile)
                .setMessage(R.string.st_add_profile_note)
                .setPositiveButton(R.string.st_add_profile, (d, w) -> {
                    FirebaseAuth.getInstance().signOut();
                    sessionManager.clearSession();
                    ChatRepository.getInstance(getApplicationContext()).resetForAccountSwitch();
                    Intent intent = new Intent(this, AuthActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton(R.string.st_cancel, null);
        builder.show();
    }

    private void showLogoutDialog() {
        String username = sessionManager.getUsername();
        new AlertDialog.Builder(this)
                .setTitle(R.string.st_log_out_confirm)
                .setMessage(getString(R.string.settings_logout_confirm, username))
                .setPositiveButton(R.string.st_log_out, (dialog, which) -> {
                    FirebaseAuth.getInstance().signOut();
                    sessionManager.clearSession();
                    ChatRepository.getInstance(getApplicationContext()).resetForAccountSwitch();
                    Intent intent = new Intent(this, AuthActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton(R.string.st_cancel, null)
                .show();
    }

    /* ------------------------------------------------------------------ */
    /* Adapter                                                             */
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
            if (item.kind == Kind.HEADER) return 0;
            if (item.kind == Kind.GAP) return 2;
            return 1;
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

            b.tvLabel.setText(item.label);
            if (item.iconRes != 0) {
                b.ivIcon.setImageResource(item.iconRes);
                b.ivIcon.setVisibility(View.VISIBLE);
            } else {
                b.ivIcon.setVisibility(View.GONE);
            }

            if (item.twoLine && item.description != null) {
                b.tvDescription.setText(item.description);
                b.tvDescription.setVisibility(View.VISIBLE);
                b.layoutSettingsRow.setMinimumHeight(getResources()
                        .getDimensionPixelSize(R.dimen.settings_row_two_line_height));
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

            boolean hideChevron = "logout".equals(item.key) || "add_profile".equals(item.key);
            b.ivChevron.setVisibility(hideChevron ? View.GONE : View.VISIBLE);

            int color = ContextCompat.getColor(SettingsActivity.this,
                    item.style == STYLE_RED ? R.color.color_destructive
                            : (item.style == STYLE_BLUE ? R.color.color_primary
                            : R.color.color_text_primary));
            b.tvLabel.setTextColor(color);
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
