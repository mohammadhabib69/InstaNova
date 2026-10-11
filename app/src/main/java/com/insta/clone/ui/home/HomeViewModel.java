package com.insta.clone.ui.home;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.insta.clone.data.local.SessionManager;
import com.insta.clone.data.model.Post;
import com.insta.clone.data.model.StoryGroup;
import com.insta.clone.data.repository.FollowRepository;
import com.insta.clone.data.repository.PostRepository;
import com.insta.clone.data.repository.StoryRepository;
import com.insta.clone.util.Constants;
import com.insta.clone.util.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * ViewModel managing Home Feed state, story groups, keyset pagination, and optimistic updates.
 */
public class HomeViewModel extends AndroidViewModel {

    private final PostRepository postRepository;
    private final StoryRepository storyRepository;
    private final FollowRepository followRepository;
    private final SessionManager sessionManager;

    private final MutableLiveData<List<Post>> postsLiveData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> isRefreshingLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isLoadingMoreLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isAllCaughtUpLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<List<StoryGroup>> storyGroupsLiveData = new MutableLiveData<>(new ArrayList<>());

    private String lastCreatedAtCursor = null;
    private boolean hasMorePosts = true;

    public HomeViewModel(@NonNull Application application) {
        super(application);
        this.postRepository = new PostRepository(application);
        this.storyRepository = new StoryRepository(application);
        this.followRepository = new FollowRepository(application);
        this.sessionManager = SessionManager.getInstance(application);

        loadInitial();
    }

    public LiveData<List<Post>> getPosts() { return postsLiveData; }
    public LiveData<Boolean> getIsRefreshing() { return isRefreshingLiveData; }
    public LiveData<Boolean> getIsAllCaughtUp() { return isAllCaughtUpLiveData; }
    public LiveData<List<StoryGroup>> getStoryGroups() { return storyGroupsLiveData; }

    public String getCurrentUserId() {
        return sessionManager.getUserId();
    }

    public void loadInitial() {
        isRefreshingLiveData.setValue(true);
        lastCreatedAtCursor = null;
        hasMorePosts = true;
        isAllCaughtUpLiveData.setValue(false);

        // 1. Fetch story groups
        storyRepository.getStoryGroups().observeForever(resource -> {
            if (resource.status == Resource.Status.SUCCESS && resource.data != null) {
                storyGroupsLiveData.postValue(resource.data);
            }
        });

        // 2. Fetch feed posts
        postRepository.getFeed(null, Constants.PAGE_SIZE_FEED).observeForever(resource -> {
            isRefreshingLiveData.postValue(false);
            if (resource.status == Resource.Status.SUCCESS && resource.data != null) {
                List<Post> fetched = resource.data;
                postsLiveData.postValue(fetched);

                if (!fetched.isEmpty()) {
                    lastCreatedAtCursor = String.valueOf(fetched.get(fetched.size() - 1).getCreatedAt());
                }
                hasMorePosts = fetched.size() >= Constants.PAGE_SIZE_FEED;
                isAllCaughtUpLiveData.postValue(!hasMorePosts && !fetched.isEmpty());
            }
        });
    }

    public void loadMore() {
        if (Boolean.TRUE.equals(isLoadingMoreLiveData.getValue()) || !hasMorePosts || lastCreatedAtCursor == null) {
            return;
        }

        isLoadingMoreLiveData.setValue(true);

        postRepository.getFeed(lastCreatedAtCursor, Constants.PAGE_SIZE_FEED).observeForever(resource -> {
            isLoadingMoreLiveData.postValue(false);
            if (resource.status == Resource.Status.SUCCESS && resource.data != null) {
                List<Post> fetched = resource.data;
                if (!fetched.isEmpty()) {
                    List<Post> current = new ArrayList<>(postsLiveData.getValue() != null ? postsLiveData.getValue() : new ArrayList<>());
                    current.addAll(fetched);
                    postsLiveData.postValue(current);
                    lastCreatedAtCursor = String.valueOf(fetched.get(fetched.size() - 1).getCreatedAt());
                }
                hasMorePosts = fetched.size() >= Constants.PAGE_SIZE_FEED;
                if (!hasMorePosts) {
                    isAllCaughtUpLiveData.postValue(true);
                }
            }
        });
    }

    public void toggleLike(Post post, int position) {
        boolean newLikeState = !post.isLikedByMe();
        int newCount = post.getLikesCount() + (newLikeState ? 1 : -1);

        // Optimistic update
        post.setLikedByMe(newLikeState);
        post.setLikesCount(Math.max(0, newCount));
        updatePostInList(post);

        postRepository.toggleLike(post.getId(), newLikeState).observeForever(resource -> {
            if (resource.status == Resource.Status.ERROR) {
                // Rollback on failure
                post.setLikedByMe(!newLikeState);
                post.setLikesCount(post.getLikesCount() + (newLikeState ? -1 : 1));
                updatePostInList(post);
            }
        });
    }

    public void toggleSave(Post post, int position) {
        boolean newSaveState = !post.isSavedByMe();

        // Optimistic update
        post.setSavedByMe(newSaveState);
        updatePostInList(post);

        postRepository.toggleSave(post.getId(), newSaveState).observeForever(resource -> {
            if (resource.status == Resource.Status.ERROR) {
                post.setSavedByMe(!newSaveState);
                updatePostInList(post);
            }
        });
    }

    public void deletePost(Post post) {
        postRepository.deletePost(post.getId()).observeForever(resource -> {
            if (resource.status == Resource.Status.SUCCESS) {
                List<Post> current = new ArrayList<>(postsLiveData.getValue() != null ? postsLiveData.getValue() : new ArrayList<>());
                current.remove(post);
                postsLiveData.postValue(current);
            }
        });
    }

    public void unfollowUser(String targetUserId) {
        followRepository.toggleFollow(targetUserId, false).observeForever(resource -> {
            if (resource.status == Resource.Status.SUCCESS) {
                loadInitial(); // Refresh feed to exclude unfollowed user
            }
        });
    }

    private void updatePostInList(Post post) {
        List<Post> current = new ArrayList<>(postsLiveData.getValue() != null ? postsLiveData.getValue() : new ArrayList<>());
        for (int i = 0; i < current.size(); i++) {
            if (current.get(i).getId().equals(post.getId())) {
                current.set(i, post);
                break;
            }
        }
        postsLiveData.setValue(current);
    }
}
