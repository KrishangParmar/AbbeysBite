-- ===========================================================================
-- Abbey's Bite — storage buckets + policies
-- Versioned migration 0002.
-- ===========================================================================

-- Public avatars (small, CDN-cacheable)
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('avatars', 'avatars', true, 2097152, array['image/jpeg','image/png','image/webp'])
on conflict (id) do nothing;

-- Private meal photos (owner-only, served via signed URLs)
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('meal-photos-private', 'meal-photos-private', false, 5242880, array['image/jpeg','image/png','image/webp'])
on conflict (id) do nothing;

-- Public recipe covers
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('recipe-images-public', 'recipe-images-public', true, 5242880, array['image/jpeg','image/png','image/webp'])
on conflict (id) do nothing;

-- Objects are stored under <user_id>/<file>; the first path segment is the
-- owner and the policies below enforce it.

create policy "avatar images are readable"
  on storage.objects for select
  using (bucket_id = 'avatars');

create policy "users manage own avatars"
  on storage.objects for all to authenticated
  using (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text)
  with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);

create policy "recipe images are readable"
  on storage.objects for select
  using (bucket_id = 'recipe-images-public');

create policy "users manage own recipe images"
  on storage.objects for all to authenticated
  using (bucket_id = 'recipe-images-public' and (storage.foldername(name))[1] = auth.uid()::text)
  with check (bucket_id = 'recipe-images-public' and (storage.foldername(name))[1] = auth.uid()::text);

-- Meal photos: fully private to the owner (signed URLs only).
create policy "users read own meal photos"
  on storage.objects for select to authenticated
  using (bucket_id = 'meal-photos-private' and (storage.foldername(name))[1] = auth.uid()::text);

create policy "users write own meal photos"
  on storage.objects for insert to authenticated
  with check (bucket_id = 'meal-photos-private' and (storage.foldername(name))[1] = auth.uid()::text);

create policy "users delete own meal photos"
  on storage.objects for delete to authenticated
  using (bucket_id = 'meal-photos-private' and (storage.foldername(name))[1] = auth.uid()::text);
