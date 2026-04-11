package com.lifelogger

import android.content.Context
import com.lifelogger.config.AppConfig
import com.lifelogger.db.AppDatabase
import com.lifelogger.db.UploadQueueEntity
import com.lifelogger.util.NetworkUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * WiFi-aware upload drain for the SQLite chunk queue.
 *
 * Design:
 *   - Scheduled drain every [AppConfig.UPLOAD_INTERVAL_SECONDS] (first tick is immediate).
 *   - [drainIfWifi]: checks WiFi, drains pending then retries failed. Concurrency-safe.
 *   - Upload one chunk at a time to avoid saturating slow home WiFi.
 *   - On 200: mark uploaded, delete local file.
 *   - On error: increment attempts; mark failed after [AppConfig.UPLOAD_RETRY_MAX].
 *
 * Also callable directly via [LifeLoggerService.drainNow] for the "Upload now" UI action.
 */
class UploadQueue(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val dao = db.uploadQueueDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private var scheduledFuture: ScheduledFuture<*>? = null

    @Volatile private var draining = false

    fun start() {
        // initialDelay = 0 so the first drain runs immediately when service starts
        scheduledFuture = scheduler.scheduleAtFixedRate(
            { scope.launch { drainIfWifi() } },
            0L,
            AppConfig.UPLOAD_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        )
    }

    fun stop() {
        scheduledFuture?.cancel(false)
        scheduler.shutdown()
    }

    /** Insert a new row into the upload queue (status = pending). */
    fun enqueue(filePath: String, recordedAt: String, durationSeconds: Float) {
        scope.launch {
            dao.insert(
                UploadQueueEntity(
                    filePath = filePath,
                    recordedAt = recordedAt,
                    durationSeconds = durationSeconds
                )
            )
            // Opportunistic immediate upload if already on WiFi
            drainIfWifi()
        }
    }

    /** Check WiFi and drain if connected. No-op if already draining or not on WiFi. */
    suspend fun drainIfWifi() {
        if (draining) return
        if (!NetworkUtil.isWifi(context)) return
        draining = true
        try {
            drainPending()
            retryFailed()
        } finally {
            draining = false
        }
    }

    /** Reset all failed chunks to pending for next drain attempt. */
    suspend fun retryFailed() {
        dao.resetFailed()
    }

    // ─── Private drain logic ──────────────────────────────────────────────────

    private suspend fun drainPending() {
        var batch = dao.getPending().take(AppConfig.UPLOAD_BATCH_SIZE)
        while (batch.isNotEmpty()) {
            for (item in batch) uploadItem(item)
            batch = dao.getPending().take(AppConfig.UPLOAD_BATCH_SIZE)
        }
    }

    private suspend fun uploadItem(item: UploadQueueEntity) {
        val file = File(item.filePath)
        if (!file.exists()) {
            dao.updateStatus(item.id, "failed")
            return
        }

        val newAttempts = item.attempts + 1
        dao.updateAttempts(item.id, newAttempts)

        runCatching {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("device_id", AppConfig.DEVICE_ID)
                .addFormDataPart("recorded_at", item.recordedAt)
                .addFormDataPart("duration_seconds", item.durationSeconds.toString())
                .addFormDataPart(
                    "file",
                    file.name,
                    file.asRequestBody("audio/aac".toMediaTypeOrNull())
                )
                .build()

            val request = Request.Builder()
                .url(AppConfig.UPLOAD_ENDPOINT)
                .post(body)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    dao.updateStatus(item.id, "uploaded")
                    file.delete()
                } else if (newAttempts >= AppConfig.UPLOAD_RETRY_MAX) {
                    dao.updateStatus(item.id, "failed")
                }
                // else: leave as pending for next tick
                Unit
            }
        }.onFailure {
            if (newAttempts >= AppConfig.UPLOAD_RETRY_MAX) {
                dao.updateStatus(item.id, "failed")
            }
        }
    }

    // ─── UI helpers ───────────────────────────────────────────────────────────

    suspend fun getPendingCount(): Int = dao.getCountByStatus("pending")
    suspend fun getFailedCount(): Int = dao.getCountByStatus("failed")
    suspend fun getUploadedCount(): Int = dao.getCountByStatus("uploaded")
    suspend fun getLastUploadTime(): String? = dao.getLastUploadTime()
}
