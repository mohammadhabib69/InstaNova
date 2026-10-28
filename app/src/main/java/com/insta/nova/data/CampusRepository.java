package com.insta.nova.data;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.google.gson.JsonElement;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.remote.RetrofitClient;
import com.insta.nova.util.Resource;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/** Campus membership, events, lost &amp; found and anonymous confessions. */
public class CampusRepository {

    private final NovaApi api;
    private final SessionManager session;

    public CampusRepository(Context context) {
        Context app = context.getApplicationContext();
        api = RetrofitClient.getInstance(app).getNovaApi();
        session = SessionManager.getInstance(app);
    }

    public String me() {
        return session.getUserId();
    }

    public LiveData<Resource<List<NovaDtos.University>>> universities(String search) {
        Map<String, String> q = new HashMap<>();
        q.put("select", "id,name,short_name,email_domains");
        q.put("order", "name.asc");
        if (search != null && !search.trim().isEmpty()) {
            q.put("name", "ilike.*" + search.trim() + "*");
        }
        return NovaCall.run(api.getUniversities(q), "universities");
    }

    public LiveData<Resource<List<NovaDtos.CampusMembership>>> membership() {
        Map<String, String> q = new HashMap<>();
        q.put("user_id", "eq." + me());
        q.put("select", "*");
        return NovaCall.run(api.getMyMembership(q), "membership");
    }

    public LiveData<Resource<NovaDtos.CampusJoinResult>> join(int universityId, String department, String batch) {
        Map<String, Object> body = new HashMap<>();
        body.put("p_university", universityId);
        body.put("p_department", emptyToNull(department));
        body.put("p_batch", emptyToNull(batch));
        return NovaCall.run(api.campusJoin(body), "campus join");
    }

    /** Verifies from a student mailbox; the reply carries verified + reason + expected. */
    public LiveData<Resource<Map<String, Object>>> verifyEmail(String email) {
        Map<String, Object> body = new HashMap<>();
        body.put("p_email", email);
        return NovaCall.run(api.verifyCampusEmail(body), "campus verify email");
    }

    public LiveData<Resource<Boolean>> leave() {
        return NovaCall.action(api.campusLeave(new HashMap<>()), "campus leave");
    }

    public LiveData<Resource<Boolean>> isVerifiedStudent() {
        return NovaCall.run(api.isVerifiedStudent(new HashMap<>()), "verified student");
    }

    /* -------------------------------- events --------------------------------- */

    public LiveData<Resource<List<NovaDtos.EventItem>>> events(boolean thisWeek, String category) {
        Map<String, String> q = new HashMap<>();
        q.put("select", "*");
        q.put("order", "starts_at.asc");
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
        iso.setTimeZone(TimeZone.getTimeZone("Asia/Dhaka"));
        String from = iso.format(new Date(System.currentTimeMillis() - 3 * 3600_000L));
        if (thisWeek) {
            String to = iso.format(new Date(System.currentTimeMillis() + 7 * 86400_000L));
            q.put("and", "(starts_at.gte." + from + ",starts_at.lte." + to + ")");
        } else {
            q.put("starts_at", "gte." + from);
        }
        if (category != null && !category.isEmpty() && !"all".equals(category)) {
            q.put("category", "eq." + category);
        }
        return NovaCall.run(api.getEvents(q), "events");
    }

    public LiveData<Resource<Boolean>> createEvent(String title, String details, String venue,
                                                   String startsAtIso, String endsAtIso,
                                                   String category, String bannerUrl, int universityId) {
        Map<String, Object> body = new HashMap<>();
        body.put("university_id", universityId);
        body.put("author_id", me());
        body.put("title", title);
        body.put("details", emptyToNull(details));
        body.put("venue", venue);
        body.put("starts_at", startsAtIso);
        body.put("ends_at", endsAtIso);
        body.put("category", category);
        body.put("banner_url", emptyToNull(bannerUrl));
        return NovaCall.action(api.insertEvent(body), "create event");
    }

    public LiveData<Resource<Boolean>> deleteEvent(String id) {
        return NovaCall.action(api.deleteEvent("eq." + id), "delete event");
    }

