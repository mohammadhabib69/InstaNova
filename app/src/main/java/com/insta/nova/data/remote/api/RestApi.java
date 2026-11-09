package com.insta.nova.data.remote.api;

import com.google.gson.JsonElement;
import com.insta.nova.data.remote.dto.CommentDto;
import com.insta.nova.data.remote.dto.ConversationDto;
import com.insta.nova.data.remote.dto.MessageDto;
import com.insta.nova.data.remote.dto.NotificationDto;
import com.insta.nova.data.remote.dto.PostDto;
import com.insta.nova.data.remote.dto.ProfileDto;
import com.insta.nova.data.remote.dto.RpcEmailRequest;
import com.insta.nova.data.remote.dto.RpcSuggestedRequest;
import com.insta.nova.data.remote.dto.StoryDto;
import com.insta.nova.data.remote.dto.StoryGroupDto;
import com.insta.nova.data.remote.dto.UnreadCountsDto;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Query;
import retrofit2.http.QueryMap;

/**
 * Supabase PostgREST and RPC API endpoints.
 */
public interface RestApi {

    // RPCs
    @POST("rest/v1/rpc/get_email_by_username")
    Call<JsonElement> getEmailByUsername(@Body RpcEmailRequest request);

    @POST("rest/v1/rpc/is_username_available")
    Call<Boolean> isUsernameAvailable(@Body RpcEmailRequest request);

    /**
     * Records the signed-in account's address against its profile, which is what makes
     * the username -> email lookup possible on a later sign-in. Safe to call repeatedly.
     */
    @POST("rest/v1/rpc/set_my_email")
    Call<Void> setMyEmail(@Body Map<String, String> body);

    @POST("rest/v1/rpc/get_story_groups")
    Call<List<StoryGroupDto>> getStoryGroups(@Body Map<String, Object> body);

    /**
     * True when the signed-in account is restricted by the owner of {@code p_post}.
     * Asked before writing an Instagram comment so the row can be created held, which
     * is what the Firestore rules then use to hide it from everyone else.
     */
    /**
     * A story reaction is a notification, not a message. Kept as its own endpoint so
     * the two can never be wired together again by accident.
     */
    @POST("rest/v1/rpc/react_to_story")
    Call<Void> reactToStory(@Body Map<String, Object> body);

    /** Replaces the tag set on one of my stories. Enforces the tagged account's tag_who. */
    @POST("rest/v1/rpc/story_poll_vote")
    Call<Void> voteStoryPoll(@Body java.util.Map<String, Object> body);

    @POST("rest/v1/rpc/story_poll_results")
    Call<java.util.List<java.util.Map<String, Object>>> storyPollResults(@Body java.util.Map<String, Object> body);

    @POST("rest/v1/rpc/story_poll_mine")
    Call<Integer> storyPollMine(@Body java.util.Map<String, Object> body);

    @POST("rest/v1/rpc/set_story_tags")
    Call<Void> setStoryTags(@Body Map<String, Object> body);

    /** Replaces the tag set on one of my posts. Enforces the tagged account's tag_who. */
    @POST("rest/v1/rpc/set_post_tags")
    Call<Void> setPostTags(@Body Map<String, Object> body);

    /**
     * Whether the signed-in account may comment on this post, per the owner's
     * Settings > Comment controls. Instagram comments live in Firestore, which cannot
     * read Postgres inside a rule, so the client asks before writing.
     */
    @POST("rest/v1/rpc/can_comment_on_post")
    Call<Boolean> canCommentOnPost(@Body Map<String, Object> body);

    @POST("rest/v1/rpc/am_i_restricted_by_post_owner")
    Call<Boolean> amIRestrictedByPostOwner(@Body Map<String, Object> body);

    @POST("rest/v1/rpc/get_suggested_users")
    Call<List<ProfileDto>> getSuggestedUsers(@Body RpcSuggestedRequest request);

    /** PAGE 18 — suggestions by raw limit map. */
    @POST("rest/v1/rpc/get_suggested_users")
    Call<List<ProfileDto>> getSuggestedUsersRaw(@Body Map<String, Object> body);

    @POST("rest/v1/rpc/get_or_create_conversation")
    Call<JsonElement> getOrCreateConversation(@Body Map<String, Object> request);

    @POST("rest/v1/rpc/get_inbox")
    Call<List<ConversationDto>> getInbox(@Body Map<String, Object> body);

    /** #55 — moves a pending request thread into the caller's inbox. */
    @POST("rest/v1/rpc/accept_message_request")
    Call<Void> acceptMessageRequest(@Body Map<String, Object> body);

