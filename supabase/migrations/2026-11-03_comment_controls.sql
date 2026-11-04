-- ============================================================================
-- Comment controls — "Who can comment on your posts".
--
-- WHAT WAS FOUND
--   profiles.comment_who existed and Settings wrote it, but nothing ever read it
--   outside the data-export screen. Any account could comment on any post no
--   matter what the owner had chosen. Same class of gap that post tagging had.
--
-- WHAT THIS DOES
--   1. Nova: enforced in the database. A trigger on nova_comments rejects a comment
--      when the post owner's comment_who excludes the author. Nova comments are
--      Postgres rows, so this is real enforcement a modified client cannot skip.
--   2. Instagram: comments are Firestore documents, which cannot read a Postgres
--      table inside a security rule. can_comment_on_post() exposes the same decision
--      so the client can refuse before writing; a determined client could still
--      write. Recorded here rather than pretended otherwise.
--
-- SEMANTICS (matching the three labels in Settings)
--   everyone  -> anybody
--   following -> only accounts the post owner follows
--   followers -> only accounts that follow the post owner
--   The post owner can always comment on their own post.
-- ============================================================================

/* ---------------------------------------------------------------------------
   1. Nova enforcement
   --------------------------------------------------------------------------- */

create or replace function public.tg_enforce_nova_comment_who()
returns trigger
language plpgsql
security definer
set search_path to 'public'
as $$
declare
    v_owner uuid;
    v_permission text;
begin
    select p.author_id into v_owner
      from public.nova_posts p
     where p.id = new.post_id;

    if v_owner is null or v_owner = new.author_id then
        return new;
    end if;

    select coalesce(pr.comment_who, 'everyone') into v_permission
      from public.profiles pr
     where pr.id = v_owner;

    if v_permission = 'following' and not exists (
        select 1 from public.follows f
         where f.follower_id = v_owner
           and f.following_id = new.author_id
    ) then
        raise exception 'the author of this post only allows comments from people they follow'
            using errcode = '42501';
    end if;

    if v_permission = 'followers' and not exists (
        select 1 from public.follows f
         where f.follower_id = new.author_id
           and f.following_id = v_owner
    ) then
        raise exception 'the author of this post only allows comments from their followers'
            using errcode = '42501';
    end if;

    return new;
end;
$$;

drop trigger if exists trg_enforce_nova_comment_who on public.nova_comments;
create trigger trg_enforce_nova_comment_who
    before insert on public.nova_comments
    for each row execute function public.tg_enforce_nova_comment_who();

/* ---------------------------------------------------------------------------
   2. Instagram: the same decision, exposed for the client to ask first
   --------------------------------------------------------------------------- */

create or replace function public.can_comment_on_post(p_post uuid)
returns boolean
language plpgsql
security definer
set search_path to 'public'
as $$
declare
    v_owner uuid;
    v_permission text;
    v_me uuid := public.request_uid();
begin
    if v_me is null then
        return false;
    end if;

    select p.user_id into v_owner from public.posts p where p.id = p_post;
    if v_owner is null or v_owner = v_me then
        return true;
    end if;

    select coalesce(pr.comment_who, 'everyone') into v_permission
      from public.profiles pr
     where pr.id = v_owner;

    if v_permission = 'nobody' then
        return false;
    end if;

    if v_permission = 'following' then
        return exists (
            select 1 from public.follows f
             where f.follower_id = v_owner
               and f.following_id = v_me
        );
    end if;

    if v_permission = 'followers' then
        return exists (
            select 1 from public.follows f
             where f.follower_id = v_me
               and f.following_id = v_owner
        );
    end if;

    return true;
end;
$$;

grant execute on function public.can_comment_on_post(uuid) to authenticated;

/* ============================================================================
   Verification
   ----------------------------------------------------------------------------
   1. select count(*) from pg_trigger where tgname='trg_enforce_nova_comment_who';  -- 1
   2. Owner sets comment_who='followers'; a non-follower inserts a nova comment
      -> 42501 "only allows comments from their followers"
   3. A follower inserts -> succeeds.
   4. select public.can_comment_on_post('<post of a followers-only owner>')
      -> false for a non-follower, true for the owner.
   ============================================================================ */
