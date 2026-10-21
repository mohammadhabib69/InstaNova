-- Prompt K: idempotent missed-call surfacing and push dispatch markers.
begin;

alter table public.calls
  add column if not exists callee_surfaced_at timestamptz,
  add column if not exists push_dispatched_at timestamptz;

comment on column public.calls.callee_surfaced_at is
  'Set atomically when the callee has surfaced a missed/no_answer call.';
comment on column public.calls.push_dispatched_at is
  'Set atomically by send-call-push before the single FCM dispatch.';

commit;
