-- DM shares need to know what kind of content a media message points at, so tapping it can
-- open the story / post / reel instead of a plain image viewer.
alter table public.messages
    add column if not exists share_kind text;

comment on column public.messages.share_kind is
    'story | post | reel — what post_id refers to for a shared media message; null for a plain photo';

alter table public.messages
    drop constraint if exists messages_share_kind_check;

alter table public.messages
    add constraint messages_share_kind_check
    check (share_kind is null or share_kind in ('story', 'post', 'reel'));

-- post_id never existed on messages (the DTO field was always null), so shares get their own ref.
alter table public.messages
    add column if not exists share_id uuid;
