-- ============================================================================
-- Push delivery for notifications written by the backend.
--
-- WHY
--   A story reaction is inserted into public.notifications and read when the owner
--   opens the Notifications screen. Nothing woke a closed app, so the reaction was
--   silent until they happened to look. This adds the delivery half only — the read
--   half (NotificationCenter.syncFromServer) already exists.
--
-- HOW
--   An AFTER INSERT trigger posts the new row to the send-notification-push Edge
--   Function through pg_net. pg_net queues the request and returns immediately, so the
--   insert is not blocked by FCM, and a slow Google response cannot fail the reaction.
--
-- SAFETY
--   The function claims the row by setting push_dispatched_at, so a webhook retry, a
--   duplicate insert or a manually repeated call delivers at most one buzz per row.
-- ============================================================================

alter table public.notifications
    add column if not exists push_dispatched_at timestamptz;

comment on column public.notifications.push_dispatched_at is
    'Set by send-notification-push when the row has been claimed for FCM delivery. '
    'Doubles as the dedupe key so one notification cannot buzz twice.';

create or replace function public.tg_push_notification()
returns trigger
language plpgsql
security definer
set search_path to 'public'
as $$
begin
    -- Only the types the app actually presents need a push.
    if new.type not in ('story_reaction', 'follow', 'follow_request') then
        return new;
    end if;

    perform net.http_post(
        url := 'https://poqdemxiuoocbzkwbyhy.supabase.co/functions/v1/send-notification-push',
        headers := jsonb_build_object('Content-Type', 'application/json'),
        body := jsonb_build_object(
            'type', 'INSERT',
            'table', 'notifications',
            'record', to_jsonb(new)
        ),
        timeout_milliseconds := 5000
    );

    return new;
end;
$$;

drop trigger if exists trg_push_notification on public.notifications;
create trigger trg_push_notification
    after insert on public.notifications
    for each row execute function public.tg_push_notification();

/* ============================================================================
   Verification
   ----------------------------------------------------------------------------
   1. insert a story_reaction row for a profile that has an fcm_token
   2. wait ~3s, then:
        select push_dispatched_at from public.notifications where id = '<id>';
      -> non-null means the function ran and claimed it
        select status_code, content from net._http_response
         order by created_at desc limit 1;
      -> 200 and {"success":true,...}
   3. delete the test row.
   ============================================================================ */
