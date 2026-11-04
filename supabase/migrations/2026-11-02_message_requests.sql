-- #55 — a DM from someone you do not follow belongs in Message Requests, not the inbox.
--
-- Before this, nothing below the UI modelled a request. `MessageRequestsActivity`
-- filtered `getConversations()` by a purely local restrict list, so it was empty for
-- every sender the user had not manually restricted, and a stranger's first message
-- appeared inline in the inbox like any other thread. Settings and the Help Center
-- both advertised the behaviour anyway.
--
-- The state belongs to the *recipient*, not to the conversation: the same thread is an
-- ordinary outgoing conversation for the person who sent it. So it lives on
-- `conversation_members`, one row per participant, which is also the row `get_inbox`
-- already joins on for the viewer.
--
-- Lifecycle:
--   pending   → first message from a non-follower, until the recipient acts
--   accepted  → in the inbox; also the default, so every existing thread is untouched
--   declined  → hidden from both lists
--
-- RLS is not relaxed anywhere. The trigger has to touch the *other* member's row, which
-- the sender could not do under `conversation_members` policies, so the function is
-- SECURITY DEFINER and does only this one, fully derived update.

alter table public.conversation_members
    add column if not exists request_state text not null default 'accepted';

alter table public.conversation_members
    drop constraint if exists conversation_members_request_state_check;
alter table public.conversation_members
    add constraint conversation_members_request_state_check
    check (request_state in ('accepted', 'pending', 'declined'));

-- Marks the recipient's view of a thread as a request unless the sender already
-- follows them. "Accepted" is a latch: once the recipient accepts, later messages from
-- the same person do not push the thread back into requests, including after they
-- unfollow. A future follow by the sender promotes a pending request to the inbox,
-- which is what following someone back should do.
create or replace function public.tg_message_request_state()
    returns trigger
    language plpgsql
    security definer
    set search_path to 'public'
as
$$
begin
    update public.conversation_members cm
       set request_state = case
               when exists (select 1
                              from public.follows f
                             where f.follower_id = new.sender_id
                               and f.following_id = cm.user_id)
                   then 'accepted'
               when cm.request_state = 'accepted' then 'accepted'
               else 'pending'
           end
     where cm.conversation_id = new.conversation_id
       and cm.user_id <> new.sender_id;
    return null;
end;
$$;

drop trigger if exists trg_message_request_state on public.messages;
create trigger trg_message_request_state
    after insert on public.messages
    for each row
execute function public.tg_message_request_state();

-- `is_request` is additive, so the client that ignores it keeps working. Declined
-- threads are dropped entirely: they are not requests any more and must not reappear
-- in the inbox.
drop function if exists public.get_inbox();

create or replace function public.get_inbox()
    returns table
        (
            conversation_id  uuid,
            other_user_id    uuid,
            other_username   text,
            other_avatar_url text,
            last_message     text,
            last_message_at  timestamptz,
            unread_count     bigint,
            is_request       boolean
        )
    language sql
    security definer
    set search_path to 'public'
as
$function$
select c.id                                                              as conversation_id,
       op.id                                                             as other_user_id,
       op.username                                                       as other_username,
       op.avatar_url                                                     as other_avatar_url,
       lm.body                                                           as last_message,
       lm.created_at                                                     as last_message_at,
       (select count(*)
          from public.messages m
         where m.conversation_id = c.id
           and m.sender_id <> (select public.request_uid())
           and m.is_read = false)                                        as unread_count,
       (me.request_state = 'pending')                                    as is_request
  from public.conversations c
  join public.conversation_members me
    on me.conversation_id = c.id and me.user_id = (select public.request_uid())
  join public.conversation_members other
    on other.conversation_id = c.id and other.user_id <> (select public.request_uid())
  join public.profiles op on op.id = other.user_id
  left join lateral (
    select m.body, m.created_at
      from public.messages m
     where m.conversation_id = c.id
     order by m.created_at desc
     limit 1
  ) lm on true
 where me.request_state <> 'declined'
 order by coalesce(lm.created_at, c.created_at) desc;
$function$;

-- Accepting only touches the caller's own membership row, so it needs no elevated
-- rights beyond what the member already has; being SECURITY DEFINER is what guarantees
-- the recipient can act on a row the trigger created for them.
create or replace function public.accept_message_request(p_conversation_id uuid)
    returns void
    language plpgsql
    security definer
    set search_path to 'public'
as
$$
declare
    v_uid uuid := public.request_uid();
begin
    if v_uid is null then
        raise exception 'not authenticated';
    end if;
    update public.conversation_members
       set request_state = 'accepted'
     where conversation_id = p_conversation_id
       and user_id = v_uid;
end;
$$;

create or replace function public.decline_message_request(p_conversation_id uuid)
    returns void
    language plpgsql
    security definer
    set search_path to 'public'
as
$$
declare
    v_uid uuid := public.request_uid();
begin
    if v_uid is null then
        raise exception 'not authenticated';
    end if;
    update public.conversation_members
       set request_state = 'declined'
     where conversation_id = p_conversation_id
       and user_id = v_uid
       and request_state = 'pending';
end;
$$;

revoke all on function public.accept_message_request(uuid) from public, anon;
revoke all on function public.decline_message_request(uuid) from public, anon;
grant execute on function public.accept_message_request(uuid) to authenticated, service_role;
grant execute on function public.decline_message_request(uuid) to authenticated, service_role;
