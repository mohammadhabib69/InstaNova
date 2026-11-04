-- ============================================================================
-- Drop stale RPC overloads
--
-- PostgREST resolves an RPC call by matching the keys present in the request
-- body against the function's parameter names. When two overloads of the same
-- function both had every argument defaulted, a body that omitted the newer
-- argument matched *both* candidates and the call failed with:
--
--     Could not choose the best candidate function between: ...
--
-- Affected calls, and what the client actually sends:
--
--   create_nova_post         the Nova composer omits p_duration_seconds when
--                            the post is set to "Never" expire. That body
--                            matched both overloads, so **a Nova post that
--                            never expires could not be created at all**.
--
--   create_instagram_post    three signatures existed; only the one taking
--                            p_media_urls jsonb is used by the app.
--
--   get_suggested_users      the profile suggestions call sends only
--                            {p_limit}, which matched both overloads.
--
-- Fix: keep exactly one signature per operation — the newest and most capable
-- one — so there is never more than one candidate to choose between.
-- ============================================================================

-- The old create_nova_post without p_duration_seconds. The remaining one has
-- p_duration_seconds defaulted, so bodies that omit it still resolve.
drop function if exists public.create_nova_post(
    p_body text,
    p_media_url text,
    p_media_type text,
    p_content_type text,
    p_flair text,
    p_is_anonymous boolean,
    p_expires_at timestamp with time zone,
    p_poll_options jsonb
);

-- The two older create_instagram_post signatures. The app posts a media array,
-- so only the p_media_urls jsonb variant is used.
drop function if exists public.create_instagram_post(
    p_caption text,
    p_media_url text,
    p_is_reel boolean,
    p_video_url text,
    p_thumbnail_url text,
    p_is_anonymous boolean,
    p_expires_at timestamp with time zone
);

drop function if exists public.create_instagram_post(
    p_caption text,
    p_media_url text,
    p_is_reel boolean,
    p_video_url text,
    p_thumbnail_url text,
    p_is_anonymous boolean,
    p_expires_at timestamp with time zone,
    p_duration_seconds integer
);

-- The one-argument suggestions variant. The two-argument one keeps
-- p_exclude_user_id defaulted, so a body of {p_limit} still resolves.
drop function if exists public.get_suggested_users(
    p_limit integer
);

-- ============================================================================
-- Verification
-- ============================================================================
-- No overloaded function may remain:
--
--   select proname, count(*)
--   from pg_proc p join pg_namespace n on n.oid = p.pronamespace
--   where n.nspname = 'public'
--   group by proname having count(*) > 1;
--
-- Expect zero rows.
--
-- A Nova post that never expires must now create:
--
--   select public.create_nova_post(
--       p_body => 'never expires', p_media_type => 'text',
--       p_content_type => 'text');
--
-- Expect a row with expires_at = null and duration_seconds = null.