    /** #55 — hides a pending request thread from both the inbox and the requests list. */
    @POST("rest/v1/rpc/decline_message_request")
    Call<Void> declineMessageRequest(@Body Map<String, Object> body);

    @POST("rest/v1/rpc/set_account_privacy")
    Call<Void> setAccountPrivacy(@Body Map<String, Object> body);

    @POST("rest/v1/rpc/approve_follow_request")
    Call<Void> approveFollowRequest(@Body Map<String, Object> body);

    /** Private-aware follow: returns "following", "requested" or "self". */
    @POST("rest/v1/rpc/follow_user")
    Call<com.google.gson.JsonElement> followUserRpc(@Body Map<String, Object> body);

    /** Deletes the caller's profiles row (all owned rows cascade). */
    @POST("rest/v1/rpc/delete_my_account")
    Call<Void> deleteMyAccount(@Body Map<String, Object> body);

    @GET("rest/v1/follow_requests")
    Call<List<Map<String, Object>>> getFollowRequests(@QueryMap Map<String, String> query);

    @DELETE("rest/v1/follow_requests")
    Call<Void> deleteFollowRequest(@QueryMap Map<String, String> query);

    @GET("rest/v1/blocks")
    Call<List<Map<String, Object>>> getBlocks(@QueryMap Map<String, String> query);

    @GET("rest/v1/nova_blocks")
    Call<List<Map<String, Object>>> getNovaBlocks(@QueryMap Map<String, String> query);

    @Headers("Prefer: resolution=ignore-duplicates,return=minimal")
    @POST("rest/v1/blocks")
    Call<Void> addBlock(@Body Map<String, Object> body);

    @DELETE("rest/v1/blocks")
    Call<Void> deleteBlock(@QueryMap Map<String, String> query);

    @GET("rest/v1/restricted_users")
    Call<List<Map<String, Object>>> getRestricted(@QueryMap Map<String, String> query);

    @Headers("Prefer: resolution=ignore-duplicates,return=minimal")
    @POST("rest/v1/restricted_users")
    Call<Void> addRestricted(@Body Map<String, Object> body);

    @DELETE("rest/v1/restricted_users")
    Call<Void> deleteRestricted(@QueryMap Map<String, String> query);

    @GET("rest/v1/close_friends")
    Call<List<Map<String, Object>>> getCloseFriends(@QueryMap Map<String, String> query);

    @POST("rest/v1/close_friends")
    Call<Void> addCloseFriend(@Body Map<String, Object> body);

    @DELETE("rest/v1/close_friends")
    Call<Void> deleteCloseFriend(@QueryMap Map<String, String> query);

    @GET("rest/v1/story_audience")
    Call<List<Map<String, Object>>> getStoryAudience(@QueryMap Map<String, String> query);

    @Headers("Prefer: resolution=ignore-duplicates,return=minimal")
    @POST("rest/v1/story_audience")
    Call<Void> addStoryAudience(@Body Map<String, Object> body);

    @DELETE("rest/v1/story_audience")
    Call<Void> deleteStoryAudience(@QueryMap Map<String, String> query);

    @GET("rest/v1/post_tags")
    Call<List<Map<String, Object>>> getPostTags(@QueryMap Map<String, String> query);

    /** The same rows typed, for the feed's tap-to-see markers. */
    @GET("rest/v1/post_tags")
    Call<List<com.insta.nova.data.remote.dto.PostTagDto>> getPostTagMarkers(
            @QueryMap Map<String, String> query);

    @Headers("Prefer: return=minimal")
    @POST("rest/v1/reports")
    Call<Void> submitReport(@Body Map<String, Object> body);

    @PATCH("rest/v1/posts")
    Call<Void> updatePost(@Query("id") String idQuery, @Body Map<String, Object> body);

    @GET("rest/v1/profiles")
    Call<List<Map<String, Object>>> getProfilesRaw(@QueryMap Map<String, String> query);

    @POST("rest/v1/rpc/get_unread_counts")
    Call<UnreadCountsDto> getUnreadCounts(@Body Map<String, Object> body);

    // PAGE 17 — presence + mutual followings (database patch required)
    @POST("rest/v1/rpc/touch_active")
    Call<Void> touchActive(@Body Map<String, Object> body);

    /** PAGE 18 — people I follow who also follow {@code p_user}. */
    @POST("rest/v1/rpc/followed_by_preview")
    Call<JsonElement> followedByPreview(@Body Map<String, Object> body);

