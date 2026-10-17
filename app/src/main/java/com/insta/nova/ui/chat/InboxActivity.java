package com.insta.nova.ui.chat;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
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
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.insta.nova.R;
import com.insta.nova.data.local.ChatSettingsStore;
import com.insta.nova.data.local.LocalChatStore;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.model.Conversation;
import com.insta.nova.data.model.Profile;
import com.insta.nova.data.repository.ChatRepository;
import com.insta.nova.data.repository.SearchRepository;
import com.insta.nova.util.Constants;
import com.insta.nova.util.Resource;
import com.insta.nova.util.TimeUtils;
import com.insta.nova.util.ToastUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Direct messaging inbox activity showing conversation threads.
 *
 * PAGE 6 presentation only: the repository, the empty state and the
 * Conversation -> ChatActivity navigation are unchanged. Added here: the
 * own-username top bar, the conversation filter over the already loaded list,
 * the row typography/colour states, the compact "{message} · {time}" second
 * line and the decorative chrome (title chevron, video, requests, row camera).
 */
public class InboxActivity extends AppCompatActivity {

    private ChatRepository chatRepository;
    private SearchRepository searchRepository;
    private SessionManager sessionManager;
    private LocalChatStore localChatStore;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView rvConversations;
    private View layoutEmpty;
    private View layoutRequestsEmpty;
    private TextView tvTitle;
    private TextView tvMessages;
    private TextView tvRequests;
    private ImageView ivTitleChevron;
    private ImageView ivVideo;
    private EditText etSearch;
    private ConversationAdapter adapter;
    private final List<Conversation> allConversations = new ArrayList<>();
    private final List<Conversation> visibleConversations = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inbox);

        chatRepository = ChatRepository.getInstance(this);
        searchRepository = new SearchRepository(this);
        localChatStore = new LocalChatStore(this);
        sessionManager = SessionManager.getInstance(this);

        ImageView ivBack = findViewById(R.id.iv_back);
        ImageView ivNewChat = findViewById(R.id.iv_new_chat);
        swipeRefresh = findViewById(R.id.swipe_refresh);
        rvConversations = findViewById(R.id.rv_conversations);
        layoutEmpty = findViewById(R.id.layout_empty);
        layoutRequestsEmpty = findViewById(R.id.layout_requests_empty);
        tvTitle = findViewById(R.id.tv_title);
        tvMessages = findViewById(R.id.tv_messages);
        tvRequests = findViewById(R.id.tv_requests);
        ivTitleChevron = findViewById(R.id.iv_title_chevron);
        ivVideo = findViewById(R.id.iv_video);
        etSearch = findViewById(R.id.et_inbox_search);
        Button btnEmptySend = findViewById(R.id.btn_empty_send);

        ivBack.setOnClickListener(v -> finish());
        ivNewChat.setOnClickListener(v -> showNewMessageSheet());
        if (btnEmptySend != null) {
            btnEmptySend.setOnClickListener(v -> showNewMessageSheet());
        }

        bindOwnUsername();
        setupSearchField();
        setupSectionRow();
        setupDecorativeChrome();

        adapter = new ConversationAdapter(visibleConversations, localChatStore, conv -> {
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
        // Local store changes only affect legacy call entries; real DM previews
        // and unread counts come from the server inbox RPC.
        // No initial load here: onResume always follows onCreate and also refreshes
        // when returning from a conversation, so loading in both issued two
        // identical requests on every entry.
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadInbox();
        // Returning from a thread clears its unread counter locally.
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    /** Top bar title: the signed-in username, 22sp bold. */
    private void bindOwnUsername() {
        String username = sessionManager != null ? sessionManager.getUsername() : null;
        if (username != null && !username.trim().isEmpty()) {
            tvTitle.setText(username);
        }
        // otherwise the layout keeps @string/direct_messages, as before
    }

    /** Existing conversation filter: filters the list already loaded in memory. */
    private void setupSearchField() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                applyConversationFilter(s != null ? s.toString() : "");
            }
        });
    }

    /** 'Messages' restores the thread list, 'Requests' opens the empty screen. */
    private void setupSectionRow() {
        tvMessages.setOnClickListener(v -> showMessageRequests(false));
        tvRequests.setOnClickListener(v -> showMessageRequests(true));
    }

    /**
     * Design-only chrome follows the shared Constants.DECORATIVE_UI contract:
     * visible with a 'Not available' toast when enabled, hidden when off.
     */
    private void setupDecorativeChrome() {
        if (Constants.DECORATIVE_UI) {
            ivTitleChevron.setVisibility(View.VISIBLE);
            ivVideo.setVisibility(View.VISIBLE);
            tvRequests.setVisibility(View.VISIBLE);
            ivTitleChevron.setOnClickListener(v -> ToastUtil.showNotAvailable(this));
            ivVideo.setOnClickListener(v -> {
                Intent intent = new Intent(InboxActivity.this, com.insta.nova.calling.CallHistoryActivity.class);
                startActivity(intent);
            });
        } else {
            ivTitleChevron.setVisibility(View.GONE);
            ivVideo.setVisibility(View.VISIBLE);
            ivVideo.setOnClickListener(v -> {
                Intent intent = new Intent(InboxActivity.this, com.insta.nova.calling.CallHistoryActivity.class);
                startActivity(intent);
            });
            tvRequests.setVisibility(View.GONE);
            showMessageRequests(false);
        }
    }

    private void showMessageRequests(boolean show) {
        if (!Constants.DECORATIVE_UI) {
            show = false;
        }
        layoutRequestsEmpty.setVisibility(show ? View.VISIBLE : View.GONE);
        swipeRefresh.setVisibility(show ? View.GONE : View.VISIBLE);
        if (show) {
            layoutEmpty.setVisibility(View.GONE);
        } else {
            layoutEmpty.setVisibility(allConversations.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void applyConversationFilter(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.US);
        visibleConversations.clear();
        if (needle.isEmpty()) {
            visibleConversations.addAll(allConversations);
        } else {
            for (Conversation c : allConversations) {
                String username = c.getOtherUsername() == null ? "" : c.getOtherUsername().toLowerCase(Locale.US);
                String fullName = c.getOtherFullName() == null ? "" : c.getOtherFullName().toLowerCase(Locale.US);
                if (username.contains(needle) || fullName.contains(needle)) {
                    visibleConversations.add(c);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void loadInbox() {
        swipeRefresh.setRefreshing(true);
        chatRepository.getConversations().observe(this, resource -> {
            swipeRefresh.setRefreshing(false);
            if (resource != null && resource.status == Resource.Status.SUCCESS && resource.data != null) {
                ChatSettingsStore settings = ChatSettingsStore.getInstance(InboxActivity.this);
                allConversations.clear();
                for (Conversation c : resource.data) {
                    if (c != null && !settings.isBlocked(c.getOtherUserId())) {
                        allConversations.add(c);
                    }
                }
                applyConversationFilter(etSearch.getText() != null ? etSearch.getText().toString() : "");
                layoutEmpty.setVisibility(allConversations.isEmpty() ? View.VISIBLE : View.GONE);
                rvConversations.setVisibility(allConversations.isEmpty() ? View.GONE : View.VISIBLE);
            } else if (resource != null && resource.status == Resource.Status.ERROR) {
                if (allConversations.isEmpty()) {
                    layoutEmpty.setVisibility(View.VISIBLE);
                    rvConversations.setVisibility(View.GONE);
                }
            }
        });
    }

    private void showNewMessageSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.InstaNovaBottomSheetDialog);
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
        private final LocalChatStore localChatStore;
        private final OnConversationClickListener listener;

        interface OnConversationClickListener {
            void onClick(Conversation conversation);
        }

        ConversationAdapter(List<Conversation> items, LocalChatStore localChatStore,
                            OnConversationClickListener listener) {
            this.items = items;
            this.localChatStore = localChatStore;
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
            Context context = holder.itemView.getContext();

            // Server inbox RPC is authoritative for real DM unread state.
            boolean isMuted = ChatSettingsStore.getInstance(context).isMuted(c.getId());
            int serverUnread = c.getUnreadCount() > 0 ? c.getUnreadCount() : (c.isUnread() ? 1 : 0);
            int unreadCount = isMuted ? 0 : serverUnread;
            boolean unread = unreadCount > 0;

            holder.tvUsername.setText(c.getOtherUsername());
            holder.ivMute.setVisibility(isMuted ? View.VISIBLE : View.GONE);
            // Name: bold while unread, regular once read
            holder.tvUsername.setTypeface(null, unread ? Typeface.BOLD : Typeface.NORMAL);

            // Preview and timestamp come from the shared server inbox row.
            String lastMsg = c.getLastMessage();
            String time = compactTime(c.getLastMessageAt());
            if (lastMsg == null || lastMsg.trim().isEmpty() || "Photo".equalsIgnoreCase(lastMsg.trim())) {
                lastMsg = context.getString(R.string.inbox_sent_a_photo);
            }
            holder.tvLastMessage.setText(time.isEmpty() ? lastMsg : lastMsg + " · " + time);
            holder.tvLastMessage.setTypeface(null, unread ? Typeface.BOLD : Typeface.NORMAL);
            holder.tvLastMessage.setTextColor(ContextCompat.getColor(context,
                    unread ? R.color.color_text_primary : R.color.color_text_secondary));

            // Numeric badge replaces the dot whenever there is something to count.
            holder.ivUnreadDot.setVisibility(View.GONE);
            if (unreadCount > 0) {
                holder.tvUnreadBadge.setVisibility(View.VISIBLE);
                holder.tvUnreadBadge.setText(unreadCount > 99 ? "99+" : String.valueOf(unreadCount));
            } else {
                holder.tvUnreadBadge.setVisibility(View.GONE);
            }

            // Camera: textPrimary while unread, muted grey once read
            ImageViewCompat.setImageTintList(holder.ivCamera, ColorStateList.valueOf(
                    ContextCompat.getColor(context, unread ? R.color.color_text_primary : R.color.color_icon_muted)));
            holder.ivCamera.setVisibility(Constants.DECORATIVE_UI ? View.VISIBLE : View.GONE);
            holder.ivCamera.setOnClickListener(Constants.DECORATIVE_UI
                    ? v -> ToastUtil.showNotAvailable(context) : null);

            // Story ring: only when the other user has an active story.
            // The Conversation model carries no story flag, so it stays hidden.
            holder.ringStory.setVisibility(View.GONE);

            if (c.getOtherAvatarUrl() != null && !c.getOtherAvatarUrl().isEmpty()) {
                com.insta.nova.util.GlideUtils.loadAvatar(holder.ivAvatar, c.getOtherAvatarUrl());
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

        /** 30s -> "1m", 45m -> "45m", 2h -> "2h", 3d -> "3d". */
        private static String compactTime(String iso) {
            return compactTime(TimeUtils.parseTimestamp(iso));
        }

        private static String compactTime(long timestamp) {
            return com.insta.nova.util.TimeFormat.formatCompactTime(timestamp);
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final ImageView ivAvatar;
            final View ringStory;
            final TextView tvUsername;
            final ImageView ivMute;
            final TextView tvLastMessage;
            final TextView tvTime;
            final View ivUnreadDot;
            final TextView tvUnreadBadge;
            final ImageView ivCamera;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                ivAvatar = itemView.findViewById(R.id.iv_avatar);
                ringStory = itemView.findViewById(R.id.ring_story);
                tvUsername = itemView.findViewById(R.id.tv_username);
                ivMute = itemView.findViewById(R.id.iv_mute);
                tvLastMessage = itemView.findViewById(R.id.tv_last_message);
                tvTime = itemView.findViewById(R.id.tv_time);
                ivUnreadDot = itemView.findViewById(R.id.iv_unread_dot);
                tvUnreadBadge = itemView.findViewById(R.id.tv_unread_badge);
                ivCamera = itemView.findViewById(R.id.iv_camera);
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
                com.insta.nova.util.GlideUtils.loadAvatar(holder.ivAvatar, profile.getAvatarUrl());
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
