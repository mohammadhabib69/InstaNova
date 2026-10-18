-- ============================================================================
-- 2026-10-18  Phase 1 profile privacy — column grants on public.profiles
--             v2: also revokes the SECURITY DEFINER suggestion RPC from
--             authenticated (v1 revoked it from PUBLIC + anon only).
--
-- Supersedes 2026-10-18_profiles_column_grants.sql (never applied).
--
-- Decisions encoded here
--   D1  birthday is granted to no role.
--   D2  anon keeps only the columns the security_invoker views and
--       get_story_groups() resolve; view behaviour is unchanged.
--   D3  authenticated keeps firebase_uid temporarily for the signup lookup
--       (AuthRepository.loadProfileRow).
--   D4  EXECUTE on public.get_suggested_users(integer, uuid) is revoked from
--       PUBLIC, anon and authenticated: it is SECURITY DEFINER, returns
--       SETOF profiles, has no application caller, and exposes birthday +
--       firebase_uid.
--   D5  public.get_suggested_users(integer) is not modified.
--   D6  public.get_or_create_conversation(uuid) is not modified here; its
--       anonymous EXECUTE grant is a separate hardening item.
--
-- Live baseline captured read-only immediately before this file was written
--   profiles.relacl   {postgres=arwdDxtm,anon=arwdDxtm,authenticated=arwdDxtm,
--                      service_role=arwdDxtm}   (no PUBLIC entry, RLS on)
--   profiles.attacl   all 13 columns NULL — no column ACLs exist
--   get_suggested_users(integer,uuid)  {=X,postgres=X,anon=X,authenticated=X,
--                                       service_role=X}, SECURITY DEFINER
--   get_suggested_users(integer)       {postgres=X,authenticated=X,service_role=X},
--                                       SECURITY INVOKER (untouched)
--   public-schema ACL fingerprint     52864b3fdc6dacb9758c4a642b8104a9 (39 objects)
--
-- Ordering requirement
--   Ship the client build that sends explicit `select` on every profiles read
--   and write first: Prefer: return=representation compiles to RETURNING *,
--   which now hits birthday (42501).
--
-- Rollback: 2026-10-18_profiles_column_grants_v2.rollback.sql
-- Checks:   2026-10-18_profiles_column_grants_v2.verify.sql
-- ============================================================================

begin;

set local lock_timeout = '5s';

-- ----------------------------------------------------------------------------
-- 1. Remove every privilege anon and authenticated hold on public.profiles.
--    Table privileges (pg_class.relacl) and column privileges
--    (pg_attribute.attacl) are stored separately, so both layers are cleared
--    to guarantee a known end state rather than an additive one.
--    postgres (owner entry) and service_role are not named and are preserved.
-- ----------------------------------------------------------------------------
revoke all on table public.profiles from anon, authenticated;

do $$
declare
  col text;
begin
  for col in
    select a.attname
      from pg_attribute a
     where a.attrelid = 'public.profiles'::regclass
       and a.attnum > 0
       and not a.attisdropped
  loop
    execute format(
      'revoke select (%1$I), insert (%1$I), update (%1$I), references (%1$I) '
      'on public.profiles from anon, authenticated', col);
  end loop;
end
$$;

-- ----------------------------------------------------------------------------
-- 2. anon — strictly the columns the anonymous read paths resolve.
--
--    v_posts / v_feed / v_saved / v_comments / v_stories (security_invoker,
--    granted to anon): join pr.id = p.user_id, project username, avatar_url,
--    is_verified.
--    get_story_groups() (SECURITY INVOKER, anon executable): username,
--    avatar_url.
--
--    full_name, bio, website, the counters and created_at are not read by any
--    anonymous path; the anonymous login flow uses the SECURITY DEFINER RPCs
--    get_email_by_username() and is_username_available().
-- ----------------------------------------------------------------------------
grant select (id, username, avatar_url, is_verified)
  on public.profiles to anon;

-- ----------------------------------------------------------------------------
-- 3. authenticated — the public surface the client actually projects, plus the
--    Phase 1 firebase_uid bridge. birthday is deliberately absent (D1).
--
--    followers_count and created_at are required by
--    GET /profiles?select=SEARCH&order=followers_count.desc,created_at.desc:
--    an ORDER BY column needs the column privilege even when it is not in the
--    projection. id is required by every id=eq. / id=neq. filter.
-- ----------------------------------------------------------------------------
grant select (
  id,
  username,
  full_name,
  bio,
  avatar_url,
  website,
  is_verified,
  posts_count,
  followers_count,
  following_count,
  created_at
) on public.profiles to authenticated;

grant select (firebase_uid) on public.profiles to authenticated;

-- ----------------------------------------------------------------------------
-- 4. Writes — authenticated only: profiles_insert_own and profiles_update_own
--    are `to authenticated`; anon has no write policy and gets no privilege.
--
--    INSERT body (AuthRepository.createProfile / ProfileRepository):
--        id, username, full_name, birthday, firebase_uid
--    UPDATE bodies (patchInternal, BioEditFragment, WizardVibeFragment,
--    ProfileFragment avatar): username, full_name, birthday, bio, avatar_url
--    RETURNING (select=WRITE_ECHO): id, username, full_name, avatar_url
-- ----------------------------------------------------------------------------
grant insert (id, username, full_name, birthday, firebase_uid)
  on public.profiles to authenticated;

grant update (username, full_name, birthday, bio, avatar_url)
  on public.profiles to authenticated;

-- ----------------------------------------------------------------------------
-- 5. Close the SECURITY DEFINER leak (D4).
--
--    The function returns SETOF profiles, so column grants cannot constrain it:
--    a caller receives all 13 columns, including birthday and firebase_uid.
--    PUBLIC hold EXECUTE through the `=X` ACL entry and anon/authenticated hold
--    explicit entries, so all three must be revoked.
--    service_role keeps its explicit grant; the owner entry is unaffected.
--    Function body, signature, return type and security mode are untouched.
-- ----------------------------------------------------------------------------
revoke execute on function public.get_suggested_users(integer, uuid)
  from public, anon, authenticated;

commit;

-- Post-apply: run §1 + §3 of the verify file, then the §2 HTTP checks against
-- the projection build. Any FAIL -> run the rollback file (single transaction,
-- fully reversible).
