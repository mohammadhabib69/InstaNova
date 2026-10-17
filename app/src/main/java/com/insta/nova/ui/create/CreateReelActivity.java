package com.insta.nova.ui.create;

import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.insta.nova.BuildConfig;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.remote.RetrofitClient;
import com.insta.nova.data.remote.api.RestApi;
import com.insta.nova.data.remote.api.StorageApi;
import com.insta.nova.data.remote.dto.PostDto;
import com.insta.nova.databinding.ActivityCreateReelBinding;
import com.insta.nova.util.ImageUtils;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.BufferedSink;
import okio.Okio;
import okio.Source;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Creates reels by selecting an MP4 video (max 60s, max 45MB),
 * extracting cover thumbnail, uploading to 'reels' and 'posts' storage buckets,
 * and saving the post record with is_reel = true.
 */
public class CreateReelActivity extends AppCompatActivity {

    private static final String TAG_API = "InstaApi";
    private static final long MAX_DURATION_MS = 60_000L;
    private static final long MAX_FILE_SIZE_BYTES = 45 * 1024 * 1024L;

    private ActivityCreateReelBinding binding;
    private Uri selectedVideoUri;
    private Bitmap coverBitmap;
    private byte[] coverJpegBytes;
    private String uploadedVideoPath;
    private String uploadedCoverPath;
    private StorageApi storageApi;
    private RestApi restApi;
    private String currentUserId;

    private ActivityResultLauncher<PickVisualMediaRequest> pickVideoLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateReelBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        currentUserId = SessionManager.getInstance(this).getUserId();
        RetrofitClient client = RetrofitClient.getInstance(this);
        storageApi = client.getStorageApi();
        restApi = client.getRestApi();

