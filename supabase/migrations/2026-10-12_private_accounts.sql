-- ============================================================================
-- Private accounts, follow requests and content visibility (PAGE 11/12 support)
--
-- IMPORTANT: the original patch only ADDED restrictive policies. Postgres ORs
-- every permissive policy on a table, so the pre-existing permissive policies
-- (posts_select, post_media_select, stories_select, highlights_select,
-- highlight_items_select) would have kept private content world-readable.
-- Those are dropped before the new can_view()-based policies are created.
-- ============================================================================

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

-- content of private accounts is visible only to the owner and approved followers
-- (the previous permissive policies MUST go, otherwise they OR-override these)
drop policy if exists posts_select on posts;
drop policy if exists posts_read on posts;
create policy posts_read on posts for select to authenticated using (can_view(user_id));

drop policy if exists post_media_select on post_media;
drop policy if exists post_media_read on post_media;
create policy post_media_read on post_media for select to authenticated using (exists (select 1 from posts p where p.id = post_id));

drop policy if exists stories_select on stories;
drop policy if exists stories_sel on stories;
create policy stories_sel on stories for select to authenticated using (user_id = auth.uid() or (expires_at > now() and can_view(user_id)));

drop policy if exists highlights_select on highlights;
drop policy if exists highlights_read on highlights;
create policy highlights_read on highlights for select to authenticated using (can_view(user_id));

drop policy if exists highlight_items_select on highlight_items;
drop policy if exists highlight_items_read on highlight_items;
create policy highlight_items_read on highlight_items for select to authenticated using (exists (select 1 from highlights h where h.id = highlight_id));

-- following a private account must go through a request
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
