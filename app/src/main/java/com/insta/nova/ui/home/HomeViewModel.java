package com.insta.nova.ui.home;

import android.app.Application;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import com.insta.nova.data.local.CommentStore;
import com.insta.nova.data.local.LocalSocialStore;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.model.Post;
import com.insta.nova.data.model.Story;
import com.insta.nova.data.model.Profile;
import com.insta.nova.data.model.StoryGroup;
import com.insta.nova.data.repository.FollowRepository;
import com.insta.nova.data.repository.PostRepository;
import com.insta.nova.data.repository.ProfileRepository;
import com.insta.nova.data.repository.StoryRepository;
import com.insta.nova.util.Constants;
import com.insta.nova.util.Resource;
import com.insta.nova.util.TimeUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;

/**
 * ViewModel managing Home Feed state, story groups, keyset pagination, and optimistic updates.
 */
public class HomeViewModel extends AndroidViewModel {

    private static final String TAG = "HomeViewModel";
    /** Row id prefix for the local "You reposted" copy of a post. */
    static final String REPOST_ID_PREFIX = "repost_";
    /** Location line shown on a reposted row. */
    static final String REPOST_LOCATION = "You reposted";

    private final PostRepository postRepository;
    private final StoryRepository storyRepository;
    private final FollowRepository followRepository;
    private final ProfileRepository profileRepository;
    private final SessionManager sessionManager;
    private final CommentStore commentStore;
    private final LocalSocialStore localStore;
    private boolean ownerProfileStarted;

    private final MutableLiveData<List<Post>> postsLiveData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> isRefreshingLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isLoadingMoreLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isAllCaughtUpLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<List<StoryGroup>> storyGroupsLiveData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> errorMessageLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> sessionExpiredLiveData = new MutableLiveData<>(false);
    private final List<RequestObserver<?>> activeObservers = new ArrayList<>();

    private String lastCreatedAtCursor;
    private boolean hasMorePosts = true;
    private boolean initialLoadStarted;
    private int loadGeneration;

    public HomeViewModel(@NonNull Application application) {
        super(application);
        this.sessionManager = SessionManager.getInstance(application);
        this.commentStore = new CommentStore(application);
        this.localStore = new LocalSocialStore(application);
        // 24-hour stories are moved to the archive on every cold start.
        this.localStore.pruneExpiredStories();

        PostRepository posts = null;
        StoryRepository stories = null;
        FollowRepository follows = null;
        ProfileRepository profiles = null;
        try {
            posts = new PostRepository(application);
            stories = new StoryRepository(application);
            follows = new FollowRepository(application);
            profiles = new ProfileRepository(application);
        } catch (RuntimeException e) {
            Log.e(TAG, "Unable to initialize Home data sources", e);
            errorMessageLiveData.setValue("Home data is unavailable. Please check your configuration and try again.");
        }
        this.postRepository = posts;
        this.storyRepository = stories;
        this.followRepository = follows;
        this.profileRepository = profiles;
    }

    public LiveData<List<Post>> getPosts() { return postsLiveData; }
    public LiveData<Boolean> getIsRefreshing() { return isRefreshingLiveData; }
    public LiveData<Boolean> getIsLoadingMore() { return isLoadingMoreLiveData; }
    public LiveData<Boolean> getIsAllCaughtUp() { return isAllCaughtUpLiveData; }
    public LiveData<List<StoryGroup>> getStoryGroups() { return storyGroupsLiveData; }
    public LiveData<String> getErrorMessage() { return errorMessageLiveData; }
    public LiveData<Boolean> getSessionExpired() { return sessionExpiredLiveData; }

    public String getCurrentUserId() {
        return sessionManager.getUserId();
    }

    /** Starts the first load once for this ViewModel instance. */
    public void loadInitial() {
        if (initialLoadStarted) return;
        initialLoadStarted = true;
        executeInitialLoad();
    }

