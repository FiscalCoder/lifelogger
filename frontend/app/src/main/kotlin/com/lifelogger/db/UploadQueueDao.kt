package com.lifelogger.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UploadQueueDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: UploadQueueEntity)

    @Query("SELECT * FROM upload_queue WHERE status = 'pending' AND attempts < 3 ORDER BY recordedAt ASC")
    suspend fun getPending(): List<UploadQueueEntity>

    @Query("SELECT * FROM upload_queue WHERE status = 'failed' ORDER BY recordedAt ASC")
    suspend fun getFailed(): List<UploadQueueEntity>

    @Query("UPDATE upload_queue SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("UPDATE upload_queue SET attempts = :attempts WHERE id = :id")
    suspend fun updateAttempts(id: String, attempts: Int)

    @Query("UPDATE upload_queue SET status = 'pending', attempts = 0 WHERE status = 'failed'")
    suspend fun resetFailed()

    @Query("SELECT COUNT(*) FROM upload_queue WHERE status = :status")
    suspend fun getCountByStatus(status: String): Int

    @Query("SELECT recordedAt FROM upload_queue WHERE status = 'uploaded' ORDER BY recordedAt DESC LIMIT 1")
    suspend fun getLastUploadTime(): String?

    @Query("SELECT * FROM upload_queue ORDER BY recordedAt DESC LIMIT 100")
    suspend fun getRecent(): List<UploadQueueEntity>
}
