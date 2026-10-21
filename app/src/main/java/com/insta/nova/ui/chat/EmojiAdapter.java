package com.insta.nova.ui.chat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.insta.nova.R;
import java.util.List;

public class EmojiAdapter extends RecyclerView.Adapter<EmojiAdapter.ViewHolder> {

    public interface OnEmojiClickListener {
        void onEmojiClick(String emoji);
    }

    private final List<String> emojis;
    private final OnEmojiClickListener listener;

    public EmojiAdapter(List<String> emojis, OnEmojiClickListener listener) {
        this.emojis = emojis;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_emoji_tile, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String emoji = emojis.get(position);
        holder.tvEmoji.setText(emoji);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onEmojiClick(emoji);
        });
    }

    @Override
    public int getItemCount() {
        return emojis != null ? emojis.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvEmoji;
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvEmoji = itemView.findViewById(R.id.tvEmoji);
        }
    }
}
