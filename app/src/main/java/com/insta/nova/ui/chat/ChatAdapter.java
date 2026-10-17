package com.insta.nova.ui.chat;

import android.content.Context;
import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.insta.nova.R;
import com.insta.nova.calling.CallRecord;
import com.insta.nova.data.model.Message;
import com.insta.nova.data.model.MessageReaction;
import com.insta.nova.databinding.ItemChatIntroBinding;
import com.insta.nova.databinding.ItemChatReceivedBinding;
import com.insta.nova.databinding.ItemChatSeenBinding;
import com.insta.nova.databinding.ItemChatSentBinding;
import com.insta.nova.databinding.ItemChatSeparatorBinding;
import com.insta.nova.util.GlideUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * PAGE 17 — conversation rows: intro, time separators, sent/received bubbles and
 * the trailing 'Seen'. Supports text, full Instagram-size images, voice notes, calls,
 * and replies.
 */
public class ChatAdapter extends ListAdapter<ChatAdapter.ChatRow, RecyclerView.ViewHolder> {

    public static final int TYPE_INTRO = 0;
    public static final int TYPE_SEPARATOR = 1;
    public static final int TYPE_SENT = 2;
    public static final int TYPE_RECEIVED = 3;
    public static final int TYPE_SEEN = 4;

    /** One rendered row; built from ChatState, never held by the ViewModel. */
    public static class ChatRow {
        public int type;
        public String id;
        public Message message;
        public String label;
        public ChatIntro intro;
        public boolean showAvatar;
        public int topPaddingDp;
        public boolean showReactions;

        static ChatRow intro(ChatIntro intro) {
            ChatRow r = new ChatRow();
            r.type = TYPE_INTRO;
            r.id = "intro";
            r.intro = intro;
            return r;
        }

        static ChatRow separator(String label) {
            ChatRow r = new ChatRow();
            r.type = TYPE_SEPARATOR;
            r.id = "sep-" + label;
            r.label = label;
            return r;
        }

        static ChatRow message(Message m, boolean sent, boolean showAvatar, int topPaddingDp) {
            ChatRow r = new ChatRow();
            r.type = sent ? TYPE_SENT : TYPE_RECEIVED;
            r.id = m.getId() != null ? m.getId() : ("m-" + m.hashCode());
            r.message = m;
            r.showAvatar = showAvatar;
            r.topPaddingDp = topPaddingDp;
            r.showReactions = !m.getReactions().isEmpty();
            return r;
        }

        static ChatRow seen() {
            ChatRow r = new ChatRow();
            r.type = TYPE_SEEN;
            r.id = "seen";
            return r;
        }
    }

    public interface Listener {
        void onViewProfile();
        void onMessageClick(Message message);
        void onMessageLongPress(Message message, View anchor);
        void onMessageDoubleTap(Message message);
        void onRetry(Message message);
        void onReactionClick(Message message);
        void onImageClick(Message message);
        void onVoicePlayClick(Message message, ProgressBar progress, ImageView playBtn);
        void onBackgroundLongPress();
    }

    private final String myId;
    private final String otherAvatarUrl;
    private final int screenWidthPx;
    private final int maxTextWidthPx;
    private final float density;
    private final Listener listener;

    private ChatTheme currentTheme;
    private String searchQuery = "";

    public ChatAdapter(String myId, String otherAvatarUrl, Listener listener, int screenWidthPx, float density) {
        super(DIFF);
        this.myId = myId;
        this.otherAvatarUrl = otherAvatarUrl;
        this.listener = listener;
        this.screenWidthPx = screenWidthPx;
        this.density = density;
        this.maxTextWidthPx = (int) (screenWidthPx * 0.75f - 32 * density);
    }

    public void setChatTheme(ChatTheme theme) {
        this.currentTheme = theme;
        notifyDataSetChanged();
    }

    public void setSearchQuery(String query) {
        this.searchQuery = query == null ? "" : query.trim().toLowerCase(Locale.US);
        notifyDataSetChanged();
    }