    @POST("rest/v1/rpc/mutual_followings")
    Call<List<ProfileDto>> mutualFollowings(@Body Map<String, Object> body);

    // Profiles
    @GET("rest/v1/profiles")
    Call<List<ProfileDto>> getProfiles(@QueryMap Map<String, String> query);

    /**
     * {@code Prefer: return=representation} compiles to {@code RETURNING *}, so the write
     * echo is narrowed with an explicit {@code select}. Callers pass
     * {@link com.insta.nova.data.remote.ProfileColumns#WRITE_ECHO}.
     */
    @Headers("Prefer: return=representation")
    @POST("rest/v1/profiles")
    Call<List<ProfileDto>> createProfile(@Query("select") String select, @Body Map<String, Object> body);

    @Headers("Prefer: return=representation")
    @PATCH("rest/v1/profiles")
    Call<List<ProfileDto>> updateProfile(@Query("id") String idQuery,
                                         @Query("select") String select,
                                         @Body Map<String, Object> body);

    // Posts & Feed Views
    @GET("rest/v1/v_feed")
    Call<List<PostDto>> getFeed(@QueryMap Map<String, String> query);

    @GET("rest/v1/v_posts")
    Call<List<PostDto>> getPosts(@QueryMap Map<String, String> query);

    @GET("rest/v1/v_saved")
    Call<List<PostDto>> getSavedPosts(@QueryMap Map<String, String> query);

    @Headers("Prefer: return=representation")
    @POST("rest/v1/posts")
    Call<List<PostDto>> createPost(@Body Map<String, Object> body);

    @DELETE("rest/v1/posts")
    Call<Void> deletePost(@Query("id") String idQuery);

    @POST("rest/v1/post_media")
    Call<Void> insertPostMedia(@Body List<Map<String, Object>> mediaItems);

    // Likes & Saves
    // ignore-duplicates + on_conflict makes a repeated like a no-op instead of a 23505.
    @Headers("Prefer: resolution=ignore-duplicates")
    @POST("rest/v1/likes")
    Call<Void> likePost(@Query("on_conflict") String onConflict, @Body Map<String, Object> body);

    @DELETE("rest/v1/likes")
    Call<Void> unlikePost(@Query("post_id") String postIdQuery, @Query("user_id") String userIdQuery);

    // Same idempotency contract as likes/follows: re-saving an already saved post is a
    // no-op instead of a 23505 that the UI would report as a failure.
    @Headers("Prefer: resolution=ignore-duplicates")
    @POST("rest/v1/saved_posts")
    Call<Void> savePost(@Query("on_conflict") String onConflict, @Body Map<String, Object> body);

    @DELETE("rest/v1/saved_posts")
    Call<Void> unsavePost(@Query("post_id") String postIdQuery, @Query("user_id") String userIdQuery);

    // Comments
    @GET("rest/v1/v_comments")
    Call<List<CommentDto>> getComments(@Query("post_id") String postIdQuery, @Query("order") String order);

    @GET("rest/v1/v_comments")
    Call<List<CommentDto>> getCommentsByUser(@QueryMap Map<String, String> query);

    @Headers("Prefer: return=representation")
    @POST("rest/v1/comments")
    Call<List<CommentDto>> addComment(@Body Map<String, Object> body);

    @DELETE("rest/v1/comments")
    Call<Void> deleteComment(@Query("id") String idQuery);

    @GET("rest/v1/comments")
    Call<List<Map<String, Object>>> getCommentsRaw(@QueryMap Map<String, String> query);

    @POST("rest/v1/comment_likes")
    Call<Void> likeComment(@Body Map<String, Object> body);

    @DELETE("rest/v1/comment_likes")
    Call<Void> unlikeComment(@Query("comment_id") String commentIdQuery, @Query("user_id") String userIdQuery);

    // Follows
    // Same guard as likes: following twice must not surface a duplicate-key error.
    @Headers("Prefer: resolution=ignore-duplicates")
    @POST("rest/v1/follows")
    Call<Void> followUser(@Query("on_conflict") String onConflict, @Body Map<String, Object> body);

    @DELETE("rest/v1/follows")
    Call<Void> unfollowUser(@Query("follower_id") String followerIdQuery, @Query("following_id") String followingIdQuery);

    @GET("rest/v1/follows")
    Call<List<Map<String, Object>>> getFollows(@QueryMap Map<String, String> query);

    /** PostgREST resource embedding: likes joined to the liker's profile. */
    @GET("rest/v1/likes")
    Call<List<Map<String, Object>>> getLikes(@QueryMap Map<String, String> query);

