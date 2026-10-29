package com.insta.nova.ui.create;

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
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.insta.nova.R;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.remote.RetrofitClient;
import com.insta.nova.data.remote.api.RestApi;
import com.insta.nova.data.remote.dto.ProfileDto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Choose the accounts to tag in a post.
 *
 * <p>Exists because Settings > Tags and mentions governed a feature that the app
 * never had: the composer built a "Tag people" row and then hid it as design-only,
 * and {@code post_tags} was read but never written, so the profile Tagged tab could
 * only ever be empty.
 *
 * <p>Returns the chosen ids via {@link #EXTRA_SELECTED_IDS}, plus their usernames so
 * the composer can show who is tagged without a second lookup.
 */
public class TagPeopleActivity extends AppCompatActivity {

    public static final String EXTRA_SELECTED_IDS = "selected_ids";
    public static final String EXTRA_SELECTED_NAMES = "selected_names";
    public static final String EXTRA_PRESELECTED = "preselected_ids";

    private final List<ProfileDto> all = new ArrayList<>();
    private final Map<String, String> selected = new LinkedHashMap<>();

    private Adapter adapter;
    private EditText search;
    private Button done;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tag_people);

        ArrayList<String> pre = getIntent().getStringArrayListExtra(EXTRA_PRESELECTED);
        if (pre != null) {
            for (String id : pre) if (id != null && !id.isEmpty()) selected.put(id, "");
        }

        findViewById(R.id.btnTagPeopleBack).setOnClickListener(v -> finish());

        search = findViewById(R.id.etTagPeopleSearch);
        done = findViewById(R.id.btnTagPeopleDone);
        done.setOnClickListener(v -> finishWithSelection());

        RecyclerView list = findViewById(R.id.rvTagPeople);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Adapter();
        list.setAdapter(adapter);

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                adapter.filter(s == null ? "" : s.toString());
            }
        });

        updateDone();
        loadPeople();
    }

    private void loadPeople() {
        String me = SessionManager.getInstance(this).getUserId();
        RestApi api = RetrofitClient.getInstance(this).getRestApi();

        Map<String, String> query = new HashMap<>();
        query.put("select", "id,username,full_name,avatar_url");
        query.put("order", "username.asc");
        query.put("limit", "100");
        if (me != null && !me.isEmpty()) query.put("id", "neq." + me);

        api.getProfiles(query).enqueue(new Callback<List<ProfileDto>>() {
            @Override
            public void onResponse(Call<List<ProfileDto>> call,
                                   Response<List<ProfileDto>> response) {
                all.clear();
                if (response.isSuccessful() && response.body() != null) {
                    for (ProfileDto dto : response.body()) {
                        if (dto != null && dto.getId() != null) all.add(dto);
                    }
                }
                // Keep names for anyone preselected so the composer can show a label
                // even if that account is not in this first page of results.
                for (ProfileDto dto : all) {
                    if (selected.containsKey(dto.getId())) {
                        selected.put(dto.getId(), dto.getUsername());
                    }
                }
                adapter.filter(search.getText() == null ? "" : search.getText().toString());
            }

            @Override
            public void onFailure(Call<List<ProfileDto>> call, Throwable t) {
                all.clear();
                adapter.filter("");
            }
        });
    }

    private void toggle(ProfileDto profile) {
        if (selected.containsKey(profile.getId())) {
            selected.remove(profile.getId());
        } else {
            selected.put(profile.getId(), profile.getUsername());
        }
        updateDone();
        adapter.notifyDataSetChanged();
    }

    private void updateDone() {
        done.setText(selected.isEmpty()
                ? getString(R.string.tag_people_done_empty)
                : getString(R.string.tag_people_done, selected.size()));
        done.setEnabled(!selected.isEmpty());
        done.setAlpha(selected.isEmpty() ? 0.5f : 1f);
    }

    private void finishWithSelection() {
        Intent out = new Intent();
        out.putStringArrayListExtra(EXTRA_SELECTED_IDS, new ArrayList<>(selected.keySet()));
        out.putStringArrayListExtra(EXTRA_SELECTED_NAMES,
                new ArrayList<>(selected.values()));
        setResult(RESULT_OK, out);
        finish();
    }

    /* ------------------------------------------------------------------ */

    private class Adapter extends RecyclerView.Adapter<Adapter.Holder> {

        private final List<ProfileDto> shown = new ArrayList<>();

        void filter(String query) {
            shown.clear();
            String q = query.trim().toLowerCase(Locale.US);
            for (ProfileDto p : all) {
                String username = p.getUsername() == null ? "" : p.getUsername();
                String full = p.getFullName() == null ? "" : p.getFullName();
                if (q.isEmpty()
                        || username.toLowerCase(Locale.US).contains(q)
                        || full.toLowerCase(Locale.US).contains(q)) {
                    shown.add(p);
                }
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_people_row, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            ProfileDto p = shown.get(position);
            holder.name.setText(p.getFullName() == null || p.getFullName().isEmpty()
                    ? p.getUsername() : p.getFullName());
            holder.username.setText("@" + p.getUsername());
            Glide.with(holder.avatar.getContext())
                    .load(p.getAvatarUrl())
                    .placeholder(R.drawable.bg_vibe_avatar_placeholder)
                    .into(holder.avatar);
            // item_people_row is shared with the Blocked/Restricted lists, where the
            // right-hand slot is an action button. Nothing to act on here, so hide it
            // and use the checkbox as a plain selection indicator.
            holder.action.setVisibility(View.GONE);
            holder.check.setVisibility(View.VISIBLE);
            holder.check.setChecked(selected.containsKey(p.getId()));
            holder.itemView.setOnClickListener(v -> toggle(p));
        }

        @Override
        public int getItemCount() {
            return shown.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final ImageView avatar = itemView.findViewById(R.id.ivAvatar);
            final TextView name = itemView.findViewById(R.id.tvName);
            final TextView username = itemView.findViewById(R.id.tvUsername);
            final android.widget.CheckBox check = itemView.findViewById(R.id.checkSelect);
            final View action = itemView.findViewById(R.id.btnRowAction);

            Holder(@NonNull View itemView) {
                super(itemView);
            }
        }
    }
}
