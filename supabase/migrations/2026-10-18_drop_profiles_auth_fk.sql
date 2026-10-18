-- Removes the Supabase-Auth-only identity rule so Firebase users can own a profile.
-- No rows, columns, policies, functions or other constraints are touched.
-- Rollback: re-add the FK while every profiles.id still exists in auth.users.

set local lock_timeout = '5s';

alter table public.profiles
  drop constraint if exists profiles_id_fkey;
