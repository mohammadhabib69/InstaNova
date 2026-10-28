package com.insta.nova.safe;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.insta.nova.data.NovaDtos;
import com.insta.nova.data.NovaRepository;
import com.insta.nova.util.Resource;

import java.util.List;

public class NovaSafeViewModel extends AndroidViewModel {

    private final NovaRepository repository;
    private final MutableLiveData<Long> trigger = new MutableLiveData<>(0L);

    private final LiveData<Resource<NovaDtos.SafetyStats>> stats;
    private final LiveData<Resource<List<NovaDtos.BlockedUser>>> blocked;
    private final LiveData<Resource<List<NovaDtos.MutedUser>>> muted;

    public NovaSafeViewModel(@NonNull Application application) {
        super(application);
        repository = new NovaRepository(application);
        stats = Transformations.switchMap(trigger, t -> repository.safetyStats());
        blocked = Transformations.switchMap(trigger, t -> repository.blockedUsers());
        muted = Transformations.switchMap(trigger, t -> repository.mutedUsers());
    }

    public LiveData<Resource<NovaDtos.SafetyStats>> stats() {
        return stats;
    }

    public LiveData<Resource<List<NovaDtos.BlockedUser>>> blocked() {
        return blocked;
    }

    public LiveData<Resource<List<NovaDtos.MutedUser>>> muted() {
        return muted;
    }

    public void refresh() {
        trigger.setValue(System.currentTimeMillis());
    }

    public void unblock(String userId) {
        repository.unblock(userId).observeForever(resource -> {
            if (resource != null && resource.status != Resource.Status.LOADING) refresh();
        });
    }

    public void setMute(String userId, boolean posts, boolean stories) {
        repository.mute(userId, posts, stories).observeForever(resource -> {
            if (resource != null && resource.status != Resource.Status.LOADING) refresh();
        });
    }
}
