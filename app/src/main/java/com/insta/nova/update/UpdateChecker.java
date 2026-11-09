package com.insta.nova.update;

import android.os.Build;
import android.util.Log;

import com.insta.nova.BuildConfig;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Reads {@code latest.json} from GitHub Releases and decides whether it is newer.
 *
 * <p>The check is always best-effort and always off the main thread. Any failure —
 * airplane mode, DNS, a 404 because no release exists yet, a timeout, malformed JSON —
 * resolves to {@link Status#FAILED} and the app keeps working exactly as before. Nothing
 * in this class writes to Firebase, Supabase or the local session stores.
 */
public final class UpdateChecker {

    private static final String TAG = "UpdateChecker";
    private static final long CONNECT_TIMEOUT_S = 6L;
    private static final long READ_TIMEOUT_S = 10L;
    private static final long CALL_TIMEOUT_S = 20L;

    public enum Status {
        /** A newer release exists; {@link Result#info} describes it. */
        UPDATE_AVAILABLE,
        /** The installed build is current (or newer, e.g. a locally bumped debug build). */
        UP_TO_DATE,
        /** Could not determine anything. Treated as "no update" by the caller. */
        FAILED
    }

    public static final class Result {
        public final Status status;
        public final UpdateInfo info;
        public final String message;

        private Result(Status status, UpdateInfo info, String message) {
            this.status = status;
            this.info = info;
            this.message = message;
        }

        static Result available(UpdateInfo info) {
            return new Result(Status.UPDATE_AVAILABLE, info, null);
        }

        static Result upToDate() {
            return new Result(Status.UP_TO_DATE, null, null);
        }

        static Result failed(String message) {
            return new Result(Status.FAILED, null, message);
        }
    }

    public interface Callback {
        /** Always invoked on the main thread. */
        void onResult(Result result);
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ota-update-check");
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });

    private final OkHttpClient client;
    private final AtomicBoolean inFlight = new AtomicBoolean(false);

    public UpdateChecker() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
                .callTimeout(CALL_TIMEOUT_S, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
    }

    public boolean isInFlight() {
        return inFlight.get();
    }

    /**
     * Runs one check. A second call while a check is already running is ignored, so a
     * fast sequence of activity resumes can never stack requests.
     */
    public void check(final Callback callback) {
        if (!inFlight.compareAndSet(false, true)) {
            Log.d(TAG, "check already running");
            return;
        }
        EXECUTOR.execute(() -> {
            Result result;
            try {
                result = fetchAndDecide();
            } catch (Throwable t) {
                Log.w(TAG, "update check failed: " + t);
                result = Result.failed(t.getClass().getSimpleName());
            } finally {
                inFlight.set(false);
            }
            final Result delivered = result;
            if (callback != null) {
                UpdateManager.postToMain(() -> callback.onResult(delivered));
            }
        });
    }

    private Result fetchAndDecide() {
        String manifestUrl = BuildConfig.UPDATE_MANIFEST_URL;
        if (manifestUrl == null || manifestUrl.trim().isEmpty()) {
            Log.i(TAG, "no manifest URL configured");
            return Result.failed("manifest URL not configured");
        }

        Request request = new Request.Builder()
                .url(manifestUrl.trim())
                .header("Accept", "application/json")
                .header("Cache-Control", "no-cache")
                .header("User-Agent", "InstaNova/" + BuildConfig.VERSION_NAME
                        + " (Android " + Build.VERSION.SDK_INT + ")")
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                Log.i(TAG, "manifest HTTP " + response.code());
                return Result.failed("HTTP " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) {
                return Result.failed("empty response");
            }
            long declared = body.contentLength();
            if (declared > UpdateManifestParser.MAX_MANIFEST_CHARS) {
                return Result.failed("manifest too large");
            }
            String json = body.string();
            if (json.length() > UpdateManifestParser.MAX_MANIFEST_CHARS) {
                return Result.failed("manifest too large");
            }

            // Debug builds may point at a local HTTP test server; release builds never can.
            UpdateInfo info = UpdateManifestParser.parse(json, BuildConfig.DEBUG);
            if (info.isNewerThan(BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)) {
                Log.i(TAG, "update available: " + info.displayVersionWithCode()
                        + " (installed " + BuildConfig.VERSION_CODE + ")");
                return Result.available(info);
            }
            Log.i(TAG, "up to date: manifest " + info.displayVersionWithCode()
                    + ", installed " + BuildConfig.VERSION_CODE);
            return Result.upToDate();
        } catch (UpdateManifestParser.UpdateParseException e) {
            Log.w(TAG, "manifest rejected: " + e.getMessage());
            return Result.failed("manifest rejected: " + e.getMessage());
        } catch (IOException e) {
            Log.i(TAG, "update check offline: " + e.getMessage());
            return Result.failed("network unavailable");
        }
    }
}
