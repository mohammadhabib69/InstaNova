-- ============================================================================
-- 2026-10-18  Rollback for 2026-10-18_profiles_column_grants_v2.sql
--
-- Restores the exact state measured read-only immediately before the migration
-- artifact was written:
--
--   public.profiles.relacl
--       {postgres=arwdDxtm/postgres, anon=arwdDxtm/postgres,
--        authenticated=arwdDxtm/postgres, service_role=arwdDxtm/postgres}
--       (no PUBLIC entry; RLS enabled)
--   public.profiles.attacl
--       all 13 columns NULL — no column ACLs
--   public.get_suggested_users(integer, uuid)
--       {=X/postgres, postgres=X/postgres, anon=X/postgres,
--        authenticated=X/postgres, service_role=X/postgres}, SECURITY DEFINER
--
-- Everything else is untouched by either file, so it is untouched here too:
-- get_suggested_users(integer), get_or_create_conversation(uuid),
-- get_story_groups(), request_uid(), get_inbox(), get_unread_counts(), every
-- view, every policy and every row.
--
-- WARNING: this restores the pre-Phase-1 posture — anon and authenticated can
-- read birthday and firebase_uid again, and the SECURITY DEFINER RPC is
-- callable by everyone including PUBLIC.
--
-- Idempotent and safe whether or not the migration was applied.
-- ============================================================================

begin;

set local lock_timeout = '5s';

-- 1. Clear both privilege layers.
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

-- 2. Restore the table ACL. ALL is arwdDxtm under PostgreSQL 17 (INSERT, SELECT,
--    UPDATE, DELETE, TRUNCATE, REFERENCES, TRIGGER, MAINTAIN), which is exactly
--    the recorded value for both roles. No column ACLs are created, so attacl
--    stays NULL on all 13 columns.
grant all on table public.profiles to anon, authenticated;

-- 3. Restore the function ACL entry by entry: the PUBLIC (`=X`) entry, then the
--    explicit anon and authenticated entries. service_role and the owner entry
--    were never revoked, so they are not re-granted.
grant execute on function public.get_suggested_users(integer, uuid) to public;
grant execute on function public.get_suggested_users(integer, uuid) to anon;
grant execute on function public.get_suggested_users(integer, uuid) to authenticated;

commit;

-- Confirm with §1 using the pre-Phase-1 expectations.
--
-- Fidelity note: the restored PRIVILEGES are exactly the recorded ones, but the
-- aclitem[] array may store them in a different order (revoking then granting
-- appends entries), so the md5 of relacl/proacl text — and therefore the §0.7
-- fingerprint — is not guaranteed to be byte-identical to
-- 52864b3fdc6dacb9758c4a642b8104a9. Compare semantically instead:
--   * aclexplode(relacl) and aclexplode(proacl) must yield the same
--     (grantee, privilege_type) set as §0.2 / §0.3;
--   * has_column_privilege() must show no column ACLs at all;
--   * the privilege counts per grantee must match.
-- Byte-identical ACL text is not a requirement and not a reliable assertion.
