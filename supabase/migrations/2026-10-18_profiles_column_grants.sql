-- ============================================================================
-- 2026-10-18  Phase 1 profile privacy: column-level grants on public.profiles
--
-- Goal (Phase 1 only)
--   * anon may read only the profile columns that public, security_invoker views
--     and anon-executable invoker functions actually reference;
--   * anon may not read birthday or firebase_uid;
--   * authenticated keeps firebase_uid temporarily (signup resolves a row by it);
--   * no row, policy, view, function body, security mode or identity expression
--     changes;
--   * TRUNCATE / REFERENCES / TRIGGER leave both client roles (TRUNCATE is not
--     subject to row level security).
--
-- Not in this migration (documented separately, see docs/privacy-phase1-db-preflight.md)
--   * profiles.is_private does not exist; set_account_privacy(boolean) is broken;
--   * SettingsRepository selects email / is_private (42703 today);
--   * Phase 2 (current_profile() RPC, removing firebase_uid from the client).
--
-- Ordering requirement
--   The Android client must already send explicit `select` projections on every
--   profiles read AND on every POST/PATCH (Prefer: return=representation compiles
--   to RETURNING *, which would ask for birthday and fail with 42501). That work
--   is committed in the working tree (docs/privacy-phase1-android.md); do not run
--   this file against a build that predates it.
--
-- Rollback: 2026-10-18_profiles_column_grants.rollback.sql
-- Checks:   2026-10-18_profiles_column_grants.verify.sql
-- ============================================================================

begin;

-- Never queue behind a long transaction on this table during a deploy.
set local lock_timeout = '5s';

-- ----------------------------------------------------------------------------
-- 1. Strip every privilege the two client roles hold on public.profiles.
--    Table-level privileges and column-level privileges are stored separately
--    (pg_class.relacl vs pg_attribute.attacl), so REVOKE ALL ON TABLE alone
--    would leave any pre-existing column grants in place.
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
-- 2. anon: strictly the columns the anonymous read paths resolve.
--
--    Consumer                                     -> columns
--    v_posts / v_feed / v_comments / v_stories      id (join), username,
--    (security_invoker, granted to anon)            avatar_url, is_verified
--    get_story_groups() (invoker, anon executable)  username, avatar_url
--    get_suggested_users(int) (invoker)             NOT granted to anon
--
--    No anonymous screen reads full_name, bio, website, the counters or
--    created_at, and the anonymous login path uses SECURITY DEFINER RPCs
--    (get_email_by_username, is_username_available), so they are not granted.
-- ----------------------------------------------------------------------------
grant select (id, username, avatar_url, is_verified)
  on public.profiles to anon;

-- ----------------------------------------------------------------------------
-- 3. authenticated: the full public surface, plus the Phase 1 firebase_uid
--    bridge. birthday is deliberately absent: nothing in the client reads it
--    (see ProfileColumns) and only a SECURITY DEFINER/INVOKER mismatch would
--    need it.
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

-- Phase 1 bridge only: AuthRepository.loadProfileRow resolves the profile row
-- for a fresh Firebase signup. Phase 2 replaces it with a current_profile() RPC.
grant select (firebase_uid) on public.profiles to authenticated;

-- ----------------------------------------------------------------------------
-- 4. Writes (authenticated only: profiles_insert_own / profiles_update_own are
--    `to authenticated`, and anon has no write policy at all).
--
--    INSERT body from AuthRepository.createProfile:
--        id, username, full_name, birthday, firebase_uid
--    PATCH bodies from patchInternal / BioEditFragment / WizardVibeFragment /
--    ProfileFragment avatar:
--        username, full_name, birthday, bio, avatar_url
--    RETURNING (select=WRITE_ECHO): id, username, full_name, avatar_url
--    ----------------------------------------------------------------------
grant insert (id, username, full_name, birthday, firebase_uid)
  on public.profiles to authenticated;

grant update (username, full_name, birthday, bio, avatar_url)
  on public.profiles to authenticated;

-- ----------------------------------------------------------------------------
-- 5. Close the only remaining anonymous path to sensitive columns:
--    get_suggested_users(integer, uuid) is SECURITY DEFINER and returns
--    `setof profiles`, so column grants cannot constrain it. Its ACL is
--    {=X/postgres, postgres, anon, authenticated, service_role} — the `=X`
--    entry is PUBLIC, so revoking from anon alone would not be enough.
--    No client call site exists (SearchRepository uses GET /rest/v1/profiles).
--    The function body, its security mode and its identity logic are untouched.
-- ----------------------------------------------------------------------------
revoke execute on function public.get_suggested_users(integer, uuid)
  from public, anon;

commit;

-- ----------------------------------------------------------------------------
-- Post-apply
--   1. Run 2026-10-18_profiles_column_grants.verify.sql (§1 assertions).
--   2. Run the HTTP smoke checks listed in §2 of the same file.
--   3. If any assertion fails: run the rollback file. All statements here are
--      transactional, so an aborted transaction leaves no partial state.
-- ----------------------------------------------------------------------------
