-- ============================================================================
-- 2026-11-02  Nova / Instagram separation + shared Reddit-style Post Options
--
-- Goal
--   * Nova gets its own physical tables: nova_posts, nova_comments, nova_votes,
--     nova_saves. The Nova feed reads ONLY these. Instagram reads ONLY the
--     Instagram tables, so a Nova post can never surface on the Insta feed,
--     reels tab, profile grid or saved list.
--   * A shared "Post Options" contract that works on both surfaces:
--       - anonymous posting (real author_id kept server-side, masked in a public
--         view, per-post random alias, 10/day rate limit)
--       - timed posts (expires_at, soft delete by pg_cron, fading opacity)
--       - Reddit-style voting (one vote per user per target, single RPC)
--       - threaded comments (parent_id, comment votes, anonymous comments)
--   * A composer destination row: "Nova only" by default, plus an explicit
--     "Also share to Instagram" toggle (Post / Story / Reel) that writes a
--     linked Instagram row carrying source_nova_post_id.
--
-- Compatibility
--   * posts.space keeps working; existing Nova rows in `posts` are migrated
--     into nova_posts and then deleted, so nothing is lost and nothing leaks.
--   * No Instagram column, view, policy or theme is removed.
--
-- Rollback: 2026-11-02_nova_separation_post_options.rollback.sql
-- Checks:   2026-11-02_nova_separation_post_options.verify.sql
-- ============================================================================

begin;

-- Never queue behind a long transaction during a deploy.
set local lock_timeout = '10s';

-- ============================================================================
-- 1. Linked-source columns on the Instagram side
--    A shared Instagram item records which Nova post it came from. Nothing
--    reads this column to build a feed; it is provenance only.
-- ============================================================================

alter table public.posts
    add column if not exists source_nova_post_id uuid;

alter table public.stories
    add column if not exists source_nova_post_id uuid;

create index if not exists idx_posts_source_nova_post
    on public.posts (source_nova_post_id)
    where source_nova_post_id is not null;

create index if not exists idx_stories_source_nova_post
    on public.stories (source_nova_post_id)
    where source_nova_post_id is not null;

-- Every row that has no explicit space is an Instagram row. Rows written
-- before this migration are space IS NULL and would silently drop out of
-- v_feed (which filters space = 'insta').
update public.posts set space = 'insta' where space is null;

alter table public.posts
    alter column space set default 'insta';

-- ============================================================================
-- 2. nova_posts
-- ============================================================================

create table if not exists public.nova_posts (
    id              uuid primary key default gen_random_uuid(),
    author_id       uuid not null references public.profiles(id) on delete cascade,
    body            text not null default '',
    media_url       text,
    media_type      text not null default 'text',
    content_type    text not null default 'all',
    flair           text,
    is_anonymous    boolean not null default false,
    anonymous_alias text,
    expires_at      timestamptz,
    score           integer not null default 0,
    upvote_count    integer not null default 0,
    downvote_count  integer not null default 0,
    comment_count   integer not null default 0,
    poll_options    jsonb,
    poll_votes      jsonb,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    deleted_at      timestamptz,
    constraint nova_posts_media_type_check
        check (media_type in ('text', 'photo', 'reel', 'poll')),
    constraint nova_posts_content_type_check
        check (content_type in ('all', 'photo', 'reel', 'text', 'poll'))
);

create index if not exists idx_nova_posts_created_at
    on public.nova_posts (created_at desc);
create index if not exists idx_nova_posts_expires_at
    on public.nova_posts (expires_at)
    where expires_at is not null;
create index if not exists idx_nova_posts_score
    on public.nova_posts (score desc);
create index if not exists idx_nova_posts_author
    on public.nova_posts (author_id, created_at desc);
create index if not exists idx_nova_posts_content_type
    on public.nova_posts (content_type, created_at desc);
create index if not exists idx_nova_posts_live
    on public.nova_posts (deleted_at, created_at desc);

