package com.insta.nova.study;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.insta.nova.R;
import com.insta.nova.data.NovaDtos;
import com.insta.nova.data.StudyRepository;
import com.insta.nova.databinding.ActivityNovaQuestionBinding;
import com.insta.nova.hub.NovaScreen;
import com.insta.nova.safe.NovaModerationDialog;
import com.insta.nova.safe.SafeActions;
import com.insta.nova.util.Resource;
import com.insta.nova.widget.NovaUi;

import java.util.List;

/** One question with its answers; voting, accepting and deleting all hit the backend. */
public class NovaQuestionActivity extends AppCompatActivity
        implements AnswerAdapter.Listener {

    public static final String EXTRA_ID = "nova.question.id";
    public static final String EXTRA_TITLE = "nova.question.title";
    public static final String EXTRA_BODY = "nova.question.body";
    public static final String EXTRA_SUBJECT = "nova.question.subject";
    public static final String EXTRA_AUTHOR = "nova.question.author";
    public static final String EXTRA_VOTES = "nova.question.votes";
    public static final String EXTRA_SOLVED = "nova.question.solved";
    public static final String EXTRA_MINE = "nova.question.mine";

    private ActivityNovaQuestionBinding binding;
    private StudyRepository repository;
    private AnswerAdapter adapter;
    private String questionId;
    private boolean mine;
    private boolean solved;
    private int votes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNovaQuestionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        NovaScreen.bindHeader(this, R.drawable.ic_nova_study,
                R.string.nova_qa_title, R.color.nova_study);

        questionId = getIntent().getStringExtra(EXTRA_ID);
        mine = getIntent().getBooleanExtra(EXTRA_MINE, false);
        solved = getIntent().getBooleanExtra(EXTRA_SOLVED, false);
        votes = getIntent().getIntExtra(EXTRA_VOTES, 0);
        if (questionId == null || questionId.isEmpty()) {
            finish();
            return;
        }

        repository = new StudyRepository(this);
        adapter = new AnswerAdapter(this);
        binding.answers.setLayoutManager(new LinearLayoutManager(this));
        binding.answers.setAdapter(adapter);
        adapter.setOwnerState(mine, solved);

        binding.tvSubject.setText(Subjects.label(this, getIntent().getStringExtra(EXTRA_SUBJECT)));
        binding.tvTitle.setText(getIntent().getStringExtra(EXTRA_TITLE));
        String body = getIntent().getStringExtra(EXTRA_BODY);
        binding.tvBody.setText(body == null ? "" : body);
        binding.tvBody.setVisibility(body == null || body.trim().isEmpty() ? View.GONE : View.VISIBLE);
        binding.tvSolved.setVisibility(solved ? View.VISIBLE : View.GONE);

        String author = getIntent().getStringExtra(EXTRA_AUTHOR);
        binding.tvMeta.setText((author == null ? "" : "@" + author) + " · "
                + getString(R.string.nova_study_answers, 0));

        renderVotes();
        binding.btnVote.setOnClickListener(v -> voteQuestion());
        binding.btnDeleteQuestion.setVisibility(mine ? View.VISIBLE : View.GONE);
        binding.btnDeleteQuestion.setOnClickListener(v -> deleteQuestion());
        binding.btnMore.setOnClickListener(v ->
                SafeActions.report(this, "question", questionId));
        binding.btnSendAnswer.setOnClickListener(v -> sendAnswer());
        binding.btnBack.setOnClickListener(v -> finish());

        loadAnswers();
    }

    private void renderVotes() {
        binding.btnVote.setText(getString(R.string.nova_study_votes, votes));
    }

    private void voteQuestion() {
        repository.vote("question", questionId).observe(this, resource -> {
            if (binding == null || resource == null) return;
            if (resource.status == Resource.Status.SUCCESS && resource.data != null) {
                votes = resource.data.votes;
                renderVotes();
                NovaUi.haptic(binding.getRoot(), true);
            } else if (resource.status == Resource.Status.ERROR) {
                showError(resource.message);
            }
        });
    }

    private void loadAnswers() {
        repository.answers(questionId).observe(this, resource -> {
            if (binding == null || resource == null) return;
            if (resource.status != Resource.Status.SUCCESS) {
                if (resource.status == Resource.Status.ERROR) showError(resource.message);
                return;
            }
            List<NovaDtos.Answer> rows = resource.data;
            adapter.submit(rows);
            adapter.setOwnerState(mine, solved);
            int count = rows == null ? 0 : rows.size();
            binding.tvAnswersTitle.setText(getString(R.string.nova_study_answers, count));
            boolean empty = count == 0;
            binding.tvAnswersEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            binding.answers.setVisibility(empty ? View.GONE : View.VISIBLE);
            if (!empty) NovaUi.staggerIn(binding.answers);
        });
    }

    @Override
    public void onVote(NovaDtos.Answer answer) {
        repository.vote("answer", answer.id).observe(this, resource -> {
            if (binding == null || resource == null) return;
            if (resource.status == Resource.Status.SUCCESS && resource.data != null) {
                answer.votes = resource.data.votes;
                answer.votedByMe = resource.data.voted;
                adapter.notifyDataSetChanged();
            } else if (resource.status == Resource.Status.ERROR) {
                showError(resource.message);
            }
        });
    }

    @Override
    public void onAccept(NovaDtos.Answer answer) {
        repository.accept(answer.id).observe(this, resource -> {
            if (binding == null || resource == null) return;
            if (resource.status == Resource.Status.SUCCESS) {
                solved = true;
                binding.tvSolved.setVisibility(View.VISIBLE);
                adapter.setOwnerState(mine, solved);
                loadAnswers();
                Toast.makeText(this, R.string.nova_study_accepted, Toast.LENGTH_SHORT).show();
            } else if (resource.status == Resource.Status.ERROR) {
                showError(resource.message);
            }
        });
    }

    @Override
    public void onDelete(NovaDtos.Answer answer) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.nova_delete)
                .setMessage(R.string.nova_delete_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.nova_delete, (d, w) ->
                        repository.deleteAnswer(answer.id).observe(this, resource -> {
                            if (binding == null || resource == null) return;
                            if (resource.status == Resource.Status.SUCCESS) {
                                loadAnswers();
                            } else if (resource.status == Resource.Status.ERROR) {
                                showError(resource.message);
                            }
                        }))
                .show();
    }

    private void deleteQuestion() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.nova_delete)
                .setMessage(R.string.nova_delete_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.nova_delete, (d, w) ->
                        repository.deleteQuestion(questionId).observe(this, resource -> {
                            if (binding == null || resource == null) return;
                            if (resource.status == Resource.Status.SUCCESS) {
                                Toast.makeText(this, R.string.nova_qa_deleted,
                                        Toast.LENGTH_SHORT).show();
                                finish();
                            } else if (resource.status == Resource.Status.ERROR) {
                                showError(resource.message);
                            }
                        }))
                .show();
    }

    private void sendAnswer() {
        String text = binding.etAnswer.getText().toString().trim();
        if (text.isEmpty()) {
            binding.etAnswer.setError(getString(R.string.nova_qa_empty_text));
            return;
        }
        NovaModerationDialog.guard(this, "answer", text, 2, false,
                () -> {
                    binding.etAnswer.setText("");
                    repository.answer(questionId, text).observe(this, resource -> {
                        if (binding == null || resource == null) return;
                        if (resource.status == Resource.Status.SUCCESS) {
                            Toast.makeText(this, R.string.nova_qa_answer_sent,
                                    Toast.LENGTH_SHORT).show();
                            loadAnswers();
                        } else if (resource.status == Resource.Status.ERROR) {
                            binding.etAnswer.setText(text);
                            showError(resource.message);
                        }
                    });
                },
                () -> binding.etAnswer.setText(text));
    }

    private void showError(String message) {
        binding.tvError.setVisibility(View.VISIBLE);
        // The backend answers with stable codes; "own_content" is a real rule, not a failure.
        if ("own_content".equals(message)) {
            binding.tvError.setText(R.string.nova_study_own_vote);
            return;
        }
        binding.tvError.setText(getString(R.string.nova_error)
                + (message == null ? "" : " (" + message + ")"));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
