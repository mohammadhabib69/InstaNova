package com.insta.nova.ui.settings;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.os.LocaleListCompat;

import com.insta.nova.BuildConfig;
import com.insta.nova.R;
import com.insta.nova.data.local.AppSettingsStore;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.remote.ProfileColumns;
import com.insta.nova.data.remote.RetrofitClient;
import com.insta.nova.data.remote.api.RestApi;
import com.insta.nova.data.remote.dto.ProfileDto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Prompt N — the preference pages that are a small set of real controls.
 *
 * <p>One activity, one explicit page id: each branch reads and writes the same
 * {@link AppSettingsStore} key the owning feature reads, so nothing here is a
 * switch that only changes its own appearance.
 */
public class SimpleSettingsActivity extends SettingsPageActivity {

    private static final String EXTRA_PAGE = "extra_page";

    public static final String PAGE_NOTIFICATIONS = "notifications";
    public static final String PAGE_TAGS = "tags";
    public static final String PAGE_COMMENT_CONTROLS = "comment_controls";
    public static final String PAGE_REQUEST_CONTROLS = "request_controls";
    public static final String PAGE_MESSAGES = "messages";
    public static final String PAGE_CALLS = "calls";
    public static final String PAGE_PERMISSIONS = "permissions";
    public static final String PAGE_THEME = "theme";
    public static final String PAGE_LANGUAGE = "language";
    public static final String PAGE_DATA_SAVER = "data_saver";
    public static final String PAGE_AUTOPLAY = "autoplay";
    public static final String PAGE_UPLOAD_QUALITY = "upload_quality";
    public static final String PAGE_ACCESSIBILITY = "accessibility";
    public static final String PAGE_ACCOUNT_TYPE = "account_type";

    private AppSettingsStore settings;
    private RestApi restApi;
    private SessionManager session;

    public static Intent intent(Context context, String page) {
        return new Intent(context, SimpleSettingsActivity.class).putExtra(EXTRA_PAGE, page);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = new AppSettingsStore(this);
        session = SessionManager.getInstance(this);
        restApi = RetrofitClient.getInstance(this).getRestApi();

        String page = getIntent().getStringExtra(EXTRA_PAGE);
        if (page == null) page = PAGE_NOTIFICATIONS;
        switch (page) {
            case PAGE_NOTIFICATIONS: buildNotifications(); break;
            case PAGE_TAGS: buildTags(); break;
            case PAGE_COMMENT_CONTROLS: buildCommentControls(); break;
            case PAGE_REQUEST_CONTROLS: buildRequestControls(); break;
            case PAGE_MESSAGES: buildMessages(); break;
            case PAGE_CALLS: buildCalls(); break;
            case PAGE_PERMISSIONS: buildPermissions(); break;
            case PAGE_THEME: buildTheme(); break;
            case PAGE_LANGUAGE: buildLanguage(); break;
            case PAGE_DATA_SAVER: buildDataSaver(); break;
            case PAGE_AUTOPLAY: buildAutoplay(); break;
            case PAGE_UPLOAD_QUALITY: buildUploadQuality(); break;
            case PAGE_ACCESSIBILITY: buildAccessibility(); break;
            case PAGE_ACCOUNT_TYPE: buildAccountType(); break;
            default: buildNotifications(); break;
        }
    }

    /* ------------------------------------------------------------------ */
    /* Notifications                                                       */
    /* ------------------------------------------------------------------ */

    private void buildNotifications() {
        setPageTitle(R.string.st_notifications_title);

        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            addParagraph(R.string.st_notif_os_blocked);
            addButton(R.string.st_notif_os_open, R.color.color_primary, v -> openNotificationSettings());
            addDivider();
        }

        // The switches below follow the master, so they are collected first and the
        // master's listener updates them in place.
        //
        // This used to call recreate(). Rebuilding the page from inside the switch
        // callback re-dispatched the touch, so one tap toggled twice and the master
        // switch could never be turned off — logcat showed two "Schedule relaunch
        // activity" lines and push_master stayed true. Updating five enabled states is
        // all the rebuild was for, and it does not re-enter the touch pipeline.
        final java.util.List<com.google.android.material.switchmaterial.SwitchMaterial> dependents =
                new java.util.ArrayList<>();

