package com.insta.nova.hub;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.insta.nova.R;
import com.insta.nova.data.NovaDtos;
import com.insta.nova.data.repository.NovaPostRepository;
import com.insta.nova.databinding.ActivityNovaPostDetailBinding;
import com.insta.nova.databinding.ItemNovaPostBinding;
import com.insta.nova.util.Resource;
import com.insta.nova.util.ToastUtil;

import java.util.List;

/**
 * A Nova post with its comment thread.
 *
 * <p>The post is passed in so the screen opens instantly; the thread is read
 * from the server. Votes, replies and the anonymous switch all write through
 * the Nova RPCs, so nothing here can touch an Instagram table.
 */
public class NovaPostDetailActivity extends AppCompatActivity
        implements NovaCommentAdapter.Listener, NovaPostAdapter.Listener {

    private static final String EXTRA_POST_ID = "extra_nova_post_id";
    private static final String EXTRA_POST = "extra_nova_post";

    private ActivityNovaPostDetailBinding binding;
    private NovaPostRepository repository;
    private NovaCommentAdapter commentAdapter;
    private NovaDtos.NovaPost post;

    /** Renders the header card with the feed's own renderer. */
    private NovaPostAdapter headerRenderer;
    private NovaPostAdapter.PostHolder headerHolder;

    /** The comment the next message replies to, if any. */
    private NovaDtos.NovaComment replyTarget;

    public static void start(Context context, NovaDtos.NovaPost post) {
        if (context == null || post == null || post.id == null) return;
        Intent intent = new Intent(context, NovaPostDetailActivity.class);
        intent.putExtra(EXTRA_POST_ID, post.id);
        intent.putExtra(EXTRA_POST, post);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNovaPostDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = new NovaPostRepository(this);

        binding.btnNovaDetailBack.setOnClickListener(v -> finish());
        binding.btnNovaCommentSend.setOnClickListener(v -> sendComment());

        commentAdapter = new NovaCommentAdapter(this);
        binding.novaCommentList.setLayoutManager(new LinearLayoutManager(this));
        binding.novaCommentList.setAdapter(commentAdapter);

        bindHeader();
        loadComments();
    }

    /**
     * Draws the post at the top of the screen.
     *
     * <p>The header is the feed's own card layout, so the card is painted by
     * the same code the feed uses — one renderer, no second implementation to
     * drift. Before this the header was inflated and left completely empty,
     * because only the post id was handed over.
     */
    private void bindHeader() {
        post = (NovaDtos.NovaPost) getIntent().getSerializableExtra(EXTRA_POST);
        if (post == null) return;
        headerRenderer = new NovaPostAdapter(this);
        headerHolder = headerRenderer.new PostHolder(binding.novaDetailPost);
        headerHolder.bind(post);
    }

    /** Repaints the header after a vote or a save. */
    private void repaintHeader() {
        if (headerHolder != null && post != null) headerHolder.bind(post);
    }

    /* ------------------------------------------------------------------ */
    /* The header card's own actions                                       */
    /* ------------------------------------------------------------------ */

    @Override
    public void onVote(NovaDtos.NovaPost target, int value) {
        if (target == null || target.id == null) return;
        repository.vote(target.id, value).observe(this, resource -> {
            if (resource == null || resource.status != Resource.Status.SUCCESS
                    || resource.data == null) {
                return;
            }
            target.score = resource.data.score;
            target.myVote = resource.data.myVote;
            target.upvoteCount = resource.data.upvoteCount;
            target.downvoteCount = resource.data.downvoteCount;
            post = target;
            repaintHeader();
        });
    }

    @Override
    public void onSave(NovaDtos.NovaPost target) {
        if (target == null || target.id == null) return;
        final boolean next = !target.isSaved;
        repository.setSaved(target.id, next).observe(this, resource -> {
            if (resource == null || resource.status != Resource.Status.SUCCESS) return;
            target.isSaved = next;
            post = target;
            repaintHeader();
        });
    }

    /** The thread is already on screen, so the comment action needs no work. */
    @Override
    public void onComments(NovaDtos.NovaPost target) {
        focusCommentField();
    }

    /** Already here. */
    @Override
    public void onOpen(NovaDtos.NovaPost target) {
        focusCommentField();
    }

    private void focusCommentField() {
        binding.etNovaCommentBody.requestFocus();
    }

    /** Reads the thread. */
    private void loadComments() {
        String postId = getIntent().getStringExtra(EXTRA_POST_ID);
        if (postId == null) {
            finish();
            return;
        }
        binding.novaDetailProgress.setVisibility(View.VISIBLE);
        repository.comments(postId).observe(this, resource -> {
            binding.novaDetailProgress.setVisibility(View.GONE);
            if (resource == null) return;
            if (resource.status == Resource.Status.SUCCESS) {
                List<NovaDtos.NovaComment> comments = resource.data;
                // Held comments only reach this screen when I wrote them or I own the
                // post, so the owner check decides whether the approve action appears.
                commentAdapter.setCanModerate(post != null && post.isMine);
                commentAdapter.submit(comments);
                boolean empty = comments == null || comments.isEmpty();
                binding.tvNovaCommentsEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                binding.tvNovaCommentsError.setVisibility(View.GONE);
            } else if (resource.status == Resource.Status.ERROR) {
                binding.tvNovaCommentsError.setVisibility(View.VISIBLE);
                binding.tvNovaCommentsError.setText(R.string.nova_feed_error);
            }
        });
    }

    /* ------------------------------------------------------------------ */
    /* Writing                                                             */
    /* ------------------------------------------------------------------ */

    private void sendComment() {
        String postId = getIntent().getStringExtra(EXTRA_POST_ID);
        if (postId == null) return;
        String body = binding.etNovaCommentBody.getText() == null
                ? "" : binding.etNovaCommentBody.getText().toString().trim();
        if (body.isEmpty()) return;

        boolean anonymous = binding.switchNovaCommentAnonymous.isChecked();
        String parentId = replyTarget == null ? null : replyTarget.id;

        binding.btnNovaCommentSend.setEnabled(false);
        repository.createComment(postId, body, parentId, anonymous).observe(this, resource -> {
            binding.btnNovaCommentSend.setEnabled(true);
            if (resource == null) return;
            if (resource.status == Resource.Status.SUCCESS) {
                binding.etNovaCommentBody.setText("");
                clearReplyTarget();
                loadComments();
            } else if (resource.status == Resource.Status.ERROR) {
                ToastUtil.showPill(this, getString(R.string.nova_comments_failed));
            }
        });
    }

    private void clearReplyTarget() {
        replyTarget = null;
        binding.tvNovaReplyTarget.setVisibility(View.GONE);
    }

    /* ------------------------------------------------------------------ */
    /* Comment actions                                                     */
    /* ------------------------------------------------------------------ */

    @Override
    public void onPollVote(NovaDtos.NovaPost post, int optionIndex) {
        if (post == null || repository == null) return;
        repository.votePoll(post.id, optionIndex).observe(this, resource -> {
            if (resource == null) return;
            if (resource.status == Resource.Status.SUCCESS && resource.data != null) {
                post.pollVotes = resource.data.counts;
                post.myPollVote = resource.data.myVote;
                repaintHeader();
            }
        });
    }

    @Override
    public void onVote(NovaDtos.NovaComment comment, int value) {
        if (comment == null) return;
        repository.voteComment(comment.id, value).observe(this, resource -> {
            if (resource == null) return;
            if (resource.status == Resource.Status.SUCCESS && resource.data != null) {
                NovaDtos.VoteOutcome outcome = resource.data;
                comment.myVote = outcome.myVote;
                comment.score = outcome.score;
                comment.upvoteCount = outcome.upvoteCount;
                comment.downvoteCount = outcome.downvoteCount;
                commentAdapter.replace(comment);
            } else if (resource.status == Resource.Status.ERROR) {
                ToastUtil.showPill(this, getString(R.string.nova_vote_failed));
            }
        });
    }

    /**
     * Releases a held comment. Offered only on a post this account wrote; the server
     * rejects anyone else, so a stale screen cannot approve on somebody's behalf.
     */
    @Override
    public void onApprove(NovaDtos.NovaComment comment) {
        if (comment == null || comment.id == null) return;
        repository.approveHeldComment(comment.id).observe(this, resource -> {
            if (resource == null) return;
            if (resource.status == Resource.Status.SUCCESS) {
                comment.isHeld = false;
                commentAdapter.replace(comment);
                ToastUtil.showPill(this, "Comment approved");
            } else if (resource.status == Resource.Status.ERROR) {
                ToastUtil.showPill(this, "Only the post owner can approve this");
            }
        });
    }

    @Override
    public void onReply(NovaDtos.NovaComment comment) {
        if (comment == null) return;
        replyTarget = comment;
        binding.tvNovaReplyTarget.setVisibility(View.VISIBLE);
        binding.tvNovaReplyTarget.setText(getString(R.string.nova_comments_reply) + " · "
                + (comment.alias == null ? "unknown" : comment.alias));
        binding.etNovaCommentBody.requestFocus();
    }
}
