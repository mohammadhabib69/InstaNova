package com.insta.nova.data;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.gson.JsonElement;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.remote.RetrofitClient;
import com.insta.nova.data.remote.dto.ProfileDto;
import com.insta.nova.util.Resource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Hub, safety, alerts and relationship actions. Every method returns
 * {@code LiveData<Resource<T>>} and never touches the main thread.
 */
public class NovaRepository {

    private static final String TAG = "NovaRepository";
    private static final Map<String, NovaDtos.DonorBadge> BADGE_CACHE = new ConcurrentHashMap<>();

    private final NovaApi api;
    private final SessionManager session;
    private final BlockMuteCache blockMuteCache;

    public NovaRepository(Context context) {
        Context app = context.getApplicationContext();
        api = RetrofitClient.getInstance(app).getNovaApi();
        session = SessionManager.getInstance(app);
        blockMuteCache = BlockMuteCache.get(app);
    }

    public String me() {
        return session.getUserId();
    }

    /* --------------------------------- hub ---------------------------------- */

    public LiveData<Resource<NovaDtos.HubSummary>> hubSummary() {
        return call(api.hubSummary(new HashMap<>()), "hub summary");
    }

    /* ------------------------------- insights ------------------------------- */

    public LiveData<Resource<NovaDtos.Insights>> insights(int days) {
        Map<String, Object> body = new HashMap<>();
        body.put("p_days", days);
        return call(api.myInsights(body), "insights");
    }

    /* -------------------------------- safety -------------------------------- */

    public LiveData<Resource<NovaDtos.SafetyStats>> safetyStats() {
        return call(api.safetyStats(new HashMap<>()), "safety stats");
    }

    public LiveData<Resource<List<NovaDtos.BlockedUser>>> blockedUsers() {
        return call(api.myBlockedUsers(new HashMap<>()), "blocked users");
    }

