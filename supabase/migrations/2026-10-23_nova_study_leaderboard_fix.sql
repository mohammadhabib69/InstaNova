-- nova_study_leaderboard — add a real all-time scope
--
-- WHY
-- ---
-- The deployed function takes an *audience* in p_scope ('campus' | 'following') and
-- hardcodes a seven-day window:
--
--   s.started_at > now() - interval '7 days'
--
-- The app used to send 'week'/'all', which matched neither branch, so the pool was empty
-- and the RPC answered 200 with []. The client now sends 'following' (fixed in
-- StudyRepository), which works. This patch adds a genuine all-time option so the
-- "All time" tab can be restored instead of being hidden.
--
-- WHAT CHANGES
-- ------------
-- p_scope gains 'week' and 'all'. Both rank the people you follow plus you; 'week' keeps
-- the seven-day window, 'all' removes it. 'campus' and 'following' behave exactly as
-- before, and the default stays 'campus', so no existing caller is affected.
--
-- Return shape, `request_uid()` auth, the completed-only filter, the ">0 minutes or me"
-- visibility rule and the 1..50 limit clamp are all preserved from the live definition.
--
-- ROLLBACK: see 2026-10-23_nova_study_leaderboard_fix.rollback.sql, which reproduces the
-- current deployed body verbatim.

begin;

create or replace function public.nova_study_leaderboard(
    p_scope text default 'campus',
    p_limit integer default 10
)
returns table(
    user_id uuid,
    username text,
    avatar_url text,
    minutes integer,
    rank integer,
    is_me boolean
)
language plpgsql
stable
security definer
set search_path to 'public'
as $$
#variable_conflict use_column
declare
  me uuid := public.request_uid();
  uni smallint;
  since timestamptz;
begin
  if me is null then
    raise exception 'not_authenticated';
  end if;

  select university_id into uni
  from public.nova_campus_members m
  where m.user_id = me;

  -- 'week' and 'all' rank the same audience as 'following'; only the window differs.
  if p_scope = 'all' then
    since := 'epoch'::timestamptz;
  else
    since := now() - interval '7 days';
  end if;

  return query
  with pool as (
    select m.user_id as uid
    from public.nova_campus_members m
    where p_scope = 'campus' and uni is not null and m.university_id = uni
    union
    select f.following_id
    from public.follows f
    where p_scope in ('following', 'week', 'all') and f.follower_id = me
    union
    select me where p_scope in ('following', 'week', 'all')
  ), w as (
    select pl.uid, coalesce(sum(s.minutes), 0)::int as mins
    from pool pl
    left join public.nova_study_sessions s
      on s.user_id = pl.uid
     and s.completed
     and s.started_at > since
    group by pl.uid
  )
  select w.uid, p.username, p.avatar_url, w.mins,
         (rank() over (order by w.mins desc))::int,
         (w.uid = me)
  from w
  join public.profiles p on p.id = w.uid
  where w.mins > 0 or w.uid = me
  order by w.mins desc, p.username
  limit least(greatest(p_limit, 1), 50);
end;
$$;

commit;
