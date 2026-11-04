-- Reverses 2026-11-02_message_requests.sql.
--
-- Restores the previous `get_inbox` signature first: dropping the extra column while
-- the function still declares it is not possible, and any client that pinned the old
-- eight-column shape keeps working afterwards.

drop function if exists public.accept_message_request(uuid);
drop function if exists public.decline_message_request(uuid);

drop trigger if exists trg_message_request_state on public.messages;
drop function if exists public.tg_message_request_state();

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
            unread_count     bigint
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
           and m.is_read = false)                                        as unread_count
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
 order by coalesce(lm.created_at, c.created_at) desc;
$function$;

alter table public.conversation_members
    drop constraint if exists conversation_members_request_state_check;
alter table public.conversation_members
    drop column if exists request_state;
