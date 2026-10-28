package com.insta.nova.data;

import com.google.gson.JsonElement;
import com.insta.nova.data.remote.dto.ProfileDto;

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
 * PostgREST + RPC surface for Nova. Every path/parameter below is one of the verified
 * schema names; the existing AuthInterceptor and TokenAuthenticator are reused because
 * this Retrofit instance is built from the same OkHttpClient.
 */
public interface NovaApi {

    /* ------------------------------- settings ------------------------------- */

    @GET("rest/v1/nova_settings")
    Call<List<NovaDtos.Settings>> getSettings(@QueryMap Map<String, String> query);

    @Headers({"Prefer: resolution=merge-duplicates,return=representation"})
    @POST("rest/v1/nova_settings")
    Call<List<NovaDtos.Settings>> upsertSettings(@Query("on_conflict") String onConflict,
                                                 @Body Map<String, Object> body);

    @Headers("Prefer: return=minimal")
    @PATCH("rest/v1/nova_settings")
    Call<Void> patchSettings(@Query("user_id") String userIdEq, @Body Map<String, Object> body);

    /* ----------------------------- moderation log ---------------------------- */

    @Headers("Prefer: return=minimal")
    @POST("rest/v1/nova_moderation_events")
    Call<Void> insertModerationEvent(@Body Map<String, Object> body);

    /* --------------------------------- safe ---------------------------------- */

    @Headers("Prefer: return=minimal")
    @POST("rest/v1/rpc/nova_block_user")
    Call<Void> blockUser(@Body Map<String, Object> body);

    @Headers("Prefer: return=minimal")
    @POST("rest/v1/rpc/nova_unblock_user")
    Call<Void> unblockUser(@Body Map<String, Object> body);

    @Headers("Prefer: return=minimal")
    @POST("rest/v1/rpc/nova_mute_user")
    Call<Void> muteUser(@Body Map<String, Object> body);

    @POST("rest/v1/rpc/nova_blocked_ids")
    Call<List<String>> blockedIds(@Body Map<String, Object> body);

    @POST("rest/v1/rpc/nova_my_blocked_users")
    Call<List<NovaDtos.BlockedUser>> myBlockedUsers(@Body Map<String, Object> body);

    @POST("rest/v1/rpc/nova_muted_users")
    Call<List<NovaDtos.MutedUser>> mutedUsers(@Body Map<String, Object> body);

    @Headers("Prefer: return=representation")
    @POST("rest/v1/rpc/nova_report")
    Call<JsonElement> report(@Body Map<String, Object> body);

    @POST("rest/v1/rpc/nova_safety_stats")
    Call<NovaDtos.SafetyStats> safetyStats(@Body Map<String, Object> body);

    /* ---------------------------------- hub ---------------------------------- */

    @POST("rest/v1/rpc/nova_hub_summary")
    Call<NovaDtos.HubSummary> hubSummary(@Body Map<String, Object> body);

    /* -------------------------------- alerts --------------------------------- */

    @GET("rest/v1/nova_alerts")
    Call<List<NovaDtos.Alert>> getAlerts(@QueryMap Map<String, String> query);

    @Headers("Prefer: return=minimal")
    @PATCH("rest/v1/nova_alerts")
    Call<Void> patchAlert(@Query("id") String idEq, @Body Map<String, Object> body);

    @Headers("Prefer: return=minimal")
    @PATCH("rest/v1/nova_alerts")
    Call<Void> markAllAlertsRead(@Query("user_id") String userIdEq,
                                 @Query("is_read") String isReadEq,
                                 @Body Map<String, Object> body);

    @POST("rest/v1/rpc/nova_unread_alerts")
    Call<Integer> unreadAlerts(@Body Map<String, Object> body);

    /* ---------------------------------- SOS ---------------------------------- */

    @GET("rest/v1/nova_districts")
    Call<List<NovaDtos.District>> getDistricts(@QueryMap Map<String, String> query);

    @POST("rest/v1/rpc/nova_blood_feed")
    Call<List<NovaDtos.BloodRequest>> bloodFeed(@Body Map<String, Object> body);

