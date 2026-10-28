package com.insta.nova.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Prompt N — persisted Settings state.
 *
 * <p>One SharedPreferences file, namespaced per signed-in profile so two accounts on
 * the same device never share privacy, notification or appearance choices. Toggles
 * are read by the feature that owns the behaviour (notifications feed, chat
 * receipts/presence, comments, calls, uploader), so a switch is never cosmetic.
 */
public class AppSettingsStore {

    private static final String PREF_NAME = "insta_app_settings";
    private static final MutableLiveData<Integer> REVISION = new MutableLiveData<>(0);

    /* Notification categories */
    public static final String KEY_PUSH_MASTER = "push_master";
    public static final String KEY_PUSH_LIKES = "push_likes_comments";
    public static final String KEY_PUSH_FOLLOWS = "push_follows";
    public static final String KEY_PUSH_DMS = "push_dms";
    public static final String KEY_PUSH_STORIES = "push_stories";
    public static final String KEY_PUSH_CALLS = "push_calls";

    /* Messages and calls */
    public static final String KEY_READ_RECEIPTS = "read_receipts";
    public static final String KEY_SHOW_ACTIVITY = "show_activity";
    public static final String KEY_VIBRATE_CALLS = "vibrate_calls";
    public static final String KEY_WHO_CAN_CALL = "who_can_call";
    public static final String KEY_DEFAULT_THEME = "default_chat_theme";

    /* Privacy */
    public static final String KEY_HIDDEN_OFFENSIVE = "hidden_offensive";
    public static final String KEY_TAG_WHO = "tag_who";
    public static final String KEY_MENTION_WHO = "mention_who";
    public static final String KEY_COMMENT_WHO = "comment_who";
    public static final String KEY_REQUEST_WHO = "request_who";

    /* App preferences */
    public static final String KEY_DATA_SAVER = "data_saver";
    public static final String KEY_AUTOPLAY = "video_autoplay";
    public static final String KEY_UPLOAD_QUALITY = "upload_quality";
    public static final String KEY_CAPTIONS = "captions";
    public static final String KEY_LARGER_TEXT = "larger_text";
    public static final String KEY_LANGUAGE = "language";

    /* Values */
    public static final String WHO_EVERYONE = "everyone";
    public static final String WHO_FOLLOWING = "following";
    public static final String WHO_FOLLOWERS = "followers";
    public static final String WHO_NOBODY = "nobody";

    public static final String AUTOPLAY_ALWAYS = "always";
    public static final String AUTOPLAY_WIFI = "wifi";
    public static final String AUTOPLAY_NEVER = "never";

    public static final String QUALITY_HIGH = "high";
    public static final String QUALITY_STANDARD = "standard";

    private static final String HIDDEN_WORDS = "hidden_words";

    private final SharedPreferences prefs;
    private final String account;

    public AppSettingsStore(Context context) {
        Context app = context.getApplicationContext();
        this.prefs = app.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String id = SessionManager.getInstance(app).getUserId();
        this.account = id == null || id.trim().isEmpty() ? "local" : id;
    }

    /** Process-wide change signal so Settings values refresh after returning. */
    public static LiveData<Integer> revision() {
        return REVISION;
    }

    private static void bump() {
        Integer value = REVISION.getValue();
        REVISION.setValue(value == null ? 1 : value + 1);
    }

    private String key(String suffix) {
        return suffix + "_" + account;
    }

    /* ------------------------------------------------------------------ */
    /* Primitives                                                          */
    /* ------------------------------------------------------------------ */

    public boolean getBool(String key, boolean fallback) {
        return prefs.getBoolean(key(key), fallback);
    }

    public void setBool(String key, boolean value) {
        prefs.edit().putBoolean(key(key), value).apply();
        bump();
    }

    public String getString(String key, String fallback) {
        String value = prefs.getString(key(key), null);
        return value == null ? fallback : value;
    }

