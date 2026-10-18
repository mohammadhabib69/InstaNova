-- ============================================================================
-- 2026-10-18  Verification for 2026-10-18_profiles_column_grants.sql
--
-- READ-ONLY. Every statement here is SELECT / has_*_privilege / md5(). Nothing
-- in this file writes. Run §0 and keep its output BEFORE applying the
-- migration; §1 and §3 run after it.
-- ============================================================================


-- ============================================================================
-- §0  PRE-FLIGHT SNAPSHOT — run first, save the output
-- ============================================================================

-- 0.1 Table ACL. Expected before: {postgres=arwdDxtm/postgres,
--     anon=arwdDxtm/postgres, authenticated=arwdDxtm/postgres,
--     service_role=arwdDxtm/postgres}
select c.relname,
       c.relacl
  from pg_class c
  join pg_namespace n on n.oid = c.relnamespace
 where n.nspname = 'public'
   and c.relname = 'profiles';

-- 0.2 Table-level privileges actually held by the client roles.
--     Expected before: 4 rows each (SELECT, INSERT, UPDATE, DELETE) plus
--     TRUNCATE/REFERENCES/TRIGGER appear in pg_class.relacl above, not here.
select grantee, privilege_type
  from information_schema.role_table_grants
 where table_schema = 'public'
   and table_name   = 'profiles'
   and grantee in ('anon', 'authenticated')
 order by grantee, privilege_type;

-- 0.3 Column-level privileges. Expected before: 0 rows (no column ACL entries).
select grantee, column_name, privilege_type
  from information_schema.column_privileges
 where table_schema = 'public'
   and table_name   = 'profiles'
   and grantee in ('anon', 'authenticated')
 order by grantee, privilege_type, column_name;

-- 0.4 Function security mode, search_path and ACL for every function the
--     migration reasons about. Save this output: §3 compares against it.
select p.oid::regprocedure::text as signature,
       p.prosecdef               as security_definer,
       p.proconfig               as settings,
       p.proacl                  as acl
  from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
 where n.nspname = 'public'
   and p.proname in ('request_uid', 'get_suggested_users', 'get_story_groups',
                     'get_inbox', 'get_unread_counts', 'set_account_privacy',
                     'handle_new_user')
 order by 1;

-- 0.5 RLS policies on public.profiles. Expected before and after: identical.
select policyname, permissive, roles::text, cmd, qual, with_check
  from pg_policies
 where schemaname = 'public'
   and tablename  = 'profiles'
 order by policyname;

-- 0.6 View definitions fingerprint. Expected before and after: identical,
--     because the migration touches no view.
select viewname,
       md5(pg_get_viewdef(('public.' || viewname)::regclass, true)) as def_md5
  from pg_views
 where schemaname = 'public'
   and viewname in ('v_posts', 'v_feed', 'v_saved', 'v_comments', 'v_stories')
 order by viewname;


-- ============================================================================
-- §1  POST-APPLY ASSERTIONS — every row must read PASS
-- ============================================================================

