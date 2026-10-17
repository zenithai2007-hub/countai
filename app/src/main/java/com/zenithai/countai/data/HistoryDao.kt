package com.zenithai.countai.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface HistoryDao {

    @Query("SELECT * FROM sessions ORDER BY createdAt DESC")
    fun getAll(): LiveData<List<SessionEntity>>

    @Query("SELECT * FROM sessions ORDER BY createdAt DESC")
    suspend fun getAllNow(): List<SessionEntity>

    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM sessions")
    suspend fun deleteAll()

    // ---- Cloud sync (v3): Room is the source of truth; these feed the worker. ----

    /** Rows waiting for upload, oldest first. Kept small (≤100) to bound request size. */
    @Query("SELECT * FROM sessions WHERE pendingSync = 1 ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getPending(limit: Int): List<SessionEntity>

    /** Mark a row uploaded: store its Supabase UUID and clear the pending flag. */
    @Query("UPDATE sessions SET remoteId = :remoteId, pendingSync = 0 WHERE id = :id")
    suspend fun markSynced(id: Long, remoteId: String)

    /** Re-queue a row for upload (e.g. after a failed attempt that should retry). */
    @Query("UPDATE sessions SET pendingSync = 1 WHERE id = :id")
    suspend fun markPending(id: Long)
}
