package com.zenithai.countai.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface InventoryDao {

    @Query("SELECT * FROM inventory ORDER BY updatedAt DESC")
    fun getAll(): LiveData<List<InventoryEntity>>

    @Query("SELECT * FROM inventory ORDER BY updatedAt DESC")
    suspend fun getAllNow(): List<InventoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: InventoryEntity): Long

    @Query("UPDATE inventory SET lastCount = :count, updatedAt = :at WHERE id = :id")
    suspend fun updateCount(id: Long, count: Int, at: Long)

    @Query("DELETE FROM inventory WHERE id = :id")
    suspend fun deleteById(id: Long)
}
