package com.insta.nova.ui.create;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.insta.nova.R;
import com.insta.nova.data.local.TemplateCatalog;
import java.util.List;

/**
 * "Made for you" — the four built-in cards. Tapping one opens the local editor;
 * nothing here touches the network.
 */
public class TemplatesActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_templates);

        findViewById(R.id.btnTemplatesBack).setOnClickListener(v -> finish());

        RecyclerView list = findViewById(R.id.rvTemplates);
        list.setLayoutManager(new GridLayoutManager(this, 2));
        list.setAdapter(new TemplateAdapter(TemplateCatalog.all(), template -> {
            Intent intent = new Intent(this, TemplateEditorActivity.class);
            intent.putExtra(TemplateCatalog.EXTRA_TEMPLATE_ID, template.id);
            startActivity(intent);
        }));
    }

    interface OnTemplateClick {
        void onTemplateClick(TemplateCatalog.Template template);
    }

    static class TemplateAdapter extends RecyclerView.Adapter<TemplateAdapter.Holder> {
        private final List<TemplateCatalog.Template> items;
        private final OnTemplateClick listener;

        TemplateAdapter(List<TemplateCatalog.Template> items, OnTemplateClick listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_template_card, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            TemplateCatalog.Template template = items.get(position);
            holder.tvBadge.setText(template.badge);
            holder.tvTitle.setText(template.title);
            holder.tvSubtitle.setText(template.subtitle);

            // Accent tint on the shared rounded background; mutate() keeps other
            // cards from inheriting it.
            if (holder.itemView.getBackground() != null) {
                GradientDrawable background =
                        (GradientDrawable) holder.itemView.getBackground().mutate();
                background.setColor(template.accentColor);
            }

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onTemplateClick(template);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class Holder extends RecyclerView.ViewHolder {
            final TextView tvBadge;
            final TextView tvTitle;
            final TextView tvSubtitle;

            Holder(@NonNull View itemView) {
                super(itemView);
                tvBadge = itemView.findViewById(R.id.tvTemplateBadge);
                tvTitle = itemView.findViewById(R.id.tvTemplateTitle);
                tvSubtitle = itemView.findViewById(R.id.tvTemplateSubtitle);
            }
        }
    }
}
