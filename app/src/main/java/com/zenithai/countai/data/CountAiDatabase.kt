package com.zenithai.countai.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SessionEntity::class, InventoryEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class CountAiDatabase : RoomDatabase() {

    abstract fun historyDao(): HistoryDao
    abstract fun inventoryDao(): InventoryDao

    companion object {
        @Volatile
        private var instance: CountAiDatabase? = null

        fun getInstance(context: Context): CountAiDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CountAiDatabase::class.java,
                    "countai.db",
                )
                    // v1 → v2 adds the inventory table + session location column.
                    // Destructive migration is acceptable here: history is a
                    // local cache the user can rebuild by counting again.
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
