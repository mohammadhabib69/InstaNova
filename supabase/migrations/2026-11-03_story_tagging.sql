-- ============================================================================
-- Story tagging.
--
-- Post tagging landed in 2026-11-03_post_tagging.sql, but a story is a separate
-- row in public.stories and post_tags.post_id cannot point at one, so tagging a
-- story was still impossible. Same rules as posts: the story's author chooses the
-- people, and the tagged account's tag_who decides whether it is allowed.
-- ============================================================================

create table if not exists public.story_tags (
    story_id uuid not null references public.stories(id) on delete cascade,
    user_id  uuid not null references public.profiles(id) on delete cascade,
    x        double precision,
    y        double precision,
    created_at timestamptz not null default now(),
    primary key (story_id, user_id)
);

alter table public.story_tags
    drop constraint if exists story_tags_xy_range;
alter table public.story_tags
    add constraint story_tags_xy_range check (
        (x is null or (x >= 0 and x <= 1)) and
        (y is null or (y >= 0 and y <= 1))
    );

create index if not exists story_tags_user_idx on public.story_tags (user_id);

alter table public.story_tags enable row level security;

drop policy if exists story_tags_select on public.story_tags;
create policy story_tags_select on public.story_tags
    for select using (true);

drop policy if exists story_tags_insert on public.story_tags;
create policy story_tags_insert on public.story_tags
    for insert with check (
        exists (
            select 1 from public.stories s
             where s.id = story_tags.story_id
               and s.user_id = public.request_uid()
        )
    );

drop policy if exists story_tags_delete on public.story_tags;
create policy story_tags_delete on public.story_tags
    for delete using (
        exists (
            select 1 from public.stories s
             where s.id = story_tags.story_id
               and s.user_id = public.request_uid()
        )
    );

/* ---------------------------------------------------------------------------
   Same tag_who gate as posts, kept as its own function so the two can diverge
   (a story tag can carry a position, a post tag may later need approval).
   --------------------------------------------------------------------------- */

create or replace function public.tg_enforce_story_tag_who()
returns trigger
language plpgsql
security definer
set search_path to 'public'
as $$
declare
    v_permission text;
begin
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

drop trigger if exists trg_enforce_story_tag_who on public.story_tags;
create trigger trg_enforce_story_tag_who
    before insert on public.story_tags
    for each row execute function public.tg_enforce_story_tag_who();

/* ---------------------------------------------------------------------------
   Replace the tag set on one of my stories
   --------------------------------------------------------------------------- */

create or replace function public.set_story_tags(p_story uuid, p_users jsonb)
returns void
language plpgsql
security definer
set search_path to 'public'
as $$
declare
    v_user uuid;
begin
    if not exists (
        select 1 from public.stories s
         where s.id = p_story and s.user_id = public.request_uid()
    ) then
        raise exception 'only the author can tag people on this story'
            using errcode = '42501';
    end if;

    delete from public.story_tags where story_id = p_story;

    for v_user in
        select (value #>> '{}')::uuid from jsonb_array_elements(coalesce(p_users, '[]'::jsonb))
    loop
        insert into public.story_tags (story_id, user_id) values (p_story, v_user);
    end loop;
end;
$$;

grant execute on function public.set_story_tags(uuid, jsonb) to authenticated;

/* ============================================================================
   Verification
   ----------------------------------------------------------------------------
   1. select to_regclass('public.story_tags');                      -- not null
   2. select count(*) from pg_trigger where tgname='trg_enforce_story_tag_who';  -- 1
   3. select public.set_story_tags('<my story>', '["<other user>"]');
      select count(*) from public.story_tags where story_id='<my story>';        -- 1
   4. tagged account with tag_who='nobody' -> 42501
   ============================================================================ */
