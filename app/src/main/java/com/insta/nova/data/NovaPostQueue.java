package com.insta.nova.data;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.insta.nova.data.entity.QueuedPost;
import com.insta.nova.data.work.PostQueueWorker;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The offline outbox. Posts captured without a connection are stored with their local image
 * paths and replayed by {@link PostQueueWorker} once the device is online again.
 */
public final class NovaPostQueue {

    /** Room refuses main-thread access, and this is called straight from a click. */
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private NovaPostQueue() {
    }

    public static void enqueue(Context context, String caption, String location, List<String> paths) {
        Context app = context.getApplicationContext();
        List<String> copy = new java.util.ArrayList<>(paths);
        String text = caption == null ? "" : caption;
        String place = location == null ? "" : location;
        IO.execute(() -> {
            try {
                copyIntoPlace(app, copy);
                QueuedPost post = new QueuedPost();
                post.id = UUID.randomUUID().toString();
                post.caption = text;
                post.location = place;
                post.imagePaths = String.join("\n", copy);
                post.status = "queued";
                post.attempts = 0;
                post.createdAt = System.currentTimeMillis();
                post.lastError = "";
                NovaDb.get(app).postQueueDao().enqueue(post);
                schedule(app);
            } catch (RuntimeException e) {
                android.util.Log.w("NovaPostQueue", "could not queue the post", e);
            }
        });
    }

    /**
     * A content:// URI is only readable while the granting activity lives, so the bytes are
     * copied into app storage before the row is written. The worker can then open plain files.
     */
    private static void copyIntoPlace(Context context, List<String> paths) {
        File dir = new File(context.getFilesDir(), "nova_queue");
        if (!dir.exists() && !dir.mkdirs()) return;
        for (int i = 0; i < paths.size(); i++) {
            String value = paths.get(i);
            if (value == null || !value.startsWith("content:")) continue;
            File target = new File(dir, UUID.randomUUID().toString() + ".jpg");
            try (java.io.InputStream in = context.getContentResolver()
                    .openInputStream(android.net.Uri.parse(value));
                 java.io.FileOutputStream out = new java.io.FileOutputStream(target)) {
                if (in == null) continue;
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                }
                paths.set(i, target.getAbsolutePath());
            } catch (IOException ignored) {
                // A failed copy leaves the URI in place; the worker will report it as failed.
            }
        }
    }


    /**
     * Writes the post to the queue *before* the upload starts and returns the row id.
     *
     * <p>The queue row used to be written only from the failure callback, so a process death in
     * the middle of an upload left nothing behind: no row, no error, and a post the user believed
     * they had shared. The row is marked {@code uploading}, which {@code PostQueueDao.next()}
     * skips, so the worker cannot race the direct upload. {@link #complete} removes it on success
     * and {@link #reclaimStuck} brings it back on the next launch if the app died.
     */
    public static String enqueueUpload(Context context, String caption, String location, List<String> paths) {
        Context app = context.getApplicationContext();
        List<String> copy = new java.util.ArrayList<>(paths);
        String text = caption == null ? "" : caption;
        String place = location == null ? "" : location;
        String id = UUID.randomUUID().toString();
        IO.execute(() -> {
            try {
                copyIntoPlace(app, copy);
                QueuedPost post = new QueuedPost();
                post.id = id;
                post.caption = text;
                post.location = place;
                post.imagePaths = String.join("\n", copy);
                post.status = "uploading";
                post.attempts = 0;
                post.createdAt = System.currentTimeMillis();
                post.lastError = "";
                NovaDb.get(app).postQueueDao().enqueue(post);
            } catch (RuntimeException e) {
                android.util.Log.w("NovaPostQueue", "could not write the upload row", e);
            }
        });
        return id;
    }

    /** Removes the write-ahead row once the server has the post. */
    public static void complete(Context context, String id) {
        if (id == null) return;
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            try {
                NovaDb.get(app).postQueueDao().delete(id);
            } catch (RuntimeException e) {
                android.util.Log.w("NovaPostQueue", "could not clear the upload row", e);
            }
        });
    }

    /**
     * Returns rows stuck in {@code uploading} to the queue and schedules the worker.
     *
     * <p>Called once per app start: a row can only be left in {@code uploading} by a process that
     * died mid-upload, so this is exactly the case the write-ahead row exists for.
     */
    public static void reclaimStuck(Context context) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            try {
                NovaDb.get(app).postQueueDao().resetStuck();
                schedule(app);
            } catch (RuntimeException e) {
                android.util.Log.w("NovaPostQueue", "could not reclaim stuck uploads", e);
            }
        });
    }

    public static void schedule(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(PostQueueWorker.class)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(PostQueueWorker.TAG)
                .build();
        // KEEP could strand the queue behind a finished/failed chain, so append instead while
        // still keeping the replays serialised.
        WorkManager.getInstance(context)
                .enqueueUniqueWork(PostQueueWorker.TAG, ExistingWorkPolicy.APPEND_OR_REPLACE, request);
    }

    public static LiveData<List<QueuedPost>> observe(Context context) {
        return NovaDb.get(context).postQueueDao().observeAll();
    }

    public static int count(Context context) {
        return NovaDb.get(context).postQueueDao().count();
    }
}
