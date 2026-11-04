-- ============================================================================
-- Story audience — close the direct-read hole.
--
-- WHAT I GOT WRONG BEFORE
--   An earlier pass concluded Story audience had no consumer at all. Wrong.
--   Reading the definitions shows the filters were already built:
--     v_stories        -> story_visible(user_id, audience) + is_story_hidden(...)
--     get_story_groups -> the same two, plus a followed-users restriction for the tray
--     is_story_hidden  -> story_audience (owner_id, viewer_id)
--     story_visible    -> 'close_friends' honoured through is_close_friend()
--   The setting's real shape is a hide list ("Hide story from ..."), not a
--   three-way choice, which is what the Settings row and its "N people" subtitle do.
--
-- THE ACTUAL GAP
--   public.stories had `stories_select USING (true)`. The views filter, the table
--   did not, so GET /rest/v1/stories returned every active story with its media_url
--   regardless of the hide list or a close_friends audience. The predicate was only
--   as good as the client choosing the view. Same shape as the posts grant issue.
--
-- WHAT THIS DOES
--   1. stories_select now enforces the identical predicate as v_stories, so the
--      base table and the view cannot disagree.
--   2. Requires a resolved caller, so the anon key alone reads nothing.
--   3. Revokes TRUNCATE / REFERENCES / TRIGGER on the three story tables — the same
--      hardening applied to public.posts, since RLS does not cover those privileges.
--
-- NOT CHANGED ON PURPOSE
--   The tray's followed-users restriction stays in get_story_groups and is NOT added
--   to RLS: opening a public story from a profile you do not follow is legitimate, so
--   RLS enforces audience + hide list only.
-- ============================================================================

/* ---------------------------------------------------------------------------
   1. Base table now agrees with the view
   --------------------------------------------------------------------------- */

drop policy if exists stories_select on public.stories;

create policy stories_select on public.stories
    for select to authenticated
    using (
        expires_at > now()
        and public.request_uid() is not null
        and public.story_visible(user_id, audience)
        and not public.is_story_hidden(user_id, public.request_uid())
    );

/* ---------------------------------------------------------------------------
   2. Privilege hardening (RLS does not cover these three)
   --------------------------------------------------------------------------- */

revoke truncate, references, trigger on public.stories from anon, authenticated;
revoke truncate, references, trigger on public.story_audience from anon, authenticated;
revoke truncate, references, trigger on public.story_views from anon, authenticated;

/* ---------------------------------------------------------------------------
   3. The view needs its own anon guard.
   ---------------------------------------------------------------------------
   Fixing the base table is not enough: v_stories is owned by postgres and runs with
   owner rights, so the caller's RLS never applies inside it. Measured after the
   policy change: anon read 0 rows from public.stories but still 3 from
   public.v_stories, because story_visible() returns true when request_uid() is null.
   Anon has no legitimate reason to read stories, so deny at the grant.

   get_story_groups() already excluded anon (execute is authenticated + service_role
   only), so nothing to change there.
   --------------------------------------------------------------------------- */

revoke select on public.v_stories from anon;

/* ============================================================================
   Verification (run each as a simulated end user, not as postgres)
   ----------------------------------------------------------------------------
   As viewer V, with request.jwt.claims sub=V:
     select count(*) from public.stories;              -- only V's visible set
     select count(*) from public.v_stories;            -- must match
   Owner hides V -> both drop by that owner's count.
   Owner audience='close_friends' and V not a close friend -> hidden from both.
   ============================================================================ */
