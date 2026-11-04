-- #65 — the chat inbox never showed a call.
--
-- The thread entry for a call has always been built on the device that terminated it
-- (CallThreadEntries), written into the local overlay only. Two consequences:
--
--   1. `get_inbox` reads `messages`, so a conversation whose newest activity was a call
--      still previewed the older text message — or nothing at all.
--   2. The other device saw no call entry until it happened to terminate a call itself.
--
-- The client cannot fix this on its own: `messages_insert` is
-- `sender_id = request_uid() AND is_conversation_member(...)`, and a call entry is
-- attributed to the CALLER on both devices (that is what makes "Missed audio call"
-- render as received for the callee). The callee therefore cannot write that row.
--
-- This function owns the write instead. It is SECURITY DEFINER and owned by postgres
-- (messages has relforcerowsecurity = false), so RLS stays exactly as it is — the
-- authorization it bypasses is replaced by an explicit check that the caller is a
-- party to this call, which is strictly narrower than the insert policy.
--
-- The body is deliberately viewer-NEUTRAL. One row is read by both sides, so a text
-- that said "no answer" to the caller and "missed" to the callee would simply show
-- whichever device called this function last. "Audio call · 53s" and "Missed audio
-- call" are true for both participants.
--
-- Idempotent by construction: the message id IS the call id, so a re-run — both devices
-- calling it, a reconnect, a retry — updates one row instead of adding another.

create or replace function public.record_call_message(p_call_id uuid)
returns void
language plpgsql
security definer
set search_path to 'public'
as $function$
declare
  v_uid  uuid := (select public.request_uid());
  v_call public.calls;
  v_peer uuid;
  v_cid  uuid;
  v_when timestamptz;
begin
  if v_uid is null or p_call_id is null then
    return;
  end if;

  select * into v_call from public.calls where id = p_call_id;
  if not found then
    return;
  end if;

  -- Only a participant may materialise the entry.
  if v_uid <> v_call.caller_id and v_uid <> v_call.callee_id then
    raise exception 'not a participant in this call' using errcode = '42501';
  end if;

  -- Only a finished call: a ringing row has no outcome to describe yet.
  if v_call.status not in ('ended', 'declined', 'missed', 'no_answer') then
    return;
  end if;

  v_peer := case when v_uid = v_call.caller_id then v_call.callee_id else v_call.caller_id end;
  v_cid  := public.get_or_create_conversation(v_peer);
  v_when := coalesce(v_call.ended_at, v_call.connected_at, v_call.created_at, now());

  insert into public.messages (id, conversation_id, sender_id, body, is_read, created_at)
  values (
    v_call.id,
    v_cid,
    v_call.caller_id,
    public.call_message_body(v_call.call_type, v_call.status, coalesce(v_call.duration_sec, 0)),
    true,                                   -- see note below
    v_when
  )
  on conflict (id) do update
    set body = excluded.body,
        conversation_id = excluded.conversation_id;

  update public.conversations set updated_at = v_when where id = v_cid;
end;
$function$;

-- is_read is true on purpose: the call already raises its own notification, and an
-- incoming call row would otherwise inflate the inbox badge with something the user
-- has no "unread message" to open.

-- One place decides the wording, so the runtime write and the backfill below cannot drift.
create or replace function public.call_message_body(p_type text, p_status text, p_dur int)
returns text
language sql
immutable
as $function$
  select case
    when coalesce(p_dur, 0) > 0 then
      (case when p_type = 'video' then 'Video call' else 'Audio call' end)
      || ' · ' ||
      case
        when p_dur < 60 then p_dur || 's'
        else (p_dur / 60) || 'm ' || lpad((p_dur % 60)::text, 2, '0') || 's'
      end
    when p_status = 'declined' then
      (case when p_type = 'video' then 'Video call' else 'Audio call' end) || ' · declined'
    else
      'Missed ' || lower(case when p_type = 'video' then 'Video call' else 'Audio call' end)
  end;
$function$;

revoke all on function public.record_call_message(uuid) from public;
grant execute on function public.record_call_message(uuid) to authenticated;
grant execute on function public.record_call_message(uuid) to service_role;

revoke all on function public.call_message_body(text, text, int) from public;
grant execute on function public.call_message_body(text, text, int) to authenticated;
grant execute on function public.call_message_body(text, text, int) to service_role;

-- Backfill the calls that already exist, so the two test devices do not each have to
-- place a fresh call to see the fix. Written as a set-based insert rather than a loop
-- over record_call_message(): that function authorises against request_uid(), which is
-- null while a migration runs, so every iteration would have silently done nothing.
--
-- Only conversations that already exist are backfilled — no synthetic threads are
-- created for historic test calls that were never messaged.
insert into public.messages (id, conversation_id, sender_id, body, is_read, created_at)
select
  c.id,
  mine.conversation_id,
  c.caller_id,
  public.call_message_body(c.call_type, c.status, coalesce(c.duration_sec, 0)),
  true,
  coalesce(c.ended_at, c.connected_at, c.created_at, now())
from public.calls c
join public.conversation_members mine  on mine.user_id = c.caller_id
join public.conversation_members theirs on theirs.conversation_id = mine.conversation_id
                                      and theirs.user_id = c.callee_id
where c.status in ('ended', 'declined', 'missed', 'no_answer')
on conflict (id) do nothing;

update public.conversations conv
   set updated_at = greatest(
         conv.updated_at,
         coalesce((select m.created_at from public.messages m
                    where m.conversation_id = conv.id
                    order by m.created_at desc limit 1), conv.updated_at))
 where exists (select 1 from public.messages m where m.conversation_id = conv.id);
