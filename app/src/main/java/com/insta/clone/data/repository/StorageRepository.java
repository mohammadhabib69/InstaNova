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

import android.util.Log;
import androidx.annotation.NonNull;
import com.insta.clone.util.MediaUrl;
import java.io.IOException;
import okhttp3.OkHttpClient;
import okhttp3.Request;

/**
 * Repository for uploading media bytes to Supabase Storage buckets (posts, stories, reels, avatars, chat).
 */
public class StorageRepository {

    private static final String TAG = "InstaApi";

    private final StorageApi storageApi;
    private final SessionManager sessionManager;
    private final OkHttpClient okHttpClient;

    public StorageRepository(Context context) {
        RetrofitClient client = RetrofitClient.getInstance(context);
        this.storageApi = client.getStorageApi();
        this.sessionManager = SessionManager.getInstance(context);
        this.okHttpClient = client.getOkHttpClient();
    }

    public LiveData<Resource<String>> uploadFile(String bucket, byte[] fileBytes, String extension, String mimeType) {
        MutableLiveData<Resource<String>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));

        if (fileBytes == null || fileBytes.length == 0) {
            String err = "Upload failed: image byte array is empty";
            Log.e(TAG, err);
            result.setValue(Resource.error(err, null));
            return result;
        }

        String myId = sessionManager.getUserId();
        if (myId == null) {
            result.setValue(Resource.error("Not authenticated", null));
            return result;
        }

        String fileName = UUID.randomUUID().toString() + "." + (extension != null ? extension : "jpg");
        String path = myId + "/" + fileName;

        String mediaTypeStr = mimeType != null ? mimeType : "image/jpeg";
        RequestBody requestBody = RequestBody.create(
                MediaType.parse(mediaTypeStr),
                fileBytes
        );

        Log.d(TAG, "uploaded " + fileBytes.length + " bytes to " + bucket + "/" + path);

        storageApi.uploadFile(bucket, path, requestBody).enqueue(new retrofit2.Callback<Void>() {
            @Override
            public void onResponse(retrofit2.Call<Void> call, retrofit2.Response<Void> response) {
                if (response.isSuccessful()) {
                    String base = BuildConfig.SUPABASE_URL.endsWith("/")
                            ? BuildConfig.SUPABASE_URL
                            : BuildConfig.SUPABASE_URL + "/";
                    String publicUrl = MediaUrl.normalize(bucket, base + "storage/v1/object/public/" + bucket + "/" + path);
                    Log.d(TAG, "Storage upload successful HTTP " + response.code() + ": " + publicUrl + ". Verifying via HEAD...");

                    Request headReq = new Request.Builder()
                            .url(publicUrl)
                            .head()
                            .build();

                    okHttpClient.newCall(headReq).enqueue(new okhttp3.Callback() {
                        @Override
                        public void onResponse(@NonNull okhttp3.Call c, @NonNull okhttp3.Response headResponse) throws IOException {
                            int headCode = headResponse.code();
                            String contentType = headResponse.header("Content-Type");
                            String lenStr = headResponse.header("Content-Length");
                            long contentLength = -1;
                            try {
                                if (lenStr != null) {
                                    contentLength = Long.parseLong(lenStr);
                                }
                            } catch (NumberFormatException ignored) {}
                            headResponse.close();

                            Log.d(TAG, "HEAD status=" + headCode + " Content-Type=" + contentType
                                    + " Content-Length=" + contentLength + " expected=" + fileBytes.length + " for " + publicUrl);

                            if (headCode == 200) {
                                result.postValue(Resource.success(publicUrl));
                            } else {
                                String err = "HEAD verification failed (HTTP " + headCode + ") for " + publicUrl;
                                Log.e(TAG, err);
                                result.postValue(Resource.error(err, null));
                            }
                        }

                        @Override
                        public void onFailure(@NonNull okhttp3.Call c, @NonNull IOException e) {
                            String err = "HEAD verification failed: " + e.getMessage();
                            Log.e(TAG, err, e);
                            result.postValue(Resource.error(err, null));
                        }
                    });
                } else {
                    String errBody = "";
                    try {
                        if (response.errorBody() != null) errBody = response.errorBody().string();
                    } catch (Exception ignored) {}
                    Log.e(TAG, "HTTP " + response.code() + " on storage upload to " + bucket + "/" + path + ": " + errBody);
                    String msg;
                    if (response.code() == 403 || (response.code() == 400 && errBody.contains("row-level security policy"))) {
                        msg = "403 Forbidden: Storage policy rejected upload or path does not start with user id.";
                    } else if (response.code() == 404) {
                        msg = "404 Not Found: Bucket '" + bucket + "' does not exist.";
                    } else if (response.code() == 401) {
                        msg = "401 Unauthorized: Invalid or missing authentication token.";
                    } else {
                        msg = "Upload failed (HTTP " + response.code() + "): " + errBody;
                    }
                    result.postValue(Resource.error(msg, null));
                }
            }

            @Override
            public void onFailure(retrofit2.Call<Void> call, Throwable t) {
                Log.e(TAG, "Storage upload network failure: " + t.getMessage(), t);
                result.postValue(Resource.error(t.getMessage() != null ? t.getMessage() : "Upload network error", null));
            }
        });

        return result;
    }

    public StorageApi getStorageApi() {
        return storageApi;
    }
}

