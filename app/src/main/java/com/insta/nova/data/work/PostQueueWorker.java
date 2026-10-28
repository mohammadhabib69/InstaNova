package com.insta.nova.data.work;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.insta.nova.data.NovaDb;
import com.insta.nova.data.entity.QueuedPost;
import com.insta.nova.data.repository.StorageRepository;
import com.insta.nova.data.remote.RetrofitClient;
import com.insta.nova.data.remote.api.RestApi;
import com.insta.nova.data.local.SessionManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Response;

/**
 * Replays posts captured while offline, using the same upload and insert path the online
 * composer uses. One row per run keeps the work bounded and resumable.
 */
public class PostQueueWorker extends Worker {

    public static final String TAG = "nova-post-queue";

    public PostQueueWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        NovaDb db = NovaDb.get(context);
        QueuedPost next = db.postQueueDao().next();
        if (next == null) {
            return Result.success();
        }

        String userId = SessionManager.getInstance(context).getUserId();
        if (userId == null || userId.isEmpty()) {
            // Signed out: keep the rows, try again once a session exists.
            db.postQueueDao().mark(next.id, "queued", "signed out");
            return Result.retry();
        }

        db.postQueueDao().mark(next.id, "uploading", "");
        try {
            List<String> urls = upload(next.imagePaths);
            if (urls.isEmpty()) {
                db.postQueueDao().mark(next.id, "failed", "no images");
                return Result.success();
            }
            post(context, userId, next, urls);
            db.postQueueDao().delete(next.id);
            return Result.success();
        } catch (Exception error) {
            String message = error.getMessage() == null ? "unknown" : error.getMessage();
            db.postQueueDao().mark(next.id, "failed", message);
            // Give transient failures another chance; permanent ones keep the error visible.
            return next.attempts >= 3 ? Result.success() : Result.retry();
        }
    }

    private List<String> upload(String paths) throws IOException {
        List<String> urls = new ArrayList<>();
        if (paths == null || paths.isEmpty()) return urls;
        StorageRepository storage = new StorageRepository(getApplicationContext());
        for (String path : paths.split("\n")) {
            File file = new File(path);
            if (!file.exists()) continue;
            urls.add(storage.uploadNovaImageSync(read(file)));
        }
        return urls;
    }

    private void post(Context context, String userId, QueuedPost row, List<String> urls) {
        RestApi api = RetrofitClient.getInstance(context).getRestApi();
        Map<String, Object> body = new HashMap<>();
        body.put("user_id", userId);
        body.put("caption", row.caption == null ? "" : row.caption);
        body.put("thumbnail_url", urls.get(0));
        body.put("is_reel", false);
        try {
            Response<?> response = api.createPost(body).execute();
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code());
            }
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private byte[] read(File file) throws IOException {
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[(int) file.length()];
            int offset = 0;
            while (offset < buffer.length) {
                int read = in.read(buffer, offset, buffer.length - offset);
                if (read <= 0) break;
                offset += read;
            }
            return buffer;
        }
    }
}
