-- Rollback for 2026-11-08_media_private_buckets.sql
-- Restores the world-readable buckets. The client keeps working either way, because it
-- always addresses objects through /object/authenticated/, which is valid for a public
-- bucket too.

drop policy if exists storage_chat_read on storage.objects;
drop function if exists public.can_read_chat_media(text);

create policy storage_chat_read on storage.objects for select to public
    using (bucket_id = 'chat');

drop policy if exists storage_posts_read   on storage.objects;
drop policy if exists storage_reels_read   on storage.objects;
drop policy if exists storage_avatars_read on storage.objects;
drop policy if exists nova_media_select    on storage.objects;

create policy storage_posts_read on storage.objects for select to public
    using (bucket_id = 'posts');
create policy storage_reels_read on storage.objects for select to public
    using (bucket_id = 'reels');
create policy storage_avatars_read on storage.objects for select to public
    using (bucket_id = 'avatars');
create policy nova_media_select on storage.objects for select to public
    using (bucket_id = 'nova');

update storage.buckets
   set public = true
 where id in ('avatars', 'chat', 'nova', 'posts', 'reels');
