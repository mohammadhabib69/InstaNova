-- ============================================================================
-- 2026-10-18  Replace legacy auth.uid() with public.request_uid()
--             in dependent views and functions (Firebase identity migration
--             follow-up).
--
-- Background
--   Supabase's auth.uid() is defined as:
--       coalesce(
--         nullif(current_setting('request.jwt.claim.sub', true), ''),
--         current_setting('request.jwt.claims', true)::jsonb ->> 'sub'
--       )::uuid
--   A Firebase ID token carries a 28-character `sub` (the Firebase UID), so the
--   final ::uuid cast raises 22P02 and PostgREST returns HTTP 400. Policies were
--   migrated to public.request_uid() on 2026-10-17; the views and functions below
--   were missed and still call auth.uid().
--
-- Scope
--   5 views, 6 function definitions. Every other token in every object is
--   unchanged. No policy, table, trigger, Storage object or grant is touched:
--   CREATE OR REPLACE preserves existing ACLs.
--
-- Rollback
--   See 2026-10-18_request_uid_views_and_functions.rollback.sql
-- ============================================================================

begin;

-- Fail fast rather than queue behind a long-running transaction on these tables.
set local lock_timeout = '5s';

-- ----------------------------------------------------------------------------
-- 1. Views
--    security_invoker = true is re-stated so the option cannot drift.
--    Column names, order and types are identical to the current definitions;
--    only the identity expression changes.
-- ----------------------------------------------------------------------------

create or replace view public.v_posts with (security_invoker = true) as
select
  p.id,
  p.user_id,
  pr.username,
  pr.avatar_url,
  pr.is_verified,
  p.caption,
  p.location,
  p.is_reel,
  p.video_url,
  p.thumbnail_url,
  p.likes_count,
  p.comments_count,
  p.created_at,
  (select coalesce(jsonb_agg(m.url order by m."position"), '[]'::jsonb) as "coalesce"
     from public.post_media m where m.post_id = p.id) as media,
  exists(select 1 from public.likes l
          where l.post_id = p.id and l.user_id = (select public.request_uid())) as liked_by_me,
  exists(select 1 from public.saved_posts s
          where s.post_id = p.id and s.user_id = (select public.request_uid())) as saved_by_me,
  (select coalesce(jsonb_agg(jsonb_build_object('username', t.username, 'avatar_url', t.avatar_url)), '[]'::jsonb) as "coalesce"
     from (
       select pr2.username, pr2.avatar_url
       from public.likes l2
       join public.profiles pr2 on pr2.id = l2.user_id
       where l2.post_id = p.id
       order by l2.created_at desc
       limit 3
     ) t) as top_likers
from public.posts p
join public.profiles pr on pr.id = p.user_id;

create or replace view public.v_feed with (security_invoker = true) as
select * from public.v_posts
where is_reel = false
  and ((select public.request_uid()) is null
       or user_id = (select public.request_uid())
       or user_id in (select follows.following_id from public.follows
                       where follows.follower_id = (select public.request_uid())));

create or replace view public.v_saved with (security_invoker = true) as
select v.*, s.created_at as saved_at
from public.v_posts v
join public.saved_posts s
  on s.post_id = v.id and s.user_id = (select public.request_uid());

create or replace view public.v_comments with (security_invoker = true) as
select
  c.id,
  c.post_id,
  c.user_id,
  pr.username,
  pr.avatar_url,
  c.parent_id,
  c.body,
  c.likes_count,
  c.created_at,
  exists(select 1 from public.comment_likes cl
          where cl.comment_id = c.id and cl.user_id = (select public.request_uid())) as liked_by_me
from public.comments c
join public.profiles pr on pr.id = c.user_id;

create or replace view public.v_stories with (security_invoker = true) as
select
  s.id,
  s.user_id,
  pr.username,
  pr.avatar_url,
  s.media_url,
  s.media_type,
  s.created_at,
  s.expires_at,
  exists(select 1 from public.story_views sv
          where sv.story_id = s.id and sv.viewer_id = (select public.request_uid())) as seen_by_me
from public.stories s
join public.profiles pr on pr.id = s.user_id
where s.expires_at > now();

-- ----------------------------------------------------------------------------
-- 2. Functions
--    Signatures, return types, language, SECURITY DEFINER/INVOKER, search_path
--    and grants are reproduced exactly. Only the identity expressions change.
-- ----------------------------------------------------------------------------

