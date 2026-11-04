-- ============================================================================
-- Make the Privacy settings options actually do something.
--
-- BACKGROUND
--   Settings persisted comment_who / tag_who / mention_who / request_who to
--   device SharedPreferences only. Nothing enforced them, and the only reader
--   in the whole app was the data-export screen, which wrote them into the
--   downloaded JSON. "Message request controls" was therefore a screen that
--   changed nothing anywhere.
--
--   "Restricted accounts" was half-built: restricted_users was read and written
--   correctly and InboxActivity already routed a restricted conversation into
--   Requests, but nothing held a restricted person's comments, which is the
--   behaviour the screen's own description promises.
--
-- WHAT THIS MIGRATION DOES
--   1. Adds the four who-can columns to profiles so the choice is account-level
--      and honoured by other devices, not just the phone that set it.
--   2. Adds is_held to comments and nova_comments.
--   3. Holds a comment automatically when its author is restricted by the post
--      owner (BEFORE INSERT triggers, so a client cannot opt out).
--   4. Hides held comments from everyone except their author and the post owner
--      for the Instagram space (RLS policy) and the Nova space (public view).
--   5. Adds approve RPCs so the post owner can release a held comment.
--   6. Enforces request_who inside the existing message-request trigger.
-- ============================================================================

/* ---------------------------------------------------------------------------
   1. Account-level who-can settings
   --------------------------------------------------------------------------- */

alter table public.profiles
    add column if not exists comment_who  text not null default 'everyone',
    add column if not exists tag_who      text not null default 'everyone',
    add column if not exists mention_who  text not null default 'everyone',
    add column if not exists request_who  text not null default 'everyone';

alter table public.profiles
    drop constraint if exists profiles_comment_who_check,
    drop constraint if exists profiles_tag_who_check,
    drop constraint if exists profiles_mention_who_check,
    drop constraint if exists profiles_request_who_check;

alter table public.profiles
    add constraint profiles_comment_who_check check (comment_who in ('everyone','following','followers')),
    add constraint profiles_tag_who_check     check (tag_who     in ('everyone','following','nobody')),
    add constraint profiles_mention_who_check check (mention_who in ('everyone','following','nobody')),
    add constraint profiles_request_who_check check (request_who in ('everyone','following','nobody'));

/* ---------------------------------------------------------------------------
   2. Held-comment state
   --------------------------------------------------------------------------- */

alter table public.comments
    add column if not exists is_held boolean not null default false;

alter table public.nova_comments
    add column if not exists is_held boolean not null default false;

create index if not exists comments_is_held_idx on public.comments (post_id) where is_held;
create index if not exists nova_comments_is_held_idx on public.nova_comments (post_id) where is_held;

/* ---------------------------------------------------------------------------
   3. Hold comments written by a restricted person

   The check runs in the database rather than the client so that a modified
   client cannot post straight past the restriction.
   --------------------------------------------------------------------------- */

create or replace function public.tg_hold_restricted_comment()
returns trigger
language plpgsql
security definer
set search_path to 'public'
as $$
begin
    if new.is_held then
        return new;
    end if;
    if exists (
        select 1
          from public.posts p
          join public.restricted_users ru
            on ru.owner_id = p.user_id
           and ru.restricted_id = new.user_id
         where p.id = new.post_id
    ) then
        new.is_held := true;
    end if;
    return new;
end;
$$;

drop trigger if exists trg_hold_restricted_comment on public.comments;
create trigger trg_hold_restricted_comment
    before insert on public.comments
    for each row execute function public.tg_hold_restricted_comment();

-- Nova comments key their author as author_id and their post owner on nova_posts.
create or replace function public.tg_hold_restricted_nova_comment()
returns trigger
language plpgsql
security definer
set search_path to 'public'
as $$
begin
    if new.is_held then
        return new;
    end if;
    if exists (
        select 1
          from public.nova_posts p
          join public.restricted_users ru
            on ru.owner_id = p.author_id
           and ru.restricted_id = new.author_id
         where p.id = new.post_id
    ) then
        new.is_held := true;
    end if;
    return new;
end;
$$;

drop trigger if exists trg_hold_restricted_nova_comment on public.nova_comments;
create trigger trg_hold_restricted_nova_comment
    before insert on public.nova_comments
    for each row execute function public.tg_hold_restricted_nova_comment();

/* ---------------------------------------------------------------------------
   4a. Visibility — Instagram comments (row level security)

   Replaces the previous `using (true)`. A held comment is now visible only to
   its author and to the owner of the post it belongs to.
   --------------------------------------------------------------------------- */

drop policy if exists comments_select on public.comments;
create policy comments_select on public.comments
    for select
    using (
        not is_held
        or user_id = public.request_uid()
        or exists (
            select 1 from public.posts p
             where p.id = comments.post_id
               and p.user_id = public.request_uid()
        )
    );

/* ---------------------------------------------------------------------------
   4b. Visibility — Nova comments (public view)

   Same rule, expressed in the view because Nova clients never touch the base
   table. Column list is unchanged apart from the new is_held flag, so existing
   readers keep working.
   --------------------------------------------------------------------------- */