    /** Explicit user-initiated refresh. A refresh is ignored while one is already running. */
    public void refresh() {
        if (Boolean.TRUE.equals(isRefreshingLiveData.getValue())) return;
        initialLoadStarted = true;
        executeInitialLoad();
    }

    private void executeInitialLoad() {
        final int generation = ++loadGeneration;
        lastCreatedAtCursor = null;
        hasMorePosts = true;
        isRefreshingLiveData.setValue(true);
        isAllCaughtUpLiveData.setValue(false);
        errorMessageLiveData.setValue(null);
        sessionExpiredLiveData.setValue(false);

        // The owner row is independent of active-story results. Publish it before
        // the network calls so an empty/error tray still keeps "Your story".
        ensureOwnerProfile();
        publishOwnerOnly();

        if (postRepository == null || storyRepository == null) {
            isRefreshingLiveData.setValue(false);
            publishError("Home data is unavailable. Please check your configuration and try again.", false);
            return;
        }

        try {
            observeOnce(storyRepository.getStoryGroups(), resource -> {
                if (generation != loadGeneration || resource == null) return;
                if (resource.status == Resource.Status.SUCCESS) {
                    storyGroupsLiveData.postValue(withOwnerFirst(resource.data));
                } else if (resource.status == Resource.Status.ERROR) {
                    // A backend failure must not remove the real owner row.
                    publishOwnerOnly();
                    publishError(resource.message == null ? "Unable to load stories." : resource.message,
                            isSessionError(resource.message));
                }
            });
        } catch (RuntimeException e) {
            Log.e(TAG, "Unable to request story groups", e);
            publishError("Unable to load stories.", false);
        }

        try {
            observeOnce(postRepository.getFeed(null, Constants.PAGE_SIZE_FEED), resource -> {
                if (generation != loadGeneration || resource == null) return;
                if (resource.status == Resource.Status.SUCCESS) {
                    List<Post> fetched = sanitizePosts(resource.data);
                    postsLiveData.postValue(mergeLocalPosts(fetched));
                    if (!fetched.isEmpty()) {
                        lastCreatedAtCursor = TimeUtils.toSupabaseTimestamp(
                                fetched.get(fetched.size() - 1).getCreatedAt());
                    }
                    hasMorePosts = fetched.size() >= Constants.PAGE_SIZE_FEED;
                    isAllCaughtUpLiveData.postValue(!hasMorePosts && !fetched.isEmpty());
                } else if (resource.status == Resource.Status.ERROR) {
                    publishError(resource.message, isSessionError(resource.message));
                }
                if (resource.status == Resource.Status.SUCCESS || resource.status == Resource.Status.ERROR) {
                    isRefreshingLiveData.postValue(false);
                }
            });
        } catch (RuntimeException e) {
            Log.e(TAG, "Unable to request feed", e);
            isRefreshingLiveData.setValue(false);
            publishError("Unable to load your feed.", false);
        }
    }

    public void loadMore() {
        if (Boolean.TRUE.equals(isLoadingMoreLiveData.getValue())
                || Boolean.TRUE.equals(isRefreshingLiveData.getValue())
                || !hasMorePosts
                || lastCreatedAtCursor == null
                || lastCreatedAtCursor.isEmpty()
                || postRepository == null) {
            return;
        }

        final int generation = loadGeneration;
        isLoadingMoreLiveData.setValue(true);
        try {
            observeOnce(postRepository.getFeed(lastCreatedAtCursor, Constants.PAGE_SIZE_FEED), resource -> {
                if (generation != loadGeneration || resource == null) return;
                if (resource.status == Resource.Status.SUCCESS) {
                    List<Post> fetched = sanitizePosts(resource.data);
                    if (!fetched.isEmpty()) {
                        List<Post> current = new ArrayList<>(sanitizePosts(postsLiveData.getValue()));
                        current.addAll(fetched);
                        postsLiveData.postValue(current);
                        lastCreatedAtCursor = TimeUtils.toSupabaseTimestamp(
                                fetched.get(fetched.size() - 1).getCreatedAt());
                    }
                    hasMorePosts = fetched.size() >= Constants.PAGE_SIZE_FEED;
                    if (!hasMorePosts) isAllCaughtUpLiveData.postValue(true);
                } else if (resource.status == Resource.Status.ERROR) {
                    publishError(resource.message, isSessionError(resource.message));
                }
                if (resource.status == Resource.Status.SUCCESS || resource.status == Resource.Status.ERROR) {
                    isLoadingMoreLiveData.postValue(false);
                }
            });
        } catch (RuntimeException e) {
            Log.e(TAG, "Unable to request more feed posts", e);
            isLoadingMoreLiveData.setValue(false);
            publishError("Unable to load more posts.", false);
        }
    }

