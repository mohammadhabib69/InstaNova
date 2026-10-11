package com.insta.clone.data.repository;

import android.content.Context;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.insta.clone.BuildConfig;
import com.insta.clone.data.local.SessionManager;
import com.insta.clone.data.remote.RetrofitClient;
import com.insta.clone.data.remote.api.StorageApi;
import com.insta.clone.util.Resource;
import java.util.UUID;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Repository for uploading media bytes to Supabase Storage buckets (posts, stories, reels, avatars, chat).
 */
public class StorageRepository {

    private final StorageApi storageApi;
    private final SessionManager sessionManager;

    public StorageRepository(Context context) {
        RetrofitClient client = RetrofitClient.getInstance(context);
        this.storageApi = client.getStorageApi();
        this.sessionManager = SessionManager.getInstance(context);
    }

    public LiveData<Resource<String>> uploadFile(String bucket, byte[] fileBytes, String extension, String mimeType) {
        MutableLiveData<Resource<String>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));

        String myId = sessionManager.getUserId();
        if (myId == null) {
            result.setValue(Resource.error("Not authenticated", null));
            return result;
        }

        String fileName = UUID.randomUUID().toString() + "." + extension;
        String path = myId + "/" + fileName;

        RequestBody requestBody = RequestBody.create(
                MediaType.parse(mimeType != null ? mimeType : "image/jpeg"),
                fileBytes
        );

        storageApi.uploadFile(bucket, path, requestBody).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    String base = BuildConfig.SUPABASE_URL.endsWith("/")
                            ? BuildConfig.SUPABASE_URL
                            : BuildConfig.SUPABASE_URL + "/";
                    String publicUrl = base + "storage/v1/object/public/" + bucket + "/" + path;
                    result.postValue(Resource.success(publicUrl));
                } else {
                    result.postValue(Resource.error("Upload failed (" + response.code() + ")", null));
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                result.postValue(Resource.error(t.getMessage() != null ? t.getMessage() : "Upload network error", null));
            }
        });

        return result;
    }
}
