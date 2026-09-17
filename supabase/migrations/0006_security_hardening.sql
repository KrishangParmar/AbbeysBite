-- ===========================================================================
-- 0006 — security hardening (final pre-handoff audit, 2026-09-13)
--
--  1. Future tables no longer inherit blanket CRUD for `authenticated`
--     (tightens 0005): new tables start locked and get grants deliberately.
--  2. A blocked user can no longer un-block themselves by DELETING the
--     'blocked' friendship row (0004 guarded only INSERT/UPDATE).
--  3. Moderation outcomes stick: a recipe in 'removed'/'under_review' can't
--     be flipped back by its author via a direct PostgREST update.
--  4. notification_log — server-side rate limiting for the send-notification
--     Edge Function (service role only; clients can't read or write it).
--  5. Auto-generated usernames stop embedding the user's email local part
--     (profiles are readable by all authenticated users).
-- ===========================================================================

-- 1 ---------------------------------------------------------------- grants
-- (Must run before any table below so nothing inherits the old default.)
alter default privileges in schema public
  revoke select, insert, update, delete on tables from authenticated;

-- 2 ---------------------------------------------------- friendship deletes
create or replace function public.friendships_delete_guard()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  -- Service-role / SQL maintenance (auth.uid() is null) stays unrestricted.
  if old.status = 'blocked'
     and auth.uid() is not null
     and old.blocked_by is distinct from auth.uid() then
    raise exception 'Only the user who blocked can remove the block.';
  end if;
  return old;
end $$;

create trigger friendships_delete_guard
  before delete on public.friendships
  for each row execute function public.friendships_delete_guard();

-- 3 ---------------------------------------------------- recipe moderation
create or replace function public.recipes_moderation_guard()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is not null
     and old.status in ('removed', 'under_review')
     and new.status is distinct from old.status then
    raise exception 'This recipe is under moderation and can''t be changed right now.';
  end if;
  return new;
end $$;

create trigger recipes_moderation_guard
  before update on public.recipes
  for each row execute function public.recipes_moderation_guard();

-- 4 ------------------------------------------------------ notification log
create table public.notification_log (
  id         bigint generated always as identity primary key,
  sender_id  uuid not null,
  type       text not null,
  created_at timestamptz not null default now()
);

create index notification_log_sender_time
  on public.notification_log (sender_id, created_at desc);

alter table public.notification_log enable row level security;
-- No policies and no grants: service role only, by design.
revoke all on public.notification_log from authenticated, anon;

-- 5 --------------------------------------------------- username generation
create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = public as $$
declare
  base text;
  candidate text;
  n int := 0;
begin
  -- Neutral, non-identifying default (profiles are visible to all members);
  -- users pick their real username during onboarding.
  base := 'member_' || substr(replace(new.id::text, '-', ''), 1, 8);
  candidate := base;
  while exists (select 1 from public.profiles where username = candidate) loop
    n := n + 1;
    candidate := base || n::text;
  end loop;
  insert into public.profiles (id, username) values (new.id, candidate);
  return new;
end $$;
