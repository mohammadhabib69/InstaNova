package com.insta.nova.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.tabs.TabLayout;
import com.insta.nova.R;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.model.Post;
import com.insta.nova.data.remote.RetrofitClient;
import com.insta.nova.data.remote.api.RestApi;
import com.insta.nova.data.remote.dto.CommentDto;
import com.insta.nova.data.repository.PostRepository;
import com.insta.nova.ui.post.PostDetailActivity;
import com.insta.nova.ui.profile.ProfileFragment;
import com.insta.nova.util.GlideUtils;
import com.insta.nova.util.Resource;
import com.insta.nova.util.TimeUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Your likes and comments, read from the real likes and v_comments rows. */
public class YourActivityActivity extends AppCompatActivity {

    private RecyclerView rvGrid;
    private RecyclerView rvComments;
    private TextView empty;
    private GridAdapter gridAdapter;
    private CommentAdapter commentAdapter;
    private PostRepository postRepository;
    private RestApi restApi;
    private String myId;
    private int tab = 0;

    private final List<Post> liked = new ArrayList<>();
    private final List<CommentDto> comments = new ArrayList<>();
    private final Map<String, Post> commentPosts = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_your_activity);

        postRepository = new PostRepository(this);
        restApi = RetrofitClient.getInstance(this).getRestApi();
        myId = SessionManager.getInstance(this).getUserId();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        rvGrid = findViewById(R.id.rvGrid);
        rvComments = findViewById(R.id.rvComments);
        empty = findViewById(R.id.tvEmpty);

        rvGrid.setLayoutManager(new GridLayoutManager(this, 3));
        rvGrid.addItemDecoration(new ProfileFragment.GridGapDecoration(
                getResources().getDimensionPixelSize(R.dimen.profile_grid_gap)));
        gridAdapter = new GridAdapter();
        rvGrid.setAdapter(gridAdapter);

        rvComments.setLayoutManager(new LinearLayoutManager(this));
        commentAdapter = new CommentAdapter();
        rvComments.setAdapter(commentAdapter);

        TabLayout tabs = findViewById(R.id.tabActivity);
        tabs.addTab(tabs.newTab().setText(R.string.st_liked_tab));
        tabs.addTab(tabs.newTab().setText(R.string.st_comments_tab));
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab t) {
                tab = t.getPosition();
                render();
            }
            @Override public void onTabUnselected(TabLayout.Tab t) {}
            @Override public void onTabReselected(TabLayout.Tab t) {}
        });

        loadLiked();
        loadComments();
    }

    private void render() {
        boolean likedTab = tab == 0;
        rvGrid.setVisibility(likedTab && !liked.isEmpty() ? View.VISIBLE : View.GONE);
        rvComments.setVisibility(!likedTab && !comments.isEmpty() ? View.VISIBLE : View.GONE);
        boolean isEmpty = likedTab ? liked.isEmpty() : comments.isEmpty();
        empty.setText(likedTab ? R.string.st_no_liked : R.string.st_no_comments);
        empty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
    }

    private void loadLiked() {
        Map<String, String> query = new HashMap<>();
        query.put("user_id", "eq." + myId);
        query.put("select", "post_id");
        query.put("order", "created_at.desc");
        query.put("limit", "100");
        restApi.getLikes(query).enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> call, Response<List<Map<String, Object>>> response) {
                if (!response.isSuccessful() || response.body() == null) { render(); return; }
                List<String> ids = new ArrayList<>();
                for (Map<String, Object> row : response.body()) {
                    Object id = row.get("post_id");
                    if (id != null) ids.add(String.valueOf(id));
                }
                if (ids.isEmpty()) { render(); return; }
                postRepository.getPostsByIds(ids).observe(YourActivityActivity.this, resource -> {
                    if (resource == null || resource.status != Resource.Status.SUCCESS || resource.data == null) {
                        render();
                        return;
                    }
                    liked.clear();
                    liked.addAll(resource.data);
                    gridAdapter.notifyDataSetChanged();
                    render();
                });
            }

            @Override
            public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                render();
            }
        });
    }

    private void loadComments() {
        Map<String, String> query = new HashMap<>();
        query.put("user_id", "eq." + myId);
        query.put("order", "created_at.desc");
        query.put("limit", "100");
        restApi.getCommentsByUser(query).enqueue(new Callback<List<CommentDto>>() {
            @Override
            public void onResponse(Call<List<CommentDto>> call, Response<List<CommentDto>> response) {
                if (!response.isSuccessful() || response.body() == null) { render(); return; }
                comments.clear();
                List<String> postIds = new ArrayList<>();
                for (CommentDto dto : response.body()) {
                    if (dto == null) continue;
                    comments.add(dto);
                    if (dto.getPostId() != null && !postIds.contains(dto.getPostId())) {
                        postIds.add(dto.getPostId());
                    }
                }
                commentAdapter.notifyDataSetChanged();
                render();
                if (!postIds.isEmpty()) loadCommentPosts(postIds);
            }

            @Override
            public void onFailure(Call<List<CommentDto>> call, Throwable t) {
                render();
            }
        });
    }

    private void loadCommentPosts(List<String> ids) {
        postRepository.getPostsByIds(ids).observe(this, resource -> {
            if (resource == null || resource.status != Resource.Status.SUCCESS || resource.data == null) return;
            commentPosts.clear();
            for (Post post : resource.data) commentPosts.put(post.getId(), post);
            commentAdapter.notifyDataSetChanged();
        });
    }

    private void openPost(String postId) {
        Post post = commentPosts.get(postId);
        if (post == null) return;
        Intent intent = new Intent(this, PostDetailActivity.class);
        intent.putExtra("post", post);
        intent.putExtra("post_id", post.getId());
        startActivity(intent);
    }

    private final class GridAdapter extends RecyclerView.Adapter<GridAdapter.Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_saved_media, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Post post = liked.get(position);
            holder.multi.setVisibility(View.GONE);
            holder.reel.setVisibility(post.isReel() ? View.VISIBLE : View.GONE);
            String url = post.getThumbnailUrl();
            if (url == null || url.isEmpty()) url = post.getFirstMediaUrl();
            GlideUtils.load(holder.image, "posts", url);
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(YourActivityActivity.this, PostDetailActivity.class);
                intent.putExtra("post", post);
                intent.putExtra("post_id", post.getId());
                startActivity(intent);
            });
        }

        @Override public int getItemCount() { return liked.size(); }

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

    private final class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_my_comment, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            CommentDto comment = comments.get(position);
            holder.text.setText(comment.getText());
            Post post = commentPosts.get(comment.getPostId());
            String url = post == null ? null : (post.getThumbnailUrl() != null
                    ? post.getThumbnailUrl() : post.getFirstMediaUrl());
            if (url == null || url.isEmpty()) holder.thumb.setImageResource(R.drawable.ic_image);
            else GlideUtils.load(holder.thumb, "posts", url);
            long ts = TimeUtils.parseTimestamp(comment.getCreatedAt());
            holder.meta.setText(ts > 0 ? TimeUtils.formatCompactRelativeTime(ts) : "");
            holder.itemView.setOnClickListener(v -> openPost(comment.getPostId()));
        }

        @Override public int getItemCount() { return comments.size(); }

        final class Holder extends RecyclerView.ViewHolder {
            final ImageView thumb;
            final TextView text;
            final TextView meta;
            Holder(@NonNull View itemView) {
                super(itemView);
                thumb = itemView.findViewById(R.id.ivThumb);
                text = itemView.findViewById(R.id.tvComment);
                meta = itemView.findViewById(R.id.tvMeta);
            }
        }
    }
}
