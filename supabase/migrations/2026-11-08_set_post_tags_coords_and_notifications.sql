-- ============================================================================
-- 2026-11-08  set_post_tags: anonymous posts, tag coordinates, notifications
--
-- Three things were wrong with the old body:
--   1. It checked posts.user_id = request_uid(), so tagging a post that was
--      published anonymously always raised 42501 — the author was never the row
--      owner on that path. public.post_is_mine() already resolves the
--      posts_anon_owners row and is the correct test.
--   2. post_tags.x / post_tags.y were never written, so a tag had no place on
--      the photo. The caller may now pass a parallel array of {x,y} objects in
--      the same 0..1 space the layout uses.
--   3. Nobody was told they had been tagged. Each newly tagged account gets one
--      notification row (type 'tag'), skipped for the author and deduplicated
--      per (post, user).
--
-- The arity changes, so the old function is dropped first: two overloads with a
-- defaulted trailing argument are ambiguous over PostgREST.
--
-- Rollback: 2026-11-08_set_post_tags_coords_and_notifications.rollback.sql
-- ============================================================================

begin;

set local lock_timeout = '10s';

drop function if exists public.set_post_tags(uuid, jsonb);

create or replace function public.set_post_tags(
    p_post   uuid,
    p_users  jsonb,
    p_coords jsonb default null
)
returns void
language plpgsql
security definer
set search_path = public
as $function$
declare
    me       uuid := public.request_uid();
    v_user   uuid;
    v_index  integer := 0;
    v_x      double precision;
    v_y      double precision;
    v_total  integer;
begin
    if me is null then
        raise exception 'not_authenticated';
    end if;

    if not public.post_is_mine(p_post) then
        raise exception 'only the author can tag people on this post'
            using errcode = '42501';
    end if;

    v_total := coalesce(jsonb_array_length(p_users), 0);

    delete from public.post_tags where post_id = p_post;

    for v_user in
        select (value #>> '{}')::uuid from jsonb_array_elements(coalesce(p_users, '[]'::jsonb))
    loop
        v_x := null;
        v_y := null;
        -- Coordinates are optional and positional: coords[i] belongs to users[i].
        if p_coords is not null
           and jsonb_typeof(p_coords) = 'array'
           and jsonb_array_length(p_coords) = v_total then
            begin
                v_x := (p_coords -> v_index ->> 'x')::double precision;
                v_y := (p_coords -> v_index ->> 'y')::double precision;
            exception when others then
                v_x := null;
                v_y := null;
            end;
            -- post_tags_xy_range only accepts 0..1, so a caller that measures in
            -- pixels cannot break the write.
            if v_x is not null then v_x := least(1.0, greatest(0.0, v_x)); end if;
            if v_y is not null then v_y := least(1.0, greatest(0.0, v_y)); end if;
        end if;

        insert into public.post_tags (post_id, user_id, x, y)
        values (p_post, v_user, v_x, v_y)
        on conflict (post_id, user_id) do update
            set x = excluded.x, y = excluded.y;

        if v_user <> me then
            insert into public.notifications (user_id, actor_id, type, post_id, body)
            select v_user, me, 'tag', p_post, 'tagged you in a post'
            where not exists (
                select 1 from public.notifications n
                 where n.user_id = v_user
                   and n.type = 'tag'
                   and n.post_id = p_post);
        end if;

        v_index := v_index + 1;
    end loop;
end;
$function$;

revoke all on function public.set_post_tags(uuid, jsonb, jsonb) from public;
grant execute on function public.set_post_tags(uuid, jsonb, jsonb) to authenticated, service_role;

commit;

notify pgrst, 'reload schema';
