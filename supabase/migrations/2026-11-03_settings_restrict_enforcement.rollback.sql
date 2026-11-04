-- ============================================================================
-- Rollback for 2026-11-03_settings_restrict_enforcement.sql
--
-- Restores the previous behaviour exactly: comments unheld and fully readable,
-- Nova comments unfiltered, request_state driven by follow status only, and the
-- four who-can columns removed from profiles.
-- ============================================================================

drop function if exists public.approve_held_comment(uuid);
drop function if exists public.approve_held_nova_comment(uuid);

drop trigger if exists trg_hold_restricted_comment on public.comments;
drop trigger if exists trg_hold_restricted_nova_comment on public.nova_comments;
drop function if exists public.tg_hold_restricted_comment();
drop function if exists public.tg_hold_restricted_nova_comment();

drop policy if exists comments_select on public.comments;
create policy comments_select on public.comments for select using (true);

-- Restore the view without the held filter. If the client has been rebuilt to
-- read is_held, drop that column first or keep the filter and only revert data.
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
          WHERE v.target_type = 'comment'::text AND v.target_id = c.id AND v.voter_id = public.request_uid()), 0) AS my_vote
   FROM public.nova_comments c
     LEFT JOIN public.profiles pr ON pr.id = c.author_id
  WHERE c.deleted_at IS NULL;

alter table public.comments drop column if exists is_held;
alter table public.nova_comments drop column if exists is_held;

alter table public.profiles
    drop constraint if exists profiles_comment_who_check,
    drop constraint if exists profiles_tag_who_check,
    drop constraint if exists profiles_mention_who_check,
    drop constraint if exists profiles_request_who_check;

alter table public.profiles
    drop column if exists comment_who,
    drop column if exists tag_who,
    drop column if exists mention_who,
    drop column if exists request_who;

-- Back to follow-status-only request classification.
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
               else 'pending'
           end
     where cm.conversation_id = new.conversation_id
       and cm.user_id <> new.sender_id;
    return null;
end;
$$;
