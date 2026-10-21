package com.insta.nova.ui.chat;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import com.insta.nova.data.model.Highlight;
import com.insta.nova.data.remote.dto.PostDto;
import com.insta.nova.data.remote.dto.ProfileDto;
import com.insta.nova.data.remote.dto.StoryDto;
import com.insta.nova.data.repository.HighlightRepository;
import com.insta.nova.data.repository.NotificationCenter;
import com.insta.nova.data.repository.UserProfileRepository;
import com.insta.nova.util.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** PAGE 18 — state for another user's profile. */
public class UserProfileViewModel extends AndroidViewModel {

    public static class Ui {
        public ProfileDto profile;
        public boolean iFollow;
        public boolean followsMe;
        public boolean hasStory;
        public boolean unseenStory;
        public String firstUnseenStoryId;
        public final List<Highlight> highlights = new ArrayList<>();
        public final List<PostDto> posts = new ArrayList<>();
        public UserProfileRepository.FollowedBy followedBy;
        public final List<ProfileDto> suggestions = new ArrayList<>();
        public boolean loading = true;
        public boolean error;
        public boolean missing;
        public boolean loadingMore;
        public boolean hasMore = true;
        public int followersDelta;
        public String bioOverride;

        public int followers() {
            int base = profile != null ? profile.getFollowersCount() : 0;
            return Math.max(0, base + followersDelta);
        }
    }

    private final UserProfileRepository repository;
    private final HighlightRepository highlightRepository;
    private final MutableLiveData<Ui> state = new MutableLiveData<>(new Ui());
    private final MutableLiveData<String> toast = new MutableLiveData<>();
    private String userId;
    private boolean followBusy;
    private boolean initialized;

    public UserProfileViewModel(@NonNull Application application) {
        super(application);
        repository = new UserProfileRepository(application);
        highlightRepository = new HighlightRepository(application);
    }

    public LiveData<Ui> state() { return state; }
    public LiveData<String> toast() { return toast; }
    public String userId() { return userId; }

    public void init(String userId) {
        if (initialized && userId != null && userId.equals(this.userId)) {
            refresh();
            return;
        }
        this.userId = userId;
        this.initialized = true;
        load(true);
    }

    public void refresh() { load(false); }

    private void load(boolean skeleton) {
        Ui current = copy(state.getValue());
        current.loading = skeleton || current.profile == null;
        current.error = false;
        current.missing = false;
        current.hasMore = true;
        current.followersDelta = 0;
        state.setValue(current);

        once(repository.getProfile(userId), res -> {
            Ui ui = copy(state.getValue());
            ui.loading = false;
            if (res == null || res.status != Resource.Status.SUCCESS) {
                ui.error = ui.profile == null;
                state.setValue(ui);
                return;
            }
            if (res.data == null) {
                ui.missing = true;
                state.setValue(ui);
                return;
            }
            ui.profile = res.data;
            ui.error = false;
            state.setValue(ui);
        });

        once(repository.getRelationship(userId), res -> {
            if (res != null && res.status == Resource.Status.SUCCESS && res.data != null) {
                Ui ui = copy(state.getValue());
                ui.iFollow = res.data[0];
                ui.followsMe = res.data[1];
                state.setValue(ui);
            }
        });

        once(repository.getStories(userId), res -> {
            if (res != null && res.status == Resource.Status.SUCCESS && res.data != null) {
                Ui ui = copy(state.getValue());
                ui.hasStory = !res.data.isEmpty();
                ui.unseenStory = false;
                ui.firstUnseenStoryId = null;
                for (StoryDto s : res.data) {
                    if (!s.isSeen()) {
                        ui.unseenStory = true;
                        if (ui.firstUnseenStoryId == null) ui.firstUnseenStoryId = s.getId();
                    }
                }
                state.setValue(ui);
            }
        });

        once(highlightRepository.getUserHighlights(userId), res -> {
            if (res != null && res.status == Resource.Status.SUCCESS && res.data != null) {
                Ui ui = copy(state.getValue());
                ui.highlights.clear();
                ui.highlights.addAll(res.data);
                state.setValue(ui);
            }
        });

        once(repository.getFollowedBy(userId), res -> {
            if (res != null && res.status == Resource.Status.SUCCESS && res.data != null) {
                Ui ui = copy(state.getValue());
                ui.followedBy = res.data;
                state.setValue(ui);
            }
        });

        once(repository.getSuggestions(12), res -> {
            if (res != null && res.status == Resource.Status.SUCCESS && res.data != null) {
                Ui ui = copy(state.getValue());
                ui.suggestions.clear();
                for (ProfileDto p : res.data) {
                    if (!userId.equals(p.getId())) ui.suggestions.add(p);
                }
                state.setValue(ui);
            }
        });

        loadFirstPage();
    }

