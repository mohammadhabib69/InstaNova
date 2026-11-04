-- 2026-10-30 — follow_user must also notify the target.
--
-- Bug: with two live accounts, B following a private A returned "requested" and wrote the
-- follow_requests row, but `notifications` gained nothing, so A had no in-app signal at all
-- unless it opened the requests screen by hand.
--
-- The function is SECURITY DEFINER and already validates the caller through public.request_uid(),
-- so the notification row can be written here without widening any RLS policy. A notification is
-- written only on a real state change: an existing follow short-circuits before this point, and
-- follow_requests uses ON CONFLICT DO NOTHING, so the insert must be guarded by the same condition
-- to stay idempotent (a repeated request must not spam the target).
--
-- Rollback: re-create follow_user from `pg_get_functiondef` captured in MIGRATION_LOG (19/20)
-- without the two INSERT blocks.

create or replace function public.follow_user(p_target uuid)
returns text
language plpgsql
security definer
set search_path to 'public', 'pg_temp'
as $function$
declare
  v_me uuid := public.request_uid();
  v_private boolean;
  v_inserted int;
begin
  if v_me is null then raise exception 'not authenticated'; end if;
  if p_target = v_me then return 'self'; end if;
  if exists (select 1 from public.follows where follower_id = v_me and following_id = p_target) then
    return 'following';
  end if;

  select coalesce((select is_private from public.profiles where id = p_target), false) into v_private;

  if v_private then
    insert into public.follow_requests (requester_id, target_id)
    values (v_me, p_target) on conflict do nothing;
    get diagnostics v_inserted = row_count;
    if v_inserted > 0 then
      insert into public.notifications (user_id, actor_id, type, body)
      values (p_target, v_me, 'follow_request', 'requested to follow you');
    end if;
    return 'requested';
  end if;

  insert into public.follows (follower_id, following_id)
  values (v_me, p_target) on conflict do nothing;
  get diagnostics v_inserted = row_count;
  if v_inserted > 0 then
    insert into public.notifications (user_id, actor_id, type, body)
    values (p_target, v_me, 'follow', 'started following you');
  end if;
  return 'following';
end;
$function$;
