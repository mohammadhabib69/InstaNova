-- ============================================================================
-- 2026-10-18  Verification for 2026-10-18_profiles_column_grants_v2.sql
--
-- READ-ONLY: SELECT, has_*_privilege, aclexplode, md5. Nothing here writes.
-- Run §0 before applying and keep the output; run §1/§3 after; run §2 over HTTP.
-- ============================================================================


-- ============================================================================
-- §0  PRE-FLIGHT SNAPSHOT (before)
-- ============================================================================

-- 0.1 Table ACL + column ACL count.
--     Expected: {postgres=arwdDxtm,anon=arwdDxtm,authenticated=arwdDxtm,
--                service_role=arwdDxtm}, rls = true, cols = 13, cols_with_acl = 0
select c.relacl::text as relacl,
       c.relrowsecurity as rls,
       (select count(*) from pg_attribute a
         where a.attrelid = c.oid and a.attnum > 0 and not a.attisdropped) as cols,
       (select count(*) from pg_attribute a
         where a.attrelid = c.oid and a.attnum > 0 and not a.attisdropped
           and a.attacl is not null) as cols_with_acl
  from pg_class c
  join pg_namespace n on n.oid = c.relnamespace
 where n.nspname = 'public' and c.relname = 'profiles';

-- 0.2 Every table-level privilege entry, expanded (grantee 0 = PUBLIC).
select case when x.grantee = 0 then 'PUBLIC' else pg_get_userbyid(x.grantee) end as grantee,
       x.privilege_type
  from aclexplode((select relacl from pg_class where oid = 'public.profiles'::regclass)) x
 order by 1, 2;

-- 0.3 Function security mode, settings, ACL and effective EXECUTE.
select p.oid::regprocedure::text as signature,
       p.prosecdef as security_definer,
       p.proconfig as settings,
       p.proacl::text as acl,
       has_function_privilege('anon', p.oid, 'execute')          as anon_exec,
       has_function_privilege('authenticated', p.oid, 'execute') as auth_exec,
       has_function_privilege('service_role', p.oid, 'execute')  as svc_exec
  from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
 where n.nspname = 'public'
   and p.proname in ('request_uid', 'get_suggested_users', 'get_story_groups',
                     'get_or_create_conversation', 'get_inbox', 'get_unread_counts')
 order by 1;

-- 0.4 RLS policies on public.profiles (must be identical before and after).
select policyname, permissive, roles::text, cmd, qual, with_check
  from pg_policies
 where schemaname = 'public' and tablename = 'profiles'
 order by policyname;

-- 0.5 View definition fingerprints (must be identical before and after).
select viewname,
       md5(pg_get_viewdef(('public.' || viewname)::regclass, true)) as def_md5
  from pg_views
 where schemaname = 'public'
   and viewname in ('v_posts', 'v_feed', 'v_saved', 'v_comments', 'v_stories')
 order by viewname;

-- 0.6 Public-schema ACL manifest — one row per relation/function. Save this
--     output and diff it against §3.6. Only two lines may differ:
--     rel:public.profiles and fn:get_suggested_users(integer,uuid).
select kind || ':' || name as object, acl_md5
  from (
        select 'rel' as kind, n.nspname || '.' || c.relname as name,
               md5(coalesce(c.relacl::text, '')) as acl_md5
          from pg_class c
          join pg_namespace n on n.oid = c.relnamespace
         where n.nspname = 'public'
           and c.relkind in ('r', 'v', 'm', 'p', 'f')
        union all
        select 'fn' as kind, p.oid::regprocedure::text as name,
               md5(coalesce(p.proacl::text, '')) as acl_md5
          from pg_proc p
          join pg_namespace n2 on n2.oid = p.pronamespace
         where n2.nspname = 'public'
       ) s
 order by 1;

