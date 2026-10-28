package com.insta.nova.data;

import android.content.Context;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.module.AppGlideModule;
import com.insta.nova.data.remote.RetrofitClient;

import java.io.InputStream;

/**
 * Routes Glide through the app's single OkHttp client so the Nova byte meter sees
 * image traffic too (and so image requests share the same connection pool and headers).
 */
@GlideModule
public class NovaGlideModule extends AppGlideModule {

    @Override
    public void registerComponents(@NonNull Context context, @NonNull Glide glide,
                                   @NonNull Registry registry) {
        registry.replace(GlideUrl.class, InputStream.class,
                new OkHttpUrlLoader.Factory(RetrofitClient.getInstance(context).getOkHttpClient()));
    }
}
