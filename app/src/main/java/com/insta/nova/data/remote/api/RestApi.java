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

    @POST("rest/v1/rpc/get_story_groups")
    Call<List<StoryGroupDto>> getStoryGroups(@Body Map<String, Object> body);

    @POST("rest/v1/rpc/get_suggested_users")
    Call<List<ProfileDto>> getSuggestedUsers(@Body RpcSuggestedRequest request);

    /** PAGE 18 — suggestions by raw limit map. */
    @POST("rest/v1/rpc/get_suggested_users")
    Call<List<ProfileDto>> getSuggestedUsersRaw(@Body Map<String, Object> body);

    @POST("rest/v1/rpc/get_or_create_conversation")
    Call<JsonElement> getOrCreateConversation(@Body Map<String, Object> request);

    @POST("rest/v1/rpc/get_inbox")
    Call<List<ConversationDto>> getInbox(@Body Map<String, Object> body);

    @POST("rest/v1/rpc/set_account_privacy")
    Call<Void> setAccountPrivacy(@Body Map<String, Object> body);

    @POST("rest/v1/rpc/approve_follow_request")
    Call<Void> approveFollowRequest(@Body Map<String, Object> body);

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

    @Headers("Prefer: return=representation")
    @POST("rest/v1/comments")
    Call<List<CommentDto>> addComment(@Body Map<String, Object> body);

    @DELETE("rest/v1/comments")
    Call<Void> deleteComment(@Query("id") String idQuery);

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

    @Headers("Prefer: return=representation")
    @POST("rest/v1/stories")
    Call<List<StoryDto>> createStory(@Body Map<String, Object> body);

    @DELETE("rest/v1/stories")
    Call<Void> deleteStory(@Query("id") String idQuery);

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

    @GET("rest/v1/highlight_items")
    Call<List<Map<String, Object>>> getHighlightItems(@QueryMap Map<String, String> query);

    @POST("rest/v1/highlight_items")
    Call<Void> createHighlightItems(@Body List<Map<String, Object>> items);

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

    @GET("rest/v1/calls")
    Call<List<com.insta.nova.calling.CallRecord>> getTerminalCalleeCalls(@QueryMap Map<String, String> query);

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

    /** Prompt J — high-priority data-only push so a killed app still rings. */
    @POST("functions/v1/send-call-push")
    Call<Map<String, Object>> sendCallPush(@Body Map<String, Object> body);

    /** Writes the caller's FCM token to its own profiles row. */
    @PATCH("rest/v1/profiles")
    Call<Void> updateOwnFcmToken(@Query("id") String idQuery, @Body Map<String, Object> body);
}
