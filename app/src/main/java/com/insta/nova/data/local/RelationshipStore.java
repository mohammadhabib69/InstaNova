package com.insta.nova.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/**
 * Fast, synchronous mirror of the relationship lists used by Settings.
 *
 * <p>The authoritative copies live in Supabase ({@code blocks},
 * {@code restricted_users}, {@code story_audience}); this cache exists because the
 * inbox and profile screens must decide synchronously whether a user is blocked or
 * restricted while rendering. Every write goes to both places.
 *
 * <p>Close Friends has no server table in this project, so it is device-local and
 * namespaced per profile.
 */
public class RelationshipStore {

    private static final String PREF_NAME = "insta_relationships";

    private static final String BLOCKED = "blocked";
    private static final String RESTRICTED = "restricted";
    private static final String CLOSE_FRIENDS = "close_friends";
    private static final String STORY_HIDDEN = "story_hidden";

    private final SharedPreferences prefs;
    private final String account;

    public RelationshipStore(Context context) {
        Context app = context.getApplicationContext();
        this.prefs = app.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String id = SessionManager.getInstance(app).getUserId();
        this.account = id == null || id.trim().isEmpty() ? "local" : id;
    }

    private String key(String suffix) {
        return suffix + "_" + account;
    }

    private Set<String> read(String suffix) {
        return new HashSet<>(prefs.getStringSet(key(suffix), new HashSet<>()));
    }

    private void write(String suffix, Set<String> values) {
        prefs.edit().putStringSet(key(suffix), new HashSet<>(values)).apply();
    }

    /* ------------------------------------------------------------------ */
    /* Blocked                                                             */
    /* ------------------------------------------------------------------ */

    public Set<String> blocked() { return read(BLOCKED); }

    public boolean isBlocked(String userId) {
        return userId != null && blocked().contains(userId);
    }

    public void setBlocked(String userId, boolean blocked) {
        if (userId == null || userId.trim().isEmpty()) return;
        Set<String> set = blocked();
        if (blocked) set.add(userId); else set.remove(userId);
        write(BLOCKED, set);
    }

    public void replaceBlocked(Set<String> ids) {
        write(BLOCKED, ids == null ? new HashSet<>() : ids);
    }

    /* ------------------------------------------------------------------ */
    /* Restricted                                                          */
    /* ------------------------------------------------------------------ */

    public Set<String> restricted() { return read(RESTRICTED); }

    public boolean isRestricted(String userId) {
        return userId != null && restricted().contains(userId);
    }

    public void setRestricted(String userId, boolean restricted) {
        if (userId == null || userId.trim().isEmpty()) return;
        Set<String> set = restricted();
        if (restricted) set.add(userId); else set.remove(userId);
        write(RESTRICTED, set);
    }

    public void replaceRestricted(Set<String> ids) {
        write(RESTRICTED, ids == null ? new HashSet<>() : ids);
    }

    /* ------------------------------------------------------------------ */
    /* Close friends                                                       */
    /* ------------------------------------------------------------------ */

    public Set<String> closeFriends() { return read(CLOSE_FRIENDS); }

    public boolean isCloseFriend(String userId) {
        return userId != null && closeFriends().contains(userId);
    }

    public void replaceCloseFriends(Set<String> ids) {
        write(CLOSE_FRIENDS, ids == null ? new HashSet<>() : ids);
    }

    public void setCloseFriend(String userId, boolean closeFriend) {
        if (userId == null || userId.trim().isEmpty()) return;
        Set<String> set = closeFriends();
        if (closeFriend) set.add(userId); else set.remove(userId);
        write(CLOSE_FRIENDS, set);
    }

    /* ------------------------------------------------------------------ */
    /* Story audience                                                      */
    /* ------------------------------------------------------------------ */

    public Set<String> storyHidden() { return read(STORY_HIDDEN); }

    public void setStoryHidden(String userId, boolean hidden) {
        if (userId == null || userId.trim().isEmpty()) return;
        Set<String> set = storyHidden();
        if (hidden) set.add(userId); else set.remove(userId);
        write(STORY_HIDDEN, set);
    }

    public void replaceStoryHidden(Set<String> ids) {
        write(STORY_HIDDEN, ids == null ? new HashSet<>() : ids);
    }
}
