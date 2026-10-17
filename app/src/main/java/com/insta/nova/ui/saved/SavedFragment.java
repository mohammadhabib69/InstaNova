package com.insta.nova.ui.saved;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.insta.nova.R;
import com.insta.nova.data.local.LocalSocialStore;
import com.insta.nova.data.model.Post;
import com.insta.nova.data.repository.PostRepository;
import com.insta.nova.databinding.FragmentSavedBinding;
import com.insta.nova.databinding.ItemSavedMediaBinding;
import com.insta.nova.ui.post.PostDetailActivity;
import com.insta.nova.util.Resource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Saved posts grid.
 *
 * <p>Server rows come from {@code v_saved}; locally created posts never reach it, so
 * they are merged in from {@link LocalSocialStore}. A post the user unsaved is hidden
 * immediately even if the server list has not refreshed yet, which is what makes
 * "unsave from the feed" disappear here without a reload.
 */
public class SavedFragment extends Fragment {

    private FragmentSavedBinding binding;
    private LocalSocialStore localStore;
    private SavedAdapter adapter;
    private final List<Post> remoteSaved = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSavedBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        localStore = new LocalSocialStore(requireContext());
        // The grid is also a bottom-nav destination; only the profile host shows a
        // back arrow.
        View back = view.findViewById(R.id.btnSavedBack);
        if (back != null) {
            if (requireActivity() instanceof SavedActivity) {
                back.setVisibility(View.VISIBLE);
                back.setOnClickListener(v -> requireActivity().finish());
            } else {
                back.setVisibility(View.GONE);
            }
        }
        adapter = new SavedAdapter();
        binding.rvSaved.setLayoutManager(new GridLayoutManager(requireContext(), 3));
        binding.rvSaved.setAdapter(adapter);
        reload();

        new PostRepository(requireContext()).getSavedPosts().observe(getViewLifecycleOwner(), r -> {
            if (r == null || r.status != Resource.Status.SUCCESS) return;
            remoteSaved.clear();
            if (r.data != null) remoteSaved.addAll(r.data);
            reload();
        });

        // Saving or unsaving anywhere in the app rebinds this grid.
        LocalSocialStore.revision().observe(getViewLifecycleOwner(), revision -> reload());
    }

    private void reload() {
        if (binding == null || adapter == null) return;
        List<Post> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (Post local : LocalSocialStore.newestFirst(localStore.savedPosts(localStore.getPosts()))) {
            if (local == null || local.getId() == null) continue;
            out.add(local);
            seen.add(local.getId());
        }
        for (Post post : remoteSaved) {
            if (post == null || post.getId() == null) continue;
            if (seen.contains(post.getId())) continue;
            if (localStore.isExplicitlyUnsaved(post.getId())) continue;
            out.add(post);
        }

        adapter.submit(out);
        binding.tvEmpty.setVisibility(out.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        adapter = null;
    }

    static class SavedAdapter extends RecyclerView.Adapter<SavedAdapter.Holder> {
        private final List<Post> posts = new ArrayList<>();

        void submit(List<Post> p) {
            posts.clear();
            if (p != null) posts.addAll(p);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemSavedMediaBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            Post post = posts.get(position);
            String url = post.getThumbnailUrl();
            if (url == null || url.isEmpty()) url = post.getFirstMediaUrl();
            com.insta.nova.util.GlideUtils.load(h.binding.ivMedia, "posts", url);
            h.binding.getRoot().setOnClickListener(v -> {
                Intent intent = new Intent(v.getContext(), PostDetailActivity.class);
                intent.putExtra("post", post);
                intent.putExtra("post_id", post.getId());
                v.getContext().startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return posts.size();
        }

        static class Holder extends RecyclerView.ViewHolder {
            final ItemSavedMediaBinding binding;

            Holder(ItemSavedMediaBinding b) {
                super(b.getRoot());
                binding = b;
            }
        }
    }
}
