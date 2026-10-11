-- Insta_Clone full schema for Supabase (Postgres 15+)
-- security_invoker views + RLS on all public tables

create extension if not exists "pgcrypto";

-- ========== TABLES ==========

create table if not exists public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  username text unique not null,
  full_name text not null default '',
  bio text not null default '',
  avatar_url text,
  website text not null default '',
  is_verified boolean not null default false,
  posts_count integer not null default 0,
  followers_count integer not null default 0,
  following_count integer not null default 0,
  birthday date,
  created_at timestamptz not null default now(),
  constraint profiles_username_len check (char_length(username) between 3 and 30),
  constraint profiles_username_fmt check (username ~ '^[a-z0-9._]+$')
);

create table if not exists public.posts (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles (id) on delete cascade,
  caption text not null default '',
  location text,
  is_reel boolean not null default false,
  video_url text,
  thumbnail_url text,
  likes_count integer not null default 0,
  comments_count integer not null default 0,
  created_at timestamptz not null default now()
);
create index if not exists posts_user_id_idx on public.posts (user_id);
create index if not exists posts_created_at_idx on public.posts (created_at desc);

create table if not exists public.post_media (
  id uuid primary key default gen_random_uuid(),
  post_id uuid not null references public.posts (id) on delete cascade,
  url text not null,
  position integer not null default 0
);
create index if not exists post_media_post_id_idx on public.post_media (post_id, position);

