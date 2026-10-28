package com.insta.nova.data.repository;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.insta.nova.BuildConfig;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.remote.RetrofitClient;
import com.insta.nova.data.remote.api.StorageApi;
import com.insta.nova.util.MediaUrl;
import com.insta.nova.util.Resource;

import java.io.IOException;
import java.util.UUID;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

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

    // P10-07: uploads are the slowest user-visible action; a named trace makes p95 real.
    @com.google.firebase.perf.metrics.AddTrace(name = "media_upload")
    public LiveData<Resource<String>> uploadFile(String bucket, byte[] fileBytes, String extension, String mimeType) {
        String myId = sessionManager.getUserId();
        if (myId == null || myId.isEmpty()) {
            MutableLiveData<Resource<String>> result = new MutableLiveData<>();
            result.setValue(Resource.error("Not authenticated", null));
            return result;
        }
        String fileName = UUID.randomUUID().toString() + "." + (extension != null ? extension : "jpg");
        return uploadToPath(bucket, fileBytes, myId + "/" + fileName, mimeType);
    }

    /**
     * NOVA — uploads to the existing public {@code posts} bucket at the documented
     * {@code {me}/nova/{uuid}.jpg} path using the same raw-bytes pipeline and the same
     * public-URL HEAD check before anything is written to a table.
     */
    public LiveData<Resource<String>> uploadNovaImage(byte[] fileBytes) {
        MutableLiveData<Resource<String>> result = new MutableLiveData<>();
        String myId = sessionManager.getUserId();
        if (myId == null || myId.isEmpty()) {
            result.setValue(Resource.error("Not authenticated", null));
            return result;
        }
        if (fileBytes == null || fileBytes.length == 0) {
            result.setValue(Resource.error("Upload failed: image byte array is empty", null));
            return result;
        }
        String path = myId + "/nova/" + UUID.randomUUID() + ".jpg";
        return uploadToPath("posts", fileBytes, path, "image/jpeg");
    }

    /**
     * NOVA — uploads to the dedicated {@code nova} bucket at {@code {me}/{uuid}.jpg}.
     *
     * <p>Nova media lives in its own bucket so the expiry job can remove it
     * without touching the Instagram media.
     */
    public LiveData<Resource<String>> uploadNovaMedia(byte[] fileBytes) {
        MutableLiveData<Resource<String>> result = new MutableLiveData<>();
        String myId = sessionManager.getUserId();
        if (myId == null || myId.isEmpty()) {
            result.setValue(Resource.error("Not authenticated", null));
            return result;
        }
        if (fileBytes == null || fileBytes.length == 0) {
            result.setValue(Resource.error("Upload failed: image byte array is empty", null));
            return result;
        }
        String path = myId + "/" + UUID.randomUUID() + ".jpg";
        return uploadToPath("nova", fileBytes, path, "image/jpeg");
    }

    /**
     * Blocking twin of {@link #uploadNovaImage(byte[])} for WorkManager. The LiveData version
     * calls setValue from the OkHttp callback, which throws off the main thread, so the offline
     * queue needs a plain synchronous call with the same bucket, path rule and HEAD check.
     */
    public String uploadNovaImageSync(byte[] fileBytes) throws IOException {
        String myId = sessionManager.getUserId();
        if (myId == null || myId.isEmpty()) throw new IOException("Not authenticated");
        if (fileBytes == null || fileBytes.length == 0) throw new IOException("empty image");
        String path = myId + "/nova/" + UUID.randomUUID() + ".jpg";
        RequestBody body = RequestBody.create(MediaType.parse("image/jpeg"), fileBytes);
        Response<Void> upload = storageApi.uploadFile("posts", path, body).execute();
        if (!upload.isSuccessful()) throw new IOException("upload HTTP " + upload.code());
        String base = BuildConfig.SUPABASE_URL.endsWith("/")
                ? BuildConfig.SUPABASE_URL
                : BuildConfig.SUPABASE_URL + "/";
        String publicUrl = MediaUrl.normalize("posts",
                base + "storage/v1/object/public/posts/" + path);
        Request head = new Request.Builder().url(publicUrl).head().build();
        try (okhttp3.Response probe = okHttpClient.newCall(head).execute()) {
            if (!probe.isSuccessful()) {
                throw new IOException("public URL not ready: HTTP " + probe.code());
            }
        }
        return publicUrl;
    }

    private LiveData<Resource<String>> uploadToPath(String bucket, byte[] fileBytes, String path, String mimeType) {
        MutableLiveData<Resource<String>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));

        if (fileBytes == null || fileBytes.length == 0) {
            String err = "Upload failed: image byte array is empty";
            Log.e(TAG, err);
            result.setValue(Resource.error(err, null));
            return result;
        }

        String mediaTypeStr = mimeType != null ? mimeType : "image/jpeg";
        RequestBody requestBody = RequestBody.create(MediaType.parse(mediaTypeStr), fileBytes);

        Log.d(TAG, "uploaded " + fileBytes.length + " bytes to " + bucket + "/" + path);

        storageApi.uploadFile(bucket, path, requestBody).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    String base = BuildConfig.SUPABASE_URL.endsWith("/")
                            ? BuildConfig.SUPABASE_URL
                            : BuildConfig.SUPABASE_URL + "/";
                    String publicUrl = MediaUrl.normalize(bucket,
                            base + "storage/v1/object/public/" + bucket + "/" + path);
                    Log.d(TAG, "Storage upload successful HTTP " + response.code() + ": " + publicUrl + ". Verifying via HEAD...");

                    Request headReq = new Request.Builder().url(publicUrl).head().build();
                    okHttpClient.newCall(headReq).enqueue(new okhttp3.Callback() {
                        @Override
                        public void onResponse(@NonNull okhttp3.Call c, @NonNull okhttp3.Response headResponse) {
                            int headCode = headResponse.code();
                            String contentType = headResponse.header("Content-Type");
                            String lenStr = headResponse.header("Content-Length");
                            long contentLength = -1;
                            try {
                                if (lenStr != null) contentLength = Long.parseLong(lenStr);
                            } catch (NumberFormatException ignored) {
                            }
                            headResponse.close();

                            Log.d(TAG, "HEAD status=" + headCode + " Content-Type=" + contentType
                                    + " Content-Length=" + contentLength + " expected=" + fileBytes.length
                                    + " for " + publicUrl);

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
                    } catch (Exception ignored) {
                    }
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
            public void onFailure(Call<Void> call, Throwable t) {
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
