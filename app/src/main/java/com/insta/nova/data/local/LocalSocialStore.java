package com.insta.nova.data.local;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.insta.nova.data.model.Highlight;
import com.insta.nova.data.model.HighlightItem;
import com.insta.nova.data.model.Post;
import com.insta.nova.data.model.Story;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Local-first social state: posts, reels, stories, highlights and the per-post
 * interaction flags. SharedPreferences + Gson, scoped per signed-in account, the
 * same approach as {@link RecentSearchStore}.
 *
 * <p>Everything the Create sheet produces lands here so the feed, the profile and
 * the story tray keep working without a network round-trip.
 */
public class LocalSocialStore {

    private static final String PREF_NAME = "insta_local_social";
    private static final long STORY_TTL_MS = 24L * 60L * 60L * 1000L;
    private static final MutableLiveData<Integer> REVISION = new MutableLiveData<>(0);

    /** Minimal liker record for the likes sheet. */
    public static class Liker {
        public String id;
        public String username;
        public String name;
        public String avatarUrl;

        public Liker() {}

        public Liker(String id, String username, String name, String avatarUrl) {
            this.id = id;
            this.username = username;
            this.name = name;
            this.avatarUrl = avatarUrl;
        }
    }

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();
    private final String account;

    public LocalSocialStore(Context context) {
        Context app = context.getApplicationContext();
        prefs = app.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SessionManager session = SessionManager.getInstance(app);
        String id = session.getUserId();
        account = id == null || id.trim().isEmpty() ? "local" : id;
    }

    public static LiveData<Integer> revision() {
        return REVISION;
    }

    private void bump() {
        Integer v = REVISION.getValue();
        REVISION.setValue(v == null ? 1 : v + 1);
    }

    private String k(String suffix) {
        return suffix + "_" + account;
    }

    // ------------------------------------------------------------------
    // Posts / reels
    // ------------------------------------------------------------------

    public List<Post> getPosts() {
        return readList(k("posts"), new TypeToken<List<Post>>() {}.getType());
    }

    public void addPost(Post post) {
        if (post == null) return;
        List<Post> posts = getPosts();
        posts.add(0, post);
        write(k("posts"), posts);
        bump();
    }

    public List<Post> getReels() {
        return readList(k("reels"), new TypeToken<List<Post>>() {}.getType());
    }

    public void addReel(Post post) {
        if (post == null) return;
        List<Post> reels = getReels();
        reels.add(0, post);
        write(k("reels"), reels);
        bump();
    }

    // ------------------------------------------------------------------
    // Stories + highlights
    // ------------------------------------------------------------------

    public List<Story> getStories() {
        return readList(k("stories"), new TypeToken<List<Story>>() {}.getType());
    }

    public void addStory(Story story) {
        if (story == null) return;
        List<Story> all = getStories();
        all.add(story);
        write(k("stories"), all);
        bump();
    }

    public List<Story> getActiveStories() {
        List<Story> active = new ArrayList<>();
        long cutoff = System.currentTimeMillis() - STORY_TTL_MS;
        for (Story s : getStories()) {
            if (s != null && s.getCreatedAt() > cutoff) active.add(s);
        }
        return active;
    }

    /** Moves stories older than 24h into the archive used by highlight creation. */
    public void pruneExpiredStories() {
        List<Story> all = getStories();
        if (all.isEmpty()) return;
        long cutoff = System.currentTimeMillis() - STORY_TTL_MS;
        List<Story> active = new ArrayList<>();
        List<Story> archive = readList(k("story_archive"), new TypeToken<List<Story>>() {}.getType());
        boolean moved = false;
        for (Story s : all) {
            if (s == null) continue;
            if (s.getCreatedAt() > cutoff) {
                active.add(s);
            } else {
                archive.add(s);
                moved = true;
            }
        }
        if (moved) {
            write(k("stories"), active);
            write(k("story_archive"), archive);
            bump();
        }
    }

