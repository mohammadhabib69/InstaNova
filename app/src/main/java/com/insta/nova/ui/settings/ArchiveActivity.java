package com.insta.nova.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.tabs.TabLayout;
import com.insta.nova.R;
import com.insta.nova.data.model.Post;
import com.insta.nova.data.model.Story;
import com.insta.nova.data.remote.RetrofitClient;
import com.insta.nova.data.remote.api.RestApi;
import com.insta.nova.data.repository.PostRepository;
import com.insta.nova.data.repository.SettingsRepository;
import com.insta.nova.ui.profile.ProfileFragment;
import com.insta.nova.util.GlideUtils;
import com.insta.nova.util.Resource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Settings → Archive. Tabs Posts / Stories, each a real grid.
 *
 * <p>Posts are rows with {@code is_archived = true}; restoring clears the flag.
 * Stories are the real story rows, including expired ones; restoring moves
 * {@code expires_at} back into the 24h window so the story returns to the tray.
 */
public class ArchiveActivity extends AppCompatActivity {

    private static final int TAB_POSTS = 0;
    private static final int TAB_STORIES = 1;

    private RecyclerView rv;
    private TextView empty;
    private ArchiveAdapter adapter;
    private PostRepository postRepository;
    private SettingsRepository settingsRepository;
    private RestApi restApi;

