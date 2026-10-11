package com.insta.clone.ui.home;

import android.app.Application;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import com.insta.clone.data.local.SessionManager;
import com.insta.clone.data.model.Post;
import com.insta.clone.data.model.StoryGroup;
import com.insta.clone.data.repository.FollowRepository;
import com.insta.clone.data.repository.PostRepository;
import com.insta.clone.data.repository.StoryRepository;
import com.insta.clone.util.Constants;
import com.insta.clone.util.Resource;
import com.insta.clone.util.TimeUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * ViewModel managing Home Feed state, story groups, keyset pagination, and optimistic updates.
 */
public class HomeViewModel extends AndroidViewModel {

    private static final String TAG = "HomeViewModel";

    private final PostRepository postRepository;
    private final StoryRepository storyRepository;
    private final FollowRepository followRepository;
    private final SessionManager sessionManager;

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

        PostRepository posts = null;
        StoryRepository stories = null;
        FollowRepository follows = null;
        try {
            posts = new PostRepository(application);
            stories = new StoryRepository(application);
            follows = new FollowRepository(application);
        } catch (RuntimeException e) {
            Log.e(TAG, "Unable to initialize Home data sources", e);
            errorMessageLiveData.setValue("Home data is unavailable. Please check your configuration and try again.");
        }
        this.postRepository = posts;
        this.storyRepository = stories;
        this.followRepository = follows;
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

        if (postRepository == null || storyRepository == null) {
            isRefreshingLiveData.setValue(false);
            publishError("Home data is unavailable. Please check your configuration and try again.", false);
            return;
        }

        try {
            observeOnce(storyRepository.getStoryGroups(), resource -> {
                if (generation != loadGeneration || resource == null) return;
                if (resource.status == Resource.Status.SUCCESS) {
                    storyGroupsLiveData.postValue(sanitizeStoryGroups(resource.data));
                } else if (resource.status == Resource.Status.ERROR) {
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
                    postsLiveData.postValue(fetched);
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
        boolean originalLiked = post.isLikedByMe();
        int originalCount = Math.max(0, post.getLikesCount());
        boolean newLikeState = !originalLiked;
        post.setLikedByMe(newLikeState);
        post.setLikesCount(Math.max(0, originalCount + (newLikeState ? 1 : -1)));
        updatePostInList(post);

        observeOnce(postRepository.toggleLike(post.getId(), newLikeState), resource -> {
            if (resource != null && resource.status == Resource.Status.ERROR
                    && post.isLikedByMe() == newLikeState) {
                post.setLikedByMe(originalLiked);
                post.setLikesCount(originalCount);
                updatePostInList(post);
                publishError(resource.message, isSessionError(resource.message));
            }
        });
    }

    public void toggleSave(Post post, int position) {
        if (!isUsablePost(post) || postRepository == null) return;
        boolean originalSaved = post.isSavedByMe();
        boolean newSaveState = !originalSaved;
        post.setSavedByMe(newSaveState);
        updatePostInList(post);

        observeOnce(postRepository.toggleSave(post.getId(), newSaveState), resource -> {
            if (resource != null && resource.status == Resource.Status.ERROR
                    && post.isSavedByMe() == newSaveState) {
                post.setSavedByMe(originalSaved);
                updatePostInList(post);
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

    private void updatePostInList(Post post) {
        if (!isUsablePost(post)) return;
        List<Post> current = new ArrayList<>(sanitizePosts(postsLiveData.getValue()));
        for (int i = 0; i < current.size(); i++) {
            Post currentPost = current.get(i);
            if (Objects.equals(currentPost.getId(), post.getId())) {
                current.set(i, post);
                postsLiveData.setValue(current);
                return;
            }
        }
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
