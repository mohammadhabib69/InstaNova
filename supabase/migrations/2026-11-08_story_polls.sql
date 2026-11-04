-- 2026-11-08 — story poll votes.
create table if not exists public.story_poll_votes (
    story_id     uuid not null references public.stories(id) on delete cascade,
    user_id      uuid not null,
    option_index integer not null check (option_index between 0 and 3),
    created_at   timestamptz not null default now(),
    primary key (story_id, user_id)
);

alter table public.story_poll_votes enable row level security;

drop policy if exists story_poll_votes_insert on public.story_poll_votes;
create policy story_poll_votes_insert on public.story_poll_votes for insert to authenticated
    with check (user_id = public.request_uid());

drop policy if exists story_poll_votes_select on public.story_poll_votes;
create policy story_poll_votes_select on public.story_poll_votes for select to authenticated
    using (user_id = public.request_uid()
           or exists (select 1 from public.stories s
                       where s.id = story_id and s.user_id = public.request_uid()));

-- One vote per user per story: a second call replaces the first.
create or replace function public.story_poll_vote(p_story uuid, p_option integer)
returns void
language plpgsql
security definer
set search_path to 'public', 'pg_temp'
as $$
begin
    insert into public.story_poll_votes (story_id, user_id, option_index)
    values (p_story, public.request_uid(), p_option)
    on conflict (story_id, user_id) do update set option_index = excluded.option_index;
end;
$$;

create or replace function public.story_poll_results(p_story uuid)
returns table (option_index integer, votes bigint)
language sql
stable
security definer
set search_path to 'public', 'pg_temp'
as $$
    select v.option_index, count(*)::bigint
      from public.story_poll_votes v
     where v.story_id = p_story
     group by v.option_index;
$$;

create or replace function public.story_poll_mine(p_story uuid)
returns integer
language sql
stable
security definer
set search_path to 'public', 'pg_temp'
as $$
    select v.option_index from public.story_poll_votes v
     where v.story_id = p_story and v.user_id = public.request_uid();
$$;

revoke all on function public.story_poll_vote(uuid, integer) from public;
revoke all on function public.story_poll_results(uuid) from public;
revoke all on function public.story_poll_mine(uuid) from public;
grant execute on function public.story_poll_vote(uuid, integer) to authenticated;
grant execute on function public.story_poll_results(uuid) to authenticated;
grant execute on function public.story_poll_mine(uuid) to authenticated;
