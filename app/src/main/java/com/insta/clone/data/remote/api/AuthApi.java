package com.insta.clone.data.remote.api;

import com.insta.clone.data.remote.dto.AuthRequest;
import com.insta.clone.data.remote.dto.AuthResponse;
import com.insta.clone.data.remote.dto.TokenResponse;
import java.util.Map;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.GET;

/**
 * Retrofit endpoints for Supabase GoTrue Authentication service (/auth/v1).
 */
public interface AuthApi {

    @GET("auth/v1/user")
    Call<TokenResponse.AuthUser> getCurrentUser();

    @POST("auth/v1/signup")
    Call<AuthResponse> signUp(@Body AuthRequest request);

    @POST("auth/v1/token?grant_type=password")
    Call<TokenResponse> signInWithPassword(@Body AuthRequest request);

    @POST("auth/v1/token?grant_type=refresh_token")
    Call<TokenResponse> refreshToken(@Body Map<String, String> request);

    @POST("auth/v1/recover")
    Call<Void> recoverPassword(@Body Map<String, String> request);

    @PUT("auth/v1/user")
    Call<Void> updateUser(@Body Map<String, Object> request);
}
