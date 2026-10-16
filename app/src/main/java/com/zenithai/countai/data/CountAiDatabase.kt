package com.zenithai.countai.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SessionEntity::class], version = 1, exportSchema = false)
abstract class CountAiDatabase : RoomDatabase() {

    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile
        private var instance: CountAiDatabase? = null

        fun getInstance(context: Context): CountAiDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CountAiDatabase::class.java,
                    "countai.db",
                ).build().also { instance = it }
            }
    }
}