with checks(name, actual, expected) as (
  values
    -- anon must not reach the sensitive columns, by any privilege
    ('anon   select birthday          = false', has_column_privilege('anon', 'public.profiles', 'birthday',     'select'), false),
    ('anon   select firebase_uid      = false', has_column_privilege('anon', 'public.profiles', 'firebase_uid', 'select'), false),
    ('anon   insert birthday          = false', has_column_privilege('anon', 'public.profiles', 'birthday',     'insert'), false),
    ('anon   update birthday          = false', has_column_privilege('anon', 'public.profiles', 'birthday',     'update'), false),

    -- anon keeps exactly the view-dependent public columns
    ('anon   select id                = true',  has_column_privilege('anon', 'public.profiles', 'id',           'select'), true),
    ('anon   select username          = true',  has_column_privilege('anon', 'public.profiles', 'username',     'select'), true),
    ('anon   select avatar_url        = true',  has_column_privilege('anon', 'public.profiles', 'avatar_url',   'select'), true),
    ('anon   select is_verified       = true',  has_column_privilege('anon', 'public.profiles', 'is_verified',  'select'), true),
    ('anon   select full_name         = false', has_column_privilege('anon', 'public.profiles', 'full_name',    'select'), false),
    ('anon   select created_at        = false', has_column_privilege('anon', 'public.profiles', 'created_at',   'select'), false),

    -- anon has no write path at all (no policy, no privilege)
    ('anon   table insert             = false', has_table_privilege('anon', 'public.profiles', 'insert'),   false),
    ('anon   table update             = false', has_table_privilege('anon', 'public.profiles', 'update'),   false),
    ('anon   table delete             = false', has_table_privilege('anon', 'public.profiles', 'delete'),   false),
    ('anon   table truncate           = false', has_table_privilege('anon', 'public.profiles', 'truncate'), false),
    ('anon   table references         = false', has_table_privilege('anon', 'public.profiles', 'references'), false),
    ('anon   table trigger            = false', has_table_privilege('anon', 'public.profiles', 'trigger'),  false),

    -- authenticated keeps the public surface used by the client
    ('auth   select id                = true',  has_column_privilege('authenticated', 'public.profiles', 'id',                'select'), true),
    ('auth   select username          = true',  has_column_privilege('authenticated', 'public.profiles', 'username',          'select'), true),
    ('auth   select full_name         = true',  has_column_privilege('authenticated', 'public.profiles', 'full_name',         'select'), true),
    ('auth   select bio               = true',  has_column_privilege('authenticated', 'public.profiles', 'bio',               'select'), true),
    ('auth   select avatar_url        = true',  has_column_privilege('authenticated', 'public.profiles', 'avatar_url',        'select'), true),
    ('auth   select website           = true',  has_column_privilege('authenticated', 'public.profiles', 'website',           'select'), true),
    ('auth   select is_verified       = true',  has_column_privilege('authenticated', 'public.profiles', 'is_verified',       'select'), true),
    ('auth   select posts_count       = true',  has_column_privilege('authenticated', 'public.profiles', 'posts_count',       'select'), true),
    -- ordering: GET /profiles?select=SEARCH&order=followers_count.desc,created_at.desc
    ('auth   select followers_count   = true',  has_column_privilege('authenticated', 'public.profiles', 'followers_count',   'select'), true),
    ('auth   select following_count   = true',  has_column_privilege('authenticated', 'public.profiles', 'following_count',   'select'), true),
    ('auth   select created_at        = true',  has_column_privilege('authenticated', 'public.profiles', 'created_at',        'select'), true),

    -- Phase 1 bridge
    ('auth   select firebase_uid      = true',  has_column_privilege('authenticated', 'public.profiles', 'firebase_uid', 'select'), true),
    ('auth   select birthday          = false', has_column_privilege('authenticated', 'public.profiles', 'birthday',     'select'), false),

    -- writes: INSERT payload id/username/full_name/birthday/firebase_uid
    ('auth   insert id                = true',  has_column_privilege('authenticated', 'public.profiles', 'id',           'insert'), true),
    ('auth   insert username          = true',  has_column_privilege('authenticated', 'public.profiles', 'username',     'insert'), true),
    ('auth   insert full_name         = true',  has_column_privilege('authenticated', 'public.profiles', 'full_name',    'insert'), true),
    ('auth   insert birthday          = true',  has_column_privilege('authenticated', 'public.profiles', 'birthday',     'insert'), true),
    ('auth   insert firebase_uid      = true',  has_column_privilege('authenticated', 'public.profiles', 'firebase_uid', 'insert'), true),
    -- writes: PATCH bodies username/full_name/birthday/bio/avatar_url
    ('auth   update username          = true',  has_column_privilege('authenticated', 'public.profiles', 'username',     'update'), true),
    ('auth   update full_name         = true',  has_column_privilege('authenticated', 'public.profiles', 'full_name',    'update'), true),
    ('auth   update birthday          = true',  has_column_privilege('authenticated', 'public.profiles', 'birthday',     'update'), true),
    ('auth   update bio               = true',  has_column_privilege('authenticated', 'public.profiles', 'bio',          'update'), true),
    ('auth   update avatar_url        = true',  has_column_privilege('authenticated', 'public.profiles', 'avatar_url',   'update'), true),
    -- counters are trigger-maintained, never client-writable
    ('auth   update posts_count       = false', has_column_privilege('authenticated', 'public.profiles', 'posts_count',  'update'), false),
    ('auth   update firebase_uid      = false', has_column_privilege('authenticated', 'public.profiles', 'firebase_uid', 'update'), false),

    -- authenticated has no table-level shortcut that would bypass the column sets
    ('auth   table select             = false', has_table_privilege('authenticated', 'public.profiles', 'select'),   false),
    ('auth   table truncate           = false', has_table_privilege('authenticated', 'public.profiles', 'truncate'), false),
    ('auth   table references         = false', has_table_privilege('authenticated', 'public.profiles', 'references'), false),
    ('auth   table trigger            = false', has_table_privilege('authenticated', 'public.profiles', 'trigger'),  false),

    -- SECURITY DEFINER suggestion overload: no anonymous reach
    ('anon   execute get_suggested_users(int,uuid)  = false',
        has_function_privilege('anon', 'public.get_suggested_users(integer,uuid)', 'execute'), false),
    ('auth   execute get_suggested_users(int,uuid)  = true',
        has_function_privilege('authenticated', 'public.get_suggested_users(integer,uuid)', 'execute'), true),
    -- identity + story RPCs must keep working for anon
    ('anon   execute request_uid()                  = true',
        has_function_privilege('anon', 'public.request_uid()', 'execute'), true),
    ('anon   execute get_story_groups()             = true',
        has_function_privilege('anon', 'public.get_story_groups()', 'execute'), true)
)
select name, actual, expected,
       case when actual = expected then 'PASS' else 'FAIL' end as result
  from checks
 order by result desc, name;

