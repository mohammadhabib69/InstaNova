-- #55 follow-up — the request default was self-defeating.
--
-- 2026-11-02_message_requests.sql gave `conversation_members.request_state` a default of
-- 'accepted' so that every thread already on disk stayed in the inbox. That is right for
-- existing rows and wrong for new ones: a conversation created today also started
-- "accepted", and since "accepted" is a latch, the trigger could never mark a stranger's
-- first message as a request. Verified on device — a first DM between two accounts that
-- do not follow each other produced two `accepted` membership rows.
--
-- The default belongs on the row at creation time and must be 'pending'; the inbox then
-- decides from the trigger. Existing rows keep 'accepted' untouched, which is exactly the
-- grandfathering the first migration intended, so no data is rewritten here.

alter table public.conversation_members
    alter column request_state set default 'pending';
