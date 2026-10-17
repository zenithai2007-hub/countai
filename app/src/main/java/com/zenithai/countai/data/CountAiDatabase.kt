package com.zenithai.countai.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SessionEntity::class, InventoryEntity::class, PendingDelete::class],
    version = 3,
    exportSchema = false,
)
abstract class CountAiDatabase : RoomDatabase() {

    abstract fun historyDao(): HistoryDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun pendingDeleteDao(): PendingDeleteDao

    companion object {
        @Volatile
        private var instance: CountAiDatabase? = null

        /**
         * v2 → v3 adds cloud-sync columns. Existing rows are marked pendingSync=1
         * so they upload on the next sync cycle — NO data is lost in the upgrade.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN remoteId TEXT")
                db.execSQL("ALTER TABLE sessions ADD COLUMN pendingSync INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE inventory ADD COLUMN remoteId TEXT")
                db.execSQL("ALTER TABLE inventory ADD COLUMN pendingSync INTEGER NOT NULL DEFAULT 1")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS pending_deletes (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "tableName TEXT NOT NULL, " +
                        "remoteId TEXT NOT NULL, " +
                        "userId TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL)"
                )
            }
        }

        fun getInstance(context: Context): CountAiDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CountAiDatabase::class.java,
                    "countai.db",
                )
                    .addMigrations(MIGRATION_2_3)
                    // Safety net for unknown future versions only; known
                    // migrations above preserve user data.
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