        pickVideoLauncher = registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(),
                uri -> {
                    if (uri != null) {
                        processSelectedVideo(uri);
                    }
                }
        );

        binding.btnPickVideo.setOnClickListener(v -> {
            pickVideoLauncher.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.VideoOnly.INSTANCE)
                    .build());
        });

        binding.btnPublish.setOnClickListener(v -> publishReel());
    }

    private void processSelectedVideo(Uri uri) {
        long fileSize = getFileSize(uri);
        if (fileSize > MAX_FILE_SIZE_BYTES) {
            toast("Video is too large (" + (fileSize / (1024 * 1024)) + " MB). Maximum allowed is 45 MB.");
            return;
        }

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(this, uri);
            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            long durationMs = durationStr != null ? Long.parseLong(durationStr) : 0;
            if (durationMs > MAX_DURATION_MS + 1000) {
                toast("Video is longer than 60 seconds (" + (durationMs / 1000) + "s). Maximum allowed is 60s.");
                return;
            }

            coverBitmap = retriever.getFrameAtTime(0);
            if (coverBitmap != null) {
                coverJpegBytes = ImageUtils.compressImage(coverBitmap);
                binding.ivCoverPreview.setVisibility(View.VISIBLE);
                binding.ivCoverPreview.setImageBitmap(coverBitmap);
            }

            selectedVideoUri = uri;
            binding.tvVideoInfo.setVisibility(View.VISIBLE);
            binding.tvVideoInfo.setText("Duration: " + (durationMs / 1000) + "s | Size: " + (fileSize / (1024 * 1024)) + " MB");
            binding.btnPickVideo.setText("Video chosen (Change)");
            binding.btnPublish.setEnabled(true);
        } catch (Exception e) {
            Log.e(TAG_API, "Failed to read video metadata: " + e.getMessage(), e);
            toast("Could not read video metadata: " + e.getMessage());
        } finally {
            try { retriever.release(); } catch (Exception ignored) {}
        }
    }

    private long getFileSize(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex != -1) return cursor.getLong(sizeIndex);
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private void publishReel() {
        if (selectedVideoUri == null || currentUserId == null) {
            toast("Please choose a video after signing in.");
            return;
        }

        setLoading(true);
        uploadedCoverPath = currentUserId + "/" + UUID.randomUUID() + ".jpg";
        byte[] coverBytes = (coverJpegBytes != null && coverJpegBytes.length > 0)
                ? coverJpegBytes
                : new byte[0];

        // 1. Upload Cover JPG to 'posts' bucket
        RequestBody coverBody = RequestBody.create(MediaType.parse("image/jpeg"), coverBytes);
        storageApi.uploadFile("posts", uploadedCoverPath, coverBody).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (!response.isSuccessful()) {
                    String err = parseError(response);
                    Log.e(TAG_API, "Failed uploading reel cover (HTTP " + response.code() + "): " + err);
                    fail("Failed uploading cover: " + err);
                    return;
                }

                Log.d(TAG_API, "Uploaded reel cover to posts/" + uploadedCoverPath);
                uploadReelVideo();
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e(TAG_API, "Network failure uploading reel cover: " + t.getMessage(), t);
                fail("Network failure uploading cover: " + t.getMessage());
            }
        });
    }

    private void uploadReelVideo() {
        uploadedVideoPath = currentUserId + "/" + UUID.randomUUID() + ".mp4";

        RequestBody videoBody = new RequestBody() {
            @Override
            public MediaType contentType() {
                return MediaType.parse("video/mp4");
            }

            @Override
            public void writeTo(BufferedSink sink) throws java.io.IOException {
                try (InputStream in = getContentResolver().openInputStream(selectedVideoUri);
                     Source source = Okio.source(in)) {
                    sink.writeAll(source);
                }
            }
        };

        storageApi.uploadFile("reels", uploadedVideoPath, videoBody).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (!response.isSuccessful()) {
                    String err = parseError(response);
                    Log.e(TAG_API, "Failed uploading reel MP4 (HTTP " + response.code() + "): " + err);
                    fail("Failed uploading video: " + err);
                    return;
                }

                Log.d(TAG_API, "Uploaded reel video to reels/" + uploadedVideoPath);
                createReelRecord();
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e(TAG_API, "Network failure uploading reel video: " + t.getMessage(), t);
                fail("Network failure uploading video: " + t.getMessage());
            }
        });
    }

    private void createReelRecord() {
        String coverUrl = getPublicUrl("posts", uploadedCoverPath);
        String videoUrl = getPublicUrl("reels", uploadedVideoPath);
        String caption = binding.etCaption.getText().toString().trim();

        Map<String, Object> body = new HashMap<>();
        body.put("user_id", currentUserId);
        body.put("caption", caption);
        body.put("is_reel", true);
        body.put("video_url", videoUrl);
        body.put("thumbnail_url", coverUrl);

        restApi.createPost(body).enqueue(new Callback<List<PostDto>>() {
            @Override
            public void onResponse(Call<List<PostDto>> call, Response<List<PostDto>> response) {
                if (!response.isSuccessful()) {
                    String err = parseError(response);
                    Log.e(TAG_API, "HTTP " + response.code() + " on create Reel: " + err);
                    fail("Failed creating reel (HTTP " + response.code() + "): " + err);
                    return;
                }

                Log.d(TAG_API, "Reel created successfully!");
                setLoading(false);
                toast("Reel shared!");
                setResult(RESULT_OK);
                finish();
            }

            @Override
            public void onFailure(Call<List<PostDto>> call, Throwable t) {
                Log.e(TAG_API, "Network failure creating reel: " + t.getMessage(), t);
                fail("Network failure creating reel: " + t.getMessage());
            }
        });
    }

    private String getPublicUrl(String bucket, String path) {
        String base = BuildConfig.SUPABASE_URL.trim();
        if (!base.endsWith("/")) base += "/";
        return base + "storage/v1/object/public/" + bucket + "/" + path;
    }

    private void fail(String message) {
        setLoading(false);
        if (uploadedCoverPath != null) {
            storageApi.deleteFile("posts", uploadedCoverPath).enqueue(new Callback<Void>() {
                @Override public void onResponse(Call<Void> c, Response<Void> r) {}
                @Override public void onFailure(Call<Void> c, Throwable t) {}
            });
            uploadedCoverPath = null;
        }
        if (uploadedVideoPath != null) {
            storageApi.deleteFile("reels", uploadedVideoPath).enqueue(new Callback<Void>() {
                @Override public void onResponse(Call<Void> c, Response<Void> r) {}
                @Override public void onFailure(Call<Void> c, Throwable t) {}
            });
            uploadedVideoPath = null;
        }
        toast(message);
    }

    private void setLoading(boolean loading) {
        binding.progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnPublish.setEnabled(!loading && selectedVideoUri != null);
        binding.btnPickVideo.setEnabled(!loading);
    }

    private String parseError(Response<?> response) {
        try {
            if (response.errorBody() != null) return response.errorBody().string();
        } catch (Exception ignored) {}
        return "Unknown error";
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}
