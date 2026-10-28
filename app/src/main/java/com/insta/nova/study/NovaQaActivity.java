package com.insta.nova.study;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.insta.nova.R;
import com.insta.nova.data.NovaDtos;
import com.insta.nova.data.StudyRepository;
import com.insta.nova.databinding.ActivityNovaQaBinding;
import com.insta.nova.databinding.DialogNovaAskBinding;
import com.insta.nova.hub.NovaScreen;
import com.insta.nova.safe.NovaModerationDialog;
import com.insta.nova.util.Resource;
import com.insta.nova.widget.NovaUi;

/**
 * Study Q&amp;A board. Every tab and subject maps to a real query; asking runs through the
 * shared moderation guard so a question cannot bypass Nova Safe.
 */
public class NovaQaActivity extends AppCompatActivity {

    private ActivityNovaQaBinding binding;
    private StudyRepository repository;
    private QaAdapter adapter;
    private String tab = "latest";
    private String subject = Subjects.ALL;
    private boolean loading;
    private String pendingTitle = "";
    private String pendingBody = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNovaQaBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        NovaScreen.bindHeader(this, R.drawable.ic_nova_study,
                R.string.nova_qa_title, R.color.nova_study);

        repository = new StudyRepository(this);
        adapter = new QaAdapter(this::open);
        binding.list.setLayoutManager(new LinearLayoutManager(this));
        binding.list.setAdapter(adapter);

        binding.btnLatest.setOnClickListener(v -> selectTab("latest"));
        binding.btnUnsolved.setOnClickListener(v -> selectTab("unsolved"));
        binding.btnMine.setOnClickListener(v -> selectTab("mine"));
        binding.btnAsk.setOnClickListener(v -> askDialog());
        binding.swipe.setOnRefreshListener(this::load);