-- 0.7 Anchor values to record: ACL fingerprint and row counts.
--     Expected fingerprint before: 52864b3fdc6dacb9758c4a642b8104a9 / 39 objects
select md5(string_agg(object || ':' || acl_md5, '|' order by object)) as acl_fingerprint,
       count(*) as objects
  from (
        select 'rel' as kind, n.nspname || '.' || c.relname as object,
               md5(coalesce(c.relacl::text, '')) as acl_md5
          from pg_class c
          join pg_namespace n on n.oid = c.relnamespace
         where n.nspname = 'public'
           and c.relkind in ('r', 'v', 'm', 'p', 'f')
        union all
        select 'fn' as kind, p.oid::regprocedure::text as object,
               md5(coalesce(p.proacl::text, '')) as acl_md5
          from pg_proc p
          join pg_namespace n2 on n2.oid = p.pronamespace
         where n2.nspname = 'public'
       ) s;

select count(*) as profiles_rows from public.profiles;


-- ============================================================================
-- §1  POST-APPLY ASSERTIONS — every row must read PASS
-- ============================================================================

with checks(name, actual, expected) as (
  values
    -- ---- no table-level SELECT can bypass the column restrictions ----------
    ('anon  table SELECT                 = false', has_table_privilege('anon', 'public.profiles', 'select'), false),
    ('auth  table SELECT                 = false', has_table_privilege('authenticated', 'public.profiles', 'select'), false),
    ('no PUBLIC table entry             = false', exists (
        select 1 from aclexplode((select relacl from pg_class where oid = 'public.profiles'::regclass)) x
         where x.grantee = 0), false),

    -- ---- anon: no sensitive read, no write, no TRUNCATE --------------------
    ('anon  select birthday              = false', has_column_privilege('anon', 'public.profiles', 'birthday', 'select'), false),
    ('anon  select firebase_uid          = false', has_column_privilege('anon', 'public.profiles', 'firebase_uid', 'select'), false),
    ('anon  select id                    = true',  has_column_privilege('anon', 'public.profiles', 'id', 'select'), true),
    ('anon  select username              = true',  has_column_privilege('anon', 'public.profiles', 'username', 'select'), true),
    ('anon  select avatar_url            = true',  has_column_privilege('anon', 'public.profiles', 'avatar_url', 'select'), true),
    ('anon  select is_verified           = true',  has_column_privilege('anon', 'public.profiles', 'is_verified', 'select'), true),
    ('anon  select full_name             = false', has_column_privilege('anon', 'public.profiles', 'full_name', 'select'), false),
    ('anon  select created_at            = false', has_column_privilege('anon', 'public.profiles', 'created_at', 'select'), false),
    ('anon  table insert                 = false', has_table_privilege('anon', 'public.profiles', 'insert'), false),
    ('anon  table update                 = false', has_table_privilege('anon', 'public.profiles', 'update'), false),
    ('anon  table delete                 = false', has_table_privilege('anon', 'public.profiles', 'delete'), false),
    ('anon  table truncate               = false', has_table_privilege('anon', 'public.profiles', 'truncate'), false),
    ('anon  table references             = false', has_table_privilege('anon', 'public.profiles', 'references'), false),
    ('anon  table trigger                = false', has_table_privilege('anon', 'public.profiles', 'trigger'), false),

    -- ---- authenticated: public surface + Phase 1 firebase_uid, no birthday --
    ('auth  select id                    = true',  has_column_privilege('authenticated', 'public.profiles', 'id', 'select'), true),
    ('auth  select username              = true',  has_column_privilege('authenticated', 'public.profiles', 'username', 'select'), true),
    ('auth  select full_name             = true',  has_column_privilege('authenticated', 'public.profiles', 'full_name', 'select'), true),
    ('auth  select bio                   = true',  has_column_privilege('authenticated', 'public.profiles', 'bio', 'select'), true),
    ('auth  select avatar_url            = true',  has_column_privilege('authenticated', 'public.profiles', 'avatar_url', 'select'), true),
    ('auth  select website               = true',  has_column_privilege('authenticated', 'public.profiles', 'website', 'select'), true),
    ('auth  select is_verified           = true',  has_column_privilege('authenticated', 'public.profiles', 'is_verified', 'select'), true),
    ('auth  select posts_count           = true',  has_column_privilege('authenticated', 'public.profiles', 'posts_count', 'select'), true),
    -- ORDER BY followers_count.desc, created_at.desc
    ('auth  select followers_count       = true',  has_column_privilege('authenticated', 'public.profiles', 'followers_count', 'select'), true),
    ('auth  select following_count       = true',  has_column_privilege('authenticated', 'public.profiles', 'following_count', 'select'), true),
    ('auth  select created_at            = true',  has_column_privilege('authenticated', 'public.profiles', 'created_at', 'select'), true),
    -- Phase 1 bridge
    ('auth  select firebase_uid          = true',  has_column_privilege('authenticated', 'public.profiles', 'firebase_uid', 'select'), true),
    ('auth  select birthday              = false', has_column_privilege('authenticated', 'public.profiles', 'birthday', 'select'), false),
    -- INSERT body
    ('auth  insert id                    = true',  has_column_privilege('authenticated', 'public.profiles', 'id', 'insert'), true),
    ('auth  insert username              = true',  has_column_privilege('authenticated', 'public.profiles', 'username', 'insert'), true),
    ('auth  insert full_name             = true',  has_column_privilege('authenticated', 'public.profiles', 'full_name', 'insert'), true),
    ('auth  insert birthday              = true',  has_column_privilege('authenticated', 'public.profiles', 'birthday', 'insert'), true),
    ('auth  insert firebase_uid          = true',  has_column_privilege('authenticated', 'public.profiles', 'firebase_uid', 'insert'), true),
    -- UPDATE bodies
    ('auth  update username              = true',  has_column_privilege('authenticated', 'public.profiles', 'username', 'update'), true),
    ('auth  update full_name             = true',  has_column_privilege('authenticated', 'public.profiles', 'full_name', 'update'), true),
    ('auth  update birthday              = true',  has_column_privilege('authenticated', 'public.profiles', 'birthday', 'update'), true),
    ('auth  update bio                   = true',  has_column_privilege('authenticated', 'public.profiles', 'bio', 'update'), true),
    ('auth  update avatar_url            = true',  has_column_privilege('authenticated', 'public.profiles', 'avatar_url', 'update'), true),
    -- counters stay trigger-maintained; identity is not client-writable
    ('auth  update posts_count           = false', has_column_privilege('authenticated', 'public.profiles', 'posts_count', 'update'), false),
    ('auth  update firebase_uid          = false', has_column_privilege('authenticated', 'public.profiles', 'firebase_uid', 'update'), false),
    ('auth  table truncate               = false', has_table_privilege('authenticated', 'public.profiles', 'truncate'), false),
    ('auth  table references             = false', has_table_privilege('authenticated', 'public.profiles', 'references'), false),
    ('auth  table trigger                = false', has_table_privilege('authenticated', 'public.profiles', 'trigger'), false),

    -- ---- D4: the SECURITY DEFINER RPC is unreachable for clients -----------
    ('anon  execute get_suggested_users(int,uuid) = false',
        has_function_privilege('anon', 'public.get_suggested_users(integer,uuid)', 'execute'), false),
    ('auth  execute get_suggested_users(int,uuid) = false',
        has_function_privilege('authenticated', 'public.get_suggested_users(integer,uuid)', 'execute'), false),
    ('svc   execute get_suggested_users(int,uuid) = true',
        has_function_privilege('service_role', 'public.get_suggested_users(integer,uuid)', 'execute'), true),
    ('no PUBLIC execute on get_suggested_users(int,uuid) = false', exists (
        select 1 from aclexplode((select proacl from pg_proc
                                   where oid = 'public.get_suggested_users(integer,uuid)'::regprocedure)) x
         where x.grantee = 0), false),

    -- ---- D5/D6 and other functions are untouched ---------------------------
    ('auth  execute get_suggested_users(int)     = true',
        has_function_privilege('authenticated', 'public.get_suggested_users(integer)', 'execute'), true),
    ('anon  execute get_suggested_users(int)     = false',
        has_function_privilege('anon', 'public.get_suggested_users(integer)', 'execute'), false),
    ('anon  execute get_or_create_conversation   = true',
        has_function_privilege('anon', 'public.get_or_create_conversation(uuid)', 'execute'), true),
    ('auth  execute get_or_create_conversation   = true',
        has_function_privilege('authenticated', 'public.get_or_create_conversation(uuid)', 'execute'), true),
    ('anon  execute get_story_groups             = true',
        has_function_privilege('anon', 'public.get_story_groups()', 'execute'), true),
    ('anon  execute request_uid                  = true',
        has_function_privilege('anon', 'public.request_uid()', 'execute'), true),
    ('anon  execute get_inbox                    = false',
        has_function_privilege('anon', 'public.get_inbox()', 'execute'), false),
    ('svc   table SELECT on profiles             = true',
        has_table_privilege('service_role', 'public.profiles', 'select'), true)
)
select name, actual, expected,
       case when actual = expected then 'PASS' else 'FAIL' end as result
  from checks
 order by result desc, name;

