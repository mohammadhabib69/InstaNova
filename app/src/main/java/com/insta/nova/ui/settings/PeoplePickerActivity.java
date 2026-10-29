package com.insta.nova.ui.settings;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.insta.nova.R;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.model.Profile;
import com.insta.nova.data.repository.SocialRepository;
import com.insta.nova.util.GlideUtils;
import com.insta.nova.util.Resource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Multi-select people picker used by Close Friends and Story audience. */
public class PeoplePickerActivity extends AppCompatActivity {

    public static final String EXTRA_SOURCE = "extra_source";
    public static final String EXTRA_PRESELECTED = "extra_preselected";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String RESULT_IDS = "result_ids";

    public static final String SOURCE_FOLLOWERS = "followers";
    public static final String SOURCE_FOLLOWING = "following";
    /** Followers + following together: the pool for Restricted accounts. */
    public static final String SOURCE_EITHER = "followers_and_following";

    private final List<Profile> all = new ArrayList<>();
    private final List<Profile> visible = new ArrayList<>();
    private final Set<String> selected = new HashSet<>();
    private Adapter adapter;
    private TextView tvTitle;
    private TextView tvCount;
    private TextView tvEmpty;
    private ProgressBar progress;
    private EditText search;

    public static Intent intent(Context context, String source, String title, List<String> preselected) {
        Intent intent = new Intent(context, PeoplePickerActivity.class);
        intent.putExtra(EXTRA_SOURCE, source);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putStringArrayListExtra(EXTRA_PRESELECTED,
                preselected == null ? new ArrayList<>() : new ArrayList<>(preselected));
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_people_list);

        tvTitle = findViewById(R.id.tvTitle);
        tvCount = findViewById(R.id.btnAction);
        tvEmpty = findViewById(R.id.tvEmpty);
        progress = findViewById(R.id.progressPeople);
        search = new EditText(this);

        tvTitle.setText(getIntent().getStringExtra(EXTRA_TITLE));
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        ArrayList<String> preselected = getIntent().getStringArrayListExtra(EXTRA_PRESELECTED);
        if (preselected != null) selected.addAll(preselected);

        tvCount.setVisibility(View.VISIBLE);
        tvCount.setText(R.string.st_add);
        tvCount.setOnClickListener(v -> {
            Intent result = new Intent();
            result.putStringArrayListExtra(RESULT_IDS, new ArrayList<>(selected));
            setResult(RESULT_OK, result);
            finish();
        });

