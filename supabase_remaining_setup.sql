-- ==============================================================================
-- InstaNova / Insta_Clone: Remaining Supabase Backend Setup Script
-- Includes: SQL A (registration_trigger_fix), SQL B (private_accounts),
--           SQL C (views_fix), SQL D (storage_policies_fix), and Verification Queries.
-- ==============================================================================

-- ==============================================================================
-- 1. SQL A: registration_trigger_fix
-- Handle new user creation when username is omitted during email code sign-up
-- ==============================================================================
create or replace function handle_new_user() returns trigger language plpgsql security definer set search_path = public as $$
begin
  insert into profiles(id, username, full_name, birthday)
  values (
    new.id,
    coalesce(nullif(lower(new.raw_user_meta_data->>'username'), ''), 'user_' || substr(replace(new.id::text, '-', ''), 1, 10)),
    coalesce(new.raw_user_meta_data->>'full_name', ''),
    nullif(new.raw_user_meta_data->>'birthday', '')::date)
  on conflict (id) do nothing;
  return new;
end $$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created after insert on auth.users for each row execute function handle_new_user();

-- ==============================================================================
-- 2. SQL B: private_accounts
-- Add is_private column, follow_requests table, can_view function, policies, and helper functions
-- ==============================================================================
alter table profiles add column if not exists is_private boolean not null default false;

create table if not exists follow_requests (
  requester_id uuid references profiles on delete cascade,
  target_id uuid references profiles on delete cascade,
  created_at timestamptz default now(),
  primary key (requester_id, target_id), check (requester_id <> target_id));

alter table follow_requests enable row level security;

drop policy if exists fr_sel on follow_requests;
drop policy if exists fr_ins on follow_requests;
drop policy if exists fr_del on follow_requests;
create policy fr_sel on follow_requests for select to authenticated using (requester_id = auth.uid() or target_id = auth.uid());
create policy fr_ins on follow_requests for insert to authenticated with check (requester_id = auth.uid());
create policy fr_del on follow_requests for delete to authenticated using (requester_id = auth.uid() or target_id = auth.uid());

create or replace function can_view(p_owner uuid) returns boolean language sql stable security definer set search_path = public as $$
  select p_owner = auth.uid()
      or not coalesce((select is_private from profiles where id = p_owner), false)
      or exists (select 1 from follows where follower_id = auth.uid() and following_id = p_owner) $$;

grant execute on function can_view(uuid) to authenticated;

drop policy if exists posts_read on posts;
create policy posts_read on posts for select to authenticated using (can_view(user_id));

drop policy if exists post_media_read on post_media;
create policy post_media_read on post_media for select to authenticated using (exists (select 1 from posts p where p.id = post_id));

drop policy if exists stories_sel on stories;
create policy stories_sel on stories for select to authenticated using (user_id = auth.uid() or (expires_at > now() and can_view(user_id)));

drop policy if exists highlights_read on highlights;
create policy highlights_read on highlights for select to authenticated using (can_view(user_id));

drop policy if exists highlight_items_read on highlight_items;
create policy highlight_items_read on highlight_items for select to authenticated using (exists (select 1 from highlights h where h.id = highlight_id));

drop policy if exists follows_w on follows;
drop policy if exists follows_ins on follows;
drop policy if exists follows_del on follows;
create policy follows_ins on follows for insert to authenticated
  with check (follower_id = auth.uid() and not exists (select 1 from profiles p where p.id = following_id and p.is_private));
create policy follows_del on follows for delete to authenticated using (follower_id = auth.uid() or following_id = auth.uid());

create or replace function follow_user(p_target uuid) returns text language plpgsql security definer set search_path = public as $$
begin
  if p_target = auth.uid() then return 'self'; end if;
  if exists (select 1 from follows where follower_id = auth.uid() and following_id = p_target) then return 'following'; end if;
  if (select is_private from profiles where id = p_target) then
    insert into follow_requests(requester_id, target_id) values (auth.uid(), p_target) on conflict do nothing;
    return 'requested';
  end if;
  insert into follows(follower_id, following_id) values (auth.uid(), p_target) on conflict do nothing;
  return 'following';
end $$;

create or replace function approve_follow_request(p_requester uuid) returns void language plpgsql security definer set search_path = public as $$
begin
  if exists (select 1 from follow_requests where requester_id = p_requester and target_id = auth.uid()) then
    insert into follows(follower_id, following_id) values (p_requester, auth.uid()) on conflict do nothing;
    delete from follow_requests where requester_id = p_requester and target_id = auth.uid();
  end if;
end $$;

create or replace function set_account_privacy(p_private boolean) returns void language plpgsql security definer set search_path = public as $$
begin
  update profiles set is_private = p_private where id = auth.uid();
  if not p_private then
    insert into follows(follower_id, following_id) select requester_id, auth.uid() from follow_requests where target_id = auth.uid() on conflict do nothing;
    delete from follow_requests where target_id = auth.uid();
  end if;
end $$;

grant execute on function follow_user(uuid), approve_follow_request(uuid), set_account_privacy(boolean) to authenticated;

