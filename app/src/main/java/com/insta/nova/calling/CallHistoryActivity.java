package com.insta.nova.calling;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
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

    private static final String TAG = "CallHistory";

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
        // Local cache first, so the list is never blank while the network answers.
        refreshFromStore();
        fetchRemoteCalls();
        // Names are resolved from what is already on disk, independently of the remote
        // refresh: a row written by a call that ended while offline still has a peer id
        // and can still be named.
        resolvePeerNames(localPeerIds());
    }

    @NonNull
    private java.util.Set<String> localPeerIds() {
        java.util.Set<String> ids = new java.util.LinkedHashSet<>();
        for (CallHistoryStore.CallRecord record : CallHistoryStore.getInstance(this).getCalls()) {
            if (record.otherUserId != null && !record.otherUserId.isEmpty()) ids.add(record.otherUserId);
        }
        return ids;
    }

    /**
     * Reads the authoritative {@code public.calls} table.
     *
     * <p>This used to read the legacy {@code call_sessions} table and hand its statuses to
     * a mapper that only understood {@code accepted}/{@code rejected}/{@code cancelled}.
     * The calls the app actually places are written to {@code calls} with
     * {@code connected}/{@code ended}/{@code declined}/{@code missed}/{@code no_answer}, so
     * every one of them fell through the mapper's else-branch — which is why the whole
     * screen read "Failed audio call". The status mapping now lives in
     * {@link CallHistoryStore#displayStatusFor} and the source is the table the state
     * machine writes.
     */
    private void fetchRemoteCalls() {
        String myUserId = SessionManager.getInstance(this).getUserId();
        if (myUserId == null || myUserId.isEmpty()) {
            binding.swipeRefresh.setRefreshing(false);
            return;
        }

        RestApi restApi = RetrofitClient.getInstance(this).getRestApi();
        Map<String, String> query = new HashMap<>();
        query.put("or", "(caller_id.eq." + myUserId + ",callee_id.eq." + myUserId + ")");
        query.put("order", "created_at.desc");
        query.put("limit", "60");

        restApi.getCalls(query).enqueue(new Callback<List<CallRecord>>() {
            @Override
            public void onResponse(@NonNull Call<List<CallRecord>> call, @NonNull Response<List<CallRecord>> response) {
                binding.swipeRefresh.setRefreshing(false);
                if (!response.isSuccessful() || response.body() == null) {
                    Log.w(TAG, "call history query HTTP " + response.code());
                    return;
                }
                java.util.Set<String> peers = new java.util.LinkedHashSet<>();
                CallHistoryStore store = CallHistoryStore.getInstance(CallHistoryActivity.this);
                for (CallRecord row : response.body()) {
                    if (row == null || row.id == null) continue;
                    store.upsertCall(row, myUserId);
                    boolean outgoing = myUserId.equals(row.callerId);
                    String peer = outgoing ? row.calleeId : row.callerId;
                    if (peer != null) peers.add(peer);
                }
                refreshFromStore();
                resolvePeerNames(peers);
            }

            @Override
            public void onFailure(@NonNull Call<List<CallRecord>> call, @NonNull Throwable t) {
                binding.swipeRefresh.setRefreshing(false);
                Log.w(TAG, "call history query failed: " + t.getMessage());
            }
        });
    }

    /**
     * Fetches the display name and avatar for the peers that still have none.
     *
     * <p>One {@code id=eq.<uuid>} request per peer, not a single
     * {@code id=in.(...)} batch. The batch form is the obvious optimisation and it was
     * the first implementation, but it silently resolved nobody here while the identical
     * single-id query on the call screen resolved the same profile in the same second —
     * so this uses the form that is proven to work on this PostgREST deployment. Peers are
     * capped and de-duplicated, and an already-known name short-circuits the request, so
     * the cost is a handful of cheap reads on first open only.
     */
    private void resolvePeerNames(@NonNull java.util.Set<String> peerIds) {
        Log.i(TAG, "resolvePeerNames ids=" + peerIds.size());
        if (peerIds.isEmpty()) return;

        CallHistoryStore store = CallHistoryStore.getInstance(this);
        // A peer is "known" only when *every* one of its rows has a name. The first version
        // asked whether any row did, so the moment one old row carried a name the peer was
        // skipped for good - and every new call for that peer opened blank and stayed
        // "Unknown" in the list, which is exactly what the screen showed.
        java.util.Set<String> named = new java.util.HashSet<>();
        java.util.Set<String> unnamed = new java.util.HashSet<>();
        for (CallHistoryStore.CallRecord record : store.getCalls()) {
            if (record.otherUserId == null) continue;
            if (record.otherUsername != null && !record.otherUsername.isEmpty()) {
                named.add(record.otherUserId);
            } else {
                unnamed.add(record.otherUserId);
            }
        }

        List<String> pending = new ArrayList<>();
        for (String id : peerIds) {
            if (id != null && !id.isEmpty() && unnamed.contains(id) && pending.size() < 12) {
                pending.add(id);
            }
        }
        Log.i(TAG, "resolvePeerNames pending=" + pending.size() + " named=" + named.size()
                    + " unnamed=" + unnamed.size());
        if (pending.isEmpty()) return;

        fetchPeer(pending, 0);
    }

    /** Resolves {@code ids[index]}, then continues with the next one. */
    private void fetchPeer(@NonNull List<String> ids, int index) {
        if (index >= ids.size()) {
            refreshFromStore();
            return;
        }
        String peerId = ids.get(index);

        Map<String, String> q = new HashMap<>();
        q.put("id", "eq." + peerId);
        q.put("select", "id,username,avatar_url");
        q.put("limit", "1");

        RetrofitClient.getInstance(this).getRestApi().getProfiles(q)
                .enqueue(new Callback<List<com.insta.nova.data.remote.dto.ProfileDto>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<com.insta.nova.data.remote.dto.ProfileDto>> c,
                                           @NonNull Response<List<com.insta.nova.data.remote.dto.ProfileDto>> r) {
                        if (r.isSuccessful() && r.body() != null && !r.body().isEmpty()) {
                            com.insta.nova.data.remote.dto.ProfileDto p = r.body().get(0);
                            if (p != null) {
                                CallHistoryStore.getInstance(CallHistoryActivity.this)
                                        .updatePeer(peerId, p.getUsername(), p.getAvatarUrl());
                            }
                        } else {
                            Log.w(TAG, "peer lookup HTTP " + r.code() + " for " + peerId);
                        }
                        fetchPeer(ids, index + 1);
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<com.insta.nova.data.remote.dto.ProfileDto>> c,
                                          @NonNull Throwable t) {
                        Log.w(TAG, "peer lookup failed for " + peerId + ": " + t.getMessage());
                        fetchPeer(ids, index + 1);
                    }
                });
    }

    private void refreshFromStore() {
        callList.clear();
        callList.addAll(CallHistoryStore.getInstance(this).getCalls());
        updateUi();
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