    public void toggleLike(Post post, int position) {
        if (!isUsablePost(post) || postRepository == null) return;
        // Resolve the authoritative copy: the bound object may predate a refresh, and a
        // second quick tap must be computed from the state the list actually holds.
        Post current = PostInteractionState.findById(postsLiveData.getValue(), post.getId());
        if (current == null) current = post;

        final boolean originalLiked = current.isLikedByMe();
        final int originalCount = Math.max(0, current.getLikesCount());
        final boolean newLikeState = !originalLiked;

        // Replace, never mutate in place: the adapter holds the same instance, so an
        // in-place edit would make DiffUtil compare an object with itself and skip the
        // rebind (the like would be written to the database but never drawn).
        final Post updated = PostInteractionState.withLiked(
                current, newLikeState, originalCount + (newLikeState ? 1 : -1));
        updatePostInList(updated);

        final String targetId = updated.getId();
        // Locally created posts have no server row (a "local_..." id is not a uuid), so
        // the like stays local instead of surfacing a 400 from PostgREST.
        if (targetId.startsWith("local_") || targetId.startsWith("repost_")) return;
        observeOnce(postRepository.toggleLike(targetId, newLikeState), resource -> {
            if (resource != null && resource.status == Resource.Status.ERROR) {
                Post latest = PostInteractionState.findById(postsLiveData.getValue(), targetId);
                Post reverted = PostInteractionState.withLiked(
                        latest == null ? updated : latest, originalLiked, originalCount);
                updatePostInList(reverted);
                publishError(resource.message, isSessionError(resource.message));
            }
        });
    }

    public void toggleSave(Post post, int position) {
        if (!isUsablePost(post) || postRepository == null) return;
        Post current = PostInteractionState.findById(postsLiveData.getValue(), post.getId());
        if (current == null) current = post;

        final boolean originalSaved = current.isSavedByMe();
        final boolean newSaveState = PostInteractionState.nextSaveState(current);

        final Post updated = PostInteractionState.withSaved(current, newSaveState);
        updatePostInList(updated);

        final String targetId = updated.getId();
        // Persisted locally so the Saved grid and restarts keep the state; locally
        // created posts have no server row to write.
        localStore.setSaved(targetId, newSaveState);
        if (targetId.startsWith("local_")) return;

        observeOnce(postRepository.toggleSave(targetId, newSaveState), resource -> {
            if (resource != null && resource.status == Resource.Status.ERROR) {
                Post latest = PostInteractionState.findById(postsLiveData.getValue(), targetId);
                Post reverted = PostInteractionState.withSaved(
                        latest == null ? updated : latest, originalSaved);
                updatePostInList(reverted);
                // Keep the persisted flag in step with the rolled-back UI.
                localStore.setSaved(targetId, originalSaved);
                publishError(resource.message, isSessionError(resource.message));
            }
        });
    }

