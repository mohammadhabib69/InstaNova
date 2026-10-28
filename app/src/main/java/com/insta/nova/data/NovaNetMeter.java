package com.insta.nova.data;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.insta.nova.data.entity.NetUsage;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.ForwardingSource;
import okio.Okio;

/**
 * Counts the bytes actually transferred by every OkHttp response (REST and images),
 * and writes one Room row per response. Categories come from the response
 * Content-Type/URL. Saver-downscaled images record an estimated full-size equivalent
 * (bytes / scale^2) so the Lite benchmark can show a real percentage.
 */
public final class NovaNetMeter {

    private static final String TAG = "NovaNetMeter";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static Context appContext;

    private NovaNetMeter() {
    }

    public static void install(Context context) {
        appContext = context.getApplicationContext();
    }

    public static Interceptor interceptor() {
        return chain -> {
            Response response = chain.proceed(chain.request());
            String contentType = "";
            MediaType type = response.body() != null ? response.body().contentType() : null;
            if (type != null) contentType = type.type() + "/" + type.subtype();
            String category = categorize(contentType, response.request().url().encodedPath());
            long len = response.body() != null ? response.body().contentLength() : -1;
            ResponseBody body = new CountingBody(response.body(), category, len);
            return response.newBuilder().body(body).build();
        };
    }

    public static OkHttpClient.Builder attach(OkHttpClient.Builder builder) {
        return builder.addInterceptor(interceptor());
    }

    private static String categorize(String contentType, String path) {
        if (contentType.startsWith("image/")) return "image";
        if (contentType.startsWith("video/")) return "video";
        if (path.contains("/rest/v1/") || path.contains("/auth/v1/") || path.contains("/functions/v1/")) {
            return "api";
        }
        if (path.contains("/storage/v1/object/")) return "image";
        return "other";
    }

    /**
     * When a write failed because the device is out of space, telemetry stops trying
     * for a while.
     *
     * <p>This is the only writer that runs on every request, and the call poll fires
     * roughly every 2.5 s, so a full disk used to produce a failed insert — and an
     * `E SQLiteDatabase: SQLiteFullException` line — at that rate for as long as space
     * stayed short. Nothing user-authored is lost when this is skipped: these rows only
     * feed the data-usage screen.
     */
    private static volatile long pausedUntilMs = 0L;

    /** How long to stop logging after a write fails for lack of space. */
    private static final long FULL_DISK_BACKOFF_MS = 60_000L;

    static void record(String category, long bytes, long contentLength) {
        if (appContext == null || bytes <= 0) return;
        if (System.currentTimeMillis() < pausedUntilMs) return;
        Context ctx = appContext;
        boolean saver = NovaDataSaver.peekActive(ctx);
        long estFull = bytes;
        if (saver && "image".equals(category)) {
            estFull = bytes * 4L; // decode factor 2 => four times the pixels
        }
        final long estimated = estFull;
        NetUsage usage = new NetUsage();
        usage.day = today();
        usage.category = category;
        usage.bytes = bytes;
        usage.saverActive = saver;
        usage.estFullBytes = estimated;
        usage.createdAt = System.currentTimeMillis();
        IO.execute(() -> {
            try {
                NovaDb.get(ctx).netUsageDao().insert(usage);
            } catch (android.database.sqlite.SQLiteFullException full) {
                pausedUntilMs = System.currentTimeMillis() + FULL_DISK_BACKOFF_MS;
                Log.w(TAG, "Storage full - pausing network usage logging for 60s");
            } catch (RuntimeException e) {
                Log.w(TAG, "Could not persist network usage", e);
            }
        });
    }

    public static String today() {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        fmt.setTimeZone(TimeZone.getTimeZone("Asia/Dhaka"));
        return fmt.format(new Date());
    }

    private static final class CountingBody extends ResponseBody {
        private final ResponseBody delegate;
        private final String category;
        private final long declaredLength;
        private BufferedSource source;
        private long read;
        private boolean reported;

        CountingBody(ResponseBody delegate, String category, long declaredLength) {
            this.delegate = delegate;
            this.category = category;
            this.declaredLength = declaredLength;
        }

        @Override
        public MediaType contentType() {
            return delegate != null ? delegate.contentType() : null;
        }

        @Override
        public long contentLength() {
            return delegate != null ? delegate.contentLength() : 0;
        }

        @NonNull
        @Override
        public BufferedSource source() {
            if (source == null) {
                BufferedSource upstream = delegate != null ? delegate.source() : new Buffer();
                source = Okio.buffer(new ForwardingSource(upstream) {
                    @Override
                    public long read(@NonNull Buffer sink, long byteCount) throws IOException {
                        long n = super.read(sink, byteCount);
                        if (n > 0) read += n;
                        return n;
                    }

                    @Override
                    public void close() throws IOException {
                        try {
                            super.close();
                        } finally {
                            report();
                        }
                    }
                });
            }
            return source;
        }

        private void report() {
            if (reported) return;
            reported = true;
            record(category, read > 0 ? read : declaredLength, declaredLength);
        }
    }
}
