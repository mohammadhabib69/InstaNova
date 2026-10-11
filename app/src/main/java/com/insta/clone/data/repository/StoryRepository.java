package com.insta.clone.data.repository;

import android.content.Context;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.insta.clone.data.local.SessionManager;
import com.insta.clone.data.model.Story;
import com.insta.clone.data.model.StoryGroup;
import com.insta.clone.data.remote.RetrofitClient;
import com.insta.clone.data.remote.api.RestApi;
import com.insta.clone.data.remote.dto.StoryGroupDto;
import com.insta.clone.util.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Active story tray repository backed by the RPC and v_stories view. */
public class StoryRepository {
    private final RestApi restApi;
    private final SessionManager sessionManager;
    private final MutableLiveData<List<StoryGroup>> rawGroups = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Resource<List<StoryGroup>>> groups = new MutableLiveData<>();

    public StoryRepository(Context context) {
        Context app = context.getApplicationContext();
        restApi = RetrofitClient.getInstance(app).getRestApi();
        sessionManager = SessionManager.getInstance(app);
    }

    public LiveData<Resource<List<StoryGroup>>> getStoryGroups() {
        groups.postValue(Resource.loading());
        restApi.getStoryGroups(Collections.emptyMap()).enqueue(new Callback<List<StoryGroupDto>>() {
            @Override public void onResponse(Call<List<StoryGroupDto>> call, Response<List<StoryGroupDto>> response) {
                if (!response.isSuccessful() || response.body() == null) { groups.postValue(Resource.error(AuthRepository.errorBody(response))); return; }
                List<StoryGroup> result = new ArrayList<>(); String uid = sessionManager.getUserId();
                for (StoryGroupDto dto : response.body()) { StoryGroup group = dto.toStoryGroup(); group.setOwner(uid != null && uid.equals(dto.getUserId())); result.add(group); }
                rawGroups.postValue(result); groups.postValue(Resource.success(result));
            }
            @Override public void onFailure(Call<List<StoryGroupDto>> call, Throwable t) { groups.postValue(Resource.error(t.getMessage())); }
        });
        return groups;
    }

    public LiveData<List<StoryGroup>> getRawStoryGroups() { return rawGroups; }
    public void setStoryGroups(List<StoryGroup> value) { rawGroups.postValue(value); groups.postValue(Resource.success(value)); }

    public void markStorySeen(String userId, String storyId) {
        Map<String, Object> body = new HashMap<>(); body.put("story_id", storyId); body.put("viewer_id", sessionManager.getUserId());
        restApi.recordStoryView(body).enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> call, Response<Void> response) { if (response.isSuccessful()) getStoryGroups(); }
            @Override public void onFailure(Call<Void> call, Throwable t) { }
        });
    }
}
