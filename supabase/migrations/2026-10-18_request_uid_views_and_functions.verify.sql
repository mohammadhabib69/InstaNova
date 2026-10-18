-- ============================================================================
-- Verification for 2026-10-18_request_uid_views_and_functions.sql
-- Every statement here is READ-ONLY (SELECT only). Run section A-C+D before
-- applying the migration, then again afterwards and diff the output.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- A. auth.uid() / request_uid() occurrences per target object
--    BEFORE: auth_uid_hits sums to 26, request_uid_hits 0 for every object.
--    AFTER : auth_uid_hits = 0 everywhere, request_uid_hits > 0.
-- ----------------------------------------------------------------------------
with objs as (
  select 'view'::text as kind, c.relname::text as name, pg_get_viewdef(c.oid, true) as body
  from pg_class c
  where c.relnamespace = 'public'::regnamespace
    and c.relkind = 'v'
    and c.relname in ('v_posts','v_feed','v_saved','v_comments','v_stories')
  union all
  select 'function',
         p.proname || '(' || pg_get_function_identity_arguments(p.oid) || ')',
         pg_get_functiondef(p.oid)
  from pg_proc p
  where p.pronamespace = 'public'::regnamespace
    and p.proname in ('get_unread_counts','get_inbox','get_story_groups',
                      'get_suggested_users','get_or_create_conversation')
)
select kind,
       name,
       (select count(*) from regexp_matches(body, 'auth\.uid\(\)', 'g'))    as auth_uid_hits,
       (select count(*) from regexp_matches(body, 'request_uid\(\)', 'g')) as request_uid_hits
from objs
order by 1, 2;

-- ----------------------------------------------------------------------------
-- B. View column signature — must be byte-identical before and after.
--    Expected: 17 / 17 / 18 / 10 / 9 columns in the same order.
-- ----------------------------------------------------------------------------
select table_name, ordinal_position, column_name, data_type
from information_schema.columns
where table_schema = 'public'
  and table_name in ('v_posts','v_feed','v_saved','v_comments','v_stories')
order by table_name, ordinal_position;

-- ----------------------------------------------------------------------------
-- C. Function signature + attribute baseline — must be identical before/after.
--    Covers args, return type, SECURITY DEFINER, volatility, parallel safety,
--    search_path and grants.
-- ----------------------------------------------------------------------------
select p.proname,
       pg_get_function_identity_arguments(p.oid) as args,
       pg_get_function_result(p.oid)            as returns,
       p.prosecdef                              as security_definer,
       p.provolatile::text                      as volatility,
       p.proparallel::text                      as parallel,
       coalesce(array_to_string(p.proconfig, ';'), '') as config,
       coalesce(p.proacl::text, '')             as acl,
       pg_get_userbyid(p.proowner)              as owner
from pg_proc p
where p.pronamespace = 'public'::regnamespace
  and p.proname in ('get_unread_counts','get_inbox','get_story_groups',
                    'get_suggested_users','get_or_create_conversation')
order by 1, 2;

-- ----------------------------------------------------------------------------
-- D. Execution smoke test in the SQL editor.
--    The editor has no JWT, so request_uid() returns NULL — the same shape as
--    an anonymous request. These must return rows/counts, not 22P02.
-- ----------------------------------------------------------------------------
select count(*) as v_posts_rows    from public.v_posts;
select count(*) as v_feed_rows     from public.v_feed;
select count(*) as v_saved_rows    from public.v_saved;
select count(*) as v_comments_rows from public.v_comments;
select count(*) as v_stories_rows  from public.v_stories;
select public.get_unread_counts()  as unread;

-- ----------------------------------------------------------------------------
-- E. The decisive check cannot run in the SQL editor: it needs a Firebase ID
--    token. Run from the app (or curl with a real token) after deployment:
--
--      GET  /rest/v1/v_posts?is_reel=eq.false&user_id=eq.<profileId>  -> 200 []
--      POST /rpc/get_unread_counts                                    -> 200 json
--
--    Before the migration both return 400 with PostgREST error 22P02.
-- ----------------------------------------------------------------------------