    public LiveData<Resource<List<NovaDtos.MutedUser>>> mutedUsers() {
        MutableLiveData<Resource<List<NovaDtos.MutedUser>>> out = new MutableLiveData<>(Resource.loading());
        api.mutedUsers(new HashMap<>()).enqueue(new Callback<List<NovaDtos.MutedUser>>() {
            @Override
            public void onResponse(Call<List<NovaDtos.MutedUser>> call, Response<List<NovaDtos.MutedUser>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    out.postValue(Resource.error(NovaErrors.code(response)));
                    return;
                }
                List<NovaDtos.MutedUser> muted = response.body();
                if (muted.isEmpty()) {
                    out.postValue(Resource.success(muted));
                    return;
                }
                StringBuilder ids = new StringBuilder("in.(");
                for (int i = 0; i < muted.size(); i++) {
                    if (i > 0) ids.append(",");
                    ids.append(muted.get(i).targetId);
                }
                ids.append(")");
                Map<String, String> q = new HashMap<>();
                q.put("id", ids.toString());
                q.put("select", "id,username,avatar_url");
                api.getProfiles(q).enqueue(new Callback<List<ProfileDto>>() {
                    @Override
                    public void onResponse(Call<List<ProfileDto>> call2, Response<List<ProfileDto>> profiles) {
                        if (profiles.isSuccessful() && profiles.body() != null) {
                            Map<String, ProfileDto> byId = new HashMap<>();
                            for (ProfileDto p : profiles.body()) byId.put(p.getId(), p);
                            for (NovaDtos.MutedUser m : muted) {
                                ProfileDto p = byId.get(m.targetId);
                                if (p != null) {
                                    m.username = p.getUsername();
                                    m.avatarUrl = p.getAvatarUrl();
                                }
                            }
                        }
                        out.postValue(Resource.success(muted));
                    }

                    @Override
                    public void onFailure(Call<List<ProfileDto>> call2, Throwable t) {
                        out.postValue(Resource.success(muted));
                    }
                });
            }

            @Override
            public void onFailure(Call<List<NovaDtos.MutedUser>> call, Throwable t) {
                out.postValue(Resource.error("network"));
            }
        });
        return out;
    }

    public LiveData<Resource<Boolean>> block(String userId) {
        Map<String, Object> body = new HashMap<>();
        body.put("p_user", userId);
        LiveData<Resource<Boolean>> result = action(api.blockUser(body), "block");
        blockMuteCache.refresh();
        return result;
    }

    public LiveData<Resource<Boolean>> unblock(String userId) {
        Map<String, Object> body = new HashMap<>();
        body.put("p_user", userId);
        LiveData<Resource<Boolean>> result = action(api.unblockUser(body), "unblock");
        blockMuteCache.refresh();
        return result;
    }

    public LiveData<Resource<Boolean>> mute(String userId, boolean posts, boolean stories) {
        Map<String, Object> body = new HashMap<>();
        body.put("p_user", userId);
        body.put("p_posts", posts);
        body.put("p_stories", stories);
        LiveData<Resource<Boolean>> result = action(api.muteUser(body), "mute");
        blockMuteCache.refresh();
        return result;
    }

    public LiveData<Resource<String>> report(String type, String targetId, String reason, String details) {
        MutableLiveData<Resource<String>> out = new MutableLiveData<>(Resource.loading());
        Map<String, Object> body = new HashMap<>();
        body.put("p_type", type);
        body.put("p_target", targetId);
        body.put("p_reason", reason);
        body.put("p_details", details == null || details.trim().isEmpty() ? null : details.trim());
        api.report(body).enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                if (response.isSuccessful()) {
                    out.postValue(Resource.success(response.body() != null ? response.body().toString() : ""));
                } else {
                    String code = NovaErrors.code(response);
                    Log.w(TAG, "report failed: " + code);
                    out.postValue(Resource.error(code));
                }
            }

            @Override
            public void onFailure(Call<JsonElement> call, Throwable t) {
                out.postValue(Resource.error("network"));
            }
        });
        return out;
    }

    /** One row per moderation outcome; failures are logged, never surfaced as UX. */
    public void logModeration(String kind, String category, int severity, String action) {
        String me = me();
        if (me == null || me.isEmpty()) return;
        Map<String, Object> body = new HashMap<>();
        body.put("user_id", me);
        body.put("kind", kind);
        body.put("category", category);
        body.put("severity", severity);
        body.put("action", action);
        api.insertModerationEvent(body).enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> call, Response<Void> response) {
                if (!response.isSuccessful()) Log.w(TAG, "moderation log HTTP " + response.code());
            }
            @Override public void onFailure(Call<Void> call, Throwable t) {
                Log.w(TAG, "moderation log failed: " + t.getMessage());
            }
        });
    }

    /* -------------------------------- badges -------------------------------- */

    public LiveData<Resource<NovaDtos.DonorBadge>> donorBadge(String userId, boolean force) {
        MutableLiveData<Resource<NovaDtos.DonorBadge>> out = new MutableLiveData<>(Resource.loading());
        if (!force) {
            NovaDtos.DonorBadge cached = BADGE_CACHE.get(userId);
            if (cached != null) {
                out.setValue(Resource.success(cached));
                return out;
            }
        }
        Map<String, Object> body = new HashMap<>();
        body.put("p_user", userId);
        api.donorBadge(body).enqueue(new Callback<NovaDtos.DonorBadge>() {
            @Override
            public void onResponse(Call<NovaDtos.DonorBadge> call, Response<NovaDtos.DonorBadge> response) {
                if (response.isSuccessful() && response.body() != null) {
                    BADGE_CACHE.put(userId, response.body());
                    out.postValue(Resource.success(response.body()));
                } else {
                    out.postValue(Resource.error(NovaErrors.code(response)));
                }
            }

            @Override
            public void onFailure(Call<NovaDtos.DonorBadge> call, Throwable t) {
                out.postValue(Resource.error("network"));
            }
        });
        return out;
    }

    public static void invalidateBadge(String userId) {
        if (userId != null) BADGE_CACHE.remove(userId);
    }

    /* -------------------------------- alerts -------------------------------- */

    public LiveData<Resource<List<NovaDtos.Alert>>> alerts(int limit, String before) {
        MutableLiveData<Resource<List<NovaDtos.Alert>>> out = new MutableLiveData<>(Resource.loading());
        String me = me();
        if (me == null || me.isEmpty()) {
            out.setValue(Resource.error("not_authenticated"));
            return out;
        }
        Map<String, String> q = new HashMap<>();
        q.put("user_id", "eq." + me);
        q.put("order", "created_at.desc");
        q.put("limit", String.valueOf(limit));
        if (before != null && !before.isEmpty()) q.put("created_at", "lt." + before);
        api.getAlerts(q).enqueue(new Callback<List<NovaDtos.Alert>>() {
            @Override
            public void onResponse(Call<List<NovaDtos.Alert>> call, Response<List<NovaDtos.Alert>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    out.postValue(Resource.success(response.body()));
                } else {
                    out.postValue(Resource.error(NovaErrors.code(response)));
                }
            }

            @Override
            public void onFailure(Call<List<NovaDtos.Alert>> call, Throwable t) {
                out.postValue(Resource.error("network"));
            }
        });
        return out;
    }

    public LiveData<Resource<Boolean>> markAlertRead(String alertId) {
        MutableLiveData<Resource<Boolean>> out = new MutableLiveData<>(Resource.loading());
        Map<String, Object> body = new HashMap<>();
        body.put("is_read", true);
        api.patchAlert("eq." + alertId, body).enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> call, Response<Void> response) {
                out.postValue(response.isSuccessful() ? Resource.success(true) : Resource.error(NovaErrors.code(response)));
            }
            @Override public void onFailure(Call<Void> call, Throwable t) {
                out.postValue(Resource.error("network"));
            }
        });
        return out;
    }

    public LiveData<Resource<Boolean>> markAllAlertsRead() {
        MutableLiveData<Resource<Boolean>> out = new MutableLiveData<>(Resource.loading());
        String me = me();
        Map<String, Object> body = new HashMap<>();
        body.put("is_read", true);
        api.markAllAlertsRead("eq." + me, "eq.false", body).enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> call, Response<Void> response) {
                out.postValue(response.isSuccessful() ? Resource.success(true) : Resource.error(NovaErrors.code(response)));
            }
            @Override public void onFailure(Call<Void> call, Throwable t) {
                out.postValue(Resource.error("network"));
            }
        });
        return out;
    }

    public LiveData<Resource<Integer>> unreadAlerts() {
        return call(api.unreadAlerts(new HashMap<>()), "unread alerts");
    }

    /** One-shot unread fetch for the tab badge poll (no observer accumulation). */
    public void unreadAlertsOnce(java.util.function.Consumer<Integer> callback) {
        api.unreadAlerts(new HashMap<>()).enqueue(new Callback<Integer>() {
            @Override public void onResponse(Call<Integer> call, Response<Integer> response) {
                if (response.isSuccessful() && response.body() != null) callback.accept(response.body());
            }
            @Override public void onFailure(Call<Integer> call, Throwable t) {
                Log.w(TAG, "unread poll failed: " + t.getMessage());
            }
        });
    }

    public LiveData<Resource<ProfileDto>> profile(String userId) {
        MutableLiveData<Resource<ProfileDto>> out = new MutableLiveData<>(Resource.loading());
        Map<String, String> q = new HashMap<>();
        q.put("id", "eq." + userId);
        q.put("select", "*");
        api.getProfiles(q).enqueue(new Callback<List<ProfileDto>>() {
            @Override public void onResponse(Call<List<ProfileDto>> call, Response<List<ProfileDto>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    out.postValue(Resource.success(response.body().get(0)));
                } else {
                    out.postValue(Resource.error(NovaErrors.code(response)));
                }
            }
            @Override public void onFailure(Call<List<ProfileDto>> call, Throwable t) {
                out.postValue(Resource.error("network"));
            }
        });
        return out;
    }

    /* -------------------------------- helpers ------------------------------- */

    private LiveData<Resource<Boolean>> action(Call<Void> call, String label) {
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
                out.postValue(Resource.error("network"));
            }
        });
        return out;
    }

    private <T> LiveData<Resource<T>> call(Call<T> call, String label) {
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
}
