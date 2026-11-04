-- ============================================================================
-- Rollback for 2026-11-02_harden_posts_grants.sql
--
-- Restores the Supabase default privilege set on public.posts. Only needed if
-- something turns out to depend on TRUNCATE / REFERENCES / TRIGGER as a client
-- role, which nothing in the app or in PostgREST does.
-- ============================================================================

grant truncate, references, trigger on public.posts to anon;
grant truncate, references, trigger on public.posts to authenticated;
