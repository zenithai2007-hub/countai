package com.zenithai.countai.repository

import android.content.Context
import com.zenithai.countai.sync.SupabaseConfig
import com.zenithai.countai.sync.SyncManager
import com.zenithai.countai.util.Prefs

/**
 * Cloud sync. Local Room data is the source of truth; this only PUSHES
 * lightweight rows up to Supabase (see [com.zenithai.countai.sync.SyncWorker]).
 *
 * When Supabase isn't configured, [NoOpCloudSyncRepository] explains itself
 * and the app keeps working fully offline.
 */
interface CloudSyncRepository {

    /** Push local changes now (debounced to 1 run / 60s). */
    suspend fun syncNow(): Result<Unit>

    /** Last successful sync timestamp, 0 = never. */
    suspend fun lastSyncedAt(): Long
}

/** Placeholder used until cloud sync is configured. */
class NoOpCloudSyncRepository : CloudSyncRepository {

    override suspend fun syncNow(): Result<Unit> =
        Result.failure(
            UnsupportedOperationException(
                "Cloud sync isn't configured in this build. " +
                    "Your history is stored safely on this device."
            )
        )

    override suspend fun lastSyncedAt(): Long = 0L
}

class SupabaseSyncRepository(private val context: Context) : CloudSyncRepository {

    override suspend fun syncNow(): Result<Unit> {
        if (!SupabaseConfig.isConfigured) {
            return Result.failure(
                UnsupportedOperationException("Cloud sync isn't configured in this build.")
            )
        }
        val accepted = SyncManager.syncNow(context.applicationContext)
        return if (accepted) {
            Result.success(Unit)
        } else {
            Result.failure(
                IllegalStateException("Just synced — please wait a minute and try again.")
            )
        }
    }

    override suspend fun lastSyncedAt(): Long = Prefs.lastSync(context.applicationContext)
}
