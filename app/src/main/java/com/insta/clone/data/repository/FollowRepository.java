package com.insta.clone.data.repository;

import android.content.Context;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.insta.clone.util.Resource;
import java.util.HashSet;
import java.util.Set;

/**
 * Repository for follow/unfollow operations and relationship status tracking.
 */
public class FollowRepository {

    private final Context context;
    private final Set<String> followingSet = new HashSet<>();

    public FollowRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    public LiveData<Resource<Boolean>> toggleFollow(String targetUserId, boolean follow) {
        MutableLiveData<Resource<Boolean>> liveData = new MutableLiveData<>();
        if (follow) {
            followingSet.add(targetUserId);
        } else {
            followingSet.remove(targetUserId);
        }
        liveData.postValue(Resource.success(follow));
        return liveData;
    }

    public LiveData<Resource<Boolean>> isFollowing(String targetUserId) {
        MutableLiveData<Resource<Boolean>> liveData = new MutableLiveData<>();
        liveData.postValue(Resource.success(followingSet.contains(targetUserId)));
        return liveData;
    }
}