-- Summary: failed must be 0.
with checks(actual, expected) as (
  values
    (has_table_privilege('anon', 'public.profiles', 'select'), false),
    (has_table_privilege('authenticated', 'public.profiles', 'select'), false),
    (has_column_privilege('anon', 'public.profiles', 'birthday', 'select'), false),
    (has_column_privilege('anon', 'public.profiles', 'firebase_uid', 'select'), false),
    (has_column_privilege('authenticated', 'public.profiles', 'birthday', 'select'), false),
    (has_column_privilege('authenticated', 'public.profiles', 'firebase_uid', 'select'), true),
    (has_function_privilege('anon', 'public.get_suggested_users(integer,uuid)', 'execute'), false),
    (has_function_privilege('authenticated', 'public.get_suggested_users(integer,uuid)', 'execute'), false),
    (has_function_privilege('service_role', 'public.get_suggested_users(integer,uuid)', 'execute'), true),
    (has_table_privilege('anon', 'public.profiles', 'truncate'), false),
    (has_table_privilege('authenticated', 'public.profiles', 'truncate'), false)
)
select count(*) filter (where actual <> expected) as failed, count(*) as total
  from checks;


-- ============================================================================
-- §2  HTTP SMOKE CHECKS (PostgREST; run after §1 with the projection build)
--
-- authenticated (Authorization: Bearer <firebase id token>)
--   GET   /rest/v1/profiles?select=<PUBLIC>&id=eq.<id>                 -> 200
--   GET   /rest/v1/profiles?select=<SEARCH>&order=followers_count.desc,created_at.desc&limit=15 -> 200
--   GET   /rest/v1/profiles?select=<SEARCH>&or=(username.ilike.*qa*,full_name.ilike.*qa*) -> 200
--   GET   /rest/v1/profiles?select=firebase_uid&limit=1               -> 200   (Phase 1 bridge)
--   GET   /rest/v1/profiles?select=birthday&limit=1                    -> 401/403
--   GET   /rest/v1/profiles?select=*&limit=1                           -> 401/403
--   POST  /rest/v1/profiles?select=<WRITE_ECHO> (return=representation) -> 201, exactly 1 row
--   PATCH /rest/v1/profiles?id=eq.<id>&select=<WRITE_ECHO> {avatar_url} -> 200, exactly 1 row
--   POST  /rest/v1/rpc/get_suggested_users {"p_limit":5,"p_exclude_user_id":null} -> 401/403  (D4)
--   POST  /rest/v1/rpc/get_unread_counts                               -> 200
--   POST  /rest/v1/rpc/get_inbox                                       -> 200
--   GET   /rest/v1/v_stories?limit=5                                   -> 200
--
-- anonymous (apikey only, no bearer)
--   GET   /rest/v1/v_feed?limit=5                                      -> 200
--   GET   /rest/v1/v_posts?limit=5                                     -> 200
--   GET   /rest/v1/v_comments?limit=5                                  -> 200
--   GET   /rest/v1/profiles?select=username,avatar_url,is_verified&limit=5 -> 200
--   GET   /rest/v1/profiles?select=birthday&limit=1                    -> 401/403
--   GET   /rest/v1/profiles?select=firebase_uid&limit=1                -> 401/403
--   GET   /rest/v1/profiles?select=*&limit=1                           -> 401/403
--   POST  /rest/v1/rpc/get_suggested_users {"p_limit":5,"p_exclude_user_id":null} -> 401/403  (D4)
--   POST  /rest/v1/rpc/get_story_groups                                -> 200
--
-- service_role (server-side key, never in the app)
--   POST  /rest/v1/rpc/get_suggested_users {"p_limit":5,"p_exclude_user_id":null} -> 200  (preserved)
--
-- Negative control: the OLD client build issues RETURNING * and must fail with
-- 42501 on profiles writes. That is expected and is why the client ships first.
-- ============================================================================


