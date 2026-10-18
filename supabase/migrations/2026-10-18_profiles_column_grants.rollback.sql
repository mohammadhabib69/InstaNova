-- ============================================================================
-- 2026-10-18  Rollback for 2026-10-18_profiles_column_grants.sql
--
-- Restores the exact pre-migration privilege state, as recorded in
-- backups/views_functions_pre_2026-10-18.json and the 2026-10-18 audit:
--
--   public.profiles                        anon=arwdDxtm, authenticated=arwdDxtm
--                                          (table-level, no column ACLs)
--   get_suggested_users(integer, uuid)     {=X, postgres, anon, authenticated,
--                                           service_role}
--
-- WARNING: this rollback restores the pre-Phase-1 posture, i.e. anon can read
-- birthday and firebase_uid again, and anon can execute the SECURITY DEFINER
-- suggestion RPC. It exists for a failed deploy, not for routine use.
--
-- It is safe to run whether or not the migration was applied: every statement
-- is idempotent and the file is a single transaction.
-- ============================================================================

begin;

set local lock_timeout = '5s';

-- 1. Clear both privilege layers so the end state is known, not additive.
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

-- 2. Restore the table-level ACL: ALL is arwdDxtm, the Supabase default for
--    public tables and the recorded pre-migration value for both roles.
grant all on table public.profiles to anon, authenticated;

-- 3. Restore the PUBLIC grant (the `=X` ACL entry) and anon's explicit grant on
--    the SECURITY DEFINER suggestion overload.
grant execute on function public.get_suggested_users(integer, uuid) to public;
grant execute on function public.get_suggested_users(integer, uuid) to anon;

commit;

-- Verify with §1 of 2026-10-18_profiles_column_grants.verify.sql using the
-- "pre-Phase-1" expectations, or simply re-run the §0 snapshot and compare it
-- with the copy saved before the migration was attempted.
