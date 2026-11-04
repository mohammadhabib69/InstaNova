-- 2026-11-08 — story editor metadata.
--
-- Photo stories are flattened on publish, so the canvas is already final. Video stories
-- keep their original file, which means the trim range, the mute flag and the text
-- elements have to travel with the row: that is what editor_meta holds. It is also the
-- record of fit/fill and the chosen filter for both kinds.
alter table public.stories
    add column if not exists editor_meta jsonb;

-- v_stories is the only read path the client uses, so the column has to be exposed there
-- or the viewer would never see it.
create or replace view public.v_stories as
select s.id,
       s.user_id,
       pr.username,
       pr.avatar_url,
       s.media_url,
       s.media_type,
       s.created_at,
       s.expires_at,
       exists (select 1 from public.story_views sv
                where sv.story_id = s.id and sv.viewer_id = public.request_uid()) as seen_by_me,
       s.audience,
       s.editor_meta
  from public.stories s
  join public.profiles pr on pr.id = s.user_id
 where s.expires_at > now()
   and public.can_view(s.user_id)
   and not public.is_story_hidden(s.user_id, public.request_uid())
   and public.story_visible(s.user_id, s.audience);