    public void deletePost(Post post) {
        if (!isUsablePost(post) || postRepository == null) return;
        observeOnce(postRepository.deletePost(post.getId()), resource -> {
            if (resource != null && resource.status == Resource.Status.SUCCESS) {
                List<Post> current = new ArrayList<>(sanitizePosts(postsLiveData.getValue()));
                current.remove(post);
                postsLiveData.postValue(current);
            } else if (resource != null && resource.status == Resource.Status.ERROR) {
                publishError(resource.message, isSessionError(resource.message));
            }
        });
    }

    public void unfollowUser(String targetUserId) {
        if (targetUserId == null || targetUserId.trim().isEmpty() || followRepository == null) return;
        observeOnce(followRepository.toggleFollow(targetUserId, false), resource -> {
            if (resource != null && resource.status == Resource.Status.SUCCESS) {
                refresh();
            } else if (resource != null && resource.status == Resource.Status.ERROR) {
                publishError(resource.message, false);
            }
        });
    }

    /**
     * Mirrors locally persisted comment counts onto the feed. Posts whose thread was
     * never opened keep the server count, so simply scrolling never changes a number.
     */
    public void refreshCommentCounts() {
        List<Post> current = postsLiveData.getValue();
        if (current == null || current.isEmpty()) return;

        List<Post> updated = new ArrayList<>(current.size());
        boolean changed = false;
        for (Post post : current) {
            if (post == null || post.getId() == null || post.getId().isEmpty()) {
                updated.add(post);
                continue;
            }
            int target = commentStore.isHydrated(post.getId())
                    ? commentStore.count(post.getId())
                    : post.getCommentsCount();
            if (target != post.getCommentsCount()) {
                Post copy = new Post(post);
                copy.setCommentsCount(target);
                updated.add(copy);
                changed = true;
            } else {
                updated.add(post);
            }
        }
        if (changed) postsLiveData.setValue(updated);
    }