    /**
     * Stories a highlight can be built from. Instagram lets you highlight any story,
     * not only expired ones, so active stories are offered too and the "share a story
     * first" empty state only appears when there is genuinely nothing.
     */
    public List<Story> getPastStories() {
        List<Story> archive = readList(k("story_archive"), new TypeToken<List<Story>>() {}.getType());
        List<Story> all = new ArrayList<>(archive);
        for (Story s : getStories()) {
            if (s != null) all.add(s);
        }
        // De-duplicate: a story can sit in both lists right after a prune.
        List<Story> unique = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Story s : all) {
            String key = s.getId() != null ? s.getId() : s.getMediaUrl();
            if (key != null && !seen.add(key)) continue;
            unique.add(s);
        }
        Collections.sort(unique, (a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
        return unique;
    }

    public List<Highlight> getHighlights() {
        return readList(k("highlights"), new TypeToken<List<Highlight>>() {}.getType());
    }

    public void addHighlight(Highlight highlight) {
        if (highlight == null) return;
        List<Highlight> highlights = getHighlights();
        highlights.add(0, highlight);
        write(k("highlights"), highlights);
        bump();
    }

    public Highlight findHighlight(String id) {
        for (Highlight h : getHighlights()) {
            if (h != null && h.getId() != null && h.getId().equals(id)) return h;
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Interaction flags
    // ------------------------------------------------------------------

    public boolean isLiked(String postId) {
        return set(k("liked")).contains(postId);
    }

    public void setLiked(String postId, boolean liked) {
        Set<String> ids = set(k("liked"));
        if (liked) ids.add(postId); else ids.remove(postId);
        saveSet(k("liked"), ids);
        bump();
    }

    public boolean isSaved(String postId) {
        return set(k("saved")).contains(postId);
    }

    public void setSaved(String postId, boolean saved) {
        if (postId == null) return;
        Set<String> ids = set(k("saved"));
        Set<String> unsaved = set(k("unsaved"));
        if (saved) {
            ids.add(postId);
            unsaved.remove(postId);
        } else {
            ids.remove(postId);
            // Remembered so a server row that still lists the post (the DELETE may
            // not have landed yet) does not reappear in the Saved grid.
            unsaved.add(postId);
        }
        saveSet(k("saved"), ids);
        saveSet(k("unsaved"), unsaved);
        bump();
    }

    public Set<String> savedIds() {
        return set(k("saved"));
    }

    /** True once the user unsaved this post on this account. */
    public boolean isExplicitlyUnsaved(String postId) {
        return postId != null && set(k("unsaved")).contains(postId);
    }

    public boolean isReposted(String postId) {
        return set(k("reposted")).contains(postId);
    }

    public void setReposted(String postId, boolean reposted) {
        Set<String> ids = set(k("reposted"));
        if (reposted) ids.add(postId); else ids.remove(postId);
        saveSet(k("reposted"), ids);
        bump();
    }

    public int repostDelta(String postId) {
        return isReposted(postId) ? 1 : 0;
    }

    /** Every reposted post id, used to rebuild the "You reposted" rows after a refresh. */
    public Set<String> repostedIds() {
        return set(k("reposted"));
    }

    public boolean isFollowing(String userId) {
        return set(k("following")).contains(userId);
    }

    public void setFollowing(String userId, boolean following) {
        if (userId == null) return;
        Set<String> ids = set(k("following"));
        if (following) ids.add(userId); else ids.remove(userId);
        saveSet(k("following"), ids);
        bump();
    }

    // ------------------------------------------------------------------
    // Likers
    // ------------------------------------------------------------------

    /**
     * Returns the liker list for a post. Server data only carries a count, so a
     * stable demo roster is generated once and then persisted; the signed-in user
     * is added/removed by the like button.
     */
    public List<Liker> getLikers(String postId, int fallbackCount, String myId, String myUsername,
                                 String myAvatar, boolean includeMe) {
        Map<String, List<Liker>> map = readMap(k("likers"));
        List<Liker> likers = map.get(postId);
        if (likers == null) {
            likers = new ArrayList<>();
            int total = Math.max(0, fallbackCount);
            if (includeMe) total = Math.max(0, total - 1);
            String[][] roster = {
                    {"qaverify", "Qaverify"}, {"anna.makes", "Anna Rivera"},
                    {"theo.frames", "Theo Park"}, {"mocha_mike", "Mike Costa"},
                    {"sofia.gif", "Sofia Almeida"}, {"waffle.wizard", "Wes Ford"}
            };
            for (int i = 0; i < total && i < roster.length; i++) {
                likers.add(new Liker("demo-" + i, roster[i][0], roster[i][1], null));
            }
            map.put(postId, likers);
            writeMap(k("likers"), map);
        }
        List<Liker> result = new ArrayList<>(likers);
        result.removeIf(l -> l != null && myId != null && myId.equals(l.id));
        if (includeMe && myId != null) {
            result.add(0, new Liker(myId,
                    myUsername == null ? "you" : myUsername, myUsername, myAvatar));
        }
        return result;
    }

    // ------------------------------------------------------------------
    // JSON helpers
    // ------------------------------------------------------------------

    private <T> List<T> readList(String key, Type type) {
        String json = prefs.getString(key, null);
        if (json == null) return new ArrayList<>();
        try {
            List<T> list = gson.fromJson(json, type);
            return list == null ? new ArrayList<>() : list;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void write(String key, List<?> list) {
        prefs.edit().putString(key, gson.toJson(list)).apply();
    }

    private Set<String> set(String key) {
        List<String> list = readList(key, new TypeToken<List<String>>() {}.getType());
        return new HashSet<>(list);
    }

    private void saveSet(String key, Set<String> values) {
        write(key, new ArrayList<>(values));
    }

    private Map<String, List<Liker>> readMap(String key) {
        String json = prefs.getString(key, null);
        if (json == null) return new LinkedHashMap<>();
        try {
            Type type = new TypeToken<Map<String, List<Liker>>>() {}.getType();
            Map<String, List<Liker>> map = gson.fromJson(json, type);
            return map == null ? new LinkedHashMap<>() : map;
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private void writeMap(String key, Map<String, List<Liker>> map) {
        prefs.edit().putString(key, gson.toJson(map)).apply();
    }

    /** Converts a highlight's items into story-like entries for the viewer. */
    public List<Story> storiesForHighlight(Highlight highlight) {
        List<Story> stories = new ArrayList<>();
        if (highlight == null) return stories;
        // Highlight items only store a media URL, so the media type is resolved from
        // the original story; otherwise videos would be played as images.
        List<Story> source = getPastStories();
        for (HighlightItem item : highlight.getItems()) {
            if (item == null) continue;
            String mediaType = "image";
            long createdAt = System.currentTimeMillis();
            for (Story candidate : source) {
                if (candidate != null && item.getMediaUrl() != null
                        && item.getMediaUrl().equals(candidate.getMediaUrl())) {
                    mediaType = candidate.getMediaType();
                    createdAt = candidate.getCreatedAt();
                    break;
                }
            }
            stories.add(new Story(item.getId(), account, "", null,
                    item.getMediaUrl(), mediaType, true, createdAt));
        }
        return stories;
    }

    public List<Post> savedPosts(List<Post> candidates) {
        Set<String> ids = savedIds();
        List<Post> result = new ArrayList<>();
        if (candidates == null) return result;
        for (Post p : candidates) {
            if (p != null && ids.contains(p.getId())) result.add(p);
        }
        return result;
    }

    /** Newest first, used by merge helpers. */
    public static List<Post> newestFirst(List<Post> posts) {
        List<Post> copy = new ArrayList<>(posts);
        Collections.sort(copy, (a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
        return copy;
    }
}