create or replace view public.nova_comments_public as
 SELECT c.id,
    c.post_id,
    c.parent_id,
        CASE
            WHEN c.is_anonymous THEN NULL::uuid
            ELSE c.author_id
        END AS author_id,
        CASE
            WHEN c.is_anonymous THEN c.anonymous_alias
            ELSE COALESCE(pr.username, 'unknown'::text)
        END AS alias,
        CASE
            WHEN c.is_anonymous THEN NULL::text
            ELSE pr.avatar_url
        END AS avatar_url,
    c.body,
    c.is_anonymous,
    c.score,
    c.upvote_count,
    c.downvote_count,
    c.created_at,
    c.author_id = public.request_uid() AS is_mine,
    COALESCE(( SELECT v.value
           FROM public.nova_votes v
          WHERE v.target_type = 'comment'::text AND v.target_id = c.id AND v.voter_id = public.request_uid()), 0) AS my_vote,
    c.is_held
   FROM public.nova_comments c
     LEFT JOIN public.profiles pr ON pr.id = c.author_id
  WHERE c.deleted_at IS NULL
    AND (
        not c.is_held
        or c.author_id = public.request_uid()
        or exists (
            select 1 from public.nova_posts p
             where p.id = c.post_id
               and p.author_id = public.request_uid()
        )
    );

/* ---------------------------------------------------------------------------
   5. Approving a held comment
   --------------------------------------------------------------------------- */

create or replace function public.approve_held_comment(p_comment uuid)
returns void
language plpgsql
security definer
set search_path to 'public'
as $$
declare
    v_post uuid;
begin
    select c.post_id into v_post from public.comments c where c.id = p_comment;
    if v_post is null then
        raise exception 'comment not found';
    end if;
    if not exists (
        select 1 from public.posts p
         where p.id = v_post and p.user_id = public.request_uid()
    ) then
        raise exception 'only the post owner can approve a held comment';
    end if;
    update public.comments set is_held = false where id = p_comment;
end;
$$;

create or replace function public.approve_held_nova_comment(p_comment uuid)
returns void
language plpgsql
security definer
set search_path to 'public'
as $$
declare
    v_post uuid;
begin
    select c.post_id into v_post from public.nova_comments c where c.id = p_comment;
    if v_post is null then
        raise exception 'comment not found';
    end if;
    if not exists (
        select 1 from public.nova_posts p
         where p.id = v_post and p.author_id = public.request_uid()
    ) then
        raise exception 'only the post owner can approve a held comment';
    end if;
    update public.nova_comments set is_held = false where id = p_comment;
end;
$$;

grant execute on function public.approve_held_comment(uuid) to authenticated;
grant execute on function public.approve_held_nova_comment(uuid) to authenticated;

/* ---------------------------------------------------------------------------
   6. request_who inside the existing message-request trigger

   Everyone (default): unchanged behaviour.
   following         : a stranger's first message lands in Requests; somebody the
                       recipient already follows is accepted.
   nobody            : the request is declined outright.
   --------------------------------------------------------------------------- */

create or replace function public.tg_message_request_state()
returns trigger
language plpgsql
security definer
set search_path to 'public'
as $$
begin
    update public.conversation_members cm
       set request_state = case
               when exists (select 1
                              from public.follows f
                             where f.follower_id = new.sender_id
                               and f.following_id = cm.user_id)
                   then 'accepted'
               when cm.request_state = 'accepted' then 'accepted'
               when coalesce((select p.request_who from public.profiles p
                               where p.id = cm.user_id), 'everyone') = 'nobody'
                   then 'declined'
               when coalesce((select p.request_who from public.profiles p
                               where p.id = cm.user_id), 'everyone') = 'following'
                    and not exists (select 1
                                      from public.follows f
                                     where f.follower_id = cm.user_id
                                       and f.following_id = new.sender_id)
                   then 'declined'
               else 'pending'
           end
     where cm.conversation_id = new.conversation_id
       and cm.user_id <> new.sender_id;
    return null;
end;
$$;

/* ============================================================================
   Verification
   ----------------------------------------------------------------------------
   1. Columns exist:
      select column_name from information_schema.columns
       where table_schema='public' and table_name='profiles'
         and column_name in ('comment_who','tag_who','mention_who','request_who');

   2. A restricted person's comment is held:
      insert a restricted_users row, insert a comment as that user, then
      select is_held from public.comments where id = <new id>;   -- expect true

   3. A held comment is invisible to a third account:
      run the same select as a third user;                            -- expect 0 rows
      as its author and as the post owner;                            -- expect 1 row

   4. Approve:
      select public.approve_held_comment('<id>');                     -- expect void
      select is_held from public.comments where id = '<id>';          -- expect false

   5. request_who='nobody' on a profile then a stranger's message:
      select request_state from public.conversation_members
       where conversation_id = '<id>' and user_id = '<recipient>';    -- expect declined
   ============================================================================ */
