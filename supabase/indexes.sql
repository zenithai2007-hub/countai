-- ============================================================================
-- CountAI — indexes (run ONCE, after schema.sql)
--
-- Every app query filters by user_id first (RLS + the worker's upserts), so
-- these composite indexes keep lookups O(log n) even with 50k users × rows.
-- Without them, each sync would scan the whole table and the free tier's
-- tiny compute would crawl.
-- ============================================================================

create index if not exists sessions_user_created_idx
on public.sessions (user_id, created_at desc);

create index if not exists inventory_user_updated_idx
on public.inventory_items (user_id, updated_at desc);
