-- ============================================================================
-- Story privacy (3-way) + story archive + real notifications + restricted users
--
-- WHY
--   1. The story 3-dot menu only knew "everyone" and "close friends". A third
--      state — everyone except the hide list — exists server-side already, but
--      "everyone" (hide list ignored) had no value at all, so the two choices
--      collapsed into one.
--   2. Archiving a story had nowhere to live: the row kept its 24h window, so
--      it stayed in the tray and the Archive tab only ever listed expired rows.
--   3. public.notifications was only written for tags and follows. Likes and
--      comments produced nothing, so the app filled the gap with locally
--      generated rows (NotificationCenter) that looked like real activity.
--   4. A restricted account's comment was flagged is_held but every surface
--      read v_comments unfiltered, so restricting someone changed nothing.
--
-- Rollback: see 2026-11-08_story_privacy_archive_notifications_restricted.rollback.sql
-- ============================================================================

begin;

/* -------------------------------------------------------------------------- */
/* 1. Story privacy: three audiences                                          */
/* -------------------------------------------------------------------------- */

alter table public.stories
    add column if not exists is_archived boolean not null default false;

-- Anything unexpected collapses to the conservative default before the new check.
update public.stories
   set audience = 'public'
 where audience is null
    or audience not in ('public', 'close_friends', 'everyone');

alter table public.stories drop constraint if exists stories_audience_check;
alter table public.stories
    add constraint stories_audience_check
    check (audience in ('public', 'close_friends', 'everyone'));

comment on column public.stories.audience is
    'public = everyone except story_audience hide list; everyone = everybody, hide list ignored; '
    'close_friends = close_friends list only.';

-- story_visible() already returns true for anything that is not close_friends,
-- so 'everyone' needs no change here; the hide list is applied by the readers.

/* -------------------------------------------------------------------------- */
/* 2. Story readers: exclude archived rows, honour the 3-way audience          */
/* -------------------------------------------------------------------------- */

create or replace view public.v_stories as
select
    s.id,
    s.user_id,
    pr.username,
    pr.avatar_url,
    s.media_url,
    s.media_type,
    s.created_at,
    s.expires_at,
    exists (
        select 1 from public.story_views sv
        where sv.story_id = s.id and sv.viewer_id = public.request_uid()
    ) as seen_by_me,
    s.audience,
    s.editor_meta
from public.stories s
join public.profiles pr on pr.id = s.user_id
where s.expires_at > now()
  and not s.is_archived
  and public.can_view(s.user_id)
  and (
        coalesce(s.audience, 'public') = 'everyone'
        or not public.is_story_hidden(s.user_id, public.request_uid())
      )
  and public.story_visible(s.user_id, s.audience);

grant select on public.v_stories to authenticated, anon;

-- The Archive tab: my own rows that are either explicitly archived or already
-- expired. Not readable through v_stories, which only carries live stories.
create or replace view public.v_story_archive as
select
    s.id,
    s.user_id,
    s.media_url,
    s.media_type,
    s.created_at,
    s.expires_at,
    s.audience,
    s.is_archived,
    s.editor_meta
from public.stories s
where s.user_id = public.request_uid()
  and (s.is_archived or s.expires_at <= now());

grant select on public.v_story_archive to authenticated;

create or replace function public.get_story_groups()
returns table(user_id uuid, username text, avatar_url text, has_unseen boolean, latest_story_id uuid)
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
    and not s.is_archived
    and public.story_visible(s.user_id, s.audience)
    and (
          coalesce(s.audience, 'public') = 'everyone'
          or not public.is_story_hidden(s.user_id, (select public.request_uid()))
        )
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

-- Direct table reads follow the same rule, so no client can see an archived story.
drop policy if exists stories_select on public.stories;
create policy stories_select on public.stories
    for select to authenticated
    using (
        expires_at > now()
        and not is_archived
        and request_uid() is not null
        and story_visible(user_id, audience)
        and (
              coalesce(audience, 'public') = 'everyone'
              or not is_story_hidden(user_id, request_uid())
            )
    );

/* -------------------------------------------------------------------------- */
/* 3. Real notifications for likes and comments                                */
/* -------------------------------------------------------------------------- */

-- A notification is written only when the actor is somebody else, when the
-- recipient has not restricted the actor, and when the same event has not
-- already been recorded. Self actions therefore can never notify the author.
create or replace function public.tg_notify_like()
returns trigger
language plpgsql
security definer
set search_path = 'public'
as $$
declare
    v_owner uuid;
begin
    select p.user_id into v_owner from public.posts p where p.id = new.post_id;
    if v_owner is null or v_owner = new.user_id then
        return new;
    end if;
    if exists (
        select 1 from public.restricted_users ru
         where ru.owner_id = v_owner and ru.restricted_id = new.user_id
    ) then
        return new;
    end if;
    if exists (
        select 1 from public.notifications n
         where n.user_id = v_owner
           and n.actor_id = new.user_id
           and n.type = 'like'
           and n.post_id = new.post_id
    ) then
        return new;
    end if;
    insert into public.notifications (user_id, actor_id, type, post_id, body)
    values (v_owner, new.user_id, 'like', new.post_id, 'liked your post');
    return new;
end;
$$;

drop trigger if exists trg_notify_like on public.likes;
create trigger trg_notify_like
    after insert on public.likes
    for each row execute function public.tg_notify_like();

create or replace function public.tg_notify_comment()
returns trigger
language plpgsql
security definer
set search_path = 'public'
as $$
declare
    v_owner uuid;
begin
    select p.user_id into v_owner from public.posts p where p.id = new.post_id;
    if v_owner is null or v_owner = new.user_id then
        return new;
    end if;
    if exists (
        select 1 from public.restricted_users ru
         where ru.owner_id = v_owner and ru.restricted_id = new.user_id
    ) then
        return new;
    end if;
    insert into public.notifications (user_id, actor_id, type, post_id, body)
    values (v_owner, new.user_id, 'comment', new.post_id,
            left(coalesce(new.body, ''), 140));
    return new;
end;
$$;

drop trigger if exists trg_notify_comment on public.comments;
create trigger trg_notify_comment
    after insert on public.comments
    for each row execute function public.tg_notify_comment();

/* -------------------------------------------------------------------------- */
/* 4. Restricted accounts: comments are only visible to their author           */
/* -------------------------------------------------------------------------- */

create or replace view public.v_comments as
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
    exists (
        select 1 from public.comment_likes cl
        where cl.comment_id = c.id and cl.user_id = public.request_uid()
    ) as liked_by_me
from public.comments c
join public.profiles pr on pr.id = c.user_id
where not coalesce(c.is_held, false)
   or c.user_id = public.request_uid();

grant select on public.v_comments to authenticated, anon;

-- A held comment is not part of the public count either, so the number on the
-- post matches what a reader can actually open.
create or replace function public.bump_comments_count()
returns trigger
language plpgsql
security definer
set search_path = 'public'
as $function$
begin
  if tg_op = 'INSERT' then
    if coalesce(new.is_held, false) then
      return new;
    end if;
    update public.posts set comments_count = comments_count + 1 where id = new.post_id;
    return new;
  elsif tg_op = 'DELETE' then
    if coalesce(old.is_held, false) then
      return old;
    end if;
    update public.posts set comments_count = greatest(comments_count - 1, 0) where id = old.post_id;
    return old;
  end if;
  return null;
end; $function$;

commit;

notify pgrst, 'reload schema';
