-- ============================================================================
-- 2026-11-08  v_feed / v_posts carry the post's music
--
-- The composer has been writing posts.music_url and posts.music_title since
-- 2026-11-08_post_music.sql, but nothing could read them: neither view exposed
-- the columns. Both are re-created here with the two fields appended to the end
-- of the select list, which is the only shape CREATE OR REPLACE VIEW accepts.
-- v_saved selects v_posts column by column, so it keeps working untouched.
--
-- Rollback: 2026-11-08_post_music_views.rollback.sql
-- ============================================================================

begin;

set local lock_timeout = '10s';

create or replace view public.v_feed as
SELECT p.id,
        CASE
            WHEN p.is_anonymous THEN NULL::uuid
            ELSE p.user_id
        END AS user_id,
        CASE
            WHEN p.is_anonymous THEN p.anonymous_alias
            ELSE pr.username
        END AS username,
        CASE
            WHEN p.is_anonymous THEN NULL::text
            ELSE pr.avatar_url
        END AS avatar_url,
        CASE
            WHEN p.is_anonymous THEN false
            ELSE pr.is_verified
        END AS is_verified,
    p.caption,
    p.location,
    p.is_reel,
    p.video_url,
    p.thumbnail_url,
    p.likes_count,
    p.comments_count,
    p.created_at,
    ( SELECT COALESCE(jsonb_agg(m.url ORDER BY m."position"), '[]'::jsonb) AS "coalesce"
           FROM post_media m
          WHERE m.post_id = p.id) AS media,
    (EXISTS ( SELECT 1
           FROM likes l
          WHERE l.post_id = p.id AND l.user_id = request_uid())) AS liked_by_me,
    (EXISTS ( SELECT 1
           FROM saved_posts s
          WHERE s.post_id = p.id AND s.user_id = request_uid())) AS saved_by_me,
    ( SELECT COALESCE(jsonb_agg(jsonb_build_object('username', t.username, 'avatar_url', t.avatar_url)), '[]'::jsonb) AS "coalesce"
           FROM ( SELECT pr2.username,
                    pr2.avatar_url
                   FROM likes l2
                     JOIN profiles pr2 ON pr2.id = l2.user_id
                  WHERE l2.post_id = p.id
                  ORDER BY l2.created_at DESC
                 LIMIT 3) t) AS top_likers,
    p.is_archived,
    p.space,
    p.is_anonymous,
    p.anonymous_alias,
    p.expires_at,
    p.duration_seconds,
    p.hide_like_count,
    p.comments_disabled,
    p.music_url,
    p.music_title
   FROM posts p
     JOIN profiles pr ON pr.id = p.user_id
  WHERE p.deleted_at IS NULL AND p.is_reel = false AND p.is_archived = false AND p.space = 'insta'::text AND (p.expires_at IS NULL OR p.expires_at > now()) AND (request_uid() IS NULL OR p.user_id = request_uid() OR COALESCE(( SELECT o.owner_id
           FROM posts_anon_owners o
          WHERE o.post_id = p.id), p.user_id) = request_uid() OR (COALESCE(( SELECT o.owner_id
           FROM posts_anon_owners o
          WHERE o.post_id = p.id), p.user_id) IN ( SELECT f.following_id
           FROM follows f
          WHERE f.follower_id = request_uid())));

create or replace view public.v_posts as
SELECT p.id,
        CASE
            WHEN p.is_anonymous THEN NULL::uuid
            ELSE p.user_id
        END AS user_id,
        CASE
            WHEN p.is_anonymous THEN p.anonymous_alias
            ELSE pr.username
        END AS username,
        CASE
            WHEN p.is_anonymous THEN NULL::text
            ELSE pr.avatar_url
        END AS avatar_url,
        CASE
            WHEN p.is_anonymous THEN false
            ELSE pr.is_verified
        END AS is_verified,
    p.caption,
    p.location,
    p.is_reel,
    p.video_url,
    p.thumbnail_url,
    p.likes_count,
    p.comments_count,
    p.created_at,
    ( SELECT COALESCE(jsonb_agg(m.url ORDER BY m."position"), '[]'::jsonb) AS "coalesce"
           FROM post_media m
          WHERE m.post_id = p.id) AS media,
    (EXISTS ( SELECT 1
           FROM likes l
          WHERE l.post_id = p.id AND l.user_id = request_uid())) AS liked_by_me,
    (EXISTS ( SELECT 1
           FROM saved_posts s
          WHERE s.post_id = p.id AND s.user_id = request_uid())) AS saved_by_me,
    ( SELECT COALESCE(jsonb_agg(jsonb_build_object('username', t.username, 'avatar_url', t.avatar_url)), '[]'::jsonb) AS "coalesce"
           FROM ( SELECT pr2.username,
                    pr2.avatar_url
                   FROM likes l2
                     JOIN profiles pr2 ON pr2.id = l2.user_id
                  WHERE l2.post_id = p.id
                  ORDER BY l2.created_at DESC
                 LIMIT 3) t) AS top_likers,
    p.is_archived,
    p.space,
    p.is_anonymous,
    p.anonymous_alias,
    p.expires_at,
    p.duration_seconds,
    p.hide_like_count,
    p.comments_disabled,
    p.music_url,
    p.music_title
   FROM posts p
     JOIN profiles pr ON pr.id = p.user_id
  WHERE p.deleted_at IS NULL AND (p.is_anonymous OR can_view(p.user_id));

commit;

notify pgrst, 'reload schema';
