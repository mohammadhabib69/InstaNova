-- ============================================================================
-- 2026-10-20  Calling System Migration (Audio, Video, WebRTC Signaling, History)
--
-- Tables created:
--   1. public.call_sessions: tracks active & historical calls (audio & video)
--   2. public.call_signals: WebRTC signaling (offer, answer, ICE candidates)
--   3. public.user_devices: FCM device registration for incoming call pushes
--
-- Security:
--   - RLS strictly enforced using public.request_uid().
--   - Users can only view and manipulate call sessions and signals where
--     they are the authenticated caller or recipient.
--   - No arbitrary user can view, join, or inject signals into another user's call.
-- ============================================================================

begin;

set local lock_timeout = '5s';

-- 1. Call Sessions Table -----------------------------------------------------
create table if not exists public.call_sessions (
    id uuid primary key default gen_random_uuid(),
    conversation_id uuid references public.conversations(id) on delete set null,
    caller_id uuid not null references public.profiles(id) on delete cascade,
    recipient_id uuid not null references public.profiles(id) on delete cascade,
    call_type text not null check (call_type in ('audio', 'video')),
    status text not null default 'ringing' check (status in ('ringing', 'accepted', 'rejected', 'ended', 'missed', 'cancelled', 'failed')),
    started_at timestamptz not null default now(),
    answered_at timestamptz,
    ended_at timestamptz,
    duration_seconds integer not null default 0,
    ended_by uuid references public.profiles(id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_call_sessions_caller on public.call_sessions (caller_id, created_at desc);
create index if not exists idx_call_sessions_recipient on public.call_sessions (recipient_id, created_at desc);
create index if not exists idx_call_sessions_conversation on public.call_sessions (conversation_id, created_at desc);
create index if not exists idx_call_sessions_status on public.call_sessions (status);

alter table public.call_sessions enable row level security;

drop policy if exists call_sessions_select on public.call_sessions;
create policy call_sessions_select on public.call_sessions for select to authenticated
    using (caller_id = (select public.request_uid()) or recipient_id = (select public.request_uid()));

drop policy if exists call_sessions_insert on public.call_sessions;
create policy call_sessions_insert on public.call_sessions for insert to authenticated
    with check (caller_id = (select public.request_uid()));

drop policy if exists call_sessions_update on public.call_sessions;
create policy call_sessions_update on public.call_sessions for update to authenticated
    using (caller_id = (select public.request_uid()) or recipient_id = (select public.request_uid()))
    with check (caller_id = (select public.request_uid()) or recipient_id = (select public.request_uid()));

grant select, insert, update on public.call_sessions to authenticated;


-- 2. Call Signals Table (WebRTC Signaling) -----------------------------------
create table if not exists public.call_signals (
    id uuid primary key default gen_random_uuid(),
    call_id uuid not null references public.call_sessions(id) on delete cascade,
    sender_id uuid not null references public.profiles(id) on delete cascade,
    recipient_id uuid not null references public.profiles(id) on delete cascade,
    signal_type text not null check (signal_type in ('offer', 'answer', 'ice_candidate', 'reject', 'end')),
    payload text not null,
    created_at timestamptz not null default now()
);

create index if not exists idx_call_signals_call on public.call_signals (call_id, created_at asc);
create index if not exists idx_call_signals_recipient on public.call_signals (recipient_id, created_at desc);

alter table public.call_signals enable row level security;

drop policy if exists call_signals_select on public.call_signals;
create policy call_signals_select on public.call_signals for select to authenticated
    using (recipient_id = (select public.request_uid()) or sender_id = (select public.request_uid()));

drop policy if exists call_signals_insert on public.call_signals;
create policy call_signals_insert on public.call_signals for insert to authenticated
    with check (sender_id = (select public.request_uid()));

grant select, insert on public.call_signals to authenticated;


-- 3. User Devices Table (FCM Token Registration) -----------------------------
create table if not exists public.user_devices (
    user_id uuid not null references public.profiles(id) on delete cascade,
    fcm_token text not null,
    device_model text,
    platform text not null default 'android',
    updated_at timestamptz not null default now(),
    primary key (user_id, fcm_token)
);

create index if not exists idx_user_devices_user on public.user_devices (user_id);

alter table public.user_devices enable row level security;

drop policy if exists user_devices_select on public.user_devices;
create policy user_devices_select on public.user_devices for select to authenticated
    using (user_id = (select public.request_uid()));

drop policy if exists user_devices_insert on public.user_devices;
create policy user_devices_insert on public.user_devices for insert to authenticated
    with check (user_id = (select public.request_uid()));

drop policy if exists user_devices_update on public.user_devices;
create policy user_devices_update on public.user_devices for update to authenticated
    using (user_id = (select public.request_uid()))
    with check (user_id = (select public.request_uid()));

drop policy if exists user_devices_delete on public.user_devices;
create policy user_devices_delete on public.user_devices for delete to authenticated
    using (user_id = (select public.request_uid()));

grant select, insert, update, delete on public.user_devices to authenticated;

commit;
