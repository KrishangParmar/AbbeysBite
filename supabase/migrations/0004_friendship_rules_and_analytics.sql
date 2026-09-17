-- ===========================================================================
-- Abbey's Bite — migration 0004
--  A. Server-side friendship state-machine enforcement (never UI-only)
--  B. First-party privacy-conscious analytics events table
-- ===========================================================================

-- --------------------------------------------------------------------------
-- A. FRIENDSHIP RULES
-- The previous policy let either participant UPDATE rows arbitrarily. Rules
-- are now enforced in the database regardless of client behavior:
--   * only the ADDRESSEE may accept/decline a pending request
--   * only the REQUESTER may cancel (→ declined) a pending request
--   * either participant may block; only the blocker may unblock
--   * accepted → declined (unfriend) allowed for either participant
--   * participant ids are immutable after insert
--   * no self-friendship; no duplicate pair rows in either direction
-- --------------------------------------------------------------------------

-- Normalize any legacy duplicate pairs before adding the symmetric guard.
create unique index if not exists friendships_pair_symmetric
  on public.friendships (least(requester_id, addressee_id),
                         greatest(requester_id, addressee_id));

create or replace function public.enforce_friendship_transition()
returns trigger language plpgsql security definer set search_path = public as $$
declare
  actor uuid := auth.uid();
begin
  -- Service role (webhooks/admin) bypasses transition checks.
  if actor is null then
    return new;
  end if;

  if new.requester_id <> old.requester_id or new.addressee_id <> old.addressee_id then
    raise exception 'friendship participants are immutable';
  end if;

  if new.status = old.status and coalesce(new.blocked_by, '00000000-0000-0000-0000-000000000000')
       = coalesce(old.blocked_by, '00000000-0000-0000-0000-000000000000') then
    return new; -- no-op update (updated_at etc.)
  end if;

  -- BLOCK: either participant, and blocked_by must be the actor.
  if new.status = 'blocked' then
    if actor not in (old.requester_id, old.addressee_id) then
      raise exception 'only participants can block';
    end if;
    if new.blocked_by is distinct from actor then
      raise exception 'blocked_by must be the blocking user';
    end if;
    return new;
  end if;

  -- UNBLOCK: blocked → declined, only by whoever blocked.
  if old.status = 'blocked' then
    if new.status <> 'declined' then
      raise exception 'a block can only transition to declined (unblock)';
    end if;
    if old.blocked_by is distinct from actor then
      raise exception 'only the user who blocked can unblock';
    end if;
    if new.blocked_by is not null then
      raise exception 'blocked_by must clear on unblock';
    end if;
    return new;
  end if;

  -- PENDING transitions.
  if old.status = 'pending' then
    if new.status = 'accepted' then
      if actor <> old.addressee_id then
        raise exception 'only the request recipient can accept';
      end if;
      return new;
    end if;
    if new.status = 'declined' then
      if actor not in (old.requester_id, old.addressee_id) then
        raise exception 'only participants can decline/cancel';
      end if;
      return new;
    end if;
    raise exception 'invalid transition from pending to %', new.status;
  end if;

  -- ACCEPTED → DECLINED (unfriend) by either participant.
  if old.status = 'accepted' and new.status = 'declined' then
    if actor not in (old.requester_id, old.addressee_id) then
      raise exception 'only participants can unfriend';
    end if;
    return new;
  end if;

  -- DECLINED rows are re-created via a fresh request (insert), not updated.
  raise exception 'invalid friendship transition % -> %', old.status, new.status;
end $$;

drop trigger if exists friendships_transition_guard on public.friendships;
create trigger friendships_transition_guard
  before update on public.friendships
  for each row execute function public.enforce_friendship_transition();

-- Inserts: requester must be the actor; initial status pending (or blocked by
-- the actor); target must not be blocked either way.
create or replace function public.enforce_friendship_insert()
returns trigger language plpgsql security definer set search_path = public as $$
declare
  actor uuid := auth.uid();
begin
  if actor is null then
    return new; -- service role
  end if;
  if new.requester_id <> actor then
    raise exception 'requests must be sent as yourself';
  end if;
  if new.status = 'blocked' then
    if new.blocked_by is distinct from actor then
      raise exception 'blocked_by must be the blocking user';
    end if;
  elsif new.status <> 'pending' then
    raise exception 'new friendships must start as pending';
  end if;
  if public.is_blocked(new.requester_id, new.addressee_id) then
    raise exception 'interaction blocked between these users';
  end if;
  return new;
end $$;

drop trigger if exists friendships_insert_guard on public.friendships;
create trigger friendships_insert_guard
  before insert on public.friendships
  for each row execute function public.enforce_friendship_insert();

-- --------------------------------------------------------------------------
-- B. ANALYTICS EVENTS (first-party, privacy-conscious)
-- Coarse product events only. NO raw chat content, NO meal photos, NO
-- health/allergy data, NO tokens. Clients insert their own events; nobody
-- reads them back through the API (dashboards use the SQL editor).
-- --------------------------------------------------------------------------

create table public.analytics_events (
  id         uuid primary key default uuid_generate_v4(),
  user_id    uuid references public.profiles (id) on delete set null,
  event      text not null check (char_length(event) between 1 and 64),
  properties jsonb not null default '{}'::jsonb check (pg_column_size(properties) < 2048),
  platform   text,
  app_version text,
  created_at timestamptz not null default now()
);

create index analytics_events_event_time on public.analytics_events (event, created_at desc);

alter table public.analytics_events enable row level security;

create policy "users write own events" on public.analytics_events
  for insert to authenticated
  with check (user_id = auth.uid() or user_id is null);
-- No select/update/delete policies: events are write-only from clients.
