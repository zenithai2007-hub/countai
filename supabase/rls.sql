-- ============================================================================
-- CountAI — Row Level Security (run ONCE, after schema.sql)
--
-- Every policy below enforces ONE rule: an authenticated user can only
-- SELECT / INSERT / UPDATE / DELETE rows where auth.uid() = user_id.
-- Nobody can ever read or change another user's counting history — not even
-- by guessing a row id. This is the security boundary that makes the app
-- safe for 50k strangers on one shared database.
-- ============================================================================

alter table public.sessions enable row level security;
alter table public.inventory_items enable row level security;

-- ---- sessions ----

-- A user can read only their own sessions.
create policy "users read own sessions"
on public.sessions for select
to authenticated
using (auth.uid() = user_id);

-- A user can insert only rows that belong to them.
create policy "users insert own sessions"
on public.sessions for insert
to authenticated
with check (auth.uid() = user_id);

-- A user can update (e.g. thumbnail_url) only their own sessions.
create policy "users update own sessions"
on public.sessions for update
to authenticated
using (auth.uid() = user_id)
with check (auth.uid() = user_id);

-- A user can delete only their own sessions.
create policy "users delete own sessions"
on public.sessions for delete
to authenticated
using (auth.uid() = user_id);

-- ---- inventory_items ----

-- A user can read only their own inventory.
create policy "users read own inventory"
on public.inventory_items for select
to authenticated
using (auth.uid() = user_id);

-- A user can insert only their own products.
create policy "users insert own inventory"
on public.inventory_items for insert
to authenticated
with check (auth.uid() = user_id);

-- A user can update only their own products.
create policy "users update own inventory"
on public.inventory_items for update
to authenticated
using (auth.uid() = user_id)
with check (auth.uid() = user_id);

-- A user can delete only their own products.
create policy "users delete own inventory"
on public.inventory_items for delete
to authenticated
using (auth.uid() = user_id);

-- ---- thumbnails bucket (private storage) ----
-- Users may upload / read / delete only files under their own folder:
--   thumbnails/<their-user-id>/...

create policy "users upload own thumbnails"
on storage.objects for insert
to authenticated
with check (
    bucket_id = 'thumbnails'
    and (storage.foldername(name))[1] = auth.uid()::text
);

create policy "users read own thumbnails"
on storage.objects for select
to authenticated
using (
    bucket_id = 'thumbnails'
    and (storage.foldername(name))[1] = auth.uid()::text
);

create policy "users delete own thumbnails"
on storage.objects for delete
to authenticated
using (
    bucket_id = 'thumbnails'
    and (storage.foldername(name))[1] = auth.uid()::text
);
