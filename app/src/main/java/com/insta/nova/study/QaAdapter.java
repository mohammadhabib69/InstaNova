package com.insta.nova.study;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.insta.nova.R;
import com.insta.nova.data.NovaDtos;
import com.insta.nova.databinding.ItemNovaQuestionBinding;
import com.insta.nova.util.TimeUtils;
import com.insta.nova.widget.NovaUi;

import java.util.ArrayList;
import java.util.List;

/** Question cards for the study board. */
public class QaAdapter extends RecyclerView.Adapter<QaAdapter.Holder> {

    public interface OnOpen {
        void open(NovaDtos.Question question);
    }

    private final List<NovaDtos.Question> items = new ArrayList<>();
    private final OnOpen onOpen;

    public QaAdapter(OnOpen onOpen) {
        this.onOpen = onOpen;
    }

    public void submit(List<NovaDtos.Question> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemNovaQuestionBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class Holder extends RecyclerView.ViewHolder {

        private final ItemNovaQuestionBinding binding;

        Holder(ItemNovaQuestionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(NovaDtos.Question q) {
            binding.tvSubject.setText(Subjects.label(binding.getRoot().getContext(), q.subject));
            binding.tvSolved.setVisibility(q.solved ? View.VISIBLE : View.GONE);
            binding.tvTitle.setText(q.title);
            boolean hasBody = q.body != null && !q.body.trim().isEmpty();
            binding.tvBody.setVisibility(hasBody ? View.VISIBLE : View.GONE);
            binding.tvBody.setText(q.body);
            binding.tvVotes.setText(binding.getRoot().getContext()
                    .getString(R.string.nova_study_votes, q.votes));

            StringBuilder meta = new StringBuilder();
            meta.append(q.authorUsername == null ? "" : "@" + q.authorUsername);
            meta.append(" · ").append(binding.getRoot().getContext()
                    .getString(R.string.nova_study_answers, q.answersCount));
            long when = TimeUtils.parseTimestamp(q.createdAt);
            if (when > 0) {
                meta.append(" · ").append(TimeUtils.formatCompactRelativeTime(when));
            }
            binding.tvMeta.setText(meta.toString());

            NovaUi.pressEffect(binding.getRoot());
            binding.getRoot().setOnClickListener(v -> {
                if (onOpen != null) onOpen.open(q);
            });
        }
    }
}
