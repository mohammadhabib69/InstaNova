package com.insta.nova.data.repository;

import android.content.Context;
import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.google.gson.Gson;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.model.Highlight;
import com.insta.nova.data.model.HighlightItem;
import com.insta.nova.data.remote.RetrofitClient;
import com.insta.nova.data.remote.api.RestApi;
import com.insta.nova.util.Resource;
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

    private static final String TAG = "HighlightRepository";

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
                        // The row and its items are two requests. The items callback used to be
                        // empty, so a rejected insert left a highlight that renders on the profile
                        // and opens a viewer with nothing in it — the "dead tile" the account
                        // already carries twice. Report success only once the items are stored,
                        // and take the orphan row back out when they are not.
                        restApi.createHighlightItems(items).enqueue(new Callback<Void>() {
                            @Override
                            public void onResponse(Call<Void> c, Response<Void> r) {
                                if (r.isSuccessful()) {
                                    result.postValue(Resource.success(highlight));
                                } else {
                                    Log.w(TAG, "highlight items rejected: " + r.code());
                                    deleteOrphan(hId);
                                    result.postValue(Resource.error("Could not add the story to the highlight.", null));
                                }
                            }

                            @Override
                            public void onFailure(Call<Void> c, Throwable t) {
                                Log.w(TAG, "highlight items failed: " + t.getMessage());
                                deleteOrphan(hId);
                                result.postValue(Resource.error(
                                        com.insta.nova.util.NetworkErrors.friendly(t, "Could not add the story to the highlight."),
                                        null));
                            }
                        });
                        return;
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

    /**
     * Removes a highlight row whose items never landed.
     *
     * <p>Best effort on purpose: the caller already has to show an error, and a failed cleanup
     * must not replace that error with a second one. Any survivor is visibly empty, so it is
     * recorded in the log rather than retried here.
     */
    private void deleteOrphan(String highlightId) {
        if (highlightId == null || highlightId.trim().isEmpty()) return;
        try {
            restApi.deleteHighlight("eq." + highlightId).enqueue(new Callback<Void>() {
                @Override public void onResponse(Call<Void> c, Response<Void> r) {
                    if (!r.isSuccessful()) Log.w(TAG, "orphan highlight " + highlightId + " not removed: " + r.code());
                }
                @Override public void onFailure(Call<Void> c, Throwable t) {
                    Log.w(TAG, "orphan highlight " + highlightId + " cleanup failed: " + t.getMessage());
                }
            });
        } catch (RuntimeException e) {
            Log.w(TAG, "orphan highlight cleanup threw", e);
        }
    }

    /**
     * Deletes a highlight the user owns.
     *
     * <p>The app could create highlights but never remove one — `deleteHighlight` existed on the
     * REST interface with no caller, so a mistaken highlight (or one whose items never landed)
     * stayed on the profile permanently, because the server rows are re-merged on every visit.
     */
    public LiveData<Resource<Boolean>> deleteHighlight(String highlightId) {
        MutableLiveData<Resource<Boolean>> result = new MutableLiveData<>();
        if (highlightId == null || highlightId.trim().isEmpty()) {
            result.setValue(Resource.error("Missing highlight id", null));
            return result;
        }
        result.setValue(Resource.loading(null));
        try {
            restApi.deleteHighlight("eq." + highlightId).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    if (response.isSuccessful()) {
                        result.postValue(Resource.success(true));
                    } else {
                        Log.w(TAG, "delete highlight " + highlightId + " failed: " + response.code());
                        result.postValue(Resource.error("Could not delete the highlight.", null));
                    }
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    result.postValue(Resource.error(
                            com.insta.nova.util.NetworkErrors.friendly(t, "Could not delete the highlight."), null));
                }
            });
        } catch (RuntimeException e) {
            Log.e(TAG, "delete highlight threw", e);
            result.setValue(Resource.error("Could not delete the highlight.", null));
        }
        return result;
    }

    /**
     * Renames a highlight. Renaming was impossible before this: the tile had no affordance at
     * all, so a highlight called "Highlight" stayed called that forever.
     */
    public LiveData<Resource<Boolean>> renameHighlight(String highlightId, String title) {
        MutableLiveData<Resource<Boolean>> result = new MutableLiveData<>();
        if (highlightId == null || highlightId.trim().isEmpty()) {
            result.setValue(Resource.error("Missing highlight id", null));
            return result;
        }
        if (title == null || title.trim().isEmpty()) {
            result.setValue(Resource.error("A highlight needs a name.", null));
            return result;
        }
        result.setValue(Resource.loading(null));
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("title", title.trim());
        try {
            restApi.updateHighlight("eq." + highlightId, body).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    if (response.isSuccessful()) {
                        result.postValue(Resource.success(true));
                    } else {
                        Log.w(TAG, "rename highlight " + highlightId + " failed: " + response.code());
                        result.postValue(Resource.error("Could not rename the highlight.", null));
                    }
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    result.postValue(Resource.error(
                            com.insta.nova.util.NetworkErrors.friendly(t, "Could not rename the highlight."), null));
                }
            });
        } catch (RuntimeException e) {
            Log.e(TAG, "rename highlight threw", e);
            result.setValue(Resource.error("Could not rename the highlight.", null));
        }
        return result;
    }

    /** Re-points the tile cover after an edit, so every device shows the same first slide. */
    public void setCover(String highlightId, String coverUrl) {
        if (highlightId == null || highlightId.trim().isEmpty() || coverUrl == null) return;
        Map<String, Object> body = new HashMap<>();
        body.put("cover_url", coverUrl);
        restApi.updateHighlight("eq." + highlightId, body).enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> call, Response<Void> response) {
                if (!response.isSuccessful()) Log.w(TAG, "cover update failed: " + response.code());
            }

            @Override public void onFailure(Call<Void> call, Throwable t) {
                Log.w(TAG, "cover update failed: " + t.getMessage());
            }
        });
    }

    /** Appends slides to an existing highlight — the "add more stories later" half of editing. */
    public LiveData<Resource<Boolean>> addHighlightItems(String highlightId, List<String> mediaUrls) {
        MutableLiveData<Resource<Boolean>> result = new MutableLiveData<>();
        if (highlightId == null || highlightId.trim().isEmpty() || mediaUrls == null
                || mediaUrls.isEmpty()) {
            result.setValue(Resource.error("Nothing to add.", null));
            return result;
        }
        result.setValue(Resource.loading(null));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String url : mediaUrls) {
            if (url == null || url.trim().isEmpty()) continue;
            Map<String, Object> row = new HashMap<>();
            row.put("highlight_id", highlightId);
            row.put("media_url", url);
            rows.add(row);
        }
        if (rows.isEmpty()) {
            result.setValue(Resource.error("Nothing to add.", null));
            return result;
        }
        try {
            restApi.createHighlightItems(rows).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    if (response.isSuccessful()) {
                        result.postValue(Resource.success(true));
                    } else {
                        Log.w(TAG, "add highlight items failed: " + response.code());
                        result.postValue(Resource.error("Could not add those stories.", null));
                    }
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    result.postValue(Resource.error(
                            com.insta.nova.util.NetworkErrors.friendly(t, "Could not add those stories."), null));
                }
            });
        } catch (RuntimeException e) {
            Log.e(TAG, "add highlight items threw", e);
            result.setValue(Resource.error("Could not add those stories.", null));
        }
        return result;
    }

    /** Removes one slide, leaving the rest of the highlight intact. */
    public LiveData<Resource<Boolean>> deleteHighlightItem(String itemId) {
        MutableLiveData<Resource<Boolean>> result = new MutableLiveData<>();
        if (itemId == null || itemId.trim().isEmpty()) {
            result.setValue(Resource.error("Missing item id", null));
            return result;
        }
        result.setValue(Resource.loading(null));
        try {
            restApi.deleteHighlightItem("eq." + itemId).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    if (response.isSuccessful()) {
                        result.postValue(Resource.success(true));
                    } else {
                        Log.w(TAG, "delete highlight item " + itemId + " failed: " + response.code());
                        result.postValue(Resource.error("Could not remove that story.", null));
                    }
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    result.postValue(Resource.error(
                            com.insta.nova.util.NetworkErrors.friendly(t, "Could not remove that story."), null));
                }
            });
        } catch (RuntimeException e) {
            Log.e(TAG, "delete highlight item threw", e);
            result.setValue(Resource.error("Could not remove that story.", null));
        }
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
