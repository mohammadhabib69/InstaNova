-- STATUS 2026-10-30: PARTIALLY APPLIED.
-- Seven revocations were executed over the recovered pooler connection
-- (aws-0-ap-northeast-1): follow_user, approve_follow_request, delete_my_account,
-- set_account_privacy, touch_active, get_story_groups, get_or_create_conversation
-- (the last also needed its PUBLIC grant removed). anon-executable functions went 18 -> 11.
-- Re-running those statements is harmless (REVOKE is idempotent).
-- NOT applied: any function this file lists that the app may call before login —
-- get_email_by_username and is_username_available are deliberately left executable by anon.

-- P0-24 · Revoke `anon` EXECUTE on RPCs that only make sense for a signed-in user.
--
-- STATUS: DRAFT — NOT APPLIED. Applying this changes the live permission surface of the
-- production database, so it stays a reviewed migration until approved. It is additive
-- and reversible (rollback at the bottom) and touches no data.
--
-- Why not all 16 anon-executable functions? Three groups need different treatment:
--
-- A. KEEP anon — required before sign-in:
--      get_email_by_username(text)   username → email, used by the login screen
--      is_username_available(text)   signup wizard checks a name before an account exists
--
-- B. KEEP anon — RLS policy helpers, not client RPCs. Revoking these would break
--    anonymous SELECT policies that call them:
--      request_uid(), can_view(uuid), is_story_hidden(uuid,uuid), story_visible(uuid,text),
--      is_close_friend(uuid,uuid), is_restricted(uuid,uuid), is_conversation_member(uuid,uuid)
--
-- C. REVOKE — reachable today at /rest/v1/rpc/<name> with only the public anon key,
--    and each requires an authenticated caller (or is a trigger function that should
--    never be callable over HTTP):
--      approve_follow_request(p_requester uuid)
--      delete_my_account()
--      follow_user(p_target uuid)
--      get_or_create_conversation(p_other_user_id uuid)
--      set_account_privacy(p_private boolean)
--      touch_active()
--      get_story_groups()
--      on_call_session_created()            (trigger function)
--      calls_prevent_terminal_overwrite()   (trigger function)
--
-- Before applying, confirm the Android client never calls group C while signed out.
-- Grep: `grep -rn "rpc/<name>" app/src/main/java` → all call sites sit behind a
-- `SessionManager.getUserId()` check in the current tree.

begin;

revoke execute on function public.approve_follow_request(uuid)      from anon;
revoke execute on function public.delete_my_account()               from anon;
revoke execute on function public.follow_user(uuid)                 from anon;
revoke execute on function public.get_or_create_conversation(uuid)  from anon;
revoke execute on function public.set_account_privacy(boolean)      from anon;
revoke execute on function public.touch_active()                    from anon;
revoke execute on function public.get_story_groups()                from anon;
revoke execute on function public.on_call_session_created()         from anon;
revoke execute on function public.calls_prevent_terminal_overwrite() from anon;

commit;

-- Verification after applying (must return zero rows):
--   select p.proname
--     from pg_proc p join pg_namespace n on n.oid = p.pronamespace
--    where n.nspname = 'public'
--      and p.proname in ('approve_follow_request','delete_my_account','follow_user',
--                        'get_or_create_conversation','set_account_privacy',
--                        'touch_active','get_story_groups')
--      and has_function_privilege('anon', p.oid, 'EXECUTE');

-- Rollback:
--   grant execute on function public.<name>(<args>) to anon;