-- ============================================================================
-- 3. nova_comments (threaded, votable, optionally anonymous)
-- ============================================================================

create table if not exists public.nova_comments (
    id              uuid primary key default gen_random_uuid(),
    post_id         uuid not null references public.nova_posts(id) on delete cascade,
    parent_id       uuid references public.nova_comments(id) on delete cascade,
    author_id       uuid not null references public.profiles(id) on delete cascade,
    body            text not null,
    is_anonymous    boolean not null default false,
    anonymous_alias text,
    score           integer not null default 0,
    upvote_count    integer not null default 0,
    downvote_count  integer not null default 0,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    deleted_at      timestamptz
);

create index if not exists idx_nova_comments_post
    on public.nova_comments (post_id, created_at asc);
create index if not exists idx_nova_comments_parent
    on public.nova_comments (parent_id);
create index if not exists idx_nova_comments_author
    on public.nova_comments (author_id, created_at desc);
create index if not exists idx_nova_comments_score
    on public.nova_comments (score desc);

-- ============================================================================
-- 4. nova_votes  (one vote per user per target)
-- ============================================================================

create table if not exists public.nova_votes (
    id          uuid primary key default gen_random_uuid(),
    voter_id    uuid not null references public.profiles(id) on delete cascade,
    target_type text not null,
    target_id   uuid not null,
    value       integer not null,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    constraint nova_votes_target_type_check check (target_type in ('post', 'comment')),
    constraint nova_votes_value_check check (value in (-1, 1)),
    constraint nova_votes_unique_target unique (voter_id, target_type, target_id)
);

create index if not exists idx_nova_votes_target
    on public.nova_votes (target_type, target_id);
create index if not exists idx_nova_votes_voter
    on public.nova_votes (voter_id);

-- ============================================================================
-- 5. nova_saves
-- ============================================================================