-- ============================================================================
-- §3  INTEGRITY — compare with the §0 captures
-- ============================================================================

-- 3.1 Policies: identical to §0.4.
select policyname, permissive, roles::text, cmd, qual, with_check
  from pg_policies
 where schemaname = 'public' and tablename = 'profiles'
 order by policyname;

-- 3.2 Views: identical to §0.5.
select viewname,
       md5(pg_get_viewdef(('public.' || viewname)::regclass, true)) as def_md5
  from pg_views
 where schemaname = 'public'
   and viewname in ('v_posts', 'v_feed', 'v_saved', 'v_comments', 'v_stories')
 order by viewname;

-- 3.3 Functions: security mode, settings and return type identical to §0.3.
select p.oid::regprocedure::text as signature,
       p.prosecdef as security_definer,
       p.proconfig as settings,
       pg_get_function_result(p.oid) as returns
  from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
 where n.nspname = 'public'
   and p.proname in ('request_uid', 'get_suggested_users', 'get_story_groups',
                     'get_or_create_conversation', 'get_inbox', 'get_unread_counts')
 order by 1;

-- 3.4 Data untouched: row count and the QA row's identifying fields.
select count(*) as profiles_rows from public.profiles;
select id, username, full_name, birthday from public.profiles where username = 'qaverify';

