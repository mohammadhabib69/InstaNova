-- ============================================================================
-- Prompt N — Settings and activity + complete Profile functionality
--
-- Additive only. Everything is idempotent so it can be re-run safely.
--   * profile settings columns (privacy, account type, presence, deactivation)
--   * follow requests for private accounts (table + RPCs)
--   * blocks / restricted / story audience / post tags
--   * reports (insert-only for users)
--   * posts.is_archived + archive-aware views
--   * presence (last_active_at + touch_active)
-- ============================================================================

begin;

-- ---------------------------------------------------------------------------
-- 1. profiles: settings columns
-- ---------------------------------------------------------------------------
alter table public.profiles add column if not exists is_private boolean not null default false;
alter table public.profiles add column if not exists account_type text not null default 'personal';
alter table public.profiles add column if not exists show_activity boolean not null default true;
alter table public.profiles add column if not exists is_deactivated boolean not null default false;
alter table public.profiles add column if not exists who_can_call text not null default 'everyone';
alter table public.profiles add column if not exists last_active_at timestamptz;

-- Column-level grants: profiles already uses column grants for authenticated.
grant select (is_private, account_type, show_activity, is_deactivated, who_can_call, last_active_at)
    on public.profiles to authenticated;
grant update (is_private, account_type, show_activity, is_deactivated, who_can_call)
    on public.profiles to authenticated;
-- Edit Profile has always shown a website field but the column was never writable.
grant update (website) on public.profiles to authenticated;

-- ---------------------------------------------------------------------------
-- 2. posts: archive flag
-- ---------------------------------------------------------------------------
alter table public.posts add column if not exists is_archived boolean not null default false;

