package com.insta.nova.data.remote;

import androidx.annotation.NonNull;

import com.insta.nova.BuildConfig;
import com.insta.nova.util.MediaUrl;

import java.io.IOException;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Points every object request at a private bucket to the RLS-checked endpoint.
 *
 * <p>Rows written before the buckets were made private still hold
 * {@code /storage/v1/object/public/<bucket>/<path>} URLs, and upload helpers keep
 * building them. A public-bucket URL is served without a token and is rejected outright
 * once the bucket is private, so the rewrite belongs at the transport layer rather than
 * in 90-odd call sites: this interceptor sits on the one OkHttp client that both Retrofit
 * and Glide use, which also covers the HEAD probe each upload runs before it writes a row.
 *
 * <p>It runs before {@link AuthInterceptor}, so the rewritten request still gets the
 * {@code apikey} and bearer token the private endpoint requires.
 */
public class StoragePrivacyInterceptor implements Interceptor {

    private static final String PUBLIC_PATH = "/storage/v1/object/public/";
    private static final String AUTH_PATH = "/storage/v1/object/authenticated/";

    private final String storageHost;

    public StoragePrivacyInterceptor() {
        HttpUrl base = HttpUrl.parse(BuildConfig.SUPABASE_URL == null ? "" : BuildConfig.SUPABASE_URL.trim());
        this.storageHost = base == null ? "" : base.host();
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        HttpUrl url = request.url();

        HttpUrl rewritten = rewrite(url);
        if (rewritten != null) {
            request = request.newBuilder().url(rewritten).build();
        }
        return chain.proceed(request);
    }

    /** @return the redirected URL, or {@code null} when the request is already correct. */
    private HttpUrl rewrite(HttpUrl url) {
        if (storageHost.isEmpty() || !storageHost.equalsIgnoreCase(url.host())) return null;
        String path = url.encodedPath();
        if (!path.startsWith(PUBLIC_PATH)) return null;

        String rest = path.substring(PUBLIC_PATH.length());
        int slash = rest.indexOf('/');
        if (slash <= 0) return null;
        if (!MediaUrl.isPrivate(rest.substring(0, slash))) return null;

        return url.newBuilder().encodedPath(AUTH_PATH + rest).build();
    }
}
