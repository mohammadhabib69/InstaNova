package com.insta.nova.ui.chat;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.insta.nova.R;
import java.util.List;

public class ThemeAdapter extends RecyclerView.Adapter<ThemeAdapter.ViewHolder> {

    public interface OnThemeSelectedListener {
        void onThemeSelected(ChatTheme theme);
    }

    private final List<ChatTheme> themes;
    private final String currentThemeId;
    private final OnThemeSelectedListener listener;

    public ThemeAdapter(List<ChatTheme> themes, String currentThemeId, OnThemeSelectedListener listener) {
        this.themes = themes;
        this.currentThemeId = currentThemeId;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_theme_tile, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatTheme theme = themes.get(position);
        holder.tvThemeName.setText(theme.name);

        GradientDrawable d1 = new GradientDrawable();
        d1.setShape(GradientDrawable.OVAL);
        d1.setColor(theme.previewColor1);
        holder.viewSwatch1.setBackground(d1);

        GradientDrawable d2 = new GradientDrawable();
        d2.setShape(GradientDrawable.OVAL);
        d2.setColor(theme.previewColor2);
        holder.viewSwatch2.setBackground(d2);

        boolean isSelected = theme.id.equalsIgnoreCase(currentThemeId);
        holder.ivCheck.setVisibility(isSelected ? View.VISIBLE : View.GONE);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onThemeSelected(theme);
        });
    }

    @Override
    public int getItemCount() {
        return themes != null ? themes.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final View viewSwatch1;
        final View viewSwatch2;
        final TextView tvThemeName;
        final ImageView ivCheck;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            viewSwatch1 = itemView.findViewById(R.id.viewSwatch1);
            viewSwatch2 = itemView.findViewById(R.id.viewSwatch2);
            tvThemeName = itemView.findViewById(R.id.tvThemeName);
            ivCheck = itemView.findViewById(R.id.ivCheck);
        }
    }
}
