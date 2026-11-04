-- ============================================================================
-- 2026-11-02  Instagram surface of the shared Post Options module
--
-- The same "Post Options" sheet has to work on Instagram as well as on Nova,
-- so the Instagram table gets the same three columns the Nova table has:
--
--   is_anonymous     the post was written without an author
--   anonymous_alias  the per-post random alias shown instead of a username
--   expires_at       the post disappears at this moment
--
-- The identity guarantee is stronger than "mask it in the view": the real
-- author of an anonymous Instagram post is NOT stored in posts.user_id at
-- all. The row is owned by a single system profile, and the real author
-- lives in posts_anon_owners, which no client role can read. A direct
-- `select * from posts` therefore cannot reveal who wrote an anonymous post.
--
-- The three public views are rebuilt on top of that:
--   * v_posts  - every post, identity masked for anonymous rows
--   * v_feed   - the Instagram home feed, identity masked, expired rows gone
--   * v_saved  - the saved list, identity masked, expired rows gone
--
-- The views move from security_invoker = true to the default (owner rights).
-- public.posts has a `select ... using (true)` policy, so row level security
-- never removed a row from these views; the views' own WHERE clauses were the
-- only effective filter and they are kept exactly as they were. Only the
-- security mode and the new masking change.
--
-- Rollback: 2026-11-02_post_options_instagram_surface.rollback.sql
-- Checks:   2026-11-02_post_options_instagram_surface.verify.sql
-- ============================================================================

begin;

set local lock_timeout = '10s';

-- ============================================================================
-- 1. The three Post Options columns on the Instagram table
-- ============================================================================

alter table public.posts
    add column if not exists is_anonymous boolean not null default false;

alter table public.posts
    add column if not exists anonymous_alias text;

alter table public.posts
    add column if not exists expires_at timestamptz;

create index if not exists idx_posts_expires_at
    on public.posts (expires_at)
    where expires_at is not null;

-- ============================================================================
-- 2. The real author of an anonymous post
--    No client role may read this table.
-- ============================================================================

create table if not exists public.posts_anon_owners (
    post_id    uuid primary key references public.posts(id) on delete cascade,
    owner_id   uuid not null references public.profiles(id) on delete cascade,
    created_at timestamptz not null default now()
);

alter table public.posts_anon_owners enable row level security;

drop policy if exists posts_anon_owners_none on public.posts_anon_owners;
create policy posts_anon_owners_none on public.posts_anon_owners
    for all to authenticated
    using (false)
    with check (false);

revoke all on table public.posts_anon_owners from anon, authenticated;

-- ============================================================================
-- 3. The system profile that owns anonymous rows in public.posts
--    One row, shared by every anonymous post on Instagram.
-- ============================================================================

insert into public.profiles (id, username, full_name, bio, is_private, is_verified)
values ('00000000-0000-4000-8000-00000000a001',
        'nova.anonymous', 'Anonymous', '', true, false)
on conflict (id) do nothing;

-- ============================================================================
-- 4. RPC: create_instagram_post
--    The single insert path for Instagram posts. It generates the alias on
--    the server for anonymous posts and keeps the real author out of
--    posts.user_id.
-- ============================================================================

