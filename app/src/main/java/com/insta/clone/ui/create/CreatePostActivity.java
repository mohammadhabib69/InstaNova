package com.insta.clone.ui.create;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.insta.clone.BuildConfig;
import com.insta.clone.data.local.SessionManager;
import com.insta.clone.data.remote.RetrofitClient;
import com.insta.clone.data.remote.api.RestApi;
import com.insta.clone.data.remote.api.StorageApi;
import com.insta.clone.data.remote.dto.PostDto;
import com.insta.clone.databinding.ActivityCreatePostBinding;
import com.insta.clone.util.Constants;
import com.insta.clone.util.ImageUtils;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Creates posts with single image or multiple images.
 * Uses ActivityResultContracts.PickVisualMedia, compresses image with ImageUtils,
 * uploads to Supabase Storage bucket 'posts', then inserts post and post_media rows.
 */
public class CreatePostActivity extends AppCompatActivity {

    private static final String TAG_API = "InstaApi";
    private static final String TAG_GLIDE = "InstaGlide";

    private ActivityCreatePostBinding binding;
    private final List<Uri> selectedUris = new ArrayList<>();
    private final List<String> uploadedPaths = new ArrayList<>();
    private final List<String> uploadedPublicUrls = new ArrayList<>();
    private StorageApi storageApi;
    private RestApi restApi;
    private okhttp3.OkHttpClient okHttpClient;
    private String currentUserId;

    private ActivityResultLauncher<PickVisualMediaRequest> pickMediaLauncher;
    private ActivityResultLauncher<PickVisualMediaRequest> pickMultipleMediaLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreatePostBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        currentUserId = SessionManager.getInstance(this).getUserId();
        RetrofitClient client = RetrofitClient.getInstance(this);
        storageApi = client.getStorageApi();
        restApi = client.getRestApi();
        okHttpClient = client.getOkHttpClient();

        setupPickers();