-- 3.5 Column ACLs after the migration: exactly the granted sets, nothing else.
select grantee, column_name, privilege_type
  from information_schema.column_privileges
 where table_schema = 'public' and table_name = 'profiles'
   and grantee in ('anon', 'authenticated')
 order by grantee, privilege_type, column_name;

-- 3.6 Public-schema ACL manifest: diff against §0.6. Only these two lines may
--     differ: rel:public.profiles and fn:get_suggested_users(integer,uuid).
select kind || ':' || name as object, acl_md5
  from (
        select 'rel' as kind, n.nspname || '.' || c.relname as name,
               md5(coalesce(c.relacl::text, '')) as acl_md5
          from pg_class c
          join pg_namespace n on n.oid = c.relnamespace
         where n.nspname = 'public'
           and c.relkind in ('r', 'v', 'm', 'p', 'f')
        union all
        select 'fn' as kind, p.oid::regprocedure::text as name,
               md5(coalesce(p.proacl::text, '')) as acl_md5
          from pg_proc p
          join pg_namespace n2 on n2.oid = p.pronamespace
         where n2.nspname = 'public'
       ) s
 order by 1;

-- 3.7 Expect a different fingerprint than §0.7 (two objects changed) but the
--     same object count (39). Record the new value for the deployment log.
select md5(string_agg(object || ':' || acl_md5, '|' order by object)) as acl_fingerprint,
       count(*) as objects
  from (
        select 'rel' as kind, n.nspname || '.' || c.relname as object,
               md5(coalesce(c.relacl::text, '')) as acl_md5
          from pg_class c
          join pg_namespace n on n.oid = c.relnamespace
         where n.nspname = 'public'
           and c.relkind in ('r', 'v', 'm', 'p', 'f')
        union all
        select 'fn' as kind, p.oid::regprocedure::text as object,
               md5(coalesce(p.proacl::text, '')) as acl_md5
          from pg_proc p
          join pg_namespace n2 on n2.oid = p.pronamespace
         where n2.nspname = 'public'
       ) s;
