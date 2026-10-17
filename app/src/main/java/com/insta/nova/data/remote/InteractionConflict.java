package com.insta.nova.data.remote;

/**
 * Conflict targets for PostgREST upserts on interaction tables.
 *
 * <p>Every table here is keyed by a composite primary key — {@code likes(post_id, user_id)},
 * {@code follows(follower_id, following_id)} — so a repeated tap used to send a plain
 * INSERT and come back as {@code 23505}, which the UI reported as a failure even though
 * the desired row already existed. Sending {@code on_conflict} together with
 * {@code Prefer: resolution=ignore-duplicates} turns that into a no-op, which is the
 * correct semantics for an idempotent toggle.
 *
 * <p>The values must match the primary-key column order, otherwise PostgREST rejects the
 * request and the write fails loudly rather than silently doing nothing.
 */
public final class InteractionConflict {

    /** Primary key of {@code public.likes}. */
    public static final String LIKE = "post_id,user_id";

    /** Primary key of {@code public.follows}. */
    public static final String FOLLOW = "follower_id,following_id";

    /** Primary key of {@code public.saved_posts}. */
    public static final String SAVE = "post_id,user_id";

    /** Primary key of {@code public.story_views}. */
    public static final String STORY_VIEW = "story_id,viewer_id";

    private InteractionConflict() {
    }
}
