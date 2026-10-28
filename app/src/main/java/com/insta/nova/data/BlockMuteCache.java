package com.insta.nova.data;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.insta.nova.data.remote.RetrofitClient;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Session cache of blocked ids and muted targets, loaded at start and refreshed after
 * every block/mute change. Every feed, tray, comment list, inbox and Nova list filters
 * through here so a blocked account disappears everywhere at once.
 */
public class BlockMuteCache {

    private static final String TAG = "BlockMuteCache";
    private static volatile BlockMuteCache instance;

    private final NovaApi api;
    private final MutableLiveData<Set<String>> blocked = new MutableLiveData<>(new HashSet<>());
    private final MutableLiveData<Set<String>> mutedPosts = new MutableLiveData<>(new HashSet<>());
    private final MutableLiveData<Set<String>> mutedStories = new MutableLiveData<>(new HashSet<>());
    private final Set<String> blockedSet = new HashSet<>();
    private final Set<String> mutedPostSet = new HashSet<>();
    private final Set<String> mutedStorySet = new HashSet<>();

    private BlockMuteCache(Context context) {
        api = RetrofitClient.getInstance(context.getApplicationContext()).getNovaApi();
    }

    public static synchronized BlockMuteCache get(Context context) {
        if (instance == null) instance = new BlockMuteCache(context);
        return instance;
    }

    public LiveData<Set<String>> blockedLive() { return blocked; }
    public LiveData<Set<String>> mutedPostsLive() { return mutedPosts; }
    public LiveData<Set<String>> mutedStoriesLive() { return mutedStories; }

    public boolean isBlocked(String userId) {
        return userId != null && blockedSet.contains(userId);
    }

    public boolean hidesPosts(String userId) {
        return isBlocked(userId) || (userId != null && mutedPostSet.contains(userId));
    }

    public boolean hidesStories(String userId) {
        return isBlocked(userId) || (userId != null && mutedStorySet.contains(userId));
    }

    public Set<String> blockedSnapshot() {
        return new HashSet<>(blockedSet);
    }

    /** Reloads both lists. Safe to call on every resume. */
    public void refresh() {
        Map<String, Object> body = new HashMap<>();
        api.blockedIds(body).enqueue(new Callback<List<String>>() {
            @Override
            public void onResponse(Call<List<String>> call, Response<List<String>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    blockedSet.clear();
                    blockedSet.addAll(response.body());
                    blocked.postValue(new HashSet<>(blockedSet));
                } else {
                    Log.w(TAG, "blocked ids HTTP " + response.code());
                }
            }

            @Override
            public void onFailure(Call<List<String>> call, Throwable t) {
                Log.w(TAG, "blocked ids failed: " + t.getMessage());
            }
        });
        api.mutedUsers(body).enqueue(new Callback<List<NovaDtos.MutedUser>>() {
            @Override
            public void onResponse(Call<List<NovaDtos.MutedUser>> call, Response<List<NovaDtos.MutedUser>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    mutedPostSet.clear();
                    mutedStorySet.clear();
                    for (NovaDtos.MutedUser m : response.body()) {
                        if (m == null || m.targetId == null) continue;
                        if (m.mutePosts) mutedPostSet.add(m.targetId);
                        if (m.muteStories) mutedStorySet.add(m.targetId);
                    }
                    mutedPosts.postValue(new HashSet<>(mutedPostSet));
                    mutedStories.postValue(new HashSet<>(mutedStorySet));
                } else {
                    Log.w(TAG, "muted users HTTP " + response.code());
                }
            }

            @Override
            public void onFailure(Call<List<NovaDtos.MutedUser>> call, Throwable t) {
                Log.w(TAG, "muted users failed: " + t.getMessage());
            }
        });
    }
}