create table if not exists public.nova_saves (
    user_id    uuid not null references public.profiles(id) on delete cascade,
    post_id    uuid not null references public.nova_posts(id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (user_id, post_id)
);

create index if not exists idx_nova_saves_post on public.nova_saves (post_id);

-- ============================================================================
-- 6. Media cleanup queue for expired Nova posts
-- ============================================================================

create table if not exists public.nova_expired_media (
    id         uuid primary key default gen_random_uuid(),
    post_id    uuid not null references public.nova_posts(id) on delete cascade,
    bucket_id  text not null default 'nova',
    object_name text not null,
    created_at timestamptz not null default now(),
    purged_at  timestamptz
);

create index if not exists idx_nova_expired_media_pending
    on public.nova_expired_media (created_at)
    where purged_at is null;

-- ============================================================================
-- 7. Row level security on every Nova table
--
--    Reads go through the public views (section 8). The base tables keep RLS
--    on with write-own-row policies as defence in depth; direct table access
--    is revoked from the client roles so the real author_id of an anonymous
--    post can never leave the database.
-- ============================================================================

alter table public.nova_posts    enable row level security;
alter table public.nova_comments enable row level security;
alter table public.nova_votes    enable row level security;
alter table public.nova_saves    enable row level security;
alter table public.nova_expired_media enable row level security;

-- nova_posts -------------------------------------------------------------
drop policy if exists nova_posts_insert_own on public.nova_posts;
create policy nova_posts_insert_own on public.nova_posts
    for insert to authenticated
    with check (author_id = public.request_uid());

drop policy if exists nova_posts_update_own on public.nova_posts;
create policy nova_posts_update_own on public.nova_posts
    for update to authenticated
    using (author_id = public.request_uid())
    with check (author_id = public.request_uid());

drop policy if exists nova_posts_delete_own on public.nova_posts;
create policy nova_posts_delete_own on public.nova_posts
    for delete to authenticated
    using (author_id = public.request_uid());

-- nova_comments ----------------------------------------------------------
drop policy if exists nova_comments_insert_own on public.nova_comments;
create policy nova_comments_insert_own on public.nova_comments
    for insert to authenticated
    with check (author_id = public.request_uid());

drop policy if exists nova_comments_update_own on public.nova_comments;
create policy nova_comments_update_own on public.nova_comments
    for update to authenticated
    using (author_id = public.request_uid())
    with check (author_id = public.request_uid());

drop policy if exists nova_comments_delete_own on public.nova_comments;
create policy nova_comments_delete_own on public.nova_comments
    for delete to authenticated
    using (author_id = public.request_uid());

-- nova_votes -------------------------------------------------------------
drop policy if exists nova_votes_select_own on public.nova_votes;
create policy nova_votes_select_own on public.nova_votes
    for select to authenticated
    using (voter_id = public.request_uid());

drop policy if exists nova_votes_insert_own on public.nova_votes;
create policy nova_votes_insert_own on public.nova_votes
    for insert to authenticated
    with check (voter_id = public.request_uid());

drop policy if exists nova_votes_delete_own on public.nova_votes;
create policy nova_votes_delete_own on public.nova_votes
    for delete to authenticated
    using (voter_id = public.request_uid());

-- nova_saves -------------------------------------------------------------
drop policy if exists nova_saves_select_own on public.nova_saves;
create policy nova_saves_select_own on public.nova_saves
    for select to authenticated
    using (user_id = public.request_uid());

drop policy if exists nova_saves_insert_own on public.nova_saves;
create policy nova_saves_insert_own on public.nova_saves
    for insert to authenticated
    with check (user_id = public.request_uid());

drop policy if exists nova_saves_delete_own on public.nova_saves;
create policy nova_saves_delete_own on public.nova_saves
    for delete to authenticated
    using (user_id = public.request_uid());

-- nova_expired_media is server-only.
drop policy if exists nova_expired_media_none on public.nova_expired_media;
create policy nova_expired_media_none on public.nova_expired_media
    for all to authenticated
    using (false)
    with check (false);

-- ============================================================================
-- 8. Public views
--
--    The views run with the view owner's rights (security_invoker = false, the
--    PostgreSQL default), so they can read the base tables that the client
--    roles may not. They are the only path a client has into Nova content.
--
--    For an anonymous post:
--      author_id  -> NULL      (the real id never leaves the server)
--      alias      -> "Anonymous Fox" (generated once, at creation time)
--      avatar_url -> NULL      (client draws the placeholder)
--    For a normal post the author's real id and profile are returned.
--
--    is_mine is computed from the real author_id, so the owner still finds
--    their own anonymous posts under "Mine" even though author_id is masked.
-- ============================================================================

create or replace view public.nova_posts_public as
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
             where s.post_id = p.id and s.user_id = public.request_uid()) as is_saved
from public.nova_posts p
left join public.profiles pr on pr.id = p.author_id
where p.deleted_at is null
  and (p.expires_at is null or p.expires_at > now());

comment on view public.nova_posts_public is
    'Masked public feed of Nova posts. author_id is NULL for anonymous posts; '
    'the owner still sees them through is_mine.';

create or replace view public.nova_comments_public as
select
    c.id,
    c.post_id,
    c.parent_id,
    case when c.is_anonymous then null::uuid else c.author_id end as author_id,
    case when c.is_anonymous
         then c.anonymous_alias
         else coalesce(pr.username, 'unknown') end as alias,
    case when c.is_anonymous then null::text else pr.avatar_url end as avatar_url,
    c.body,
    c.is_anonymous,
    c.score,
    c.upvote_count,
    c.downvote_count,
    c.created_at,
    (c.author_id = public.request_uid()) as is_mine,
    coalesce(
        (select v.value from public.nova_votes v
          where v.target_type = 'comment' and v.target_id = c.id
            and v.voter_id = public.request_uid()),
        0) as my_vote
from public.nova_comments c
left join public.profiles pr on pr.id = c.author_id
where c.deleted_at is null;

comment on view public.nova_comments_public is
    'Masked public feed of Nova comments. author_id is NULL for anonymous comments.';

