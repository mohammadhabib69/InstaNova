-- ============================================================================
-- 2026-10-22  Prompt J — real two-device call signaling
--
-- Adds:
--   1. public.calls   — the single authoritative call record + state machine
--   2. public.profiles.fcm_token — per-user push token (column grant only)
--   3. Realtime publication for public.calls (postgres_changes)
--
-- Why a new table and not call_sessions:
--   call_sessions (2026-10-20) predates Prompt J. It has recipient_id instead
--   of callee_id, answered_at instead of connected_at, duration_seconds
--   instead of duration_sec, no ringing_at, and its status vocabulary
--   (ringing/accepted/rejected/cancelled/failed) cannot express the two
--   distinct pre-answer states Prompt J requires ('calling' vs 'ringing') or
--   'no_answer'. A vocabulary that cannot represent 'calling' makes the
--   caller-ack rule unrepresentable, so the table is created fresh rather
--   than migrated in place. call_sessions is left untouched and still backs
--   the WebRTC SDP/ICE exchange (call_signals).
--
-- State machine encoded in the status CHECK:
--   calling    caller inserted the row, callee has not acknowledged yet
--   ringing    callee device displayed the incoming call  (THE ACK)
--   connected  callee answered; connected_at is the shared timer origin
--   ended      somebody hung up; ended_by + duration_sec are written
--   declined   callee explicitly declined
--   missed     rang for 30s with no answer
--   no_answer  never acked within 45s of creation
--
-- Security: RLS strictly enforced through public.request_uid(). A user can
-- only see and write rows where they are the caller or the callee, so the
-- Realtime subscription inherits exactly the same visibility.
-- ============================================================================

begin;

set local lock_timeout = '5s';

-- ----------------------------------------------------------------------------
-- 1. calls table
-- ----------------------------------------------------------------------------
create table if not exists public.calls (
    id uuid primary key default gen_random_uuid(),
    caller_id uuid not null references public.profiles(id) on delete cascade,
    callee_id uuid not null references public.profiles(id) on delete cascade,
    call_type text not null check (call_type in ('audio', 'video')),
    status text not null default 'calling'
        check (status in (
            'calling', 'ringing', 'connected', 'ended',
            'declined', 'missed', 'no_answer'
        )),
    created_at timestamptz not null default now(),
    ringing_at timestamptz,
    connected_at timestamptz,
    ended_at timestamptz,
    ended_by uuid references public.profiles(id) on delete set null,
    duration_sec integer not null default 0,
    -- A call to yourself can never legitimately occur; without this the
    -- caller could satisfy the RLS predicate on both sides of one row.
    constraint calls_participants_differ check (caller_id <> callee_id)
);

create index if not exists idx_calls_callee_created
    on public.calls (callee_id, created_at desc);
create index if not exists idx_calls_caller_created
    on public.calls (caller_id, created_at desc);
create index if not exists idx_calls_status
    on public.calls (status);

alter table public.calls enable row level security;

drop policy if exists calls_select on public.calls;
create policy calls_select on public.calls for select to authenticated
    using (caller_id = (select public.request_uid())
           or callee_id = (select public.request_uid()));

drop policy if exists calls_insert on public.calls;
create policy calls_insert on public.calls for insert to authenticated
    with check (caller_id = (select public.request_uid()));

drop policy if exists calls_update on public.calls;
create policy calls_update on public.calls for update to authenticated
    using (caller_id = (select public.request_uid())
           or callee_id = (select public.request_uid()))
    with check (caller_id = (select public.request_uid())
                or callee_id = (select public.request_uid()));

grant select, insert, update on public.calls to authenticated;

-- ----------------------------------------------------------------------------
-- 2. profiles.fcm_token
--
-- 2026-10-18_profiles_column_grants_v2 stripped table-level privileges from
-- anon/authenticated and re-granted column by column, so adding a column is
-- not enough — it needs its own column grants or every write will fail with
-- 42501 (permission denied for table profiles).
--
-- select is required because the client must not blind itself to the column
-- on RETURNING *, and update is restricted to this one column so a push-token
-- write cannot be used to edit profile identity fields.
-- ----------------------------------------------------------------------------
alter table public.profiles add column if not exists fcm_token text;

grant select (fcm_token) on public.profiles to authenticated;
grant update (fcm_token) on public.profiles to authenticated;

-- ----------------------------------------------------------------------------
-- 3. Realtime
--
-- postgres_changes delivery is driven by the publication. The guard makes the
-- migration re-runnable: a bare ADD TABLE raises 42710 ("relation is already
-- member of publication") on every re-run.
-- ----------------------------------------------------------------------------
do $$
begin
    if not exists (
        select 1
          from pg_publication_tables
         where pubname    = 'supabase_realtime'
           and schemaname = 'public'
           and tablename  = 'calls'
    ) then
        alter publication supabase_realtime add table public.calls;
    end if;
end
$$;

commit;

-- Post-apply checks:
--   1. select count(*) from pg_publication_tables
--      where pubname='supabase_realtime' and tablename='calls';   -- expect 1
--   2. select relreplident from pg_class
--      where oid = 'public.calls'::regclass;                      -- expect 'd'
--   3. information_schema.columns for profiles.fcm_token           -- expect 1 row
--   4. \dp public.calls                                           -- expect the
--      three policies above and select,insert,update for authenticated