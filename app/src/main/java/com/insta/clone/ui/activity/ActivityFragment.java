package com.insta.clone.ui.activity;

import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.insta.clone.R;
import com.insta.clone.data.local.SessionManager;
import com.insta.clone.data.remote.RetrofitClient;
import com.insta.clone.data.remote.api.RestApi;
import com.insta.clone.data.remote.dto.NotificationDto;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * BottomSheet displaying recent notifications (Likes, Comments, Follows) from Supabase.
 */
public class ActivityFragment extends BottomSheetDialogFragment {

    private final List<NotificationDto> notifications = new ArrayList<>();
    private NotificationAdapter adapter;
    private ProgressBar progressBar;
    private TextView tvEmpty;

    public ActivityFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.layout_new_message_sheet, container, false);

        TextView tvTitle = view.findViewById(R.id.et_search); // we can hide search or replace layout
        // Instead, let's programmatically construct or inflate a simple notification sheet
        return createNotificationSheetView(inflater, container);
    }

    private View createNotificationSheetView(LayoutInflater inflater, ViewGroup container) {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(requireContext());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setBackgroundColor(requireContext().getColor(R.color.color_background));
        layout.setPadding(0, 16, 0, 24);

        View dragHandle = new View(requireContext());
        android.widget.LinearLayout.LayoutParams handleLp = new android.widget.LinearLayout.LayoutParams(80, 8);
        handleLp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        dragHandle.setLayoutParams(handleLp);
        dragHandle.setBackgroundResource(R.drawable.bg_drag_handle);
        layout.addView(dragHandle);

        TextView tvTitle = new TextView(requireContext());
        tvTitle.setText("Notifications");
        tvTitle.setTextSize(18);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setTextColor(requireContext().getColor(R.color.color_text_primary));
        tvTitle.setPadding(32, 24, 32, 16);
        layout.addView(tvTitle);

        View divider = new View(requireContext());
        divider.setLayoutParams(new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        divider.setBackgroundColor(requireContext().getColor(R.color.color_divider));
        layout.addView(divider);

        progressBar = new ProgressBar(requireContext());
        android.widget.LinearLayout.LayoutParams pbLp = new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pbLp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        pbLp.topMargin = 40;
        progressBar.setLayoutParams(pbLp);
        layout.addView(progressBar);

        tvEmpty = new TextView(requireContext());
        tvEmpty.setText("No notifications yet");
        tvEmpty.setTextColor(requireContext().getColor(R.color.color_text_secondary));
        tvEmpty.setTextSize(14);
        tvEmpty.setGravity(android.view.Gravity.CENTER);
        android.widget.LinearLayout.LayoutParams emptyLp = new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        emptyLp.topMargin = 40;
        emptyLp.bottomMargin = 40;
        tvEmpty.setLayoutParams(emptyLp);
        tvEmpty.setVisibility(View.GONE);
        layout.addView(tvEmpty);

        RecyclerView rv = new RecyclerView(requireContext());
        rv.setLayoutParams(new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new NotificationAdapter(notifications);
        rv.setAdapter(adapter);
        layout.addView(rv);

        loadNotifications();

        return layout;
    }

    private void loadNotifications() {
        SessionManager sm = SessionManager.getInstance(requireContext());
        String myId = sm.getUserId();
        if (myId == null) {
            progressBar.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.VISIBLE);
            return;
        }

        RestApi api = RetrofitClient.getInstance(requireContext()).getRestApi();
        Map<String, String> query = new HashMap<>();
        query.put("user_id", "eq." + myId);
        query.put("order", "created_at.desc");
        query.put("limit", "50");

        api.getNotifications(query).enqueue(new Callback<List<NotificationDto>>() {
            @Override
            public void onResponse(Call<List<NotificationDto>> call, Response<List<NotificationDto>> response) {
                if (isAdded()) {
                    progressBar.setVisibility(View.GONE);
                    if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                        notifications.clear();
                        notifications.addAll(response.body());
                        adapter.notifyDataSetChanged();
                        tvEmpty.setVisibility(View.GONE);
                    } else {
                        tvEmpty.setVisibility(View.VISIBLE);
                    }
                }
            }

            @Override
            public void onFailure(Call<List<NotificationDto>> call, Throwable t) {
                if (isAdded()) {
                    progressBar.setVisibility(View.GONE);
                    tvEmpty.setVisibility(notifications.isEmpty() ? View.VISIBLE : View.GONE);
                }
            }
        });
    }

    private static class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.Holder> {
        private final List<NotificationDto> items;

        NotificationAdapter(List<NotificationDto> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification_row, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            NotificationDto item = items.get(position);
            String actor = item.getActorUsername() != null ? item.getActorUsername() : "Someone";
            String type = item.getType() != null ? item.getType() : "";
            String message;

            if ("like".equalsIgnoreCase(type)) {
                message = "<b>" + actor + "</b> liked your post.";
            } else if ("comment".equalsIgnoreCase(type)) {
                message = "<b>" + actor + "</b> commented: " + (item.getText() != null ? item.getText() : "");
            } else if ("follow".equalsIgnoreCase(type)) {
                message = "<b>" + actor + "</b> started following you.";
            } else {
                message = "<b>" + actor + "</b> " + (item.getText() != null ? item.getText() : "interacted with you.");
            }

            holder.tvText.setText(Html.fromHtml(message, Html.FROM_HTML_MODE_LEGACY));

            if (item.getActorAvatarUrl() != null && !item.getActorAvatarUrl().isEmpty()) {
                com.insta.clone.util.GlideUtils.loadAvatar(holder.ivAvatar, item.getActorAvatarUrl());
            } else {
                holder.ivAvatar.setImageResource(R.drawable.ic_person_placeholder);
            }

            if (item.getPostThumbnailUrl() != null && !item.getPostThumbnailUrl().isEmpty()) {
                holder.ivPostThumb.setVisibility(View.VISIBLE);
                com.insta.clone.util.GlideUtils.load(holder.ivPostThumb, "posts", item.getPostThumbnailUrl());
            } else {
                holder.ivPostThumb.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class Holder extends RecyclerView.ViewHolder {
            final ImageView ivAvatar;
            final TextView tvText;
            final ImageView ivPostThumb;

            Holder(@NonNull View itemView) {
                super(itemView);
                ivAvatar = itemView.findViewById(R.id.iv_actor_avatar);
                tvText = itemView.findViewById(R.id.tv_notification_text);
                ivPostThumb = itemView.findViewById(R.id.iv_post_thumb);
            }
        }
    }
}
