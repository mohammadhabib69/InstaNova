-- ============================================================================
-- 2026-11-02  Rollback for the Nova separation / Post Options migration
--
-- Removes the Nova-specific tables, views, RPCs and storage policies and puts
-- the legacy Nova rows back into public.posts with space = 'nova'.
--
-- The legacy rows are restored from nova_posts; their original ids are not
-- preserved (nova_posts generates new ids), so the restored rows are new
-- rows rather than the original ones.
-- ============================================================================

begin;

-- The Instagram rows that were shared from Nova keep their provenance column
-- until it is dropped; clear it first so the drop cannot fail.
update public.posts set source_nova_post_id = null where source_nova_post_id is not null;
update public.stories set source_nova_post_id = null where source_nova_post_id is not null;

-- Put the Nova content back into the Instagram table, space = 'nova'.
insert into public.posts (id, user_id, caption, thumbnail_url, is_reel, space, created_at)
select gen_random_uuid(), p.author_id, p.body, p.media_url,
       (p.media_type = 'reel'), 'nova', p.created_at
  from public.nova_posts p
 where p.deleted_at is null
   and p.media_type in ('photo','reel');

-- Storage policies and bucket.
drop policy if exists nova_media_insert on storage.objects;
drop policy if exists nova_media_select on storage.objects;
drop policy if exists nova_media_update on storage.objects;
drop policy if exists nova_media_delete on storage.objects;
delete from storage.buckets where id = 'nova';

-- RPCs.
drop function if exists public.create_nova_post(text, text, text, text, text, boolean, timestamptz, jsonb);
drop function if exists public.create_nova_comment(uuid, text, uuid, boolean);
drop function if exists public.nova_cast_vote(text, uuid, integer);
drop function if exists public.cast_vote(uuid, integer);
drop function if exists public.cast_comment_vote(uuid, integer);
drop function if exists public.toggle_nova_save(uuid, boolean);
drop function if exists public.share_nova_to_instagram(uuid, text);
drop function if exists public.nova_expire_posts();
drop function if exists public.nova_purge_expired_media();
drop function if exists public.nova_random_alias();

-- The cron job.
do $$
declare v_job record;
begin
    for v_job in select jobid from cron.job where jobname = 'expire_nova_posts' loop
        perform cron.unschedule(v_job.jobid);
    end loop;
end $$;

-- Views, then tables.
drop view if exists public.nova_posts_public;
drop view if exists public.nova_comments_public;

drop table if exists public.nova_expired_media;
drop table if exists public.nova_saves;
drop table if exists public.nova_votes;
drop table if exists public.nova_comments;
drop table if exists public.nova_posts;

-- The provenance columns.
alter table public.posts drop column if exists source_nova_post_id;
alter table public.stories drop column if exists source_nova_post_id;

-- The space default stays: rows without a space are Instagram rows and the
-- Instagram feed filters on space = 'insta'.

commit;