-- ---------------------------------------------------------------------------
-- 3. follow requests (private accounts)
-- ---------------------------------------------------------------------------
create table if not exists public.follow_requests (
  requester_id uuid not null references public.profiles (id) on delete cascade,
  target_id uuid not null references public.profiles (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (requester_id, target_id),
  constraint follow_requests_no_self check (requester_id <> target_id)
);
alter table public.follow_requests enable row level security;

drop policy if exists follow_requests_select on public.follow_requests;
create policy follow_requests_select on public.follow_requests for select to authenticated
  using (requester_id = public.request_uid() or target_id = public.request_uid());

drop policy if exists follow_requests_insert on public.follow_requests;
create policy follow_requests_insert on public.follow_requests for insert to authenticated
  with check (requester_id = public.request_uid());

drop policy if exists follow_requests_delete on public.follow_requests;
create policy follow_requests_delete on public.follow_requests for delete to authenticated
  using (requester_id = public.request_uid() or target_id = public.request_uid());

-- ---------------------------------------------------------------------------
-- 4. blocks / restricted / story audience / post tags / reports
-- ---------------------------------------------------------------------------
create table if not exists public.blocks (
  blocker_id uuid not null references public.profiles (id) on delete cascade,
  blocked_id uuid not null references public.profiles (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (blocker_id, blocked_id),
  constraint blocks_no_self check (blocker_id <> blocked_id)
);
alter table public.blocks enable row level security;
drop policy if exists blocks_own on public.blocks;
create policy blocks_own on public.blocks for all to authenticated
  using (blocker_id = public.request_uid())
  with check (blocker_id = public.request_uid());
grant select, insert, delete on public.blocks to authenticated;

create table if not exists public.restricted_users (
  owner_id uuid not null references public.profiles (id) on delete cascade,
  restricted_id uuid not null references public.profiles (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (owner_id, restricted_id),
  constraint restricted_no_self check (owner_id <> restricted_id)
);
alter table public.restricted_users enable row level security;
drop policy if exists restricted_own on public.restricted_users;
create policy restricted_own on public.restricted_users for all to authenticated
  using (owner_id = public.request_uid())
  with check (owner_id = public.request_uid());
grant select, insert, delete on public.restricted_users to authenticated;

create table if not exists public.story_audience (
  owner_id uuid not null references public.profiles (id) on delete cascade,
  viewer_id uuid not null references public.profiles (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (owner_id, viewer_id),
  constraint story_audience_no_self check (owner_id <> viewer_id)
);
alter table public.story_audience enable row level security;
drop policy if exists story_audience_own on public.story_audience;
create policy story_audience_own on public.story_audience for all to authenticated
  using (owner_id = public.request_uid())
  with check (owner_id = public.request_uid());
grant select, insert, delete on public.story_audience to authenticated;

create table if not exists public.post_tags (
  post_id uuid not null references public.posts (id) on delete cascade,
  user_id uuid not null references public.profiles (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (post_id, user_id)
);
alter table public.post_tags enable row level security;
drop policy if exists post_tags_select on public.post_tags;
create policy post_tags_select on public.post_tags for select to authenticated using (true);
drop policy if exists post_tags_insert on public.post_tags;
create policy post_tags_insert on public.post_tags for insert to authenticated
  with check (exists (select 1 from public.posts p where p.id = post_id and p.user_id = public.request_uid()));
drop policy if exists post_tags_delete on public.post_tags;
create policy post_tags_delete on public.post_tags for delete to authenticated
  using (exists (select 1 from public.posts p where p.id = post_id and p.user_id = public.request_uid()));
grant select, insert, delete on public.post_tags to authenticated;

create table if not exists public.reports (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles (id) on delete cascade,
  category text not null default 'other',
  message text not null,
  app_version text,
  created_at timestamptz not null default now()
);
alter table public.reports enable row level security;
drop policy if exists reports_insert_own on public.reports;
create policy reports_insert_own on public.reports for insert to authenticated
  with check (user_id = public.request_uid());
-- No SELECT policy: a user can send a report but cannot read reports (own or others).
grant insert on public.reports to authenticated;

-- ---------------------------------------------------------------------------
-- 5. visibility helpers
-- ---------------------------------------------------------------------------
create or replace function public.can_view(p_owner uuid)
returns boolean
language sql
stable
security definer
set search_path = public, pg_temp
as $$
  select case
    when p_owner is null then false
    when p_owner = public.request_uid() then true
    when exists (
      select 1 from public.blocks b
       where (b.blocker_id = public.request_uid() and b.blocked_id = p_owner)
          or (b.blocker_id = p_owner and b.blocked_id = public.request_uid())
    ) then false
    when coalesce((select is_deactivated from public.profiles where id = p_owner), false) then false
    when not coalesce((select is_private from public.profiles where id = p_owner), false) then true
    else exists (
      select 1 from public.follows f
       where f.follower_id = public.request_uid() and f.following_id = p_owner
    )
  end;
$$;

create or replace function public.is_story_hidden(p_owner uuid, p_viewer uuid)
returns boolean
language sql
stable
security definer
set search_path = public, pg_temp
as $$
  select exists (
    select 1 from public.story_audience sa
     where sa.owner_id = p_owner and sa.viewer_id = p_viewer
  );
$$;

create or replace function public.is_restricted(p_owner uuid, p_sender uuid)
returns boolean
language sql
stable
security definer
set search_path = public, pg_temp
as $$
  select exists (
    select 1 from public.restricted_users r
     where r.owner_id = p_owner and r.restricted_id = p_sender
  );
$$;

revoke all on function public.can_view(uuid) from public;
revoke all on function public.is_story_hidden(uuid, uuid) from public;
revoke all on function public.is_restricted(uuid, uuid) from public;
grant execute on function public.can_view(uuid) to authenticated, anon;
grant execute on function public.is_story_hidden(uuid, uuid) to authenticated, anon;
grant execute on function public.is_restricted(uuid, uuid) to authenticated;

-- ---------------------------------------------------------------------------
-- 6. follow rules for private accounts
-- ---------------------------------------------------------------------------
drop policy if exists follows_insert on public.follows;
create policy follows_insert on public.follows for insert to authenticated
  with check (
    follower_id = public.request_uid()
    and not exists (select 1 from public.profiles p where p.id = following_id and p.is_private)
  );

create or replace function public.follow_user(p_target uuid)
returns text
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_me uuid := public.request_uid();
begin
  if v_me is null then raise exception 'not authenticated'; end if;
  if p_target = v_me then return 'self'; end if;
  if exists (select 1 from public.follows where follower_id = v_me and following_id = p_target) then
    return 'following';
  end if;
  if coalesce((select is_private from public.profiles where id = p_target), false) then
    insert into public.follow_requests (requester_id, target_id)
    values (v_me, p_target) on conflict do nothing;
    return 'requested';
  end if;
  insert into public.follows (follower_id, following_id)
  values (v_me, p_target) on conflict do nothing;
  return 'following';
end;
$$;

create or replace function public.approve_follow_request(p_requester uuid)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_me uuid := public.request_uid();
begin
  if v_me is null then raise exception 'not authenticated'; end if;
  if exists (select 1 from public.follow_requests where requester_id = p_requester and target_id = v_me) then
    insert into public.follows (follower_id, following_id)
    values (p_requester, v_me) on conflict do nothing;
    delete from public.follow_requests where requester_id = p_requester and target_id = v_me;
  end if;
end;
$$;

create or replace function public.set_account_privacy(p_private boolean)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_me uuid := public.request_uid();
begin
  if v_me is null then raise exception 'not authenticated'; end if;
  update public.profiles set is_private = p_private where id = v_me;
  if not p_private then
    insert into public.follows (follower_id, following_id)
    select requester_id, v_me from public.follow_requests where target_id = v_me
    on conflict do nothing;
    delete from public.follow_requests where target_id = v_me;
  end if;
end;
$$;

create or replace function public.touch_active()
returns void
language sql
security definer
set search_path = public, pg_temp
as $$
  update public.profiles
     set last_active_at = now()
   where id = public.request_uid()
     and show_activity = true;
$$;

create or replace function public.delete_my_account()
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_me uuid := public.request_uid();
begin
  if v_me is null then raise exception 'not authenticated'; end if;
  delete from public.profiles where id = v_me;
end;
$$;

revoke all on function public.follow_user(uuid) from public;
revoke all on function public.approve_follow_request(uuid) from public;
revoke all on function public.set_account_privacy(boolean) from public;
revoke all on function public.touch_active() from public;
revoke all on function public.delete_my_account() from public;
grant execute on function public.follow_user(uuid) to authenticated;
grant execute on function public.approve_follow_request(uuid) to authenticated;
grant execute on function public.set_account_privacy(boolean) to authenticated;
grant execute on function public.touch_active() to authenticated;
grant execute on function public.delete_my_account() to authenticated;

-- ---------------------------------------------------------------------------
-- 7. views: privacy + archive + story audience
-- ---------------------------------------------------------------------------
drop view if exists public.v_feed;
drop view if exists public.v_saved;
drop view if exists public.v_posts;

create view public.v_posts with (security_invoker = true) as
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
  (select coalesce(jsonb_agg(m.url order by m.position), '[]'::jsonb)
     from public.post_media m where m.post_id = p.id) as media,
  exists(select 1 from public.likes l where l.post_id = p.id and l.user_id = public.request_uid()) as liked_by_me,
  exists(select 1 from public.saved_posts s where s.post_id = p.id and s.user_id = public.request_uid()) as saved_by_me,
  (select coalesce(jsonb_agg(jsonb_build_object('username', t.username, 'avatar_url', t.avatar_url)), '[]'::jsonb)
     from (
       select pr2.username, pr2.avatar_url
       from public.likes l2
       join public.profiles pr2 on pr2.id = l2.user_id
       where l2.post_id = p.id
       order by l2.created_at desc
       limit 3
     ) t) as top_likers,
  p.is_archived
from public.posts p
join public.profiles pr on pr.id = p.user_id
where public.can_view(p.user_id);

create view public.v_feed with (security_invoker = true) as
select * from public.v_posts
where is_reel = false
  and is_archived = false
  and (public.request_uid() is null
       or user_id = public.request_uid()
       or user_id in (select following_id from public.follows where follower_id = public.request_uid()));

create view public.v_saved with (security_invoker = true) as
select v.*, s.created_at as saved_at
from public.v_posts v
join public.saved_posts s on s.post_id = v.id and s.user_id = public.request_uid();

grant select on public.v_posts, public.v_feed, public.v_saved to authenticated;
grant select on public.v_posts, public.v_feed, public.v_saved to anon;

drop view if exists public.v_stories;
create view public.v_stories with (security_invoker = true) as
select
  s.id,
  s.user_id,
  pr.username,
  pr.avatar_url,
  s.media_url,
  s.media_type,
  s.created_at,
  s.expires_at,
  exists(select 1 from public.story_views sv where sv.story_id = s.id and sv.viewer_id = public.request_uid()) as seen_by_me
from public.stories s
join public.profiles pr on pr.id = s.user_id
where s.expires_at > now()
  and public.can_view(s.user_id)
  and not public.is_story_hidden(s.user_id, public.request_uid());
grant select on public.v_stories to authenticated, anon;

commit;