    private final List<Post> posts = new ArrayList<>();
    private final List<Story> stories = new ArrayList<>();
    private int tab = TAB_POSTS;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_archive);

        postRepository = new PostRepository(this);
        settingsRepository = new SettingsRepository(this);
        restApi = RetrofitClient.getInstance(this).getRestApi();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        rv = findViewById(R.id.rvArchive);
        empty = findViewById(R.id.tvArchiveEmpty);

        rv.setLayoutManager(new GridLayoutManager(this, 3));
        rv.addItemDecoration(new ProfileFragment.GridGapDecoration(
                getResources().getDimensionPixelSize(R.dimen.profile_grid_gap)));
        adapter = new ArchiveAdapter();
        rv.setAdapter(adapter);

        TabLayout tabs = findViewById(R.id.tabArchive);
        tabs.addTab(tabs.newTab().setText(R.string.st_archive_posts));
        tabs.addTab(tabs.newTab().setText(R.string.st_archive_stories));
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab selected) {
                tab = selected.getPosition();
                load();
            }
            @Override public void onTabUnselected(TabLayout.Tab t) {}
            @Override public void onTabReselected(TabLayout.Tab t) {}
        });

        load();
    }

    private void load() {
        if (tab == TAB_POSTS) loadPosts(); else loadStories();
    }

    private void loadPosts() {
        posts.clear();
        adapter.notifyDataSetChanged();
        String userId = settingsRepository.getUserId();
        if (userId == null || userId.isEmpty()) {
            showEmpty();
            return;
        }
        postRepository.getArchivedPosts(userId).observe(this, resource -> {
            if (resource == null || resource.status != Resource.Status.SUCCESS || resource.data == null) {
                if (resource != null && resource.status == Resource.Status.ERROR) showEmpty();
                return;
            }
            posts.clear();
            posts.addAll(resource.data);
            adapter.notifyDataSetChanged();
            empty.setText(R.string.st_archive_posts_empty);
            empty.setVisibility(posts.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    private void loadStories() {
        stories.clear();
        adapter.notifyDataSetChanged();
        settingsRepository.getArchive().observe(this, resource -> {
            if (resource == null || resource.status != Resource.Status.SUCCESS || resource.data == null) {
                if (resource != null && resource.status == Resource.Status.ERROR) showEmpty();
                return;
            }
            stories.clear();
            stories.addAll(resource.data);
            adapter.notifyDataSetChanged();
            empty.setText(R.string.st_archive_stories_empty);
            empty.setVisibility(stories.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    private void showEmpty() {
        empty.setText(tab == TAB_POSTS ? R.string.st_archive_posts_empty
                : R.string.st_archive_stories_empty);
        empty.setVisibility(View.VISIBLE);
    }

    private void openPostActions(Post post) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.st_post_options)
                .setItems(new String[]{getString(R.string.st_unarchive), getString(R.string.st_delete_item)}, (d, which) -> {
                    if (which == 0) {
                        postRepository.setArchived(post.getId(), false).observe(this, r -> {
                            if (r != null && r.status == Resource.Status.SUCCESS) {
                                Toast.makeText(this, R.string.st_unarchived_toast, Toast.LENGTH_SHORT).show();
                                loadPosts();
                            }
                        });
                    } else {
                        postRepository.deletePost(post.getId()).observe(this, r -> {
                            if (r != null && r.status == Resource.Status.SUCCESS) {
                                Toast.makeText(this, R.string.st_deleted_toast, Toast.LENGTH_SHORT).show();
                                loadPosts();
                            }
                        });
                    }
                })
                .show();
    }

    private void openStoryActions(Story story) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.st_archive_stories)
                .setItems(new String[]{getString(R.string.st_unarchive), getString(R.string.st_delete_item)}, (d, which) -> {
                    if (which == 0) {
                        Map<String, Object> body = new HashMap<>();
                        body.put("is_archived", false);
                        // UTC, not the device clock: a local timestamp labelled 'Z' shifted
                        // the restored 24h window by the timezone offset.
                        java.text.SimpleDateFormat format = new java.text.SimpleDateFormat(
                                "yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US);
                        format.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                        body.put("expires_at", format.format(
                                new java.util.Date(System.currentTimeMillis() + 24L * 60 * 60 * 1000)));
                        restApi.updateStory("eq." + story.getId(), body).enqueue(new Callback<Void>() {
                            @Override public void onResponse(Call<Void> call, Response<Void> response) {
                                if (response.isSuccessful()) {
                                    Toast.makeText(ArchiveActivity.this, R.string.st_unarchived_toast,
                                            Toast.LENGTH_SHORT).show();
                                    loadStories();
                                } else {
                                    Toast.makeText(ArchiveActivity.this, R.string.st_something_went_wrong,
                                            Toast.LENGTH_LONG).show();
                                }
                            }
                            @Override public void onFailure(Call<Void> call, Throwable t) {
                                Toast.makeText(ArchiveActivity.this, R.string.st_network_error,
                                        Toast.LENGTH_LONG).show();
                            }
                        });
                    } else {
                        restApi.deleteStory("eq." + story.getId()).enqueue(new Callback<Void>() {
                            @Override public void onResponse(Call<Void> call, Response<Void> response) {
                                if (response.isSuccessful()) loadStories();
                            }
                            @Override public void onFailure(Call<Void> call, Throwable t) {
                                Toast.makeText(ArchiveActivity.this, R.string.st_network_error,
                                        Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                })
                .show();
    }

    private final class ArchiveAdapter extends RecyclerView.Adapter<ArchiveAdapter.Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_saved_media, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            holder.multi.setVisibility(View.GONE);
            holder.reel.setVisibility(View.GONE);
            if (tab == TAB_POSTS) {
                Post post = posts.get(position);
                String url = post.getThumbnailUrl();
                if (url == null || url.isEmpty()) url = post.getFirstMediaUrl();
                GlideUtils.load(holder.image, "posts", url);
                holder.itemView.setOnClickListener(v -> openPostActions(post));
            } else {
                Story story = stories.get(position);
                GlideUtils.load(holder.image, "stories", story.getMediaUrl());
                holder.itemView.setOnClickListener(v -> openStoryActions(story));
            }
        }

        @Override
        public int getItemCount() {
            return tab == TAB_POSTS ? posts.size() : stories.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ImageView image;
            final ImageView multi;
            final ImageView reel;
            Holder(@NonNull View itemView) {
                super(itemView);
                image = itemView.findViewById(R.id.ivMedia);
                multi = itemView.findViewById(R.id.ivMultiBadge);
                reel = itemView.findViewById(R.id.ivReelBadge);
            }
        }
    }
}