-- ============================================================================
-- 9. Grants
--    The base tables are unreachable from the client roles. Only the views and
--    the RPCs below are callable.
-- ============================================================================

revoke all on table public.nova_posts from anon, authenticated;
revoke all on table public.nova_comments from anon, authenticated;
revoke all on table public.nova_votes from anon, authenticated;
revoke all on table public.nova_expired_media from anon, authenticated;

revoke all on table public.nova_saves from anon;
grant select, insert, delete on table public.nova_saves to authenticated;

grant select on table public.nova_posts_public to anon, authenticated;
grant select on table public.nova_comments_public to anon, authenticated;

-- ============================================================================
-- 10. Helper: a per-post random alias for anonymous content
-- ============================================================================

create or replace function public.nova_random_alias()
returns text
language plpgsql
volatile
security definer
set search_path = public
as $$
declare
    adjectives text[] := array['Swift','Quiet','Brave','Curious','Bright','Calm',
                               'Clever','Gentle','Lucky','Nimble','Silent','Sunny',
                               'Wild','Witty','Zesty','Hidden','Velvet','Copper'];
    animals    text[] := array['Fox','Owl','Falcon','Panda','Otter','Heron','Wolf',
                               'Lynx','Marten','Badger','Gecko','Crane','Ibex',
                               'Dingo','Finch','Hare','Mole','Ibex'];
begin
    return 'Anonymous '
        || adjectives[1 + floor(random() * array_length(adjectives, 1))::int]
        || ' '
        || animals[1 + floor(random() * array_length(animals, 1))::int];
end;
$$;

-- ============================================================================
-- 11. RPC: create_nova_post
--     Owns the anonymous rate limit, the alias and the expiry validation.
-- ============================================================================

create or replace function public.create_nova_post(
    p_body         text        default '',
    p_media_url    text        default null,
    p_media_type   text        default 'text',
    p_content_type text        default 'all',
    p_flair        text        default null,
    p_is_anonymous boolean     default false,
    p_expires_at   timestamptz default null,
    p_poll_options jsonb       default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    me       uuid := public.request_uid();
    v_id     uuid;
    v_alias  text;
    v_mine   integer;
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
        is_anonymous, anonymous_alias, expires_at, poll_options)
    values (
        me, coalesce(p_body, ''), p_media_url, p_media_type, p_content_type, p_flair,
        coalesce(p_is_anonymous, false), v_alias, p_expires_at, p_poll_options)
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
-- 12. RPC: create_nova_comment  (threaded, optionally anonymous)
-- ============================================================================

