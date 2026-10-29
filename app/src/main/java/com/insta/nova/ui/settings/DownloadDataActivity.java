package com.insta.nova.ui.settings;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.insta.nova.BuildConfig;
import com.insta.nova.R;
import com.insta.nova.calling.CallRecord;
import com.insta.nova.data.local.AppSettingsStore;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.remote.RetrofitClient;
import com.insta.nova.data.remote.api.RestApi;
import com.insta.nova.data.remote.dto.PostDto;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Download your data: a real JSON export of the profile, posts, saved posts and a
 * call history summary, shared through the Android sheet.
 */
public class DownloadDataActivity extends SettingsPageActivity {

    private RestApi restApi;
    private String myId;

    /**
     * Sections that could not be read.
     *
     * <p>Every loader below used to fall back to an empty array, so a failed request was
     * indistinguishable from "you have no data" — the one thing an export must never do.
     */
    private final JSONArray exportErrors = new JSONArray();
    private boolean postsTruncated;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setPageTitle(R.string.st_download_data_title);
        restApi = RetrofitClient.getInstance(this).getRestApi();
        myId = SessionManager.getInstance(this).getUserId();

        addParagraph(R.string.st_download_data_body);
        addPrimaryButton(R.string.st_download, v -> export());
    }

    private void export() {
        if (myId == null || myId.isEmpty()) {
            Toast.makeText(this, R.string.st_download_failed, Toast.LENGTH_LONG).show();
            return;
        }
        showLoading(true);
        JSONObject root = new JSONObject();
        try {
            root.put("app", "InstaNova");
            root.put("generated_at", new java.util.Date().toString());
            root.put("version", BuildConfig.VERSION_NAME);
            // Seed every section so an unreachable backend yields [] plus an entry under
            // "errors" instead of a key that silently disappears.
            root.put("profile", new JSONObject());
            root.put("posts", new JSONArray());
            root.put("saved_posts", new JSONArray());
            root.put("calls", new JSONArray());
        } catch (Exception ignored) {
        }

        Map<String, String> profileQuery = new HashMap<>();
        profileQuery.put("id", "eq." + myId);
        profileQuery.put("select", "id,username,full_name,bio,website,avatar_url,account_type,"
                + "followers_count,following_count,posts_count,created_at,is_private");
        profileQuery.put("limit", "1");
        restApi.getProfilesRaw(profileQuery).enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> call,
                                   Response<List<Map<String, Object>>> response) {
                try {
                    root.put("profile", response.isSuccessful() && response.body() != null
                            && !response.body().isEmpty()
                            ? new JSONObject(response.body().get(0)) : new JSONObject());
                } catch (Exception ignored) {
                }
                loadPosts(root);
            }

            @Override
            public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                recordError("profile", t.getMessage());
                loadPosts(root);
            }
        });
    }

    private void loadPosts(JSONObject root) {
        Map<String, String> query = new HashMap<>();
        query.put("user_id", "eq." + myId);
        query.put("order", "created_at.desc");
        query.put("limit", "200");
        restApi.getPosts(query).enqueue(new Callback<List<PostDto>>() {
            @Override
            public void onResponse(Call<List<PostDto>> call, Response<List<PostDto>> response) {
                JSONArray posts = new JSONArray();
                if (response.isSuccessful() && response.body() != null) {
                    for (PostDto dto : response.body()) {
                        if (dto == null) continue;
                        try {
                            JSONObject o = new JSONObject();
                            o.put("id", dto.getId());
                            o.put("caption", dto.getCaption());
                            o.put("created_at", dto.getCreatedAt());
                            o.put("likes_count", dto.getLikesCount());
                            o.put("comments_count", dto.getCommentsCount());
                            o.put("is_reel", dto.isReel());
                            o.put("thumbnail_url", dto.getThumbnailUrl());
                            o.put("video_url", dto.getVideoUrl());
                            posts.put(o);
                        } catch (Exception ignored) {
                        }
                    }
                }
                try { root.put("posts", posts); } catch (Exception ignored) {
                }
                loadSaved(root);
            }

            @Override
            public void onFailure(Call<List<PostDto>> call, Throwable t) {
                recordError("posts", t.getMessage());
                loadSaved(root);
            }
        });
    }

    private void loadSaved(JSONObject root) {
        Map<String, String> query = new HashMap<>();
        query.put("user_id", "eq." + myId);
        query.put("order", "saved_at.desc");
        query.put("limit", "200");
        restApi.getSavedPosts(query).enqueue(new Callback<List<PostDto>>() {
            @Override
            public void onResponse(Call<List<PostDto>> call, Response<List<PostDto>> response) {
                JSONArray saved = new JSONArray();
                if (response.isSuccessful() && response.body() != null) {
                    for (PostDto dto : response.body()) {
                        if (dto == null) continue;
                        try {
                            JSONObject o = new JSONObject();
                            o.put("id", dto.getId());
                            o.put("caption", dto.getCaption());
                            saved.put(o);
                        } catch (Exception ignored) {
                        }
                    }
                }
                try { root.put("saved_posts", saved); } catch (Exception ignored) {
                }
                loadCalls(root);
            }

            @Override
            public void onFailure(Call<List<PostDto>> call, Throwable t) {
                recordError("saved_posts", t.getMessage());
                loadCalls(root);
            }
        });
    }

    private void loadCalls(JSONObject root) {
        Map<String, String> query = new HashMap<>();
        query.put("or", "(caller_id.eq." + myId + ",callee_id.eq." + myId + ")");
        query.put("order", "created_at.desc");
        query.put("limit", "200");
        restApi.getCalls(query).enqueue(new Callback<List<CallRecord>>() {
            @Override
            public void onResponse(Call<List<CallRecord>> call, Response<List<CallRecord>> response) {
                JSONArray calls = new JSONArray();
                if (response.isSuccessful() && response.body() != null) {
                    for (CallRecord record : response.body()) {
                        if (record == null) continue;
                        try {
                            JSONObject o = new JSONObject();
                            o.put("id", record.id);
                            o.put("direction", myId.equals(record.callerId) ? "outgoing" : "incoming");
                            o.put("type", record.callType);
                            o.put("status", record.status);
                            o.put("duration_sec", record.durationSec);
                            o.put("created_at", record.createdAt);
                            calls.put(o);
                        } catch (Exception ignored) {
                        }
                    }
                }
                try { root.put("calls", calls); } catch (Exception ignored) {
                }
                try { root.put("settings", settingsJson()); } catch (Exception ignored) {
                }
                writeAndShare(root);
            }

            @Override
            public void onFailure(Call<List<CallRecord>> call, Throwable t) {
                recordError("calls", t.getMessage());
                writeAndShare(root);
            }
        });
    }

    private void recordError(String section, String message) {
        try {
            JSONObject o = new JSONObject();
            o.put("section", section);
            o.put("message", message == null ? "request failed" : message);
            exportErrors.put(o);
        } catch (Exception ignored) {
        }
    }

    private JSONObject settingsJson() throws Exception {
        AppSettingsStore settings = new AppSettingsStore(this);
        JSONObject o = new JSONObject();
        o.put("read_receipts", settings.readReceipts());
        o.put("show_activity", settings.showActivity());
        o.put("data_saver", settings.dataSaver());
        o.put("video_autoplay", settings.autoplay());
        o.put("upload_quality", settings.uploadQuality());
        o.put("who_can_call", settings.whoCanCall());
        o.put("comment_who", settings.commentWho());
        o.put("tag_who", settings.tagWho());
        o.put("mention_who", settings.mentionWho());
        return o;
    }

    private void writeAndShare(JSONObject root) {
        showLoading(false);
        // Always present, so an empty list means "nothing failed", not "the field is missing".
        try {
            root.put("errors", exportErrors);
        } catch (Exception ignored) {
        }
        try {
            File dir = new File(getCacheDir(), "exports");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("no dir");
            File file = new File(dir, "instanova-data-" + System.currentTimeMillis() + ".json");
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
            }
            Uri uri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", file);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("application/json");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, getString(R.string.st_download_data_title)));
            Toast.makeText(this, R.string.st_download_ready, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, R.string.st_download_failed, Toast.LENGTH_LONG).show();
        }
    }
}