create or replace function public.create_instagram_post(
    p_caption     text        default '',
    p_media_url   text        default null,
    p_is_reel     boolean     default false,
    p_video_url   text        default null,
    p_thumbnail_url text      default null,
    p_is_anonymous boolean    default false,
    p_expires_at  timestamptz default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    me      uuid := public.request_uid();
    v_alias text;
    v_owner uuid;
    v_id    uuid;
begin
    if me is null then
        raise exception 'not_authenticated';
    end if;
    if p_expires_at is not null and p_expires_at <= now() then
        raise exception 'invalid_expiry';
    end if;

    if coalesce(p_is_anonymous, false) then
        v_alias := public.nova_random_alias();
        v_owner := '00000000-0000-4000-8000-00000000a001'::uuid;
    else
        v_owner := me;
    end if;

    insert into public.posts (user_id, caption, thumbnail_url, video_url,
                              is_reel, space, is_anonymous, anonymous_alias,
                              expires_at)
    values (v_owner, coalesce(p_caption, ''), p_thumbnail_url, p_video_url,
            coalesce(p_is_reel, false), 'insta', coalesce(p_is_anonymous, false),
            v_alias, p_expires_at)
    returning id into v_id;

    if p_media_url is not null and btrim(p_media_url) <> '' then
        insert into public.post_media (post_id, url, position)
        values (v_id, p_media_url, 0);
    end if;

    if coalesce(p_is_anonymous, false) then
        insert into public.posts_anon_owners (post_id, owner_id)
        values (v_id, me)
        on conflict (post_id) do update set owner_id = excluded.owner_id;
    end if;

    return jsonb_build_object(
        'id', v_id,
        'user_id', case when p_is_anonymous then null else me end,
        'is_anonymous', coalesce(p_is_anonymous, false),
        'anonymous_alias', v_alias,
        'expires_at', p_expires_at);
end;
$$;

-- ============================================================================
-- 5. RPCs that keep own-post actions working for anonymous rows
--    The row's user_id is the system profile, so ownership has to be looked
--    up in posts_anon_owners for anonymous posts.
-- ============================================================================

create or replace function public.post_is_mine(p_post_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1 from public.posts p
         where p.id = p_post_id
           and (p.user_id = public.request_uid()
                or exists (select 1 from public.posts_anon_owners o
                            where o.post_id = p.id
                              and o.owner_id = public.request_uid())));
$$;

create or replace function public.delete_my_post(p_post_id uuid)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
begin
    if public.request_uid() is null then
        raise exception 'not_authenticated';
    end if;
    if not public.post_is_mine(p_post_id) then
        raise exception 'not_owner';
    end if;
    delete from public.posts where id = p_post_id;
    return true;
end;
$$;

create or replace function public.update_my_post_caption(p_post_id uuid, p_caption text)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
begin
    if public.request_uid() is null then
        raise exception 'not_authenticated';
    end if;
    if not public.post_is_mine(p_post_id) then
        raise exception 'not_owner';
    end if;
    update public.posts set caption = coalesce(p_caption, '') where id = p_post_id;
    return true;
end;
$$;

-- ============================================================================
-- 6. The rebuilt public views
-- ============================================================================

-- v_posts ---------------------------------------------------------------
create or replace view public.v_posts as
select p.id,
       case when p.is_anonymous then null::uuid else p.user_id end as user_id,
       case when p.is_anonymous then p.anonymous_alias else pr.username end as username,
       case when p.is_anonymous then null::text else pr.avatar_url end as avatar_url,
       case when p.is_anonymous then false else pr.is_verified end as is_verified,
       p.caption,
       p.location,
       p.is_reel,
       p.video_url,
       p.thumbnail_url,
       p.likes_count,
       p.comments_count,
       p.created_at,
       (select coalesce(jsonb_agg(m.url order by m."position"), '[]'::jsonb) as "coalesce"
          from post_media m
         where m.post_id = p.id) as media,
       (exists (select 1 from likes l
                 where l.post_id = p.id and l.user_id = public.request_uid())) as liked_by_me,
       (exists (select 1 from saved_posts s
                 where s.post_id = p.id and s.user_id = public.request_uid())) as saved_by_me,
       (select coalesce(jsonb_agg(jsonb_build_object('username', t.username,
                                                      'avatar_url', t.avatar_url)),
                        '[]'::jsonb) as "coalesce"
          from (select pr2.username, pr2.avatar_url
                  from (likes l2 join profiles pr2 on pr2.id = l2.user_id)
                 where l2.post_id = p.id
                 order by l2.created_at desc
                 limit 3) t) as top_likers,
       p.is_archived,
       p.space,
       p.is_anonymous,
       p.anonymous_alias,
       p.expires_at
  from (posts p join profiles pr on pr.id = p.user_id)
 where p.is_anonymous
    or public.can_view(p.user_id);

comment on view public.v_posts is
    'Every post. The author id of an anonymous post is NULL and the alias '
    'replaces the username; the real author is kept server-side only.';

-- v_feed ----------------------------------------------------------------
-- Rebuilt straight from public.posts (not from v_posts) so the feed filter
-- can use the REAL author of an anonymous post and the identity can still be
-- masked in the output.
create or replace view public.v_feed as
select p.id,
       case when p.is_anonymous then null::uuid else p.user_id end as user_id,
       case when p.is_anonymous then p.anonymous_alias else pr.username end as username,
       case when p.is_anonymous then null::text else pr.avatar_url end as avatar_url,
       case when p.is_anonymous then false else pr.is_verified end as is_verified,
       p.caption,
       p.location,
       p.is_reel,
       p.video_url,
       p.thumbnail_url,
       p.likes_count,
       p.comments_count,
       p.created_at,
       (select coalesce(jsonb_agg(m.url order by m."position"), '[]'::jsonb) as "coalesce"
          from post_media m
         where m.post_id = p.id) as media,
       (exists (select 1 from likes l
                 where l.post_id = p.id and l.user_id = public.request_uid())) as liked_by_me,
       (exists (select 1 from saved_posts s
                 where s.post_id = p.id and s.user_id = public.request_uid())) as saved_by_me,
       (select coalesce(jsonb_agg(jsonb_build_object('username', t.username,
                                                      'avatar_url', t.avatar_url)),
                        '[]'::jsonb) as "coalesce"
          from (select pr2.username, pr2.avatar_url
                  from (likes l2 join profiles pr2 on pr2.id = l2.user_id)
                 where l2.post_id = p.id
                 order by l2.created_at desc
                 limit 3) t) as top_likers,
       p.is_archived,
       p.space,
       p.is_anonymous,
       p.anonymous_alias,
       p.expires_at
  from (posts p join profiles pr on pr.id = p.user_id)
 where p.is_reel = false
   and p.is_archived = false
   and p.space = 'insta'
   and (p.expires_at is null or p.expires_at > now())
   and (public.request_uid() is null
        or p.user_id = public.request_uid()
        or coalesce((select o.owner_id from public.posts_anon_owners o
                      where o.post_id = p.id), p.user_id) = public.request_uid()
        or coalesce((select o.owner_id from public.posts_anon_owners o
                      where o.post_id = p.id), p.user_id) in
           (select f.following_id from public.follows f
             where f.follower_id = public.request_uid()));

-- v_saved ---------------------------------------------------------------
create or replace view public.v_saved as
select v.id,
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
       v.space,
       v.is_anonymous,
       v.anonymous_alias,
       v.expires_at
  from (v_posts v
        join saved_posts s on s.post_id = v.id and s.user_id = public.request_uid())
 where v.expires_at is null or v.expires_at > now();

-- ============================================================================
-- 7. Expiry for Instagram posts
--    Same job as Nova: soft-delete, drop the media rows and queue the
--    storage object for removal.
-- ============================================================================

create or replace function public.nova_expire_instagram_posts()
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
    v_count integer;
begin
    insert into public.nova_expired_media (post_id, bucket_id, object_name)
    select p.id, 'posts', coalesce(p.thumbnail_url, '')
      from public.posts p
     where p.deleted_at is null
       and p.expires_at is not null
       and p.expires_at <= now()
       and p.thumbnail_url is not null
       and p.thumbnail_url <> ''
    on conflict do nothing;

    update public.posts p
       set deleted_at = now()
     where p.deleted_at is null
       and p.expires_at is not null
       and p.expires_at <= now();

    get diagnostics v_count = row_count;

    delete from public.post_media m
     using public.posts p
     where m.post_id = p.id
       and p.deleted_at is not null
       and p.expires_at is not null
       and p.expires_at <= now();

    return v_count;
end;
$$;

-- public.posts has no deleted_at column yet; add it so the expiry job can
-- soft-delete instead of destroying the row.
alter table public.posts
    add column if not exists deleted_at timestamptz;

-- Re-run the definition now that deleted_at exists (the function above needs
-- the column at run time, not at create time, but the order is kept clear).
create or replace function public.nova_expire_instagram_posts()
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
    v_count integer;
begin
    insert into public.nova_expired_media (post_id, bucket_id, object_name)
    select p.id, 'posts', coalesce(p.thumbnail_url, '')
      from public.posts p
     where p.deleted_at is null
       and p.expires_at is not null
       and p.expires_at <= now()
       and p.thumbnail_url is not null
       and p.thumbnail_url <> ''
    on conflict do nothing;

    update public.posts p
       set deleted_at = now()
     where p.deleted_at is null
       and p.expires_at is not null
       and p.expires_at <= now();

    get diagnostics v_count = row_count;

    delete from public.post_media m
     using public.posts p
     where m.post_id = p.id
       and p.deleted_at is not null
       and p.expires_at is not null
       and p.expires_at <= now();

    return v_count;
end;
$$;

-- The public views must not show a soft-deleted post.
create or replace view public.v_posts as
select p.id,
       case when p.is_anonymous then null::uuid else p.user_id end as user_id,
       case when p.is_anonymous then p.anonymous_alias else pr.username end as username,
       case when p.is_anonymous then null::text else pr.avatar_url end as avatar_url,
       case when p.is_anonymous then false else pr.is_verified end as is_verified,
       p.caption,
       p.location,
       p.is_reel,
       p.video_url,
       p.thumbnail_url,
       p.likes_count,
       p.comments_count,
       p.created_at,
       (select coalesce(jsonb_agg(m.url order by m."position"), '[]'::jsonb) as "coalesce"
          from post_media m
         where m.post_id = p.id) as media,
       (exists (select 1 from likes l
                 where l.post_id = p.id and l.user_id = public.request_uid())) as liked_by_me,
       (exists (select 1 from saved_posts s
                 where s.post_id = p.id and s.user_id = public.request_uid())) as saved_by_me,
       (select coalesce(jsonb_agg(jsonb_build_object('username', t.username,
                                                      'avatar_url', t.avatar_url)),
                        '[]'::jsonb) as "coalesce"
          from (select pr2.username, pr2.avatar_url
                  from (likes l2 join profiles pr2 on pr2.id = l2.user_id)
                 where l2.post_id = p.id
                 order by l2.created_at desc
                 limit 3) t) as top_likers,
       p.is_archived,
       p.space,
       p.is_anonymous,
       p.anonymous_alias,
       p.expires_at
  from (posts p join profiles pr on pr.id = p.user_id)
 where p.deleted_at is null
   and (p.is_anonymous or public.can_view(p.user_id));

create or replace view public.v_feed as
select p.id,
       case when p.is_anonymous then null::uuid else p.user_id end as user_id,
       case when p.is_anonymous then p.anonymous_alias else pr.username end as username,
       case when p.is_anonymous then null::text else pr.avatar_url end as avatar_url,
       case when p.is_anonymous then false else pr.is_verified end as is_verified,
       p.caption,
       p.location,
       p.is_reel,
       p.video_url,
       p.thumbnail_url,
       p.likes_count,
       p.comments_count,
       p.created_at,
       (select coalesce(jsonb_agg(m.url order by m."position"), '[]'::jsonb) as "coalesce"
          from post_media m
         where m.post_id = p.id) as media,
       (exists (select 1 from likes l
                 where l.post_id = p.id and l.user_id = public.request_uid())) as liked_by_me,
       (exists (select 1 from saved_posts s
                 where s.post_id = p.id and s.user_id = public.request_uid())) as saved_by_me,
       (select coalesce(jsonb_agg(jsonb_build_object('username', t.username,
                                                      'avatar_url', t.avatar_url)),
                        '[]'::jsonb) as "coalesce"
          from (select pr2.username, pr2.avatar_url
                  from (likes l2 join profiles pr2 on pr2.id = l2.user_id)
                 where l2.post_id = p.id
                 order by l2.created_at desc
                 limit 3) t) as top_likers,
       p.is_archived,
       p.space,
       p.is_anonymous,
       p.anonymous_alias,
       p.expires_at
  from (posts p join profiles pr on pr.id = p.user_id)
 where p.deleted_at is null
   and p.is_reel = false
   and p.is_archived = false
   and p.space = 'insta'
   and (p.expires_at is null or p.expires_at > now())
   and (public.request_uid() is null
        or p.user_id = public.request_uid()
        or coalesce((select o.owner_id from public.posts_anon_owners o
                      where o.post_id = p.id), p.user_id) = public.request_uid()
        or coalesce((select o.owner_id from public.posts_anon_owners o
                      where o.post_id = p.id), p.user_id) in
           (select f.following_id from public.follows f
             where f.follower_id = public.request_uid()));

create or replace view public.v_saved as
select v.id,
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
       v.space,
       v.is_anonymous,
       v.anonymous_alias,
       v.expires_at
  from (v_posts v
        join saved_posts s on s.post_id = v.id and s.user_id = public.request_uid())
 where v.expires_at is null or v.expires_at > now();

-- ============================================================================
-- 8. The expiry job now covers both surfaces
-- ============================================================================

do $$
declare
    v_job record;
begin
    for v_job in select jobid from cron.job where jobname = 'expire_nova_posts' loop
        perform cron.unschedule(v_job.jobid);
    end loop;
end $$;

select cron.schedule('expire_nova_posts', '* * * * *', $cron$
    select public.nova_expire_posts();
    select public.nova_expire_instagram_posts();
    select public.nova_purge_expired_media();
$cron$);

-- ============================================================================
-- 9. Grants
-- ============================================================================

revoke all on function public.create_instagram_post(text, text, boolean, text, text, boolean, timestamptz) from public;
grant execute on function public.create_instagram_post(text, text, boolean, text, text, boolean, timestamptz) to authenticated;

revoke all on function public.delete_my_post(uuid) from public;
grant execute on function public.delete_my_post(uuid) to authenticated;

revoke all on function public.update_my_post_caption(uuid, text) from public;
grant execute on function public.update_my_post_caption(uuid, text) to authenticated;

revoke all on function public.post_is_mine(uuid) from public;
grant execute on function public.post_is_mine(uuid) to authenticated;

revoke all on function public.nova_expire_instagram_posts() from public;
grant execute on function public.nova_expire_instagram_posts() to authenticated;

commit;
