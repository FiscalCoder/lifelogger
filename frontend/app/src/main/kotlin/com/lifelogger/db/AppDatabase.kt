package com.lifelogger.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [UploadQueueEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun uploadQueueDao(): UploadQueueDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lifelogger.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .build()
                    .also { INSTANCE = it }
            }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE upload_queue ADD COLUMN queuedAt INTEGER NOT NULL DEFAULT 0"
                )
            }
        }
    }
}
