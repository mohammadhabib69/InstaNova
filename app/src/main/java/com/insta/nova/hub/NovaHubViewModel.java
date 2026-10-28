package com.insta.nova.hub;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.insta.nova.data.NovaDtos;
import com.insta.nova.data.NovaRepository;
import com.insta.nova.util.Resource;

/** Holds the hub summary so rotation does not refetch or lose the numbers. */
public class NovaHubViewModel extends AndroidViewModel {

    private final NovaRepository repository;
    private final MutableLiveData<Long> trigger = new MutableLiveData<>(0L);
    private final LiveData<Resource<NovaDtos.HubSummary>> summary;

    public NovaHubViewModel(@NonNull Application application) {
        super(application);
        repository = new NovaRepository(application);
        summary = Transformations.switchMap(trigger, ignored -> repository.hubSummary());
    }

    public LiveData<Resource<NovaDtos.HubSummary>> summary() {
        return summary;
    }

    public void refresh() {
        trigger.setValue(System.currentTimeMillis());
    }
}
