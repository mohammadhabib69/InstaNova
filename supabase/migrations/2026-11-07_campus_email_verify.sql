-- ============================================================================
-- Campus verification by student email.
--
-- RULE (given by the product owner): a university mailbox looks like
--   someone@stud.<university domain>      e.g. @stud.kuet.ac.bd
-- The "stud" label after the @ marks it as a student address, so an email whose domain is
-- exactly 'stud.' || one of the university's email_domains verifies the member.
--
-- Before this the only path to verified = true was nova_campus_join, which reads the
-- email claim from the session. An account registered with a personal mailbox could never
-- verify, and there was no screen offering another way.
-- ============================================================================

alter table public.nova_campus_members
    add column if not exists verified_email text;

create or replace function public.nova_campus_verify_email(p_email text)
returns jsonb
language plpgsql
security definer
set search_path to 'public'
as $$
declare
    v_uid     uuid := public.request_uid();
    v_uni     bigint;
    v_domain  text;
    v_ok      boolean;
begin
    if v_uid is null then
        raise exception 'not_authenticated' using errcode = '28000';
    end if;

    select university_id into v_uni
      from public.nova_campus_members
     where user_id = v_uid;

    if v_uni is null then
        raise exception 'not_in_campus' using errcode = '22023';
    end if;

    v_domain := lower(split_part(trim(coalesce(p_email, '')), '@', 2));
    if v_domain = '' or position('.' in v_domain) = 0 then
        return jsonb_build_object('verified', false, 'reason', 'invalid_email');
    end if;

    select exists (
        select 1
          from public.nova_universities u,
               lateral unnest(u.email_domains) dom
         where u.id = v_uni
           and v_domain = 'stud.' || lower(dom)
    ) into v_ok;

    if not v_ok then
        return jsonb_build_object(
            'verified', false,
            'reason',   'domain',
            'domain',   v_domain,
            'expected', (select jsonb_agg('stud.' || lower(dom))
                           from public.nova_universities u,
                                lateral unnest(u.email_domains) dom
                          where u.id = v_uni));
    end if;

    update public.nova_campus_members
       set verified = true,
           verified_email = lower(trim(p_email))
     where user_id = v_uid;

    return jsonb_build_object('verified', true, 'domain', v_domain);
end;
$$;

revoke all on function public.nova_campus_verify_email(text) from public, anon;
grant execute on function public.nova_campus_verify_email(text) to authenticated;