    // Stories & Views
    @GET("rest/v1/v_stories")
    Call<List<StoryDto>> getStories(@QueryMap Map<String, String> query);

    /** Own stories that were archived or have expired — the Archive tab's source. */
    @GET("rest/v1/v_story_archive")
    Call<List<StoryDto>> getArchivedStories(@QueryMap Map<String, String> query);

    @Headers("Prefer: return=representation")
    @POST("rest/v1/stories")
    Call<List<StoryDto>> createStory(@Body Map<String, Object> body);

    @DELETE("rest/v1/stories")
    Call<Void> deleteStory(@Query("id") String idQuery);

    @PATCH("rest/v1/stories")
    Call<Void> updateStory(@Query("id") String idQuery, @Body Map<String, Object> body);

    @POST("rest/v1/story_views")
    Call<Void> recordStoryView(@Body Map<String, Object> body);

    @GET("rest/v1/story_views")
    Call<List<Map<String, Object>>> getStoryViews(@QueryMap Map<String, String> query);

    // Highlights
    @GET("rest/v1/highlights")
    Call<List<Map<String, Object>>> getHighlights(@QueryMap Map<String, String> query);

    @Headers("Prefer: return=representation")
    @POST("rest/v1/highlights")
    Call<List<Map<String, Object>>> createHighlight(@Body Map<String, Object> body);

    @DELETE("rest/v1/highlights")
    Call<Void> deleteHighlight(@Query("id") String idQuery);

    @PATCH("rest/v1/highlights")
    Call<Void> updateHighlight(@Query("id") String idQuery, @Body Map<String, Object> body);

    @GET("rest/v1/highlight_items")
    Call<List<Map<String, Object>>> getHighlightItems(@QueryMap Map<String, String> query);

    @POST("rest/v1/highlight_items")
    Call<Void> createHighlightItems(@Body List<Map<String, Object>> items);

    @DELETE("rest/v1/highlight_items")
    Call<Void> deleteHighlightItem(@Query("id") String idQuery);

    // Direct Messages
    @GET("rest/v1/messages")
    Call<List<MessageDto>> getMessages(@QueryMap Map<String, String> query);

    @Headers("Prefer: return=representation")
    @POST("rest/v1/messages")
    Call<List<MessageDto>> sendMessage(@Body Map<String, Object> body);

    @PATCH("rest/v1/messages")
    Call<Void> markMessagesRead(@QueryMap Map<String, String> query, @Body Map<String, Object> body);

    @DELETE("rest/v1/messages")
    Call<Void> deleteMessage(@Query("id") String idQuery);

    // PAGE 17 — capability probe for the reactions table (404 when the patch is absent)
    @GET("rest/v1/message_reactions")
    Call<List<Map<String, Object>>> probeReactions(@Query("select") String select,
                                                   @Query("limit") String limit);

    // PAGE 17 — message reactions: one row per (message_id, user_id)
    @Headers("Prefer: resolution=merge-duplicates,return=minimal")
    @POST("rest/v1/message_reactions")
    Call<Void> upsertReaction(@Body Map<String, Object> body);

    @DELETE("rest/v1/message_reactions")
    Call<Void> deleteReaction(@Query("message_id") String messageIdQuery,
                              @Query("user_id") String userIdQuery);

    // Notifications
    @GET("rest/v1/notifications")
    Call<List<NotificationDto>> getNotifications(@QueryMap Map<String, String> query);

    @PATCH("rest/v1/notifications")
    Call<Void> markNotificationsRead(@Query("user_id") String userIdQuery, @Query("read") String readQuery, @Body Map<String, Object> body);

    // Calling System — Sessions
    @GET("rest/v1/call_sessions")
    Call<List<com.insta.nova.data.remote.dto.CallSessionDto>> getCallSessions(@QueryMap Map<String, String> query);

    @Headers("Prefer: return=representation")
    @POST("rest/v1/call_sessions")
    Call<List<com.insta.nova.data.remote.dto.CallSessionDto>> createCallSession(@Body Map<String, Object> body);

    @PATCH("rest/v1/call_sessions")
    Call<Void> updateCallSession(@Query("id") String idQuery, @Body Map<String, Object> body);

    // Calling System — Signals
    @GET("rest/v1/call_signals")
    Call<List<com.insta.nova.data.remote.dto.CallSignalDto>> getCallSignals(@QueryMap Map<String, String> query);

    @POST("rest/v1/call_signals")
    Call<Void> sendCallSignal(@Body Map<String, Object> body);

