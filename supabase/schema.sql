-- ============================================================================
-- CountAI — Supabase schema (run ONCE in the Supabase dashboard SQL editor)
-- Dashboard → SQL Editor → New query → paste → Run.
--
-- Design notes (why 50k users fit the free tier):
-- * Tables store METADATA ONLY: ids, timestamps, counts. No photos/blobs.
--   A session row is ~200 bytes.
-- * `user_id` on every row + RLS (rls.sql) so users can only touch their own.
-- * `thumbnails` is a PRIVATE storage bucket for ~100KB thumbnails.
-- ============================================================================

-- One saved counting session (numbers only).
create table if not exists public.sessions (
    id uuid primary key,
    user_id uuid not null references auth.users (id) on delete cascade,
    created_at timestamptz not null default now(),
    mode text not null default 'live',
    counts_json text not null default '{}',
    -- Storage path like "<user_id>/<session_id>.jpg" in the `thumbnails`
    -- bucket, or null when the user never enabled cloud backup.
    thumbnail_url text
);

-- One named product in Smart Inventory Mode.
create table if not exists public.inventory_items (
    id uuid primary key,
    user_id uuid not null references auth.users (id) on delete cascade,
    name text not null,
    label text not null,
    last_count integer not null default 0,
    updated_at timestamptz not null default now()
);

-- Private bucket for the tiny (~100KB) thumbnails. Create it if the
-- `storage.buckets` insert below is not permitted in your project; otherwise
-- create it in Dashboard → Storage → New bucket (name: thumbnails, PRIVATE).
insert into storage.buckets (id, name, public)
values ('thumbnails', 'thumbnails', false)
on conflict (id) do nothing;