    public LiveData<Resource<Boolean>> rsvp(String eventId, String status) {
        if (status == null) {
            return NovaCall.action(api.deleteRsvp("eq." + eventId, "eq." + me()), "remove rsvp");
        }
        Map<String, Object> body = new HashMap<>();
        body.put("event_id", eventId);
        body.put("user_id", me());
        body.put("status", status);
        return NovaCall.action(api.upsertRsvp("event_id,user_id", body), "rsvp");
    }

    /* ------------------------------ lost & found ----------------------------- */

    public LiveData<Resource<List<NovaDtos.LostFoundItem>>> lostFound(String kind, String status) {
        Map<String, String> q = new HashMap<>();
        q.put("select", "*");
        q.put("order", "created_at.desc");
        if (kind != null && !kind.isEmpty()) q.put("kind", "eq." + kind);
        if (status != null && !status.isEmpty()) q.put("status", "eq." + status);
        return NovaCall.run(api.getLostFound(q), "lost and found");
    }

    public LiveData<Resource<Boolean>> createLostFound(String kind, String title, String details,
                                                       String place, String eventDate,
                                                       String photoUrl, int universityId,
                                                       String contact) {
        Map<String, Object> body = new HashMap<>();
        body.put("university_id", universityId);
        body.put("author_id", me());
        body.put("kind", kind);
        body.put("title", title);
        body.put("details", emptyToNull(details));
        body.put("place", place);
        body.put("event_date", eventDate);
        body.put("photo_url", emptyToNull(photoUrl));
        body.put("contact", emptyToNull(contact));
        body.put("status", "open");
        return NovaCall.action(api.insertLostFound(body), "create lostfound");
    }

    public LiveData<Resource<Boolean>> updateLostFound(String id, Map<String, Object> fields) {
        return NovaCall.action(api.updateLostFound("eq." + id, fields), "update lostfound");
    }

    public LiveData<Resource<Boolean>> deleteLostFound(String id) {
        return NovaCall.action(api.deleteLostFound("eq." + id), "delete lostfound");
    }

    /* ------------------------------- confessions ----------------------------- */

    public LiveData<Resource<List<NovaDtos.Confession>>> confessions(int limit, int offset) {
        Map<String, Object> body = new HashMap<>();
        body.put("p_limit", limit);
        body.put("p_offset", offset);
        return NovaCall.run(api.confessionsFeed(body), "confessions");
    }

    public LiveData<Resource<String>> postConfession(String text, String photoUrl) {
        Map<String, Object> body = new HashMap<>();
        body.put("p_body", text);
        body.put("p_photo", photoUrl == null ? "" : photoUrl);
        androidx.lifecycle.MutableLiveData<Resource<String>> out =
                new androidx.lifecycle.MutableLiveData<>(Resource.loading());
        api.postConfession(body).enqueue(new retrofit2.Callback<JsonElement>() {
            @Override
            public void onResponse(retrofit2.Call<JsonElement> call, retrofit2.Response<JsonElement> response) {
                if (response.isSuccessful() && response.body() != null) {
                    JsonElement value = response.body();
                    out.postValue(Resource.success(value.isJsonPrimitive()
                            ? value.getAsString() : value.toString()));
                } else {
                    out.postValue(Resource.error(NovaErrors.code(response)));
                }
            }

            @Override
            public void onFailure(retrofit2.Call<JsonElement> call, Throwable t) {
                out.postValue(Resource.error("network"));
            }
        });
        return out;
    }

    public LiveData<Resource<Integer>> likeConfession(String id, boolean like) {
        Map<String, Object> body = new HashMap<>();
        body.put("p_id", id);
        body.put("p_like", like);
        return NovaCall.run(api.likeConfession(body), "like confession");
    }

    public LiveData<Resource<Boolean>> deleteConfession(String id) {
        Map<String, Object> body = new HashMap<>();
        body.put("p_id", id);
        return NovaCall.action(api.deleteConfession(body), "delete confession");
    }

    private static String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