    // Calling System — FCM Devices
    @Headers("Prefer: resolution=merge-duplicates")
    @POST("rest/v1/user_devices")
    Call<Void> registerDeviceToken(@Body Map<String, Object> body);

    // Calling System — Push Dispatcher
    // Prompt K: send-call-push below is the only call push endpoint.

    // ========================================================================
    // Prompt J — public.calls (the authoritative call record + state machine)
    //
    // Distinct from call_sessions above: `calls` carries the two distinct
    // pre-answer states ('calling' vs 'ringing') that the ack rule depends on,
    // and is the only table published to Supabase Realtime.
    // ========================================================================

    @GET("rest/v1/calls")
    Call<List<com.insta.nova.calling.CallRecord>> getCalls(@QueryMap Map<String, String> query);

    @Headers("Prefer: return=representation")
    @POST("rest/v1/calls")
    Call<List<com.insta.nova.calling.CallRecord>> createCall(@Body Map<String, Object> body);

    @PATCH("rest/v1/calls")
    Call<Void> updateCall(@Query("id") String idQuery, @Body Map<String, Object> body);

    /**
     * Prompt M — conditional terminal update (write-once terminal state).
     *
     * Only updates the row if it is currently in a non-terminal status
     * and ended_by is still null. If the row is already terminal, PostgREST
     * filters it out and updates 0 rows without modifying anything.
     */
    @PATCH("rest/v1/calls")
    Call<Void> terminateCall(@Query("id") String idQuery,
                             @Query("status") String statusFilter,
                             @Query("ended_by") String endedByFilter,
                             @Body Map<String, Object> body);

    /**
     * Prompt M — conditional accept update. Only applies if call is calling or ringing.
     */
    @PATCH("rest/v1/calls")
    Call<Void> acceptCall(@Query("id") String idQuery,
                          @Query("status") String statusFilter,
                          @Body Map<String, Object> body);

    @GET("rest/v1/calls")
    Call<List<com.insta.nova.calling.CallRecord>> getTerminalCalleeCalls(@QueryMap Map<String, String> query);

    /**
     * Closes a call row that was left pending, filtered by status only.
     *
     * <p>{@link #terminateCall} also filters {@code ended_by=is.null} because a normal
     * hang-up must be write-once. A leaked row can already carry {@code ended_by} and
     * {@code ended_at} while its status is still {@code ringing}, and such a row can
     * never match that filter — the PATCH answers 204, so the client used to log
     * "TERMINATE-OK" for a write that changed nothing. This variant asks for the
     * affected rows back so the caller can tell "closed" from "already closed".
     */
    @Headers("Prefer: return=representation")
    @PATCH("rest/v1/calls")
    Call<List<com.insta.nova.calling.CallRecord>> closePendingCall(
            @Query("id") String idQuery,
            @Query("status") String statusFilter,
            @Body Map<String, Object> body);

    /** Prompt K — claim one terminal row exactly once for missed-call surfacing. */
    @PATCH("rest/v1/calls")
    Call<Void> markCalleeSurfaced(@Query("id") String idQuery,
                                  @Query("callee_surfaced_at") String surfacedQuery,
                                  @Body Map<String, Object> body);

    /** Prompt K — compare-and-set ACK: only the first caller-side `calling` row wins. */
    @PATCH("rest/v1/calls")
    Call<Void> acknowledgeCall(@Query("id") String idQuery,
                               @Query("status") String statusQuery,
                               @Body Map<String, Object> body);

    /**
     * #65 — materialises the call as a row in {@code messages} so the inbox preview and
     * the other device both see it.
     *
     * <p>Server-side rather than a plain insert: `messages_insert` requires
     * `sender_id = request_uid()`, and a call entry is always attributed to the caller,
     * so the callee could not write it. The RPC checks participation itself and is
     * idempotent on the call id.
     */
    @POST("rest/v1/rpc/record_call_message")
    Call<Void> recordCallMessage(@Body Map<String, Object> body);

    /** Prompt J — high-priority data-only push so a killed app still rings. */
    @POST("functions/v1/send-call-push")
    Call<Map<String, Object>> sendCallPush(@Body Map<String, Object> body);

    /** Heartbeat: records that the caller is active right now (see profiles.last_active_at). */
    @PATCH("rest/v1/profiles")
    Call<Void> touchLastActive(@Query("id") String idQuery, @Body Map<String, Object> body);

    /** Writes the caller's FCM token to its own profiles row. */
    @PATCH("rest/v1/profiles")
    Call<Void> updateOwnFcmToken(@Query("id") String idQuery, @Body Map<String, Object> body);
}
