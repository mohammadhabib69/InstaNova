-- ============================================================================
-- 2026-11-02  Fade window and Hot ranking
--
-- The post card fades with the time left: opacity = clamp(remaining / total,
-- 0.4, 1.0). "total" is the window the author picked (1 hour, 6 hours, 24
-- hours, 7 days), so it has to be stored next to expires_at.
--
-- The Hot ranking is Reddit's shape: score damped by the age of the post,
--     hot_rank = score / (age_hours + 2) ^ 1.5
-- computed in the public view so the server can order by it.
--
-- Rollback: 2026-11-02_nova_fade_window_and_hot_rank.rollback.sql
-- ============================================================================

begin;

set local lock_timeout = '10s';

-- ============================================================================
-- 1. The chosen window
-- ============================================================================

alter table public.nova_posts
    add column if not exists duration_seconds integer;

alter table public.posts
    add column if not exists duration_seconds integer;

-- ============================================================================
-- 2. create_nova_post takes the window
-- ============================================================================

create or replace function public.create_nova_post(
    p_body            text        default '',
    p_media_url       text        default null,
    p_media_type      text        default 'text',
    p_content_type    text        default 'all',
    p_flair           text        default null,
    p_is_anonymous    boolean     default false,
    p_expires_at      timestamptz default null,
    p_duration_seconds integer    default null,
    p_poll_options    jsonb       default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    me      uuid := public.request_uid();
    v_id    uuid;
    v_alias text;
    v_mine  integer;
begin
    if me is null then
        raise exception 'not_authenticated';
    end if;
    if p_media_type is null or p_media_type not in ('text','photo','reel','poll') then
        raise exception 'invalid_media_type';
    end if;
    if p_content_type is null or p_content_type not in ('all','photo','reel','text','poll') then
        raise exception 'invalid_content_type';
    end if;
    if coalesce(btrim(p_body), '') = '' and p_media_url is null
       and p_media_type <> 'poll' then
        raise exception 'empty_post';
    end if;
    if p_expires_at is not null and p_expires_at <= now() then
        raise exception 'invalid_expiry';
    end if;

    if p_is_anonymous then
        select count(*)::int into v_mine
        from public.nova_posts p
        where p.author_id = me
          and p.is_anonymous
          and p.deleted_at is null
          and p.created_at > now() - interval '1 day';
        if v_mine >= 10 then
            raise exception 'anonymous_rate_limit';
        end if;
        v_alias := public.nova_random_alias();
    end if;

    insert into public.nova_posts (
        author_id, body, media_url, media_type, content_type, flair,
        is_anonymous, anonymous_alias, expires_at, duration_seconds, poll_options)
    values (
        me, coalesce(p_body, ''), p_media_url, p_media_type, p_content_type, p_flair,
        coalesce(p_is_anonymous, false), v_alias, p_expires_at, p_duration_seconds,
        p_poll_options)
    returning id into v_id;

    return jsonb_build_object(
        'id', v_id,
        'author_id', case when p_is_anonymous then null else me end,
        'alias', coalesce(v_alias,
                          (select pr.username from public.profiles pr where pr.id = me)),
        'is_anonymous', coalesce(p_is_anonymous, false),
        'expires_at', p_expires_at);
end;
$$;

-- ============================================================================
-- 3. create_instagram_post takes the window too
-- ============================================================================

create or replace function public.create_instagram_post(
    p_caption       text        default '',
    p_media_url     text        default null,
    p_is_reel       boolean     default false,
    p_video_url     text        default null,
    p_thumbnail_url text        default null,
    p_is_anonymous  boolean     default false,
    p_expires_at    timestamptz default null,
    p_duration_seconds integer  default null
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
                              expires_at, duration_seconds)
    values (v_owner, coalesce(p_caption, ''), p_thumbnail_url, p_video_url,
            coalesce(p_is_reel, false), 'insta', coalesce(p_is_anonymous, false),
            v_alias, p_expires_at, p_duration_seconds)
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
-- 4. The public views carry the window and the Hot rank
-- ============================================================================

drop view if exists public.v_saved;
drop view if exists public.v_feed;
drop view if exists public.v_posts;
drop view if exists public.nova_posts_public;

create view public.nova_posts_public as
select
    p.id,
    case when p.is_anonymous then null::uuid else p.author_id end as author_id,
    case when p.is_anonymous
         then p.anonymous_alias
         else coalesce(pr.username, 'unknown') end as alias,
    case when p.is_anonymous then null::text else pr.avatar_url end as avatar_url,
    case when p.is_anonymous then null::text else pr.full_name end as full_name,
    p.body,
    p.media_url,
    p.media_type,
    p.content_type,
    p.flair,
    p.is_anonymous,
    p.expires_at,
    p.duration_seconds,
    p.score,
    p.upvote_count,
    p.downvote_count,
    p.comment_count,
    p.poll_options,
    p.poll_votes,
    p.created_at,
    (p.author_id = public.request_uid()) as is_mine,
    case when p.is_anonymous and p.author_id = public.request_uid()
         then coalesce(pr.username, 'you') end as my_alias,
    coalesce(
        (select v.value from public.nova_votes v
          where v.target_type = 'post' and v.target_id = p.id
            and v.voter_id = public.request_uid()),
        0) as my_vote,
    exists (select 1 from public.nova_saves s
             where s.post_id = p.id and s.user_id = public.request_uid()) as is_saved,
    round((p.score::numeric
           / power(greatest(extract(epoch from (now() - p.created_at)) / 3600.0 + 2.0,
                            0.0001), 1.5))::numeric, 8) as hot_rank
from public.nova_posts p
left join public.profiles pr on pr.id = p.author_id
where p.deleted_at is null
  and (p.expires_at is null or p.expires_at > now());

create view public.v_posts as
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
       p.expires_at,
       p.duration_seconds
  from (posts p join profiles pr on pr.id = p.user_id)
 where p.deleted_at is null
   and (p.is_anonymous or public.can_view(p.user_id));

create view public.v_feed as
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
       p.expires_at,
       p.duration_seconds
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

create view public.v_saved as
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
       v.expires_at,
       v.duration_seconds
  from (v_posts v
        join saved_posts s on s.post_id = v.id and s.user_id = public.request_uid())
 where v.expires_at is null or v.expires_at > now();

-- ============================================================================
-- 4b. Grants on the rebuilt views
-- ============================================================================

grant select on table public.nova_posts_public to anon, authenticated;
grant select on table public.v_posts to anon, authenticated;
grant select on table public.v_feed to anon, authenticated;
grant select on table public.v_saved to anon, authenticated;

revoke insert, update, delete, truncate, references, trigger
    on table public.nova_posts_public from anon, authenticated;
revoke insert, update, delete, truncate, references, trigger
    on table public.v_posts from anon, authenticated;
revoke insert, update, delete, truncate, references, trigger
    on table public.v_feed from anon, authenticated;
revoke insert, update, delete, truncate, references, trigger
    on table public.v_saved from anon, authenticated;

-- ============================================================================
-- 5. Grants for the changed signatures
-- ============================================================================

revoke all on function public.create_nova_post(text, text, text, text, text, boolean, timestamptz, integer, jsonb) from public;
grant execute on function public.create_nova_post(text, text, text, text, text, boolean, timestamptz, integer, jsonb) to authenticated;

revoke all on function public.create_instagram_post(text, text, boolean, text, text, boolean, timestamptz, integer) from public;
grant execute on function public.create_instagram_post(text, text, boolean, text, text, boolean, timestamptz, integer) to authenticated;

commit;