-- Summary: expect failed = 0
with checks(actual, expected) as (
  values
    (has_column_privilege('anon', 'public.profiles', 'birthday', 'select'), false),
    (has_column_privilege('anon', 'public.profiles', 'firebase_uid', 'select'), false),
    (has_table_privilege('anon', 'public.profiles', 'truncate'), false),
    (has_table_privilege('authenticated', 'public.profiles', 'truncate'), false),
    (has_function_privilege('anon', 'public.get_suggested_users(integer,uuid)', 'execute'), false)
)
select count(*) filter (where actual <> expected) as failed, count(*) as total
  from checks;


-- ============================================================================
-- §2  HTTP SMOKE CHECKS — run after §1, with the migrated client build
--     (PostgREST, not SQL; listed here so the deploy has one checklist)
--
--   authenticated (Authorization: Bearer <firebase id token>)
--     GET  /rest/v1/profiles?select=<PUBLIC>&id=eq.<profile_id>&limit=1   -> 200, 1 row
--     GET  /rest/v1/profiles?select=<SEARCH>&order=followers_count.desc,created_at.desc&limit=15 -> 200
--     GET  /rest/v1/profiles?select=<SEARCH>&or=(username.ilike.*qa*,full_name.ilike.*qa*) -> 200
--     POST /rest/v1/profiles?select=<WRITE_ECHO>  (Prefer: return=representation)
--          body {id,username,full_name,birthday,firebase_uid}            -> 201, 1 row, id matches
--     PATCH /rest/v1/profiles?id=eq.<id>&select=<WRITE_ECHO>
--          body {avatar_url}                                             -> 200, 1 row
--     POST /rest/v1/rpc/get_unread_counts                                  -> 200
--     POST /rest/v1/rpc/get_inbox                                          -> 200
--     GET  /rest/v1/v_stories?limit=5                                      -> 200
--
--   anonymous (apikey only, no bearer)
--     GET  /rest/v1/v_feed?limit=5                                        -> 200
--     GET  /rest/v1/v_posts?limit=5                                        -> 200
--     GET  /rest/v1/profiles?select=username,avatar_url,is_verified&limit=5 -> 200
--     GET  /rest/v1/profiles?select=birthday&limit=1                       -> 401/403 (must NOT be 200)
--     GET  /rest/v1/profiles?select=firebase_uid&limit=1                   -> 401/403 (must NOT be 200)
--     GET  /rest/v1/profiles?select=*&limit=1                              -> 401/403 (must NOT be 200)
--     POST /rest/v1/rpc/get_suggested_users {"p_limit":5,"p_exclude_user_id":null}
--                                                                          -> 401/403 (revoked)
--     POST /rest/v1/rpc/get_story_groups                                   -> 200
--
--   Negative control: with the OLD client build, POST/PATCH to profiles returns
--   42501 (RETURNING * hits birthday). That is expected and is why the client
--   change ships first.
-- ============================================================================


-- ============================================================================
-- §3  DEPENDENT-OBJECT INTEGRITY — output must equal the §0.4 / §0.5 / §0.6
--     captures. Any difference means the migration touched something it should
--     not have.
-- ============================================================================

-- 3.1 Function security mode / settings / ACL (only the ACL of
--     get_suggested_users(integer,uuid) may differ: no PUBLIC, no anon entry).
select p.oid::regprocedure::text as signature,
       p.prosecdef               as security_definer,
       p.proconfig               as settings,
       p.proacl                  as acl
  from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
 where n.nspname = 'public'
   and p.proname in ('request_uid', 'get_suggested_users', 'get_story_groups',
                     'get_inbox', 'get_unread_counts', 'set_account_privacy',
                     'handle_new_user')
 order by 1;

-- 3.2 Policies: identical to §0.5 (names, roles, cmd, qual, with_check).
select policyname, permissive, roles::text, cmd, qual, with_check
  from pg_policies
 where schemaname = 'public'
   and tablename  = 'profiles'
 order by policyname;

-- 3.3 View definitions: identical to §0.6.
select viewname,
       md5(pg_get_viewdef(('public.' || viewname)::regclass, true)) as def_md5
  from pg_views
 where schemaname = 'public'
   and viewname in ('v_posts', 'v_feed', 'v_saved', 'v_comments', 'v_stories')
 order by viewname;

-- 3.4 No profile data changed: row count and the QA row's identifying fields.
select count(*) as profiles_rows from public.profiles;

select id, username, full_name, birthday
  from public.profiles
 where username = 'qaverify';
