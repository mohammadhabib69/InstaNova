package com.insta.nova.ui.chat;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.insta.nova.R;
import com.insta.nova.data.local.ChatSettingsStore;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.model.Message;
import com.insta.nova.data.model.MessageReaction;
import com.insta.nova.databinding.ActivityChatBinding;
import com.insta.nova.databinding.PopupChatMessageBinding;
import com.insta.nova.databinding.SheetChatCameraBinding;
import com.insta.nova.databinding.SheetChatMoreActionsBinding;
import com.insta.nova.util.Constants;
import com.insta.nova.util.GlideUtils;
import com.insta.nova.util.ToastUtil;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * PAGE 17 — 1-on-1 Instagram-identical chat.
 */
public class ChatActivity extends AppCompatActivity implements ChatAdapter.Listener {

    public static final String EXTRA_CONVERSATION_ID = "EXTRA_CONVERSATION_ID";
    public static final String EXTRA_OTHER_USER_ID = "EXTRA_OTHER_USER_ID";
    public static final String EXTRA_OTHER_USERNAME = "EXTRA_OTHER_USERNAME";
    public static final String EXTRA_OTHER_AVATAR = "EXTRA_OTHER_AVATAR";

    private static final long POLL_MS = 3000L;

    private static final List<String> EMOJI_LIST = Arrays.asList(
            "❤️", "😂", "🔥", "😍", "✨", "👏", "🙌", "😊",
            "👍", "🥺", "😭", "😮", "🎉", "😎", "💯", "🙏",
            "😘", "🥰", "🤔", "👀", "💀", "🤩", "🥳", "😇",
            "🤤", "😈", "😴", "🤯", "🥶", "😡", "💔", "💕",
            "💖", "🍕", "🍔", "☕", "🍻", "🎵", "📸", "🌟",
            "⚡", "🌈", "☀️", "🌙", "🤝", "✌️", "💪", "💡"
    );

    private ActivityChatBinding binding;
    private ChatViewModel viewModel;
    private ChatAdapter adapter;
    private LinearLayoutManager layoutManager;

