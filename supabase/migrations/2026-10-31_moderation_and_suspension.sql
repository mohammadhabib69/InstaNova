-- Replaces the two moderation pieces that were waiting on a Blaze-billed Cloud Function.
-- Both are plain Postgres: rules cannot aggregate, SQL can; and a suspension column removes the
-- need for a scheduled job. RLS stays enabled throughout — nothing here disables a policy.

-- 1. Suspension flag ---------------------------------------------------------
alter table public.profiles add column if not exists suspended_at timestamptz;

comment on column public.profiles.suspended_at is
    'Non-null while the account is suspended; writes from this user are refused by trigger.';

create or replace function public.is_suspended(p_user uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1 from public.profiles p
        where p.id = p_user and p.suspended_at is not null
    );
$$;

-- One central gate for every write that represents "posting as this account".
create or replace function public.reject_if_suspended()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
    v_user uuid;
begin
    v_user := coalesce(
        (to_jsonb(new) ->> 'user_id')::uuid,
        (to_jsonb(new) ->> 'sender_id')::uuid,
        (to_jsonb(new) ->> 'reporter_id')::uuid
    );
    if v_user is not null and public.is_suspended(v_user) then
        raise exception 'account suspended' using errcode = '42501';
    end if;
    return new;
end;
$$;

do $$
declare
    t text;
begin
    foreach t in array array['posts', 'comments', 'messages', 'stories', 'nova_reports'] loop
        if exists (select 1 from information_schema.tables
                   where table_schema = 'public' and table_name = t) then
            execute format('drop trigger if exists trg_reject_suspended on public.%I', t);
            execute format(
                'create trigger trg_reject_suspended before insert on public.%I
                 for each row execute function public.reject_if_suspended()', t);
        end if;
    end loop;
end;
$$;

-- 2. Report aggregation ------------------------------------------------------
-- A report is cheap to write and expensive to count on demand, and RLS cannot aggregate.
-- Keeping the counters in a trigger-maintained table makes a moderation queue a plain select.
create table if not exists public.report_stats (
    target_type    text        not null,
    target_id      uuid        not null,
    open_count     int         not null default 0,
    total_count    int         not null default 0,
    last_report_at timestamptz,
    primary key (target_type, target_id)
);

alter table public.report_stats enable row level security;

drop policy if exists report_stats_read on public.report_stats;
create policy report_stats_read on public.report_stats
    for select using (auth.role() = 'authenticated');

-- Only the trigger writes here, so there is deliberately no insert/update/delete policy:
-- without one, RLS refuses those writes for every client role.

create or replace function public.refresh_report_stats(p_type text, p_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    if p_type is null or p_id is null then
        return;
    end if;

    insert into public.report_stats (target_type, target_id, open_count, total_count, last_report_at)
    select p_type, p_id,
           count(*) filter (where coalesce(r.status, 'open') = 'open'),
           count(*),
           max(r.created_at)
    from public.nova_reports r
    where r.target_type = p_type and r.target_id = p_id
    on conflict (target_type, target_id) do update
        set open_count     = excluded.open_count,
            total_count    = excluded.total_count,
            last_report_at = excluded.last_report_at;

    delete from public.report_stats s
    where s.target_type = p_type and s.target_id = p_id and s.total_count = 0;
end;
$$;

create or replace function public.trg_report_stats()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    if tg_op in ('UPDATE', 'DELETE') then
        perform public.refresh_report_stats(old.target_type, old.target_id);
    end if;
    if tg_op in ('INSERT', 'UPDATE') then
        perform public.refresh_report_stats(new.target_type, new.target_id);
    end if;
    return null;
end;
$$;

drop trigger if exists trg_report_stats on public.nova_reports;
create trigger trg_report_stats
    after insert or update or delete on public.nova_reports
    for each row execute function public.trg_report_stats();

-- Backfill whatever is already in the table.
do $$
declare
    r record;
begin
    for r in select distinct target_type, target_id from public.nova_reports loop
        perform public.refresh_report_stats(r.target_type, r.target_id);
    end loop;
end;
$$;

-- 3. Moderation queue --------------------------------------------------------
create or replace view public.v_moderation_queue as
select s.target_type,
       s.target_id,
       s.open_count,
       s.total_count,
       s.last_report_at
from public.report_stats s
where s.open_count > 0
order by s.open_count desc, s.last_report_at desc;

comment on view public.v_moderation_queue is
    'Targets with at least one open report, most-reported first.';
