package com.insta.clone.data.remote.api;

import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.DELETE;
import retrofit2.http.Path;

/**
 * Retrofit endpoints for Supabase Storage service (/storage/v1).
 */
public interface StorageApi {

    @POST("storage/v1/object/{bucket}/{path}")
    Call<Void> uploadFile(
            @Path("bucket") String bucket,
            @Path(value = "path", encoded = true) String path,
            @Body RequestBody fileBody
    );

    @DELETE("storage/v1/object/{bucket}/{path}")
    Call<Void> deleteFile(
            @Path("bucket") String bucket,
            @Path(value = "path", encoded = true) String path
    );
}
