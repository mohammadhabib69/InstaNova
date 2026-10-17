package com.insta.nova.ui.chat;

import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.insta.nova.R;
import com.insta.nova.data.remote.dto.PostDto;
import com.insta.nova.data.remote.dto.ProfileDto;
import com.insta.nova.data.model.Highlight;
import com.insta.nova.databinding.ItemP18EmptyBinding;
import com.insta.nova.databinding.ItemP18HeaderBinding;
import com.insta.nova.databinding.ItemP18HighlightBinding;
import com.insta.nova.databinding.ItemP18HighlightsBinding;
import com.insta.nova.databinding.ItemP18SuggestionBinding;
import com.insta.nova.databinding.ItemP18TabsBinding;
import com.insta.nova.databinding.ItemP18TileBinding;
import com.insta.nova.util.GlideUtils;
import com.insta.nova.ui.widget.AvatarPreview;
import com.insta.nova.data.repository.UserProfileRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** PAGE 18 — header / highlights / tabs / 3-up grid rows. */
public class ProfileScreenAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int TYPE_HEADER = 0;
    public static final int TYPE_HIGHLIGHTS = 1;
    public static final int TYPE_TABS = 2;
    public static final int TYPE_TILE = 3;
    public static final int TYPE_EMPTY = 4;

    public interface Listener {
        void onAvatar();
        void onPostsStat();
        void onTab(int tab);
        void onFollowersStat();
        void onFollowingStat();
        void onFollowButton();
        void onMessage();
        void onAddPerson();
        void onHighlight(Highlight highlight);
        void onPost(PostDto post);
        void onBioMore();
        void onHashtag(String tag);
        void onMention(String username);
        void onUrl(String url);
        void onSuggestionFollow(ProfileDto profile);
        void onSuggestionDismiss(ProfileDto profile);
    }

    private final Listener listener;
    private final List<Object> rows = new ArrayList<>();
    private UserProfileViewModel.Ui ui;
    private int tab;
    private boolean suggestionsOpen;
    private static final Pattern LINK = Pattern.compile("(#\\w+|@\\w+|https?://\\S+)");

    public ProfileScreenAdapter(Listener listener) {
        this.listener = listener;
        setHasStableIds(false);
    }

    public void submit(UserProfileViewModel.Ui ui, int tab, boolean suggestionsOpen) {
        this.ui = ui;
        this.tab = tab;
        this.suggestionsOpen = suggestionsOpen;
        rebuild();
        notifyDataSetChanged();
    }

    public int currentTab() { return tab; }

    private void rebuild() {
        rows.clear();
        if (ui == null || ui.profile == null) return;
        rows.add(TYPE_HEADER);
        if (!ui.highlights.isEmpty()) rows.add(TYPE_HIGHLIGHTS);
        rows.add(TYPE_TABS);
        if (tab == 0) {
            if (ui.posts.isEmpty()) rows.add(TYPE_EMPTY);
            else rows.addAll(ui.posts);
        } else {
            rows.add(TYPE_EMPTY);
        }
    }

    @Override public int getItemViewType(int position) {
        Object row = rows.get(position);
        if (row instanceof PostDto) return TYPE_TILE;
        return (int) row;
    }

    @NonNull @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case TYPE_HEADER:
                return new HeaderHolder(ItemP18HeaderBinding.inflate(inflater, parent, false));
            case TYPE_HIGHLIGHTS:
                return new HighlightsHolder(ItemP18HighlightsBinding.inflate(inflater, parent, false));
            case TYPE_TABS:
                return new TabsHolder(ItemP18TabsBinding.inflate(inflater, parent, false));
            case TYPE_EMPTY:
                return new EmptyHolder(ItemP18EmptyBinding.inflate(inflater, parent, false));
            default:
                return new TileHolder(ItemP18TileBinding.inflate(inflater, parent, false));
        }
    }

    @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object row = rows.get(position);
        if (holder instanceof HeaderHolder) bindHeader((HeaderHolder) holder);
        else if (holder instanceof HighlightsHolder) bindHighlights((HighlightsHolder) holder);
        else if (holder instanceof TabsHolder) bindTabs((TabsHolder) holder);
        else if (holder instanceof EmptyHolder) bindEmpty((EmptyHolder) holder);
        else if (holder instanceof TileHolder) bindTile((TileHolder) holder, (PostDto) row);
    }

    @Override public int getItemCount() { return rows.size(); }

    @Override public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof HeaderHolder) AvatarPreview.dismiss();
        super.onViewRecycled(holder);
    }

    /* ---------------- header ---------------- */

    private void bindHeader(HeaderHolder holder) {
        ItemP18HeaderBinding b = holder.binding;
        ProfileDto p = ui.profile;

        if (ui.hasStory) {
            b.avatarRing.setBackgroundResource(ui.unseenStory
                    ? R.drawable.bg_p18_avatar_ring : R.drawable.bg_p18_avatar_ring_seen);
        } else {
            b.avatarRing.setBackgroundResource(R.drawable.bg_p18_avatar_ring_none);
        }
        GlideUtils.loadAvatar(b.ivAvatar, p.getAvatarUrl());

        b.tvFullName.setText(p.getFullName() == null || p.getFullName().isEmpty()
                ? p.getUsername() : p.getFullName());
        b.tvPostsCount.setText(count(p.getPostsCount()));
        b.tvFollowersCount.setText(count(ui.followers()));
        b.tvFollowingCount.setText(count(p.getFollowingCount()));

        bindBio(b);

        String site = p.getWebsite();
        if (site == null || site.trim().isEmpty()) {
            b.websiteRow.setVisibility(View.GONE);
        } else {
            b.websiteRow.setVisibility(View.VISIBLE);
            b.tvWebsite.setText(site);
            b.websiteRow.setOnClickListener(v -> listener.onUrl(normalizeUrl(site)));
        }

        bindFollowedBy(b);

        // Follow button state.
        if (ui.iFollow) {
            b.tvFollow.setText("Following");
            b.ivFollowChevron.setVisibility(View.VISIBLE);
            b.btnFollow.setBackgroundResource(R.drawable.bg_p18_button);
        } else {
            b.tvFollow.setText(ui.followsMe ? "Follow back" : "Follow");
            b.ivFollowChevron.setVisibility(View.GONE);
            b.btnFollow.setBackgroundResource(R.drawable.bg_p18_primary);
        }

        b.btnFollow.setOnClickListener(v -> {
            if (ui.iFollow) listener.onFollowButton();
            else listener.onFollowButton();
        });
        b.btnMessage.setOnClickListener(v -> listener.onMessage());
        b.btnAddPerson.setOnClickListener(v -> listener.onAddPerson());
        b.btnAddPerson.setBackgroundResource(suggestionsOpen
                ? R.drawable.bg_p18_button_light : R.drawable.bg_p18_button);

        b.avatarFrame.setOnClickListener(v -> {
            AvatarPreview.dismiss();
            listener.onAvatar();
        });
        b.avatarFrame.setOnLongClickListener(v -> {
            AvatarPreview.show(v, p.getAvatarUrl());
            return true; // never fall through to the single-tap story action
        });
        b.avatarFrame.setOnTouchListener((v, event) -> {
            if ((event.getAction() == MotionEvent.ACTION_UP
                    || event.getAction() == MotionEvent.ACTION_CANCEL)
                    && AvatarPreview.isShowing()) {
                AvatarPreview.dismiss();
            }
            return false; // preserve normal click/long-click dispatch
        });
        b.colPosts.setOnClickListener(v -> listener.onPostsStat());
        b.colFollowers.setOnClickListener(v -> listener.onFollowersStat());
        b.colFollowing.setOnClickListener(v -> listener.onFollowingStat());
        b.btnMoreBio.setOnClickListener(v -> listener.onBioMore());
        b.tvSeeTranslation.setOnClickListener(v -> { });

        b.suggestionsBlock.setVisibility(suggestionsOpen && !ui.suggestions.isEmpty()
                ? View.VISIBLE : View.GONE);
        if (suggestionsOpen) {
            SuggestionAdapter adapter = new SuggestionAdapter();
            b.rvSuggestions.setLayoutManager(new LinearLayoutManager(
                    b.rvSuggestions.getContext(), LinearLayoutManager.HORIZONTAL, false));
            b.rvSuggestions.setAdapter(adapter);
        }
        b.tvSeeAllSuggestions.setOnClickListener(v -> { });
    }

    private void bindBio(ItemP18HeaderBinding b) {
        ProfileDto p = ui.profile;
        String bio = p.getBio() == null ? "" : p.getBio();
        b.tvBio.setVisibility(bio.isEmpty() ? View.GONE : View.VISIBLE);
        b.tvSeeTranslation.setVisibility(bio.isEmpty() ? View.GONE : View.VISIBLE);
        if (bio.isEmpty()) {
            b.btnMoreBio.setVisibility(View.GONE);
            return;
        }
        SpannableString span = new SpannableString(bio);
        Matcher m = LINK.matcher(bio);
        while (m.find()) {
            final String token = m.group();
            int color = token.startsWith("#") ? b.tvBio.getContext().getColor(R.color.profile_mention)
                    : b.tvBio.getContext().getColor(R.color.profile_link);
            span.setSpan(new ClickableSpan() {
                @Override public void onClick(@NonNull View widget) {
                    if (token.startsWith("#")) listener.onHashtag(token.substring(1));
                    else if (token.startsWith("@")) listener.onMention(token.substring(1));
                    else listener.onUrl(token);
                }
                @Override public void updateDrawState(@NonNull TextPaint ds) {
                    ds.setColor(color);
                    ds.setUnderlineText(false);
                }
            }, m.start(), m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        b.tvBio.setText(span);
        b.tvBio.setMovementMethod(LinkMovementMethod.getInstance());
        boolean expanded = "expanded".equals(ui.bioOverride);
        b.tvBio.setMaxLines(expanded ? 30 : 5);
        b.btnMoreBio.setVisibility(expanded ? View.GONE : View.VISIBLE);
    }

    private void bindFollowedBy(ItemP18HeaderBinding b) {
        UserProfileRepository.FollowedBy fb = ui.followedBy;
        b.followedByAvatars.removeAllViews();
        if (fb == null || fb.total == 0 || fb.usernames.isEmpty()) {
            b.followedByRow.setVisibility(View.GONE);
            return;
        }
        b.followedByRow.setVisibility(View.VISIBLE);
        float d = b.getRoot().getResources().getDisplayMetrics().density;
        int size = (int) (28 * d);
        int overlap = (int) (10 * d);
        for (int i = 0; i < Math.min(3, fb.avatars.size()); i++) {
            ImageView iv = new ImageView(b.getRoot().getContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            if (i > 0) lp.setMarginStart(-overlap);
            iv.setLayoutParams(lp);
            iv.setBackgroundResource(R.drawable.bg_p18_inner_gap);
            iv.setPadding((int) (1.5f * d), (int) (1.5f * d), (int) (1.5f * d), (int) (1.5f * d));
            GlideUtils.loadAvatar(iv, fb.avatars.get(i));
            b.followedByAvatars.addView(iv);
        }

        String text;
        if (fb.total == 1 || fb.usernames.size() == 1) {
            text = "Followed by " + fb.usernames.get(0);
        } else if (fb.usernames.size() == 2 && fb.total <= 2) {
            text = "Followed by " + fb.usernames.get(0) + " and " + fb.usernames.get(1);
        } else if (fb.usernames.size() >= 2) {
            int others = Math.max(1, fb.total - 2);
            text = "Followed by " + fb.usernames.get(0) + ", " + fb.usernames.get(1)
                    + " " + com.insta.nova.util.Plural.likedByTail(b.followedByRow.getContext(), others);
        } else {
            text = "Followed by " + fb.usernames.get(0);
        }
        SpannableString span = new SpannableString(text);
        for (String name : fb.usernames) {
            int idx = text.indexOf(name);
            if (idx >= 0) span.setSpan(new StyleSpan(Typeface.BOLD), idx, idx + name.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        b.tvFollowedBy.setText(span);
    }

    private static String normalizeUrl(String url) {
        return url.startsWith("http") ? url : "https://" + url;
    }

    static String count(int value) {
        return com.insta.nova.util.NumberFormat.formatProfileStat(value);
    }

    private static String trim(double v) {
        String s = String.format(Locale.US, "%.1f", v);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    /* ---------------- highlights ---------------- */

    private void bindHighlights(HighlightsHolder holder) {
        holder.binding.rvHighlights.setLayoutManager(new LinearLayoutManager(
                holder.binding.getRoot().getContext(), LinearLayoutManager.HORIZONTAL, false));
        holder.binding.rvHighlights.setAdapter(new HighlightAdapter(ui.highlights, listener));
    }

    /* ---------------- tabs ---------------- */

    private void bindTabs(TabsHolder holder) {
        ItemP18TabsBinding b = holder.binding;
        int active = b.getRoot().getContext().getColor(R.color.color_text_primary);
        int muted = b.getRoot().getContext().getColor(R.color.color_text_secondary);
        b.icGrid.setColorFilter(tab == 0 ? active : muted);
        b.icRepost.setColorFilter(tab == 1 ? active : muted);
        b.icTagged.setColorFilter(tab == 2 ? active : muted);
        b.gridUnderline.setVisibility(tab == 0 ? View.VISIBLE : View.INVISIBLE);
        b.tabGrid.setOnClickListener(v -> listener.onTab(0));
        b.tabRepost.setOnClickListener(v -> listener.onTab(1));
        b.tabTagged.setOnClickListener(v -> listener.onTab(2));
    }

    /* ---------------- grid ---------------- */

    private void bindTile(TileHolder holder, PostDto post) {
        ItemP18TileBinding b = holder.binding;
        String image = post.isReel() && post.getThumbnailUrl() != null && !post.getThumbnailUrl().isEmpty()
                ? post.getThumbnailUrl()
                : (post.getMediaUrls() != null && !post.getMediaUrls().isEmpty() ? post.getMediaUrls().get(0) : null);
        GlideUtils.load(b.ivTile, "posts", image);
        if (post.isReel()) {
            b.ivBadge.setVisibility(View.VISIBLE);
            b.ivBadge.setImageResource(R.drawable.ic_play);
        } else if (post.getMediaUrls() != null && post.getMediaUrls().size() > 1) {
            b.ivBadge.setVisibility(View.VISIBLE);
            b.ivBadge.setImageResource(R.drawable.ic_multi_image);
        } else {
            b.ivBadge.setVisibility(View.GONE);
        }
        b.tileRoot.setOnClickListener(v -> listener.onPost(post));
    }

    private void bindEmpty(EmptyHolder holder) {
        ItemP18EmptyBinding b = holder.binding;
        if (tab == 1) {
            b.tvEmptyTitle.setText("No reposts yet");
            b.tvEmptySubtitle.setText("");
        } else if (tab == 2) {
            b.tvEmptyTitle.setText("No photos of you yet");
            b.tvEmptySubtitle.setText("");
        } else {
            b.tvEmptyTitle.setText("No posts yet");
            b.tvEmptySubtitle.setText("");
        }
    }

    static class HeaderHolder extends RecyclerView.ViewHolder {
        final ItemP18HeaderBinding binding;
        HeaderHolder(ItemP18HeaderBinding b) { super(b.getRoot()); binding = b; }
    }
    static class HighlightsHolder extends RecyclerView.ViewHolder {
        final ItemP18HighlightsBinding binding;
        HighlightsHolder(ItemP18HighlightsBinding b) { super(b.getRoot()); binding = b; }
    }
    static class TabsHolder extends RecyclerView.ViewHolder {
        final ItemP18TabsBinding binding;
        TabsHolder(ItemP18TabsBinding b) { super(b.getRoot()); binding = b; }
    }
    static class TileHolder extends RecyclerView.ViewHolder {
        final ItemP18TileBinding binding;
        TileHolder(ItemP18TileBinding b) {
            super(b.getRoot());
            binding = b;
            int w = b.getRoot().getResources().getDisplayMetrics().widthPixels / 3;
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, w * 4 / 3);
            b.getRoot().setLayoutParams(lp);
        }
    }
    static class EmptyHolder extends RecyclerView.ViewHolder {
        final ItemP18EmptyBinding binding;
        EmptyHolder(ItemP18EmptyBinding b) { super(b.getRoot()); binding = b; }
    }

    /* ---------------- nested adapters ---------------- */

    private class SuggestionAdapter extends RecyclerView.Adapter<SuggestionAdapter.Holder> {
        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemP18SuggestionBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }
        @Override public void onBindViewHolder(@NonNull Holder h, int position) {
            ProfileDto p = ui.suggestions.get(position);
            GlideUtils.loadAvatar(h.b.ivSuggestAvatar, p.getAvatarUrl());
            h.b.tvSuggestUsername.setText(p.getUsername());
            h.b.tvSuggestMeta.setText(com.insta.nova.util.Plural.followers(h.b.getRoot().getContext(), p.getFollowersCount()));
            h.b.btnSuggestFollow.setText("Follow");
            h.b.btnSuggestFollow.setOnClickListener(v -> listener.onSuggestionFollow(p));
            h.b.btnDismiss.setOnClickListener(v -> listener.onSuggestionDismiss(p));
        }
        @Override public int getItemCount() { return ui == null ? 0 : ui.suggestions.size(); }
        class Holder extends RecyclerView.ViewHolder {
            final ItemP18SuggestionBinding b;
            Holder(ItemP18SuggestionBinding b) { super(b.getRoot()); this.b = b; }
        }
    }

    private static class HighlightAdapter extends RecyclerView.Adapter<HighlightAdapter.Holder> {
        private final List<Highlight> items;
        private final Listener listener;
        HighlightAdapter(List<Highlight> items, Listener listener) {
            this.items = items;
            this.listener = listener;
        }
        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemP18HighlightBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }
        @Override public void onBindViewHolder(@NonNull Holder h, int position) {
            Highlight item = items.get(position);
            GlideUtils.load(h.b.ivCover, "highlights", item.getCoverUrl());
            h.b.tvTitle.setText(item.getTitle());
            h.b.getRoot().setOnClickListener(v -> listener.onHighlight(item));
        }
        @Override public int getItemCount() { return items.size(); }
        static class Holder extends RecyclerView.ViewHolder {
            final ItemP18HighlightBinding b;
            Holder(ItemP18HighlightBinding b) { super(b.getRoot()); this.b = b; }
        }
    }
}
