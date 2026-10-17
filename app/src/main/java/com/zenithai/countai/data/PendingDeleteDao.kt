package com.zenithai.countai.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PendingDeleteDao {

    @Insert
    suspend fun insert(tombstone: PendingDelete)

    @Query("SELECT * FROM pending_deletes ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getBatch(limit: Int): List<PendingDelete>

    @Query("DELETE FROM pending_deletes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM pending_deletes")
    suspend fun deleteAll()
}