    @POST("rest/v1/rpc/nova_create_blood_request")
    Call<NovaDtos.CreateRequestResult> createBloodRequest(@Body Map<String, Object> body);

    @POST("rest/v1/rpc/nova_pledge")
    Call<NovaDtos.PledgeResult> pledge(@Body Map<String, Object> body);

    @Headers("Prefer: return=minimal")
    @POST("rest/v1/rpc/nova_unpledge")
    Call<Void> unpledge(@Body Map<String, Object> body);

    @POST("rest/v1/rpc/nova_confirm_donation")
    Call<NovaDtos.ConfirmResult> confirmDonation(@Body Map<String, Object> body);

    @Headers("Prefer: return=minimal")
    @POST("rest/v1/rpc/nova_set_request_status")
    Call<Void> setRequestStatus(@Body Map<String, Object> body);

    @POST("rest/v1/rpc/nova_request_contact")
    Call<JsonElement> requestContact(@Body Map<String, Object> body);

    @POST("rest/v1/rpc/nova_request_pledges")
    Call<List<NovaDtos.Pledge>> requestPledges(@Body Map<String, Object> body);

    @POST("rest/v1/rpc/nova_match_count")
    Call<Integer> matchCount(@Body Map<String, Object> body);

    @GET("rest/v1/nova_blood_requests")
    Call<List<NovaDtos.BloodRequest>> getMyBloodRequests(@QueryMap Map<String, String> query);

    @GET("rest/v1/nova_blood_pledges")
    Call<List<Map<String, Object>>> getMyPledges(@QueryMap Map<String, String> query);

    @GET("rest/v1/nova_donors")
    Call<List<NovaDtos.Donor>> getDonor(@QueryMap Map<String, String> query);

    /**
     * Upsert, not a plain insert.
     *
     * <p>{@code nova_donors} is keyed by user_id. The screen decides between create and
     * edit from a read that can fail, and when that read failed it inserted over an
     * existing row and surfaced a duplicate-key error the user could do nothing about.
     * merge-duplicates makes the same call correct in both cases.
     */
    @Headers({
            "Prefer: return=minimal,resolution=merge-duplicates",
            "Content-Type: application/json"
    })
    @POST("rest/v1/nova_donors")
    Call<Void> insertDonor(@Body Map<String, Object> body);

    @Headers("Prefer: return=minimal")
    @PATCH("rest/v1/nova_donors")
    Call<Void> patchDonor(@Query("user_id") String userIdEq, @Body Map<String, Object> body);

    @DELETE("rest/v1/nova_donors")
    Call<Void> deleteDonor(@Query("user_id") String userIdEq);

    @POST("rest/v1/rpc/nova_donor_badge")
    Call<NovaDtos.DonorBadge> donorBadge(@Body Map<String, Object> body);

    /* -------------------------------- campus --------------------------------- */

    @GET("rest/v1/nova_universities")
    Call<List<NovaDtos.University>> getUniversities(@QueryMap Map<String, String> query);

    /**
     * Verifies campus membership from a student mailbox. The server accepts an address whose
     * domain is exactly "stud." + one of the university's domains, e.g. @stud.kuet.ac.bd.
     */
    @Headers("Content-Type: application/json")
    @POST("rest/v1/rpc/nova_campus_verify_email")
    retrofit2.Call<java.util.Map<String, Object>> verifyCampusEmail(@retrofit2.http.Body java.util.Map<String, Object> body);

    @POST("rest/v1/rpc/nova_campus_join")
    Call<NovaDtos.CampusJoinResult> campusJoin(@Body Map<String, Object> body);

    @Headers("Prefer: return=minimal")
    @POST("rest/v1/rpc/nova_campus_leave")
    Call<Void> campusLeave(@Body Map<String, Object> body);

    @GET("rest/v1/nova_campus_members")
    Call<List<NovaDtos.CampusMembership>> getMyMembership(@QueryMap Map<String, String> query);

