package com.insta.nova.data;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.insta.nova.data.remote.RetrofitClient;
import com.insta.nova.util.Resource;

import java.util.HashMap;
import java.util.Map;

/** Aggregated growth data for Nova Insights. */
public class InsightsRepository {

    private final NovaApi api;

    public InsightsRepository(Context context) {
        api = RetrofitClient.getInstance(context.getApplicationContext()).getNovaApi();
    }

    public LiveData<Resource<NovaDtos.Insights>> load(int days) {
        Map<String, Object> body = new HashMap<>();
        body.put("p_days", Math.max(7, Math.min(90, days)));
        return NovaCall.run(api.myInsights(body), "insights");
    }
}