        binding.btnPickImage.setOnClickListener(v -> {
            pickMultipleMediaLauncher.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build());
        });

        binding.btnPublish.setOnClickListener(v -> publishPost());
    }

    private void setupPickers() {
        pickMultipleMediaLauncher = registerForActivityResult(
                new ActivityResultContracts.PickMultipleVisualMedia(Constants.MAX_CAROUSEL_IMAGES),
                uris -> {
                    if (uris != null && !uris.isEmpty()) {
                        selectedUris.clear();
                        selectedUris.addAll(uris);
                        onMediaSelected();
                    }
                }
        );

        pickMediaLauncher = registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(),
                uri -> {
                    if (uri != null) {
                        selectedUris.clear();
                        selectedUris.add(uri);
                        onMediaSelected();
                    }
                }
        );
    }

    private void onMediaSelected() {
        if (selectedUris.isEmpty()) return;
        Uri firstUri = selectedUris.get(0);
        binding.ivPreview.setVisibility(View.VISIBLE);
        Glide.with(this)
                .load(firstUri)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        Log.e(TAG_GLIDE, "Failed to load local preview with Glide: " + e);
                        return false;
                    }
                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        return false;
                    }
                })
                .into(binding.ivPreview);

        binding.btnPublish.setEnabled(true);
        if (selectedUris.size() > 1) {
            binding.btnPickImage.setText(selectedUris.size() + " photos selected (Change)");
        } else {
            binding.btnPickImage.setText("Photo selected (Change)");
        }
    }

    private void publishPost() {
        if (selectedUris.isEmpty() || currentUserId == null) {
            toast("Please choose a photo after signing in.");
            return;
        }

        setLoading(true);
        uploadedPaths.clear();
        uploadedPublicUrls.clear();

        uploadMediaSequentially(0);
    }

    private void uploadMediaSequentially(int index) {
        if (index >= selectedUris.size()) {
            // All images uploaded; now create post
            createPostRecord();
            return;
        }

        Uri uri = selectedUris.get(index);
        byte[] jpegBytes;
        try {
            jpegBytes = ImageUtils.processUriToJpegBytes(this, uri);
        } catch (IOException e) {
            Log.e(TAG_API, "Failed to process image: " + e.getMessage(), e);
            fail("Failed to process image: " + e.getMessage());
            return;
        }

        if (jpegBytes == null || jpegBytes.length == 0) {
            fail("Image processing produced empty byte array");
            return;
        }

        String path = currentUserId + "/" + UUID.randomUUID() + ".jpg";
        RequestBody body = RequestBody.create(MediaType.parse("image/jpeg"), jpegBytes);
        Log.d(TAG_API, "uploaded " + jpegBytes.length + " bytes to posts/" + path);

        storageApi.uploadFile("posts", path, body).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (!response.isSuccessful()) {
                    String err = parseError(response);
                    Log.e(TAG_API, "HTTP " + response.code() + " on uploadFile 'posts/" + path + "': " + err);
                    String msg;
                    if (response.code() == 403) msg = "403 Forbidden: Bucket policy error or invalid path.";
                    else if (response.code() == 404) msg = "404 Not Found: Bucket 'posts' does not exist.";
                    else if (response.code() == 401) msg = "401 Unauthorized: Invalid token.";
                    else msg = "Upload failed (HTTP " + response.code() + "): " + err;
                    fail(msg);
                    return;
                }

                String publicUrl = com.insta.clone.util.MediaUrl.normalize("posts", getPublicUrl("posts", path));
                Log.d(TAG_API, "HTTP 200 uploadFile posts/" + path + ". Verifying via HEAD...");

                okhttp3.Request headReq = new okhttp3.Request.Builder()
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

                        Log.d(TAG_API, "HEAD status=" + headCode + " Content-Type=" + contentType
                                + " Content-Length=" + contentLength + " expected=" + jpegBytes.length + " for " + publicUrl);

                        if (headCode == 200) {
                            runOnUiThread(() -> {
                                uploadedPaths.add(path);
                                uploadedPublicUrls.add(publicUrl);
                                verifyUrlWithGlide(publicUrl);
                                uploadMediaSequentially(index + 1);
                            });
                        } else {
                            runOnUiThread(() -> fail("HEAD verification failed (HTTP " + headCode + ") for " + publicUrl));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull okhttp3.Call c, @NonNull IOException e) {
                        Log.e(TAG_API, "HEAD verification failed: " + e.getMessage(), e);
                        runOnUiThread(() -> fail("HEAD verification failed: " + e.getMessage()));
                    }
                });
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e(TAG_API, "Network failure uploading image: " + t.getMessage(), t);
                fail("Network failure uploading image: " + t.getMessage());
            }
        });
    }

    private void verifyUrlWithGlide(String url) {
        Glide.with(getApplicationContext())
                .load(url)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        Log.e(TAG_GLIDE, "Failed to load public image from storage: " + url + " - " + e);
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        Log.d(TAG_GLIDE, "Successfully loaded public image with Glide: " + url);
                        return false;
                    }
                })
                .preload();
    }

    private void createPostRecord() {
        if (uploadedPublicUrls.isEmpty()) {
            fail("No uploaded images found.");
            return;
        }

        String caption = binding.etCaption.getText().toString().trim();
        Map<String, Object> body = new HashMap<>();
        body.put("user_id", currentUserId);
        body.put("caption", caption);
        body.put("thumbnail_url", uploadedPublicUrls.get(0));
        body.put("is_reel", false);

        restApi.createPost(body).enqueue(new Callback<List<PostDto>>() {
            @Override
            public void onResponse(Call<List<PostDto>> call, Response<List<PostDto>> response) {
                if (!response.isSuccessful() || response.body() == null || response.body().isEmpty()) {
                    String err = parseError(response);
                    Log.e(TAG_API, "HTTP " + response.code() + " on createPost: " + err);
                    fail("Failed to create post (HTTP " + response.code() + "): " + err);
                    return;
                }

                String postId = response.body().get(0).getId();
                Log.d(TAG_API, "Created post with ID: " + postId);
                insertPostMedia(postId);
            }

            @Override
            public void onFailure(Call<List<PostDto>> call, Throwable t) {
                Log.e(TAG_API, "Network failure creating post: " + t.getMessage(), t);
                fail("Network error creating post: " + t.getMessage());
            }
        });
    }

    private void insertPostMedia(String postId) {
        List<Map<String, Object>> mediaList = new ArrayList<>();
        for (int i = 0; i < uploadedPublicUrls.size(); i++) {
            Map<String, Object> m = new HashMap<>();
            m.put("post_id", postId);
            m.put("url", uploadedPublicUrls.get(i));
            m.put("position", i);
            mediaList.add(m);
        }

        restApi.insertPostMedia(mediaList).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (!response.isSuccessful()) {
                    String err = parseError(response);
                    Log.e(TAG_API, "HTTP " + response.code() + " on insertPostMedia: " + err);
                    fail("Failed to attach post media (HTTP " + response.code() + "): " + err);
                    return;
                }

                Log.d(TAG_API, "HTTP 200: Post media attached successfully.");
                setLoading(false);
                toast("Post shared!");
                setResult(RESULT_OK);
                finish();
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e(TAG_API, "Network failure attaching media: " + t.getMessage(), t);
                fail("Network error attaching media: " + t.getMessage());
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
        // Clean up leftovers
        for (String p : uploadedPaths) {
            storageApi.deleteFile("posts", p).enqueue(new Callback<Void>() {
                @Override public void onResponse(Call<Void> c, Response<Void> r) {}
                @Override public void onFailure(Call<Void> c, Throwable t) {}
            });
        }
        uploadedPaths.clear();
        uploadedPublicUrls.clear();
        toast(message);
    }

    private void setLoading(boolean loading) {
        binding.progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnPublish.setEnabled(!loading && !selectedUris.isEmpty());
        binding.btnPickImage.setEnabled(!loading);
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
