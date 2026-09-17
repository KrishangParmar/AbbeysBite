-- ===========================================================================
-- Abbey's Bite — AI usage log (server-side rate limiting)
-- Versioned migration 0003. Only Edge Functions (service role) touch this;
-- content is never stored — just user, task and timestamp.
-- ===========================================================================

create table public.ai_usage_log (
  id         uuid primary key default uuid_generate_v4(),
  user_id    uuid not null references public.profiles (id) on delete cascade,
  task       text not null,
  created_at timestamptz not null default now()
);

create index ai_usage_log_user_task_time
  on public.ai_usage_log (user_id, task, created_at desc);

alter table public.ai_usage_log enable row level security;
-- No policies: clients can neither read nor write; service role bypasses RLS.