create table if not exists public.likes (
  post_id uuid not null references public.posts (id) on delete cascade,
  user_id uuid not null references public.profiles (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (post_id, user_id)
);

create table if not exists public.saved_posts (
  post_id uuid not null references public.posts (id) on delete cascade,
  user_id uuid not null references public.profiles (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (post_id, user_id)
);

create table if not exists public.comments (
  id uuid primary key default gen_random_uuid(),
  post_id uuid not null references public.posts (id) on delete cascade,
  user_id uuid not null references public.profiles (id) on delete cascade,
  parent_id uuid references public.comments (id) on delete cascade,
  body text not null,
  likes_count integer not null default 0,
  created_at timestamptz not null default now()
);
create index if not exists comments_post_id_idx on public.comments (post_id, created_at);

create table if not exists public.comment_likes (
  comment_id uuid not null references public.comments (id) on delete cascade,
  user_id uuid not null references public.profiles (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (comment_id, user_id)
);

create table if not exists public.follows (
  follower_id uuid not null references public.profiles (id) on delete cascade,
  following_id uuid not null references public.profiles (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (follower_id, following_id),
  constraint follows_no_self check (follower_id <> following_id)
);

create table if not exists public.stories (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles (id) on delete cascade,
  media_url text not null,
  media_type text not null default 'image',
  created_at timestamptz not null default now(),
  expires_at timestamptz not null default (now() + interval '24 hours')
);
create index if not exists stories_user_active_idx on public.stories (user_id, expires_at);

create table if not exists public.story_views (
  story_id uuid not null references public.stories (id) on delete cascade,
  viewer_id uuid not null references public.profiles (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (story_id, viewer_id)
);

create table if not exists public.highlights (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles (id) on delete cascade,
  title text not null,
  cover_url text,
  created_at timestamptz not null default now()
);

create table if not exists public.highlight_items (
  id uuid primary key default gen_random_uuid(),
  highlight_id uuid not null references public.highlights (id) on delete cascade,
  media_url text not null,
  position integer not null default 0,
  created_at timestamptz not null default now()
);

create table if not exists public.conversations (
  id uuid primary key default gen_random_uuid(),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.conversation_members (
  conversation_id uuid not null references public.conversations (id) on delete cascade,
  user_id uuid not null references public.profiles (id) on delete cascade,
  last_read_at timestamptz,
  primary key (conversation_id, user_id)
);

create table if not exists public.messages (
  id uuid primary key default gen_random_uuid(),
  conversation_id uuid not null references public.conversations (id) on delete cascade,
  sender_id uuid not null references public.profiles (id) on delete cascade,
  body text not null default '',
  media_url text,
  is_read boolean not null default false,
  created_at timestamptz not null default now()
);
create index if not exists messages_conversation_idx on public.messages (conversation_id, created_at);

create table if not exists public.notifications (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles (id) on delete cascade,
  actor_id uuid references public.profiles (id) on delete set null,
  type text not null,
  post_id uuid references public.posts (id) on delete cascade,
  comment_id uuid references public.comments (id) on delete cascade,
  body text not null default '',
  read boolean not null default false,
  created_at timestamptz not null default now()
);
create index if not exists notifications_user_idx on public.notifications (user_id, created_at desc);

-- ========== PROFILE TRIGGER ==========

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  uname text;
  fname text;
begin
  uname := lower(coalesce(new.raw_user_meta_data->>'username', split_part(coalesce(new.email, 'user'), '@', 1)));
  uname := regexp_replace(uname, '[^a-z0-9._]', '', 'g');
  if char_length(uname) < 3 then
    uname := 'user' || substr(replace(new.id::text, '-', ''), 1, 8);
  end if;
  fname := coalesce(new.raw_user_meta_data->>'full_name', '');
  insert into public.profiles (id, username, full_name)
  values (new.id, uname, fname)
  on conflict (id) do nothing;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- ========== COUNT TRIGGERS ==========

create or replace function public.bump_posts_count()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if tg_op = 'INSERT' then
    update public.profiles set posts_count = posts_count + 1 where id = new.user_id;
    return new;
  elsif tg_op = 'DELETE' then
    update public.profiles set posts_count = greatest(posts_count - 1, 0) where id = old.user_id;
    return old;
  end if;
  return null;
end; $$;

drop trigger if exists trg_posts_count on public.posts;
create trigger trg_posts_count after insert or delete on public.posts
  for each row execute function public.bump_posts_count();

create or replace function public.bump_likes_count()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if tg_op = 'INSERT' then
    update public.posts set likes_count = likes_count + 1 where id = new.post_id;
    return new;
  elsif tg_op = 'DELETE' then
    update public.posts set likes_count = greatest(likes_count - 1, 0) where id = old.post_id;
    return old;
  end if;
  return null;
end; $$;

drop trigger if exists trg_likes_count on public.likes;
create trigger trg_likes_count after insert or delete on public.likes
  for each row execute function public.bump_likes_count();

create or replace function public.bump_comments_count()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if tg_op = 'INSERT' then
    update public.posts set comments_count = comments_count + 1 where id = new.post_id;
    return new;
  elsif tg_op = 'DELETE' then
    update public.posts set comments_count = greatest(comments_count - 1, 0) where id = old.post_id;
    return old;
  end if;
  return null;
end; $$;

drop trigger if exists trg_comments_count on public.comments;
create trigger trg_comments_count after insert or delete on public.comments
  for each row execute function public.bump_comments_count();

create or replace function public.bump_follow_counts()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if tg_op = 'INSERT' then
    update public.profiles set following_count = following_count + 1 where id = new.follower_id;
    update public.profiles set followers_count = followers_count + 1 where id = new.following_id;
    return new;
  elsif tg_op = 'DELETE' then
    update public.profiles set following_count = greatest(following_count - 1, 0) where id = old.follower_id;
    update public.profiles set followers_count = greatest(followers_count - 1, 0) where id = old.following_id;
    return old;
  end if;
  return null;
end; $$;

drop trigger if exists trg_follow_counts on public.follows;
create trigger trg_follow_counts after insert or delete on public.follows
  for each row execute function public.bump_follow_counts();

-- ========== VIEWS ==========

drop view if exists public.v_feed;
drop view if exists public.v_saved;
drop view if exists public.v_comments;
drop view if exists public.v_stories;
drop view if exists public.v_posts;

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
  (select coalesce(jsonb_agg(m.url order by m.position), '[]'::jsonb)
     from public.post_media m where m.post_id = p.id) as media,
  exists(select 1 from public.likes l where l.post_id = p.id and l.user_id = auth.uid()) as liked_by_me,
  exists(select 1 from public.saved_posts s where s.post_id = p.id and s.user_id = auth.uid()) as saved_by_me,
  (select coalesce(jsonb_agg(jsonb_build_object('username', t.username, 'avatar_url', t.avatar_url)), '[]'::jsonb)
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

create view public.v_feed with (security_invoker = true) as
select * from public.v_posts
where is_reel = false
  and (auth.uid() is null
       or user_id = auth.uid()
       or user_id in (select following_id from public.follows where follower_id = auth.uid()));

create view public.v_saved with (security_invoker = true) as
select v.*, s.created_at as saved_at
from public.v_posts v
join public.saved_posts s on s.post_id = v.id and s.user_id = auth.uid();

create view public.v_comments with (security_invoker = true) as
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
  exists(select 1 from public.comment_likes cl where cl.comment_id = c.id and cl.user_id = auth.uid()) as liked_by_me
from public.comments c
join public.profiles pr on pr.id = c.user_id;

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
  exists(select 1 from public.story_views sv where sv.story_id = s.id and sv.viewer_id = auth.uid()) as seen_by_me
from public.stories s
join public.profiles pr on pr.id = s.user_id
where s.expires_at > now();

grant select on public.v_posts, public.v_feed, public.v_saved, public.v_comments, public.v_stories to authenticated;
grant select on public.v_posts, public.v_feed, public.v_saved, public.v_comments, public.v_stories to anon;

-- ========== RPCs ==========

create or replace function public.get_email_by_username(p_username text)
returns text
language sql
security definer
set search_path = public
as $$
  select u.email
  from auth.users u
  join public.profiles p on p.id = u.id
  where lower(p.username) = lower(p_username)
  limit 1;
$$;

create or replace function public.is_username_available(p_username text)
returns boolean
language sql
security definer
set search_path = public
as $$
  select not exists (select 1 from public.profiles where lower(username) = lower(p_username));
$$;

create or replace function public.get_story_groups()
returns table (
  user_id uuid,
  username text,
  avatar_url text,
  has_unseen boolean,
  latest_story_id uuid
)
language sql
security invoker
set search_path = public
as $$
  select
    s.user_id,
    pr.username,
    pr.avatar_url,
    bool_or(not exists (
      select 1 from public.story_views sv
      where sv.story_id = s.id and sv.viewer_id = auth.uid()
    )) as has_unseen,
    (array_agg(s.id order by s.created_at desc))[1] as latest_story_id
  from public.stories s
  join public.profiles pr on pr.id = s.user_id
  where s.expires_at > now()
    and (auth.uid() is null or
      (
      s.user_id = auth.uid()
      or s.user_id in (select following_id from public.follows where follower_id = auth.uid())
      )
    )
  group by s.user_id, pr.username, pr.avatar_url
  order by
    case when s.user_id = auth.uid() then 0 else 1 end,
    max(s.created_at) desc;
$$;

create or replace function public.get_suggested_users(p_limit integer default 20)
returns setof public.profiles
language sql
security invoker
set search_path = public
as $$
  select p.*
  from public.profiles p
  where p.id <> auth.uid()
    and p.id not in (select following_id from public.follows where follower_id = auth.uid())
  order by p.followers_count desc, p.created_at desc
  limit greatest(coalesce(p_limit, 20), 1);
$$;

create or replace function public.get_or_create_conversation(p_other_user_id uuid)
returns uuid
language plpgsql
security invoker
set search_path = public
as $$
declare
  cid uuid;
begin
  if p_other_user_id is null or p_other_user_id = auth.uid() then
    raise exception 'invalid other user';
  end if;

  select cm1.conversation_id into cid
  from public.conversation_members cm1
  join public.conversation_members cm2
    on cm1.conversation_id = cm2.conversation_id
  where cm1.user_id = auth.uid()
    and cm2.user_id = p_other_user_id
  limit 1;

  if cid is not null then
    return cid;
  end if;

  insert into public.conversations default values returning id into cid;
  insert into public.conversation_members (conversation_id, user_id) values (cid, auth.uid());
  insert into public.conversation_members (conversation_id, user_id) values (cid, p_other_user_id);
  return cid;
end;
$$;

create or replace function public.get_inbox()
returns table (
  conversation_id uuid,
  other_user_id uuid,
  other_username text,
  other_avatar_url text,
  last_message text,
  last_message_at timestamptz,
  unread_count bigint
)
language sql
security invoker
set search_path = public
as $$
  select
    c.id as conversation_id,
    op.id as other_user_id,
    op.username as other_username,
    op.avatar_url as other_avatar_url,
    lm.body as last_message,
    lm.created_at as last_message_at,
    (select count(*) from public.messages m
      where m.conversation_id = c.id
        and m.sender_id <> auth.uid()
        and m.is_read = false) as unread_count
  from public.conversations c
  join public.conversation_members me on me.conversation_id = c.id and me.user_id = auth.uid()
  join public.conversation_members other on other.conversation_id = c.id and other.user_id <> auth.uid()
  join public.profiles op on op.id = other.user_id
  left join lateral (
    select m.body, m.created_at
    from public.messages m
    where m.conversation_id = c.id
    order by m.created_at desc
    limit 1
  ) lm on true
  order by coalesce(lm.created_at, c.created_at) desc;
$$;

create or replace function public.get_unread_counts()
returns json
language sql
security invoker
set search_path = public
as $$
  select json_build_object(
    'notifications', (select count(*)::int from public.notifications n where n.user_id = auth.uid() and n.read = false),
    'messages', (select count(*)::int from public.messages m
                 join public.conversation_members cm on cm.conversation_id = m.conversation_id
                 where cm.user_id = auth.uid() and m.sender_id <> auth.uid() and m.is_read = false)
  );
$$;

grant execute on function public.get_email_by_username(text) to anon, authenticated;
grant execute on function public.is_username_available(text) to anon, authenticated;
-- The debug login bypass needs the public story tray RPC; story writes remain authenticated-only.
grant execute on function public.get_story_groups() to anon, authenticated;
grant execute on function public.get_suggested_users(integer) to authenticated;
grant execute on function public.get_or_create_conversation(uuid) to authenticated;
grant execute on function public.get_inbox() to authenticated;
grant execute on function public.get_unread_counts() to authenticated;

-- Authenticated-only RPCs must not be callable with the public/publishable key alone.
revoke execute on function public.get_suggested_users(integer) from anon;
revoke execute on function public.get_inbox() from anon;
revoke execute on function public.get_unread_counts() from anon;
revoke execute on function public.get_suggested_users(integer) from public;
revoke execute on function public.get_inbox() from public;
revoke execute on function public.get_unread_counts() from public;
revoke execute on function public.bump_comments_count() from public;
revoke execute on function public.bump_follow_counts() from public;
revoke execute on function public.bump_likes_count() from public;
revoke execute on function public.bump_posts_count() from public;
revoke execute on function public.handle_new_user() from public;
revoke execute on function public.bump_comments_count() from anon, authenticated;
revoke execute on function public.bump_follow_counts() from anon, authenticated;
revoke execute on function public.bump_likes_count() from anon, authenticated;
revoke execute on function public.bump_posts_count() from anon, authenticated;
revoke execute on function public.handle_new_user() from anon, authenticated;

-- ========== RLS ==========

alter table public.profiles enable row level security;
alter table public.posts enable row level security;
alter table public.post_media enable row level security;
alter table public.likes enable row level security;
alter table public.saved_posts enable row level security;
alter table public.comments enable row level security;
alter table public.comment_likes enable row level security;
alter table public.follows enable row level security;
alter table public.stories enable row level security;
alter table public.story_views enable row level security;
alter table public.highlights enable row level security;
alter table public.highlight_items enable row level security;
alter table public.conversations enable row level security;
alter table public.conversation_members enable row level security;
alter table public.messages enable row level security;
alter table public.notifications enable row level security;

-- profiles
drop policy if exists profiles_select on public.profiles;
-- Public profile reads are required by the debug login bypass and are safe for this public profile data.
create policy profiles_select on public.profiles for select to anon, authenticated using (true);
drop policy if exists profiles_insert_own on public.profiles;
create policy profiles_insert_own on public.profiles for insert to authenticated with check (id = auth.uid());
drop policy if exists profiles_update_own on public.profiles;
create policy profiles_update_own on public.profiles for update to authenticated using (id = auth.uid()) with check (id = auth.uid());

-- posts
drop policy if exists posts_select on public.posts;
-- Public feed reads are required by the debug login bypass; writes remain authenticated-only.
create policy posts_select on public.posts for select to anon, authenticated using (true);
drop policy if exists posts_insert_own on public.posts;
create policy posts_insert_own on public.posts for insert to authenticated with check (user_id = auth.uid());
drop policy if exists posts_update_own on public.posts;
create policy posts_update_own on public.posts for update to authenticated using (user_id = auth.uid());
drop policy if exists posts_delete_own on public.posts;
create policy posts_delete_own on public.posts for delete to authenticated using (user_id = auth.uid());

-- post_media
drop policy if exists post_media_select on public.post_media;
create policy post_media_select on public.post_media for select to anon, authenticated using (true);
drop policy if exists post_media_insert on public.post_media;
create policy post_media_insert on public.post_media for insert to authenticated
  with check (exists (select 1 from public.posts p where p.id = post_id and p.user_id = auth.uid()));
drop policy if exists post_media_delete on public.post_media;
create policy post_media_delete on public.post_media for delete to authenticated
  using (exists (select 1 from public.posts p where p.id = post_id and p.user_id = auth.uid()));

-- likes
drop policy if exists likes_select on public.likes;
create policy likes_select on public.likes for select to authenticated using (true);
drop policy if exists likes_insert on public.likes;
create policy likes_insert on public.likes for insert to authenticated with check (user_id = auth.uid());
drop policy if exists likes_delete on public.likes;
create policy likes_delete on public.likes for delete to authenticated using (user_id = auth.uid());

-- saved
drop policy if exists saved_select on public.saved_posts;
create policy saved_select on public.saved_posts for select to authenticated using (user_id = auth.uid());
drop policy if exists saved_insert on public.saved_posts;
create policy saved_insert on public.saved_posts for insert to authenticated with check (user_id = auth.uid());
drop policy if exists saved_delete on public.saved_posts;
create policy saved_delete on public.saved_posts for delete to authenticated using (user_id = auth.uid());

-- comments
drop policy if exists comments_select on public.comments;
create policy comments_select on public.comments for select to authenticated using (true);
drop policy if exists comments_insert on public.comments;
create policy comments_insert on public.comments for insert to authenticated with check (user_id = auth.uid());
drop policy if exists comments_delete on public.comments;
create policy comments_delete on public.comments for delete to authenticated using (user_id = auth.uid());

-- comment_likes
drop policy if exists comment_likes_all on public.comment_likes;
create policy comment_likes_all on public.comment_likes for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());

-- follows
drop policy if exists follows_select on public.follows;
create policy follows_select on public.follows for select to authenticated using (true);
drop policy if exists follows_insert on public.follows;
create policy follows_insert on public.follows for insert to authenticated with check (follower_id = auth.uid());
drop policy if exists follows_delete on public.follows;
create policy follows_delete on public.follows for delete to authenticated using (follower_id = auth.uid());

-- stories
drop policy if exists stories_select on public.stories;
create policy stories_select on public.stories for select to anon, authenticated using (true);
drop policy if exists stories_insert on public.stories;
create policy stories_insert on public.stories for insert to authenticated with check (user_id = auth.uid());
drop policy if exists stories_delete on public.stories;
create policy stories_delete on public.stories for delete to authenticated using (user_id = auth.uid());

-- story_views
drop policy if exists story_views_select on public.story_views;
create policy story_views_select on public.story_views for select to authenticated using (true);
drop policy if exists story_views_insert on public.story_views;
create policy story_views_insert on public.story_views for insert to authenticated with check (viewer_id = auth.uid());

-- highlights
drop policy if exists highlights_select on public.highlights;
create policy highlights_select on public.highlights for select to authenticated using (true);
drop policy if exists highlights_own on public.highlights;
create policy highlights_own on public.highlights for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());

drop policy if exists highlight_items_select on public.highlight_items;
create policy highlight_items_select on public.highlight_items for select to authenticated using (true);
drop policy if exists highlight_items_own on public.highlight_items;
create policy highlight_items_own on public.highlight_items for all to authenticated
  using (exists (select 1 from public.highlights h where h.id = highlight_id and h.user_id = auth.uid()))
  with check (exists (select 1 from public.highlights h where h.id = highlight_id and h.user_id = auth.uid()));

-- conversations / messages
drop policy if exists conversations_member on public.conversations;
create policy conversations_member on public.conversations for select to authenticated
  using (exists (select 1 from public.conversation_members cm where cm.conversation_id = id and cm.user_id = auth.uid()));
drop policy if exists conversations_insert on public.conversations;
create policy conversations_insert on public.conversations for insert to authenticated with check (true);

drop policy if exists conversation_members_select on public.conversation_members;
create policy conversation_members_select on public.conversation_members for select to authenticated
  using (exists (select 1 from public.conversation_members cm where cm.conversation_id = conversation_id and cm.user_id = auth.uid()));
drop policy if exists conversation_members_insert on public.conversation_members;
create policy conversation_members_insert on public.conversation_members for insert to authenticated with check (true);

drop policy if exists messages_select on public.messages;
create policy messages_select on public.messages for select to authenticated
  using (exists (select 1 from public.conversation_members cm where cm.conversation_id = conversation_id and cm.user_id = auth.uid()));
drop policy if exists messages_insert on public.messages;
create policy messages_insert on public.messages for insert to authenticated
  with check (sender_id = auth.uid()
    and exists (select 1 from public.conversation_members cm where cm.conversation_id = conversation_id and cm.user_id = auth.uid()));
drop policy if exists messages_update on public.messages;
create policy messages_update on public.messages for update to authenticated
  using (exists (select 1 from public.conversation_members cm where cm.conversation_id = conversation_id and cm.user_id = auth.uid()));

-- notifications
drop policy if exists notifications_own on public.notifications;
create policy notifications_own on public.notifications for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());

-- ========== STORAGE BUCKETS ==========

insert into storage.buckets (id, name, public)
values
  ('avatars', 'avatars', true),
  ('posts', 'posts', true),
  ('stories', 'stories', true)
on conflict (id) do update set public = excluded.public;

drop policy if exists storage_avatars_read on storage.objects;
create policy storage_avatars_read on storage.objects for select using (bucket_id = 'avatars');
drop policy if exists storage_avatars_write on storage.objects;
create policy storage_avatars_write on storage.objects for insert to authenticated
  with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);
drop policy if exists storage_avatars_update on storage.objects;
create policy storage_avatars_update on storage.objects for update to authenticated
  using (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);
drop policy if exists storage_avatars_delete on storage.objects;
create policy storage_avatars_delete on storage.objects for delete to authenticated
  using (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists storage_posts_read on storage.objects;
create policy storage_posts_read on storage.objects for select using (bucket_id = 'posts');
drop policy if exists storage_posts_write on storage.objects;
create policy storage_posts_write on storage.objects for insert to authenticated
  with check (bucket_id = 'posts' and (storage.foldername(name))[1] = auth.uid()::text);
drop policy if exists storage_posts_delete on storage.objects;
create policy storage_posts_delete on storage.objects for delete to authenticated
  using (bucket_id = 'posts' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists storage_stories_read on storage.objects;
create policy storage_stories_read on storage.objects for select using (bucket_id = 'stories');
drop policy if exists storage_stories_write on storage.objects;
create policy storage_stories_write on storage.objects for insert to authenticated
  with check (bucket_id = 'stories' and (storage.foldername(name))[1] = auth.uid()::text);
drop policy if exists storage_stories_delete on storage.objects;
create policy storage_stories_delete on storage.objects for delete to authenticated
  using (bucket_id = 'stories' and (storage.foldername(name))[1] = auth.uid()::text);