    @POST("rest/v1/rpc/nova_is_verified_student")
    Call<Boolean> isVerifiedStudent(@Body Map<String, Object> body);

    @GET("rest/v1/nova_v_events")
    Call<List<NovaDtos.EventItem>> getEvents(@QueryMap Map<String, String> query);

    @Headers("Prefer: return=minimal")
    @POST("rest/v1/nova_events")
    Call<Void> insertEvent(@Body Map<String, Object> body);

    @Headers("Prefer: return=minimal")
    @PATCH("rest/v1/nova_events")
    Call<Void> updateEvent(@Query("id") String idEq, @Body Map<String, Object> body);

    @DELETE("rest/v1/nova_events")
    Call<Void> deleteEvent(@Query("id") String idEq);

    @Headers("Prefer: resolution=merge-duplicates,return=minimal")
    @POST("rest/v1/nova_event_rsvps")
    Call<Void> upsertRsvp(@Query("on_conflict") String onConflict, @Body Map<String, Object> body);

    @DELETE("rest/v1/nova_event_rsvps")
    Call<Void> deleteRsvp(@Query("event_id") String eventIdEq,
                          @Query("user_id") String userIdEq);

    @GET("rest/v1/nova_v_lostfound")
    Call<List<NovaDtos.LostFoundItem>> getLostFound(@QueryMap Map<String, String> query);

    @Headers("Prefer: return=minimal")
    @POST("rest/v1/nova_lostfound")
    Call<Void> insertLostFound(@Body Map<String, Object> body);

    @Headers("Prefer: return=minimal")
    @PATCH("rest/v1/nova_lostfound")
    Call<Void> updateLostFound(@Query("id") String idEq, @Body Map<String, Object> body);

    @DELETE("rest/v1/nova_lostfound")
    Call<Void> deleteLostFound(@Query("id") String idEq);

    @POST("rest/v1/rpc/nova_post_confession")
    Call<JsonElement> postConfession(@Body Map<String, Object> body);

    @POST("rest/v1/rpc/nova_confessions_feed")
    Call<List<NovaDtos.Confession>> confessionsFeed(@Body Map<String, Object> body);

    @POST("rest/v1/rpc/nova_like_confession")
    Call<Integer> likeConfession(@Body Map<String, Object> body);

    @Headers("Prefer: return=minimal")
    @POST("rest/v1/rpc/nova_delete_confession")
    Call<Void> deleteConfession(@Body Map<String, Object> body);

    /* --------------------------------- study --------------------------------- */

    @Headers("Prefer: return=minimal")
    @POST("rest/v1/nova_study_sessions")
    Call<Void> insertStudySession(@Body Map<String, Object> body);

    @POST("rest/v1/rpc/nova_study_stats")
    Call<NovaDtos.StudyStats> studyStats(@Body Map<String, Object> body);

    /** Raw sessions, used to rebuild the board when the aggregate RPC returns nothing. */
    @GET("rest/v1/nova_study_sessions")
    Call<java.util.List<java.util.Map<String, Object>>> getStudySessions(
            @retrofit2.http.QueryMap java.util.Map<String, String> query);

    @POST("rest/v1/rpc/nova_study_leaderboard")
    Call<List<NovaDtos.LeaderRow>> studyLeaderboard(@Body Map<String, Object> body);

    @GET("rest/v1/nova_v_questions")
    Call<List<NovaDtos.Question>> getQuestions(@QueryMap Map<String, String> query);

    @Headers("Prefer: return=minimal")
    @POST("rest/v1/nova_questions")
    Call<Void> insertQuestion(@Body Map<String, Object> body);

    @Headers("Prefer: return=minimal")
    @PATCH("rest/v1/nova_questions")
    Call<Void> updateQuestion(@Query("id") String idEq, @Body Map<String, Object> body);

    @DELETE("rest/v1/nova_questions")
    Call<Void> deleteQuestion(@Query("id") String idEq);

    @GET("rest/v1/nova_v_answers")
    Call<List<NovaDtos.Answer>> getAnswers(@QueryMap Map<String, String> query);

