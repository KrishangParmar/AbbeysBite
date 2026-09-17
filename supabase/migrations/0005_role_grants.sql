-- ===========================================================================
-- Abbey's Bite — migration 0005: explicit role grants
--
-- This project (fresh Postgres 17 template) does not implicitly grant table
-- privileges on developer-created tables to the API roles, so RLS policies
-- alone returned 42501. Grants below are INTENTIONALLY minimal per table;
-- RLS remains the row-level authority everywhere.
-- ===========================================================================

grant usage on schema public to anon, authenticated, service_role;

-- Standard user-owned tables: full CRUD for signed-in users, rows constrained
-- by their RLS policies.
grant select, insert, update, delete on
  public.profiles,
  public.user_preferences,
  public.pantry_items,
  public.meal_entries,
  public.recipes,
  public.recipe_ingredients,
  public.recipe_steps,
  public.recipe_likes,
  public.saved_recipes,
  public.friendships,
  public.progress_sharing_preferences,
  public.reports
to authenticated;

-- Billing mirror: read own row only (writes are service-role webhook only).
grant select on public.revenuecat_customers to authenticated;

-- Analytics: write-only from clients.
grant insert on public.analytics_events to authenticated;

-- ai_usage_log: deliberately NO client grants (Edge Functions/service only).

-- Anonymous role gets nothing in public (the app always authenticates).

-- service_role bypasses RLS but still needs table privileges:
grant all on all tables in schema public to service_role;

-- Future tables created by the postgres role: default to the same posture.
alter default privileges in schema public
  grant select, insert, update, delete on tables to authenticated;
alter default privileges in schema public
  grant all on tables to service_role;
