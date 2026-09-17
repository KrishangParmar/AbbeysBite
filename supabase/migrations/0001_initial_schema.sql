-- ===========================================================================
-- Abbey's Bite — initial schema
-- Versioned migration 0001. Requires Supabase (PostgreSQL 15+, auth schema).
-- ===========================================================================

create extension if not exists "uuid-ossp";

-- ---------------------------------------------------------------- utilities

create or replace function public.set_updated_at()
returns trigger language plpgsql as $$
begin
  new.updated_at = now();
  return new;
end $$;

-- ---------------------------------------------------------------- profiles

create table public.profiles (
  id          uuid primary key references auth.users (id) on delete cascade,
  username    text not null unique check (username ~ '^[a-z0-9_]{3,24}$'),
  display_name text,
  avatar_url  text,
  bio         text check (char_length(bio) <= 200),
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

create trigger profiles_updated_at before update on public.profiles
  for each row execute function public.set_updated_at();

-- Auto-create a profile row (with a generated unique username) on signup.
create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = public as $$
declare
  base text;
  candidate text;
  n int := 0;
begin
  base := lower(regexp_replace(split_part(new.email, '@', 1), '[^a-z0-9_]', '', 'g'));
  if base is null or char_length(base) < 3 then
    base := 'user';
  end if;
  base := left(base, 20);
  candidate := base;
  while exists (select 1 from public.profiles where username = candidate) loop
    n := n + 1;
    candidate := base || n::text;
  end loop;
  insert into public.profiles (id, username) values (new.id, candidate);
  return new;
end $$;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- ---------------------------------------------------------------- preferences

create table public.user_preferences (
  user_id     uuid primary key references public.profiles (id) on delete cascade,
  goals       text[] not null default '{}',
  diet_preference text not null default 'no_preference',
  avoid_foods text[] not null default '{}',
  cooking_setup text not null default 'full_kitchen',
  onboarding_completed boolean not null default false,
  updated_at  timestamptz not null default now()
);

create trigger user_preferences_updated_at before update on public.user_preferences
  for each row execute function public.set_updated_at();

create table public.pantry_items (
  id         uuid primary key default uuid_generate_v4(),
  user_id    uuid not null references public.profiles (id) on delete cascade,
  name       text not null check (char_length(name) between 1 and 80),
  category   text,
  created_at timestamptz not null default now(),
  unique (user_id, name)
);

-- ---------------------------------------------------------------- journal

create table public.meal_entries (
  id         uuid primary key default uuid_generate_v4(),
  user_id    uuid not null references public.profiles (id) on delete cascade,
  meal_type  text not null check (meal_type in ('breakfast','lunch','dinner','snack')),
  meal_name  text not null default '',
  photo_path text,
  eaten_at   timestamptz not null default now(),
  entry_date date not null,
  note       text check (char_length(note) <= 500),
  satisfaction text check (satisfaction in ('still_hungry','satisfied','very_satisfied')),
  analysis   jsonb,
  created_at timestamptz not null default now()
);

create index meal_entries_user_date on public.meal_entries (user_id, entry_date desc);

-- ---------------------------------------------------------------- recipes

create table public.recipes (
  id          uuid primary key default uuid_generate_v4(),
  author_id   uuid not null references public.profiles (id) on delete cascade,
  title       text not null check (char_length(title) between 3 and 90),
  description text not null default '' check (char_length(description) <= 600),
  cover_image_url text,
  prep_minutes int not null default 0 check (prep_minutes between 0 and 1440),
  cook_minutes int not null default 0 check (cook_minutes between 0 and 1440),
  total_minutes int not null default 0,
  difficulty  text not null default 'easy' check (difficulty in ('easy','medium','involved')),
  servings    int not null default 1 check (servings between 1 and 99),
  cuisine     text,
  tags        text[] not null default '{}',
  diet_tags   text[] not null default '{}',
  youtube_url text,
  tips        text,
  status      text not null default 'draft' check (status in ('draft','published','under_review','removed')),
  like_count  int not null default 0,
  save_count  int not null default 0,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

create index recipes_status_created on public.recipes (status, created_at desc);
create index recipes_author on public.recipes (author_id);

create trigger recipes_updated_at before update on public.recipes
  for each row execute function public.set_updated_at();

create table public.recipe_ingredients (
  id         uuid primary key default uuid_generate_v4(),
  recipe_id  uuid not null references public.recipes (id) on delete cascade,
  name       text not null,
  quantity   text,
  unit       text,
  sort_order int not null default 0,
  optional   boolean not null default false,
  substitution text
);

create index recipe_ingredients_recipe on public.recipe_ingredients (recipe_id, sort_order);

create table public.recipe_steps (
  id          uuid primary key default uuid_generate_v4(),
  recipe_id   uuid not null references public.recipes (id) on delete cascade,
  step_number int not null,
  instruction text not null
);

create index recipe_steps_recipe on public.recipe_steps (recipe_id, step_number);

create table public.recipe_likes (
  recipe_id  uuid not null references public.recipes (id) on delete cascade,
  user_id    uuid not null references public.profiles (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (recipe_id, user_id)
);

create table public.saved_recipes (
  recipe_id  uuid not null references public.recipes (id) on delete cascade,
  user_id    uuid not null references public.profiles (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (recipe_id, user_id)
);

-- Keep denormalized counters in sync.
create or replace function public.bump_like_count()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if tg_op = 'INSERT' then
    update public.recipes set like_count = like_count + 1 where id = new.recipe_id;
    return new;
  else
    update public.recipes set like_count = greatest(like_count - 1, 0) where id = old.recipe_id;
    return old;
  end if;
end $$;

create trigger recipe_likes_bump
  after insert or delete on public.recipe_likes
  for each row execute function public.bump_like_count();

create or replace function public.bump_save_count()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if tg_op = 'INSERT' then
    update public.recipes set save_count = save_count + 1 where id = new.recipe_id;
    return new;
  else
    update public.recipes set save_count = greatest(save_count - 1, 0) where id = old.recipe_id;
    return old;
  end if;
end $$;

create trigger saved_recipes_bump
  after insert or delete on public.saved_recipes
  for each row execute function public.bump_save_count();

-- ---------------------------------------------------------------- social

create table public.friendships (
  id           uuid primary key default uuid_generate_v4(),
  requester_id uuid not null references public.profiles (id) on delete cascade,
  addressee_id uuid not null references public.profiles (id) on delete cascade,
  status       text not null default 'pending'
               check (status in ('pending','accepted','declined','blocked')),
  blocked_by   uuid references public.profiles (id) on delete set null,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now(),
  check (requester_id <> addressee_id),
  unique (requester_id, addressee_id)
);

create index friendships_addressee on public.friendships (addressee_id, status);
create index friendships_requester on public.friendships (requester_id, status);

create trigger friendships_updated_at before update on public.friendships
  for each row execute function public.set_updated_at();

create table public.progress_sharing_preferences (
  user_id uuid primary key references public.profiles (id) on delete cascade,
  share_active_days boolean not null default false,
  share_meals_logged boolean not null default false,
  share_plant_variety boolean not null default false,
  share_recipes_published boolean not null default false,
  share_recipe_achievements boolean not null default false,
  updated_at timestamptz not null default now()
);

create trigger sharing_prefs_updated_at before update on public.progress_sharing_preferences
  for each row execute function public.set_updated_at();

-- ---------------------------------------------------------------- moderation

create table public.reports (
  id          uuid primary key default uuid_generate_v4(),
  reporter_id uuid not null references public.profiles (id) on delete cascade,
  target_type text not null check (target_type in ('recipe','user')),
  target_id   uuid not null,
  reason      text not null,
  details     text,
  status      text not null default 'open'
              check (status in ('open','reviewing','resolved','dismissed')),
  created_at  timestamptz not null default now()
);

create index reports_status on public.reports (status, created_at desc);

-- ---------------------------------------------------------------- billing

create table public.revenuecat_customers (
  user_id       uuid primary key references public.profiles (id) on delete cascade,
  rc_app_user_id text,
  is_premium    boolean not null default false,
  updated_at    timestamptz not null default now()
);

create trigger revenuecat_customers_updated_at before update on public.revenuecat_customers
  for each row execute function public.set_updated_at();

-- ===========================================================================
-- ROW LEVEL SECURITY
-- UI checks are never trusted; these policies are the actual authorization.
-- ===========================================================================

alter table public.profiles enable row level security;
alter table public.user_preferences enable row level security;
alter table public.pantry_items enable row level security;
alter table public.meal_entries enable row level security;
alter table public.recipes enable row level security;
alter table public.recipe_ingredients enable row level security;
alter table public.recipe_steps enable row level security;
alter table public.recipe_likes enable row level security;
alter table public.saved_recipes enable row level security;
alter table public.friendships enable row level security;
alter table public.progress_sharing_preferences enable row level security;
alter table public.reports enable row level security;
alter table public.revenuecat_customers enable row level security;

-- Two users are blocked (either direction) — used to fence off interactions.
create or replace function public.is_blocked(a uuid, b uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.friendships f
    where f.status = 'blocked'
      and ((f.requester_id = a and f.addressee_id = b)
        or (f.requester_id = b and f.addressee_id = a))
  );
$$;

-- ------------------------------------------------ profiles
-- Readable by any signed-in user (needed for community/social), unless blocked.
create policy "profiles are readable" on public.profiles
  for select to authenticated
  using (not public.is_blocked(auth.uid(), id));

create policy "users update own profile" on public.profiles
  for update to authenticated
  using (id = auth.uid())
  with check (id = auth.uid());

-- ------------------------------------------------ preferences / pantry / journal
-- Strictly private to the owner.
create policy "own preferences" on public.user_preferences
  for all to authenticated
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create policy "own pantry" on public.pantry_items
  for all to authenticated
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create policy "own meals" on public.meal_entries
  for all to authenticated
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create policy "own sharing prefs" on public.progress_sharing_preferences
  for all to authenticated
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

-- ------------------------------------------------ recipes
create policy "published recipes readable" on public.recipes
  for select to authenticated
  using (
    (status = 'published' and not public.is_blocked(auth.uid(), author_id))
    or author_id = auth.uid()
  );

create policy "authors insert recipes" on public.recipes
  for insert to authenticated
  with check (author_id = auth.uid());

create policy "authors update recipes" on public.recipes
  for update to authenticated
  using (author_id = auth.uid())
  with check (author_id = auth.uid());

create policy "authors delete recipes" on public.recipes
  for delete to authenticated
  using (author_id = auth.uid());

create policy "ingredients readable with recipe" on public.recipe_ingredients
  for select to authenticated
  using (exists (
    select 1 from public.recipes r
    where r.id = recipe_id
      and (r.status = 'published' or r.author_id = auth.uid())
  ));

create policy "authors manage ingredients" on public.recipe_ingredients
  for all to authenticated
  using (exists (select 1 from public.recipes r where r.id = recipe_id and r.author_id = auth.uid()))
  with check (exists (select 1 from public.recipes r where r.id = recipe_id and r.author_id = auth.uid()));

create policy "steps readable with recipe" on public.recipe_steps
  for select to authenticated
  using (exists (
    select 1 from public.recipes r
    where r.id = recipe_id
      and (r.status = 'published' or r.author_id = auth.uid())
  ));

create policy "authors manage steps" on public.recipe_steps
  for all to authenticated
  using (exists (select 1 from public.recipes r where r.id = recipe_id and r.author_id = auth.uid()))
  with check (exists (select 1 from public.recipes r where r.id = recipe_id and r.author_id = auth.uid()));

-- ------------------------------------------------ likes & saves
create policy "likes readable" on public.recipe_likes
  for select to authenticated using (true);

create policy "users like as themselves" on public.recipe_likes
  for insert to authenticated
  with check (
    user_id = auth.uid()
    and exists (
      select 1 from public.recipes r
      where r.id = recipe_id and r.status = 'published'
        and not public.is_blocked(auth.uid(), r.author_id)
    )
  );

create policy "users remove own likes" on public.recipe_likes
  for delete to authenticated using (user_id = auth.uid());

create policy "own saves readable" on public.saved_recipes
  for select to authenticated using (user_id = auth.uid());

create policy "users save as themselves" on public.saved_recipes
  for insert to authenticated
  with check (
    user_id = auth.uid()
    and exists (select 1 from public.recipes r where r.id = recipe_id and r.status = 'published')
  );

create policy "users remove own saves" on public.saved_recipes
  for delete to authenticated using (user_id = auth.uid());

-- ------------------------------------------------ friendships
create policy "participants read friendships" on public.friendships
  for select to authenticated
  using (requester_id = auth.uid() or addressee_id = auth.uid());

create policy "users send requests" on public.friendships
  for insert to authenticated
  with check (
    requester_id = auth.uid()
    and not public.is_blocked(auth.uid(), addressee_id)
  );

-- Participants may update status; legal transitions are enforced app-side and
-- the blocked state can only be set by a participant on their own row pair.
create policy "participants update friendships" on public.friendships
  for update to authenticated
  using (requester_id = auth.uid() or addressee_id = auth.uid())
  with check (requester_id = auth.uid() or addressee_id = auth.uid());

create policy "participants delete friendships" on public.friendships
  for delete to authenticated
  using (requester_id = auth.uid() or addressee_id = auth.uid());

-- ------------------------------------------------ reports
create policy "users file reports" on public.reports
  for insert to authenticated
  with check (reporter_id = auth.uid());

create policy "reporters read own reports" on public.reports
  for select to authenticated
  using (reporter_id = auth.uid());

-- ------------------------------------------------ revenuecat
create policy "own billing row" on public.revenuecat_customers
  for select to authenticated
  using (user_id = auth.uid());
-- Writes happen only via service-role (webhook/edge function) — no policy needed.
