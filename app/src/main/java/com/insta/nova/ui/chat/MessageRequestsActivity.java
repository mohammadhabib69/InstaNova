package com.insta.nova.ui.chat;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.insta.nova.R;
import com.insta.nova.data.local.RelationshipStore;
import com.insta.nova.data.model.Conversation;
import com.insta.nova.data.repository.ChatRepository;
import com.insta.nova.util.GlideUtils;
import com.insta.nova.util.Resource;

import java.util.ArrayList;
import java.util.List;

/** Restricted accounts' threads live here, not in the main inbox. */
public class MessageRequestsActivity extends AppCompatActivity {

    private final List<Conversation> items = new ArrayList<>();
    private Adapter adapter;
    private TextView empty;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_people_list);
        ((TextView) findViewById(R.id.tvTitle)).setText(R.string.st_message_requests);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        empty = findViewById(R.id.tvEmpty);
        progress = findViewById(R.id.progressPeople);
        empty.setText(R.string.st_no_message_requests);

        RecyclerView rv = findViewById(R.id.rvPeople);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Adapter();
        rv.setAdapter(adapter);

        progress.setVisibility(View.VISIBLE);
        ChatRepository.getInstance(this).getConversations().observe(this, resource -> {
            progress.setVisibility(View.GONE);
            if (resource == null || resource.status != Resource.Status.SUCCESS
                    || resource.data == null) {
                updateEmpty();
                return;
            }
            RelationshipStore relationships = new RelationshipStore(this);
            items.clear();
            for (Conversation c : resource.data) {
                // The server marks a thread as a request for this viewer only, based on
                // whether the other person is an approved follower. The old check read a
                // purely local restrict list, which nobody populates by default, so this
                // screen could never fill — that was the bug in #55.
                if (c != null && c.isRequest()) items.add(c);
            }
            adapter.notifyDataSetChanged();
            updateEmpty();
        });
    }

    private void updateEmpty() {
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /**
     * Accept or decline a request, then drop the row locally.
     *
     * <p>Re-reads the list rather than removing optimistically: the inbox decides what
     * is a request on the server, and a local-only removal would look accepted while a
     * failed call left the thread pending.
     */
    private void decide(Conversation c, boolean accept) {
        androidx.lifecycle.LiveData<com.insta.nova.util.Resource<Boolean>> live =
                accept
                        ? com.insta.nova.data.repository.ChatRepository.getInstance(this)
                        .acceptMessageRequest(c.getId())
                        : com.insta.nova.data.repository.ChatRepository.getInstance(this)
                        .declineMessageRequest(c.getId());
        live.observe(this, resource -> {
            if (resource == null || resource.status != com.insta.nova.util.Resource.Status.SUCCESS) {
                android.widget.Toast.makeText(this, R.string.st_request_failed, android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            android.widget.Toast.makeText(this,
                    accept ? R.string.st_request_accepted : R.string.st_request_declined,
                    android.widget.Toast.LENGTH_SHORT).show();
            items.remove(c);
            adapter.notifyDataSetChanged();
            updateEmpty();
        });
    }

    private final class Adapter extends RecyclerView.Adapter<Adapter.Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_people_row, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Conversation c = items.get(position);
            holder.username.setText(c.getOtherUsername());
            holder.name.setText(c.getLastMessage() == null ? "" : c.getLastMessage());
            GlideUtils.loadAvatar(holder.avatar, c.getOtherAvatarUrl());
            holder.action.setVisibility(View.VISIBLE);
            holder.action.setText(R.string.st_accept_request);
            holder.action.setOnClickListener(v -> decide(c, true));
            // Decline is the destructive half, so it sits behind a long-press and a
            // confirmation instead of racing the Accept button for the same tap.
            holder.itemView.setOnLongClickListener(v -> {
                new androidx.appcompat.app.AlertDialog.Builder(MessageRequestsActivity.this)
                        .setTitle(R.string.st_decline_request)
                        .setMessage(R.string.st_decline_request_body)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.st_decline_request,
                                (d, w) -> decide(c, false))
                        .show();
                return true;
            });
            holder.itemView.findViewById(R.id.checkSelect).setVisibility(View.GONE);
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(MessageRequestsActivity.this, ChatActivity.class);
                intent.putExtra(ChatActivity.EXTRA_CONVERSATION_ID, c.getId());
                intent.putExtra(ChatActivity.EXTRA_OTHER_USER_ID, c.getOtherUserId());
                intent.putExtra(ChatActivity.EXTRA_OTHER_USERNAME, c.getOtherUsername());
                intent.putExtra(ChatActivity.EXTRA_OTHER_AVATAR, c.getOtherAvatarUrl());
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ImageView avatar;
            final TextView username;
            final TextView name;
            final TextView action;

            Holder(@NonNull View itemView) {
                super(itemView);
                avatar = itemView.findViewById(R.id.ivAvatar);
                username = itemView.findViewById(R.id.tvUsername);
                name = itemView.findViewById(R.id.tvName);
                action = itemView.findViewById(R.id.btnRowAction);
            }
        }
    }
}
