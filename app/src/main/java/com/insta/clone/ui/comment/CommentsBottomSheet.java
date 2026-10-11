package com.insta.clone.ui.comment;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.insta.clone.R;
import com.insta.clone.data.local.SessionManager;
import com.insta.clone.data.model.Comment;
import com.insta.clone.data.repository.CommentRepository;
import com.insta.clone.databinding.BottomSheetCommentsBinding;
import com.insta.clone.databinding.ItemCommentBinding;
import com.insta.clone.util.Resource;
import com.insta.clone.util.TimeUtils;
import java.util.ArrayList;
import java.util.List;

/**
 * BottomSheet displaying real threaded comments, emoji reaction bar, and comment input.
 * Fully backed by PostgREST v_comments / comments table.
 */
public class CommentsBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_POST_ID = "arg_post_id";
    private static final String ARG_COUNT = "arg_count";

    private BottomSheetCommentsBinding binding;
    private String postId;
    private int commentCount;
    private CommentsAdapter adapter;
    private final List<Comment> commentsList = new ArrayList<>();
    private CommentRepository commentRepository;
    private String currentUserId;

    public static CommentsBottomSheet newInstance(String postId, int commentCount) {
        CommentsBottomSheet sheet = new CommentsBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_POST_ID, postId);
        args.putInt(ARG_COUNT, commentCount);
        sheet.setArguments(args);
        return sheet;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL, R.style.InstagramBottomSheetDialog);
        if (getArguments() != null) {
            postId = getArguments().getString(ARG_POST_ID, "");
            commentCount = getArguments().getInt(ARG_COUNT, 0);
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetCommentsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        commentRepository = new CommentRepository(requireContext());
        currentUserId = SessionManager.getInstance(requireContext()).getUserId();

        adapter = new CommentsAdapter(commentsList, currentUserId, new CommentsAdapter.OnCommentInteractionListener() {
            @Override
            public void onLikeClick(Comment comment, int position) {
                boolean newState = !comment.isLikedByMe();
                comment.setLikedByMe(newState);
                comment.setLikesCount(Math.max(0, comment.getLikesCount() + (newState ? 1 : -1)));
                adapter.notifyItemChanged(position);
                commentRepository.toggleCommentLike(comment.getId(), newState);
            }

            @Override
            public void onDeleteClick(Comment comment, int position) {
                new AlertDialog.Builder(requireContext())
                        .setTitle("Delete Comment")
                        .setMessage("Do you want to delete this comment?")
                        .setPositiveButton("Delete", (dialog, which) -> {
                            commentsList.remove(position);
                            adapter.notifyItemRemoved(position);
                            commentRepository.deleteComment(comment.getId());
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });

        binding.rvComments.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvComments.setAdapter(adapter);

        setupEmojiBar();
        loadComments();

        binding.etCommentInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int count, int after) {
                boolean hasText = s != null && s.toString().trim().length() > 0;
                binding.btnPostComment.setEnabled(hasText);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        binding.btnPostComment.setOnClickListener(v -> {
            String text = binding.etCommentInput.getText().toString().trim();
            if (!text.isEmpty()) {
                binding.btnPostComment.setEnabled(false);
                commentRepository.addComment(postId, text).observe(getViewLifecycleOwner(), resource -> {
                    if (resource != null && resource.status == Resource.Status.SUCCESS && resource.data != null) {
                        commentsList.add(resource.data);
                        adapter.notifyItemInserted(commentsList.size() - 1);
                        binding.rvComments.smoothScrollToPosition(commentsList.size() - 1);
                        binding.etCommentInput.setText("");
                        binding.btnPostComment.setEnabled(true);
                    } else if (resource != null && resource.status == Resource.Status.ERROR) {
                        Toast.makeText(requireContext(), "Failed to post comment", Toast.LENGTH_SHORT).show();
                        binding.btnPostComment.setEnabled(true);
                    }
                });
            }
        });
    }

    private void loadComments() {
        if (postId == null || postId.isEmpty()) return;
        commentRepository.getComments(postId).observe(getViewLifecycleOwner(), resource -> {
            if (resource != null && resource.status == Resource.Status.SUCCESS && resource.data != null) {
                commentsList.clear();
                commentsList.addAll(resource.data);
                adapter.notifyDataSetChanged();
            }
        });
    }

    private void setupEmojiBar() {
        View.OnClickListener emojiClick = v -> {
            if (v instanceof android.widget.TextView) {
                CharSequence emoji = ((android.widget.TextView) v).getText();
                binding.etCommentInput.append(emoji);
            }
        };
        binding.emojiHeart.setOnClickListener(emojiClick);
        binding.emojiHands.setOnClickListener(emojiClick);
        binding.emojiFire.setOnClickListener(emojiClick);
        binding.emojiClap.setOnClickListener(emojiClick);
        binding.emojiCry.setOnClickListener(emojiClick);
        binding.emojiLove.setOnClickListener(emojiClick);
        binding.emojiSurprise.setOnClickListener(emojiClick);
        binding.emojiLaugh.setOnClickListener(emojiClick);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    static class CommentsAdapter extends RecyclerView.Adapter<CommentsAdapter.CommentViewHolder> {
        private final List<Comment> items;
        private final String currentUserId;
        private final OnCommentInteractionListener listener;

        interface OnCommentInteractionListener {
            void onLikeClick(Comment comment, int position);
            void onDeleteClick(Comment comment, int position);
        }

        CommentsAdapter(List<Comment> items, String currentUserId, OnCommentInteractionListener listener) {
            this.items = items;
            this.currentUserId = currentUserId;
            this.listener = listener;
        }

        @NonNull
        @Override
        public CommentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemCommentBinding binding = ItemCommentBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false
            );
            return new CommentViewHolder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull CommentViewHolder holder, int position) {
            Comment comment = items.get(position);
            holder.binding.tvCommentText.setText(comment.getUsername() + "  " + comment.getText());

            long time = comment.getCreatedAt();
            if (time > 0) {
                holder.binding.tvTime.setText(TimeUtils.formatRelativeTime(time));
            } else {
                holder.binding.tvTime.setText("just now");
            }

            if (comment.getLikesCount() > 0) {
                holder.binding.tvLikes.setText(comment.getLikesCount() + " likes");
                holder.binding.tvLikes.setVisibility(View.VISIBLE);
            } else {
                holder.binding.tvLikes.setVisibility(View.GONE);
            }

            holder.binding.ivLike.setImageResource(
                    comment.isLikedByMe() ? R.drawable.ic_favorite_filled : R.drawable.ic_favorite_outline
            );
            if (comment.isLikedByMe()) {
                holder.binding.ivLike.setColorFilter(holder.itemView.getContext().getColor(R.color.color_like));
            } else {
                holder.binding.ivLike.clearColorFilter();
            }

            holder.binding.ivLike.setOnClickListener(v -> {
                if (listener != null) listener.onLikeClick(comment, position);
            });

            if (currentUserId != null && currentUserId.equals(comment.getUserId())) {
                holder.itemView.setOnLongClickListener(v -> {
                    if (listener != null) listener.onDeleteClick(comment, position);
                    return true;
                });
            } else {
                holder.itemView.setOnLongClickListener(null);
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class CommentViewHolder extends RecyclerView.ViewHolder {
            final ItemCommentBinding binding;
            CommentViewHolder(ItemCommentBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
