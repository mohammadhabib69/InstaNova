-- Story audience editing was impossible: `stories` had SELECT / INSERT / DELETE policies but no
-- UPDATE policy, so any PATCH (even from the owner) was refused by RLS. The client had no control
-- either, which is why the More sheet offered only delete / save / highlight.
--
-- Only the owner may change a row, and only to a row they still own (WITH CHECK), so the policy
-- cannot be used to hand a story to somebody else. RLS stays enabled.

drop policy if exists stories_update on public.stories;

create policy stories_update on public.stories
    for update
    using (user_id = public.request_uid())
    with check (user_id = public.request_uid());

-- Guard rails: audience is free text today, and `story_visible()` only understands 'public' and
-- 'close_friends'. Anything else would silently behave like 'public', so reject it at the table.
update public.stories
   set audience = 'public'
 where audience is null or audience not in ('public', 'close_friends');

alter table public.stories
    drop constraint if exists stories_audience_check;

alter table public.stories
    add constraint stories_audience_check
    check (audience in ('public', 'close_friends'));