        buildSubjectChips();
        renderTabs();
        load();
    }

    private void selectTab(String value) {
        if (value.equals(tab)) return;
        tab = value;
        renderTabs();
        load();
    }

    private void renderTabs() {
        style(binding.btnLatest, "latest".equals(tab));
        style(binding.btnUnsolved, "unsolved".equals(tab));
        style(binding.btnMine, "mine".equals(tab));
    }

    private void style(TextView chip, boolean active) {
        chip.setBackgroundResource(active ? R.drawable.bg_nova_chip_on : R.drawable.bg_nova_chip);
        chip.setTextColor(getColor(active ? R.color.color_background : R.color.nova_text_3));
    }

    private void buildSubjectChips() {
        binding.subjectRow.removeAllViews();
        // Built-ins plus any subject the user created, so a custom one filters like the rest.
        String[] custom = Subjects.allKeys(this);
        String[] keys = new String[custom.length + 1];
        keys[0] = Subjects.ALL;
        System.arraycopy(custom, 0, keys, 1, custom.length);
        for (String key : keys) {
            boolean active = key.equals(subject);
            TextView chip = new TextView(this);
            chip.setText(Subjects.label(this, key));
            chip.setTextSize(11f);
            chip.setSingleLine(true);
            chip.setGravity(Gravity.CENTER);
            chip.setMinHeight(NovaUi.dp(this, 34));
            chip.setPadding(NovaUi.dp(this, 12), 0, NovaUi.dp(this, 12), 0);
            chip.setBackgroundResource(active
                    ? R.drawable.bg_nova_chip_on : R.drawable.bg_nova_chip);
            chip.setTextColor(getColor(active ? R.color.color_background : R.color.nova_text_3));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(NovaUi.dp(this, 6));
            chip.setLayoutParams(params);
            chip.setOnClickListener(v -> {
                if (key.equals(subject)) return;
                subject = key;
                buildSubjectChips();
                load();
            });
            binding.subjectRow.addView(chip);
        }
    }

    private void load() {
        if (loading) return;
        loading = true;
        binding.progress.setVisibility(View.VISIBLE);
        binding.tvError.setVisibility(View.GONE);
        boolean first = adapter.getItemCount() == 0;
        repository.questions(tab, subject).observe(this, resource -> {
            if (binding == null || resource == null) return;
            if (resource.status == Resource.Status.LOADING) return;
            loading = false;
            binding.progress.setVisibility(View.GONE);
            binding.swipe.setRefreshing(false);
            if (resource.status == Resource.Status.SUCCESS) {
                java.util.List<NovaDtos.Question> rows = resource.data;
                adapter.submit(rows);
                boolean empty = rows == null || rows.isEmpty();
                binding.tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                binding.swipe.setVisibility(empty ? View.GONE : View.VISIBLE);
                if (!empty) NovaUi.staggerIn(binding.list);
            } else if (resource.status == Resource.Status.ERROR) {
                binding.tvError.setVisibility(View.VISIBLE);
                binding.tvError.setText(getString(R.string.nova_error)
                        + (resource.message == null ? "" : " (" + resource.message + ")"));
                boolean empty = adapter.getItemCount() == 0 && first;
                binding.tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                binding.swipe.setVisibility(empty ? View.GONE : View.VISIBLE);
            }
        });
    }

    private void askDialog() {
        DialogNovaAskBinding ask = DialogNovaAskBinding.inflate(getLayoutInflater());
        // A draft is restored once (guard -> Edit); a brand new question starts empty.
        ask.etTitle.setText(pendingTitle);
        ask.etBody.setText(pendingBody);
        pendingTitle = "";
        pendingBody = "";
        final String[] pick = {Subjects.allKeys(this)[0]};
        buildAskChips(ask.subjectRow, pick);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.nova_study_ask)
                .setView(ask.getRoot())
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.nova_study_ask, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String title = ask.etTitle.getText().toString().trim();
                    String body = ask.etBody.getText().toString().trim();
                    // nova_questions_title_check rejects short titles; match it here so the
                    // user is never bounced by the database after submitting.
                    if (title.length() < 6) {
                        ask.etTitle.setError(getString(R.string.nova_qa_title_short));
                        return;
                    }
                    if (body.length() < 4) {
                        // The backend rejects an empty body, so catch it here instead.
                        ask.etBody.setError(getString(R.string.nova_qa_empty_text));
                        return;
                    }
                    String text = title + "\n" + body;
                    dialog.dismiss();
                    NovaModerationDialog.guard(this, "question", text, 2, false,
                            () -> send(title, body, pick[0]),
                            () -> {
                                // Keep what was typed so "Edit" returns to a filled form.
                                pendingTitle = title;
                                pendingBody = body;
                                askDialog();
                            });
                }));
        dialog.show();
    }

    private void buildAskChips(LinearLayout row, String[] pick) {
        row.removeAllViews();
        row.setTag(pick[0]);
        for (String key : Subjects.allKeys(this)) {
            TextView chip = new TextView(this);
            boolean active = key.equals(pick[0]);
            chip.setText(Subjects.label(this, key));
            chip.setTextSize(10f);
            chip.setGravity(Gravity.CENTER);
            chip.setMinHeight(NovaUi.dp(this, 32));
            chip.setPadding(NovaUi.dp(this, 8), 0, NovaUi.dp(this, 8), 0);
            chip.setBackgroundResource(active
                    ? R.drawable.bg_nova_chip_on : R.drawable.bg_nova_chip);
            chip.setTextColor(getColor(active ? R.color.color_background : R.color.nova_text_3));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            params.setMarginEnd(NovaUi.dp(this, 4));
            chip.setLayoutParams(params);
            chip.setOnClickListener(v -> {
                pick[0] = key;
                buildAskChips(row, pick);
            });
            row.addView(chip);
        }

        // Custom subject: the fixed four are rarely enough for a real course load.
        TextView add = new TextView(this);
        add.setText(R.string.nova_study_subject_custom);
        add.setTextSize(10f);
        add.setSingleLine(true);
        add.setGravity(Gravity.CENTER);
        add.setMinHeight(NovaUi.dp(this, 32));
        add.setPadding(NovaUi.dp(this, 8), 0, NovaUi.dp(this, 8), 0);
        add.setBackgroundResource(R.drawable.bg_nova_chip);
        add.setTextColor(getColor(R.color.nova_cyan));
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        addParams.setMarginStart(NovaUi.dp(this, 4));
        add.setLayoutParams(addParams);
        add.setOnClickListener(v -> promptCustomSubject(row, pick));
        row.addView(add);
    }

    /** Asks for a subject name, stores it on the device and selects it straight away. */
    private void promptCustomSubject(LinearLayout row, String[] pick) {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint(R.string.nova_study_subject_custom_hint);
        input.setSingleLine(true);
        int pad = NovaUi.dp(this, 18);
        input.setPadding(pad, pad, pad, pad);
        new AlertDialog.Builder(this)
                .setTitle(R.string.nova_study_subject_custom)
                .setView(input)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.nova_study_subject_custom_add, (d, w) -> {
                    String key = Subjects.addCustom(this, input.getText().toString());
                    if (key == null) {
                        Toast.makeText(this, R.string.nova_study_subject_custom_invalid,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    pick[0] = key;
                    buildAskChips(row, pick);
                })
                .show();
    }

    private void send(String title, String body, String subjectKey) {
        repository.ask(subjectKey, title, body, null).observe(this, resource -> {
            if (binding == null || resource == null) return;
            if (resource.status == Resource.Status.SUCCESS) {
                pendingTitle = "";
                pendingBody = "";
                Toast.makeText(this, R.string.nova_qa_question_sent, Toast.LENGTH_SHORT).show();
                tab = "latest";
                renderTabs();
                loading = false;
                load();
            } else if (resource.status == Resource.Status.ERROR) {
                showError(resource.message);
            }
        });
    }

    private void open(NovaDtos.Question question) {
        Intent intent = new Intent(this, NovaQuestionActivity.class);
        intent.putExtra(NovaQuestionActivity.EXTRA_ID, question.id);
        intent.putExtra(NovaQuestionActivity.EXTRA_TITLE, question.title);
        intent.putExtra(NovaQuestionActivity.EXTRA_BODY, question.body);
        intent.putExtra(NovaQuestionActivity.EXTRA_SUBJECT, question.subject);
        intent.putExtra(NovaQuestionActivity.EXTRA_AUTHOR, question.authorUsername);
        intent.putExtra(NovaQuestionActivity.EXTRA_VOTES, question.votes);
        intent.putExtra(NovaQuestionActivity.EXTRA_SOLVED, question.solved);
        intent.putExtra(NovaQuestionActivity.EXTRA_MINE, question.isMine);
        startActivity(intent);
    }

    private void showError(String message) {
        binding.tvError.setVisibility(View.VISIBLE);
        binding.tvError.setText(getString(R.string.nova_error)
                + (message == null ? "" : " (" + message + ")"));
        // The error line can sit below the fold, so say it out loud too.
        Toast.makeText(this, binding.tvError.getText(), Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Answers or deletes may have changed the board while we were away.
        if (adapter != null && !loading) load();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
