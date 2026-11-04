-- Prompt Q — Close Friends story audience
-- Additive, idempotent.

alter table public.stories
    add column if not exists audience text not null default 'public';

create table if not exists public.close_friends (
    owner_id uuid not null references public.profiles(id) on delete cascade,
    friend_id uuid not null references public.profiles(id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (owner_id, friend_id)
);

alter table public.close_friends enable row level security;

drop policy if exists close_friends_select on public.close_friends;
create policy close_friends_select on public.close_friends
    for select to authenticated
    using (owner_id = (select public.request_uid()));

drop policy if exists close_friends_insert on public.close_friends;
create policy close_friends_insert on public.close_friends
    for insert to authenticated
    with check (owner_id = (select public.request_uid()));

drop policy if exists close_friends_delete on public.close_friends;
create policy close_friends_delete on public.close_friends
    for delete to authenticated
    using (owner_id = (select public.request_uid()));

grant select, insert, delete on public.close_friends to authenticated;

create or replace function public.is_close_friend(p_owner uuid, p_viewer uuid)
returns boolean
language sql stable security definer set search_path = public
as $$
    select exists (
        select 1 from public.close_friends cf
        where cf.owner_id = p_owner and cf.friend_id = p_viewer
    );
$$;

grant execute on function public.is_close_friend(uuid, uuid) to authenticated, anon;

create or replace function public.story_visible(p_owner uuid, p_audience text)
returns boolean
language sql stable security definer set search_path = public
as $$
    select case
        when coalesce(p_audience, 'public') <> 'close_friends' then true
        when (select public.request_uid()) is null then true
        when p_owner = (select public.request_uid()) then true
        else public.is_close_friend(p_owner, (select public.request_uid()))
    end;
$$;

grant execute on function public.story_visible(uuid, text) to authenticated, anon;

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
    and public.story_visible(s.user_id, s.audience)
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
    ) as seen_by_me
from public.stories s
join public.profiles pr on pr.id = s.user_id
where s.expires_at > now()
  and public.can_view(s.user_id)
  and not public.is_story_hidden(s.user_id, public.request_uid())
  and public.story_visible(s.user_id, s.audience);

grant select on public.v_stories to authenticated, anon;
