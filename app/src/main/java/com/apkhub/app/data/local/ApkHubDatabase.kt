package com.apkhub.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TrackedAppEntity::class,
        CachedReleaseEntity::class,
        DownloadTaskEntity::class,
        ActivityHistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ApkHubDatabase : RoomDatabase() {
    abstract fun trackedAppDao(): TrackedAppDao
    abstract fun cachedReleaseDao(): CachedReleaseDao
    abstract fun downloadTaskDao(): DownloadTaskDao
    abstract fun activityHistoryDao(): ActivityHistoryDao

    companion object {
        fun build(context: Context): ApkHubDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                ApkHubDatabase::class.java,
                "apkhub.db"
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
