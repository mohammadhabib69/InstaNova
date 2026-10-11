package com.insta.clone.data.remote;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import com.insta.clone.BuildConfig;
import com.insta.clone.data.local.SessionManager;
import com.insta.clone.data.remote.api.AuthApi;
import com.insta.clone.data.remote.api.RestApi;
import com.insta.clone.data.remote.api.StorageApi;
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

    private final AuthApi authApi;
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

        SessionManager sessionManager = SessionManager.getInstance(context);

        if (baseUrl.isEmpty() || BuildConfig.SUPABASE_ANON_KEY == null
                || BuildConfig.SUPABASE_ANON_KEY.trim().isEmpty()) {
            throw new IllegalStateException("Supabase URL and key are required. Configure local.properties.");
        }

        // Read the current session for every request. This is intentionally not cached.
        Interceptor authInterceptor = new AuthInterceptor(sessionManager);

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
                .addInterceptor(errorInterceptor)
                .authenticator(new TokenAuthenticator(context));

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

        this.authApi = retrofit.create(AuthApi.class);
        this.restApi = retrofit.create(RestApi.class);
        this.storageApi = retrofit.create(StorageApi.class);
    }

    public AuthApi getAuthApi() { return authApi; }
    public RestApi getRestApi() { return restApi; }
    public StorageApi getStorageApi() { return storageApi; }
    public OkHttpClient getOkHttpClient() { return okHttpClient; }
}