        // Search box sits under the title bar.
        search.setHint(R.string.st_search);
        search.setSingleLine(true);
        search.setTextSize(15);
        search.setBackgroundResource(R.drawable.bg_search_field);
        search.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                resolveThemeColor()));
        int pad = dp(12);
        search.setPadding(pad, 0, pad, 0);
        search.setTextColor(getColor(R.color.color_text_primary));
        search.setHintTextColor(getColor(R.color.color_text_secondary));
        ViewGroup root = (ViewGroup) tvTitle.getParent().getParent();
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        lp.setMargins(dp(16), 0, dp(16), dp(12));
        search.setLayoutParams(lp);
        root.addView(search, 2);

        RecyclerView rv = findViewById(R.id.rvPeople);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Adapter();
        rv.setAdapter(adapter);
        findViewById(R.id.progressPeople).setVisibility(View.VISIBLE);

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                filter(s == null ? "" : s.toString());
            }
        });

        load();
    }

    private int resolveThemeColor() {
        android.util.TypedValue value = new android.util.TypedValue();
        if (getTheme().resolveAttribute(R.attr.themeSurfaceVariant, value, true)) return value.data;
        return getColor(R.color.color_surface_variant);
    }

    private void load() {
        String source = getIntent().getStringExtra(EXTRA_SOURCE);
        SocialRepository repo = new SocialRepository(this);
        String myId = SessionManager.getInstance(this).getUserId();
        androidx.lifecycle.LiveData<Resource<List<Profile>>> live;
        if (SOURCE_EITHER.equals(source)) {
            // Restricting is about people I interact with, which can be either direction:
            // merge both lists instead of forcing the user to guess which one holds them.
            androidx.lifecycle.MediatorLiveData<Resource<List<Profile>>> merged =
                    new androidx.lifecycle.MediatorLiveData<>();
            final List<Profile> combined = new ArrayList<>();
            final Set<String> seen = new HashSet<>();
            androidx.lifecycle.LiveData<Resource<List<Profile>>> followers = repo.getFollowers(myId);
            androidx.lifecycle.LiveData<Resource<List<Profile>>> following = repo.getFollowing(myId);
            merged.addSource(followers, resource -> {
                if (resource != null && resource.data != null) collect(resource.data, combined, seen);
                merged.setValue(Resource.success(new ArrayList<>(combined)));
            });
            merged.addSource(following, resource -> {
                if (resource != null && resource.data != null) collect(resource.data, combined, seen);
                merged.setValue(Resource.success(new ArrayList<>(combined)));
            });
            live = merged;
        } else {
            live = SOURCE_FOLLOWERS.equals(source) ? repo.getFollowers(myId) : repo.getFollowing(myId);
        }
        live.observe(this, resource -> {
            progress.setVisibility(View.GONE);
            if (resource == null || resource.status != Resource.Status.SUCCESS || resource.data == null) {
                tvEmpty.setText(R.string.st_network_error);
                tvEmpty.setVisibility(View.VISIBLE);
                return;
            }
            all.clear();
            for (Profile p : resource.data) {
                if (p != null && p.getId() != null && !p.getId().equals(myId)) all.add(p);
            }
            filter(search.getText() == null ? "" : search.getText().toString());
        });
    }

    private static void collect(List<Profile> from, List<Profile> into, Set<String> seen) {
        for (Profile p : from) {
            if (p == null || p.getId() == null) continue;
            if (seen.add(p.getId())) into.add(p);
        }
    }

    private void filter(String query) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        visible.clear();
        for (Profile p : all) {
            if (needle.isEmpty()
                    || (p.getUsername() != null && p.getUsername().toLowerCase(Locale.ROOT).contains(needle))
                    || (p.getFullName() != null && p.getFullName().toLowerCase(Locale.ROOT).contains(needle))) {
                visible.add(p);
            }
        }
        adapter.notifyDataSetChanged();
        tvEmpty.setText(all.isEmpty() ? R.string.st_no_followers : R.string.st_no_results);
        tvEmpty.setVisibility(visible.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class Adapter extends RecyclerView.Adapter<Adapter.Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_people_row, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Profile profile = visible.get(position);
            holder.tvUsername.setText(profile.getUsername());
            holder.tvName.setText(profile.getFullName());
            GlideUtils.loadAvatar(holder.ivAvatar, profile.getAvatarUrl());
            holder.btnAction.setVisibility(View.GONE);
            holder.check.setVisibility(View.VISIBLE);
            holder.check.setChecked(selected.contains(profile.getId()));
            View.OnClickListener toggle = v -> {
                if (selected.contains(profile.getId())) selected.remove(profile.getId());
                else selected.add(profile.getId());
                notifyItemChanged(position);
                // Keep the action word visible: overwriting it with "2 selected" turns the
                // confirm button into what looks like a label.
                tvCount.setText(selected.isEmpty() ? getString(R.string.st_add)
                        : getString(R.string.st_add) + " (" + selected.size() + ")");
            };
            holder.check.setOnClickListener(toggle);
            holder.itemView.setOnClickListener(toggle);
            if (position == 0 && selected.isEmpty()) tvCount.setText(R.string.st_add);
        }

        @Override
        public int getItemCount() {
            return visible.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ImageView ivAvatar;
            final TextView tvUsername;
            final TextView tvName;
            final TextView btnAction;
            final CheckBox check;

            Holder(@NonNull View itemView) {
                super(itemView);
                ivAvatar = itemView.findViewById(R.id.ivAvatar);
                tvUsername = itemView.findViewById(R.id.tvUsername);
                tvName = itemView.findViewById(R.id.tvName);
                btnAction = itemView.findViewById(R.id.btnRowAction);
                check = itemView.findViewById(R.id.checkSelect);
            }
        }
    }
}
