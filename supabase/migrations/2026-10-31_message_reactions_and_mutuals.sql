-- Two objects the chat screen probes for and reports as "unavailable" when absent.
-- Both are additive: no existing table, policy or grant changes behaviour besides enabling the
-- feature. RLS stays enabled on the new table.

-- 1. Message reactions -------------------------------------------------------
create table if not exists public.message_reactions (
    message_id uuid        not null references public.messages (id) on delete cascade,
    user_id    uuid        not null references public.profiles (id) on delete cascade,
    emoji      text        not null,
    created_at timestamptz not null default now(),
    primary key (message_id, user_id)
);

create index if not exists message_reactions_message_idx
    on public.message_reactions (message_id);

alter table public.message_reactions enable row level security;

-- Anyone who can see the message can see its reactions.
drop policy if exists message_reactions_read on public.message_reactions;
create policy message_reactions_read on public.message_reactions
    for select using (
        public.is_conversation_member(
            (select m.conversation_id from public.messages m where m.id = message_id),
            public.request_uid()
        )
    );

-- One reaction per user per message, and only from a participant.
drop policy if exists message_reactions_insert on public.message_reactions;
create policy message_reactions_insert on public.message_reactions
    for insert with check (
        user_id = public.request_uid()
        and public.is_conversation_member(
            (select m.conversation_id from public.messages m where m.id = message_id),
            public.request_uid()
        )
    );

drop policy if exists message_reactions_update on public.message_reactions;
create policy message_reactions_update on public.message_reactions
    for update using (user_id = public.request_uid())
    with check (user_id = public.request_uid());

drop policy if exists message_reactions_delete on public.message_reactions;
create policy message_reactions_delete on public.message_reactions
    for delete using (user_id = public.request_uid());

grant select, insert, update, delete on public.message_reactions to authenticated;

-- 2. Mutual followings -------------------------------------------------------
-- Returns the profiles that both the caller and p_other follow. The chat intro shows the first few
-- ("You both follow X and N others"), so the ordering is stable and the list bounded.
create or replace function public.mutual_followings(p_other uuid)
returns setof public.profiles
language sql
stable
security definer
set search_path = public
as $$
    select p.*
    from public.profiles p
    where exists (
        select 1
        from public.follows me
        join public.follows them
          on them.follower_id = p.id
        where me.follower_id = public.request_uid()
          and me.following_id = p.id
          and them.following_id = p_other
    )
    order by p.username
    limit 25;
$$;

comment on function public.mutual_followings(uuid) is
    'Profiles followed by both the caller and p_other (chat intro line).';

grant execute on function public.mutual_followings(uuid) to authenticated;
