package com.insta.clone.ui.comment;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.insta.clone.R;
import com.insta.clone.data.model.Comment;
import com.insta.clone.databinding.BottomSheetCommentsBinding;
import com.insta.clone.databinding.ItemCommentBinding;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * BottomSheet displaying threaded comments, emoji reaction bar, and comment input.
 */
public class CommentsBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_POST_ID = "arg_post_id";
    private static final String ARG_COUNT = "arg_count";

    private BottomSheetCommentsBinding binding;
    private String postId;
    private int commentCount;
    private CommentsAdapter adapter;
    private final List<Comment> commentsList = new ArrayList<>();

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

        adapter = new CommentsAdapter(commentsList);
        binding.rvComments.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvComments.setAdapter(adapter);

        loadInitialComments();
        setupEmojiBar();

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
                Comment comment = new Comment(
                        UUID.randomUUID().toString(),
                        postId,
                        "my_user_id",
                        "you",
                        "",
                        text,
                        0,
                        false,
                        System.currentTimeMillis()
                );
                commentsList.add(comment);
                adapter.notifyItemInserted(commentsList.size() - 1);
                binding.rvComments.smoothScrollToPosition(commentsList.size() - 1);
                binding.etCommentInput.setText("");
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

    private void loadInitialComments() {
        commentsList.clear();
        commentsList.add(new Comment("1", postId, "u1", "alex_photos", "", "Stunning capture! 🔥", 3, false, System.currentTimeMillis() - 3600000));
        commentsList.add(new Comment("2", postId, "u2", "travel_bug", "", "Where is this location?", 1, false, System.currentTimeMillis() - 7200000));
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    static class CommentsAdapter extends RecyclerView.Adapter<CommentsAdapter.CommentViewHolder> {
        private final List<Comment> items;

        CommentsAdapter(List<Comment> items) {
            this.items = items;
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
            holder.binding.tvTime.setText("1h");
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