    private static final DiffUtil.ItemCallback<ChatRow> DIFF = new DiffUtil.ItemCallback<ChatRow>() {
        @Override
        public boolean areItemsTheSame(@NonNull ChatRow a, @NonNull ChatRow b) {
            if (a.type != b.type) return false;
            return a.id != null && a.id.equals(b.id);
        }

        @Override
        public boolean areContentsTheSame(@NonNull ChatRow a, @NonNull ChatRow b) {
            if (!areItemsTheSame(a, b)) return false;
            if (a.type == TYPE_INTRO) return sameIntro(a.intro, b.intro);
            if (a.type == TYPE_SEPARATOR) return eq(a.label, b.label);
            if (a.type == TYPE_SEEN) return true;
            Message ma = a.message;
            Message mb = b.message;
            if (ma == null || mb == null) return false;
            return eq(ma.getText(), mb.getText())
                    && eq(ma.getImageUrl(), mb.getImageUrl())
                    && eq(ma.getLocalImageUri(), mb.getLocalImageUri())
                    && eq(ma.getMessageType(), mb.getMessageType())
                    && eq(ma.getVoicePath(), mb.getVoicePath())
                    && ma.getVoiceDurationMs() == mb.getVoiceDurationMs()
                    && eq(ma.getReplyToText(), mb.getReplyToText())
                    && ma.isPending() == mb.isPending()
                    && ma.isFailed() == mb.isFailed()
                    && ma.isRead() == mb.isRead()
                    && a.showAvatar == b.showAvatar
                    && a.topPaddingDp == b.topPaddingDp
                    && reactionKey(ma).equals(reactionKey(mb));
        }
    };

    private static boolean sameIntro(ChatIntro a, ChatIntro b) {
        if (a == null || b == null) return a == b;
        return eq(a.fullName, b.fullName) && eq(a.username, b.username)
                && eq(a.avatarUrl, b.avatarUrl) && a.followers == b.followers
                && a.posts == b.posts && eq(a.followLine, b.followLine)
                && eq(a.mutualLine, b.mutualLine);
    }

    private static String reactionKey(Message m) {
        StringBuilder sb = new StringBuilder();
        for (MessageReaction r : m.getReactions()) {
            sb.append(r.getEmoji()).append(':').append(r.getUserId()).append('|');
        }
        return sb.toString();
    }

