package com.insta.nova.data;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wire models for the Nova backend. Field names mirror the verified schema exactly
 * (the only source of truth); nothing here invents a column.
 */
public final class NovaDtos {

    private NovaDtos() {
    }

    public static class Settings {
        @SerializedName("user_id") public String userId;
        @SerializedName("hide_toxic") public boolean hideToxic;
        @SerializedName("warn_before_post") public boolean warnBeforePost = true;
        @SerializedName("teen_safe") public boolean teenSafe;
        @SerializedName("data_saver") public String dataSaver = "auto";
        @SerializedName("language") public String language = "en";
        @SerializedName("intro_seen") public boolean introSeen;

        public Settings copy() {
            Settings s = new Settings();
            s.userId = userId;
            s.hideToxic = hideToxic;
            s.warnBeforePost = warnBeforePost;
            s.teenSafe = teenSafe;
            s.dataSaver = dataSaver;
            s.language = language;
            s.introSeen = introSeen;
            return s;
        }
    }

    public static class HubSummary {
        @SerializedName("unread_alerts") public int unreadAlerts;
        @SerializedName("is_donor") public boolean isDonor;
        @SerializedName("donor_district") public String donorDistrict;
        @SerializedName("sos_open") public int sosOpen;
        @SerializedName("sos_critical") public int sosCritical;
        @SerializedName("campus_joined") public boolean campusJoined;
        @SerializedName("campus_verified") public boolean campusVerified;
        @SerializedName("events_next_7d") public int eventsNext7d;
        @SerializedName("study_today_minutes") public int studyTodayMinutes;
        @SerializedName("safe_events_7d") public int safeEvents7d;
    }

    public static class SafetyStats {
        @SerializedName("total") public int total;
        @SerializedName("warned") public int warned;
        @SerializedName("blocked") public int blocked;
        @SerializedName("edited") public int edited;
        @SerializedName("posted_anyway") public int postedAnyway;
        @SerializedName("hidden") public int hidden;
        @SerializedName("kindness_score") public int kindnessScore;
        @SerializedName("by_category") public Map<String, Integer> byCategory = new HashMap<>();
    }

    public static class BlockedUser {
        @SerializedName("user_id") public String userId;
        @SerializedName("username") public String username;
        @SerializedName("avatar_url") public String avatarUrl;
        @SerializedName("created_at") public String createdAt;
    }

    public static class MutedUser {
        @SerializedName("target_id") public String targetId;
        @SerializedName("mute_posts") public boolean mutePosts;
        @SerializedName("mute_stories") public boolean muteStories;
        public transient String username;
        public transient String avatarUrl;
    }

    public static class District {
        @SerializedName("name") public String name;
        @SerializedName("division") public String division;
    }

    public static class Donor {
        @SerializedName("user_id") public String userId;
        @SerializedName("blood_group") public String bloodGroup;
        @SerializedName("district") public String district;
        @SerializedName("area") public String area;
        @SerializedName("available") public boolean available = true;
        @SerializedName("last_donation_date") public String lastDonationDate;
        @SerializedName("donations_count") public int donationsCount;
    }

    public static class BloodRequest {
        @SerializedName("id") public String id;
        @SerializedName("requester_id") public String requesterId;
        @SerializedName("requester_username") public String requesterUsername;
        @SerializedName("requester_avatar_url") public String requesterAvatarUrl;
        @SerializedName("blood_group") public String bloodGroup;
        @SerializedName("units_needed") public int unitsNeeded;
        @SerializedName("patient_name") public String patientName;
        @SerializedName("hospital") public String hospital;
        @SerializedName("district") public String district;
        @SerializedName("area") public String area;
        @SerializedName("needed_by") public String neededBy;
        @SerializedName("urgency") public String urgency;
        @SerializedName("notes") public String notes;
        /** Optional photo attached when the request was created. */
        @SerializedName("photo_url") public String photoUrl;
        @SerializedName("status") public String status;
        @SerializedName("created_at") public String createdAt;
        @SerializedName("pledged_count") public int pledgedCount;
        @SerializedName("donated_count") public int donatedCount;
        @SerializedName("my_pledge") public boolean myPledge;
        @SerializedName("is_mine") public boolean isMine;
        @SerializedName("can_donate") public boolean canDonate;
    }

    public static class Pledge {
        @SerializedName("donor_id") public String donorId;
        @SerializedName("username") public String username;
        @SerializedName("avatar_url") public String avatarUrl;
        @SerializedName("blood_group") public String bloodGroup;
        @SerializedName("status") public String status;
        @SerializedName("created_at") public String createdAt;
    }

