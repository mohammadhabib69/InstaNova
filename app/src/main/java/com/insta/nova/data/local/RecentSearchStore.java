package com.insta.nova.data.local;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores recent search queries and profiles.
 */
public class RecentSearchStore {
    private static final String PREF_NAME = "insta_search_prefs";
    private static final String KEY_RECENT_QUERIES = "recent_queries";
    private static final int MAX_RECENTS = 15;

    private final SharedPreferences prefs;
    private final Gson gson;

    public RecentSearchStore(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
    }

    public List<String> getRecentSearches() {
        String json = prefs.getString(KEY_RECENT_QUERIES, null);
        if (json == null) return new ArrayList<>();
        Type type = new TypeToken<List<String>>() {}.getType();
        return gson.fromJson(json, type);
    }

    public void addSearch(String query) {
        if (query == null || query.trim().isEmpty()) return;
        List<String> list = getRecentSearches();
        list.remove(query);
        list.add(0, query);
        if (list.size() > MAX_RECENTS) {
            list = list.subList(0, MAX_RECENTS);
        }
        prefs.edit().putString(KEY_RECENT_QUERIES, gson.toJson(list)).apply();
    }

    public void removeSearch(String query) {
        List<String> list = getRecentSearches();
        list.remove(query);
        prefs.edit().putString(KEY_RECENT_QUERIES, gson.toJson(list)).apply();
    }

    public void clearAll() {
        prefs.edit().remove(KEY_RECENT_QUERIES).apply();
    }
}
