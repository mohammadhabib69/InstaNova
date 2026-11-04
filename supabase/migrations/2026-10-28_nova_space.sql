-- 2026-10-28 — Nova becomes its own posting space.
--
-- Until now every post lived in one list, so the Nova tab could only mirror the
-- Insta feed. This adds the missing marker:
--
--   posts.space = 'insta'  → Home / Explore / Reels / Profile
--   posts.space = 'nova'   → Nova tab only
--
-- Existing rows default to 'insta', so nobody's feed changes content.
-- The view bodies below mirror the live definitions (request_uid(), can_view(),
-- is_archived) and only append `space` as the last column.
--
-- Rollback: supabase/migrations/2026-10-28_nova_space.rollback.sql

-- 1. The marker -------------------------------------------------------------
alter table public.posts
    add column if not exists space text not null default 'insta';

alter table public.posts drop constraint if exists posts_space_check;
alter table public.posts
    add constraint posts_space_check check (space in ('insta', 'nova'));

create index if not exists posts_space_created_idx
    on public.posts (space, created_at desc);

-- 2. Expose it on the post views -------------------------------------------
-- CREATE OR REPLACE VIEW may only append columns at the end, which is what we do.
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
  (select coalesce(jsonb_agg(m.url order by m."position"), '[]'::jsonb)
     from public.post_media m where m.post_id = p.id) as media,
  (exists (select 1 from public.likes l
             where l.post_id = p.id and l.user_id = public.request_uid())) as liked_by_me,
  (exists (select 1 from public.saved_posts s
             where s.post_id = p.id and s.user_id = public.request_uid())) as saved_by_me,
  (select coalesce(jsonb_agg(jsonb_build_object('username', t.username,
                                                'avatar_url', t.avatar_url)), '[]'::jsonb)
     from (select pr2.username, pr2.avatar_url
             from public.likes l2
             join public.profiles pr2 on pr2.id = l2.user_id
            where l2.post_id = p.id
            order by l2.created_at desc
            limit 3) t) as top_likers,
  p.is_archived,
  p.space
from public.posts p
join public.profiles pr on pr.id = p.user_id
where public.can_view(p.user_id);

-- 3. Home feed stays Insta-only -------------------------------------------
create or replace view public.v_feed with (security_invoker = true) as
select * from public.v_posts
where is_reel = false
  and is_archived = false
  and space = 'insta'
  and (public.request_uid() is null
       or user_id = public.request_uid()
       or user_id in (select following_id from public.follows
                       where follower_id = public.request_uid()));

-- 4. Saved keeps both spaces; recreated so it carries the new column --------
-- v_saved already ends with saved_at, and CREATE OR REPLACE may only append, so
-- the columns are listed explicitly and `space` goes last.
create or replace view public.v_saved with (security_invoker = true) as
select
  v.id,
  v.user_id,
  v.username,
  v.avatar_url,
  v.is_verified,
  v.caption,
  v.location,
  v.is_reel,
  v.video_url,
  v.thumbnail_url,
  v.likes_count,
  v.comments_count,
  v.created_at,
  v.media,
  v.liked_by_me,
  v.saved_by_me,
  v.top_likers,
  v.is_archived,
  s.created_at as saved_at,
  v.space
from public.v_posts v
join public.saved_posts s on s.post_id = v.id and s.user_id = public.request_uid();
