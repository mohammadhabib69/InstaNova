package com.insta.nova.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.insta.nova.R;

/**
 * Single-item footer adapter displaying the "You're all caught up" card.
 */
public class CaughtUpAdapter extends RecyclerView.Adapter<CaughtUpAdapter.ViewHolder> {

    private boolean visible = false;

    public void setVisible(boolean visible) {
        if (this.visible != visible) {
            this.visible = visible;
            notifyDataSetChanged();
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_all_caught_up, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {}

    @Override
    public int getItemCount() {
        return visible ? 1 : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
