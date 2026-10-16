package com.insta.clone.ui.settings;

import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.insta.clone.R;
import com.insta.clone.data.model.Story;
import com.insta.clone.data.repository.SettingsRepository;
import com.insta.clone.databinding.ActivityMediaGridBinding;
import com.insta.clone.databinding.ItemSavedMediaBinding;
import com.insta.clone.ui.story.StoryViewerActivity;
import com.insta.clone.util.GlideUtils;
import com.insta.clone.util.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * Settings > Archive: a 3-column grid of every story I posted, expired rows
 * included. Tapping one opens it in the story viewer.
 */
public class ArchiveActivity extends AppCompatActivity {

    private ActivityMediaGridBinding binding;
    private Adapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMediaGridBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.tvTitle.setText(R.string.settings_archive_title);
        binding.tvEmpty.setText(R.string.settings_archive_empty);
        binding.btnBack.setOnClickListener(v -> finish());

        final int gap = getResources().getDimensionPixelSize(R.dimen.settings_grid_gap);
        binding.rvMedia.setLayoutManager(new GridLayoutManager(this, 3));
        binding.rvMedia.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                                       @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                outRect.set(gap, gap, gap, gap);
            }
        });
        adapter = new Adapter();
        binding.rvMedia.setAdapter(adapter);

        new SettingsRepository(this).getArchive().observe(this, resource -> {
            if (resource == null) return;
            binding.progressMedia.setVisibility(resource.status == Resource.Status.LOADING ? View.VISIBLE : View.GONE);
            if (resource.status == Resource.Status.SUCCESS && resource.data != null) {
                adapter.submit(resource.data);
                binding.tvEmpty.setVisibility(resource.data.isEmpty() ? View.VISIBLE : View.GONE);
            } else if (resource.status == Resource.Status.ERROR) {
                binding.tvEmpty.setText(resource.message == null
                        ? getString(R.string.settings_archive_empty) : resource.message);
                binding.tvEmpty.setVisibility(View.VISIBLE);
            }
        });
    }

    private final class Adapter extends RecyclerView.Adapter<Adapter.Holder> {

        private final List<Story> stories = new ArrayList<>();

        void submit(List<Story> data) {
            stories.clear();
            if (data != null) stories.addAll(data);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemSavedMediaBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Story story = stories.get(position);
            GlideUtils.load(holder.binding.ivMedia, "stories", story.getMediaUrl());
            holder.binding.getRoot().setOnClickListener(v -> {
                Intent intent = new Intent(ArchiveActivity.this, StoryViewerActivity.class);
                intent.putExtra("user_id", story.getUserId());
                intent.putExtra("story_id", story.getId());
                intent.putExtra("username", story.getUsername());
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return stories.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ItemSavedMediaBinding binding;
            Holder(ItemSavedMediaBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
