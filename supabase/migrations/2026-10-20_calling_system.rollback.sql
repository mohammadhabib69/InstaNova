-- ============================================================================
-- 2026-10-20  Calling System Migration Rollback
-- ============================================================================

begin;

drop table if exists public.user_devices cascade;
drop table if exists public.call_signals cascade;
drop table if exists public.call_sessions cascade;

commit;
