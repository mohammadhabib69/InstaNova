package com.insta.nova.study;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.insta.nova.R;
import com.insta.nova.data.NovaDtos;
import com.insta.nova.databinding.ItemNovaAnswerBinding;
import com.insta.nova.util.TimeUtils;
import com.insta.nova.widget.NovaUi;

import java.util.ArrayList;
import java.util.List;

/** Answers on a question, with vote / accept / delete wired to the caller. */
public class AnswerAdapter extends RecyclerView.Adapter<AnswerAdapter.Holder> {

    public interface Listener {
        void onVote(NovaDtos.Answer answer);

        void onAccept(NovaDtos.Answer answer);

        void onDelete(NovaDtos.Answer answer);
    }

    private final List<NovaDtos.Answer> items = new ArrayList<>();
    private final Listener listener;
    private boolean questionOwner;
    private boolean questionSolved;

    public AnswerAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<NovaDtos.Answer> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    public void setOwnerState(boolean owner, boolean solved) {
        questionOwner = owner;
        questionSolved = solved;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemNovaAnswerBinding.inflate(
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

        private final ItemNovaAnswerBinding binding;

        Holder(ItemNovaAnswerBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(NovaDtos.Answer a) {
            binding.tvBody.setText(a.body);
            binding.tvAccepted.setVisibility(a.accepted ? View.VISIBLE : View.GONE);

            StringBuilder meta = new StringBuilder();
            meta.append(a.authorUsername == null ? "" : "@" + a.authorUsername);
            long when = TimeUtils.parseTimestamp(a.createdAt);
            if (when > 0) {
                meta.append(" · ").append(TimeUtils.formatCompactRelativeTime(when));
            }
            binding.tvMeta.setText(meta.toString());

            binding.btnVote.setText(binding.getRoot().getContext()
                    .getString(R.string.nova_study_votes, a.votes));
            binding.btnVote.setTextColor(binding.getRoot().getContext().getColor(
                    a.votedByMe ? R.color.nova_study : R.color.nova_text_2));
            NovaUi.pressEffect(binding.btnVote);
            binding.btnVote.setOnClickListener(v -> {
                if (listener != null) listener.onVote(a);
            });

            // Accepting is the asker's call, and only until the question is solved.
            boolean canAccept = questionOwner && !questionSolved && !a.accepted;
            binding.btnAccept.setVisibility(canAccept ? View.VISIBLE : View.GONE);
            binding.btnAccept.setOnClickListener(v -> {
                if (listener != null) listener.onAccept(a);
            });

            binding.btnDelete.setVisibility(a.isMine ? View.VISIBLE : View.GONE);
            binding.btnDelete.setOnClickListener(v -> {
                if (listener != null) listener.onDelete(a);
            });
        }
    }
}
