-- Rollback for 2026-10-23_nova_study_leaderboard_fix.sql
--
-- Restores the exact function body that was live before the patch. Lifted verbatim from
-- `supabase db dump --schema public` taken on 2026-10-23, so no reconstruction is involved.
-- Apply in Dashboard -> SQL Editor, or with psql, if the all-time scope misbehaves.

CREATE OR REPLACE FUNCTION "public"."nova_study_leaderboard"("p_scope" "text" DEFAULT 'campus'::"text", "p_limit" integer DEFAULT 10) RETURNS TABLE("user_id" "uuid", "username" "text", "avatar_url" "text", "minutes" integer, "rank" integer, "is_me" boolean)
    LANGUAGE "plpgsql" STABLE SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
#variable_conflict use_column
declare me uuid := public.request_uid(); uni smallint;
begin
  if me is null then raise exception 'not_authenticated'; end if;
  select university_id into uni from public.nova_campus_members m where m.user_id = me;
  return query
  with pool as (
    select m.user_id as uid from public.nova_campus_members m where p_scope = 'campus' and uni is not null and m.university_id = uni
    union
    select f.following_id from public.follows f where p_scope = 'following' and f.follower_id = me
    union
    select me where p_scope = 'following'
  ), w as (
    select pl.uid, coalesce(sum(s.minutes), 0)::int as mins
    from pool pl
    left join public.nova_study_sessions s on s.user_id = pl.uid and s.completed and s.started_at > now() - interval '7 days'
    group by pl.uid
  )
  select w.uid, p.username, p.avatar_url, w.mins, (rank() over (order by w.mins desc))::int, (w.uid = me)
  from w join public.profiles p on p.id = w.uid
  where w.mins > 0 or w.uid = me
  order by w.mins desc, p.username
  limit least(greatest(p_limit, 1), 50);
end $$;
