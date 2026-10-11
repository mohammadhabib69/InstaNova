package com.insta.clone.ui.chat;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.insta.clone.R;
import com.insta.clone.data.local.SessionManager;
import com.insta.clone.data.model.Message;
import com.insta.clone.data.repository.ChatRepository;
import com.insta.clone.data.repository.StorageRepository;
import com.insta.clone.util.ImageUtils;
import com.insta.clone.util.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * 1-on-1 Direct Message Chat Activity supporting text, images, 3-second polling, and read receipts.
 */
public class ChatActivity extends AppCompatActivity {

    private String conversationId;
    private String otherUserId;
    private String otherUsername;
    private String otherAvatarUrl;
    private String myUserId;

    private ChatRepository chatRepository;
    private StorageRepository storageRepository;

    private RecyclerView rvMessages;
    private EditText etMessage;
    private TextView btnSend;
    private ImageView btnAttachImage;
    private ProgressBar pbUploading;
    private MessageAdapter adapter;
    private final List<Message> messageList = new ArrayList<>();

    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            fetchMessagesSilently();
            pollHandler.postDelayed(this, 3000);
        }
    };

    private final ActivityResultLauncher<PickVisualMediaRequest> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), this::handleImagePicked);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        conversationId = getIntent().getStringExtra("EXTRA_CONVERSATION_ID");
        otherUserId = getIntent().getStringExtra("EXTRA_OTHER_USER_ID");
        otherUsername = getIntent().getStringExtra("EXTRA_OTHER_USERNAME");
        otherAvatarUrl = getIntent().getStringExtra("EXTRA_OTHER_AVATAR");

        SessionManager sessionManager = SessionManager.getInstance(this);
        myUserId = sessionManager.getUserId();

        chatRepository = ChatRepository.getInstance(this);
        storageRepository = new StorageRepository(this);

        ImageView ivBack = findViewById(R.id.iv_back);
        ImageView ivAvatar = findViewById(R.id.iv_avatar);
        TextView tvUsername = findViewById(R.id.tv_username);
        rvMessages = findViewById(R.id.rv_messages);
        etMessage = findViewById(R.id.et_message);
        btnSend = findViewById(R.id.btn_send);
        btnAttachImage = findViewById(R.id.btn_attach_image);
        pbUploading = findViewById(R.id.pb_uploading);

        ivBack.setOnClickListener(v -> finish());
        tvUsername.setText(otherUsername != null ? otherUsername : "Chat");

        if (otherAvatarUrl != null && !otherAvatarUrl.isEmpty()) {
            com.insta.clone.util.GlideUtils.loadAvatar(ivAvatar, otherAvatarUrl);
        } else {
            ivAvatar.setImageResource(R.drawable.ic_person_placeholder);
        }

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvMessages.setLayoutManager(layoutManager);

        adapter = new MessageAdapter(messageList, myUserId, otherAvatarUrl);
        rvMessages.setAdapter(adapter);

        btnSend.setOnClickListener(v -> sendTextMessage());
        btnAttachImage.setOnClickListener(v -> pickImageLauncher.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()
        ));

        etMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                boolean hasText = s != null && s.toString().trim().length() > 0;
                btnSend.setAlpha(hasText ? 1.0f : 0.4f);
            }
        });
        btnSend.setAlpha(0.4f);

        loadInitialMessages();
    }

    @Override
    protected void onResume() {
        super.onResume();
        markAsRead();
        pollHandler.postDelayed(pollRunnable, 3000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        pollHandler.removeCallbacks(pollRunnable);
    }

    private void markAsRead() {
        if (conversationId != null) {
            chatRepository.markMessagesRead(conversationId);
        }
    }

    private void loadInitialMessages() {
        if (conversationId == null) return;
        chatRepository.getMessages(conversationId).observe(this, res -> {
            if (res != null && res.status == Resource.Status.SUCCESS && res.data != null) {
                messageList.clear();
                messageList.addAll(res.data);
                adapter.notifyDataSetChanged();
                if (!messageList.isEmpty()) {
                    rvMessages.scrollToPosition(messageList.size() - 1);
                }
            }
        });
    }

    private void fetchMessagesSilently() {
        if (conversationId == null) return;
        chatRepository.getMessages(conversationId).observe(this, res -> {
            if (res != null && res.status == Resource.Status.SUCCESS && res.data != null) {
                int oldSize = messageList.size();
                int newSize = res.data.size();
                if (newSize != oldSize) {
                    messageList.clear();
                    messageList.addAll(res.data);
                    adapter.notifyDataSetChanged();
                    rvMessages.smoothScrollToPosition(messageList.size() - 1);
                    markAsRead();
                }
            }
        });
    }

    private void sendTextMessage() {
        String text = etMessage.getText() != null ? etMessage.getText().toString().trim() : "";
        if (text.isEmpty() || conversationId == null) return;

        etMessage.setText("");
        btnSend.setEnabled(false);

        chatRepository.sendMessage(conversationId, text, null).observe(this, res -> {
            btnSend.setEnabled(true);
            if (res != null && res.status == Resource.Status.SUCCESS && res.data != null) {
                messageList.add(res.data);
                adapter.notifyItemInserted(messageList.size() - 1);
                rvMessages.scrollToPosition(messageList.size() - 1);
            } else if (res != null && res.status == Resource.Status.ERROR) {
                Toast.makeText(this, "Failed to send: " + res.message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void handleImagePicked(Uri uri) {
        if (uri == null || conversationId == null) return;
        SessionManager sessionManager = SessionManager.getInstance(this);
        String currentUid = sessionManager.getUserId();
        if (currentUid != null && !currentUid.isEmpty()) {
            myUserId = currentUid;
        }
        if (myUserId == null || myUserId.isEmpty()) {
            Toast.makeText(this, "Not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }
        pbUploading.setVisibility(View.VISIBLE);

        // Optimistic sender preview immediately while uploading
        Message optimisticMsg = new Message();
        optimisticMsg.setId("temp_" + System.currentTimeMillis());
        optimisticMsg.setConversationId(conversationId);
        optimisticMsg.setSenderId(myUserId);
        optimisticMsg.setText("");
        optimisticMsg.setImageUrl(uri.toString());
        optimisticMsg.setMediaUrl(uri.toString());
        optimisticMsg.setCreatedAt(System.currentTimeMillis());
        messageList.add(optimisticMsg);
        adapter.notifyItemInserted(messageList.size() - 1);
        rvMessages.scrollToPosition(messageList.size() - 1);

        new Thread(() -> {
            byte[] bytes = null;
            try {
                bytes = ImageUtils.processUriToJpegBytes(this, uri);
            } catch (Exception e) {
                android.util.Log.e("InstaApi", "Error processing chat image: " + e.getMessage());
            }
            final byte[] finalBytes = bytes;
            runOnUiThread(() -> {
                if (finalBytes == null || finalBytes.length == 0) {
                    pbUploading.setVisibility(View.GONE);
                    messageList.remove(optimisticMsg);
                    adapter.notifyDataSetChanged();
                    Toast.makeText(this, "Failed to process image", Toast.LENGTH_SHORT).show();
                    return;
                }

                storageRepository.uploadFile("chat", finalBytes, "jpg", "image/jpeg").observe(this, res -> {
                    if (res != null && res.status == Resource.Status.SUCCESS && res.data != null) {
                        String imageUrl = res.data;
                        chatRepository.sendMessage(conversationId, "", imageUrl).observe(this, msgRes -> {
                            pbUploading.setVisibility(View.GONE);
                            if (msgRes != null && msgRes.status == Resource.Status.SUCCESS && msgRes.data != null) {
                                int idx = messageList.indexOf(optimisticMsg);
                                if (idx >= 0) {
                                    messageList.set(idx, msgRes.data);
                                    adapter.notifyItemChanged(idx);
                                } else {
                                    messageList.add(msgRes.data);
                                    adapter.notifyItemInserted(messageList.size() - 1);
                                }
                                rvMessages.scrollToPosition(messageList.size() - 1);
                            } else if (msgRes != null && msgRes.status == Resource.Status.ERROR) {
                                messageList.remove(optimisticMsg);
                                adapter.notifyDataSetChanged();
                                Toast.makeText(this, "Failed to send image message", Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else if (res != null && res.status == Resource.Status.ERROR) {
                        pbUploading.setVisibility(View.GONE);
                        messageList.remove(optimisticMsg);
                        adapter.notifyDataSetChanged();
                        Toast.makeText(this, "Upload failed: " + res.message, Toast.LENGTH_SHORT).show();
                    }
                });
            });
        }).start();
    }

    // --- Message Adapter ---

    private static class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_SENT = 1;
        private static final int TYPE_RECEIVED = 2;

        private final List<Message> items;
        private final String currentUserId;
        private final String otherAvatarUrl;

        MessageAdapter(List<Message> items, String currentUserId, String otherAvatarUrl) {
            this.items = items;
            this.currentUserId = currentUserId;
            this.otherAvatarUrl = otherAvatarUrl;
        }

        @Override
        public int getItemViewType(int position) {
            Message m = items.get(position);
            return (m.getSenderId() != null && m.getSenderId().equals(currentUserId)) ? TYPE_SENT : TYPE_RECEIVED;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_SENT) {
                View v = inflater.inflate(R.layout.item_message_sent, parent, false);
                return new SentHolder(v);
            } else {
                View v = inflater.inflate(R.layout.item_message_received, parent, false);
                return new ReceivedHolder(v);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Message m = items.get(position);
            String text = m.getText();
            String media = m.getImageUrl();

            if (holder instanceof SentHolder) {
                SentHolder sh = (SentHolder) holder;
                bindBubble(sh.layoutBubble, sh.tvMessage, sh.ivMessageImage, text, media);
            } else if (holder instanceof ReceivedHolder) {
                ReceivedHolder rh = (ReceivedHolder) holder;
                bindBubble(rh.layoutBubble, rh.tvMessage, rh.ivMessageImage, text, media);

                if (otherAvatarUrl != null && !otherAvatarUrl.isEmpty()) {
                    com.insta.clone.util.GlideUtils.loadAvatar(rh.ivAvatar, otherAvatarUrl);
                } else {
                    rh.ivAvatar.setImageResource(R.drawable.ic_person_placeholder);
                }
            }
        }

        private void bindBubble(View layoutBubble, TextView tvMessage, ImageView ivImage, String text, String media) {
            boolean hasMedia = media != null && !media.isEmpty();
            boolean hasText = text != null && !text.isEmpty() && !text.equalsIgnoreCase("Photo") && !text.equalsIgnoreCase("Sent a photo");

            if (hasMedia) {
                ivImage.setVisibility(View.VISIBLE);
                if (ivImage instanceof com.google.android.material.imageview.ShapeableImageView) {
                    com.google.android.material.imageview.ShapeableImageView siv = (com.google.android.material.imageview.ShapeableImageView) ivImage;
                    float radiusPx = 18f * ivImage.getContext().getResources().getDisplayMetrics().density;
                    siv.setShapeAppearanceModel(
                            siv.getShapeAppearanceModel().toBuilder()
                                    .setAllCornerSizes(radiusPx)
                                    .build()
                    );
                }
                com.insta.clone.util.GlideUtils.load(ivImage, "chat", media);

                ivImage.setOnClickListener(v -> {
                    android.content.Intent viewerIntent = new android.content.Intent(v.getContext(), ImageViewerActivity.class);
                    viewerIntent.putExtra(ImageViewerActivity.EXTRA_IMAGE_URL, media);
                    viewerIntent.putExtra(ImageViewerActivity.EXTRA_BUCKET, "chat");
                    v.getContext().startActivity(viewerIntent);
                });
            } else {
                ivImage.setVisibility(View.GONE);
                ivImage.setOnClickListener(null);
            }

            if (hasText) {
                layoutBubble.setVisibility(View.VISIBLE);
                tvMessage.setText(text);
            } else if (!hasMedia) {
                layoutBubble.setVisibility(View.VISIBLE);
                tvMessage.setText(text != null ? text : "");
            } else {
                layoutBubble.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class SentHolder extends RecyclerView.ViewHolder {
            final View layoutBubble;
            final TextView tvMessage;
            final ImageView ivMessageImage;
            final TextView tvTime;

            SentHolder(@NonNull View itemView) {
                super(itemView);
                layoutBubble = itemView.findViewById(R.id.layout_bubble);
                tvMessage = itemView.findViewById(R.id.tv_message);
                ivMessageImage = itemView.findViewById(R.id.iv_message_image);
                tvTime = itemView.findViewById(R.id.tv_time);
            }
        }

        static class ReceivedHolder extends RecyclerView.ViewHolder {
            final View layoutBubble;
            final TextView tvMessage;
            final ImageView ivMessageImage;
            final ImageView ivAvatar;
            final TextView tvTime;

            ReceivedHolder(@NonNull View itemView) {
                super(itemView);
                layoutBubble = itemView.findViewById(R.id.layout_bubble);
                tvMessage = itemView.findViewById(R.id.tv_message);
                ivMessageImage = itemView.findViewById(R.id.iv_message_image);
                ivAvatar = itemView.findViewById(R.id.iv_avatar);
                tvTime = itemView.findViewById(R.id.tv_time);
            }
        }
    }
}
