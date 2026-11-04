-- ============================================================================
-- 2026-11-02  Verification for the Nova separation / Post Options migration
-- Every statement below must return the expected shape.
-- ============================================================================

-- 1. The four Nova tables exist with RLS on.
select table_name, rls_enabled
from pg_tables
where schemaname = 'public'
  and table_name in ('nova_posts','nova_comments','nova_votes','nova_saves')
order by table_name;

-- 2. The public views exist and expose the masking columns.
select table_name
from information_schema.views
where table_schema = 'public'
  and table_name in ('nova_posts_public','nova_comments_public')
order by table_name;

-- 3. The masking contract: author_id is NULL for anonymous rows, alias is the
--    random per-post alias, is_mine still resolves for the owner.
select id,
       author_id is null as author_masked,
       alias,
       is_mine,
       my_vote,
       is_saved,
       expires_at
from public.nova_posts_public
order by created_at desc
limit 20;

-- 4. No Nova row may remain in the Instagram posts table.
select count(*) as nova_rows_left_in_posts
from public.posts
where space = 'nova';

-- 5. Instagram rows keep their own space and carry the provenance column.
select space, count(*)
from public.posts
group by space
order by space;

-- 6. Votes: one row per user per target.
select voter_id, target_type, target_id, count(*) as dupes
from public.nova_votes
group by voter_id, target_type, target_id
having count(*) > 1;

-- 7. Indexes on (created_at), (expires_at) and (score).
select indexname from pg_indexes
where schemaname = 'public'
  and tablename in ('nova_posts','nova_comments')
order by indexname;

-- 8. The expiry job is scheduled.
select jobid, jobname, schedule, command, active
from cron.job
where jobname = 'expire_nova_posts';

-- 9. Client roles cannot read the base tables, only the views.
select table_name, grantee, privilege_type
from information_schema.role_table_grants
where table_schema = 'public'
  and table_name in ('nova_posts','nova_comments','nova_votes','nova_saves',
                     'nova_posts_public','nova_comments_public')
  and grantee in ('anon','authenticated')
order by table_name, grantee, privilege_type;

-- 10. The Instagram source-link column exists.
select column_name, data_type
from information_schema.columns
where table_schema = 'public'
  and table_name in ('posts','stories')
  and column_name = 'source_nova_post_id';

-- 11. The Nova storage bucket and its policies.
select id, name, public from storage.buckets where id = 'nova';
select policyname, cmd, roles from pg_policies
where schemaname = 'storage' and tablename = 'objects' and policyname like 'nova%'
order by policyname;
