package com.insta.clone.data.repository;

import android.content.Context;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.insta.clone.data.remote.RetrofitClient;
import com.insta.clone.data.remote.api.RestApi;
import com.insta.clone.data.remote.dto.UnreadCountsDto;
import com.insta.clone.util.Resource;
import java.util.Collections;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Notification and inbox badge counts from Supabase. */
public class ActivityRepository {
    public static class UnreadCounts {
        private final int notifications; private final int directMessages;
        public UnreadCounts(int notifications, int directMessages) { this.notifications = notifications; this.directMessages = directMessages; }
        public int getNotifications() { return notifications; }
        public int getDirectMessages() { return directMessages; }
    }
    private final RestApi restApi;
    public ActivityRepository(Context context) { restApi = RetrofitClient.getInstance(context.getApplicationContext()).getRestApi(); }
    public LiveData<Resource<UnreadCounts>> getUnreadCounts() {
        MutableLiveData<Resource<UnreadCounts>> result = new MutableLiveData<>(); result.postValue(Resource.loading());
        restApi.getUnreadCounts(Collections.emptyMap()).enqueue(new Callback<UnreadCountsDto>() {
            @Override public void onResponse(Call<UnreadCountsDto> call, Response<UnreadCountsDto> response) {
                if (response.isSuccessful() && response.body() != null) result.postValue(Resource.success(new UnreadCounts(response.body().getNotifications(), response.body().getDirectMessages())));
                else result.postValue(Resource.error(AuthRepository.errorBody(response)));
            }
            @Override public void onFailure(Call<UnreadCountsDto> call, Throwable t) { result.postValue(Resource.error(t.getMessage())); }
        }); return result;
    }
}
