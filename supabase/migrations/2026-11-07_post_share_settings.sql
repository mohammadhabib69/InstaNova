-- Part 4 "Advanced settings": hide the like count on this post, and turn comments off.
-- Both default to the current behaviour so existing rows are unaffected.
alter table public.posts
    add column if not exists hide_like_count boolean not null default false;

alter table public.posts
    add column if not exists comments_disabled boolean not null default false;
