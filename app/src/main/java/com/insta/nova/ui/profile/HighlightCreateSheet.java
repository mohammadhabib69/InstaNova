package com.insta.nova.ui.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.insta.nova.BuildConfig;
import com.insta.nova.R;
import com.insta.nova.data.local.LocalSocialStore;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.model.Highlight;
import com.insta.nova.data.model.HighlightItem;
import com.insta.nova.data.model.Story;
import com.insta.nova.data.remote.RetrofitClient;
import com.insta.nova.databinding.BottomSheetHighlightCreateBinding;
import com.insta.nova.util.TimeUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Creates a highlight from archived (past) stories: name field plus a selectable
 * grid of thumbnails. Saved locally through {@link LocalSocialStore}.
 */
public class HighlightCreateSheet extends BottomSheetDialogFragment {

    private BottomSheetHighlightCreateBinding binding;
    private final List<Story> stories = new ArrayList<>();
    private final Set<Integer> selected = new HashSet<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetHighlightCreateBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        LocalSocialStore store = new LocalSocialStore(requireContext());
        stories.addAll(store.getPastStories());

        PickerAdapter adapter = new PickerAdapter();
        binding.rvHighlightStories.setLayoutManager(new GridLayoutManager(requireContext(), 3));
        binding.rvHighlightStories.setAdapter(adapter);