    @Headers("Prefer: return=minimal")
    @POST("rest/v1/nova_answers")
    Call<Void> insertAnswer(@Body Map<String, Object> body);

    @Headers("Prefer: return=minimal")
    @PATCH("rest/v1/nova_answers")
    Call<Void> updateAnswer(@Query("id") String idEq, @Body Map<String, Object> body);

    @DELETE("rest/v1/nova_answers")
    Call<Void> deleteAnswer(@Query("id") String idEq);

    @POST("rest/v1/rpc/nova_toggle_vote")
    Call<NovaDtos.VoteResult> toggleVote(@Body Map<String, Object> body);

    @POST("rest/v1/rpc/nova_accept_answer")
    Call<NovaDtos.AcceptResult> acceptAnswer(@Body Map<String, Object> body);

    /* -------------------------------- insights -------------------------------- */

    @POST("rest/v1/rpc/nova_my_insights")
    Call<NovaDtos.Insights> myInsights(@Body Map<String, Object> body);

    /* -------------------------------- profiles -------------------------------- */

    @GET("rest/v1/profiles")
    Call<List<ProfileDto>> getProfiles(@QueryMap Map<String, String> query);

    /* -------------------------- Reddit-style posts -------------------------- */
    /* The Nova feed reads nova_posts_public and nothing else. The view is the
       only path into the Nova tables: the base tables are sealed from the
       client roles so the real author of an anonymous post cannot leak. */

    @GET("rest/v1/nova_posts_public")
    Call<List<NovaDtos.NovaPost>> getNovaPosts(@QueryMap Map<String, String> query);

    @GET("rest/v1/nova_comments_public")
    Call<List<NovaDtos.NovaComment>> getNovaComments(@QueryMap Map<String, String> query);

    @POST("rest/v1/rpc/create_nova_post")
    Call<NovaDtos.CreatedRef> createNovaPost(@Body Map<String, Object> body);

    @POST("rest/v1/rpc/create_nova_comment")
    Call<NovaDtos.CreatedRef> createNovaComment(@Body Map<String, Object> body);

    /** Releases a comment held because its author is restricted by the post owner. */
    @POST("rest/v1/rpc/approve_held_nova_comment")
    Call<com.google.gson.JsonElement> approveHeldNovaComment(@Body Map<String, Object> body);

    @POST("rest/v1/rpc/cast_vote")
    Call<NovaDtos.VoteOutcome> castVote(@Body Map<String, Object> body);

    /** Casts or changes a poll choice. The server rebuilds the counts, so the client
     *  never has to add or subtract locally. */
    @Headers("Content-Type: application/json")
    @POST("rest/v1/rpc/nova_vote_poll")
    Call<NovaDtos.PollOutcome> votePoll(@Body Map<String, Object> body);

    @POST("rest/v1/rpc/cast_comment_vote")
    Call<NovaDtos.VoteOutcome> castCommentVote(@Body Map<String, Object> body);

    @POST("rest/v1/rpc/toggle_nova_save")
    Call<com.google.gson.JsonElement> toggleNovaSave(@Body Map<String, Object> body);

    @POST("rest/v1/rpc/share_nova_to_instagram")
    Call<com.google.gson.JsonElement> shareNovaToInstagram(@Body Map<String, Object> body);

    /* ------------------------ Instagram Post Options ------------------------ */
    /* The same options sheet writes to the Instagram table when it is used on
       the Instagram surface. The server keeps the real author of an anonymous
       post out of posts.user_id, so the identity cannot be read back. */

    @POST("rest/v1/rpc/create_instagram_post")
    Call<com.google.gson.JsonElement> createInstagramPost(@Body Map<String, Object> body);

    @POST("rest/v1/rpc/delete_my_post")
    Call<com.google.gson.JsonElement> deleteMyPost(@Body Map<String, Object> body);

    @POST("rest/v1/rpc/update_my_post_caption")
    Call<com.google.gson.JsonElement> updateMyPostCaption(@Body Map<String, Object> body);
}
