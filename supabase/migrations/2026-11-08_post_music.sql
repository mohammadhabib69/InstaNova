-- ============================================================================
-- 2026-11-08  Post music
--
-- "Add music" was a decorative row. There is no licensed catalogue in the app,
-- so the track is whatever audio file the author picks off the device: it is
-- uploaded to the posts bucket and the row keeps the URL plus the title shown
-- on the post.
--
-- The arity of create_instagram_post changes again, so the eleven-argument
-- version is dropped first (two overloads with defaulted trailing arguments are
-- ambiguous over PostgREST).
--
-- Rollback: 2026-11-08_post_music.rollback.sql
-- ============================================================================

begin;

set local lock_timeout = '10s';

alter table public.posts
    add column if not exists music_url   text,
    add column if not exists music_title text;

drop function if exists public.create_instagram_post(
    text, jsonb, boolean, text, text, boolean, timestamptz, integer,
    boolean, boolean, text);

create or replace function public.create_instagram_post(
    p_caption          text        default '',
    p_media_urls       jsonb       default null,
    p_is_reel          boolean     default false,
    p_video_url        text        default null,
    p_thumbnail_url    text        default null,
    p_is_anonymous     boolean     default false,
    p_expires_at       timestamptz default null,
    p_duration_seconds integer     default null,
    p_hide_like_count  boolean     default false,
    p_comments_disabled boolean    default false,
    p_location         text        default null,
    p_music_url        text        default null,
    p_music_title      text        default null
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
    v_url   text;
    v_pos   integer := 0;
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
                              expires_at, duration_seconds,
                              hide_like_count, comments_disabled, location,
                              music_url, music_title)
    values (v_owner, coalesce(p_caption, ''), p_thumbnail_url, p_video_url,
            coalesce(p_is_reel, false), 'insta', coalesce(p_is_anonymous, false),
            v_alias, p_expires_at, p_duration_seconds,
            coalesce(p_hide_like_count, false),
            coalesce(p_comments_disabled, false),
            nullif(btrim(coalesce(p_location, '')), ''),
            nullif(btrim(coalesce(p_music_url, '')), ''),
            nullif(btrim(coalesce(p_music_title, '')), ''))
    returning id into v_id;

    if p_media_urls is not null and jsonb_typeof(p_media_urls) = 'array' then
        for v_url in
            select jsonb_array_elements_text(p_media_urls)
        loop
            if v_url is null or btrim(v_url) = '' then
                continue;
            end if;
            insert into public.post_media (post_id, url, position)
            values (v_id, v_url, v_pos);
            v_pos := v_pos + 1;
        end loop;
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
        'expires_at', p_expires_at,
        'hide_like_count', coalesce(p_hide_like_count, false),
        'comments_disabled', coalesce(p_comments_disabled, false),
        'location', nullif(btrim(coalesce(p_location, '')), ''),
        'music_url', nullif(btrim(coalesce(p_music_url, '')), ''),
        'music_title', nullif(btrim(coalesce(p_music_title, '')), ''));
end;
$$;

revoke all on function public.create_instagram_post(
    text, jsonb, boolean, text, text, boolean, timestamptz, integer,
    boolean, boolean, text, text, text) from public;
grant execute on function public.create_instagram_post(
    text, jsonb, boolean, text, text, boolean, timestamptz, integer,
    boolean, boolean, text, text, text) to authenticated, service_role;

commit;

notify pgrst, 'reload schema';