    public static class DonorBadge {
        @SerializedName("is_donor") public boolean isDonor;
        @SerializedName("donations") public int donations;
    }

    public static class Alert {
        @SerializedName("id") public String id;
        @SerializedName("user_id") public String userId;
        @SerializedName("kind") public String kind;
        @SerializedName("title") public String title;
        @SerializedName("body") public String body;
        @SerializedName("ref_id") public String refId;
        @SerializedName("is_read") public boolean isRead;
        @SerializedName("created_at") public String createdAt;
    }

    public static class University {
        @SerializedName("id") public int id;
        @SerializedName("name") public String name;
        @SerializedName("short_name") public String shortName;
        @SerializedName("email_domains") public List<String> emailDomains = new ArrayList<>();
    }

    public static class CampusMembership {
        @SerializedName("user_id") public String userId;
        @SerializedName("university_id") public int universityId;
        @SerializedName("department") public String department;
        @SerializedName("batch") public String batch;
        @SerializedName("verified") public boolean verified;
        public transient String universityName;
    }

    public static class CampusJoinResult {
        @SerializedName("university_id") public int universityId;
        @SerializedName("verified") public boolean verified;
    }

    public static class EventItem {
        @SerializedName("id") public String id;
        @SerializedName("university_id") public int universityId;
        @SerializedName("author_id") public String authorId;
        @SerializedName("title") public String title;
        @SerializedName("details") public String details;
        @SerializedName("venue") public String venue;
        @SerializedName("starts_at") public String startsAt;
        @SerializedName("ends_at") public String endsAt;
        @SerializedName("banner_url") public String bannerUrl;
        @SerializedName("category") public String category;
        @SerializedName("created_at") public String createdAt;
        @SerializedName("author_username") public String authorUsername;
        @SerializedName("author_avatar_url") public String authorAvatarUrl;
        @SerializedName("going_count") public int goingCount;
        @SerializedName("interested_count") public int interestedCount;
        @SerializedName("my_rsvp") public String myRsvp;
        @SerializedName("is_mine") public boolean isMine;
    }

    public static class LostFoundItem {
        @SerializedName("id") public String id;
        @SerializedName("university_id") public int universityId;
        @SerializedName("author_id") public String authorId;
        @SerializedName("kind") public String kind;
        @SerializedName("title") public String title;
        @SerializedName("details") public String details;
        @SerializedName("place") public String place;
        @SerializedName("photo_url") public String photoUrl;
        @SerializedName("event_date") public String eventDate;
        @SerializedName("status") public String status;
        @SerializedName("created_at") public String createdAt;
        @SerializedName("author_username") public String authorUsername;
        @SerializedName("author_avatar_url") public String authorAvatarUrl;
        @SerializedName("author_verified") public boolean authorVerified;
        @SerializedName("is_mine") public boolean isMine;
        /** Contact number the reporter wants to be reachable on; required on new reports. */
        @SerializedName("contact") public String contact;
    }

    public static class Confession {
        @SerializedName("id") public String id;
        @SerializedName("body") public String body;
        @SerializedName("created_at") public String createdAt;
        @SerializedName("likes_count") public int likesCount;
        @SerializedName("liked_by_me") public boolean likedByMe;
        @SerializedName("is_mine") public boolean isMine;
        /** Optional picture posted with the confession. */
        @SerializedName("photo_url") public String photoUrl;
    }

    public static class StudyStats {
        @SerializedName("today_minutes") public int todayMinutes;
        @SerializedName("week_minutes") public int weekMinutes;
        @SerializedName("total_minutes") public int totalMinutes;
        @SerializedName("total_sessions") public int totalSessions;
        @SerializedName("current_streak") public int currentStreak;
        @SerializedName("best_streak") public int bestStreak;
        @SerializedName("daily") public List<StudyDay> daily = new ArrayList<>();
    }

    public static class StudyDay {
        @SerializedName("day") public String day;
        @SerializedName("minutes") public int minutes;
    }

    public static class LeaderRow {
        @SerializedName("user_id") public String userId;
        @SerializedName("username") public String username;
        @SerializedName("avatar_url") public String avatarUrl;
        @SerializedName("minutes") public int minutes;
        @SerializedName("rank") public int rank;
        @SerializedName("is_me") public boolean isMe;
    }