create or replace function public.create_nova_comment(
    p_post_id      uuid    default null,
    p_body         text    default '',
    p_parent_id    uuid    default null,
    p_is_anonymous boolean default false
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
    v_owner uuid;
    v_mine  integer;
begin
    if me is null then
        raise exception 'not_authenticated';
    end if;
    if coalesce(btrim(p_body), '') = '' then
        raise exception 'empty_comment';
    end if;

    select p.author_id into v_owner
    from public.nova_posts p
    where p.id = p_post_id and p.deleted_at is null;

    if v_owner is null then
        raise exception 'not_found';
    end if;

    if p_parent_id is not null then
        if not exists (select 1 from public.nova_comments c
                       where c.id = p_parent_id and c.post_id = p_post_id
                         and c.deleted_at is null) then
            raise exception 'invalid_parent';
        end if;
    end if;

    if p_is_anonymous then
        select count(*)::int into v_mine
        from public.nova_comments c
        where c.author_id = me
          and c.is_anonymous
          and c.deleted_at is null
          and c.created_at > now() - interval '1 day';
        if v_mine >= 30 then
            raise exception 'anonymous_rate_limit';
        end if;
        v_alias := public.nova_random_alias();
    end if;

    insert into public.nova_comments (
        post_id, parent_id, author_id, body, is_anonymous, anonymous_alias)
    values (p_post_id, p_parent_id, me, p_body,
            coalesce(p_is_anonymous, false), v_alias)
    returning id into v_id;

    update public.nova_posts
       set comment_count = (
               select count(*)::int from public.nova_comments c
                where c.post_id = p_post_id and c.deleted_at is null),
           updated_at = now()
     where id = p_post_id;

    return jsonb_build_object(
        'id', v_id,
        'author_id', case when p_is_anonymous then null else me end,
        'alias', coalesce(v_alias,
                          (select pr.username from public.profiles pr where pr.id = me)),
        'is_anonymous', coalesce(p_is_anonymous, false));
end;
$$;

-- ============================================================================
-- 13. RPC: nova_cast_vote  (single statement, no races)
--
--     The target row is locked FOR UPDATE first, so two simultaneous votes on
--     the same post are serialised and the counters cannot drift.
--     value  1 = upvote, -1 = downvote, 0 = remove the vote.
--     Voting the same value again removes the vote (toggle); voting a
--     different value switches it.
-- ============================================================================

create or replace function public.nova_cast_vote(
    p_target_type text,
    p_target_id   uuid,
    p_value       integer
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    me      uuid := public.request_uid();
    v_owner uuid;
    v_prev  integer;
    v_new   integer;
    v_up    integer;
    v_down  integer;
begin
    if me is null then
        raise exception 'not_authenticated';
    end if;
    if p_target_type is null or p_target_type not in ('post','comment') then
        raise exception 'invalid_target_type';
    end if;
    if p_value is null or p_value not in (-1, 0, 1) then
        raise exception 'invalid_value';
    end if;

    if p_target_type = 'post' then
        select p.author_id into v_owner
        from public.nova_posts p
        where p.id = p_target_id and p.deleted_at is null
        for update;
    else
        select c.author_id into v_owner
        from public.nova_comments c
        where c.id = p_target_id and c.deleted_at is null
        for update;
    end if;

    if v_owner is null then
        raise exception 'not_found';
    end if;
    if v_owner = me then
        raise exception 'own_content';
    end if;

    select v.value into v_prev
    from public.nova_votes v
    where v.voter_id = me
      and v.target_type = p_target_type
      and v.target_id = p_target_id;

    if p_value = 0 or (v_prev is not null and v_prev = p_value) then
        delete from public.nova_votes v
        where v.voter_id = me
          and v.target_type = p_target_type
          and v.target_id = p_target_id;
        v_new := 0;
    else
        insert into public.nova_votes (voter_id, target_type, target_id, value)
        values (me, p_target_type, p_target_id, p_value)
        on conflict (voter_id, target_type, target_id)
        do update set value = excluded.value, updated_at = now();
        v_new := p_value;
    end if;

    select count(*) filter (where value = 1)::int,
           count(*) filter (where value = -1)::int
    into v_up, v_down
    from public.nova_votes v
    where v.target_type = p_target_type and v.target_id = p_target_id;

    if p_target_type = 'post' then
        update public.nova_posts p
           set upvote_count = v_up,
               downvote_count = v_down,
               score = v_up - v_down,
               updated_at = now()
         where p.id = p_target_id;
    else
        update public.nova_comments c
           set upvote_count = v_up,
               downvote_count = v_down,
               score = v_up - v_down,
               updated_at = now()
         where c.id = p_target_id;
    end if;

    return jsonb_build_object(
        'my_vote', v_new,
        'score', v_up - v_down,
        'upvote_count', v_up,
        'downvote_count', v_down);
end;
$$;

-- The public entry point named in the spec: cast_vote(post_id, value).
create or replace function public.cast_vote(p_post_id uuid, p_value integer)
returns jsonb
language sql
security definer
set search_path = public
as $$
    select public.nova_cast_vote('post', p_post_id, p_value);
$$;

create or replace function public.cast_comment_vote(p_comment_id uuid, p_value integer)
returns jsonb
language sql
security definer
set search_path = public
as $$
    select public.nova_cast_vote('comment', p_comment_id, p_value);
$$;

-- ============================================================================
-- 14. RPC: toggle_nova_save
-- ============================================================================

create or replace function public.toggle_nova_save(p_post_id uuid, p_save boolean)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    me uuid := public.request_uid();
begin
    if me is null then
        raise exception 'not_authenticated';
    end if;
    if not exists (select 1 from public.nova_posts p
                   where p.id = p_post_id and p.deleted_at is null) then
        raise exception 'not_found';
    end if;

    if coalesce(p_save, false) then
        insert into public.nova_saves (user_id, post_id)
        values (me, p_post_id)
        on conflict do nothing;
    else
        delete from public.nova_saves s
        where s.user_id = me and s.post_id = p_post_id;
    end if;

    return jsonb_build_object('is_saved', coalesce(p_save, false));
end;
$$;

-- ============================================================================
-- 15. RPC: share_nova_to_instagram(post_id, type)
--
--     The only way Nova content reaches Instagram, and only when the author
--     explicitly asks for it. The new Instagram row stores the id of the Nova
--     post it came from.
-- ============================================================================

create or replace function public.share_nova_to_instagram(p_post_id uuid, p_type text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    me     uuid := public.request_uid();
    v_post public.nova_posts%rowtype;
    v_new  uuid;
begin
    if me is null then
        raise exception 'not_authenticated';
    end if;
    if p_type is null or p_type not in ('post','story','reel') then
        raise exception 'invalid_type';
    end if;

    select p.* into v_post
    from public.nova_posts p
    where p.id = p_post_id and p.deleted_at is null;

    if v_post.id is null then
        raise exception 'not_found';
    end if;
    if v_post.author_id <> me then
        raise exception 'not_owner';
    end if;
    -- A story or a reel needs media. A text-only Nova post can only be shared
    -- as an Instagram post.
    if p_type in ('story', 'reel')
       and (v_post.media_url is null or btrim(v_post.media_url) = '') then
        raise exception 'media_required';
    end if;

    if p_type = 'post' then
        insert into public.posts (user_id, caption, thumbnail_url, is_reel,
                                  space, source_nova_post_id)
        values (me, v_post.body, v_post.media_url, false, 'insta', v_post.id)
        returning id into v_new;

        if v_post.media_url is not null then
            insert into public.post_media (post_id, url, position)
            values (v_new, v_post.media_url, 0);
        end if;
    elsif p_type = 'reel' then
        insert into public.posts (user_id, caption, thumbnail_url, video_url,
                                  is_reel, space, source_nova_post_id)
        values (me, v_post.body, v_post.media_url, v_post.media_url, true,
                'insta', v_post.id)
        returning id into v_new;
    else
        insert into public.stories (user_id, media_url, media_type,
                                    expires_at, source_nova_post_id)
        values (me, v_post.media_url, 'image',
                now() + interval '24 hours', v_post.id)
        returning id into v_new;
    end if;

    return jsonb_build_object('id', v_new, 'type', p_type,
                               'source_nova_post_id', v_post.id);
end;
$$;

-- ============================================================================
-- 16. Expiry job body
--     Soft-deletes expired posts (and their comments) and queues their media
--     for removal. Runs every minute from pg_cron.
-- ============================================================================

create or replace function public.nova_expire_posts()
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
    v_count integer;
begin
    insert into public.nova_expired_media (post_id, bucket_id, object_name)
    select p.id, 'nova',
           coalesce(p.media_url, '')
      from public.nova_posts p
     where p.deleted_at is null
       and p.expires_at is not null
       and p.expires_at <= now()
       and p.media_url is not null
       and p.media_url <> ''
    on conflict do nothing;

    update public.nova_posts p
       set deleted_at = now(), updated_at = now()
     where p.deleted_at is null
       and p.expires_at is not null
       and p.expires_at <= now();

    get diagnostics v_count = row_count;

    update public.nova_comments c
       set deleted_at = now(), updated_at = now()
     where c.deleted_at is null
       and c.post_id in (select p.id from public.nova_posts p
                          where p.deleted_at is not null);

    return v_count;
end;
$$;

-- ============================================================================
-- 17. Media purge
--     Removes the queued objects from Supabase Storage. Deleting the
--     storage.objects row is what drops the object from the bucket.
-- ============================================================================

create or replace function public.nova_purge_expired_media()
returns integer
language plpgsql
security definer
set search_path = public, storage
as $$
declare
    v_count integer := 0;
    r        record;
begin
    for r in
        select m.id, m.bucket_id, m.object_name
        from public.nova_expired_media m
        where m.purged_at is null
    loop
        begin
            delete from storage.objects o
            where o.bucket_id = r.bucket_id
              and o.name = r.object_name;
            update public.nova_expired_media m
               set purged_at = now()
             where m.id = r.id;
            v_count := v_count + 1;
        exception when others then
            -- One bad object must not stop the rest of the queue.
            null;
        end;
    end loop;
    return v_count;
end;
$$;

-- ============================================================================
-- 18. Migrate legacy Nova rows out of `posts` into nova_posts
--
--     Posts written by the old Nova composer lived in `posts` with
--     space = 'nova'. They are moved over so the Nova feed keeps its content
--     while `posts` becomes Instagram-only again.
-- ============================================================================

insert into public.nova_posts (
    author_id, body, media_url, media_type, content_type,
    is_anonymous, anonymous_alias, created_at, updated_at)
select p.user_id,
       coalesce(p.caption, ''),
       coalesce(p.thumbnail_url,
                (select m.url from public.post_media m
                  where m.post_id = p.id order by m.position limit 1)),
       case when p.is_reel then 'reel' else 'photo' end,
       case when p.is_reel then 'reel' else 'photo' end,
       false,
       null,
       p.created_at,
       p.created_at
  from public.posts p
 where p.space = 'nova'
   and not exists (select 1 from public.nova_posts np where np.body = coalesce(p.caption, '')
                     and np.created_at = p.created_at)
on conflict do nothing;

-- The Nova rows have their own home now. Removing them from `posts` is what
-- stops them from surfacing on the Instagram profile grid, reels tab and
-- saved list, which read the shared table without a space filter.
delete from public.posts p
 where p.space = 'nova'
   and exists (select 1 from public.nova_posts np
                where np.created_at = p.created_at
                  and np.body = coalesce(p.caption, ''));

-- ============================================================================
-- 19. Storage bucket for Nova media
-- ============================================================================

insert into storage.buckets (id, name, public)
values ('nova', 'nova', true)
on conflict (id) do nothing;

drop policy if exists nova_media_insert on storage.objects;
create policy nova_media_insert on storage.objects
    for insert to authenticated
    with check (bucket_id = 'nova');

drop policy if exists nova_media_select on storage.objects;
create policy nova_media_select on storage.objects
    for select to anon, authenticated
    using (bucket_id = 'nova');

drop policy if exists nova_media_update on storage.objects;
create policy nova_media_update on storage.objects
    for update to authenticated
    using (bucket_id = 'nova')
    with check (bucket_id = 'nova');

drop policy if exists nova_media_delete on storage.objects;
create policy nova_media_delete on storage.objects
    for delete to authenticated
    using (bucket_id = 'nova');

-- ============================================================================
-- 20. RPC grants
-- ============================================================================

do $$
declare
    r text;
begin
    foreach r in array array[
        'create_nova_post',
        'create_nova_comment',
        'nova_cast_vote',
        'cast_vote',
        'cast_comment_vote',
        'toggle_nova_save',
        'share_nova_to_instagram',
        'nova_expire_posts',
        'nova_purge_expired_media'
    ] loop
        execute format('revoke all on function public.%s from public', r);
        execute format('grant execute on function public.%s to authenticated', r);
    end loop;
end $$;

commit;
