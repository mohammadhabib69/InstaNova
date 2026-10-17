-- =====================================================================
-- Firebase Authentication -> Supabase identity bridge
-- =====================================================================
-- Firebase UIDs are 28-character strings, not UUIDs, and Supabase's
-- auth.uid() casts the JWT "sub" claim to uuid. Every RLS policy that used
-- auth.uid() therefore errors for a Firebase-authenticated request.
--
-- This migration:
--   1. adds profiles.firebase_uid (nullable, unique) so a Firebase identity
--      maps to an existing uuid primary key - no id is rewritten, no row is
--      deleted, all foreign keys stay valid;
--   2. adds public.request_uid(), which resolves the caller to a profile id
--      from either a Supabase JWT (uuid sub, unchanged behaviour) or a
--      Firebase JWT (sub -> profiles.firebase_uid);
--   3. rewrites only the policies that referenced auth.uid(), preserving
--      their name, command, permissive/restrictive flag and roles.
--
-- Run order matters: apply BEFORE enabling Third-Party Auth -> Firebase.
-- =====================================================================

begin;

-- 1. Identity column ---------------------------------------------------
alter table public.profiles add column if not exists firebase_uid text;

create unique index if not exists profiles_firebase_uid_key
    on public.profiles (firebase_uid)
    where firebase_uid is not null;

comment on column public.profiles.firebase_uid is
    'FirebaseAuth uid for accounts created after the Firebase migration. NULL for legacy Supabase-Auth rows.';

-- 2. Resolver ----------------------------------------------------------
create or replace function public.request_uid()
returns uuid
language sql
stable
security definer
set search_path = public, pg_temp
as $$
    select coalesce(
        (select p.id
           from public.profiles p
          where p.firebase_uid is not null
            and p.firebase_uid = (auth.jwt() ->> 'sub')
          limit 1),
        case
            when (auth.jwt() ->> 'sub')
                 ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
            then (auth.jwt() ->> 'sub')::uuid
        end
    );
$$;

revoke all on function public.request_uid() from public;
grant execute on function public.request_uid() to anon, authenticated;

comment on function public.request_uid() is
    'Current caller as a profiles.id. Supabase JWT: uuid sub. Firebase JWT: sub resolved through profiles.firebase_uid.';

-- 3. Policy rewrite ----------------------------------------------------
do $$
declare
    r        record;
    new_qual text;
    new_check text;
    perm     text;
    roles    text;
begin
    for r in
        select * from pg_policies
         where schemaname in ('public', 'storage')
           and (coalesce(qual, '') like '%auth.uid()%'
                or coalesce(with_check, '') like '%auth.uid()%')
    loop
        new_qual  := r.qual;
        new_check := r.with_check;

        if r.schemaname = 'storage' then
            -- folder name must match either the profile id or the Firebase uid
            new_qual := replace(new_qual,
                '= (auth.uid())::text',
                '= any (array[(public.request_uid())::text, (auth.jwt() ->> ''sub'')])');
            new_check := replace(new_check,
                '= (auth.uid())::text',
                '= any (array[(public.request_uid())::text, (auth.jwt() ->> ''sub'')])');
        else
            new_qual  := replace(new_qual, 'auth.uid()', 'public.request_uid()');
            new_check := replace(new_check, 'auth.uid()', 'public.request_uid()');
        end if;

        perm  := case when r.permissive = 'RESTRICTIVE' then 'restrictive' else 'permissive' end;
        roles := array_to_string(r.roles, ', ');

        execute format('drop policy %I on %I.%I', r.policyname, r.schemaname, r.tablename);

        if r.cmd = 'ALL' then
            execute format(
                'create policy %I on %I.%I as %s for all to %s using (%s) with check (%s)',
                r.policyname, r.schemaname, r.tablename, perm, roles, new_qual, new_check);
        elsif r.cmd = 'INSERT' then
            execute format(
                'create policy %I on %I.%I as %s for insert to %s with check (%s)',
                r.policyname, r.schemaname, r.tablename, perm, roles, new_check);
        elsif r.cmd = 'UPDATE' then
            execute format(
                'create policy %I on %I.%I as %s for update to %s using (%s)%s',
                r.policyname, r.schemaname, r.tablename, perm, roles, new_qual,
                case when new_check is null then ''
                     else format(' with check (%s)', new_check) end);
        else
            execute format(
                'create policy %I on %I.%I as %s for %s to %s using (%s)',
                r.policyname, r.schemaname, r.tablename, perm,
                lower(r.cmd), roles, new_qual);
        end if;
    end loop;
end
$$;

-- 4. Bootstrap: the very first write has no profile row yet, so
--    request_uid() cannot resolve it. Allow the insert when the row's own
--    firebase_uid equals the caller's Firebase sub claim.
drop policy if exists profiles_insert_own on public.profiles;
create policy profiles_insert_own on public.profiles
    for insert to authenticated
    with check (
        id = public.request_uid()
        or firebase_uid = (auth.jwt() ->> 'sub')
    );

commit;
