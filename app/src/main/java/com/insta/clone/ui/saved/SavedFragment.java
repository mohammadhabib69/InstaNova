package com.insta.clone.ui.saved;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.insta.clone.R;
import com.insta.clone.data.model.Post;
import com.insta.clone.data.repository.PostRepository;
import com.insta.clone.databinding.FragmentSavedBinding;
import com.insta.clone.databinding.ItemSavedMediaBinding;
import com.insta.clone.util.Resource;
import java.util.ArrayList;
import java.util.List;

/** Saved posts grid sourced from v_saved. */
public class SavedFragment extends Fragment {
    private FragmentSavedBinding binding;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater i, @Nullable ViewGroup p, @Nullable Bundle s) { binding = FragmentSavedBinding.inflate(i,p,false); return binding.getRoot(); }
    @Override public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        binding.rvSaved.setLayoutManager(new GridLayoutManager(requireContext(), 3)); SavedAdapter adapter = new SavedAdapter(); binding.rvSaved.setAdapter(adapter);
        new PostRepository(requireContext()).getSavedPosts().observe(getViewLifecycleOwner(), r -> { if (r.status == Resource.Status.SUCCESS) { adapter.submit(r.data); binding.tvEmpty.setVisibility(r.data == null || r.data.isEmpty() ? View.VISIBLE : View.GONE); } });
    }
    @Override public void onDestroyView() { super.onDestroyView(); binding = null; }
    static class SavedAdapter extends RecyclerView.Adapter<SavedAdapter.Holder> {
        private final List<Post> posts = new ArrayList<>(); void submit(List<Post> p) { posts.clear(); if (p != null) posts.addAll(p); notifyDataSetChanged(); }
        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup p, int t) { return new Holder(ItemSavedMediaBinding.inflate(LayoutInflater.from(p.getContext()),p,false)); }
        @Override public void onBindViewHolder(@NonNull Holder h, int pos) {
            Post post = posts.get(pos);
            com.insta.clone.util.GlideUtils.load(h.binding.ivMedia, "posts", post.getFirstMediaUrl());
            h.binding.getRoot().setOnClickListener(v -> {
                android.content.Intent intent = new android.content.Intent(v.getContext(), com.insta.clone.ui.post.PostDetailActivity.class);
                intent.putExtra("post", post);
                intent.putExtra("post_id", post.getId());
                v.getContext().startActivity(intent);
            });
        }
        @Override public int getItemCount() { return posts.size(); }
        static class Holder extends RecyclerView.ViewHolder { final ItemSavedMediaBinding binding; Holder(ItemSavedMediaBinding b) { super(b.getRoot()); binding=b; } }
    }
}
