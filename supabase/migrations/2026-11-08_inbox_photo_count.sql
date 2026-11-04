-- Inbox preview: "4 photos" instead of a generic "Sent a photo" when the newest message is
-- part of a photo burst. `photo_count` is the trailing run of media messages from the newest
-- sender inside a 60s window, mirroring how the app groups a multi-select into one album.
--
-- This is the 2026-11-02 definition (security definer + public.request_uid(), which resolves
-- the Firebase `sub` to a profile id) with one additive column. Dropping first because the
-- return type changes; grants are restored at the end.

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
            is_request       boolean,
            photo_count      bigint
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
       (me.request_state = 'pending')                                    as is_request,
       case
           when lm.media_url is null then 0::bigint
           else (select count(*)
                   from public.messages m2
                  where m2.conversation_id = c.id
                    and m2.media_url is not null
                    and m2.sender_id = lm.sender_id
                    and m2.created_at <= lm.created_at
                    and m2.created_at > lm.created_at - interval '60 seconds')
       end                                                               as photo_count
  from public.conversations c
  join public.conversation_members me
    on me.conversation_id = c.id and me.user_id = (select public.request_uid())
  join public.conversation_members other
    on other.conversation_id = c.id and other.user_id <> (select public.request_uid())
  join public.profiles op on op.id = other.user_id
  left join lateral (
    select m.body, m.created_at, m.media_url, m.sender_id
      from public.messages m
     where m.conversation_id = c.id
     order by m.created_at desc
     limit 1
  ) lm on true
 where me.request_state <> 'declined'
 order by coalesce(lm.created_at, c.created_at) desc;
$function$;

grant execute on function public.get_inbox() to authenticated, service_role;
revoke execute on function public.get_inbox() from anon;
revoke execute on function public.get_inbox() from public;