    public void setString(String key, String value) {
        prefs.edit().putString(key(key), value == null ? "" : value).apply();
        bump();
    }

    public Set<String> getSet(String key) {
        return new HashSet<>(prefs.getStringSet(key(key), new HashSet<>()));
    }

    public void setSet(String key, Set<String> values) {
        prefs.edit().putStringSet(key(key), new HashSet<>(values)).apply();
        bump();
    }

    /* ------------------------------------------------------------------ */
    /* Notifications                                                       */
    /* ------------------------------------------------------------------ */

    public boolean pushMaster() { return getBool(KEY_PUSH_MASTER, true); }
    public void setPushMaster(boolean value) { setBool(KEY_PUSH_MASTER, value); }

    public boolean pushLikes() { return getBool(KEY_PUSH_LIKES, true); }
    public void setPushLikes(boolean value) { setBool(KEY_PUSH_LIKES, value); }

    public boolean pushFollows() { return getBool(KEY_PUSH_FOLLOWS, true); }
    public void setPushFollows(boolean value) { setBool(KEY_PUSH_FOLLOWS, value); }

    public boolean pushDms() { return getBool(KEY_PUSH_DMS, true); }
    public void setPushDms(boolean value) { setBool(KEY_PUSH_DMS, value); }

    public boolean pushStories() { return getBool(KEY_PUSH_STORIES, true); }
    public void setPushStories(boolean value) { setBool(KEY_PUSH_STORIES, value); }

    public boolean pushCalls() { return getBool(KEY_PUSH_CALLS, true); }
    public void setPushCalls(boolean value) { setBool(KEY_PUSH_CALLS, value); }

    /**
     * Category gate used by the in-app feed and the FCM service.
     * Unknown types stay visible so a new server type is never silently dropped.
     */
    public boolean allowsNotificationType(String type) {
        if (!pushMaster()) return false;
        String t = type == null ? "" : type.toLowerCase();
        if (t.contains("like")) return pushLikes();
        if (t.contains("comment") || t.contains("mention") || t.contains("tag")) return pushLikes();
        if (t.contains("follow")) return pushFollows();
        if (t.contains("message") || t.contains("dm")) return pushDms();
        if (t.contains("story")) return pushStories();
        if (t.contains("call")) return pushCalls();
        return true;
    }

    /* ------------------------------------------------------------------ */
    /* Messages and calls                                                  */
    /* ------------------------------------------------------------------ */

    /**
     * One switch, one meaning: read receipts and online status both decide whether the
     * other side can see that you were here, so they share the Online status value.
     * The key is kept so an older build's saved choice still reads back sanely.
     */
    public boolean readReceipts() {
        if (prefs.contains(key(KEY_SHOW_ACTIVITY))) return showActivity();
        return getBool(KEY_READ_RECEIPTS, true);
    }
    public void setReadReceipts(boolean value) { setBool(KEY_READ_RECEIPTS, value); }

    public boolean showActivity() { return getBool(KEY_SHOW_ACTIVITY, true); }
    public void setShowActivity(boolean value) { setBool(KEY_SHOW_ACTIVITY, value); }

    public boolean vibrateCalls() { return getBool(KEY_VIBRATE_CALLS, true); }
    public void setVibrateCalls(boolean value) { setBool(KEY_VIBRATE_CALLS, value); }

    public String whoCanCall() { return getString(KEY_WHO_CAN_CALL, WHO_EVERYONE); }
    public void setWhoCanCall(String value) { setString(KEY_WHO_CAN_CALL, value); }

    public String defaultChatTheme() { return getString(KEY_DEFAULT_THEME, "default"); }
    public void setDefaultChatTheme(String value) { setString(KEY_DEFAULT_THEME, value); }

    /* ------------------------------------------------------------------ */
    /* Privacy                                                             */
    /* ------------------------------------------------------------------ */

