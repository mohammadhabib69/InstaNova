-- Rollback for 2026-10-28_nova_space.sql — Nova stops being a separate space.
-- Nova posts become ordinary posts again (they stay visible on Home).

create or replace view public.v_feed with (security_invoker = true) as
select * from public.v_posts
where is_reel = false
  and (auth.uid() is null
       or user_id = auth.uid()
       or user_id in (select following_id from public.follows where follower_id = auth.uid()));

create or replace view public.v_saved with (security_invoker = true) as
select v.*, s.created_at as saved_at
from public.v_posts v
join public.saved_posts s on s.post_id = v.id and s.user_id = auth.uid();

drop index if exists public.posts_space_created_idx;

alter table public.posts drop constraint if exists posts_space_check;
alter table public.posts drop column if exists space;
