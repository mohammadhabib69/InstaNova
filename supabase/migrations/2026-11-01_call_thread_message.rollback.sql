-- Reverses 2026-11-01_call_thread_message.sql.
--
-- The thread rows it created are removed first: every one of them has a primary key
-- that is also a `calls` id, which no genuine chat message can have. Nothing else is
-- touched — no policies were changed, so there is nothing to restore on the tables.

delete from public.messages m
 where exists (select 1 from public.calls c where c.id = m.id)
   and m.sender_id = (select c.caller_id from public.calls c where c.id = m.id);

drop function if exists public.record_call_message(uuid);
drop function if exists public.call_message_body(text, text, int);
