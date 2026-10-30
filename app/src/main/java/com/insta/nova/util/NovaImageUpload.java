package com.insta.nova.util;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.insta.nova.data.remote.RetrofitClient;
import com.insta.nova.data.remote.api.StorageApi;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.UUID;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Uploads one picked image into a storage bucket and hands back the URL the app stores.
 *
 * <p>Nova surfaces (Lost &amp; Found, SOS) keep a single optional photo, so this stays a
 * one-shot helper: read the bytes off the main thread, PUT them through the existing
 * authenticated StorageApi, then normalise the public path to the private one the
 * interceptor expects. Nothing is cached and nothing is retried automatically — a failed
 * upload leaves the record without a photo instead of blocking the report.
 */
public final class NovaImageUpload {

    public interface UploadCallback {
        /** Null-safe: {@code url} is non-empty and already normalised for this bucket. */
        void onUploaded(String url);

        /** Called for every failure; the caller decides whether to continue without a photo. */
        void onFailed(String message);
    }

    private NovaImageUpload() {
    }

    public static void upload(Context context, Uri uri, String bucket, UploadCallback callback) {
        if (context == null || uri == null || bucket == null) {
            if (callback != null) callback.onFailed("No image selected");
            return;
        }
        final Context appContext = context.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        final String path = "nova/" + UUID.randomUUID().toString().replace("-", "") + ".jpg";

        new Thread(() -> {
            byte[] bytes;
            try (InputStream in = appContext.getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IllegalStateException("Unreadable image");
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                bytes = out.toByteArray();
            } catch (Exception e) {
                String message = e.getMessage() == null ? "Could not read the image" : e.getMessage();
                main.post(() -> {
                    if (callback != null) callback.onFailed(message);
                });
                return;
            }

            RequestBody body = RequestBody.create(bytes, MediaType.parse("image/jpeg"));
            StorageApi api = RetrofitClient.getInstance(appContext).getStorageApi();
            api.uploadFile(bucket, path, body).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    if (!response.isSuccessful()) {
                        if (callback != null) callback.onFailed("HTTP " + response.code());
                        return;
                    }
                    String base = com.insta.nova.BuildConfig.SUPABASE_URL;
                    if (!base.endsWith("/")) base = base + "/";
                    String url = MediaUrl.normalize(bucket,
                            base + "storage/v1/object/public/" + bucket + "/" + path);
                    if (callback != null) callback.onUploaded(url);
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    if (callback != null) callback.onFailed(t.getMessage() == null ? "Upload failed" : t.getMessage());
                }
            });
        }).start();
    }
}