    public static class Question {
        @SerializedName("id") public String id;
        @SerializedName("author_id") public String authorId;
        @SerializedName("subject") public String subject;
        @SerializedName("title") public String title;
        @SerializedName("body") public String body;
        @SerializedName("image_url") public String imageUrl;
        @SerializedName("solved") public boolean solved;
        @SerializedName("answers_count") public int answersCount;
        @SerializedName("votes") public int votes;
        @SerializedName("created_at") public String createdAt;
        @SerializedName("author_username") public String authorUsername;
        @SerializedName("author_avatar_url") public String authorAvatarUrl;
        @SerializedName("voted_by_me") public boolean votedByMe;
        @SerializedName("is_mine") public boolean isMine;
    }

    public static class Answer {
        @SerializedName("id") public String id;
        @SerializedName("question_id") public String questionId;
        @SerializedName("author_id") public String authorId;
        @SerializedName("body") public String body;
        @SerializedName("votes") public int votes;
        @SerializedName("accepted") public boolean accepted;
        @SerializedName("created_at") public String createdAt;
        @SerializedName("author_username") public String authorUsername;
        @SerializedName("author_avatar_url") public String authorAvatarUrl;
        @SerializedName("voted_by_me") public boolean votedByMe;
        @SerializedName("is_mine") public boolean isMine;
    }

    public static class Insights {
        @SerializedName("days") public List<InsightDay> days = new ArrayList<>();
        @SerializedName("totals") public InsightTotals totals;
        @SerializedName("top_post") public TopPost topPost;
        @SerializedName("best_hour") public int bestHour;
    }

    public static class InsightDay {
        @SerializedName("day") public String day;
        @SerializedName("likes") public int likes;
        @SerializedName("comments") public int comments;
        @SerializedName("followers") public int followers;
        @SerializedName("posts") public int posts;
    }

    public static class InsightTotals {
        @SerializedName("likes") public int likes;
        @SerializedName("comments") public int comments;
        @SerializedName("new_followers") public int newFollowers;
        @SerializedName("posts") public int posts;
        @SerializedName("followers_total") public int followersTotal;
    }

    public static class TopPost {
        @SerializedName("id") public String id;
        @SerializedName("caption") public String caption;
        @SerializedName("likes") public int likes;
        @SerializedName("comments") public int comments;
        @SerializedName("thumb") public String thumb;
    }

    public static class CreateRequestResult {
        @SerializedName("id") public String id;
        @SerializedName("matched") public int matched;
    }

    public static class PledgeResult {
        @SerializedName("status") public String status;
        @SerializedName("phone") public String phone;
    }

    public static class ConfirmResult {
        @SerializedName("donated") public boolean donated;
        @SerializedName("fulfilled") public boolean fulfilled;
    }

    public static class VoteResult {
        @SerializedName("voted") public boolean voted;
        @SerializedName("votes") public int votes;
    }

    public static class AcceptResult {
        @SerializedName("accepted") public boolean accepted;
    }

    public static class CountResult {
        @SerializedName("count") public int count;
    }

    /* ------------------------------------------------------------------ */
    /* Reddit-style posts (nova_posts_public / nova_comments_public)      */
    /* ------------------------------------------------------------------ */

    /**
     * One row of nova_posts_public.
     *
     * <p>{@code authorId} is null for an anonymous post: the server never sends the
     * real id, it sends the per-post random alias instead. {@code isMine} still
     * resolves for the author, so their own anonymous posts are listed under
     * "Mine" with an Anonymous badge.
     */
    // Serializable so the feed can hand a card straight to the detail screen,
    // which then renders it immediately instead of showing an empty header
    // while the thread loads.
    public static class NovaPost implements java.io.Serializable {
        private static final long serialVersionUID = 1L;

        @SerializedName("id") public String id;
        @SerializedName("author_id") public String authorId;
        @SerializedName("alias") public String alias;
        @SerializedName("avatar_url") public String avatarUrl;
        @SerializedName("full_name") public String fullName;
        @SerializedName("body") public String body;
        @SerializedName("media_url") public String mediaUrl;
        @SerializedName("media_type") public String mediaType = "text";
        @SerializedName("content_type") public String contentType = "all";
        @SerializedName("flair") public String flair;
        @SerializedName("is_anonymous") public boolean isAnonymous;
        @SerializedName("expires_at") public String expiresAt;
        @SerializedName("duration_seconds") public Integer durationSeconds;
        @SerializedName("score") public int score;
        @SerializedName("upvote_count") public int upvoteCount;
        @SerializedName("downvote_count") public int downvoteCount;
        @SerializedName("comment_count") public int commentCount;
        @SerializedName("poll_options") public java.util.List<String> pollOptions;
        @SerializedName("poll_votes") public java.util.List<Integer> pollVotes;
        /** Option this account picked in a poll; null until it votes or is loaded. */
        public Integer myPollVote;
        @SerializedName("created_at") public String createdAt;
        @SerializedName("is_mine") public boolean isMine;
        @SerializedName("my_alias") public String myAlias;
        @SerializedName("my_vote") public int myVote;
        @SerializedName("is_saved") public boolean isSaved;
        @SerializedName("hot_rank") public double hotRank;