    public boolean hideOffensiveComments() { return getBool(KEY_HIDDEN_OFFENSIVE, true); }
    public void setHideOffensiveComments(boolean value) { setBool(KEY_HIDDEN_OFFENSIVE, value); }

    public List<String> hiddenWords() {
        String raw = prefs.getString(key(HIDDEN_WORDS), "");
        List<String> words = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) return words;
        for (String part : raw.split("\\n")) {
            String word = part.trim();
            if (!word.isEmpty()) words.add(word);
        }
        return words;
    }

    public void setHiddenWords(List<String> words) {
        List<String> clean = new ArrayList<>();
        if (words != null) {
            for (String word : words) {
                if (word != null && !word.trim().isEmpty()) clean.add(word.trim().toLowerCase());
            }
        }
        prefs.edit().putString(key(HIDDEN_WORDS), android.text.TextUtils.join("\n", clean)).apply();
        bump();
    }

    public void addHiddenWord(String word) {
        if (word == null || word.trim().isEmpty()) return;
        List<String> words = hiddenWords();
        String clean = word.trim().toLowerCase();
        for (String existing : words) {
            if (existing.equalsIgnoreCase(clean)) return;
        }
        words.add(clean);
        setHiddenWords(words);
    }

    public void removeHiddenWord(String word) {
        List<String> words = hiddenWords();
        words.remove(word);
        setHiddenWords(words);
    }

    /** True when the comment must be hidden for this account. */
    public boolean isCommentHidden(String body) {
        if (body == null || body.trim().isEmpty()) return false;
        String lower = body.toLowerCase();
        for (String word : hiddenWords()) {
            if (!word.isEmpty() && lower.contains(word)) return true;
        }
        return false;
    }

    public String tagWho() { return getString(KEY_TAG_WHO, WHO_EVERYONE); }
    public void setTagWho(String value) { setString(KEY_TAG_WHO, value); }

    public String mentionWho() { return getString(KEY_MENTION_WHO, WHO_EVERYONE); }
    public void setMentionWho(String value) { setString(KEY_MENTION_WHO, value); }

    public String commentWho() { return getString(KEY_COMMENT_WHO, WHO_EVERYONE); }
    public void setCommentWho(String value) { setString(KEY_COMMENT_WHO, value); }

    public String requestWho() { return getString(KEY_REQUEST_WHO, WHO_EVERYONE); }
    public void setRequestWho(String value) { setString(KEY_REQUEST_WHO, value); }

    /* ------------------------------------------------------------------ */
    /* App preferences                                                     */
    /* ------------------------------------------------------------------ */

    public boolean dataSaver() { return getBool(KEY_DATA_SAVER, false); }
    public void setDataSaver(boolean value) { setBool(KEY_DATA_SAVER, value); }

    public String autoplay() { return getString(KEY_AUTOPLAY, AUTOPLAY_WIFI); }
    public void setAutoplay(String value) { setString(KEY_AUTOPLAY, value); }

    public String uploadQuality() { return getString(KEY_UPLOAD_QUALITY, QUALITY_STANDARD); }
    public void setUploadQuality(String value) { setString(KEY_UPLOAD_QUALITY, value); }

    public boolean captions() { return getBool(KEY_CAPTIONS, false); }
    public void setCaptions(boolean value) { setBool(KEY_CAPTIONS, value); }

    public boolean largerText() { return getBool(KEY_LARGER_TEXT, false); }
    public void setLargerText(boolean value) { setBool(KEY_LARGER_TEXT, value); }

    public String language() { return getString(KEY_LANGUAGE, "en"); }
    public void setLanguage(String value) { setString(KEY_LANGUAGE, value); }

    public void clearForAccount() {
        SharedPreferences.Editor editor = prefs.edit();
        for (String key : prefs.getAll().keySet()) {
            if (key.endsWith("_" + account)) editor.remove(key);
        }
        editor.apply();
        bump();
    }
}
