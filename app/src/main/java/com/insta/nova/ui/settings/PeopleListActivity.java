package com.insta.nova.ui.settings;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.insta.nova.R;
import com.insta.nova.data.local.RelationshipStore;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.model.Profile;
import com.insta.nova.data.repository.FollowRepository;
import com.insta.nova.data.repository.RelationshipRepository;
import com.insta.nova.data.repository.SearchRepository;
import com.insta.nova.data.repository.SocialRepository;
import com.insta.nova.ui.chat.UserProfileActivity;
import com.insta.nova.util.GlideUtils;
import com.insta.nova.util.Resource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Prompt N — real people lists behind Settings and Profile.
 *
 * <p>Modes share one renderer: Blocked, Restricted, Close Friends, Story audience,
 * Followers, Following and Discover people. Every action writes the same store the
 * owning feature reads.
 */
public class PeopleListActivity extends AppCompatActivity {

    public static final String EXTRA_MODE = "extra_mode";
    public static final String EXTRA_TITLE = "extra_title";

    public static final String MODE_BLOCKED = "blocked";
    public static final String MODE_RESTRICTED = "restricted";
    public static final String MODE_CLOSE_FRIENDS = "close_friends";
    public static final String MODE_STORY_AUDIENCE = "story_audience";
    public static final String MODE_FOLLOWERS = "followers";
    public static final String MODE_FOLLOWING = "following";
    public static final String MODE_DISCOVER = "discover";

    /** Last successful payload per mode and profile, so reopening is instant. */
    private static final java.util.Map<String, List<Profile>> CACHE = new java.util.HashMap<>();
    private static final java.util.Map<String, Set<String>> FOLLOWING_CACHE = new java.util.HashMap<>();

    private final List<Profile> people = new ArrayList<>();
    private final Set<String> myFollowing = new HashSet<>();
    private boolean firstResume = true;
    private Adapter adapter;
    private String mode;
    private TextView tvEmpty;
    private ProgressBar progress;
    private TextView btnAdd;
    private RelationshipRepository relationships;
    private FollowRepository followRepository;
    private String myId;

    private ActivityResultLauncher<Intent> pickerLauncher;

    public static Intent intent(Context context, String mode, String title) {
        return new Intent(context, PeopleListActivity.class)
                .putExtra(EXTRA_MODE, mode)
                .putExtra(EXTRA_TITLE, title);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_people_list);

        mode = getIntent().getStringExtra(EXTRA_MODE);
        if (mode == null) mode = MODE_BLOCKED;
        relationships = new RelationshipRepository(this);
        followRepository = new FollowRepository(this);
        myId = SessionManager.getInstance(this).getUserId();

        TextView title = findViewById(R.id.tvTitle);
        title.setText(getIntent().getStringExtra(EXTRA_TITLE));
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        tvEmpty = findViewById(R.id.tvEmpty);
        progress = findViewById(R.id.progressPeople);
        btnAdd = findViewById(R.id.btnAction);

        TextView description = findViewById(R.id.tvDescription);
        int desc = descriptionFor(mode);
        if (desc != 0) {
            description.setText(desc);
            description.setVisibility(View.VISIBLE);
        }

        // Restricted accounts had no way to add anybody: the list was permanently empty and
        // the only entry point was the profile three-dot. Same picker pattern as the others.
        if (MODE_CLOSE_FRIENDS.equals(mode) || MODE_STORY_AUDIENCE.equals(mode)
                || MODE_RESTRICTED.equals(mode)) {
            btnAdd.setVisibility(View.VISIBLE);
            btnAdd.setText(R.string.st_add);
            btnAdd.setOnClickListener(v -> openPicker());
        }

        RecyclerView rv = findViewById(R.id.rvPeople);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Adapter();
        rv.setAdapter(adapter);

        pickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                    ArrayList<String> ids = result.getData().getStringArrayListExtra(PeoplePickerActivity.RESULT_IDS);
                    if (ids == null || ids.isEmpty()) return;
                    for (String id : ids) {
                        if (MODE_CLOSE_FRIENDS.equals(mode)) {
                            relationships.addCloseFriend(id);
                        } else if (MODE_RESTRICTED.equals(mode)) {
                            relationships.setRestricted(id, true).observe(this, r -> { });
                        } else {
                            relationships.addStoryHidden(id).observe(this, r -> { });
                        }
                    }
                    Toast.makeText(this, R.string.st_save_changes, Toast.LENGTH_SHORT).show();
                    new android.os.Handler(android.os.Looper.getMainLooper())
                            .postDelayed(this::load, 900);
                });

        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter == null) return;
        // onCreate already fired the first load. Loading again on the immediately
        // following onResume doubled every request and kept the spinner up twice as
        // long, which is what made this screen feel slow to open.
        if (firstResume) {
            firstResume = false;
            return;
        }
        load();
    }

    private int descriptionFor(String mode) {
        if (MODE_BLOCKED.equals(mode)) return R.string.st_blocked_desc;
        if (MODE_RESTRICTED.equals(mode)) return R.string.st_restricted_desc;
        if (MODE_CLOSE_FRIENDS.equals(mode)) return R.string.st_close_friends_desc;
        if (MODE_STORY_AUDIENCE.equals(mode)) return R.string.st_hide_story_desc;
        return 0;
    }

    private void openPicker() {
        String source;
        if (MODE_STORY_AUDIENCE.equals(mode)) {
            source = PeoplePickerActivity.SOURCE_FOLLOWERS;
        } else if (MODE_RESTRICTED.equals(mode)) {
            source = PeoplePickerActivity.SOURCE_EITHER;
        } else {
            source = PeoplePickerActivity.SOURCE_FOLLOWING;
        }
        List<String> preselected = new ArrayList<>();
        if (MODE_STORY_AUDIENCE.equals(mode)) preselected.addAll(new RelationshipStore(this).storyHidden());
        else if (MODE_RESTRICTED.equals(mode)) preselected.addAll(new RelationshipStore(this).restricted());
        else preselected.addAll(new RelationshipStore(this).closeFriends());
        pickerLauncher.launch(PeoplePickerActivity.intent(this, source,
                getString(R.string.st_select_people), preselected));
    }

    private void load() {
        final long startedAt = android.os.SystemClock.elapsedRealtime();
        // A list that was opened once is drawn from memory straight away, so the second
        // visit is instant; the request below still refreshes it in the background.
        List<Profile> cached = CACHE.get(cacheKey());
        if (cached != null && !cached.isEmpty()) {
            people.clear();
            people.addAll(cached);
            adapter.notifyDataSetChanged();
            tvEmpty.setVisibility(View.GONE);
            progress.setVisibility(View.GONE);
        } else {
            progress.setVisibility(View.VISIBLE);
            tvEmpty.setVisibility(View.GONE);
        }
        SocialRepository social = new SocialRepository(this);
        LiveData<Resource<List<Profile>>> live;
        switch (mode) {
            case MODE_RESTRICTED:
                live = relationships.loadRestricted();
                break;
            case MODE_CLOSE_FRIENDS:
                live = relationships.loadCloseFriends();
                break;
            case MODE_STORY_AUDIENCE:
                live = relationships.loadStoryHidden();
                break;
            case MODE_FOLLOWERS:
                live = social.getFollowers(myId);
                break;
            case MODE_FOLLOWING:
                live = social.getFollowing(myId);
                break;
            case MODE_DISCOVER:
                live = new SearchRepository(this).getSuggestedUsers(30);
                break;
            case MODE_BLOCKED:
            default:
                live = relationships.loadBlocked();
                break;
        }
        live.observe(this, resource -> {
            progress.setVisibility(View.GONE);
            if (resource == null) return;
            if (resource.status == Resource.Status.ERROR) {
                if (people.isEmpty()) {
                    tvEmpty.setText(R.string.st_network_error);
                    tvEmpty.setVisibility(View.VISIBLE);
                }
                return;
            }
            if (resource.status != Resource.Status.SUCCESS || resource.data == null) return;
            people.clear();
            for (Profile p : resource.data) {
                if (p != null && p.getId() != null) people.add(p);
            }
            CACHE.put(cacheKey(), new ArrayList<>(people));
            adapter.notifyDataSetChanged();
            tvEmpty.setText(emptyText());
            tvEmpty.setVisibility(people.isEmpty() ? View.VISIBLE : View.GONE);
            android.util.Log.i("PeopleList", "mode=" + mode + " rows=" + people.size()
                    + " in " + (android.os.SystemClock.elapsedRealtime() - startedAt) + "ms");
            if (needsMyFollowing()) loadMyFollowing();
        });
    }

    /** One cache bucket per mode and signed-in profile. */
    private String cacheKey() {
        return mode + "|" + myId;
    }

    private int emptyText() {
        if (MODE_BLOCKED.equals(mode)) return R.string.st_blocked_empty;
        if (MODE_RESTRICTED.equals(mode)) return R.string.st_restricted_empty;
        if (MODE_CLOSE_FRIENDS.equals(mode)) return R.string.st_close_friends_empty;
        if (MODE_STORY_AUDIENCE.equals(mode)) return R.string.st_none_hidden;
        if (MODE_FOLLOWERS.equals(mode)) return R.string.st_no_followers;
        if (MODE_FOLLOWING.equals(mode)) return R.string.st_no_following;
        return R.string.st_discover_empty;
    }

    private boolean needsMyFollowing() {
        return MODE_FOLLOWERS.equals(mode) || MODE_FOLLOWING.equals(mode) || MODE_DISCOVER.equals(mode);
    }

    private void loadMyFollowing() {
        Set<String> known = FOLLOWING_CACHE.get(myId);
        if (known != null) {
            myFollowing.clear();
            myFollowing.addAll(known);
            adapter.notifyDataSetChanged();
        }
        new SocialRepository(this).getFollowing(myId).observe(this, resource -> {
            if (resource == null || resource.status != Resource.Status.SUCCESS || resource.data == null) return;
            myFollowing.clear();
            for (Profile p : resource.data) if (p != null && p.getId() != null) myFollowing.add(p.getId());
            FOLLOWING_CACHE.put(myId, new HashSet<>(myFollowing));
            adapter.notifyDataSetChanged();
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
            Profile profile = people.get(position);
            holder.tvUsername.setText(profile.getUsername());
            holder.tvName.setText(profile.getFullName());
            GlideUtils.loadAvatar(holder.ivAvatar, profile.getAvatarUrl());
            holder.check.setVisibility(View.GONE);
            holder.btnAction.setVisibility(View.VISIBLE);

            if (MODE_BLOCKED.equals(mode)) {
                holder.btnAction.setText(R.string.st_unblock);
                holder.btnAction.setTextColor(getColor(R.color.color_text_primary));
                holder.btnAction.setOnClickListener(v -> relationships.setBlocked(profile.getId(), false)
                        .observe(PeopleListActivity.this, r -> {
                            if (r != null && r.status == Resource.Status.SUCCESS) load();
                        }));
            } else if (MODE_RESTRICTED.equals(mode)) {
                holder.btnAction.setText(R.string.st_unrestrict);
                holder.btnAction.setOnClickListener(v -> relationships.setRestricted(profile.getId(), false)
                        .observe(PeopleListActivity.this, r -> {
                            if (r != null && r.status == Resource.Status.SUCCESS) load();
                        }));
            } else if (MODE_CLOSE_FRIENDS.equals(mode) || MODE_STORY_AUDIENCE.equals(mode)) {
                holder.btnAction.setText(R.string.st_remove);
                holder.btnAction.setOnClickListener(v -> {
                    if (MODE_CLOSE_FRIENDS.equals(mode)) {
                        relationships.removeCloseFriend(profile.getId())
                                .observe(PeopleListActivity.this, r -> load());
                    } else {
                        relationships.removeStoryHidden(profile.getId()).observe(PeopleListActivity.this, r -> {
                            if (r != null && r.status == Resource.Status.SUCCESS) load();
                        });
                    }
                });
            } else {
                boolean following = myFollowing.contains(profile.getId());
                // On the followers list every row already follows me, so a missing
                // follow from my side is a follow-back, not a cold follow.
                boolean followsMe = MODE_FOLLOWERS.equals(mode);
                holder.btnAction.setText(following ? R.string.st_following
                        : (followsMe ? R.string.st_follow_back : R.string.st_follow));
                holder.btnAction.setTextColor(getColor(following
                        ? R.color.color_text_primary : R.color.white));
                holder.btnAction.setBackgroundResource(following
                        ? R.drawable.bg_button_secondary : R.drawable.bg_button_primary);
                holder.btnAction.setOnClickListener(v -> {
                    boolean next = !myFollowing.contains(profile.getId());
                    followRepository.followWithState(profile.getId(), next).observe(PeopleListActivity.this, r -> {
                        if (r == null) return;
                        if (r.status == Resource.Status.SUCCESS && "requested".equals(r.data)) {
                            Toast.makeText(PeopleListActivity.this, R.string.st_follow_requested,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (next) myFollowing.add(profile.getId());
                        else myFollowing.remove(profile.getId());
                        notifyItemChanged(position);
                    });
                });
            }

            holder.itemView.setOnClickListener(v -> {
                if (profile.getId() == null || profile.getId().equals(myId)) return;
                Intent intent = new Intent(PeopleListActivity.this, UserProfileActivity.class);
                intent.putExtra(UserProfileActivity.EXTRA_USER_ID, profile.getId());
                intent.putExtra(UserProfileActivity.EXTRA_USERNAME, profile.getUsername());
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return people.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ImageView ivAvatar;
            final TextView tvUsername;
            final TextView tvName;
            final TextView btnAction;
            final android.widget.CheckBox check;

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