        public long createdAtMillis() {
            return com.insta.nova.util.TimeUtils.parseTimestamp(createdAt);
        }

        public long expiresAtMillis() {
            return com.insta.nova.util.TimeUtils.parseTimestamp(expiresAt);
        }

        /** True when the post has an expiry moment at all. */
        public boolean hasExpiry() {
            return expiresAt != null && !expiresAt.trim().isEmpty();
        }

        /** True when the expiry moment has passed. */
        public boolean isExpired(long nowMillis) {
            return hasExpiry() && expiresAtMillis() <= nowMillis;
        }

        public boolean isPhoto() {
            return "photo".equalsIgnoreCase(mediaType);
        }

        public boolean isPoll() {
            return "poll".equalsIgnoreCase(mediaType);
        }

        public boolean isReel() {
            return "reel".equalsIgnoreCase(mediaType);
        }

        /**
         * The fade the card uses: clamp(remaining / total, 0.4, 1.0).
         *
         * <p>When the window is unknown (a post written before the column existed)
         * the post is treated as fresh, so it never fades for the wrong reason.
         */
        public float fadeAlpha(long nowMillis) {
            if (!hasExpiry()) return 1f;
            long total = durationSeconds == null ? 0L : durationSeconds.longValue() * 1000L;
            if (total <= 0L) return 1f;
            long remaining = expiresAtMillis() - nowMillis;
            if (remaining <= 0L) return 0.4f;
            float ratio = (float) remaining / (float) total;
            return Math.max(0.4f, Math.min(1f, ratio));
        }
    }

    /** One row of nova_comments_public. Same masking rules as NovaPost. */
    public static class NovaComment {
        @SerializedName("id") public String id;
        @SerializedName("post_id") public String postId;
        @SerializedName("parent_id") public String parentId;
        @SerializedName("author_id") public String authorId;
        @SerializedName("alias") public String alias;
        @SerializedName("avatar_url") public String avatarUrl;
        @SerializedName("body") public String body;
        @SerializedName("is_anonymous") public boolean isAnonymous;
        @SerializedName("score") public int score;
        @SerializedName("upvote_count") public int upvoteCount;
        @SerializedName("downvote_count") public int downvoteCount;
        @SerializedName("created_at") public String createdAt;
        @SerializedName("is_mine") public boolean isMine;
        @SerializedName("my_vote") public int myVote;
        /**
         * True when the author is restricted by the post owner. The public view only
         * returns a held comment to its author and to the post owner, so seeing one at
         * all means this screen is allowed to know it exists.
         */
        @SerializedName("is_held") public boolean isHeld;

        public long createdAtMillis() {
            return com.insta.nova.util.TimeUtils.parseTimestamp(createdAt);
        }

        public boolean isReply() {
            return parentId != null && !parentId.trim().isEmpty();
        }
    }

    /** The result of cast_vote / cast_comment_vote. */
    public static class PollOutcome {
        @SerializedName("counts") public java.util.List<Integer> counts;
        @SerializedName("my_vote") public Integer myVote;
        @SerializedName("total") public Integer total;
    }

    public static class VoteOutcome {
        @SerializedName("my_vote") public int myVote;
        @SerializedName("score") public int score;
        @SerializedName("upvote_count") public int upvoteCount;
        @SerializedName("downvote_count") public int downvoteCount;
    }

    /** The result of create_nova_post / create_nova_comment. */
    public static class CreatedRef {
        @SerializedName("id") public String id;
        @SerializedName("author_id") public String authorId;
        @SerializedName("alias") public String alias;
        @SerializedName("is_anonymous") public boolean isAnonymous;
        @SerializedName("expires_at") public String expiresAt;
        @SerializedName("anonymous_alias") public String anonymousAlias;
    }
}
