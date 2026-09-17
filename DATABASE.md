# Database

PostgreSQL on Supabase. All migrations are versioned SQL in
`supabase/migrations/` and must be applied in order. Every table uses UUID
primary keys and `created_at`/`updated_at` timestamps where meaningful
(`updated_at` maintained by trigger).

### Migration history

- `0001_initial_schema.sql` — core schema + RLS.
- `0002_storage.sql` — storage buckets + policies.
- `0003_ai_usage_log.sql` — gateway rate-limit counters.
- `0004_friendship_rules_and_analytics.sql` — friendship state machine now
  enforced by **DB triggers** (not app-side only), plus the `analytics_events`
  table.
- `0005_role_grants.sql` — role grants required for client writes. **Apply
  this or every client write fails with Postgres error `42501`.**
- `0006_security_hardening.sql` — recipe moderation guard, a friendship delete
  guard, the `notification_log` table, and neutral auto-generated usernames.

## Entity overview

```
auth.users ─1:1─ profiles ─1:1─ user_preferences
                    │      ─1:1─ progress_sharing_preferences
                    │      ─1:1─ revenuecat_customers
                    │      ─1:N─ pantry_items
                    │      ─1:N─ meal_entries            (journal; analysis as jsonb)
                    │      ─1:N─ recipes ─1:N─ recipe_ingredients
                    │                    ─1:N─ recipe_steps
                    │                    ─N:M─ recipe_likes / saved_recipes
                    │      ─N:M─ friendships (requester/addressee + status)
                    │      ─1:N─ reports
                    └────── 1:N─ ai_usage_log            (rate limiting, no content)
```

### Tables

| Table | Purpose | Notable columns |
| --- | --- | --- |
| `profiles` | Public identity; auto-created by trigger on signup with a generated unique username | `username` (unique, `^[a-z0-9_]{3,24}$`), `display_name`, `avatar_url`, `bio` |
| `user_preferences` | Private food preferences | `goals[]`, `diet_preference`, `avoid_foods[]`, `cooking_setup`, `onboarding_completed` |
| `pantry_items` | Private pantry | unique `(user_id, name)` |
| `meal_entries` | Journal entries | `meal_type`, `photo_path` (private bucket path), `entry_date` (local calendar date), `analysis` jsonb (the full MealAnalysis), `satisfaction` |
| `recipes` | Community recipes | `status` draft/published/under_review/removed, denormalized `like_count`/`save_count` (trigger-maintained), `total_minutes` for fast filtering |
| `recipe_ingredients` / `recipe_steps` | Ordered relations | `sort_order` / `step_number` |
| `recipe_likes` / `saved_recipes` | Composite-PK join tables — the PK makes duplicate likes structurally impossible | |
| `friendships` | One row per user pair | `status` pending/accepted/declined/blocked, `blocked_by`, unique `(requester_id, addressee_id)` |
| `progress_sharing_preferences` | Opt-in sharing toggles, all default **false** | |
| `reports` | UGC moderation queue | `target_type` recipe/user, `status` open→resolved |
| `revenuecat_customers` | Server-side entitlement mirror (RevenueCat webhook) | `is_premium` |
| `ai_usage_log` | Per-user per-task counters for gateway rate limiting; **no content stored** | |
| `analytics_events` | Product analytics event log (added in `0004`) | |
| `notification_log` | Server-side notification delivery log (added in `0006`) | |

## Row Level Security

RLS is enabled on every table; the client uses only the anon key + user JWT.
UI checks are never the authorization mechanism.

Guarantees, as implemented in `0001_initial_schema.sql`:

- **Own-data tables** (`user_preferences`, `pantry_items`, `meal_entries`,
  `progress_sharing_preferences`): `user_id = auth.uid()` for every verb.
- **Profiles**: readable by signed-in users (community needs it) *except*
  across a block (`is_blocked()` helper); only the owner updates.
- **Recipes**: anyone reads `published` (block-fenced); only the author
  inserts/updates/deletes; relations follow the parent recipe's visibility.
- **Likes/saves**: only as yourself, only on published recipes; likes on a
  blocked author's recipes are rejected. Composite PKs prevent duplicates;
  counters update via `security definer` triggers.
- **Friendships**: only participants can read/update; requests can't be sent
  across a block. Legal state transitions are enforced by `FriendshipRules`
  app-side **and** by DB triggers server-side (`0004`), with a delete guard
  added in `0006`; progress access is additionally re-checked in the
  `friend-progress` function.
- **Friend progress** is *never* computed client-side: the `friend-progress`
  Edge Function (service role) checks ACCEPTED status **and** each per-stat
  opt-in before returning anything.
- **`ai_usage_log`**: no client policies at all — only Edge Functions touch it.
- Service-role credentials exist only in Edge Function secrets, never in the
  app.

## Storage (migration `0002_storage.sql`)

| Bucket | Visibility | Path convention | Notes |
| --- | --- | --- | --- |
| `avatars` | public | `<user_id>/<file>.jpg` | 2 MB limit |
| `recipe-images-public` | public | `<user_id>/<file>.jpg` | 5 MB limit |
| `meal-photos-private` | **private** | `<user_id>/<file>.jpg` | owner-only via RLS; the app requests 12h signed URLs |

Policies key off the first path segment (`storage.foldername(name)[1] =
auth.uid()`), so users can only ever write/read inside their own folder.

## Account deletion

`delete-account` Edge Function: removes the user's storage objects in all
three buckets, then `auth.admin.deleteUser()` — every table cascades from
`auth.users` via FK `on delete cascade`. Meets Apple's account-deletion
requirement.

## Client-side JSON contracts

`meal_entries.analysis` stores the full `MealAnalysis` JSON exactly as the AI
returned it (post-validation). Kotlin models use `@SerialName` snake_case and
decode with `ignoreUnknownKeys`, so the schema can evolve additively without
breaking older clients.
