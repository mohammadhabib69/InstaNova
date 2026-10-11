package com.insta.clone.ui.chat;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.insta.clone.R;
import com.insta.clone.data.model.Conversation;
import com.insta.clone.data.model.Profile;
import com.insta.clone.data.repository.ChatRepository;
import com.insta.clone.data.repository.SearchRepository;
import com.insta.clone.util.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * Direct messaging inbox activity showing conversation threads.
 */
public class InboxActivity extends AppCompatActivity {

    private ChatRepository chatRepository;
    private SearchRepository searchRepository;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView rvConversations;
    private View layoutEmpty;
    private ConversationAdapter adapter;
    private final List<Conversation> conversationList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inbox);

        chatRepository = ChatRepository.getInstance(this);
        searchRepository = new SearchRepository(this);

        ImageView ivBack = findViewById(R.id.iv_back);
        ImageView ivNewChat = findViewById(R.id.iv_new_chat);
        swipeRefresh = findViewById(R.id.swipe_refresh);
        rvConversations = findViewById(R.id.rv_conversations);
        layoutEmpty = findViewById(R.id.layout_empty);
        Button btnEmptySend = findViewById(R.id.btn_empty_send);

        ivBack.setOnClickListener(v -> finish());
        ivNewChat.setOnClickListener(v -> showNewMessageSheet());
        if (btnEmptySend != null) {
            btnEmptySend.setOnClickListener(v -> showNewMessageSheet());
        }

        adapter = new ConversationAdapter(conversationList, conv -> {
            Intent intent = new Intent(InboxActivity.this, ChatActivity.class);
            intent.putExtra("EXTRA_CONVERSATION_ID", conv.getId());
            intent.putExtra("EXTRA_OTHER_USER_ID", conv.getOtherUserId());
            intent.putExtra("EXTRA_OTHER_USERNAME", conv.getOtherUsername());
            intent.putExtra("EXTRA_OTHER_AVATAR", conv.getOtherAvatarUrl());
            startActivity(intent);
        });

        rvConversations.setLayoutManager(new LinearLayoutManager(this));
        rvConversations.setAdapter(adapter);

        swipeRefresh.setOnRefreshListener(this::loadInbox);

        loadInbox();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadInbox();
    }

    private void loadInbox() {
        swipeRefresh.setRefreshing(true);
        chatRepository.getConversations().observe(this, resource -> {
            swipeRefresh.setRefreshing(false);
            if (resource != null && resource.status == Resource.Status.SUCCESS && resource.data != null) {
                conversationList.clear();
                conversationList.addAll(resource.data);
                adapter.notifyDataSetChanged();
                layoutEmpty.setVisibility(conversationList.isEmpty() ? View.VISIBLE : View.GONE);
                rvConversations.setVisibility(conversationList.isEmpty() ? View.GONE : View.VISIBLE);
            } else if (resource != null && resource.status == Resource.Status.ERROR) {
                if (conversationList.isEmpty()) {
                    layoutEmpty.setVisibility(View.VISIBLE);
                    rvConversations.setVisibility(View.GONE);
                }
            }
        });
    }

    private void showNewMessageSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.InstagramBottomSheetDialog);
        View sheet = LayoutInflater.from(this).inflate(R.layout.layout_new_message_sheet, null);
        int sheetHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.85);
        sheet.setMinimumHeight(sheetHeight);
        dialog.setContentView(sheet);

        dialog.setOnShowListener(d -> {
            View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                com.google.android.material.bottomsheet.BottomSheetBehavior<?> behavior =
                        com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet);
                behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
                behavior.setPeekHeight(sheetHeight);
            }
        });

        EditText etSearch = sheet.findViewById(R.id.et_search);
        RecyclerView rvUsers = sheet.findViewById(R.id.rv_users);
        ProgressBar pbLoading = sheet.findViewById(R.id.pb_loading);
        ImageView ivClose = sheet.findViewById(R.id.iv_close);

        ivClose.setOnClickListener(v -> dialog.dismiss());

        List<Profile> userList = new ArrayList<>();
        UserPickerAdapter userAdapter = new UserPickerAdapter(userList, profile -> {
            pbLoading.setVisibility(View.VISIBLE);
            chatRepository.getOrCreateConversation(profile.getId()).observe(InboxActivity.this, res -> {
                pbLoading.setVisibility(View.GONE);
                if (res != null && res.status == Resource.Status.SUCCESS && res.data != null) {
                    dialog.dismiss();
                    Intent intent = new Intent(InboxActivity.this, ChatActivity.class);
                    intent.putExtra("EXTRA_CONVERSATION_ID", res.data);
                    intent.putExtra("EXTRA_OTHER_USER_ID", profile.getId());
                    intent.putExtra("EXTRA_OTHER_USERNAME", profile.getUsername());
                    intent.putExtra("EXTRA_OTHER_AVATAR", profile.getAvatarUrl());
                    startActivity(intent);
                } else if (res != null && res.status == Resource.Status.ERROR) {
                    Toast.makeText(InboxActivity.this, "Failed to start chat: " + res.message, Toast.LENGTH_SHORT).show();
                }
            });
        });

        rvUsers.setLayoutManager(new LinearLayoutManager(this));
        rvUsers.setAdapter(userAdapter);

        // Initially show suggested users
        pbLoading.setVisibility(View.VISIBLE);
        searchRepository.getSuggestedUsers(15).observe(this, res -> {
            pbLoading.setVisibility(View.GONE);
            if (res != null && res.status == Resource.Status.SUCCESS && res.data != null) {
                userList.clear();
                userList.addAll(res.data);
                userAdapter.notifyDataSetChanged();
            }
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String q = s != null ? s.toString().trim() : "";
                if (q.isEmpty()) {
                    searchRepository.getSuggestedUsers(15).observe(InboxActivity.this, res -> {
                        if (res != null && res.status == Resource.Status.SUCCESS && res.data != null) {
                            userList.clear();
                            userList.addAll(res.data);
                            userAdapter.notifyDataSetChanged();
                        }
                    });
                } else {
                    pbLoading.setVisibility(View.VISIBLE);
                    searchRepository.searchUsers(q).observe(InboxActivity.this, res -> {
                        pbLoading.setVisibility(View.GONE);
                        if (res != null && res.status == Resource.Status.SUCCESS && res.data != null) {
                            userList.clear();
                            userList.addAll(res.data);
                            userAdapter.notifyDataSetChanged();
                        }
                    });
                }
            }
        });

        dialog.show();
    }

    // --- Adapters ---

    private static class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.ViewHolder> {
        private final List<Conversation> items;
        private final OnConversationClickListener listener;

        interface OnConversationClickListener {
            void onClick(Conversation conversation);
        }

        ConversationAdapter(List<Conversation> items, OnConversationClickListener listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_conversation, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Conversation c = items.get(position);
            holder.tvUsername.setText(c.getOtherUsername());
            String lastMsg = c.getLastMessage();
            if (lastMsg == null || lastMsg.trim().isEmpty() || "Photo".equalsIgnoreCase(lastMsg.trim())) {
                lastMsg = "Sent a photo";
            }
            holder.tvLastMessage.setText(lastMsg);
            holder.ivUnreadDot.setVisibility(c.isUnread() ? View.VISIBLE : View.GONE);

            if (c.getOtherAvatarUrl() != null && !c.getOtherAvatarUrl().isEmpty()) {
                com.insta.clone.util.GlideUtils.loadAvatar(holder.ivAvatar, c.getOtherAvatarUrl());
            } else {
                holder.ivAvatar.setImageResource(R.drawable.ic_person_placeholder);
            }

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onClick(c);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final ImageView ivAvatar;
            final TextView tvUsername;
            final TextView tvLastMessage;
            final TextView tvTime;
            final View ivUnreadDot;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                ivAvatar = itemView.findViewById(R.id.iv_avatar);
                tvUsername = itemView.findViewById(R.id.tv_username);
                tvLastMessage = itemView.findViewById(R.id.tv_last_message);
                tvTime = itemView.findViewById(R.id.tv_time);
                ivUnreadDot = itemView.findViewById(R.id.iv_unread_dot);
            }
        }
    }

    private static class UserPickerAdapter extends RecyclerView.Adapter<UserPickerAdapter.ViewHolder> {
        private final List<Profile> items;
        private final OnUserClickListener listener;

        interface OnUserClickListener {
            void onUserClick(Profile profile);
        }

        UserPickerAdapter(List<Profile> items, OnUserClickListener listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user_row, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Profile profile = items.get(position);
            holder.tvUsername.setText(profile.getUsername());
            holder.tvFullName.setText(profile.getFullName() != null ? profile.getFullName() : "");

            if (profile.getAvatarUrl() != null && !profile.getAvatarUrl().isEmpty()) {
                com.insta.clone.util.GlideUtils.loadAvatar(holder.ivAvatar, profile.getAvatarUrl());
            } else {
                holder.ivAvatar.setImageResource(R.drawable.ic_person_placeholder);
            }

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onUserClick(profile);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final ImageView ivAvatar;
            final TextView tvUsername;
            final TextView tvFullName;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                ivAvatar = itemView.findViewById(R.id.iv_avatar);
                tvUsername = itemView.findViewById(R.id.tv_username);
                tvFullName = itemView.findViewById(R.id.tv_full_name);
            }
        }
    }
}
