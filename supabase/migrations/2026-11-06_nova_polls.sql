-- ============================================================================
-- Nova polls — voting.
--
-- WHAT WAS FOUND
--   The composer already wrote polls: create_nova_post stores p_poll_options and the
--   public view exposes poll_options / poll_votes. Verified by inserting a poll post in
--   a rolled-back transaction: both options were stored and came back through
--   nova_posts_public. What did not exist was any way to vote, and any table holding a
--   vote — nova_cast_vote accepts only 'post' and 'comment'. poll_votes could therefore
--   only ever be null or a hand-written array.
--
-- WHAT THIS ADDS
--   1. nova_poll_votes — one row per voter per post, so a vote can change without
--      double counting.
--   2. nova_vote_poll(post, option) — validates the option against poll_options,
--      upserts the choice, recomputes the counts array into nova_posts.poll_votes and
--      returns { counts, my_vote, total }.
--   3. RLS: a voter may read only their own row; only the RPC writes.
-- ============================================================================

create table if not exists public.nova_poll_votes (
    post_id      uuid not null references public.nova_posts(id) on delete cascade,
    user_id      uuid not null references public.profiles(id) on delete cascade,
    option_index integer not null check (option_index >= 0 and option_index <= 9),
    created_at   timestamptz not null default now(),
    primary key (post_id, user_id)
);

create index if not exists nova_poll_votes_post_idx on public.nova_poll_votes(post_id);

alter table public.nova_poll_votes enable row level security;

drop policy if exists nova_poll_votes_select on public.nova_poll_votes;
create policy nova_poll_votes_select on public.nova_poll_votes
    for select to authenticated using (user_id = public.request_uid());

revoke truncate, references, trigger on public.nova_poll_votes from anon, authenticated;

create or replace function public.nova_vote_poll(p_post uuid, p_option integer)
returns jsonb
language plpgsql
security definer
set search_path to 'public'
as $$
declare
    me      uuid := public.request_uid();
    opts    jsonb;
    v_count integer;
    counts  jsonb;
    mine    integer;
begin
    if me is null then raise exception 'not_authenticated'; end if;

    select poll_options into opts
      from public.nova_posts
     where id = p_post and deleted_at is null
     for update;
    if not found then raise exception 'unknown_post'; end if;
    if opts is null or jsonb_typeof(opts) <> 'array' then raise exception 'not_a_poll'; end if;

    v_count := jsonb_array_length(opts);
    if p_option is null or p_option < 0 or p_option >= v_count then
        raise exception 'invalid_option';
    end if;

    insert into public.nova_poll_votes(post_id, user_id, option_index)
    values (p_post, me, p_option)
    on conflict (post_id, user_id) do update set option_index = excluded.option_index;

    -- Rebuild the whole array instead of incrementing, so a changed vote cannot leave
    -- the total inflation that a naive +1/-1 pair would.
    select jsonb_agg(coalesce((
               select count(*)::int
                 from public.nova_poll_votes v
                where v.post_id = p_post and v.option_index = idx
           ), 0) order by idx)
      into counts
      from generate_series(0, v_count - 1) as idx;

    update public.nova_posts set poll_votes = counts, updated_at = now() where id = p_post;

    select option_index into mine from public.nova_poll_votes
     where post_id = p_post and user_id = me;

    return jsonb_build_object('counts', counts, 'my_vote', mine,
                              'total', (select coalesce(sum(value::int), 0)
                                          from jsonb_array_elements_text(counts) value));
end $$;

grant execute on function public.nova_vote_poll(uuid, integer) to authenticated;

/* ============================================================================
   Verification
   ----------------------------------------------------------------------------
   select count(*) from public.nova_poll_votes;      -- table exists
   as an authenticated user, nova_vote_poll(<poll post>, 0) returns counts [1,0]
   voting again for option 1 returns counts [0,1] and my_vote 1
   ============================================================================ */
