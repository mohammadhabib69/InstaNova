package com.insta.clone.data.remote.api;

import com.google.gson.JsonElement;
import com.insta.clone.data.remote.dto.CommentDto;
import com.insta.clone.data.remote.dto.ConversationDto;
import com.insta.clone.data.remote.dto.MessageDto;
import com.insta.clone.data.remote.dto.NotificationDto;
import com.insta.clone.data.remote.dto.PostDto;
import com.insta.clone.data.remote.dto.ProfileDto;
import com.insta.clone.data.remote.dto.RpcEmailRequest;
import com.insta.clone.data.remote.dto.RpcSuggestedRequest;
import com.insta.clone.data.remote.dto.StoryDto;
import com.insta.clone.data.remote.dto.StoryGroupDto;
import com.insta.clone.data.remote.dto.UnreadCountsDto;
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

    // Profiles
    @GET("rest/v1/profiles")
    Call<List<ProfileDto>> getProfiles(@QueryMap Map<String, String> query);

    @Headers("Prefer: return=representation")
    @POST("rest/v1/profiles")
    Call<List<ProfileDto>> createProfile(@Body Map<String, Object> body);

    @Headers("Prefer: return=representation")
    @PATCH("rest/v1/profiles")
    Call<List<ProfileDto>> updateProfile(@Query("id") String idQuery, @Body Map<String, Object> body);

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
    @POST("rest/v1/likes")
    Call<Void> likePost(@Body Map<String, Object> body);

    @DELETE("rest/v1/likes")
    Call<Void> unlikePost(@Query("post_id") String postIdQuery, @Query("user_id") String userIdQuery);

    @POST("rest/v1/saved_posts")
    Call<Void> savePost(@Body Map<String, Object> body);

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
    @POST("rest/v1/follows")
    Call<Void> followUser(@Body Map<String, Object> body);

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

    // Notifications
    @GET("rest/v1/notifications")
    Call<List<NotificationDto>> getNotifications(@QueryMap Map<String, String> query);

    @PATCH("rest/v1/notifications")
    Call<Void> markNotificationsRead(@Query("user_id") String userIdQuery, @Query("read") String readQuery, @Body Map<String, Object> body);
}