    /** Local creations always sit above the server page. */
    private List<Post> mergeLocalPosts(List<Post> remote) {
        List<Post> out = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Post local : localStore.getPosts()) {
            if (local == null) continue;
            out.add(applyLocalSaveState(local));
            ids.add(local.getId());
        }
        if (remote != null) {
            for (Post post : remote) {
                if (post == null || ids.contains(post.getId())) continue;
                out.add(applyLocalSaveState(post));
            }
        }
        return withRepostCopies(out);
    }

    /**
     * Rebuilds the "You reposted" rows from the persisted repost set.
     *
     * <p>A repost copy only lived in the in-memory list, so every refresh (pull-to-refresh,
     * onResume, a new page) replaced it with the plain server page and the row vanished.
     * Deriving the copies from {@link LocalSocialStore#repostedIds()} makes them survive
     * any merge and keeps the order stable.
     */
    private List<Post> withRepostCopies(List<Post> base) {
        List<Post> out = new ArrayList<>();
        if (base == null) return out;

        Map<String, Post> byOrigin = new LinkedHashMap<>();
        for (Post post : base) {
            if (post == null || post.getId() == null) continue;
            if (post.getId().startsWith(REPOST_ID_PREFIX)) continue;
            if (!byOrigin.containsKey(post.getId())) byOrigin.put(post.getId(), post);
        }

        for (String originId : localStore.repostedIds()) {
            Post origin = byOrigin.get(originId);
            if (origin == null) continue;
            Post copy = new Post(origin);
            copy.setId(REPOST_ID_PREFIX + originId);
            copy.setLocation(REPOST_LOCATION);
            out.add(copy);
        }
        for (Post post : base) {
            if (post == null || post.getId() == null) continue;
            if (post.getId().startsWith(REPOST_ID_PREFIX)) continue;
            out.add(post);
        }
        return out;
    }

    /**
     * The bookmark is a local flag; v_feed only knows the server's view, so the
     * persisted save/unsave wins whenever it differs.
     */
    private Post applyLocalSaveState(Post post) {
        if (post == null || post.getId() == null || post.getId().isEmpty()) return post;
        if (localStore.isSaved(post.getId())) {
            if (post.isSavedByMe()) return post;
            Post copy = new Post(post);
            copy.setSaved(true);
            return copy;
        }
        if (localStore.isExplicitlyUnsaved(post.getId()) && post.isSavedByMe()) {
            Post copy = new Post(post);
            copy.setSaved(false);
            return copy;
        }
        return post;
    }

    /** Re-applies local creations without waiting for a network refresh. */
    public void refreshLocalContent() {
        List<Post> current = postsLiveData.getValue();
        if (current == null) current = new ArrayList<>();
        postsLiveData.setValue(mergeLocalPosts(new ArrayList<>(current)));
    }

    /**
     * Applies a repost / undo to the visible feed. The row itself is derived from the
     * persisted repost set (see {@link #withRepostCopies(List)}), so this only has to
     * re-merge the list that is already on screen.
     */
    public void applyRepost(Post post, boolean reposted) {
        if (post == null || post.getId() == null) return;
        List<Post> current = sanitizePosts(postsLiveData.getValue());
        postsLiveData.setValue(mergeLocalPosts(current));
    }

    private void updatePostInList(Post post) {
        if (!isUsablePost(post)) return;
        List<Post> current = sanitizePosts(postsLiveData.getValue());
        postsLiveData.setValue(PostInteractionState.replace(current, post));
    }

    private List<Post> sanitizePosts(List<Post> source) {
        List<Post> result = new ArrayList<>();
        if (source == null) return result;
        for (Post post : source) {
            if (!isUsablePost(post)) continue;
            if (post.getUserId() == null) post.setUserId("");
            if (post.getUsername() == null) post.setUsername("");
            if (post.getAvatarUrl() == null) post.setAvatarUrl("");
            if (post.getCaption() == null) post.setCaption("");
            if (post.getLocation() == null) post.setLocation("");
            if (post.getMediaUrls() == null) post.setMediaUrls(new ArrayList<>());
            if (post.getMediaType() == null) post.setMediaType("image");
            result.add(post);
        }
        return result;
    }

    private List<StoryGroup> sanitizeStoryGroups(List<StoryGroup> source) {
        List<StoryGroup> result = new ArrayList<>();
        if (source == null) return result;
        for (StoryGroup group : source) {
            if (group == null) continue;
            if (group.getUserId() == null) group.setUserId("");
            if (group.getUsername() == null) group.setUsername("");
            if (group.getAvatarUrl() == null) group.setAvatarUrl("");
            result.add(group);
        }
        return result;
    }

    private List<StoryGroup> withOwnerFirst(List<StoryGroup> groups) {
        List<StoryGroup> source = sanitizeStoryGroups(groups);
        List<Story> localStories = localStore.getActiveStories();
        if (!localStories.isEmpty()) {
            String ownerId = sessionManager.getUserId();
            StoryGroup owner = null;
            for (StoryGroup group : source) {
                if (group == null) continue;
                if (group.isOwner() || (ownerId != null && ownerId.equals(group.getUserId()))) {
                    owner = group;
                    break;
                }
            }
            List<Story> merged = new ArrayList<>(localStories);
            if (owner != null && owner.getStories() != null) {
                for (Story story : owner.getStories()) {
                    boolean duplicate = false;
                    for (Story local : merged) {
                        if (story != null && local != null && story.getId() != null
                                && story.getId().equals(local.getId())) {
                            duplicate = true;
                            break;
                        }
                    }
                    if (!duplicate) merged.add(story);
                }
            }
            if (owner != null) source.remove(owner);
            // hasUnseen=true -> gradient ring; the add badge disappears because
            // the group now reports an active story.
            source.add(0, new StoryGroup(ownerId,
                    sessionManager.getUsername(), sessionManager.getAvatarUrl(),
                    true, true, merged));
        }
        return StoryTrayOrdering.ownerFirst(
                source,
                sessionManager.getUserId(),
                sessionManager.getUsername(),
                sessionManager.getAvatarUrl());
    }

    private void publishOwnerFirst(List<StoryGroup> groups) {
        storyGroupsLiveData.postValue(withOwnerFirst(groups));
    }

    private void publishOwnerOnly() {
        storyGroupsLiveData.setValue(StoryTrayOrdering.ownerFirst(
                new ArrayList<>(),
                sessionManager.getUserId(),
                sessionManager.getUsername(),
                sessionManager.getAvatarUrl()));
    }

    /**
     * Loads the signed-in user's real profile once when the cached avatar is
     * missing, so a no-story owner row still shows the actual avatar.
     */
    private void ensureOwnerProfile() {
        if (ownerProfileStarted || profileRepository == null) return;
        String userId = sessionManager.getUserId();
        if (userId == null || userId.trim().isEmpty()) return;
        if (sessionManager.getAvatarUrl() != null && !sessionManager.getAvatarUrl().trim().isEmpty()) {
            return;
        }
        ownerProfileStarted = true;
        try {
            observeOnce(profileRepository.getProfile(userId), resource -> {
                if (resource == null || resource.status != Resource.Status.SUCCESS || resource.data == null) {
                    ownerProfileStarted = false;
                    return;
                }
                Profile profile = resource.data;
                if (profile.getAvatarUrl() != null && !profile.getAvatarUrl().trim().isEmpty()) {
                    sessionManager.updateAvatar(profile.getAvatarUrl());
                }
                if (profile.getUsername() != null && !profile.getUsername().trim().isEmpty()) {
                    sessionManager.updateUsername(profile.getUsername());
                }
                publishOwnerFirst(storyGroupsLiveData.getValue());
            });
        } catch (RuntimeException e) {
            ownerProfileStarted = false;
            Log.e(TAG, "Unable to resolve owner profile for the story tray", e);
        }
    }

    private boolean isUsablePost(Post post) {
        return post != null && post.getId() != null && !post.getId().trim().isEmpty();
    }

    private boolean isSessionError(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase();
        return lower.contains("401") || lower.contains("jwt") || lower.contains("token")
                || lower.contains("session") || lower.contains("not authenticated");
    }

    private void publishError(String message, boolean sessionExpired) {
        String safeMessage = message == null || message.trim().isEmpty()
                ? "Unable to load the Home feed. Please try again."
                : message;
        errorMessageLiveData.postValue(safeMessage);
        if (sessionExpired) {
            sessionManager.clearSession();
            sessionExpiredLiveData.postValue(true);
        }
    }

    private <T> void observeOnce(LiveData<Resource<T>> source, Observer<Resource<T>> callback) {
        if (source == null) return;
        RequestObserver<T> observer = new RequestObserver<>(source, callback);
        activeObservers.add(observer);
        source.observeForever(observer);
    }

    @Override
    protected void onCleared() {
        for (RequestObserver<?> observer : new ArrayList<>(activeObservers)) {
            observer.dispose();
        }
        activeObservers.clear();
        super.onCleared();
    }

    private final class RequestObserver<T> implements Observer<Resource<T>> {
        private final LiveData<Resource<T>> source;
        private final Observer<Resource<T>> callback;
        private boolean disposed;

        RequestObserver(LiveData<Resource<T>> source, Observer<Resource<T>> callback) {
            this.source = source;
            this.callback = callback;
        }

        @Override
        public void onChanged(Resource<T> resource) {
            if (disposed || resource == null) return;
            callback.onChanged(resource);
            if (resource.status == Resource.Status.SUCCESS || resource.status == Resource.Status.ERROR) {
                dispose();
            }
        }

        void dispose() {
            if (disposed) return;
            disposed = true;
            source.removeObserver(this);
            activeObservers.remove(this);
        }
    }
}
