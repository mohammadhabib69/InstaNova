-- ============================================================================
-- Story media stops being world-readable.
--
-- MEASURED BEFORE THE CHANGE
--   All six storage buckets were public. Fetching the newest story's media_url with
--   curl and no credentials at all returned HTTP 200 and 57,927 bytes. Section 22 closed
--   the row, so a client can no longer discover that URL — but the object itself stayed
--   readable to anyone holding the link, forever, including after the story expires.
--
-- WHAT THIS DOES
--   1. stories bucket -> public = false. Objects are then served only through
--      /storage/v1/object/authenticated/..., which enforces the storage RLS policy.
--   2. storage_stories_read was `bucket_id = 'stories'` — any signed-in account could
--      read any story object. It now applies the same decision as v_stories:
--        owner            -> always (the archive screen shows expired stories)
--        everyone else    -> only while unexpired, allowed by story_visible(audience)
--                            and not in the owner's hide list.
--   3. Upload/update/delete policies are untouched, so publishing still works.
--
-- CLIENT
--   MediaUrl.authenticated() swaps the public endpoint for the authenticated one and
--   GlideUtils attaches the apikey/Bearer headers for buckets in PRIVATE_BUCKETS.
-- ============================================================================

update storage.buckets set public = false where id = 'stories';

drop policy if exists storage_stories_read on storage.objects;

create policy storage_stories_read on storage.objects
    for select to authenticated
    using (
        bucket_id = 'stories'
        and (
            -- Your own uploads stay readable to you. Two things depend on this that the
            -- row-based rule cannot express: the archive screen lists expired stories,
            -- and the upload verification HEAD runs before the story row exists.
            (storage.foldername(name))[1] = public.request_uid()::text
            or exists (
                select 1
                  from public.stories s
                 where s.media_url like '%' || storage.objects.name
                   and s.expires_at > now()
                   and public.story_visible(s.user_id, s.audience)
                   and not public.is_story_hidden(s.user_id, public.request_uid())
            )
        )
    );

/* ============================================================================
   Verification
   ----------------------------------------------------------------------------
   1. select public from storage.buckets where id='stories';            -- false
   2. curl the old /object/public/... URL with no headers               -- 400/404
   3. as the owner (JWT sub = story owner) the authenticated endpoint returns the object
   4. after hiding the story from a viewer, that viewer gets an error from the same URL
   ============================================================================ */
