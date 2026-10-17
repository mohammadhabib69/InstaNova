package com.insta.nova.data.remote;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import com.insta.nova.BuildConfig;
import com.insta.nova.data.remote.api.RestApi;
import com.insta.nova.data.remote.api.StorageApi;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import okio.Buffer;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.converter.scalars.ScalarsConverterFactory;

/**
 * Singleton Retrofit client configuring baseUrl, auth headers, token refresh, and diagnostics logging.
 */
public class RetrofitClient {

    private static final String TAG = "InstaApi";
    private static RetrofitClient instance;

    private final RestApi restApi;
    private final StorageApi storageApi;
    private final OkHttpClient okHttpClient;

    public static synchronized RetrofitClient getInstance(Context context) {
        if (instance == null) {
            instance = new RetrofitClient(context.getApplicationContext());
        }
        return instance;
    }

    private RetrofitClient(Context context) {
        // Enforce trailing slash on base URL
        String baseUrl = BuildConfig.SUPABASE_URL.trim();
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }

        if (baseUrl.isEmpty() || BuildConfig.SUPABASE_ANON_KEY == null
                || BuildConfig.SUPABASE_ANON_KEY.trim().isEmpty()) {
            throw new IllegalStateException("Supabase URL and key are required. Configure local.properties.");
        }

        // Read the current session for every request. This is intentionally not cached.
        Interceptor authInterceptor = new AuthInterceptor();

        // Calling fallback interceptor: routes call_sessions & call_signals to local signaling relay if Supabase 404s
        Interceptor callingFallbackInterceptor = chain -> {
            Request request = chain.request();
            String path = request.url().encodedPath();
            boolean isCallingEndpoint = path.contains("call_sessions") || path.contains("call_signals") || path.contains("user_devices");

            Response response = null;
            try {
                response = chain.proceed(request);
            } catch (IOException e) {
                if (!isCallingEndpoint) throw e;
            }

            if (isCallingEndpoint && (response == null || response.code() == 404)) {
                if (response != null) {
                    response.close();
                }
                String[] localHosts = new String[] { "127.0.0.1", "192.168.10.130" };
                for (String host : localHosts) {
                    try {
                        okhttp3.HttpUrl newUrl = request.url().newBuilder()
                                .scheme("http")
                                .host(host)
                                .port(8088)
                                .build();
                        Request localRequest = request.newBuilder()
                                .url(newUrl)
                                .removeHeader("apikey")
                                .removeHeader("Authorization")
                                .build();
                        Response localResponse = chain.proceed(localRequest);
                        if (localResponse.isSuccessful() || localResponse.code() == 204) {
                            return localResponse;
                        }
                        localResponse.close();
                    } catch (Exception ignored) {}
                }
            }

            return response != null ? response : chain.proceed(request);
        };

        // Error Interceptor: log error response body and record in ApiErrorTracker
        Interceptor errorInterceptor = chain -> {
            Request request = chain.request();
            Response response = chain.proceed(request);

            if (!response.isSuccessful()) {
                String errorBodyStr = "";
                ResponseBody body = response.body();
                if (body != null) {
                    try {
                        byte[] bytes = body.bytes();
                        errorBodyStr = new String(bytes, StandardCharsets.UTF_8);
                        // Recreate response with new body since bytes() consumed it
                        response = response.newBuilder()
                                .body(ResponseBody.create(body.contentType(), bytes))
                                .build();
                    } catch (Exception ignored) {}
                }

                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "HTTP " + response.code() + " on " + request.method() + " " + request.url() + " : " + errorBodyStr);
                }

                ApiErrorTracker.recordError(request.method(), request.url().toString(), response.code(), errorBodyStr);
            }

            return response;
        };

        OkHttpClient.Builder okBuilder = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .addInterceptor(authInterceptor)
                .addInterceptor(callingFallbackInterceptor)
                .addInterceptor(errorInterceptor)
                .authenticator(new TokenAuthenticator());

        if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor(message -> {
                // Redact sensitive tokens in logs
                if (message.contains("apikey:") || message.contains("Authorization:")) {
                    Log.d(TAG, "[HEADER REDACTED]");
                } else {
                    Log.d(TAG, message);
                }
            });
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);
            okBuilder.addInterceptor(logging);
        }

        this.okHttpClient = okBuilder.build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(ScalarsConverterFactory.create())
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        this.restApi = retrofit.create(RestApi.class);
        this.storageApi = retrofit.create(StorageApi.class);
    }

    public RestApi getRestApi() { return restApi; }
    public StorageApi getStorageApi() { return storageApi; }
    public OkHttpClient getOkHttpClient() { return okHttpClient; }
}
