-- =====================================================================
-- Auth wizard support — "lets an account exist before the username is chosen"
-- Run once in the Supabase SQL editor (Dashboard -> SQL Editor -> New query).
--
-- Self-sufficient: replaces the function body AND re-asserts the trigger, so it
-- is correct whether or not on_auth_user_created is currently present. Both
-- statements are idempotent. This replaces SQL A from the brief, whose function
-- body would break sign-up (see the note below).
--
-- HARDENED vs. the draft in the brief. public.profiles carries:
--     constraint profiles_username_fmt check (username ~ '^[a-z0-9._]+$')
--     constraint profiles_username_len check (char_length(username) between 3 and 30)
--     username text unique not null
-- The draft inserted raw_user_meta_data->>'username' verbatim, so a username with
-- capitals / spaces / fewer than 3 or more than 30 characters, or a username that
-- a previous account already took, raised an exception *inside the trigger* —
-- which aborts the auth.users INSERT, so the whole sign-up fails with a 500
-- ("Database error saving new user"). Usernames are normalised here and a bad
-- birthday or a duplicate username can no longer break sign-up.
-- =====================================================================

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  uname text;
  fname text;
  bday  date;
begin
  -- 1. username: normalise, then fall back to a UUID-derived name.
  uname := lower(coalesce(new.raw_user_meta_data->>'username', ''));
  uname := regexp_replace(uname, '[^a-z0-9._]', '', 'g');
  uname := trim(both '.' from uname);
  if char_length(uname) < 3 or char_length(uname) > 30 then
    uname := 'user_' || substr(replace(new.id::text, '-', ''), 1, 10);
  end if;

  fname := coalesce(new.raw_user_meta_data->>'full_name', '');

  -- 2. birthday: an unparsable value must not abort the sign-up.
  begin
    bday := nullif(new.raw_user_meta_data->>'birthday', '')::date;
  exception when others then
    bday := null;
  end;

  -- 3. insert; if the username is already taken, retry with the derived name.
  begin
    insert into public.profiles (id, username, full_name, birthday)
    values (new.id, uname, fname, bday)
    on conflict (id) do nothing;
  exception when unique_violation then
    insert into public.profiles (id, username, full_name, birthday)
    values (new.id, 'user_' || substr(replace(new.id::text, '-', ''), 1, 10), fname, bday)
    on conflict (id) do nothing;
  end;

  return new;
end;
$$;

-- Permissions: match supabase/schema.sql (the function is SECURITY DEFINER and is
-- only ever invoked by the trigger, never by a client).
revoke execute on function public.handle_new_user() from public;
revoke execute on function public.handle_new_user() from anon, authenticated;

-- Re-assert the trigger (no-op if it is already correct).
drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();
