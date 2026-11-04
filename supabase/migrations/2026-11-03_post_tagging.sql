-- ============================================================================
-- Post tagging — makes Settings > Tags and mentions mean something.
--
-- WHAT WAS FOUND
--   public.post_tags existed but nothing ever inserted into it. The only reader
--   was ProfileFragment's "Tagged" tab, which therefore always showed the empty
--   state. CreatePostActivity even built a "Tag people" row and then hid it as
--   design-only. So tag_who / mention_who controlled a feature that did not exist:
--   the settings wrote a preference and nothing in the app could ever tag anybody.
--
-- WHAT THIS ADDS
--   1. Position columns so a tag can sit on the photo, Instagram style.
--   2. RLS: anyone can read the tags on a post; only the post's author can create
--      or remove them.
--   3. A trigger enforcing the tagged account's tag_who:
--        everyone  -> any tag accepted
--        following -> only if the poster follows the tagged account
--        nobody    -> rejected
--   The check lives in the database so a modified client cannot tag somebody who
--   has opted out.
-- ============================================================================

alter table public.post_tags
    add column if not exists x double precision,
    add column if not exists y double precision;

alter table public.post_tags
    drop constraint if exists post_tags_xy_range;
alter table public.post_tags
    add constraint post_tags_xy_range check (
        (x is null or (x >= 0 and x <= 1)) and
        (y is null or (y >= 0 and y <= 1))
    );

create unique index if not exists post_tags_unique
    on public.post_tags (post_id, user_id);
create index if not exists post_tags_user_idx
    on public.post_tags (user_id);

alter table public.post_tags enable row level security;

drop policy if exists post_tags_select on public.post_tags;
create policy post_tags_select on public.post_tags
    for select using (true);

drop policy if exists post_tags_insert on public.post_tags;
create policy post_tags_insert on public.post_tags
    for insert with check (
        exists (
            select 1 from public.posts p
             where p.id = post_tags.post_id
               and p.user_id = public.request_uid()
        )
    );

drop policy if exists post_tags_delete on public.post_tags;
create policy post_tags_delete on public.post_tags
    for delete using (
        exists (
            select 1 from public.posts p
             where p.id = post_tags.post_id
               and p.user_id = public.request_uid()
        )
    );

/* ---------------------------------------------------------------------------
   Enforce tag_who at insert time
   --------------------------------------------------------------------------- */

create or replace function public.tg_enforce_tag_who()
returns trigger
language plpgsql
security definer
set search_path to 'public'
as $$
declare
    v_permission text;
begin
    -- Tagging yourself is always allowed and is how "add yourself" works.
    if new.user_id = public.request_uid() then
        return new;
    end if;

    select coalesce(p.tag_who, 'everyone') into v_permission
      from public.profiles p
     where p.id = new.user_id;

    if v_permission = 'nobody' then
        raise exception 'this account does not accept tags'
            using errcode = '42501';
    end if;

    if v_permission = 'following' and not exists (
        select 1 from public.follows f
         where f.follower_id = public.request_uid()
           and f.following_id = new.user_id
    ) then
        raise exception 'this account only accepts tags from people they follow'
            using errcode = '42501';
    end if;

    return new;
end;
$$;

drop trigger if exists trg_enforce_tag_who on public.post_tags;
create trigger trg_enforce_tag_who
    before insert on public.post_tags
    for each row execute function public.tg_enforce_tag_who();

/* ---------------------------------------------------------------------------
   Convenience RPC: replace the tag set for one of my posts
   --------------------------------------------------------------------------- */

create or replace function public.set_post_tags(p_post uuid, p_users jsonb)
returns void
language plpgsql
security definer
set search_path to 'public'
as $$
declare
    v_user uuid;
begin
    if not exists (
        select 1 from public.posts p
         where p.id = p_post and p.user_id = public.request_uid()
    ) then
        raise exception 'only the author can tag people on this post'
            using errcode = '42501';
    end if;

    delete from public.post_tags where post_id = p_post;

    for v_user in
        select (value #>> '{}')::uuid from jsonb_array_elements(coalesce(p_users, '[]'::jsonb))
    loop
        insert into public.post_tags (post_id, user_id) values (p_post, v_user);
    end loop;
end;
$$;

grant execute on function public.set_post_tags(uuid, jsonb) to authenticated;

/* ============================================================================
   Verification
   ----------------------------------------------------------------------------
   1. Columns:
      select column_name from information_schema.columns
       where table_schema='public' and table_name='post_tags';

   2. A tag lands:
      select public.set_post_tags('<post owned by me>', '["<other user>"]');
      select * from public.post_tags where post_id = '<post>';        -- 1 row

   3. tag_who = 'nobody' blocks it:
      update public.profiles set tag_who='nobody' where id='<other user>';
      select public.set_post_tags('<post>', '["<other user>"]');      -- 42501

   4. Someone else's post is refused:
      select public.set_post_tags('<post owned by someone else>', '["<x>"]');  -- 42501
   ============================================================================ */
