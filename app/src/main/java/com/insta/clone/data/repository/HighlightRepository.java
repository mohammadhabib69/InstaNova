package com.insta.clone.data.repository;

import android.content.Context;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.google.gson.Gson;
import com.insta.clone.data.local.SessionManager;
import com.insta.clone.data.model.Highlight;
import com.insta.clone.data.model.HighlightItem;
import com.insta.clone.data.remote.RetrofitClient;
import com.insta.clone.data.remote.api.RestApi;
import com.insta.clone.util.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Repository for story highlights creation, management, and items retrieval.
 */
public class HighlightRepository {

    private final RestApi restApi;
    private final SessionManager sessionManager;
    private final Gson gson = new Gson();

    public HighlightRepository(Context context) {
        RetrofitClient client = RetrofitClient.getInstance(context);
        this.restApi = client.getRestApi();
        this.sessionManager = SessionManager.getInstance(context);
    }

    public LiveData<Resource<List<Highlight>>> getUserHighlights(String userId) {
        MutableLiveData<Resource<List<Highlight>>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));

        Map<String, String> query = new HashMap<>();
        query.put("user_id", "eq." + userId);
        query.put("order", "created_at.desc");

        restApi.getHighlights(query).enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> call, Response<List<Map<String, Object>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Highlight> highlights = new ArrayList<>();
                    for (Map<String, Object> map : response.body()) {
                        String id = (String) map.get("id");
                        String uId = (String) map.get("user_id");
                        String title = (String) map.get("title");
                        String cover = (String) map.get("cover_url");
                        highlights.add(new Highlight(id, uId, title, cover));
                    }
                    result.postValue(Resource.success(highlights));
                } else {
                    result.postValue(Resource.error("Failed to load highlights", null));
                }
            }

            @Override
            public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                result.postValue(Resource.error(t.getMessage() != null ? t.getMessage() : "Network error", null));
            }
        });

        return result;
    }

    public LiveData<Resource<Highlight>> createHighlight(String title, String coverUrl, List<String> mediaUrls) {
        MutableLiveData<Resource<Highlight>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));

        String myId = sessionManager.getUserId();
        if (myId == null) {
            result.setValue(Resource.error("Not authenticated", null));
            return result;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("user_id", myId);
        body.put("title", title != null ? title.trim() : "Highlights");
        body.put("cover_url", coverUrl != null ? coverUrl : (mediaUrls != null && !mediaUrls.isEmpty() ? mediaUrls.get(0) : null));

        restApi.createHighlight(body).enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> call, Response<List<Map<String, Object>>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    Map<String, Object> map = response.body().get(0);
                    String hId = (String) map.get("id");
                    Highlight highlight = new Highlight(hId, myId, (String) map.get("title"), (String) map.get("cover_url"));

                    if (mediaUrls != null && !mediaUrls.isEmpty()) {
                        List<Map<String, Object>> items = new ArrayList<>();
                        for (String url : mediaUrls) {
                            Map<String, Object> item = new HashMap<>();
                            item.put("highlight_id", hId);
                            item.put("media_url", url);
                            items.add(item);
                        }
                        restApi.createHighlightItems(items).enqueue(new Callback<Void>() {
                            @Override public void onResponse(Call<Void> c, Response<Void> r) {}
                            @Override public void onFailure(Call<Void> c, Throwable t) {}
                        });
                    }
                    result.postValue(Resource.success(highlight));
                } else {
                    result.postValue(Resource.error("Failed to create highlight", null));
                }
            }

            @Override
            public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                result.postValue(Resource.error(t.getMessage() != null ? t.getMessage() : "Network error", null));
            }
        });

        return result;
    }

    public LiveData<Resource<List<HighlightItem>>> getHighlightItems(String highlightId) {
        MutableLiveData<Resource<List<HighlightItem>>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));

        Map<String, String> query = new HashMap<>();
        query.put("highlight_id", "eq." + highlightId);
        query.put("order", "created_at.asc");

        restApi.getHighlightItems(query).enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> call, Response<List<Map<String, Object>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<HighlightItem> items = new ArrayList<>();
                    for (Map<String, Object> map : response.body()) {
                        String id = (String) map.get("id");
                        String hId = (String) map.get("highlight_id");
                        String url = (String) map.get("media_url");
                        items.add(new HighlightItem(id, hId, url));
                    }
                    result.postValue(Resource.success(items));
                } else {
                    result.postValue(Resource.error("Failed to load highlight items", null));
                }
            }

            @Override
            public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                result.postValue(Resource.error(t.getMessage() != null ? t.getMessage() : "Network error", null));
            }
        });

        return result;
    }
}
