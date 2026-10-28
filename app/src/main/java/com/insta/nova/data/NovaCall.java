package com.insta.nova.data;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.insta.nova.util.Resource;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Shared enqueue + error-code plumbing for the Nova repositories. */
public final class NovaCall {

    private static final String TAG = "NovaRepository";

    private NovaCall() {
    }

    public static <T> LiveData<Resource<T>> run(Call<T> call, String label) {
        MutableLiveData<Resource<T>> out = new MutableLiveData<>(Resource.loading());
        call.enqueue(new Callback<T>() {
            @Override
            public void onResponse(Call<T> c, Response<T> response) {
                if (response.isSuccessful()) {
                    out.postValue(Resource.success(response.body()));
                } else {
                    String code = NovaErrors.code(response);
                    Log.w(TAG, label + " failed: " + code);
                    out.postValue(Resource.error(code));
                }
            }

            @Override
            public void onFailure(Call<T> c, Throwable t) {
                Log.w(TAG, label + " network failure: " + t.getMessage());
                out.postValue(Resource.error("network"));
            }
        });
        return out;
    }

    public static LiveData<Resource<Boolean>> action(Call<Void> call, String label) {
        MutableLiveData<Resource<Boolean>> out = new MutableLiveData<>(Resource.loading());
        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> c, Response<Void> response) {
                if (response.isSuccessful()) {
                    out.postValue(Resource.success(true));
                } else {
                    String code = NovaErrors.code(response);
                    Log.w(TAG, label + " failed: " + code);
                    out.postValue(Resource.error(code));
                }
            }

            @Override
            public void onFailure(Call<Void> c, Throwable t) {
                Log.w(TAG, label + " network failure: " + t.getMessage());
                out.postValue(Resource.error("network"));
            }
        });
        return out;
    }
}