-- ==============================================================================
-- 3. SQL C: views_fix
-- Ensure v_posts, v_feed, and v_saved have security_invoker = true so anon gets 0 rows
-- ==============================================================================
drop view if exists v_feed;
drop view if exists v_saved;
create or replace view v_posts with (security_invoker = true) as
select p.id, p.user_id, pr.username, pr.avatar_url, pr.is_verified, p.caption, p.location, p.is_reel, p.video_url, p.thumbnail_url,
  p.likes_count, p.comments_count, p.created_at,
  (select coalesce(jsonb_agg(m.url order by m.position), '[]'::jsonb) from post_media m where m.post_id = p.id) as media,
  exists(select 1 from likes l where l.post_id = p.id and l.user_id = auth.uid()) as liked_by_me,
  exists(select 1 from saved_posts s where s.post_id = p.id and s.user_id = auth.uid()) as saved_by_me,
  (select coalesce(jsonb_agg(jsonb_build_object('username', t.username, 'avatar_url', t.avatar_url)), '[]'::jsonb)
   from (select pr2.username, pr2.avatar_url from likes l2 join profiles pr2 on pr2.id = l2.user_id
         where l2.post_id = p.id order by l2.created_at desc limit 3) t) as top_likers
from posts p join profiles pr on pr.id = p.user_id;

create view v_feed with (security_invoker = true) as
select * from v_posts where is_reel = false
  and (user_id = auth.uid() or user_id in (select following_id from follows where follower_id = auth.uid()));

create view v_saved with (security_invoker = true) as
select v.*, s.created_at as saved_at from v_posts v join saved_posts s on s.post_id = v.id and s.user_id = auth.uid();

grant select on v_posts, v_feed, v_saved to authenticated;

-- ==============================================================================
-- 4. SQL D: storage_policies_fix
-- Set storage buckets to public and ensure RLS policies st_read, st_ins, st_upd, st_del exist
-- ==============================================================================
insert into storage.buckets (id, name, public) values
  ('avatars','avatars',true), ('posts','posts',true), ('stories','stories',true), ('reels','reels',true), ('chat','chat',true)
on conflict (id) do update set public = true;

drop policy if exists st_read on storage.objects;
drop policy if exists st_ins on storage.objects;
drop policy if exists st_upd on storage.objects;
drop policy if exists st_del on storage.objects;

create policy st_read on storage.objects for select using (bucket_id in ('avatars','posts','stories','reels','chat'));
create policy st_ins on storage.objects for insert to authenticated
  with check (bucket_id in ('avatars','posts','stories','reels','chat') and (storage.foldername(name))[1] = auth.uid()::text);
create policy st_upd on storage.objects for update to authenticated
  using ((storage.foldername(name))[1] = auth.uid()::text) with check ((storage.foldername(name))[1] = auth.uid()::text);
create policy st_del on storage.objects for delete to authenticated using ((storage.foldername(name))[1] = auth.uid()::text);

-- ==============================================================================
-- 5. Verification Queries (Run these to verify all applied changes)
-- ==============================================================================

-- Q1: Trigger verification
select tgname from pg_trigger where tgname = 'on_auth_user_created';

-- Q2: Public functions verification
select proname from pg_proc p join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public' and proname in (
  'handle_new_user','can_view','follow_user','approve_follow_request',
  'set_account_privacy','get_email_by_username','get_story_groups',
  'get_suggested_users','get_or_create_conversation','get_inbox',
  'get_unread_counts','bump_counters'
);

-- Q3: profiles.is_private column verification
select column_name from information_schema.columns
where table_schema = 'public' and table_name = 'profiles' and column_name = 'is_private';

-- Q4: follow_requests table verification
select to_regclass('public.follow_requests');

-- Q5: RLS policies verification
select tablename, policyname from pg_policies
where schemaname = 'public' and policyname in (
  'posts_read','post_media_read','stories_sel','highlights_read',
  'highlight_items_read','follows_ins','follows_del','fr_sel','fr_ins','fr_del'
);

-- Q6: Storage objects policies verification
select policyname, cmd from pg_policies
where schemaname = 'storage' and tablename = 'objects';

-- Q7: Storage buckets verification
select id, public from storage.buckets;

-- Q8: Trigger test (creates dummy user, checks profile username generation, rolls back via exception)
do $$ declare v uuid := gen_random_uuid(); u text; begin
  insert into auth.users(id, instance_id, aud, role, email, raw_user_meta_data)
  values (v, '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', 'trigger-test@example.invalid', '{}'::jsonb);
  select username into u from public.profiles where id = v;
  raise exception 'TRIGGER_TEST_OK username=%', u;
end $$;

-- Q9: Check that trigger test did not leave any user
select count(*) as leftover_test_users from auth.users where email = 'trigger-test@example.invalid';

-- Q10: Verify view definitions and security_invoker options
select relname, reloptions from pg_class
where relname in ('v_posts', 'v_feed', 'v_saved') and relnamespace = 'public'::regnamespace;