        binding.btnSaveHighlight.setOnClickListener(v -> save(store));
    }

    private void save(LocalSocialStore store) {
        if (selected.isEmpty()) {
            Toast.makeText(requireContext(), R.string.highlight_pick_one, Toast.LENGTH_SHORT).show();
            return;
        }
        String name = binding.etHighlightName.getText().toString().trim();
        if (name.isEmpty()) name = getString(R.string.highlight_default_name);

        String id = "local_highlight_" + UUID.randomUUID();
        Highlight highlight = new Highlight(id,
                SessionManager.getInstance(requireContext()).getUserId(), name, null);
        highlight.setCreatedAt(String.valueOf(System.currentTimeMillis()));

        List<HighlightItem> items = new ArrayList<>();
        String cover = null;
        for (int i = 0; i < stories.size(); i++) {
            if (!selected.contains(i)) continue;
            Story story = stories.get(i);
            if (cover == null) cover = story.getMediaUrl();
            items.add(new HighlightItem(id + "_" + i, id, story.getMediaUrl()));
        }
        highlight.setCoverUrl(cover);
        highlight.setItems(items);

        store.addHighlight(highlight);

        // Local-only highlights never left this device: a friend opening the profile saw an empty
        // row, and reinstalling lost them. Two things were missing here: the stories that never
        // finished uploading were simply skipped, and nothing ever tried again. Upload them now
        // and mirror, so what the picker promised ("Saved") is what other people can actually see.
        List<HighlightItem> unresolved = new ArrayList<>();
        for (HighlightItem item : items) {
            if (resolveMedia(item.getMediaUrl()) == null) unresolved.add(item);
        }
        final String highlightName = name;
        uploadMissing(unresolved, 0, () -> finishMirror(highlightName, items));
    }

    /**
     * Uploads, one at a time, the stories that never made it to Storage. Failures are logged and
     * skipped: a highlight with three of four slides is better than a save that errors out.
     */
    private void uploadMissing(List<HighlightItem> missing, int index, Runnable onDone) {
        if (index >= missing.size() || !isAdded()) {
            onDone.run();
            return;
        }
        final File file = localFile(missing.get(index).getMediaUrl());
        if (file == null || !file.exists() || file.length() == 0) {
            uploadMissing(missing, index + 1, onDone);
            return;
        }
        String userId = SessionManager.getInstance(requireContext()).getUserId();
        if (userId == null || userId.isEmpty()) {
            onDone.run();
            return;
        }
        boolean video = file.getName().toLowerCase().endsWith(".mp4");
        String extension = video ? ".mp4" : ".jpg";
        String path = userId + "/hl_" + System.currentTimeMillis() + extension;
        RequestBody body = RequestBody.create(
                MediaType.parse(video ? "video/mp4" : "image/jpeg"), file);

        RetrofitClient.getInstance(requireContext()).getStorageApi()
                .uploadFile("stories", path, body)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                        if (response.isSuccessful()) {
                            String base = BuildConfig.SUPABASE_URL == null
                                    ? "" : BuildConfig.SUPABASE_URL.trim();
                            while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
                            // Remember the mapping, so the same local file is never re-uploaded
                            // when another highlight picks it.
                            requireContext().getSharedPreferences("story_urls",
                                            android.content.Context.MODE_PRIVATE)
                                    .edit().putString("story_url_" + file.getName(),
                                            base + "/storage/v1/object/public/stories/" + path)
                                    .apply();
                        } else {
                            android.util.Log.w("InstaApi",
                                    "highlight mirror upload failed: HTTP " + response.code());
                        }
                        uploadMissing(missing, index + 1, onDone);
                    }

                    @Override
                    public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                        android.util.Log.w("InstaApi",
                                "highlight mirror upload failed: " + t.getMessage());
                        uploadMissing(missing, index + 1, onDone);
                    }
                });
    }

    /** The local file behind a story row, whether it is stored as a raw path or a file:// URI. */
    private File localFile(String mediaUrl) {
        if (mediaUrl == null || mediaUrl.isEmpty()) return null;
        if (mediaUrl.startsWith("http://") || mediaUrl.startsWith("https://")) return null;
        String path = mediaUrl.startsWith("file://")
                ? android.net.Uri.parse(mediaUrl).getPath() : mediaUrl;
        return path == null ? null : new File(path);
    }

    /** Mirrors whatever resolved to a real URL and reports exactly what the user ended up with. */
    private void finishMirror(String name, List<HighlightItem> items) {
        if (!isAdded()) return;
        List<String> urls = new ArrayList<>();
        for (HighlightItem item : items) {
            String resolved = resolveMedia(item.getMediaUrl());
            if (resolved != null) urls.add(resolved);
        }
        String coverUrl = null;
        for (String url : urls) { coverUrl = url; break; }

        if (coverUrl == null || urls.isEmpty()) {
            Toast.makeText(requireContext(),
                    "Saved on this device only — these stories never finished uploading.",
                    Toast.LENGTH_LONG).show();
            dismiss();
            return;
        }
        new com.insta.nova.data.repository.HighlightRepository(requireContext())
                .createHighlight(name, coverUrl, urls);
        if (urls.size() < items.size()) {
            Toast.makeText(requireContext(),
                    "Saved — " + (items.size() - urls.size())
                            + " story(ies) stayed on this device because they could not upload.",
                    Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(requireContext(), R.string.highlight_saved, Toast.LENGTH_SHORT).show();
        }
        dismiss();
    }

    /**
     * Stories live on this device as {@code files/stories/story_<ts>.jpg}. Publishing that path
     * verbatim produced highlights nobody else could load. The publish step records the public URL
     * per file name; anything without a mapping is skipped rather than written as a dead link.
     */
    private String resolveMedia(String url) {
        if (url == null || url.isEmpty()) return null;
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        String name = url.substring(url.lastIndexOf('/') + 1);
        String published = requireContext()
                .getSharedPreferences("story_urls", android.content.Context.MODE_PRIVATE)
                .getString("story_url_" + name, null);
        return published;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private class PickerAdapter extends RecyclerView.Adapter<PickerAdapter.Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_highlight_pick, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Story story = stories.get(position);
            Glide.with(holder.ivThumb).load(story.getMediaUrl()).into(holder.ivThumb);
            holder.cbSelect.setChecked(selected.contains(position));
            holder.itemView.setOnClickListener(v -> {
                if (selected.contains(position)) selected.remove(position);
                else selected.add(position);
                notifyItemChanged(position);
            });
        }

        @Override
        public int getItemCount() {
            return stories.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final ImageView ivThumb;
            final CheckBox cbSelect;

            Holder(@NonNull View itemView) {
                super(itemView);
                ivThumb = itemView.findViewById(R.id.ivThumb);
                cbSelect = itemView.findViewById(R.id.cbSelect);
            }
        }
    }
}
