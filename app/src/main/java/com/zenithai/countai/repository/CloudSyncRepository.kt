package com.zenithai.countai.repository

/**
 * FUTURE — cloud sync, team workspaces and the API layer.
 *
 * Local history ([com.zenithai.countai.data.SessionRepository]) stays the
 * source of truth; a future implementation of this interface will push/pull
 * sessions to the cloud. v1 ships with [NoOpCloudSyncRepository].
 */
interface CloudSyncRepository {

    /** Push local changes and pull remote ones. */
    suspend fun syncNow(): Result<Unit>

    /** Last successful sync timestamp, 0 = never. */
    suspend fun lastSyncedAt(): Long
}

/** Placeholder used until cloud sync ships. */
class NoOpCloudSyncRepository : CloudSyncRepository {

    override suspend fun syncNow(): Result<Unit> =
        Result.failure(
            UnsupportedOperationException(
                "Cloud sync is a planned feature (see README → Roadmap). " +
                    "Your history is stored safely on this device."
            )
        )

    override suspend fun lastSyncedAt(): Long = 0L
}