    private void loadFirstPage() {
        once(repository.getPosts(userId, 0), res -> {
            if (res != null && res.status == Resource.Status.SUCCESS && res.data != null) {
                Ui ui = copy(state.getValue());
                ui.posts.clear();
                ui.posts.addAll(res.data);
                ui.hasMore = res.data.size() >= UserProfileRepository.PAGE_SIZE;
                ui.loading = false;
                state.setValue(ui);
            }
        });
    }

    public void loadMore() {
        Ui ui = state.getValue();
        if (ui == null || ui.loadingMore || !ui.hasMore || ui.posts.isEmpty()) return;
        Ui next = copy(ui);
        next.loadingMore = true;
        state.setValue(next);
        once(repository.getPosts(userId, ui.posts.size()), res -> {
            Ui out = copy(state.getValue());
            out.loadingMore = false;
            if (res != null && res.status == Resource.Status.SUCCESS && res.data != null) {
                out.posts.addAll(res.data);
                out.hasMore = res.data.size() >= UserProfileRepository.PAGE_SIZE;
            } else {
                out.hasMore = false;
            }
            state.setValue(out);
        });
    }

    /** Optimistic follow; rolls back and refetches on failure. */
    public void setFollowing(boolean follow) {
        if (followBusy) return;
        followBusy = true;
        Ui optimistic = copy(state.getValue());
        optimistic.iFollow = follow;
        optimistic.followersDelta += follow ? 1 : -1;
        state.setValue(optimistic);

        LiveData<Resource<Boolean>> call = follow ? repository.follow(userId) : repository.unfollow(userId);
        once(call, res -> {
            followBusy = false;
            boolean ok = res != null && res.status == Resource.Status.SUCCESS;
            Ui out = copy(state.getValue());
            if (!ok) {
                out.iFollow = !follow;
                out.followersDelta += follow ? -1 : 1;
                state.setValue(out);
                toast.setValue("Couldn't update follow");
                return;
            }
            if (follow && out.profile != null) {
                // Demo audience: a follow is answered by the other account.
                NotificationCenter.get(getApplication()).onFollowed(
                        out.profile.getId(), out.profile.getUsername(), out.profile.getAvatarUrl());
            }
            state.setValue(out);
            once(repository.getProfile(userId), r2 -> {
                if (r2 != null && r2.status == Resource.Status.SUCCESS && r2.data != null) {
                    Ui fresh = copy(state.getValue());
                    fresh.profile = r2.data;
                    fresh.followersDelta = 0;
                    state.setValue(fresh);
                }
            });
        });
    }

    public void followSuggestion(ProfileDto profile) {
        once(repository.follow(profile.getId()), res -> {
            if (res != null && res.status == Resource.Status.SUCCESS) {
                Ui ui = copy(state.getValue());
                ui.suggestions.remove(profile);
                state.setValue(ui);
            } else {
                toast.setValue("Couldn't follow");
            }
        });
    }

    public void dismissSuggestion(ProfileDto profile) {
        Ui ui = copy(state.getValue());
        ui.suggestions.remove(profile);
        state.setValue(ui);
    }

    public void setBioExpanded() {
        Ui ui = copy(state.getValue());
        ui.bioOverride = "expanded";
        state.setValue(ui);
    }

    private Ui copy(Ui in) {
        Ui ui = new Ui();
        if (in == null) return ui;
        ui.profile = in.profile;
        ui.iFollow = in.iFollow;
        ui.followsMe = in.followsMe;
        ui.hasStory = in.hasStory;
        ui.unseenStory = in.unseenStory;
        ui.firstUnseenStoryId = in.firstUnseenStoryId;
        ui.highlights.addAll(in.highlights);
        ui.posts.addAll(in.posts);
        ui.followedBy = in.followedBy;
        ui.suggestions.addAll(in.suggestions);
        ui.loading = in.loading;
        ui.error = in.error;
        ui.missing = in.missing;
        ui.loadingMore = in.loadingMore;
        ui.hasMore = in.hasMore;
        ui.followersDelta = in.followersDelta;
        ui.bioOverride = in.bioOverride;
        return ui;
    }

    private <T> void once(LiveData<Resource<T>> live, Consumer<Resource<T>> cb) {
        if (live == null) return;
        live.observeForever(new Observer<Resource<T>>() {
            @Override public void onChanged(Resource<T> resource) {
                if (resource == null || resource.status == Resource.Status.LOADING) return;
                live.removeObserver(this);
                cb.accept(resource);
            }
        });
    }
}
