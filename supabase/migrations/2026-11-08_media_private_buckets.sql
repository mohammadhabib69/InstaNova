-- 2026-11-08 — every media bucket becomes private.
--
-- Only `stories` was private before this. posts, reels, nova, chat and avatars were
-- world-readable: the storage API served /object/public/... without consulting a policy,
-- so anyone holding an object URL — and every URL in the app is enumerable from the
-- feed — could fetch the bytes, signed in or not.
--
-- No stored URL had to be rewritten. The client now sends every object request through
-- /storage/v1/object/authenticated/ (StoragePrivacyInterceptor on the shared OkHttp
-- client, which also carries the upload verification HEAD), so the existing
-- /public/ strings keep working as identifiers while the policy decides access.

update storage.buckets
   set public = false
 where id in ('avatars', 'chat', 'nova', 'posts', 'reels');

-- Read access becomes a signed-in-user decision. These policies were granted to
-- `public`, i.e. anon + authenticated; the anon key ships inside the APK, so with the
-- role left as-is a private bucket would still have been readable by anyone who
-- extracted it. `authenticated` is the role a Firebase-verified request assumes.

drop policy if exists storage_posts_read   on storage.objects;
drop policy if exists storage_reels_read   on storage.objects;
drop policy if exists storage_avatars_read on storage.objects;
drop policy if exists storage_chat_read    on storage.objects;
drop policy if exists nova_media_select    on storage.objects;

create policy storage_posts_read on storage.objects for select to authenticated
    using (bucket_id = 'posts');

create policy storage_reels_read on storage.objects for select to authenticated
    using (bucket_id = 'reels');

create policy storage_avatars_read on storage.objects for select to authenticated
    using (bucket_id = 'avatars');

create policy nova_media_select on storage.objects for select to authenticated
    using (bucket_id = 'nova');

-- Chat is the one bucket where "any signed-in user" is not good enough: a DM photo
-- should only be visible to the two people in the conversation. The object name is the
-- only link back to a conversation, so the policy resolves it through the message row
-- that points at the object. The sender's own folder is also allowed, because the bytes
-- are uploaded before the message row exists.
create or replace function public.can_read_chat_media(p_object_name text)
returns boolean
language sql
stable
security definer
set search_path to 'public', 'pg_temp'
as $$
    select split_part(p_object_name, '/', 1) = coalesce(public.request_uid()::text, '')
        or exists (
            select 1
              from public.messages m
              join public.conversation_members cm
                on cm.conversation_id = m.conversation_id
             where cm.user_id = public.request_uid()
               and m.media_url like '%' || p_object_name
        );
$$;

revoke all on function public.can_read_chat_media(text) from public;
grant execute on function public.can_read_chat_media(text) to authenticated;

create policy storage_chat_read on storage.objects for select to authenticated
    using (bucket_id = 'chat' and public.can_read_chat_media(name));