create or replace function public.get_unread_counts()
returns json
language sql
security definer
set search_path to 'public'
as $function$
  select json_build_object(
    'notifications', (select count(*)::int from public.notifications n
                       where n.user_id = (select public.request_uid()) and n.read = false),
    'messages', (select count(*)::int from public.messages m
                 join public.conversation_members cm on cm.conversation_id = m.conversation_id
                 where cm.user_id = (select public.request_uid())
                   and m.sender_id <> (select public.request_uid())
                   and m.is_read = false)
  );
$function$;

create or replace function public.get_inbox()
returns table(
  conversation_id uuid,
  other_user_id uuid,
  other_username text,
  other_avatar_url text,
  last_message text,
  last_message_at timestamp with time zone,
  unread_count bigint
)
language sql
security definer
set search_path to 'public'
as $function$
  select
    c.id as conversation_id,
    op.id as other_user_id,
    op.username as other_username,
    op.avatar_url as other_avatar_url,
    lm.body as last_message,
    lm.created_at as last_message_at,
    (select count(*) from public.messages m
      where m.conversation_id = c.id
        and m.sender_id <> (select public.request_uid())
        and m.is_read = false) as unread_count
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

create or replace function public.get_story_groups()
returns table(
  user_id uuid,
  username text,
  avatar_url text,
  has_unseen boolean,
  latest_story_id uuid
)
language sql
set search_path to 'public'
as $function$
  select
    s.user_id,
    pr.username,
    pr.avatar_url,
    bool_or(not exists (
      select 1 from public.story_views sv
      where sv.story_id = s.id and sv.viewer_id = (select public.request_uid())
    )) as has_unseen,
    (array_agg(s.id order by s.created_at desc))[1] as latest_story_id
  from public.stories s
  join public.profiles pr on pr.id = s.user_id
  where s.expires_at > now()
    and (
      (select public.request_uid()) is null
      or s.user_id = (select public.request_uid())
      or s.user_id in (select following_id from public.follows
                        where follower_id = (select public.request_uid()))
    )
  group by s.user_id, pr.username, pr.avatar_url
  order by
    case when s.user_id = (select public.request_uid()) then 0 else 1 end,
    max(s.created_at) desc;
$function$;

create or replace function public.get_suggested_users(p_limit integer default 20)
returns setof profiles
language sql
set search_path to 'public'
as $function$
  select p.*
  from public.profiles p
  where p.id <> (select public.request_uid())
    and p.id not in (select following_id from public.follows
                      where follower_id = (select public.request_uid()))
  order by p.followers_count desc, p.created_at desc
  limit greatest(coalesce(p_limit, 20), 1);
$function$;

create or replace function public.get_suggested_users(
  p_limit integer default 20,
  p_exclude_user_id uuid default null::uuid
)
returns setof profiles
language sql
security definer
set search_path to 'public'
as $function$
  select p.*
  from public.profiles p
  where p.id <> coalesce((select public.request_uid()), '00000000-0000-0000-0000-000000000000'::uuid)
    and p.id not in (select following_id from public.follows
                      where follower_id = (select public.request_uid()))
  order by p.followers_count desc, p.created_at desc
  limit greatest(coalesce(p_limit, 20), 1);
$function$;

create or replace function public.get_or_create_conversation(p_other_user_id uuid)
returns uuid
language plpgsql
security definer
set search_path to 'public'
as $function$
declare
  cid uuid;
begin
  if p_other_user_id is null or p_other_user_id = (select public.request_uid()) then
    raise exception 'invalid other user';
  end if;

  select cm1.conversation_id into cid
  from public.conversation_members cm1
  join public.conversation_members cm2
    on cm1.conversation_id = cm2.conversation_id
  where cm1.user_id = (select public.request_uid())
    and cm2.user_id = p_other_user_id
  limit 1;

  if cid is not null then
    return cid;
  end if;

  insert into public.conversations default values returning id into cid;
  insert into public.conversation_members (conversation_id, user_id)
    values (cid, (select public.request_uid()));
  insert into public.conversation_members (conversation_id, user_id)
    values (cid, p_other_user_id);
  return cid;
end;
$function$;

commit;
