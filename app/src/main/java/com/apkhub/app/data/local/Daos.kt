package com.apkhub.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackedAppDao {
    @Query("SELECT * FROM tracked_apps ORDER BY hasUpdate DESC, displayName COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<TrackedAppEntity>>

    @Query("SELECT * FROM tracked_apps WHERE id = :id")
    fun observeById(id: Long): Flow<TrackedAppEntity?>

    @Query("SELECT * FROM tracked_apps ORDER BY displayName COLLATE NOCASE ASC")
    suspend fun getAllOnce(): List<TrackedAppEntity>

    @Query("SELECT * FROM tracked_apps WHERE id = :id")
    suspend fun getById(id: Long): TrackedAppEntity?

    @Query("SELECT * FROM tracked_apps WHERE LOWER(owner) = LOWER(:owner) AND LOWER(repo) = LOWER(:repo) LIMIT 1")
    suspend fun findByRepo(owner: String, repo: String): TrackedAppEntity?

    @Query("SELECT * FROM tracked_apps WHERE packageName = :packageName LIMIT 1")
    suspend fun findByPackageName(packageName: String): TrackedAppEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: TrackedAppEntity): Long

    @Update
    suspend fun update(entity: TrackedAppEntity)

    @Query("DELETE FROM tracked_apps WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface CachedReleaseDao {
    @Query("SELECT * FROM cached_releases WHERE appId = :appId ORDER BY publishedAt DESC, releaseId DESC")
    fun observeByAppId(appId: Long): Flow<List<CachedReleaseEntity>>

    @Query("SELECT * FROM cached_releases WHERE appId = :appId ORDER BY publishedAt DESC, releaseId DESC")
    suspend fun getByAppId(appId: Long): List<CachedReleaseEntity>

    @Query("DELETE FROM cached_releases WHERE appId = :appId")
    suspend fun deleteByAppId(appId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(releases: List<CachedReleaseEntity>)

    @Transaction
    suspend fun replaceForApp(appId: Long, releases: List<CachedReleaseEntity>) {
        deleteByAppId(appId)
        if (releases.isNotEmpty()) {
            insertAll(releases)
        }
    }
}

@Dao
interface DownloadTaskDao {
    @Query("SELECT * FROM download_tasks ORDER BY createdAt DESC LIMIT 100")
    fun observeAll(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE id = :id")
    suspend fun getById(id: Long): DownloadTaskEntity?

    @Query("SELECT * FROM download_tasks WHERE status IN ('QUEUED', 'DOWNLOADING', 'VERIFYING', 'INSTALLING')")
    suspend fun getInProgress(): List<DownloadTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadTaskEntity): Long

    @Update
    suspend fun update(entity: DownloadTaskEntity)

    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM download_tasks WHERE status IN ('INSTALLED', 'FAILED', 'CANCELLED')")
    suspend fun clearFinished()
}

@Dao
interface ActivityHistoryDao {
    @Query("SELECT * FROM activity_history ORDER BY timestamp DESC LIMIT 200")
    fun observeRecent(): Flow<List<ActivityHistoryEntity>>

    @Query("SELECT * FROM activity_history WHERE appId = :appId ORDER BY timestamp DESC LIMIT 50")
    fun observeForApp(appId: Long): Flow<List<ActivityHistoryEntity>>

    @Insert
    suspend fun insert(entity: ActivityHistoryEntity): Long

    @Query("DELETE FROM activity_history")
    suspend fun clearAll()
}
