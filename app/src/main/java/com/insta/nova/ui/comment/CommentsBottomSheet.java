package com.insta.nova.ui.comment;

import android.app.Dialog;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.insta.nova.R;
import com.insta.nova.data.local.CommentStore;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.model.Comment;
import com.insta.nova.data.repository.CommentRepository;
import com.insta.nova.data.repository.NotificationCenter;
import com.insta.nova.databinding.BottomSheetCommentsBinding;
import com.insta.nova.databinding.ItemCommentBinding;
import com.insta.nova.util.GlideUtils;
import com.insta.nova.util.KeyboardUtils;
import com.insta.nova.util.Resource;
import com.insta.nova.util.TimeUtils;
import java.util.ArrayList;
import java.util.List;

/**
 * Instagram-style comments sheet.
 *
 * <p>Drag handle, centred title, scrollable thread with per-comment likes and
 * Reply, and a composer pinned above the keyboard. The thread is persisted per
 * post by {@link CommentStore}; the server read is imported once so existing
 * comments (and their count) are not lost on first open.
 */
public class CommentsBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_POST_ID = "arg_post_id";
    private static final String ARG_COUNT = "arg_count";

    private BottomSheetCommentsBinding binding;
    private String postId;
    private int commentCount;
    private CommentsAdapter adapter;
    private final List<Comment> commentsList = new ArrayList<>();
    private CommentStore commentStore;
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
        setStyle(STYLE_NORMAL, R.style.InstaNovaBottomSheetDialog);
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
            FrameLayout bottomSheet = dialog.findViewById(
                    com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                // A single downward swipe closes the sheet, like Instagram.
                behavior.setSkipCollapsed(true);
                behavior.setDraggable(true);
            }
        });
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetCommentsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        commentStore = new CommentStore(requireContext());
        commentRepository = new CommentRepository(requireContext());
        currentUserId = SessionManager.getInstance(requireContext()).getUserId();

        GlideUtils.loadAvatar(binding.ivMyAvatar,
                SessionManager.getInstance(requireContext()).getAvatarUrl());

        adapter = new CommentsAdapter(commentsList, currentUserId,
                new CommentsAdapter.OnCommentInteractionListener() {
                    @Override
                    public void onLikeClick(Comment comment, int position) {
                        commentStore.toggleLike(postId, comment.getId());
                        commentsList.clear();
                        commentsList.addAll(commentStore.getComments(postId));
                        adapter.notifyItemChanged(position);
                    }

                    @Override
                    public void onReplyClick(Comment comment) {
                        if (comment.getUsername() == null) return;
                        String prefix = "@" + comment.getUsername() + " ";
                        binding.etCommentInput.setText(prefix);
                        binding.etCommentInput.setSelection(prefix.length());
                        KeyboardUtils.showKeyboard(binding.etCommentInput);
                    }

                    @Override
                    public void onDeleteClick(Comment comment, int position) {
                        confirmDelete(comment);
                    }
                });

        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        binding.rvComments.setLayoutManager(layoutManager);
        binding.rvComments.setAdapter(adapter);

        binding.etCommentInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int count, int after) {
                setPostEnabled(s != null && s.toString().trim().length() > 0);
            }

            @Override public void afterTextChanged(Editable s) {}
        });
        setPostEnabled(false);

        binding.btnPostComment.setOnClickListener(v -> postComment());

        loadThread();
    }

    /**
     * Local thread first: once hydrated there is no network call at all. The first
     * open imports the server rows so real comments survive the switch to local
     * persistence.
     */
    private void loadThread() {
        if (postId == null || postId.isEmpty()) {
            showEmptyState();
            return;
        }
        if (commentStore.isHydrated(postId)) {
            render(commentStore.getComments(postId));
            return;
        }

        binding.progressComments.setVisibility(View.VISIBLE);
        binding.emptyComments.setVisibility(View.GONE);
        commentRepository.getComments(postId).observe(getViewLifecycleOwner(), resource -> {
            if (binding == null || resource == null) return;
            if (resource.status == Resource.Status.SUCCESS) {
                commentStore.hydrate(postId, resource.data);
                render(commentStore.getComments(postId));
            } else if (resource.status == Resource.Status.ERROR) {
                // Offline: keep whatever is stored locally rather than blocking the sheet.
                render(commentStore.getComments(postId));
            }
        });
    }

    private void render(List<Comment> comments) {
        if (binding == null) return;
        binding.progressComments.setVisibility(View.GONE);
        commentsList.clear();
        commentsList.addAll(comments);
        adapter.notifyDataSetChanged();
        boolean empty = commentsList.isEmpty();
        binding.emptyComments.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private void showEmptyState() {
        if (binding == null) return;
        binding.progressComments.setVisibility(View.GONE);
        binding.emptyComments.setVisibility(View.VISIBLE);
    }

    private void postComment() {
        String text = binding.etCommentInput.getText().toString().trim();
        if (text.isEmpty() || postId == null || postId.isEmpty()) return;

        Comment added = commentStore.add(postId, text);
        commentsList.add(added);
        adapter.notifyItemInserted(commentsList.size() - 1);
        binding.rvComments.smoothScrollToPosition(commentsList.size() - 1);
        binding.emptyComments.setVisibility(View.GONE);
        binding.etCommentInput.setText("");
        setPostEnabled(false);

        // Feeds the activity screen: a demo account answers on this thread.
        NotificationCenter.get(requireContext()).onCommentPosted(postId, text);
    }

    private void setPostEnabled(boolean enabled) {
        if (binding == null) return;
        binding.btnPostComment.setEnabled(enabled);
        binding.btnPostComment.setAlpha(enabled ? 1f : 0.45f);
    }

    private void confirmDelete(Comment comment) {
        if (comment == null) return;
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.comments_delete_title)
                .setMessage(R.string.comments_delete_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.comments_delete_action, (dialog, which) -> {
                    commentStore.delete(postId, comment.getId());
                    render(commentStore.getComments(postId));
                })
                .show();
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

            void onReplyClick(Comment comment);

            void onDeleteClick(Comment comment, int position);
        }

        CommentsAdapter(List<Comment> items, String currentUserId,
                        OnCommentInteractionListener listener) {
            this.items = items;
            this.currentUserId = currentUserId;
            this.listener = listener;
        }

        @NonNull
        @Override
        public CommentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemCommentBinding binding = ItemCommentBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new CommentViewHolder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull CommentViewHolder holder, int position) {
            Comment comment = items.get(position);
            ItemCommentBinding row = holder.binding;

            String username = comment.getUsername() == null ? "" : comment.getUsername();
            String body = comment.getText() == null ? "" : comment.getText();
            SpannableString content = new SpannableString(username + "  " + body);
            if (!username.isEmpty()) {
                content.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, username.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            row.tvCommentText.setText(content);

            row.tvTime.setText(comment.getCreatedAt() > 0
                    ? TimeUtils.formatCompactRelativeTime(comment.getCreatedAt())
                    : row.getRoot().getContext().getString(R.string.comments_time_now));

            int likes = comment.getLikesCount();
            if (likes > 0) {
                row.tvLikes.setText(row.getRoot().getContext().getResources()
                        .getQuantityString(R.plurals.comment_likes, likes, likes));
                row.tvLikes.setVisibility(View.VISIBLE);
            } else {
                row.tvLikes.setVisibility(View.GONE);
            }

            boolean liked = comment.isLikedByMe();
            row.ivLike.setImageResource(liked
                    ? R.drawable.ic_favorite_filled : R.drawable.ic_favorite_outline);
            ImageViewCompat.setImageTintList(row.ivLike, ColorStateList.valueOf(
                    ContextCompat.getColor(row.getRoot().getContext(),
                            liked ? R.color.color_like : R.color.color_text_secondary)));
            row.layoutLike.setContentDescription(row.getRoot().getContext()
                    .getString(R.string.comments_like));

            GlideUtils.loadAvatar(row.ivAvatar, comment.getAvatarUrl());

            row.layoutLike.setOnClickListener(v -> {
                if (listener != null) listener.onLikeClick(comment, position);
            });
            row.btnReply.setOnClickListener(v -> {
                if (listener != null) listener.onReplyClick(comment);
            });

            if (currentUserId != null && currentUserId.equals(comment.getUserId())) {
                row.getRoot().setOnLongClickListener(v -> {
                    if (listener != null) listener.onDeleteClick(comment, position);
                    return true;
                });
            } else {
                row.getRoot().setOnLongClickListener(null);
                row.getRoot().setLongClickable(false);
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
