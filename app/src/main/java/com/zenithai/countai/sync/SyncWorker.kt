package com.zenithai.countai.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.zenithai.countai.data.CountAiDatabase
import com.zenithai.countai.util.Prefs
import io.github.jan_tennert.supabase.postgrest.from
import io.github.jan_tennert.supabase.postgrest.query.filter.eq
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Uploads local metadata to Supabase. NEVER downloads bulk data.
 *
 * What goes up (per row, ~200 bytes):
 * - sessions: id, user_id, created_at, mode, counts_json, thumbnail_url
 * - inventory: id, user_id, name, label, last_count, updated_at
 * - tombstones for rows the user deleted locally
 *
 * What NEVER goes up here: full photos/videos (see [ThumbnailWorker] for the
 * tiny opt-in thumbnails). This metadata-only design is the reason 50k users
 * fit in the free tier.
 *
 * Failure policy: WorkManager retries with exponential backoff; after
 * [SyncManager.MAX_ATTEMPTS] the worker reports success-without-work ("parks")
 * and the next 6-hour cycle picks the rows up again. A stuck worker must
 * never spin forever — that is how you burn a free tier.
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!SupabaseConfig.isConfigured) return Result.success()
        val client = SupabaseClientProvider.get(applicationContext)
            ?: return Result.success()
        val userId = try {
            client.auth.currentUserOrNull()?.id
        } catch (e: Exception) {
            null
        } ?: return Result.success() // signed out → rows wait for next sign-in

        return try {
            val db = CountAiDatabase.getInstance(applicationContext)
            processTombstones(db, client)
            syncSessions(db, client, userId)
            syncInventory(db, client, userId)
            Prefs.setLastSync(applicationContext, System.currentTimeMillis())
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount >= SyncManager.MAX_ATTEMPTS) {
                // Park: leave rows pending; the next periodic cycle retries.
                Result.success()
            } else {
                Result.retry()
            }
        }
    }

    private suspend fun processTombstones(
        db: CountAiDatabase,
        client: io.github.jan_tennert.supabase.SupabaseClient,
    ) {
        val dao = db.pendingDeleteDao()
        val batch = dao.getBatch(50)
        for (t in batch) {
            try {
                client.from(t.tableName).delete {
                    filter { eq("id", t.remoteId) }
                }
                dao.deleteById(t.id)
            } catch (e: Exception) {
                // Leave the tombstone; next cycle retries. RLS guarantees we
                // can only ever delete our own user's rows.
            }
        }
    }

    private suspend fun syncSessions(
        db: CountAiDatabase,
        client: io.github.jan_tennert.supabase.SupabaseClient,
        userId: String,
    ) {
        val dao = db.historyDao()
        val pending = dao.getPending(SyncManager.BATCH_SIZE)
        if (pending.isEmpty()) return
        val remote = pending.map { s ->
            val rid = s.remoteId ?: UUID.randomUUID().toString()
            RemoteSession(
                id = rid,
                userId = userId,
                createdAt = iso(s.createdAt),
                mode = s.mode,
                countsJson = s.countsJson,
                thumbnailUrl = null, // filled by ThumbnailWorker when opted in
            )
        }
        client.from("sessions").upsert(remote)
        for ((local, r) in pending.zip(remote)) {
            dao.markSynced(local.id, r.id)
        }
    }

    private suspend fun syncInventory(
        db: CountAiDatabase,
        client: io.github.jan_tennert.supabase.SupabaseClient,
        userId: String,
    ) {
        val dao = db.inventoryDao()
        val pending = dao.getPending(SyncManager.BATCH_SIZE)
        if (pending.isEmpty()) return
        val remote = pending.map { item ->
            val rid = item.remoteId ?: UUID.randomUUID().toString()
            RemoteInventoryItem(
                id = rid,
                userId = userId,
                name = item.productName,
                label = item.label,
                lastCount = item.lastCount,
                updatedAt = iso(item.updatedAt),
            )
        }
        client.from("inventory_items").upsert(remote)
        for ((local, r) in pending.zip(remote)) {
            dao.markSynced(local.id, r.id)
        }
    }

    companion object {
        private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        /** Thread-safe enough: called from a single worker thread. */
        fun iso(epochMillis: Long): String = synchronized(isoFormat) {
            isoFormat.format(java.util.Date(epochMillis))
        }
    }
}