    private String conversationId;
    private String otherUserId;
    private String otherUsername;
    private String otherAvatar;

    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            viewModel.refresh();
            pollHandler.postDelayed(this, POLL_MS);
        }
    };

    // Live presence / typing come from Firebase Realtime Database: `setTyping()` and `goOnline()`
    // were already implemented in RealtimeChatRepository but no screen ever called them, which is
    // why the header only ever showed a stale "Active 15h ago" from a one-shot query.
    private com.insta.nova.data.repository.RealtimeChatRepository realtime;
    private com.google.firebase.database.ValueEventListener typingListener;
    private final Handler typingHandler = new Handler(Looper.getMainLooper());
    private Runnable typingStopRunnable;
    private boolean typingFlagSent;
    private com.google.firebase.database.ValueEventListener presenceListener;
    /** Non-null whenever the live channel has something more current than the polled status. */
    private String liveStatus;
    private boolean otherOnline;
    private boolean otherTypingActive;
    private String otherFirebaseUid;

    private String pendingPhotoPath;
    private Uri pendingPhotoUri;
    private PopupWindow activePopup;

    private String anchorRowId;
    private int anchorOffset;

    // Quoted reply state
    private Message pendingReplyMessage = null;

    // Voice recording state
    private MediaRecorder mediaRecorder;
    private File voiceFile;
    private long voiceStartTime;
    private boolean isRecording = false;
    private final Handler voiceHandler = new Handler(Looper.getMainLooper());
    private final Runnable voiceTimerRunnable = new Runnable() {
        @Override
        public void run() {
            if (isRecording) {
                long elapsedSec = (System.currentTimeMillis() - voiceStartTime) / 1000;
                long mins = elapsedSec / 60;
                long secs = elapsedSec % 60;
                binding.tvRecordingTimer.setText(String.format(Locale.US, "%d:%02d", mins, secs));
                voiceHandler.postDelayed(this, 500L);
            }
        }
    };

    // Voice playback state
    private MediaPlayer mediaPlayer;
    private String playingVoiceMessageId = null;
    private ProgressBar activeVoiceProgressBar = null;
    private ImageView activeVoicePlayButton = null;
    private final Handler playbackHandler = new Handler(Looper.getMainLooper());
    private final Runnable playbackProgressRunnable = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null && mediaPlayer.isPlaying() && activeVoiceProgressBar != null) {
                int pos = mediaPlayer.getCurrentPosition();
                int total = mediaPlayer.getDuration();
                if (total > 0) {
                    activeVoiceProgressBar.setProgress((int) ((pos / (float) total) * 100));
                }
                playbackHandler.postDelayed(this, 100L);
            }
        }
    };

    // Activity Result Launchers
    // Multiple photos in one go: the system picker returns up to 10 and each is queued as its
    // own media message, so the thread shows them as one run.
    private final ActivityResultLauncher<PickVisualMediaRequest> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.PickMultipleVisualMedia(10),
                    uris -> {
                        if (uris == null || uris.isEmpty()) return;
                        // Picking four sent eight: the result list can carry the same Uri more
                        // than once, so send each distinct photo exactly once.
                        java.util.LinkedHashSet<Uri> unique = new java.util.LinkedHashSet<>(uris);
                        for (Uri uri : unique) viewModel.sendImage(uri);
                    });

    private final ActivityResultLauncher<Uri> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (success && pendingPhotoUri != null) {
                    viewModel.sendImage(pendingPhotoUri);
                } else if (pendingPhotoPath != null) {
                    //noinspection ResultOfMethodCallIgnored
                    new File(pendingPhotoPath).delete();
                }
            });

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) launchCamera();
                else ToastUtil.showPill(this, "Camera permission needed");
            });

    private final ActivityResultLauncher<String> audioPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startVoiceRecording();
                else ToastUtil.showPill(this, "Microphone permission needed");
            });

    private final ActivityResultLauncher<Intent> detailsLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Intent data = result.getData();
                    if (data.getBooleanExtra(ChatDetailsActivity.RESULT_USER_BLOCKED, false)) {
                        finish();
                        return;
                    }
                    if (data.getBooleanExtra(ChatDetailsActivity.RESULT_OPEN_SEARCH, false)) {
                        openSearchMode();
                    }
                    String themeId = data.getStringExtra(ChatDetailsActivity.RESULT_THEME_CHANGED);
                    if (themeId != null) {
                        applyTheme(ChatTheme.findById(themeId));
                    }
                }
            });

    private final ActivityResultLauncher<Intent> callLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                viewModel.refresh();
            });

    /**
     * Leaves the conversation without dumping the user out of the app.
     *
     * <p>When a notification opens this screen the activity is the root of its own task, so
     * {@code finish()} closed the whole app instead of returning to the inbox. Route back to Home
     * in that case; a normal inbox opener still just finishes.
     */
    private void goBack() {
        if (isTaskRoot()) {
            startActivity(new android.content.Intent(this, com.insta.nova.ui.main.MainActivity.class)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP));
        }
        finish();
    }

    @Override


    protected void onCreate(Bundle savedInstanceState) {
        // This screen used to pin itself to night mode to keep a dark chat surface in one piece,
        // which meant a light-mode app still opened every DM black. The chat surface now follows
        // the app: the header, reply bar and sheets come from the theme, and the default
        // wallpaper has a light variant (see applyTheme). Explicitly chosen chat themes are
        // left as they are, since those are the user's own choice.
        super.onCreate(savedInstanceState);
        // System back must take the same safe route as the header button.
        getOnBackPressedDispatcher().addCallback(this,
                new androidx.activity.OnBackPressedCallback(true) {
                    @Override public void handleOnBackPressed() { goBack(); }
                });
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        conversationId = getIntent().getStringExtra(EXTRA_CONVERSATION_ID);
        otherUserId = getIntent().getStringExtra(EXTRA_OTHER_USER_ID);
        otherUsername = getIntent().getStringExtra(EXTRA_OTHER_USERNAME);
        otherAvatar = getIntent().getStringExtra(EXTRA_OTHER_AVATAR);

        // PART 1 item 3: The header must sit directly under the status bar like Instagram.
        // Apply status bar insets to top padding, and nav bar / ime insets to bottom padding.
        ViewCompat.setOnApplyWindowInsetsListener(binding.chatRoot, (v, insets) -> {
            Insets status = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            Insets nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(0, status.top, 0, nav.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(binding.chatRoot);

        String myId = SessionManager.getInstance(this).getUserId();
        adapter = new ChatAdapter(myId, otherAvatar, this,
                getResources().getDisplayMetrics().widthPixels,
                getResources().getDisplayMetrics().density);

        layoutManager = new LinearLayoutManager(this);
        binding.rvMessages.setLayoutManager(layoutManager);
        binding.rvMessages.setAdapter(adapter);
        binding.rvMessages.setItemAnimator(null);

        // Long press empty background opens Theme Picker (Part 6)
        binding.chatRoot.setOnLongClickListener(v -> {
            showThemePicker();
            return true;
        });
        binding.rvMessages.setOnLongClickListener(v -> {
            showThemePicker();
            return true;
        });

        viewModel = new ViewModelProvider(this).get(ChatViewModel.class);
        viewModel.init(conversationId, otherUserId, otherUsername, otherAvatar);
        realtime = new com.insta.nova.data.repository.RealtimeChatRepository();
        viewModel.state().observe(this, this::renderState);
        viewModel.toast().observe(this, message -> {
            if (message != null && !message.isEmpty()) ToastUtil.showPill(this, message);
        });

        // Setup Header navigation & actions
        binding.btnBack.setOnClickListener(v -> goBack());
        binding.layoutHeaderUser.setOnClickListener(v -> openChatDetails());
        binding.btnDetails.setOnClickListener(v -> openChatDetails());

        // PART 1 item 1: Phone icon starts Audio call
        binding.btnAudioCall.setOnClickListener(v -> startAudioCall());
        // Video call icon starts Video call
        binding.btnVideoCall.setOnClickListener(v -> startVideoCall());

        // Input bar wiring
        binding.btnSend.setOnClickListener(v -> sendTypedText());
        binding.btnCamera.setOnClickListener(v -> launchCameraWithPermission());
        binding.btnGallery.setOnClickListener(v -> openGallery());

        binding.btnEmojiPicker.setOnClickListener(v -> toggleEmojiPicker());
        setupVoiceRecording();

        // Reply bar cancel
        binding.btnCancelReply.setOnClickListener(v -> clearReplyPreview());

        // In-conversation search bar
        setupSearchBar();

        // Emoji Picker grid setup
        setupEmojiPicker();

        binding.etMessage.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                boolean hasText = s != null && s.toString().trim().length() > 0;
                updateInputMode(hasText);
                if (hasText) noteTyping();
            }
        });
        binding.etMessage.setOnEditorActionListener((v, actionId, event) -> {
            sendTypedText();
            return true;
        });
        binding.etMessage.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && binding.layoutEmojiPicker.getVisibility() == View.VISIBLE) {
                binding.layoutEmojiPicker.setVisibility(View.GONE);
                binding.btnEmojiPicker.setImageResource(R.drawable.ic_smiley);
            }
        });
        updateInputMode(false);

        // Load saved theme
        String themeId = ChatSettingsStore.getInstance(this).getConversationTheme(conversationId);
        applyTheme(ChatTheme.findById(themeId));

        // Auto scrolling and stacking
        binding.rvMessages.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                updateStacking((b - t) < (ob - ot)));

        binding.rvMessages.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy < 0 && layoutManager.findFirstVisibleItemPosition() == 0
                        && adapter.getItemCount() > 0) {
                    if (anchorRowId == null) {
                        anchorRowId = rowIdAt(0);
                        View first = layoutManager.findViewByPosition(0);
                        anchorOffset = first == null ? 0 : first.getTop();
                    }
                    viewModel.loadOlder();
                }
                if (isAtBottom()) anchorRowId = null;
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.setVisible(true);
        startLiveStatus();
        touchLastActive();
        pollHandler.removeCallbacks(pollRunnable);
        pollHandler.postDelayed(pollRunnable, POLL_MS);
        String themeId = ChatSettingsStore.getInstance(this).getConversationTheme(conversationId);
        applyTheme(ChatTheme.findById(themeId));
    }

    @Override
    protected void onPause() {
        super.onPause();
        viewModel.setVisible(false);
        stopLiveStatus();
        pollHandler.removeCallbacks(pollRunnable);
        dismissPopup();
        stopVoicePlayback();
    }

    /**
     * Subscribes to presence and typing, and announces this client as online.
     *
     * <p>Both writes are already permitted by {@code database.rules.json}: {@code presence/$uid}
     * is owner-write and readable by any signed-in user, {@code typing/$cid/$uid} requires chat
     * membership. Nothing here polls — the values arrive as they change.
     */
    private void startLiveStatus() {
        if (realtime == null || conversationId == null) return;
        SessionManager session = SessionManager.getInstance(this);
        // RTDB authorises on auth.uid (the Firebase uid); every screen speaks profile uuids.
        // profiles.firebase_uid is that bridge, so no rules deploy is needed.
        final String myId = session.getFirebaseUid();
        if (myId == null || myId.isEmpty()) return;

        realtime.goOnline(myId);
        realtime.addMember(conversationId, myId);

        if (otherUserId == null || otherUserId.isEmpty()) return;
        if (typingListener != null) return;

        new com.insta.nova.data.repository.ChatRepository(this)
                .fetchFirebaseUid(otherUserId, otherId -> {
                    if (otherId == null || otherId.isEmpty() || isFinishing()) return;
                    otherFirebaseUid = otherId;
                    // Both members must exist before either may read typing/reactions.
                    realtime.addMember(conversationId, otherId);

                    typingListener = new com.google.firebase.database.ValueEventListener() {
                        @Override public void onDataChange(
                                @NonNull com.google.firebase.database.DataSnapshot s) {
                            Boolean b = s.getValue(Boolean.class);
                            otherTypingActive = b != null && b;
                            applyStatus(otherTypingActive ? "typing…" : liveStatus);
                        }

                        @Override public void onCancelled(
                                @NonNull com.google.firebase.database.DatabaseError e) {
                            otherTypingActive = false;
                        }
                    };
                    realtime.listenTypingFor(conversationId, otherId, typingListener);

                    presenceListener = new com.google.firebase.database.ValueEventListener() {
                        @Override public void onDataChange(
                                @NonNull com.google.firebase.database.DataSnapshot s) {
                            String state = s.child("state").getValue(String.class);
                            liveStatus = com.insta.nova.data.repository.RealtimeChatRepository.STATE_ONLINE
                                    .equals(state) ? "Active now" : null;
                            applyStatus(otherTypingActive ? "typing…" : liveStatus);
                        }

                        @Override public void onCancelled(
                                @NonNull com.google.firebase.database.DatabaseError e) {}
                    };
                    realtime.listenPresence(otherId, presenceListener);
                });
    }

    /**
     * Records "this account is active now" so the other side's header can say "Active now".
     *
     * <p>{@code profiles.last_active_at} was read everywhere and written nowhere.
     */
    private void touchLastActive() {
        String myId = SessionManager.getInstance(this).getUserId();
        if (myId == null || myId.isEmpty()) return;
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("last_active_at", new java.text.SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", java.util.Locale.US)
                .format(new java.util.Date()));
        com.insta.nova.data.remote.RetrofitClient.getInstance(this).getRestApi()
                .touchLastActive("eq." + myId, body)
                .enqueue(new retrofit2.Callback<Void>() {
                    @Override public void onResponse(retrofit2.Call<Void> c,
                                                     retrofit2.Response<Void> r) {
                        android.util.Log.d("ChatPresence", "heartbeat HTTP " + r.code());
                    }

                    @Override public void onFailure(retrofit2.Call<Void> c, Throwable t) {
                        android.util.Log.w("ChatPresence", "heartbeat failed: " + t.getMessage());
                    }
                });
    }

    private void stopLiveStatus() {
        if (typingStopRunnable != null) typingHandler.removeCallbacks(typingStopRunnable);
        String myId = SessionManager.getInstance(this).getFirebaseUid();
        if (realtime != null && conversationId != null && myId != null) {
            realtime.setTyping(conversationId, myId, false);
            realtime.goOffline(myId);
        }
        typingFlagSent = false;
        otherTypingActive = false;
        liveStatus = null;
        if (otherFirebaseUid != null && conversationId != null && realtime != null) {
            if (typingListener != null) {
                realtime.stopListeningTypingFor(conversationId, otherFirebaseUid, typingListener);
                typingListener = null;
            }
            if (presenceListener != null) {
                realtime.stopListeningPresence(otherFirebaseUid, presenceListener);
                presenceListener = null;
            }
            otherFirebaseUid = null;
        }
    }

    private void applyStatus(@Nullable String status) {
        if (binding == null) return;
        // The dot means "recently active", so it follows the same value the subtitle shows.
        boolean online = "Active now".equals(status);
        binding.ivPresenceDot.setVisibility(online ? View.VISIBLE : View.GONE);
        if (status == null || status.isEmpty()) {
            binding.tvHeaderStatus.setVisibility(View.GONE);
            return;
        }
        binding.tvHeaderStatus.setVisibility(View.VISIBLE);
        binding.tvHeaderStatus.setText(status);
    }

    /** Debounced typing flag: written once per burst, cleared 3 s after the last keystroke. */
    private void noteTyping() {
        if (realtime == null || conversationId == null) return;
        // Same trap as presence: the rules compare auth.uid, so the profile uuid is refused.
        String myId = SessionManager.getInstance(this).getFirebaseUid();
        if (myId == null || myId.isEmpty()) return;
        if (!typingFlagSent) {
            typingFlagSent = true;
            realtime.setTyping(conversationId, myId, true);
        }
        if (typingStopRunnable != null) typingHandler.removeCallbacks(typingStopRunnable);
        typingStopRunnable = () -> {
            typingFlagSent = false;
            realtime.setTyping(conversationId, myId, false);
        };
        typingHandler.postDelayed(typingStopRunnable, 3000L);
    }

    /* ------------------------------------------------------------------ */
    /* Calls & Details Navigation                                          */
    /* ------------------------------------------------------------------ */

    private void openChatDetails() {
        Intent intent = new Intent(this, ChatDetailsActivity.class);
        intent.putExtra(ChatDetailsActivity.EXTRA_CONVERSATION_ID, conversationId);
        intent.putExtra(ChatDetailsActivity.EXTRA_OTHER_USER_ID, otherUserId);
        intent.putExtra(ChatDetailsActivity.EXTRA_OTHER_USERNAME, otherUsername);
        intent.putExtra(ChatDetailsActivity.EXTRA_OTHER_AVATAR, otherAvatar);
        detailsLauncher.launch(intent);
    }

    private void startAudioCall() {
        Intent intent = new Intent(this, AudioCallActivity.class);
        intent.putExtra(AudioCallActivity.EXTRA_CONVERSATION_ID, conversationId);
        intent.putExtra(AudioCallActivity.EXTRA_OTHER_USER_ID, otherUserId);
        intent.putExtra(AudioCallActivity.EXTRA_OTHER_USERNAME, otherUsername);
        intent.putExtra(AudioCallActivity.EXTRA_OTHER_AVATAR, otherAvatar);
        callLauncher.launch(intent);
    }

    private void startVideoCall() {
        Intent intent = new Intent(this, VideoCallActivity.class);
        intent.putExtra(VideoCallActivity.EXTRA_CONVERSATION_ID, conversationId);
        intent.putExtra(VideoCallActivity.EXTRA_OTHER_USER_ID, otherUserId);
        intent.putExtra(VideoCallActivity.EXTRA_OTHER_USERNAME, otherUsername);
        intent.putExtra(VideoCallActivity.EXTRA_OTHER_AVATAR, otherAvatar);
        callLauncher.launch(intent);
    }

    /* ------------------------------------------------------------------ */
    /* Theme Handling (Part 6)                                             */
    /* ------------------------------------------------------------------ */

    private void applyTheme(ChatTheme theme) {
        if (theme == null) return;
        // "Default" is not a colour choice, so it follows the app: black in dark mode, white in
        // light mode. Named themes (Berry, Ocean, …) stay exactly as picked.
        ChatTheme effective = theme;
        if ("default".equals(theme.id) && !isNightMode()) {
            effective = ChatTheme.lightDefault();
        }
        binding.chatRoot.setBackgroundColor(effective.backgroundColor);
        binding.rvMessages.setBackgroundColor(effective.backgroundColor);
        adapter.setChatTheme(effective);
    }

    private boolean isNightMode() {
        int mask = getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return mask == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private void showThemePicker() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.InstaNovaBottomSheetDialog);
        View sheet = getLayoutInflater().inflate(R.layout.sheet_chat_theme_picker, null);
        RecyclerView rvThemes = sheet.findViewById(R.id.rvThemes);
        rvThemes.setLayoutManager(new LinearLayoutManager(this));

        String currentId = ChatSettingsStore.getInstance(this).getConversationTheme(conversationId);
        ThemeAdapter themeAdapter = new ThemeAdapter(ChatTheme.getThemes(), currentId, theme -> {
            ChatSettingsStore.getInstance(this).setConversationTheme(conversationId, theme.id);
            applyTheme(theme);
            dialog.dismiss();
            ToastUtil.showPill(this, "Theme set to " + theme.name);
        });
        rvThemes.setAdapter(themeAdapter);

        dialog.setContentView(sheet);
        dialog.show();
    }

    /* ------------------------------------------------------------------ */
    /* Search in Conversation                                              */
    /* ------------------------------------------------------------------ */

    private void openSearchMode() {
        binding.layoutSearchBar.setVisibility(View.VISIBLE);
        binding.etSearchConversation.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(binding.etSearchConversation, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void setupSearchBar() {
        binding.btnCloseSearch.setOnClickListener(v -> {
            binding.layoutSearchBar.setVisibility(View.GONE);
            binding.etSearchConversation.setText("");
            adapter.setSearchQuery("");
            binding.tvSearchCount.setVisibility(View.GONE);
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(binding.etSearchConversation.getWindowToken(), 0);
            }
        });

        binding.etSearchConversation.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                String query = s != null ? s.toString().trim() : "";
                adapter.setSearchQuery(query);
                if (query.isEmpty()) {
                    binding.tvSearchCount.setVisibility(View.GONE);
                } else {
                    int matches = 0;
                    int firstMatchIndex = -1;
                    List<ChatAdapter.ChatRow> list = adapter.getCurrentList();
                    for (int i = 0; i < list.size(); i++) {
                        ChatAdapter.ChatRow row = list.get(i);
                        if (row.message != null && row.message.getText() != null) {
                            if (row.message.getText().toLowerCase(Locale.US).contains(query.toLowerCase(Locale.US))) {
                                matches++;
                                if (firstMatchIndex < 0) firstMatchIndex = i;
                            }
                        }
                    }
                    binding.tvSearchCount.setVisibility(View.VISIBLE);
                    binding.tvSearchCount.setText(matches + (matches == 1 ? " match" : " matches"));
                    if (firstMatchIndex >= 0) {
                        binding.rvMessages.smoothScrollToPosition(firstMatchIndex);
                    }
                }
            }
        });
    }

    /* ------------------------------------------------------------------ */
    /* Reply Preview Bar                                                   */
    /* ------------------------------------------------------------------ */

    private void showReplyPreview(Message message) {
        this.pendingReplyMessage = message;
        binding.layoutReplyBar.setVisibility(View.VISIBLE);
        String sender = (message.getSenderId() != null && message.getSenderId().equals(SessionManager.getInstance(this).getUserId()))
                ? "yourself" : (otherUsername != null ? otherUsername : "user");
        binding.tvReplyTargetUser.setText("Replying to @" + sender);
        String preview = message.getText();
        if (preview == null || preview.trim().isEmpty()) {
            preview = message.hasImage() ? "Photo" : "Attachment";
        }
        binding.tvReplyTargetText.setText(preview);

        binding.etMessage.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(binding.etMessage, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void clearReplyPreview() {
        this.pendingReplyMessage = null;
        binding.layoutReplyBar.setVisibility(View.GONE);
    }

    /* ------------------------------------------------------------------ */
    /* Emoji Picker (Part 4 item 1)                                        */
    /* ------------------------------------------------------------------ */

    private void setupEmojiPicker() {
        binding.rvEmojiPicker.setLayoutManager(new GridLayoutManager(this, 7));
        EmojiAdapter emojiAdapter = new EmojiAdapter(EMOJI_LIST, emoji -> {
            int start = Math.max(binding.etMessage.getSelectionStart(), 0);
            int end = Math.max(binding.etMessage.getSelectionEnd(), 0);
            binding.etMessage.getText().replace(Math.min(start, end), Math.max(start, end), emoji);
        });
        binding.rvEmojiPicker.setAdapter(emojiAdapter);
    }

    private void toggleEmojiPicker() {
        if (binding.layoutEmojiPicker.getVisibility() == View.VISIBLE) {
            binding.layoutEmojiPicker.setVisibility(View.GONE);
            binding.btnEmojiPicker.setImageResource(R.drawable.ic_smiley);
            binding.etMessage.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(binding.etMessage, InputMethodManager.SHOW_IMPLICIT);
        } else {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(binding.etMessage.getWindowToken(), 0);
            binding.layoutEmojiPicker.setVisibility(View.VISIBLE);
            binding.btnEmojiPicker.setImageResource(R.drawable.ic_keyboard);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Voice Recording (Part 4 item 3)                                     */
    /* ------------------------------------------------------------------ */

    private void setupVoiceRecording() {
        binding.btnMic.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            } else {
                startVoiceRecording();
            }
        });

        binding.btnCancelRecording.setOnClickListener(v -> cancelVoiceRecording());
        binding.btnStopAndSendRecording.setOnClickListener(v -> stopAndSendVoiceRecording());
    }

    private void startVoiceRecording() {
        try {
            File dir = new File(getCacheDir(), "voice_notes");
            if (!dir.exists()) dir.mkdirs();
            voiceFile = new File(dir, "voice_" + System.currentTimeMillis() + ".m4a");

            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setOutputFile(voiceFile.getAbsolutePath());
            mediaRecorder.prepare();
            mediaRecorder.start();

            isRecording = true;
            voiceStartTime = System.currentTimeMillis();

            binding.layoutInputBar.setVisibility(View.GONE);
            binding.layoutRecording.setVisibility(View.VISIBLE);
            binding.tvRecordingTimer.setText("0:00");

            AlphaAnimation blink = new AlphaAnimation(1.0f, 0.2f);
            blink.setDuration(600);
            blink.setRepeatMode(Animation.REVERSE);
            blink.setRepeatCount(Animation.INFINITE);
            binding.ivRecordingDot.startAnimation(blink);

            voiceHandler.post(voiceTimerRunnable);
        } catch (Exception e) {
            isRecording = false;
            binding.layoutRecording.setVisibility(View.GONE);
            binding.layoutInputBar.setVisibility(View.VISIBLE);
            ToastUtil.showPill(this, "Recording failed");
        }
    }

    private void cancelVoiceRecording() {
        if (!isRecording) return;
        try {
            if (mediaRecorder != null) {
                mediaRecorder.stop();
                mediaRecorder.release();
                mediaRecorder = null;
            }
        } catch (Exception ignored) {}
        if (voiceFile != null && voiceFile.exists()) {
            voiceFile.delete();
        }
        isRecording = false;
        voiceHandler.removeCallbacksAndMessages(null);
        binding.ivRecordingDot.clearAnimation();
        binding.layoutRecording.setVisibility(View.GONE);
        binding.layoutInputBar.setVisibility(View.VISIBLE);
        ToastUtil.showPill(this, "Voice note discarded");
    }

    private void stopAndSendVoiceRecording() {
        if (!isRecording) return;
        long durationMs = System.currentTimeMillis() - voiceStartTime;
        try {
            if (mediaRecorder != null) {
                mediaRecorder.stop();
                mediaRecorder.release();
                mediaRecorder = null;
            }
        } catch (Exception ignored) {}

        isRecording = false;
        voiceHandler.removeCallbacksAndMessages(null);
        binding.ivRecordingDot.clearAnimation();
        binding.layoutRecording.setVisibility(View.GONE);
        binding.layoutInputBar.setVisibility(View.VISIBLE);

        if (durationMs >= 500 && voiceFile != null && voiceFile.exists()) {
            viewModel.sendVoiceMessage(voiceFile.getAbsolutePath(), durationMs);
        } else {
            ToastUtil.showPill(this, "Voice message too short");
        }
    }

    /* ------------------------------------------------------------------ */
    /* Voice Playback (Part 4 item 3)                                      */
    /* ------------------------------------------------------------------ */

    @Override
    public void onVoicePlayClick(Message message, ProgressBar progress, ImageView playBtn) {
        if (message == null) return;
        String path = message.getVoicePath();
        if (path == null || path.isEmpty()) return;

        if (playingVoiceMessageId != null && playingVoiceMessageId.equals(message.getId())) {
            // Toggle pause
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                playBtn.setImageResource(R.drawable.ic_play);
                playbackHandler.removeCallbacks(playbackProgressRunnable);
                return;
            } else if (mediaPlayer != null) {
                mediaPlayer.start();
                playBtn.setImageResource(R.drawable.ic_pause);
                playbackHandler.post(playbackProgressRunnable);
                return;
            }
        }

        stopVoicePlayback();

        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(path);
            mediaPlayer.prepare();
            mediaPlayer.start();

            playingVoiceMessageId = message.getId();
            activeVoiceProgressBar = progress;
            activeVoicePlayButton = playBtn;

            playBtn.setImageResource(R.drawable.ic_pause);
            playbackHandler.post(playbackProgressRunnable);

            mediaPlayer.setOnCompletionListener(mp -> {
                progress.setProgress(0);
                playBtn.setImageResource(R.drawable.ic_play);
                stopVoicePlayback();
            });
        } catch (Exception e) {
            ToastUtil.showPill(this, "Playback error");
            stopVoicePlayback();
        }
    }

    private void stopVoicePlayback() {
        playbackHandler.removeCallbacks(playbackProgressRunnable);
        if (activeVoicePlayButton != null) {
            activeVoicePlayButton.setImageResource(R.drawable.ic_play);
        }
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }
        playingVoiceMessageId = null;
        activeVoiceProgressBar = null;
        activeVoicePlayButton = null;
    }

    /* ------------------------------------------------------------------ */
    /* More Actions Sheet ('+') (Part 4 item 5)                           */
    /* ------------------------------------------------------------------ */

    private void showMoreActionsSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.InstaNovaBottomSheetDialog);
        SheetChatMoreActionsBinding sheet = SheetChatMoreActionsBinding.inflate(getLayoutInflater());

        sheet.actionVoice.setOnClickListener(v -> {
            dialog.dismiss();
            startVoiceRecording();
        });
        sheet.actionPhotos.setOnClickListener(v -> {
            dialog.dismiss();
            openGallery();
        });
        sheet.actionHeart.setOnClickListener(v -> {
            dialog.dismiss();
            viewModel.sendText("❤️");
        });
        sheet.actionAudioCall.setOnClickListener(v -> {
            dialog.dismiss();
            startAudioCall();
        });
        sheet.actionVideoCall.setOnClickListener(v -> {
            dialog.dismiss();
            startVideoCall();
        });
        sheet.actionTheme.setOnClickListener(v -> {
            dialog.dismiss();
            showThemePicker();
        });

        dialog.setContentView(sheet.getRoot());
        dialog.show();
    }

    /* ------------------------------------------------------------------ */
    /* Rendering & Updates                                                 */
    /* ------------------------------------------------------------------ */

    private void renderState(ChatViewModel.ChatState state) {
        if (state == null) return;

        String name = state.intro != null ? state.intro.displayName() : otherUsername;
        binding.tvHeaderName.setText(name == null ? "" : name);
        // The polled status is a fallback: the Realtime Database value wins whenever it has
        // something to say, and "typing…" outranks both.
        applyStatus(otherTypingActive ? "typing…"
                : liveStatus != null ? liveStatus : state.statusText);

        String avatar = state.intro != null && state.intro.avatarUrl != null
                && !state.intro.avatarUrl.isEmpty() ? state.intro.avatarUrl : otherAvatar;
        if (avatar != null && !avatar.isEmpty()) {
            GlideUtils.loadAvatar(binding.ivHeaderAvatar, avatar);
        } else {
            binding.ivHeaderAvatar.setImageResource(R.drawable.ic_person_placeholder);
        }

        List<ChatAdapter.ChatRow> rows =
                ChatAdapter.buildRows(state, SessionManager.getInstance(this).getUserId());

        boolean atBottom = isAtBottom() || adapter.getItemCount() == 0;
        adapter.submitList(rows, () -> {
            if (anchorRowId != null) {
                int index = indexOfRow(rows, anchorRowId);
                if (index >= 0) {
                    layoutManager.scrollToPositionWithOffset(index, anchorOffset);
                    anchorRowId = null;
                    return;
                }
                anchorRowId = null;
            }
            updateStacking(false);
            if (atBottom && !rows.isEmpty()) {
                binding.rvMessages.scrollToPosition(rows.size() - 1);
            }
        });
    }

    private void updateStacking(boolean viewportShrunk) {
        if (adapter == null || layoutManager == null) return;
        int used = 0;
        for (int i = 0; i < binding.rvMessages.getChildCount(); i++) {
            used += binding.rvMessages.getChildAt(i).getHeight();
        }
        boolean overflows = used > binding.rvMessages.getHeight() - 2;
        if (layoutManager.getStackFromEnd() != overflows) {
            layoutManager.setStackFromEnd(overflows);
        }
        if (viewportShrunk && overflows) {
            binding.rvMessages.scrollToPosition(Math.max(0, adapter.getItemCount() - 1));
        }
    }

    private int indexOfRow(List<ChatAdapter.ChatRow> rows, String id) {
        for (int i = 0; i < rows.size(); i++) {
            if (id.equals(rows.get(i).id)) return i;
        }
        return -1;
    }

    private boolean isAtBottom() {
        if (adapter == null || adapter.getItemCount() == 0) return true;
        int lastVisible = layoutManager.findLastVisibleItemPosition();
        return lastVisible >= adapter.getItemCount() - 1;
    }

    private String rowIdAt(int position) {
        if (position < 0 || position >= adapter.getItemCount()) return null;
        return adapter.getCurrentList().get(position).id;
    }

    /* ------------------------------------------------------------------ */
    /* Input                                                               */
    /* ------------------------------------------------------------------ */

    private void updateInputMode(boolean hasText) {
        int icons = hasText ? View.GONE : View.VISIBLE;
        binding.btnMic.setVisibility(icons);
        binding.btnGallery.setVisibility(icons);
        binding.btnEmojiPicker.setVisibility(icons);
        // The "+" more-actions control is gone; its entries live elsewhere in the app.
        binding.btnSend.setVisibility(hasText ? View.VISIBLE : View.GONE);
    }

    private void sendTypedText() {
        String text = binding.etMessage.getText() != null
                ? binding.etMessage.getText().toString() : "";
        if (text.trim().isEmpty()) return;

        if (pendingReplyMessage != null) {
            String targetSender = (pendingReplyMessage.getSenderId() != null
                    && pendingReplyMessage.getSenderId().equals(SessionManager.getInstance(this).getUserId()))
                    ? "you" : (otherUsername != null ? otherUsername : "user");
            String targetText = pendingReplyMessage.getText();
            if (targetText == null || targetText.isEmpty()) {
                targetText = pendingReplyMessage.hasImage() ? "Photo" : "Attachment";
            }
            viewModel.sendReplyText(text, pendingReplyMessage.getId(), targetSender, targetText);
            clearReplyPreview();
        } else {
            viewModel.sendText(text);
        }
        binding.etMessage.setText("");
    }

    private void openGallery() {
        galleryLauncher.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    private void launchCameraWithPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
            return;
        }
        launchCamera();
    }

    private void launchCamera() {
        try {
            File dir = new File(getCacheDir(), "chat_photos");
            if (!dir.exists() && !dir.mkdirs()) {
                ToastUtil.showPill(this, "Camera unavailable");
                return;
            }
            File file = new File(dir, "chat_" + System.currentTimeMillis() + ".jpg");
            pendingPhotoPath = file.getAbsolutePath();
            pendingPhotoUri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", file);
            cameraLauncher.launch(pendingPhotoUri);
        } catch (Exception e) {
            ToastUtil.showPill(this, "Camera unavailable");
        }
    }

    /* ------------------------------------------------------------------ */
    /* Adapter Callbacks & Message Long Press (Part 5)                     */
    /* ------------------------------------------------------------------ */

    /**
     * "View profile" on the conversation intro card.
     *
     * <p>This used to open {@code ChatDetailsActivity} — the chat's own settings sheet —
     * which is not what the label promises: the tap is on the person's card, under their
     * picture and follower counts, so it has to land on that person's profile. The details
     * sheet stays reachable from the header and the ⓘ button.
     */
    @Override
    public void onViewProfile() {
        if (otherUserId == null || otherUserId.isEmpty()) {
            // Identity not resolved yet; the details sheet is the only safe destination.
            openChatDetails();
            return;
        }
        Intent intent = new Intent(this, UserProfileActivity.class);
        intent.putExtra(UserProfileActivity.EXTRA_USER_ID, otherUserId);
        intent.putExtra(UserProfileActivity.EXTRA_USERNAME, otherUsername);
        startActivity(intent);
    }

    @Override
    public void onMessageClick(Message message) {
        if (message != null && message.isFailed()) viewModel.retry(message);
    }

    @Override
    public void onMessageLongPress(Message message, View anchor) {
        if (message == null) return;
        dismissPopup();

        PopupChatMessageBinding popup = PopupChatMessageBinding.inflate(getLayoutInflater());
        popup.emojiHeart.setOnClickListener(v -> { viewModel.setReaction(message, "❤️"); dismissPopup(); });
        popup.emojiLaugh.setOnClickListener(v -> { viewModel.setReaction(message, "😂"); dismissPopup(); });
        popup.emojiSurprised.setOnClickListener(v -> { viewModel.setReaction(message, "😮"); dismissPopup(); });
        popup.emojiSad.setOnClickListener(v -> { viewModel.setReaction(message, "😢"); dismissPopup(); });
        popup.emojiAngry.setOnClickListener(v -> { viewModel.setReaction(message, "😡"); dismissPopup(); });
        popup.emojiThumbsUp.setOnClickListener(v -> { viewModel.setReaction(message, "👍"); dismissPopup(); });

        // Reply
        popup.rowReply.setOnClickListener(v -> {
            dismissPopup();
            showReplyPreview(message);
        });

        // Copy
        String text = message.getText();
        boolean copyable = text != null && !text.trim().isEmpty();
        popup.rowCopy.setVisibility(copyable ? View.VISIBLE : View.GONE);
        popup.rowCopy.setOnClickListener(v -> {
            copyToClipboard(text);
            dismissPopup();
        });

        // Unsend / Delete
        String myId = SessionManager.getInstance(this).getUserId();
        boolean mine = message.getSenderId() != null && message.getSenderId().equals(myId);
        popup.tvUnsendLabel.setText(mine ? "Unsend" : "Delete");
        popup.rowUnsend.setOnClickListener(v -> {
            dismissPopup();
            confirmUnsend(message);
        });

        PopupWindow window = new PopupWindow(popup.getRoot(),
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setOutsideTouchable(true);
        window.setElevation(8 * getResources().getDisplayMetrics().density);
        window.setOnDismissListener(() -> activePopup = null);
        activePopup = window;

        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        window.getContentView().measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int height = window.getContentView().getMeasuredHeight();
        int y = location[1] - height - 8;
        if (y < 0) y = location[1] + anchor.getHeight() + 8;
        window.showAtLocation(anchor, Gravity.NO_GRAVITY, Math.max(16, location[0]), y);
    }

    @Override
    public void onBackgroundLongPress() {
        showThemePicker();
    }

    @Override
    public void onMessageDoubleTap(Message message) {
        if (message == null) return;
        viewModel.toggleHeart(message);
    }

    @Override
    public void onRetry(Message message) {
        viewModel.retry(message);
    }

    @Override
    public void onCallEntryClick(boolean video) {
        if (video) startVideoCall(); else startAudioCall();
    }

    public void onReactionClick(Message message) {
        if (message == null) return;
        String mine = viewModel.myReaction(message);
        if (mine == null) return;
        new AlertDialog.Builder(this)
                .setTitle(message.getReactions().size() > 1 ? "Remove your reaction?" : "Remove reaction?")
                .setMessage("Tap to remove")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", (d, w) -> viewModel.removeMyReaction(message))
                .show();
    }

    @Override
    public void onImageClick(Message message) {
        if (message == null) return;
        // A share opens the thing that was shared, not a lone picture.
        String kind = message.getShareKind();
        String ref = message.getShareId();
        if (kind != null && ref != null && !ref.isEmpty()) {
            if ("story".equals(kind)) {
                Intent story = new Intent(this,
                        com.insta.nova.ui.story.StoryViewerActivity.class);
                story.putExtra("story_id", ref);
                story.putExtra("user_id", message.getSenderId());
                startActivity(story);
                return;
            }
            if ("post".equals(kind) || "reel".equals(kind)) {
                Intent post = new Intent(this, com.insta.nova.ui.post.PostDetailActivity.class);
                post.putExtra("post_id", ref);
                startActivity(post);
                return;
            }
        }
        String url = message.getLocalImageUri() != null ? message.getLocalImageUri() : message.getImageUrl();
        if (url == null || url.isEmpty()) return;
        Intent intent = new Intent(this, ImageViewerActivity.class);
        intent.putExtra(ImageViewerActivity.EXTRA_IMAGE_URL, url);
        intent.putExtra(ImageViewerActivity.EXTRA_BUCKET, "chat");
        startActivity(intent);
    }

    private void confirmUnsend(Message message) {
        new AlertDialog.Builder(this)
                .setTitle("Unsend message?")
                .setMessage("This will remove the message for everyone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Unsend", (d, w) -> viewModel.unsend(message))
                .show();
    }

    private void copyToClipboard(String text) {
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null && text != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("message", text));
            ToastUtil.showPill(this, "Copied");
        }
    }

    private void dismissPopup() {
        if (activePopup != null) {
            activePopup.dismiss();
            activePopup = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        pollHandler.removeCallbacksAndMessages(null);
        voiceHandler.removeCallbacksAndMessages(null);
        playbackHandler.removeCallbacksAndMessages(null);
        dismissPopup();
        stopVoicePlayback();
        binding = null;
    }
}
