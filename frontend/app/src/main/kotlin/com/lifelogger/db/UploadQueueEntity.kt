package com.lifelogger.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "upload_queue")
data class UploadQueueEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val filePath: String,
    val recordedAt: String,          // ISO-8601 string
    val durationSeconds: Float,
    val attempts: Int = 0,
    val status: String = "pending"   // pending | done | failed
)
