package com.insta.nova.ui.settings;

import android.content.Intent;
import android.os.Bundle;

import com.insta.nova.R;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.model.Profile;
import com.insta.nova.data.remote.RetrofitClient;
import com.insta.nova.data.remote.api.RestApi;
import com.insta.nova.data.remote.dto.PostDto;
import com.insta.nova.data.remote.dto.ProfileDto;
import com.insta.nova.ui.chat.UserProfileActivity;
import com.insta.nova.ui.widget.InsightsChartView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Audience analytics.
 *
 * <p>Follow counts come from the account row; the interaction ranking is built from
 * every like and comment left on this account's posts. Both were already read here
 * — what was missing was any view of the distribution, so the numbers sat in a list.
 *
 * <p>Instagram's audience screen leads with age, city and gender. This app collects
 * none of those, so instead of inventing them the screen charts what it does know
 * and says plainly that demographics are absent.
 */
public class AudienceAnalyticsActivity extends SettingsPageActivity {

    private RestApi api;
    private String myId;
    private int followers;
    private int following;

    private long likesTotal;
    private long commentsTotal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setPageTitle(R.string.st_audience_title);
        api = RetrofitClient.getInstance(this).getRestApi();
        myId = SessionManager.getInstance(this).getUserId();
        showLoading(true);
        loadCounts();
    }

    private void loadCounts() {
        Map<String, String> query = new HashMap<>();
        query.put("id", "eq." + myId);
        query.put("select", "followers_count,following_count");
        query.put("limit", "1");
        api.getProfilesRaw(query).enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> call,
                                   Response<List<Map<String, Object>>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    Object f = response.body().get(0).get("followers_count");
                    Object g = response.body().get(0).get("following_count");
                    followers = f == null ? 0 : ((Number) f).intValue();
                    following = g == null ? 0 : ((Number) g).intValue();
                }
                renderCounts();
            }

            @Override
            public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                renderCounts();
            }
        });
    }

    private boolean countsRendered;

    private void renderCounts() {
        if (countsRendered) return;
        countsRendered = true;
        content.removeAllViews();
        List<String[]> stats = new ArrayList<>();
        stats.add(new String[]{format(followers), getString(R.string.st_followers_count)});
        stats.add(new String[]{format(following), getString(R.string.st_following_count)});
        addStatGrid(stats);
        loadEngagement();
    }

    private void loadEngagement() {
        Map<String, String> query = new HashMap<>();
        query.put("user_id", "eq." + myId);
        query.put("select", "id");
        query.put("limit", "500");
        api.getPosts(query).enqueue(new Callback<List<PostDto>>() {
            @Override
            public void onResponse(Call<List<PostDto>> call, Response<List<PostDto>> response) {
                if (!response.isSuccessful() || response.body() == null || response.body().isEmpty()) {
                    finishLoading();
                    addSection(R.string.st_most_engaged);
                    addParagraph(R.string.st_no_engagement);
                    return;
                }
                List<String> ids = new ArrayList<>();
                for (PostDto dto : response.body()) {
                    if (dto != null && dto.getId() != null) ids.add(dto.getId());
                }
                if (ids.isEmpty()) {
                    finishLoading();
                    addSection(R.string.st_most_engaged);
                    addParagraph(R.string.st_no_engagement);
                    return;
                }
                String in = "in.(" + android.text.TextUtils.join(",", ids) + ")";
                Map<String, Integer> scores = new LinkedHashMap<>();

                Map<String, String> likesQuery = new HashMap<>();
                likesQuery.put("post_id", in);
                likesQuery.put("select", "user_id");
                likesQuery.put("limit", "5000");
                api.getLikes(likesQuery).enqueue(new Callback<List<Map<String, Object>>>() {
                    @Override
                    public void onResponse(Call<List<Map<String, Object>>> c,
                                           Response<List<Map<String, Object>>> r) {
                        count(r, scores, true);
                        loadCommentScores(in, scores);
                    }

                    @Override
                    public void onFailure(Call<List<Map<String, Object>>> c, Throwable t) {
                        loadCommentScores(in, scores);
                    }
                });
            }

            @Override
            public void onFailure(Call<List<PostDto>> call, Throwable t) {
                finishLoading();
                addSection(R.string.st_most_engaged);
                addParagraph(R.string.st_no_engagement);
            }
        });
    }

    private void loadCommentScores(String in, Map<String, Integer> scores) {
        Map<String, String> query = new HashMap<>();
        query.put("post_id", in);
        query.put("select", "user_id");
        query.put("limit", "5000");
        api.getCommentsRaw(query).enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> c,
                                   Response<List<Map<String, Object>>> r) {
                count(r, scores, false);
                renderEngagement(scores);
            }

            @Override
            public void onFailure(Call<List<Map<String, Object>>> c, Throwable t) {
                renderEngagement(scores);
            }
        });
    }

    private void count(Response<List<Map<String, Object>>> response,
                       Map<String, Integer> scores, boolean isLike) {
        if (!response.isSuccessful() || response.body() == null) return;
        for (Map<String, Object> row : response.body()) {
            if (isLike) likesTotal++;
            else commentsTotal++;
            Object user = row.get("user_id");
            if (user == null) continue;
            String id = String.valueOf(user);
            if (id.equals(myId)) continue;
            Integer current = scores.get(id);
            scores.put(id, current == null ? 1 : current + 1);
        }
    }

    private void renderEngagement(Map<String, Integer> scores) {
        List<Map.Entry<String, Integer>> ranked = new ArrayList<>(scores.entrySet());
        Collections.sort(ranked, (a, b) -> Integer.compare(b.getValue(), a.getValue()));

        int accent = resolveThemeColor(R.attr.themePrimary, R.color.color_primary);
        int accentSoft = withAlpha(accent, 150);

        if (ranked.isEmpty()) {
            finishLoading();
            addSection(R.string.st_most_engaged);
            addParagraph(R.string.st_no_engagement);
            addParagraph(R.string.st_audience_demographics_note);
            return;
        }

        /* Top five accounts by interactions, as bars. */
        int top = Math.min(5, ranked.size());
        float[] values = new float[top];
        String[] labels = new String[top];
        List<String> topIds = new ArrayList<>();
        long topFiveTotal = 0L;
        for (int i = 0; i < top; i++) {
            values[i] = ranked.get(i).getValue();
            topFiveTotal += ranked.get(i).getValue();
            // Rank rather than username: the test accounts differ only in their last
            // character, so truncating five usernames produced five identical
            // "novatest…" labels. The list underneath names them in this order.
            labels[i] = "#" + (i + 1);
            topIds.add(ranked.get(i).getKey());
        }
        addSection(R.string.st_audience_engagement_chart);
        InsightsChartView rankedChart = addChart(InsightsChartView.Mode.BAR);
        rankedChart.setEmptyText(getString(R.string.st_audience_no_chart));
        rankedChart.setValues(values, labels);

        /*
         * How concentrated the audience is: the share of all interactions that came
         * from the five most active accounts. Instagram splits likes from comments
         * here, but that split is already the ring on the insights screen — repeating
         * it would show the same 39 twice on two adjacent pages.
         */
        addSection(R.string.st_audience_split);
        InsightsChartView split = addChart(InsightsChartView.Mode.DONUT);
        split.setEmptyText(getString(R.string.st_audience_no_chart));
        long everyoneElse = Math.max(0L, (likesTotal + commentsTotal) - topFiveTotal);
        split.setDonut(new float[]{topFiveTotal, everyoneElse},
                new int[]{accent, accentSoft},
                format(likesTotal + commentsTotal),
                getString(R.string.st_insights_interactions));
        addLegend(new String[]{getString(R.string.st_audience_top_five),
                getString(R.string.st_audience_others)}, new int[]{accent, accentSoft});

        addSection(R.string.st_most_engaged);

        Map<String, String> query = new HashMap<>();
        query.put("id", "in.(" + android.text.TextUtils.join(",", topIds) + ")");
        query.put("select", "id,username,full_name,avatar_url");
        api.getProfiles(query).enqueue(new Callback<List<ProfileDto>>() {
            @Override
            public void onResponse(Call<List<ProfileDto>> call,
                                   Response<List<ProfileDto>> response) {
                finishLoading();
                if (!response.isSuccessful() || response.body() == null) {
                    addParagraph(R.string.st_no_engagement);
                    return;
                }
                Map<String, Profile> profiles = new HashMap<>();
                for (ProfileDto dto : response.body()) {
                    if (dto != null) profiles.put(dto.getId(), dto.toDomain());
                }

                for (String id : topIds) {
                    Profile profile = profiles.get(id);
                    if (profile == null) continue;
                    addRow("@" + profile.getUsername(),
                            getString(R.string.st_engagement_count, scores.get(id)),
                            R.drawable.ic_person_circle, v -> {
                                Intent intent = new Intent(AudienceAnalyticsActivity.this,
                                        UserProfileActivity.class);
                                intent.putExtra(UserProfileActivity.EXTRA_USER_ID, profile.getId());
                                intent.putExtra(UserProfileActivity.EXTRA_USERNAME,
                                        profile.getUsername());
                                startActivity(intent);
                            });
                }
                addParagraph(R.string.st_audience_note);
                addParagraph(R.string.st_audience_demographics_note);
            }

            @Override
            public void onFailure(Call<List<ProfileDto>> call, Throwable t) {
                finishLoading();
                addParagraph(R.string.st_no_engagement);
            }
        });
    }

    private int withAlpha(int color, int alpha) {
        return android.graphics.Color.argb(alpha,
                android.graphics.Color.red(color),
                android.graphics.Color.green(color),
                android.graphics.Color.blue(color));
    }

    private String format(long value) {
        if (value >= 1_000_000L) {
            return String.format(Locale.US, "%.1fM", value / 1_000_000d);
        }
        if (value >= 1_000L) {
            return String.format(Locale.US, "%.1fK", value / 1_000d);
        }
        return String.valueOf(value);
    }

    private void finishLoading() {
        showLoading(false);
    }
}
