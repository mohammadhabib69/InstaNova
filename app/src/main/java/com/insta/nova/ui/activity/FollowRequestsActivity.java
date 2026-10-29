package com.insta.nova.ui.activity;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.insta.nova.R;
import com.insta.nova.data.model.Profile;
import com.insta.nova.data.repository.RelationshipRepository;
import com.insta.nova.util.GlideUtils;
import com.insta.nova.util.Resource;

import java.util.ArrayList;
import java.util.List;

/** Follow requests from private-account follows: approve or delete. */
public class FollowRequestsActivity extends AppCompatActivity {

    private final List<Profile> requests = new ArrayList<>();
    private Adapter adapter;
    private TextView empty;
    private ProgressBar progress;
    private RelationshipRepository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_people_list);
        repository = new RelationshipRepository(this);

        ((TextView) findViewById(R.id.tvTitle)).setText(R.string.st_follow_requests);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        empty = findViewById(R.id.tvEmpty);
        progress = findViewById(R.id.progressPeople);

        RecyclerView rv = findViewById(R.id.rvPeople);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Adapter();
        rv.setAdapter(adapter);
        load();
    }

    private void load() {
        progress.setVisibility(View.VISIBLE);
        repository.loadFollowRequests().observe(this, resource -> {
            progress.setVisibility(View.GONE);
            if (resource == null) return;
            if (resource.status == Resource.Status.ERROR) {
                empty.setText(R.string.st_network_error);
                empty.setVisibility(View.VISIBLE);
                return;
            }
            if (resource.status != Resource.Status.SUCCESS || resource.data == null) return;
            requests.clear();
            requests.addAll(resource.data);
            adapter.notifyDataSetChanged();
            empty.setText(R.string.st_no_results);
            empty.setVisibility(requests.isEmpty() ? View.VISIBLE : View.GONE);
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
            Profile profile = requests.get(position);
            holder.username.setText(profile.getUsername());
            holder.name.setText(profile.getFullName());
            GlideUtils.loadAvatar(holder.avatar, profile.getAvatarUrl());
            holder.action.setVisibility(View.VISIBLE);
            holder.action.setText(R.string.st_confirm);
            holder.action.setOnClickListener(v -> repository.approveRequest(profile.getId())
                    .observe(FollowRequestsActivity.this, r -> {
                        if (r != null && r.status == Resource.Status.SUCCESS) {
                            Toast.makeText(FollowRequestsActivity.this,
                                    R.string.st_request_approved, Toast.LENGTH_SHORT).show();
                            load();
                        }
                    }));
            holder.itemView.setOnClickListener(v -> {
                new androidx.appcompat.app.AlertDialog.Builder(FollowRequestsActivity.this)
                        .setTitle(profile.getUsername())
                        .setPositiveButton(R.string.st_confirm, (d, w) -> {
                            repository.approveRequest(profile.getId()).observe(FollowRequestsActivity.this, r -> {
                                if (r != null && r.status == Resource.Status.SUCCESS) {
                                    Toast.makeText(FollowRequestsActivity.this,
                                            R.string.st_request_approved, Toast.LENGTH_SHORT).show();
                                    load();
                                }
                            });
                        })
                        .setNegativeButton(R.string.st_delete_item, (d, w) -> {
                            repository.declineRequest(profile.getId()).observe(FollowRequestsActivity.this, r -> {
                                if (r != null && r.status == Resource.Status.SUCCESS) {
                                    Toast.makeText(FollowRequestsActivity.this,
                                            R.string.st_request_declined, Toast.LENGTH_SHORT).show();
                                    load();
                                }
                            });
                        })
                        .show();
            });
            holder.itemView.setOnLongClickListener(v -> {
                repository.declineRequest(profile.getId()).observe(FollowRequestsActivity.this, r -> {
                    if (r != null && r.status == Resource.Status.SUCCESS) {
                        Toast.makeText(FollowRequestsActivity.this,
                                R.string.st_request_declined, Toast.LENGTH_SHORT).show();
                        load();
                    }
                });
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return requests.size();
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
                itemView.findViewById(R.id.checkSelect).setVisibility(View.GONE);
            }
        }
    }
}
