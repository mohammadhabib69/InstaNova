package com.insta.nova.calling;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.remote.RetrofitClient;
import com.insta.nova.data.remote.api.RestApi;
import com.insta.nova.data.remote.dto.CallSessionDto;
import com.insta.nova.databinding.ActivityCallHistoryBinding;
import com.insta.nova.ui.chat.AudioCallActivity;
import com.insta.nova.ui.chat.ChatActivity;
import com.insta.nova.ui.chat.VideoCallActivity;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Call History Hub displaying persistent audio and video call logs,
 * call directions, durations, and 1-tap call-again actions.
 */
public class CallHistoryActivity extends AppCompatActivity implements CallHistoryAdapter.OnCallActionListener {

    private ActivityCallHistoryBinding binding;
    private final List<CallHistoryStore.CallRecord> callList = new ArrayList<>();
    private CallHistoryAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCallHistoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.callHistoryRoot, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnNewCall.setOnClickListener(v -> finish()); // returns to inbox

        adapter = new CallHistoryAdapter(this, callList, this);
        binding.rvCallHistory.setLayoutManager(new LinearLayoutManager(this));
        binding.rvCallHistory.setAdapter(adapter);

        binding.swipeRefresh.setOnRefreshListener(this::loadCalls);

        loadCalls();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCalls();
    }

    private void loadCalls() {
        callList.clear();
        List<CallHistoryStore.CallRecord> localCalls = CallHistoryStore.getInstance(this).getCalls();
        callList.addAll(localCalls);
        updateUi();

        // Also fetch from remote Supabase if available
        fetchRemoteCalls();
    }

    private void fetchRemoteCalls() {
        String myUserId = SessionManager.getInstance(this).getUserId();
        if (myUserId == null) {
            binding.swipeRefresh.setRefreshing(false);
            return;
        }

        RestApi restApi = RetrofitClient.getInstance(this).getRestApi();
        Map<String, String> query = new HashMap<>();
        query.put("select", "*,caller:profiles!call_sessions_caller_id_fkey(id,username,avatar_url),recipient:profiles!call_sessions_recipient_id_fkey(id,username,avatar_url)");
        query.put("or", "(caller_id.eq." + myUserId + ",recipient_id.eq." + myUserId + ")");
        query.put("order", "created_at.desc");
        query.put("limit", "50");

        restApi.getCallSessions(query).enqueue(new Callback<List<CallSessionDto>>() {
            @Override
            public void onResponse(@NonNull Call<List<CallSessionDto>> call, @NonNull Response<List<CallSessionDto>> response) {
                binding.swipeRefresh.setRefreshing(false);
                if (response.isSuccessful() && response.body() != null) {
                    for (CallSessionDto dto : response.body()) {
                        CallSession session = new CallSession();
                        session.callId = dto.id;
                        session.conversationId = dto.conversationId;
                        session.callerId = dto.callerId;
                        session.recipientId = dto.recipientId;
                        session.callType = dto.callType;
                        session.status = dto.status;
                        session.durationSeconds = dto.durationSeconds;
                        session.isOutgoing = myUserId.equals(dto.callerId);

                        if (session.isOutgoing && dto.recipient != null) {
                            session.recipientUsername = dto.recipient.getUsername();
                            session.recipientAvatar = dto.recipient.getAvatarUrl();
                        } else if (!session.isOutgoing && dto.caller != null) {
                            session.callerUsername = dto.caller.getUsername();
                            session.callerAvatar = dto.caller.getAvatarUrl();
                        }

                        CallHistoryStore.getInstance(CallHistoryActivity.this).addCall(session);
                    }
                    // Refresh from store
                    callList.clear();
                    callList.addAll(CallHistoryStore.getInstance(CallHistoryActivity.this).getCalls());
                    updateUi();
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<CallSessionDto>> call, @NonNull Throwable t) {
                binding.swipeRefresh.setRefreshing(false);
            }
        });
    }

    private void updateUi() {
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        boolean isEmpty = callList.isEmpty();
        binding.layoutEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        binding.rvCallHistory.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onCallAgain(@NonNull CallHistoryStore.CallRecord record) {
        boolean isVideo = CallSession.TYPE_VIDEO.equalsIgnoreCase(record.callType);
        Intent intent;
        if (isVideo) {
            intent = new Intent(this, VideoCallActivity.class);
            intent.putExtra(VideoCallActivity.EXTRA_CONVERSATION_ID, record.conversationId);
            intent.putExtra(VideoCallActivity.EXTRA_OTHER_USER_ID, record.otherUserId);
            intent.putExtra(VideoCallActivity.EXTRA_OTHER_USERNAME, record.otherUsername);
            intent.putExtra(VideoCallActivity.EXTRA_OTHER_AVATAR, record.otherAvatar);
        } else {
            intent = new Intent(this, AudioCallActivity.class);
            intent.putExtra(AudioCallActivity.EXTRA_CONVERSATION_ID, record.conversationId);
            intent.putExtra(AudioCallActivity.EXTRA_OTHER_USER_ID, record.otherUserId);
            intent.putExtra(AudioCallActivity.EXTRA_OTHER_USERNAME, record.otherUsername);
            intent.putExtra(AudioCallActivity.EXTRA_OTHER_AVATAR, record.otherAvatar);
        }
        startActivity(intent);
    }

    @Override
    public void onOpenChat(@NonNull CallHistoryStore.CallRecord record) {
        if (record.conversationId != null) {
            Intent intent = new Intent(this, ChatActivity.class);
            intent.putExtra(ChatActivity.EXTRA_CONVERSATION_ID, record.conversationId);
            intent.putExtra(ChatActivity.EXTRA_OTHER_USER_ID, record.otherUserId);
            intent.putExtra(ChatActivity.EXTRA_OTHER_USERNAME, record.otherUsername);
            intent.putExtra(ChatActivity.EXTRA_OTHER_AVATAR, record.otherAvatar);
            startActivity(intent);
        }
    }
}
