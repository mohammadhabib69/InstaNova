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
import com.insta.nova.safe.NovaGuard;
import com.insta.nova.safe.NovaModerationDialog;
import com.insta.nova.util.GlideUtils;
import com.insta.nova.util.KeyboardUtils;
import com.insta.nova.util.Resource;
import com.insta.nova.util.TimeUtils;
import android.widget.Toast;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.insta.nova.data.firebase.FirebaseData;
import com.insta.nova.data.repository.FirestoreCommentRepository;
import com.insta.nova.data.remote.RetrofitClient;
import com.insta.nova.data.remote.api.RestApi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

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
    private static final String ARG_OWNER = "arg_owner_uid";
    private static final String ARG_COMMENTS_OFF = "arg_comments_off";

    private BottomSheetCommentsBinding binding;
    private String postId;
    private int commentCount;
    /** The author turned commenting off; the input bar turns into a note. */
    private boolean commentsDisabled;
    private CommentsAdapter adapter;
    private final List<Comment> commentsList = new ArrayList<>();
    private CommentStore commentStore;
    private CommentRepository commentRepository;
    private FirestoreCommentRepository firestoreComments;
    private String currentUserId;

    /** authorUid → username for rows loaded from Firestore, filled once per thread. */
    private final Map<String, String> nameCache = new HashMap<>();

    /** Comment ids currently held for review, so render() can label them. */
    private final java.util.Set<String> heldCommentIds = new java.util.HashSet<>();

    /** Uid of the post's author; null when the caller could not supply one. */
    @Nullable
    private String postOwnerUid;

    public static CommentsBottomSheet newInstance(String postId, int commentCount) {
        return newInstance(postId, commentCount, null);
    }

    /**
     * @param postOwnerUid lets the sheet offer "approve" on a held comment. Without it
     *                     the thread still loads; it simply shows no moderation action.
     */
    public static CommentsBottomSheet newInstance(String postId, int commentCount,
                                                  @Nullable String postOwnerUid) {
        return newInstance(postId, commentCount, postOwnerUid, false);
    }

    /** @param commentsDisabled the post's own choice — nobody can comment on it. */
    public static CommentsBottomSheet newInstance(String postId, int commentCount,
                                                  @Nullable String postOwnerUid,
                                                  boolean commentsDisabled) {
        CommentsBottomSheet sheet = new CommentsBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_POST_ID, postId);
        args.putInt(ARG_COUNT, commentCount);
        args.putString(ARG_OWNER, postOwnerUid);
        args.putBoolean(ARG_COMMENTS_OFF, commentsDisabled);
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
            postOwnerUid = getArguments().getString(ARG_OWNER);
            commentsDisabled = getArguments().getBoolean(ARG_COMMENTS_OFF, false);
        }
        // A reposted row carries a synthetic "repost_<uuid>" id. Comments live on the
        // original post, so an un-stripped id made v_comments return an error and the
        // thread opened empty — which read as "comments sometimes don't load".
        if (postId != null) postId = postId.replaceFirst("^repost_", "");
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
        firestoreComments = new FirestoreCommentRepository();
        currentUserId = SessionManager.getInstance(requireContext()).getUserId();

        GlideUtils.loadAvatar(binding.ivMyAvatar,
                SessionManager.getInstance(requireContext()).getAvatarUrl());

        adapter = new CommentsAdapter(commentsList, currentUserId,
                new CommentsAdapter.OnCommentInteractionListener() {
                    @Override
                    public void onLikeClick(Comment comment, int position) {
                        boolean liked = !comment.isLikedByMe();
                        commentStore.toggleLike(postId, comment.getId());
                        commentsList.clear();
                        commentsList.addAll(commentStore.getComments(postId));
                        adapter.notifyItemChanged(position);
                        // Persist the like too, otherwise it disappears on the next load.
                        if (comment.getId() != null && !comment.getId().isEmpty()) {
                            commentRepository.toggleCommentLike(comment.getId(), liked);
                        }
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

                    @Override
                    public void onApproveHeldClick(Comment comment, int position) {
                        if (comment.getId() == null) return;
                        firestoreComments.releaseHeldComment(comment.getId())
                                .observe(getViewLifecycleOwner(), resource -> {
                                    if (resource == null) return;
                                    if (resource.status == Resource.Status.SUCCESS) {
                                        heldCommentIds.remove(comment.getId());
                                        Toast.makeText(requireContext(),
                                                R.string.comments_held_released,
                                                Toast.LENGTH_SHORT).show();
                                        loadThread();
                                    } else if (resource.status == Resource.Status.ERROR) {
                                        Toast.makeText(requireContext(),
                                                R.string.comments_held_release_failed,
                                                Toast.LENGTH_SHORT).show();
                                    }
                                });
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

        if (commentsDisabled) {
            // The post carries comments_disabled, so the box is closed for every
            // viewer, not just hidden behind a toast.
            binding.ivMyAvatar.setVisibility(View.GONE);
            binding.btnPostComment.setVisibility(View.GONE);
            binding.etCommentInput.setEnabled(false);
            binding.etCommentInput.setFocusable(false);
            binding.etCommentInput.setHint(R.string.comments_off_hint);
        }

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
        // Local cache first so the sheet is never blank, then Firestore reconciles it.
        if (commentStore.isHydrated(postId)) {
            render(commentStore.getComments(postId));
        } else {
            binding.progressComments.setVisibility(View.VISIBLE);
            binding.emptyComments.setVisibility(View.GONE);
        }
        resolvePostOwner();
        loadFromServer();
    }

    /**
     * Reads the post owner's Firebase uid.
     *
     * <p>Deliberately not taken from {@code Post.getUserId()}: that field is the
     * Supabase uuid, while a comment's {@code authorUid} is a Firebase uid, so
     * comparing the two would never match and the approve action would never appear.
     * The value here is the same one {@code firestore.rules} uses for {@code isPostOwner}.
     */
    private void resolvePostOwner() {
        if (postId == null || postId.isEmpty()) return;
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("posts").document(postId).get()
                .addOnSuccessListener(doc -> {
                    if (doc == null || !doc.exists()) return;
                    String owner = doc.getString("authorUid");
                    if (owner == null || owner.trim().isEmpty()) return;
                    postOwnerUid = owner;
                    if (binding != null) render(commentStore.getComments(postId));
                });
    }

    /**
     * Firestore is the store for comments (P4-05). Server rows are merged into the local cache,
     * keyed by document id, so a reopen shows the thread even offline and a row written by this
     * device is never duplicated.
     */
    /**
     * Supabase is the store for comments: the same table the post's {@code comments_count}
     * is derived from by trigger. The Firestore page this replaced wrote a counter into a
     * different database, which is why the number on the post never moved.
     *
     * <p>Server rows are merged into the local mirror, keyed by id, so an offline reopen
     * still shows the thread and a row written on this device is never duplicated.
     */
    private void loadFromServer() {
        commentRepository.getComments(postId).observe(getViewLifecycleOwner(), resource -> {
            if (binding == null || resource == null) return;
            if (resource.status != Resource.Status.SUCCESS || resource.data == null) {
                render(commentStore.getComments(postId));
                return;
            }

            List<Comment> merged = new ArrayList<>(commentStore.getComments(postId));
            for (Comment comment : resource.data) {
                if (comment == null || comment.getId() == null) continue;
                if (containsComment(merged, comment.getId())) continue;
                merged.add(comment);
            }
            merged.sort((a, b) -> Long.compare(
                    a == null ? 0L : a.getCreatedAt(), b == null ? 0L : b.getCreatedAt()));
            commentStore.hydrate(postId, merged);
            render(commentStore.getComments(postId));
        });
    }

    private boolean containsComment(List<Comment> list, String id) {
        for (Comment c : list) {
            if (c.getId() != null && c.getId().equals(id)) return true;
        }
        return false;
    }

    /** Username for a row loaded from Firestore; a placeholder until publicProfiles answers. */
    private String displayName(String uid) {
        if (uid == null) return "user";
        String cached = nameCache.get(uid);
        if (cached != null) return cached;
        String mine = FirebaseAuth.getInstance().getUid();
        if (uid.equals(mine)) {
            String name = SessionManager.getInstance(requireContext()).getUsername();
            if (name != null && !name.trim().isEmpty()) {
                nameCache.put(uid, name);
                return name;
            }
        }
        return "user";
    }

    /** One publicProfiles read per new author, then one re-render. */
    private void resolveNames(List<String> uids) {
        for (String uid : uids) {
            FirebaseData.firestore().collection("publicProfiles").document(uid).get()
                    .addOnSuccessListener(doc -> {
                        String name = doc.getString("username");
                        if (name != null && !name.trim().isEmpty()) nameCache.put(uid, name);
                        else nameCache.put(uid, "user");
                        if (binding != null) render(commentStore.getComments(postId));
                    })
                    .addOnFailureListener(e -> nameCache.put(uid, "user"));
        }
    }

    private void render(List<Comment> comments) {
        if (binding == null) return;
        binding.progressComments.setVisibility(View.GONE);
        com.insta.nova.data.local.AppSettingsStore settings =
                new com.insta.nova.data.local.AppSettingsStore(requireContext());
        com.insta.nova.data.NovaSettingsStore nova = com.insta.nova.data.NovaSettingsStore.get(requireContext());
        boolean filterToxic = nova.current().hideToxic;
        com.insta.nova.data.BlockMuteCache relationships =
                com.insta.nova.data.BlockMuteCache.get(requireContext());

        boolean isOwner = postOwnerUid != null && postOwnerUid.equals(currentUserId);
        adapter.setHeldState(heldCommentIds, isOwner);

        commentsList.clear();
        int hidden = 0;
        for (Comment comment : comments) {
            if (comment == null) continue;
            // Belt and braces: firestore.rules already stops a held comment reaching a
            // third account, but a row cached on this device before the restriction was
            // applied must not leak either.
            boolean held = heldCommentIds.contains(comment.getId());
            boolean mine = currentUserId != null
                    && currentUserId.equals(comment.getUserId());
            if (held && !mine && !isOwner) {
                hidden++;
                continue;
            }
            if (settings.isCommentHidden(comment.getText())) {
                hidden++;
                continue;
            }
            // Blocked authors disappear completely (they are not "hidden", they are gone).
            if (relationships.isBlocked(comment.getUserId())) continue;
            if (filterToxic && NovaGuard.evaluate(requireContext(), comment.getText()).severity >= 2) {
                hidden++;
                continue;
            }
            commentsList.add(comment);
        }
        adapter.notifyDataSetChanged();
        boolean empty = commentsList.isEmpty();
        binding.emptyComments.setVisibility(empty ? View.VISIBLE : View.GONE);

        // Honest counter: the reader always knows how many rows were collapsed.
        if (hidden > 0) {
            binding.tvHiddenCount.setText(getString(R.string.nova_hidden_count, hidden));
            binding.tvHiddenCount.setVisibility(View.VISIBLE);
        } else {
            binding.tvHiddenCount.setVisibility(View.GONE);
        }
    }

    private void showEmptyState() {
        if (binding == null) return;
        binding.progressComments.setVisibility(View.GONE);
        binding.emptyComments.setVisibility(View.VISIBLE);
    }

    private void postComment() {
        if (binding == null) return;
        String text = binding.etCommentInput.getText().toString().trim();
        if (text.isEmpty() || postId == null || postId.isEmpty()) return;

        // Nova Safe: severity 3 blocks, severity 1-2 warns when the user asked for it.
        // Only the "post anyway" branch reaches submitComment().
        NovaModerationDialog.guard(requireActivity(), "comment", text, 3, false,
                () -> submitComment(text),
                () -> {
                    if (binding != null) {
                        binding.etCommentInput.requestFocus();
                        binding.etCommentInput.setSelection(binding.etCommentInput.length());
                    }
                });
    }

    private void submitComment(String text) {
        if (binding == null || text == null || text.isEmpty()) return;
        if (postId == null || postId.isEmpty()) return;

        // One id for both stores: the optimistic row and the Firestore document are the same
        // comment, so reopening the thread cannot show it twice.
        String commentId = UUID.randomUUID().toString();
        Comment added = commentStore.add(postId, text, commentId);
        commentsList.add(added);
        adapter.notifyItemInserted(commentsList.size() - 1);
        binding.rvComments.smoothScrollToPosition(commentsList.size() - 1);
        binding.emptyComments.setVisibility(View.GONE);
        binding.etCommentInput.setText("");
        setPostEnabled(false);

        String authorUid = FirebaseAuth.getInstance().getUid();
        if (authorUid == null || authorUid.trim().isEmpty()) {
            rollbackComment(commentId, "Sign in again to comment.");
            return;
        }

        // Firestore is the store for comments (P4-05). A comment written by an account
        // the post owner has restricted is created held: the rules then return it only
        // to its author and to the owner. The check runs server-side because the
        // restricted list belongs to the owner and is not readable by anyone else.
        checkRestrictedThenWrite(postId, commentId, authorUid, text);
    }

    /**
     * Checks the post owner's Comment controls first, then whether this account is
     * restricted by them, and only then writes.
     */
    private void checkRestrictedThenWrite(String postId, String commentId,
                                          String authorUid, String text) {
        Map<String, Object> body = new HashMap<>();
        body.put("p_post", postId);
        RestApi api = RetrofitClient.getInstance(requireContext()).getRestApi();
        api.canCommentOnPost(body).enqueue(new Callback<Boolean>() {
            @Override
            public void onResponse(Call<Boolean> call, Response<Boolean> response) {
                if (response.isSuccessful() && Boolean.FALSE.equals(response.body())) {
                    // The owner restricted commenting. Drop the optimistic row so the
                    // composer does not show a comment that was never written.
                    rollbackComment(commentId,
                            getString(R.string.comments_not_allowed));
                    Toast.makeText(requireContext(), R.string.comments_not_allowed,
                            Toast.LENGTH_LONG).show();
                    return;
                }
                checkRestrictedThenWriteInternal(postId, commentId, authorUid, text);
            }

            @Override
            public void onFailure(Call<Boolean> call, Throwable t) {
                checkRestrictedThenWriteInternal(postId, commentId, authorUid, text);
            }
        });
    }

    private void checkRestrictedThenWriteInternal(String postId, String commentId,
                                                  String authorUid, String text) {
        Map<String, Object> body = new HashMap<>();
        body.put("p_post", postId);
        RetrofitClient.getInstance(requireContext()).getRestApi()
                .amIRestrictedByPostOwner(body)
                .enqueue(new Callback<Boolean>() {
                    @Override
                    public void onResponse(Call<Boolean> call, Response<Boolean> response) {
                        // Fail open on a transport error: a missing flag would publish a
                        // held comment, but failing closed would lose the comment
                        // entirely for everybody. The rules are the real gate.
                        boolean held = response.isSuccessful()
                                && Boolean.TRUE.equals(response.body());
                        writeComment(commentId, authorUid, text, held);
                    }

                    @Override
                    public void onFailure(Call<Boolean> call, Throwable t) {
                        writeComment(commentId, authorUid, text, false);
                    }
                });
    }

    private void writeComment(String commentId, String authorUid, String text, boolean held) {
        if (binding == null) return;

        // A restricted account's comment is held for the owner's review. Supabase has no
        // client-side "held" insert, so it is kept on this device only instead of being
        // published and then moderated.
        if (held) {
            setPostEnabled(true);
            Toast.makeText(requireContext(),
                    "This account is restricted, so your comment is waiting for review.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        commentRepository.addComment(postId, text).observe(getViewLifecycleOwner(), resource -> {
            if (resource == null) return;
            if (resource.status == Resource.Status.ERROR) {
                rollbackComment(commentId, resource.message);
                return;
            }
            if (resource.status != Resource.Status.SUCCESS || binding == null) return;

            setPostEnabled(true);
            // Swap the optimistic row for the server row (same text, server uuid) so a
            // reload after this shows exactly one copy of the comment.
            commentStore.delete(postId, commentId);
            Comment saved = resource.data;
            if (saved != null && saved.getId() != null) {
                commentStore.add(postId, saved.getText() == null ? text : saved.getText(),
                        saved.getId());
            }
            render(commentStore.getComments(postId));
        });
    }

    /** Removes an optimistic comment whose backend write was refused. */
    private void rollbackComment(String commentId, String message) {
        commentStore.delete(postId, commentId);
        if (binding == null) return;
        render(commentStore.getComments(postId));
        setPostEnabled(true);
        Toast.makeText(requireContext(),
                message == null || message.trim().isEmpty() ? "Couldn't post comment" : message,
                Toast.LENGTH_SHORT).show();
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
                    // Supabase is the store; deleting only locally would resurrect on reopen,
                    // and the trigger would never decrement posts.comments_count.
                    if (comment.getId() != null && !comment.getId().isEmpty()) {
                        commentRepository.deleteComment(comment.getId());
                    }
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

            void onApproveHeldClick(Comment comment, int position);
        }

        /** Ids held for review, and whether this reader is the post owner. */
        private java.util.Set<String> heldIds = java.util.Collections.emptySet();
        private boolean canModerate;

        void setHeldState(java.util.Set<String> heldIds, boolean canModerate) {
            this.heldIds = heldIds == null ? java.util.Collections.emptySet() : heldIds;
            this.canModerate = canModerate;
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

            String when = comment.getCreatedAt() > 0
                    ? TimeUtils.formatCompactRelativeTime(comment.getCreatedAt())
                    : row.getRoot().getContext().getString(R.string.comments_time_now);
            boolean held = heldIds != null && comment.getId() != null
                    && heldIds.contains(comment.getId());
            if (held) {
                when = when + " · " + (canModerate
                        ? row.getRoot().getContext().getString(R.string.comments_held_approve)
                        : row.getRoot().getContext().getString(R.string.comments_held_review));
            }
            row.tvTime.setText(when);

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

            // A held comment belongs to the owner's moderation queue, so long-press
            // releases it. Otherwise the author's own row keeps its delete action.
            if (held && canModerate) {
                row.getRoot().setLongClickable(true);
                row.getRoot().setOnLongClickListener(v -> {
                    if (listener != null) listener.onApproveHeldClick(comment, position);
                    return true;
                });
            } else if (currentUserId != null && currentUserId.equals(comment.getUserId())) {
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