    private static boolean eq(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

    public static List<ChatRow> buildRows(ChatViewModel.ChatState state, String myId) {
        List<ChatRow> rows = new ArrayList<>();
        if (state == null) return rows;

        if (state.intro != null && !state.intro.isEmpty()) {
            rows.add(ChatRow.intro(state.intro));
        }

        List<Message> messages = state.messages;
        for (int i = 0; i < messages.size(); i++) {
            Message current = messages.get(i);
            Message previous = i > 0 ? messages.get(i - 1) : null;
            Message next = i + 1 < messages.size() ? messages.get(i + 1) : null;

            boolean separator = ChatViewModel.needsSeparator(previous, current);
            if (separator) {
                rows.add(ChatRow.separator(ChatViewModel.separatorLabel(current.getCreatedAt())));
            }

            boolean sent = current.getSenderId() != null && current.getSenderId().equals(myId);
            boolean sameSenderAsPrevious = previous != null
                    && current.getSenderId() != null
                    && current.getSenderId().equals(previous.getSenderId());
            boolean newGroup = previous == null || separator || !sameSenderAsPrevious;

            boolean nextContinues = next != null
                    && current.getSenderId() != null
                    && current.getSenderId().equals(next.getSenderId())
                    && !ChatViewModel.needsSeparator(current, next);
            boolean lastOfGroup = !nextContinues;

            int topPadding = separator ? 0 : (newGroup ? 16 : 4);
            rows.add(ChatRow.message(current, sent, !sent && lastOfGroup, topPadding));
        }

        if (!messages.isEmpty()) {
            Message last = messages.get(messages.size() - 1);
            boolean mine = last.getSenderId() != null && last.getSenderId().equals(myId);
            if (mine && last.isRead() && !last.isPending()) {
                rows.add(ChatRow.seen());
            }
        }
        return rows;
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case TYPE_INTRO:
                return new IntroHolder(ItemChatIntroBinding.inflate(inflater, parent, false));
            case TYPE_SEPARATOR:
                return new SeparatorHolder(ItemChatSeparatorBinding.inflate(inflater, parent, false));
            case TYPE_SEEN:
                return new SeenHolder(ItemChatSeenBinding.inflate(inflater, parent, false));
            case TYPE_SENT:
                return new SentHolder(ItemChatSentBinding.inflate(inflater, parent, false));
            default:
                return new ReceivedHolder(ItemChatReceivedBinding.inflate(inflater, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatRow row = getItem(position);
        if (holder instanceof IntroHolder) {
            holder.itemView.setOnLongClickListener(v -> { listener.onBackgroundLongPress(); return true; });
            bindIntro((IntroHolder) holder, row.intro);
        } else if (holder instanceof SeparatorHolder) {
            ((SeparatorHolder) holder).itemView.setOnLongClickListener(v -> { listener.onBackgroundLongPress(); return true; });
            ((SeparatorHolder) holder).binding.tvSeparator.setText(row.label);
        } else if (holder instanceof SentHolder) {
            SentHolder h = (SentHolder) holder;
            h.binding.rowRoot.setOnLongClickListener(v -> { listener.onBackgroundLongPress(); return true; });
            applyTopPadding(h.binding.rowRoot, row.topPaddingDp);
            bindSent(h, row);
        } else if (holder instanceof ReceivedHolder) {
            ReceivedHolder h = (ReceivedHolder) holder;
            h.binding.rowRoot.setOnLongClickListener(v -> { listener.onBackgroundLongPress(); return true; });
            applyTopPadding(h.binding.rowRoot, row.topPaddingDp);
            bindReceived(h, row);
        }
    }

    private void bindIntro(IntroHolder holder, ChatIntro intro) {
        ItemChatIntroBinding b = holder.binding;
        if (intro == null) return;
        GlideUtils.loadAvatar(b.ivIntroAvatar, intro.avatarUrl);
        b.tvIntroName.setText(intro.displayName());
        b.tvIntroUsername.setText(intro.username == null ? "" : intro.username);
        b.tvIntroStats.setText(com.insta.nova.util.Plural.followers(b.getRoot().getContext(), intro.followers)
                + " \u00B7 " + com.insta.nova.util.Plural.posts(b.getRoot().getContext(), intro.posts));

        if (intro.followLine == null || intro.followLine.isEmpty()) {
            b.tvIntroFollow.setVisibility(View.GONE);
        } else {
            b.tvIntroFollow.setVisibility(View.VISIBLE);
            b.tvIntroFollow.setText(intro.followLine);
        }
        if (intro.mutualLine == null || intro.mutualLine.isEmpty()) {
            b.tvIntroMutual.setVisibility(View.GONE);
        } else {
            b.tvIntroMutual.setVisibility(View.VISIBLE);
            b.tvIntroMutual.setText(intro.mutualLine);
        }
        b.btnViewProfile.setOnClickListener(v -> listener.onViewProfile());
    }

    private void bindSent(SentHolder h, ChatRow row) {
        ItemChatSentBinding b = h.binding;
        Message m = row.message;
        if (m == null) return;

        bindCommon(b.layoutBubble, b.tvMessage, b.ivMessageImage,
                b.layoutVoiceMessage, b.btnVoicePlay, b.progressVoice, b.tvVoiceDuration,
                b.layoutCallMessage, b.ivCallIcon, b.tvCallTitle, b.tvCallSubtitle,
                b.layoutQuotedReply, b.tvReplyUser, b.tvReplyText,
                b.ivError, b.layoutReactionPill, b.tvReactions, row, true);
    }

    private void bindReceived(ReceivedHolder h, ChatRow row) {
        ItemChatReceivedBinding b = h.binding;
        Message m = row.message;
        if (m == null) return;

        b.ivAvatar.setVisibility(row.showAvatar ? View.VISIBLE : View.INVISIBLE);
        if (row.showAvatar) {
            String avatar = otherAvatarUrl;
            if (avatar != null && !avatar.isEmpty()) GlideUtils.loadAvatar(b.ivAvatar, avatar);
            else b.ivAvatar.setImageResource(R.drawable.ic_person_placeholder);
        }

        bindCommon(b.layoutBubble, b.tvMessage, b.ivMessageImage,
                b.layoutVoiceMessage, b.btnVoicePlay, b.progressVoice, b.tvVoiceDuration,
                b.layoutCallMessage, b.ivCallIcon, b.tvCallTitle, b.tvCallSubtitle,
                b.layoutQuotedReply, b.tvReplyUser, b.tvReplyText,
                null, b.layoutReactionPill, b.tvReactions, row, false);
    }

    private void bindCommon(LinearLayout bubble, TextView text, ImageView image,
                            LinearLayout voiceLayout, ImageView voicePlay, ProgressBar voiceProgress, TextView voiceDuration,
                            LinearLayout callLayout, ImageView callIcon, TextView callTitle, TextView callSubtitle,
                            LinearLayout replyLayout, TextView replyUser, TextView replyText,
                            ImageView error, LinearLayout pill, TextView reactionText,
                            ChatRow row, boolean sent) {
        Message m = row.message;
        Context ctx = bubble.getContext();
        float radius = 22 * density;

        boolean isImage = m.hasImage();
        boolean isVoice = "voice".equals(m.getMessageType());
        boolean isCall = "audio_call".equals(m.getMessageType()) || "video_call".equals(m.getMessageType());
        String body = m.getText();
        boolean hasText = body != null && !body.trim().isEmpty() && !isVoice && !isCall;

        // Background styling
        if (isImage && !hasText) {
            bubble.setBackground(null);
            bubble.setPadding(0, 0, 0, 0);
        } else {
            if (currentTheme != null) {
                bubble.setBackground(sent
                        ? currentTheme.createSentBubbleDrawable(radius)
                        : currentTheme.createReceivedBubbleDrawable(radius));
            } else {
                bubble.setBackgroundResource(sent
                        ? R.drawable.bg_chat_bubble_sent : R.drawable.bg_chat_bubble_received);
            }
            int h = (int) (16 * density);
            int v = (int) (9 * density);
            bubble.setPadding(h, v, h, v);
        }

        // Quoted Reply Preview
        if (m.hasReply() && replyLayout != null) {
            replyLayout.setVisibility(View.VISIBLE);
            replyUser.setText("Replying to @" + (m.getReplyToSender() != null ? m.getReplyToSender() : "user"));
            replyText.setText(m.getReplyToText());
        } else if (replyLayout != null) {
            replyLayout.setVisibility(View.GONE);
        }

        // Image bubble with Instagram proportions
        if (isImage) {
            image.setVisibility(View.VISIBLE);
            int imageWidth = (int) (screenWidthPx * 0.70f);
            ViewGroup.LayoutParams lp = image.getLayoutParams();
            if (lp != null) {
                lp.width = imageWidth;
                image.setLayoutParams(lp);
            }
            image.setMaxWidth(imageWidth);
            image.setMaxHeight((int) (screenWidthPx * 0.95f));

            String local = m.getLocalImageUri();
            if (local != null && !local.isEmpty()) {
                GlideUtils.load(image, "chat", local);
            } else {
                GlideUtils.load(image, "chat", m.getImageUrl());
            }
            image.setOnClickListener(v -> listener.onImageClick(m));
        } else {
            image.setVisibility(View.GONE);
            image.setOnClickListener(null);
            image.setImageDrawable(null);
        }

        // Voice message bubble
        if (isVoice) {
            voiceLayout.setVisibility(View.VISIBLE);
            voiceDuration.setText(formatDuration(m.getVoiceDurationMs()));
            voicePlay.setOnClickListener(v -> listener.onVoicePlayClick(m, voiceProgress, voicePlay));
        } else {
            voiceLayout.setVisibility(View.GONE);
            voicePlay.setOnClickListener(null);
        }

        // Call message bubble
        //
        // Prompt J: the wording is derived from the terminal status of the
        // originating `calls` row, so both devices render the same entry from
        // the same source. The views themselves are unchanged — only the text
        // they carry varies by outcome.
        if (isCall) {
            callLayout.setVisibility(View.VISIBLE);
            boolean isVideo = "video_call".equals(m.getMessageType());
            String kind = isVideo ? "Video call" : "Audio call";
            callIcon.setImageResource(isVideo ? R.drawable.ic_video : R.drawable.ic_phone_call);

            long secs = m.getCallDurationSec();
            String outcome = m.getCallOutcome();

            if (CallRecord.STATUS_CONNECTED.equals(outcome)) {
                // "Audio call · 3:24"
                callTitle.setText(kind);
                callSubtitle.setText(formatCallDuration(secs));
            } else if (CallRecord.STATUS_MISSED.equals(outcome)
                    || CallRecord.STATUS_NO_ANSWER.equals(outcome)) {
                if (sent) {
                    // Caller side: the call was placed but never answered.
                    callTitle.setText(kind);
                    callSubtitle.setText(CallRecord.STATUS_NO_ANSWER.equals(outcome)
                            ? "No answer" : "Missed");
                } else {
                    // Callee side: "Missed audio call"
                    callTitle.setText("Missed " + kind.toLowerCase(Locale.US));
                    callSubtitle.setText("Missed call");
                }
            } else if (CallRecord.STATUS_DECLINED.equals(outcome)) {
                if (sent) {
                    callTitle.setText(kind);
                    callSubtitle.setText("Call declined");
                } else {
                    callTitle.setText(kind);
                    callSubtitle.setText("Declined");
                }
            } else {
                // 'ended' (hang-up or caller cancel) and legacy entries.
                callTitle.setText(kind);
                callSubtitle.setText(secs > 0 ? ("Call ended \u00B7 " + formatCallDuration(secs)) : "Call ended");
            }
        } else {
            callLayout.setVisibility(View.GONE);
        }

        // Text bubble
        if (hasText) {
            text.setVisibility(View.VISIBLE);
            text.setMaxWidth(maxTextWidthPx);

            // Handle Search Highlighting
            if (!searchQuery.isEmpty() && body.toLowerCase(Locale.US).contains(searchQuery)) {
                SpannableString spannable = new SpannableString(body);
                String lower = body.toLowerCase(Locale.US);
                int start = lower.indexOf(searchQuery);
                while (start >= 0) {
                    int end = start + searchQuery.length();
                    spannable.setSpan(new BackgroundColorSpan(Color.parseColor("#E6C200")),
                            start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    spannable.setSpan(new ForegroundColorSpan(Color.BLACK),
                            start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    start = lower.indexOf(searchQuery, end);
                }
                text.setText(spannable);
            } else {
                text.setText(body);
            }
        } else {
            text.setVisibility(View.GONE);
            text.setText("");
        }

        bubble.setAlpha(m.isPending() ? 0.6f : 1f);

        if (error != null) {
            error.setVisibility(m.isFailed() ? View.VISIBLE : View.GONE);
            error.setOnClickListener(m.isFailed() ? v -> listener.onRetry(m) : null);
        }

        // Reaction chip: pill overlapping bottom edge
        List<MessageReaction> reactions = m.getReactions();
        if (!reactions.isEmpty()) {
            pill.setVisibility(View.VISIBLE);
            reactionText.setText(reactionSummary(reactions));
            pill.setOnClickListener(v -> listener.onReactionClick(m));
            int extra = (int) (12 * density);
            View parent = (View) bubble.getParent();
            parent.setPadding(parent.getPaddingLeft(), parent.getPaddingTop(),
                    parent.getPaddingRight(), extra);
        } else {
            pill.setVisibility(View.GONE);
            pill.setOnClickListener(null);
            View parent = (View) bubble.getParent();
            if (parent.getPaddingBottom() != 0) {
                parent.setPadding(parent.getPaddingLeft(), parent.getPaddingTop(),
                        parent.getPaddingRight(), 0);
            }
        }

        bubble.setOnClickListener(v -> listener.onMessageClick(m));
        bubble.setOnLongClickListener(v -> {
            listener.onMessageLongPress(m, v);
            return true;
        });
        bubble.setOnTouchListener(new DoubleTapDetector(() -> listener.onMessageDoubleTap(m)));
    }

    static String reactionSummary(List<MessageReaction> reactions) {
        List<String> emojis = new ArrayList<>();
        for (MessageReaction r : reactions) {
            if (r == null || r.getEmoji() == null) continue;
            if (!emojis.contains(r.getEmoji())) emojis.add(r.getEmoji());
        }
        if (emojis.isEmpty()) return "";
        if (reactions.size() <= 2) {
            StringBuilder sb = new StringBuilder();
            for (String e : emojis) sb.append(e);
            return sb.toString();
        }
        return emojis.get(0) + " " + reactions.size();
    }

    private static String formatDuration(long ms) {
        if (ms <= 0) return "0:01";
        long totalSecs = ms / 1000;
        long mins = totalSecs / 60;
        long secs = totalSecs % 60;
        return String.format(Locale.US, "%d:%02d", mins, secs);
    }

    /** Prompt J — renders a call's real duration as {@code m:ss}. */
    private static String formatCallDuration(long secs) {
        if (secs <= 0) return "0:00";
        long mins = secs / 60;
        long rem = secs % 60;
        return String.format(Locale.US, "%d:%02d", mins, rem);
    }

    private void applyTopPadding(View root, int dp) {
        int px = (int) (dp * density);
        if (root.getPaddingTop() != px) {
            root.setPadding(root.getPaddingLeft(), px, root.getPaddingRight(), root.getPaddingBottom());
        }
    }

    static class IntroHolder extends RecyclerView.ViewHolder {
        final ItemChatIntroBinding binding;
        IntroHolder(ItemChatIntroBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    static class SeparatorHolder extends RecyclerView.ViewHolder {
        final ItemChatSeparatorBinding binding;
        SeparatorHolder(ItemChatSeparatorBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    static class SeenHolder extends RecyclerView.ViewHolder {
        final ItemChatSeenBinding binding;
        SeenHolder(ItemChatSeenBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    static class SentHolder extends RecyclerView.ViewHolder {
        final ItemChatSentBinding binding;
        SentHolder(ItemChatSentBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    static class ReceivedHolder extends RecyclerView.ViewHolder {
        final ItemChatReceivedBinding binding;
        ReceivedHolder(ItemChatReceivedBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
