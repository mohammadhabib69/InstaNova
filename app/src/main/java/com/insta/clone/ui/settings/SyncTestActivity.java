package com.insta.clone.ui.settings;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.util.Base64;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.gson.Gson;
import com.insta.clone.BuildConfig;
import com.insta.clone.data.local.SessionManager;
import com.insta.clone.data.remote.RetrofitClient;
import com.insta.clone.data.remote.api.AuthApi;
import com.insta.clone.data.remote.api.RestApi;
import com.insta.clone.data.remote.api.StorageApi;
import com.insta.clone.data.remote.dto.TokenResponse;
import com.insta.clone.databinding.ActivitySyncTestBinding;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Debug network smoke test; each result reports HTTP status and Gson parsing. */
public class SyncTestActivity extends AppCompatActivity {
    private ActivitySyncTestBinding binding;
    private RestApi restApi; private AuthApi authApi; private StorageApi storageApi; private SessionManager session;
    private final StringBuilder report = new StringBuilder();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); binding = ActivitySyncTestBinding.inflate(getLayoutInflater()); setContentView(binding.getRoot());
        session = SessionManager.getInstance(this); showConfig();
        try { RetrofitClient c = RetrofitClient.getInstance(this); restApi = c.getRestApi(); authApi = c.getAuthApi(); storageApi = c.getStorageApi(); }
        catch (Exception e) { append("CONFIG | FAIL | " + e.getMessage()); }
        binding.btnRunChecks.setOnClickListener(v -> runChecks());
        binding.btnCopyReport.setOnClickListener(v -> { ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE); if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Insta_Clone sync report", report.toString())); Toast.makeText(this, "Report copied", Toast.LENGTH_SHORT).show(); });
    }

    private void showConfig() {
        String key = BuildConfig.SUPABASE_ANON_KEY == null ? "" : BuildConfig.SUPABASE_ANON_KEY;
        String token = session.getAccessToken();
        binding.tvConfig.setText("Host: " + BuildConfig.SUPABASE_URL + "\nKey: " + (key.startsWith("sb_publishable_") ? "sb_publishable_…" : "JWT anon") + "\nSession: " + (token != null && !token.isEmpty()) + "  User: " + session.getUserId() + "  Token expiry: " + (token == null ? "n/a" : jwtExpiry(token)));
    }

    private void runChecks() {
        report.setLength(0); binding.tvReport.setText(""); showConfig();
        if (restApi == null || authApi == null) { append("CONFIG | FAIL | Retrofit is not configured"); return; }
        append("CONFIG | OK | host/key/session configuration loaded"); authUser(); profiles(); feed(); stories(); notificationsAndInbox(); storage();
    }

    private void authUser() { authApi.getCurrentUser().enqueue(new Callback<TokenResponse.AuthUser>() {
        @Override public void onResponse(Call<TokenResponse.AuthUser> c, Response<TokenResponse.AuthUser> r) { row("GET /auth/v1/user", r, r.body() == null ? 0 : 1, r.body() != null); }
        @Override public void onFailure(Call<TokenResponse.AuthUser> c, Throwable t) { fail("GET /auth/v1/user", t); }
    }); }

    private void profiles() { Map<String,String> q = new HashMap<>(); q.put("id", "eq." + String.valueOf(session.getUserId())); restApi.getProfiles(q).enqueue(listCallback("GET /rest/v1/profiles?id=eq.{uid}")); }
    private void feed() { Map<String,String> q = new HashMap<>(); q.put("limit", "5"); q.put("order", "created_at.desc"); restApi.getFeed(q).enqueue(listCallback("GET /rest/v1/v_feed?limit=5")); restApi.getPosts(q).enqueue(listCallback("GET /rest/v1/v_posts?limit=5")); }
    private void stories() { restApi.getStoryGroups(Collections.emptyMap()).enqueue(listCallback("POST /rest/v1/rpc/get_story_groups")); restApi.getStories(Collections.singletonMap("limit", "5")).enqueue(listCallback("GET /rest/v1/v_stories?limit=5")); }
    private void notificationsAndInbox() { restApi.getNotifications(Collections.singletonMap("limit", "5")).enqueue(listCallback("GET /rest/v1/notifications?limit=5")); restApi.getInbox(Collections.emptyMap()).enqueue(listCallback("POST /rest/v1/rpc/get_inbox")); }

    private void storage() {
        if (storageApi == null || session.getUserId() == null) { append("STORAGE | FAIL | no session/user id"); return; }
        String path = session.getUserId() + "/synctest-" + UUID.randomUUID() + ".png";
        byte[] pixel = Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=", Base64.DEFAULT);
        storageApi.uploadFile("avatars", path, RequestBody.create(MediaType.parse("image/png"), pixel)).enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> c, Response<Void> r) {
                if (!r.isSuccessful()) { row("POST /storage/v1/object/avatars/{uid}/synctest.png", r, 0, false); return; }
                append("STORAGE upload | HTTP " + r.code() + " | public URL parsed: " + BuildConfig.SUPABASE_URL + "storage/v1/object/public/avatars/" + path);
                storageApi.deleteFile("avatars", path).enqueue(new Callback<Void>() {
                    @Override public void onResponse(Call<Void> c2, Response<Void> r2) { row("DELETE /storage/v1/object/avatars/{uid}/synctest.png", r2, 0, r2.isSuccessful()); }
                    @Override public void onFailure(Call<Void> c2, Throwable t) { fail("DELETE /storage/v1/object/avatars/{uid}/synctest.png", t); }
                });
            }
            @Override public void onFailure(Call<Void> c, Throwable t) { fail("POST /storage/v1/object/avatars/{uid}/synctest.png", t); }
        });
    }

    private <T> Callback<List<T>> listCallback(String endpoint) { return new Callback<List<T>>() {
        @Override public void onResponse(Call<List<T>> c, Response<List<T>> r) { row(endpoint, r, r.body() == null ? 0 : r.body().size(), r.body() != null); }
        @Override public void onFailure(Call<List<T>> c, Throwable t) { fail(endpoint, t); }
    }; }
    private void row(String endpoint, Response<?> r, int count, boolean parsed) { append(endpoint + " | HTTP " + r.code() + " | rows=" + count + " | Gson=" + parsed + (r.isSuccessful() ? "" : " | " + error(r))); }
    private void fail(String endpoint, Throwable t) { append(endpoint + " | NETWORK FAIL | Gson=false | " + t.getMessage()); }
    private void append(String line) { runOnUiThread(() -> { report.append(line).append('\n'); binding.tvReport.setText(report.toString()); }); }
    private static String error(Response<?> r) { try { return r.errorBody() == null ? "" : r.errorBody().string(); } catch (Exception e) { return e.getMessage(); } }
    private static String jwtExpiry(String token) { try { String[] p = token.split("\\."); String json = new String(Base64.decode(p[1], Base64.URL_SAFE), StandardCharsets.UTF_8); Object exp = new Gson().fromJson(json, Map.class).get("exp"); return String.valueOf(exp); } catch (Exception e) { return "unreadable"; } }
}
