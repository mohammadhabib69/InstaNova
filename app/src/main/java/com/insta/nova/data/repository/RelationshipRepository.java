package com.insta.nova.data.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.insta.nova.data.local.RelationshipStore;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.model.Profile;
import com.insta.nova.data.remote.RetrofitClient;
import com.insta.nova.data.remote.api.RestApi;
import com.insta.nova.data.remote.dto.ProfileDto;
import com.insta.nova.util.Resource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Prompt N — blocked accounts, restricted accounts, story audience, follow requests
 * and the report form.
 *
 * <p>Supabase is authoritative; {@link RelationshipStore} keeps a synchronous mirror
 * for screens that must decide while rendering (inbox filters, profile menu).
 */
public class RelationshipRepository {

    private final RestApi restApi;
    private final SessionManager session;
    private final RelationshipStore store;

    public RelationshipRepository(Context context) {
        Context app = context.getApplicationContext();
        this.restApi = RetrofitClient.getInstance(app).getRestApi();
        this.session = SessionManager.getInstance(app);
        this.store = new RelationshipStore(app);
    }

    public String myId() {
        return session.getUserId();
    }

    /* ------------------------------------------------------------------ */
    /* Blocked                                                             */
    /* ------------------------------------------------------------------ */

    public LiveData<Resource<List<Profile>>> loadBlocked() {
        // Blocking writes `nova_blocks` (NovaApi.nova_block_user) and the feed filter reads
        // `nova_blocked_ids`. This list used to read the legacy `blocks` table, which nothing
        // writes any more, so a freshly blocked account showed up as "0 blocked" and could not
        // be unblocked from Settings at all.
        return loadPeople("rest/v1/nova_blocks", "blocked_id", true);
    }

    public LiveData<Resource<Boolean>> setBlocked(String userId, boolean blocked) {
        if (blocked) {
            store.setBlocked(userId, true);
            Map<String, Object> body = new HashMap<>();
            body.put("blocker_id", session.getUserId());
            body.put("blocked_id", userId);
            return voidCall(restApi.addBlock(body));
        }
        store.setBlocked(userId, false);
        Map<String, String> query = new HashMap<>();
        query.put("blocker_id", "eq." + session.getUserId());
        query.put("blocked_id", "eq." + userId);
        return voidCall(restApi.deleteBlock(query));
    }

    /* ------------------------------------------------------------------ */
    /* Restricted                                                          */
    /* ------------------------------------------------------------------ */

    public LiveData<Resource<List<Profile>>> loadRestricted() {
        return loadPeople("rest/v1/restricted_users", "restricted_id", false);
    }

    public LiveData<Resource<Boolean>> setRestricted(String userId, boolean restricted) {
        if (restricted) {
            store.setRestricted(userId, true);
            Map<String, Object> body = new HashMap<>();
            body.put("owner_id", session.getUserId());
            body.put("restricted_id", userId);
            return voidCall(restApi.addRestricted(body));
        }
        store.setRestricted(userId, false);
        Map<String, String> query = new HashMap<>();
        query.put("owner_id", "eq." + session.getUserId());
        query.put("restricted_id", "eq." + userId);
        return voidCall(restApi.deleteRestricted(query));
    }

    /* ------------------------------------------------------------------ */
    /* Story audience                                                      */
    /* ------------------------------------------------------------------ */

    public LiveData<Resource<List<Profile>>> loadStoryHidden() {
        return loadPeople("rest/v1/story_audience", "viewer_id", false);
    }

    public LiveData<Resource<Boolean>> addStoryHidden(String userId) {
        store.setStoryHidden(userId, true);
        Map<String, Object> body = new HashMap<>();
        body.put("owner_id", session.getUserId());
        body.put("viewer_id", userId);
        return voidCall(restApi.addStoryAudience(body));
    }

    public LiveData<Resource<Boolean>> removeStoryHidden(String userId) {
        store.setStoryHidden(userId, false);
        Map<String, String> query = new HashMap<>();
        query.put("owner_id", "eq." + session.getUserId());
        query.put("viewer_id", "eq." + userId);
        return voidCall(restApi.deleteStoryAudience(query));
    }