        addSwitch(R.string.st_push_notifications, R.string.st_push_notifications_desc,
                settings.pushMaster(), (button, checked) -> {
                    settings.setPushMaster(checked);
                    for (com.google.android.material.switchmaterial.SwitchMaterial s : dependents) {
                        setDependentEnabled(s, checked);
                    }
                });
        addDivider();

        final boolean master = settings.pushMaster();
        dependents.add(addSwitch(R.string.st_notif_likes, 0, settings.pushLikes(),
                (b, c) -> settings.setPushLikes(c)));
        dependents.add(addSwitch(R.string.st_notif_follows, 0, settings.pushFollows(),
                (b, c) -> settings.setPushFollows(c)));
        dependents.add(addSwitch(R.string.st_notif_dms, 0, settings.pushDms(),
                (b, c) -> settings.setPushDms(c)));
        dependents.add(addSwitch(R.string.st_notif_stories, 0, settings.pushStories(),
                (b, c) -> settings.setPushStories(c)));
        dependents.add(addSwitch(R.string.st_notif_calls, 0, settings.pushCalls(),
                (b, c) -> settings.setPushCalls(c)));
        for (com.google.android.material.switchmaterial.SwitchMaterial s : dependents) {
            setDependentEnabled(s, master);
        }
    }

    /**
     * Greys a switch out as well as disabling it.
     *
     * <p>{@code styleSwitch} assigns explicit track and thumb tints, which ignore the
     * disabled state — so a disabled switch still rendered in full blue and looked
     * tappable while silently swallowing taps.
     */
    private void setDependentEnabled(
            com.google.android.material.switchmaterial.SwitchMaterial toggle, boolean enabled) {
        toggle.setEnabled(enabled);
        toggle.setAlpha(enabled ? 1f : 0.4f);
    }

    private void openNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        try {
            startActivity(intent);
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        }
    }

    /* ------------------------------------------------------------------ */
    /* Privacy                                                             */
    /* ------------------------------------------------------------------ */

    private void buildTags() {
        setPageTitle(R.string.st_tags_mentions_title);
        String[] labels = {getString(R.string.st_everyone), getString(R.string.st_people_you_follow),
                getString(R.string.st_no_one)};

        addRadioGroup(getString(R.string.st_who_can_tag), labels, indexOf(settings.tagWho()),
                (group, checkedId) -> {
                    String value = whoAt(group, checkedId);
                    settings.setTagWho(value);
                    // Mirrored to the account row: the tag path runs server-side, so a
                    // device-only value could never be enforced.
                    updateProfile("tag_who", value);
                });
        addDivider();
        addRadioGroup(getString(R.string.st_who_can_mention), labels, indexOf(settings.mentionWho()),
                (group, checkedId) -> {
                    String value = whoAt(group, checkedId);
                    settings.setMentionWho(value);
                    updateProfile("mention_who", value);
                });
        addParagraph(R.string.st_tags_note);
    }

    private void buildCommentControls() {
        setPageTitle(R.string.st_comment_controls_title);
        String[] labels = {getString(R.string.st_everyone), getString(R.string.st_people_you_follow),
                getString(R.string.st_followers)};
        addRadioGroup(getString(R.string.st_who_can_comment), labels, indexOf(settings.commentWho()),
                (group, checkedId) -> {
                    String value = whoAt(group, checkedId);
                    settings.setCommentWho(value);
                    updateProfile("comment_who", value);
                });
        addParagraph(R.string.st_comment_note);
    }

    private void buildRequestControls() {
        setPageTitle(R.string.st_request_controls_title);
        String[] labels = {getString(R.string.st_everyone), getString(R.string.st_people_i_follow),
                getString(R.string.st_no_one)};
        addRadioGroup(getString(R.string.st_who_can_request), labels, indexOf(settings.requestWho()),
                (group, checkedId) -> {
                    String value = whoAt(group, checkedId);
                    settings.setRequestWho(value);
                    // Read by tg_message_request_state, so this one decides whether a
                    // stranger's message arrives as a request or is declined.
                    updateProfile("request_who", value);
                });
        addParagraph(R.string.st_request_note);
    }

    /* ------------------------------------------------------------------ */
    /* Messages and calls                                                  */
    /* ------------------------------------------------------------------ */

    private void buildMessages() {
        setPageTitle(R.string.st_sec_messages);
        // One control only: read receipts and online status were two switches over the
        // same presence data, so the duplicate row is gone and this one covers both.
        addSwitch(R.string.st_online_status, R.string.st_online_status_desc,
                settings.showActivity(), (b, c) -> {
                    settings.setShowActivity(c);
                    updateProfile("show_activity", c);
                });
        addParagraph("When Online status is off, InstaNova stops writing your last-active time, so nobody sees \"Active now\" for you.");
    }

    private void buildCalls() {
        setPageTitle(R.string.st_call_prefs_title);
        String[] labels = {getString(R.string.st_everyone), getString(R.string.st_people_i_follow),
                getString(R.string.st_no_one)};
        addRadioGroup(getString(R.string.st_who_can_call), labels, indexOf(settings.whoCanCall()),
                (group, checkedId) -> {
                    String value = whoAt(group, checkedId);
                    settings.setWhoCanCall(value);
                    updateProfile("who_can_call", value);
                });
        addDivider();
        addSwitch(R.string.st_vibrate_calls, 0, settings.vibrateCalls(),
                (b, c) -> settings.setVibrateCalls(c));
    }

    private void buildPermissions() {
        setPageTitle(R.string.st_permissions_title);
        addParagraph(R.string.st_permissions_body);
        addButton(R.string.st_open_app_settings, R.color.color_primary, v -> {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        });
    }

    /* ------------------------------------------------------------------ */
    /* App preferences                                                     */
    /* ------------------------------------------------------------------ */

    private void buildTheme() {
        setPageTitle(R.string.st_theme_title);
        String[] labels = {getString(R.string.st_dark), getString(R.string.st_light),
                getString(R.string.st_system)};
        int[] modes = {AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.MODE_NIGHT_NO,
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM};
        int current = readThemeMode();
        int checked = 0;
        for (int i = 0; i < modes.length; i++) if (modes[i] == current) checked = i;

        RadioGroup[] holder = new RadioGroup[1];
        holder[0] = addRadioGroup(null, labels, checked, (group, checkedId) -> {
            int index = group.indexOfChild(group.findViewById(checkedId));
            if (index < 0 || index >= modes.length) return;
            getSharedPreferences("insta_settings", Context.MODE_PRIVATE)
                    .edit().putInt(SettingsActivity.PREF_THEME, modes[index]).apply();
            AppCompatDelegate.setDefaultNightMode(modes[index]);
        });
        addParagraph(R.string.st_theme_note);
    }

    private int readThemeMode() {
        SharedPreferences sp = getSharedPreferences("insta_settings", Context.MODE_PRIVATE);
        return sp.getInt(SettingsActivity.PREF_THEME, AppCompatDelegate.MODE_NIGHT_YES);
    }

    private void buildLanguage() {
        setPageTitle(R.string.st_language_title);
        String[] labels = {getString(R.string.st_english), getString(R.string.st_bangla)};
        String[] tags = {"en", "bn"};
        int checked = "bn".equals(settings.language()) ? 1 : 0;
        addRadioGroup(null, labels, checked, (group, checkedId) -> {
            int index = group.indexOfChild(group.findViewById(checkedId));
            if (index < 0 || index >= tags.length) return;
            settings.setLanguage(tags[index]);
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags[index]));
            Toast.makeText(this, R.string.st_language_saved, Toast.LENGTH_SHORT).show();
        });
        addParagraph(R.string.st_language_note);
    }

    private void buildDataSaver() {
        setPageTitle(R.string.st_data_saver);
        addSwitch(R.string.st_data_saver, R.string.st_data_saver_desc,
                settings.dataSaver(), (b, c) -> settings.setDataSaver(c));
    }

    private void buildAutoplay() {
        setPageTitle(R.string.st_autoplay_title);
        String[] labels = {getString(R.string.st_always), getString(R.string.st_wifi_only),
                getString(R.string.st_never)};
        String[] values = {AppSettingsStore.AUTOPLAY_ALWAYS, AppSettingsStore.AUTOPLAY_WIFI,
                AppSettingsStore.AUTOPLAY_NEVER};
        int checked = 1;
        for (int i = 0; i < values.length; i++) if (values[i].equals(settings.autoplay())) checked = i;
        addRadioGroup(null, labels, checked, (group, checkedId) -> {
            int index = group.indexOfChild(group.findViewById(checkedId));
            if (index >= 0 && index < values.length) settings.setAutoplay(values[index]);
        });
    }

    private void buildUploadQuality() {
        setPageTitle(R.string.st_upload_quality_title);
        String[] labels = {getString(R.string.st_standard), getString(R.string.st_high)};
        String[] values = {AppSettingsStore.QUALITY_STANDARD, AppSettingsStore.QUALITY_HIGH};
        int checked = AppSettingsStore.QUALITY_HIGH.equals(settings.uploadQuality()) ? 1 : 0;
        addRadioGroup(null, labels, checked, (group, checkedId) -> {
            int index = group.indexOfChild(group.findViewById(checkedId));
            if (index >= 0 && index < values.length) settings.setUploadQuality(values[index]);
        });
        addParagraph(R.string.st_upload_standard_desc);
        addParagraph(R.string.st_upload_high_desc);
    }

    private void buildAccessibility() {
        setPageTitle(R.string.st_accessibility_title);
        addSwitch(R.string.st_larger_text, R.string.st_larger_text_desc,
                settings.largerText(), (b, c) -> {
                    settings.setLargerText(c);
                    recreate();
                });
        addParagraph(R.string.st_accessibility_note);
    }

    /* ------------------------------------------------------------------ */
    /* Account type                                                        */
    /* ------------------------------------------------------------------ */

    private void buildAccountType() {
        setPageTitle(R.string.st_account_type_title);
        String[] labels = {getString(R.string.st_personal), getString(R.string.st_creator),
                getString(R.string.st_business)};
        String[] values = {"personal", "creator", "business"};
        String current = settings.getString("account_type", "personal");
        int checked = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(current)) checked = i;

        addRadioGroup(null, labels, checked, (group, checkedId) -> {
            int index = group.indexOfChild(group.findViewById(checkedId));
            if (index < 0 || index >= values.length) return;
            settings.setString("account_type", values[index]);
            updateProfile("account_type", values[index]);
        });
        addParagraph(R.string.st_account_type_note);
    }

    /* ------------------------------------------------------------------ */
    /* Helpers                                                             */
    /* ------------------------------------------------------------------ */

    /** Mapping order must match the labels passed to each group. */
    private int indexOf(String value) {
        if (AppSettingsStore.WHO_FOLLOWING.equals(value)) return 1;
        if (AppSettingsStore.WHO_FOLLOWERS.equals(value)) return 2;
        if (AppSettingsStore.WHO_NOBODY.equals(value)) return 2;
        return 0;
    }

    private String whoAt(RadioGroup group, int checkedId) {
        int index = group.indexOfChild(group.findViewById(checkedId));
        if (index == 1) return AppSettingsStore.WHO_FOLLOWING;
        if (index == 2) {
            // The third option differs per page: followers (comments) or nobody.
            return group.getChildCount() == 3 && isCommentGroup(group)
                    ? AppSettingsStore.WHO_FOLLOWERS : AppSettingsStore.WHO_NOBODY;
        }
        return AppSettingsStore.WHO_EVERYONE;
    }

    private boolean isCommentGroup(RadioGroup group) {
        // The comment page is the only third option worded "Followers".
        for (int i = 0; i < group.getChildCount(); i++) {
            android.widget.RadioButton radio = (android.widget.RadioButton) group.getChildAt(i);
            if (radio.getText() != null && radio.getText().toString()
                    .equals(getString(R.string.st_followers))) return true;
        }
        return false;
    }

    /** Mirrors a setting onto the caller's profiles row so other devices honour it. */
    private void updateProfile(String column, Object value) {
        String id = session.getUserId();
        if (id == null || id.trim().isEmpty()) return;
        Map<String, Object> body = new HashMap<>();
        body.put(column, value);
        restApi.updateProfile("eq." + id, ProfileColumns.WRITE_ECHO, body)
                .enqueue(new Callback<List<ProfileDto>>() {
                    @Override
                    public void onResponse(Call<List<ProfileDto>> call, Response<List<ProfileDto>> response) {
                        if (!response.isSuccessful()) {
                            Toast.makeText(SimpleSettingsActivity.this,
                                    "Saved on this device. Server sync failed (" + response.code() + ").",
                                    Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<List<ProfileDto>> call, Throwable t) {
                        Toast.makeText(SimpleSettingsActivity.this,
                                "Saved on this device. Server sync failed.",
                                Toast.LENGTH_LONG).show();
                    }
                });
    }
}
