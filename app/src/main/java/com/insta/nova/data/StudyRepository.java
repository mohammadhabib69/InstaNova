package com.insta.nova.data;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.remote.RetrofitClient;
import com.insta.nova.util.Resource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Study sessions, streaks, leaderboards and the Q&amp;A board. */
public class StudyRepository {

    private final NovaApi api;
    private final SessionManager session;

    public StudyRepository(Context context) {
        Context app = context.getApplicationContext();
        api = RetrofitClient.getInstance(app).getNovaApi();
        session = SessionManager.getInstance(app);
    }

    public String me() {
        return session.getUserId();
    }

    public LiveData<Resource<NovaDtos.StudyStats>> stats() {
        return NovaCall.run(api.studyStats(new HashMap<>()), "study stats");
    }

    /**
     * The leaderboard, with a measured fallback.
     *
     * <p>{@code nova_study_leaderboard} answers {@code 200 []} for accounts that do have
     * saved sessions (verified on device: the stats RPC returns 2 minutes for the same
     * account while the board RPC returns {@code content-length: 2}). When that happens
     * the board is rebuilt from {@code nova_study_sessions} so the screen shows real
     * minutes instead of an empty state that contradicts the stats card above it.
     */
    public LiveData<Resource<List<NovaDtos.LeaderRow>>> leaderboard(String scope, int limit) {
        final androidx.lifecycle.MutableLiveData<Resource<List<NovaDtos.LeaderRow>>> out =
                new androidx.lifecycle.MutableLiveData<>();
        out.setValue(Resource.loading(null));
        Map<String, Object> body = new HashMap<>();
        // nova_study_leaderboard takes an AUDIENCE, not a period: its pool is built from
        // 'campus' or 'following' and the time window is fixed at seven days inside the
        // function. Sending "week"/"all" matched neither branch, so the pool was empty and
        // the RPC answered 200 [] — the real cause of the empty board.
        // The function now understands 'week' and 'all' natively (see
        // supabase/migrations/2026-10-23_nova_study_leaderboard_fix.sql); both rank the
        // people you follow plus you.
        body.put("p_scope", "all".equals(scope) ? "all" : "week");
        body.put("p_limit", limit);
        api.studyLeaderboard(body).enqueue(new retrofit2.Callback<List<NovaDtos.LeaderRow>>() {
            @Override
            public void onResponse(retrofit2.Call<List<NovaDtos.LeaderRow>> call,
                                   retrofit2.Response<List<NovaDtos.LeaderRow>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    out.postValue(Resource.success(response.body()));
                    return;
                }
                buildFromSessions(scope, limit, out);
            }

            @Override
            public void onFailure(retrofit2.Call<List<NovaDtos.LeaderRow>> call, Throwable t) {
                buildFromSessions(scope, limit, out);
            }
        });
        return out;
    }

    /** Aggregates raw sessions into the same row shape the RPC returns. */
    private void buildFromSessions(String scope, int limit,
                                   androidx.lifecycle.MutableLiveData<Resource<List<NovaDtos.LeaderRow>>> out) {
        Map<String, String> q = new HashMap<>();
        q.put("select", "user_id,minutes,started_at");
        if ("week".equalsIgnoreCase(scope)) {
            String from = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
                    .minusDays(7).toString();
            q.put("started_at", "gte." + from);
        }
        q.put("limit", "1000");
        api.getStudySessions(q).enqueue(new retrofit2.Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(retrofit2.Call<List<Map<String, Object>>> call,
                                   retrofit2.Response<List<Map<String, Object>>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    out.postValue(Resource.success(new java.util.ArrayList<>()));
                    return;
                }
                Map<String, Integer> totals = new HashMap<>();
                for (Map<String, Object> row : response.body()) {
                    if (row == null) continue;
                    Object uid = row.get("user_id");
                    Object minutes = row.get("minutes");
                    if (!(uid instanceof String) || !(minutes instanceof Number)) continue;
                    totals.merge((String) uid, ((Number) minutes).intValue(), Integer::sum);
                }
                if (totals.isEmpty()) {
                    out.postValue(Resource.success(new java.util.ArrayList<>()));
                    return;
                }
                attachNames(totals, limit, out);
            }

            @Override
            public void onFailure(retrofit2.Call<List<Map<String, Object>>> call, Throwable t) {
                out.postValue(Resource.success(new java.util.ArrayList<>()));
            }
        });
    }

    /** Resolves usernames for the aggregated ids and ranks them. */
    private void attachNames(Map<String, Integer> totals, int limit,
                             androidx.lifecycle.MutableLiveData<Resource<List<NovaDtos.LeaderRow>>> out) {
        Map<String, String> q = new HashMap<>();
        q.put("select", "id,username,avatar_url");
        q.put("id", "in.(" + android.text.TextUtils.join(",", totals.keySet()) + ")");
        api.getProfiles(q).enqueue(new retrofit2.Callback<List<com.insta.nova.data.remote.dto.ProfileDto>>() {
            @Override
            public void onResponse(retrofit2.Call<List<com.insta.nova.data.remote.dto.ProfileDto>> call,
                                   retrofit2.Response<List<com.insta.nova.data.remote.dto.ProfileDto>> response) {
                Map<String, com.insta.nova.data.remote.dto.ProfileDto> byId = new HashMap<>();
                if (response.isSuccessful() && response.body() != null) {
                    for (com.insta.nova.data.remote.dto.ProfileDto dto : response.body()) {
                        if (dto != null && dto.getId() != null) byId.put(dto.getId(), dto);
                    }
                }
                List<Map.Entry<String, Integer>> ranked =
                        new java.util.ArrayList<>(totals.entrySet());
                ranked.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

                String myId = me();
                List<NovaDtos.LeaderRow> rows = new java.util.ArrayList<>();
                int rank = 1;
                for (Map.Entry<String, Integer> entry : ranked) {
                    if (rows.size() >= limit) break;
                    NovaDtos.LeaderRow row = new NovaDtos.LeaderRow();
                    row.userId = entry.getKey();
                    row.minutes = entry.getValue();
                    row.rank = rank++;
                    row.isMe = entry.getKey().equals(myId);
                    com.insta.nova.data.remote.dto.ProfileDto dto = byId.get(entry.getKey());
                    if (dto != null) {
                        row.username = dto.getUsername();
                        row.avatarUrl = dto.getAvatarUrl();
                    }
                    rows.add(row);
                }
                out.postValue(Resource.success(rows));
            }

            @Override
            public void onFailure(retrofit2.Call<List<com.insta.nova.data.remote.dto.ProfileDto>> call, Throwable t) {
                out.postValue(Resource.success(new java.util.ArrayList<>()));
            }
        });
    }

    public LiveData<Resource<Boolean>> saveSession(String subject, int minutes, String startedAtIso) {
        Map<String, Object> body = new HashMap<>();
        body.put("user_id", me());
        body.put("subject", subject);
        body.put("minutes", minutes);
        body.put("completed", true);
        body.put("started_at", startedAtIso);
        return NovaCall.action(api.insertStudySession(body), "save session");
    }

    /* --------------------------------- Q&A ----------------------------------- */

    public LiveData<Resource<List<NovaDtos.Question>>> questions(String tab, String subject) {
        Map<String, String> q = new HashMap<>();
        q.put("select", "*");
        q.put("order", "created_at.desc");
        if ("unsolved".equals(tab)) q.put("solved", "eq.false");
        if ("mine".equals(tab)) q.put("author_id", "eq." + me());
        if (subject != null && !subject.isEmpty() && !"all".equals(subject)) {
            q.put("subject", "eq." + subject);
        }
        return NovaCall.run(api.getQuestions(q), "questions");
    }

    public LiveData<Resource<List<NovaDtos.Answer>>> answers(String questionId) {
        Map<String, String> q = new HashMap<>();
        q.put("question_id", "eq." + questionId);
        q.put("select", "*");
        q.put("order", "accepted.desc,votes.desc,created_at.asc");
        return NovaCall.run(api.getAnswers(q), "answers");
    }

    public LiveData<Resource<Boolean>> ask(String subject, String title, String body, String imageUrl) {
        Map<String, Object> row = new HashMap<>();
        row.put("author_id", me());
        row.put("subject", subject);
        row.put("title", title);
        row.put("body", body);
        row.put("image_url", emptyToNull(imageUrl));
        return NovaCall.action(api.insertQuestion(row), "ask question");
    }

    public LiveData<Resource<Boolean>> updateQuestion(String id, Map<String, Object> fields) {
        return NovaCall.action(api.updateQuestion("eq." + id, fields), "update question");
    }

    public LiveData<Resource<Boolean>> deleteQuestion(String id) {
        return NovaCall.action(api.deleteQuestion("eq." + id), "delete question");
    }

    public LiveData<Resource<Boolean>> answer(String questionId, String body) {
        Map<String, Object> row = new HashMap<>();
        row.put("question_id", questionId);
        row.put("author_id", me());
        row.put("body", body);
        return NovaCall.action(api.insertAnswer(row), "answer");
    }

    public LiveData<Resource<Boolean>> updateAnswer(String id, String body) {
        Map<String, Object> row = new HashMap<>();
        row.put("body", body);
        return NovaCall.action(api.updateAnswer("eq." + id, row), "update answer");
    }

    public LiveData<Resource<Boolean>> deleteAnswer(String id) {
        return NovaCall.action(api.deleteAnswer("eq." + id), "delete answer");
    }

    public LiveData<Resource<NovaDtos.VoteResult>> vote(String type, String id) {
        Map<String, Object> body = new HashMap<>();
        body.put("p_type", type);
        body.put("p_id", id);
        return NovaCall.run(api.toggleVote(body), "vote");
    }

    public LiveData<Resource<NovaDtos.AcceptResult>> accept(String answerId) {
        Map<String, Object> body = new HashMap<>();
        body.put("p_answer", answerId);
        return NovaCall.run(api.acceptAnswer(body), "accept answer");
    }

    private static String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