    public LiveData<Resource<Boolean>> replaceStoryHidden(Set<String> ids, Set<String> previous) {
        MutableLiveData<Resource<Boolean>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));
        Set<String> next = ids == null ? new HashSet<>() : new HashSet<>(ids);
        Set<String> old = previous == null ? new HashSet<>() : new HashSet<>(previous);

        List<String> toAdd = new ArrayList<>(next);
        toAdd.removeAll(old);
        List<String> toRemove = new ArrayList<>(old);
        toRemove.removeAll(next);

        store.replaceStoryHidden(next);
        if (toAdd.isEmpty() && toRemove.isEmpty()) {
            result.setValue(Resource.success(true));
            return result;
        }

        final int[] pending = {toAdd.size() + toRemove.size()};
        final boolean[] failed = {false};
        for (String id : toAdd) {
            Map<String, Object> body = new HashMap<>();
            body.put("owner_id", session.getUserId());
            body.put("viewer_id", id);
            restApi.addStoryAudience(body).enqueue(finish(result, pending, failed));
        }
        for (String id : toRemove) {
            Map<String, String> query = new HashMap<>();
            query.put("owner_id", "eq." + session.getUserId());
            query.put("viewer_id", "eq." + id);
            restApi.deleteStoryAudience(query).enqueue(finish(result, pending, failed));
        }
        return result;
    }

    /* ------------------------------------------------------------------ */
    /* Close friends (device-local)                                        */
    /* ------------------------------------------------------------------ */

    public LiveData<Resource<List<Profile>>> loadCloseFriends() {
        MutableLiveData<Resource<List<Profile>>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));
        Map<String, String> query = new HashMap<>();
        query.put("owner_id", "eq." + session.getUserId());
        query.put("select", "friend_id");
        query.put("order", "created_at.desc");
        restApi.getCloseFriends(query).enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> call,
                                   Response<List<Map<String, Object>>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    // Offline: keep the local mirror usable.
                    profilesByIds(store.closeFriends(), result);
                    return;
                }
                Set<String> ids = new HashSet<>();
                for (Map<String, Object> row : response.body()) {
                    Object id = row.get("friend_id");
                    if (id != null) ids.add(String.valueOf(id));
                }
                store.replaceCloseFriends(ids);
                profilesByIds(ids, result);
            }

            @Override
            public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                profilesByIds(store.closeFriends(), result);
            }
        });
        return result;
    }

    public LiveData<Resource<Boolean>> addCloseFriend(String friendId) {
        MutableLiveData<Resource<Boolean>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));
        Map<String, Object> body = new HashMap<>();
        body.put("owner_id", session.getUserId());
        body.put("friend_id", friendId);
        restApi.addCloseFriend(body).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    store.setCloseFriend(friendId, true);
                    result.postValue(Resource.success(true));
                } else {
                    result.postValue(Resource.error(AuthRepository.errorBody(response), false));
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                result.postValue(Resource.error("Network error", false));
            }
        });
        return result;
    }

    public LiveData<Resource<Boolean>> removeCloseFriend(String friendId) {
        MutableLiveData<Resource<Boolean>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));
        Map<String, String> query = new HashMap<>();
        query.put("owner_id", "eq." + session.getUserId());
        query.put("friend_id", "eq." + friendId);
        restApi.deleteCloseFriend(query).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    store.setCloseFriend(friendId, false);
                    result.postValue(Resource.success(true));
                } else {
                    result.postValue(Resource.error(AuthRepository.errorBody(response), false));
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                result.postValue(Resource.error("Network error", false));
            }
        });
        return result;
    }

    /* ------------------------------------------------------------------ */
    /* Follow requests                                                     */
    /* ------------------------------------------------------------------ */

    public LiveData<Resource<List<Profile>>> loadFollowRequests() {
        MutableLiveData<Resource<List<Profile>>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));
        Map<String, String> query = new HashMap<>();
        query.put("target_id", "eq." + session.getUserId());
        query.put("select", "requester_id");
        query.put("order", "created_at.desc");
        restApi.getFollowRequests(query).enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> call, Response<List<Map<String, Object>>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    result.postValue(Resource.error(AuthRepository.errorBody(response), null));
                    return;
                }
                Set<String> ids = new HashSet<>();
                for (Map<String, Object> row : response.body()) {
                    Object id = row.get("requester_id");
                    if (id != null) ids.add(String.valueOf(id));
                }
                profilesByIds(ids, result);
            }

            @Override
            public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                result.postValue(Resource.error(t.getMessage() == null ? "Network error" : t.getMessage(), null));
            }
        });
        return result;
    }

    public LiveData<Resource<Boolean>> approveRequest(String requesterId) {
        Map<String, Object> body = new HashMap<>();
        body.put("p_requester", requesterId);
        return voidCall(restApi.approveFollowRequest(body));
    }

    public LiveData<Resource<Boolean>> declineRequest(String requesterId) {
        Map<String, String> query = new HashMap<>();
        query.put("requester_id", "eq." + requesterId);
        query.put("target_id", "eq." + session.getUserId());
        return voidCall(restApi.deleteFollowRequest(query));
    }

    /* ------------------------------------------------------------------ */
    /* Reports                                                             */
    /* ------------------------------------------------------------------ */

    public LiveData<Resource<Boolean>> submitReport(String category, String message, String version) {
        Map<String, Object> body = new HashMap<>();
        body.put("user_id", session.getUserId());
        body.put("category", category == null ? "other" : category);
        body.put("message", message);
        body.put("app_version", version);
        return voidCall(restApi.submitReport(body));
    }

    /* ------------------------------------------------------------------ */
    /* Helpers                                                             */
    /* ------------------------------------------------------------------ */

    private LiveData<Resource<List<Profile>>> loadPeople(String path, String idField, boolean blocked) {
        MutableLiveData<Resource<List<Profile>>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));
        // Two tables carry blocks in this codebase: the legacy `blocks` and the one the app
        // actually writes, `nova_blocks`. Matching only the legacy path silently sent the
        // blocked list to the story-audience endpoint (400: story_audience.blocked_id).
        boolean isNovaBlock = "rest/v1/nova_blocks".equals(path);
        boolean isBlock = "rest/v1/blocks".equals(path) || isNovaBlock;
        boolean isRestricted = "rest/v1/restricted_users".equals(path);

        Map<String, String> query = new HashMap<>();
        query.put(isBlock ? "blocker_id" : (isRestricted ? "owner_id" : "owner_id"),
                "eq." + session.getUserId());
        query.put("select", idField);
        query.put("order", "created_at.desc");

        Call<List<Map<String, Object>>> call = isNovaBlock ? restApi.getNovaBlocks(query)
                : (isBlock ? restApi.getBlocks(query)
                : (isRestricted ? restApi.getRestricted(query) : restApi.getStoryAudience(query)));

        call.enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> c, Response<List<Map<String, Object>>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    // Offline: keep the local mirror rather than showing an empty list as fact.
                    if (response.code() == 0 || response.code() >= 500) {
                        profilesByIds(localIds(isBlock, isRestricted), result);
                    } else {
                        result.postValue(Resource.error(AuthRepository.errorBody(response), null));
                    }
                    return;
                }
                Set<String> ids = new HashSet<>();
                for (Map<String, Object> row : response.body()) {
                    Object id = row.get(idField);
                    if (id != null) ids.add(String.valueOf(id));
                }
                if (isBlock) store.replaceBlocked(ids);
                if (isRestricted) store.replaceRestricted(ids);
                if (!isBlock && !isRestricted) store.replaceStoryHidden(ids);
                profilesByIds(ids, result);
            }

            @Override
            public void onFailure(Call<List<Map<String, Object>>> c, Throwable t) {
                profilesByIds(localIds(isBlock, isRestricted), result);
            }
        });
        return result;
    }

    private Set<String> localIds(boolean blocked, boolean restricted) {
        if (blocked) return store.blocked();
        if (restricted) return store.restricted();
        return store.storyHidden();
    }

    private void profilesByIds(Set<String> ids, MutableLiveData<Resource<List<Profile>>> result) {
        if (ids == null || ids.isEmpty()) {
            result.postValue(Resource.success(new ArrayList<>()));
            return;
        }
        Map<String, String> query = new HashMap<>();
        query.put("id", "in.(" + android.text.TextUtils.join(",", ids) + ")");
        query.put("select", "id,username,full_name,avatar_url,is_verified,is_private");
        query.put("limit", "200");
        restApi.getProfiles(query).enqueue(new Callback<List<ProfileDto>>() {
            @Override
            public void onResponse(Call<List<ProfileDto>> call, Response<List<ProfileDto>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    result.postValue(Resource.error(AuthRepository.errorBody(response), null));
                    return;
                }
                List<Profile> people = new ArrayList<>();
                for (ProfileDto dto : response.body()) {
                    if (dto != null) people.add(dto.toDomain());
                }
                result.postValue(Resource.success(people));
            }

            @Override
            public void onFailure(Call<List<ProfileDto>> call, Throwable t) {
                result.postValue(Resource.error(t.getMessage() == null ? "Network error" : t.getMessage(), null));
            }
        });
    }

    private Callback<Void> finish(MutableLiveData<Resource<Boolean>> result, int[] pending, boolean[] failed) {
        return new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (!response.isSuccessful()) failed[0] = true;
                pending[0]--;
                if (pending[0] <= 0) {
                    if (failed[0]) result.postValue(Resource.error("Could not save every change", false));
                    else result.postValue(Resource.success(true));
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                failed[0] = true;
                pending[0]--;
                if (pending[0] <= 0) {
                    result.postValue(Resource.error(t.getMessage() == null ? "Network error" : t.getMessage(), false));
                }
            }
        };
    }

    private LiveData<Resource<Boolean>> voidCall(Call<Void> call) {
        MutableLiveData<Resource<Boolean>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));
        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> c, Response<Void> response) {
                result.postValue(response.isSuccessful()
                        ? Resource.success(true)
                        : Resource.error(AuthRepository.errorBody(response), false));
            }

            @Override
            public void onFailure(Call<Void> c, Throwable t) {
                result.postValue(Resource.error(t.getMessage() == null ? "Network error" : t.getMessage(), false));
            }
        });
        return result;
    }
}
