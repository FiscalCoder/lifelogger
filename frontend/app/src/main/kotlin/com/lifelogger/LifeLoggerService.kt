package com.lifelogger

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.lifelogger.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Android foreground service that owns the microphone and orchestrates:
 *   - [VadEngine]          — IDLE-phase energy polling
 *   - [AudioChunkManager]  — recording + silence detection
 *   - [UploadQueue]        — WiFi-aware upload drain
 *
 * Lifecycle:
 *   onCreate  → allocate components, acquire wake lock
 *   onStartCommand → startForeground, start VAD, start upload scheduler
 *   onDestroy → stop VAD, stop upload scheduler, release wake lock
 *
 * Battery: PARTIAL_WAKE_LOCK only (CPU on, screen can off). 12-hour auto-release
 * acts as a dead-man switch if the service crashes without calling releaseWakeLock.
 *
 * Binder: StatusFragment binds to get a [LocalBinder] for battery-mode control.
 */
class LifeLoggerService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "lifelogger_recording"
        private const val WAKE_LOCK_TAG = "LifeLogger::RecordingWakeLock"
        private const val WAKE_LOCK_TIMEOUT_MS = 12 * 60 * 60 * 1_000L  // 12 hours

        const val STATE_STOPPED = "stopped"
        const val STATE_LISTENING = "listening"
        const val STATE_RECORDING = "recording"
    }

    /** Current recording state, readable by StatusFragment via binder. */
    @Volatile var recordingState: String = STATE_STOPPED
        private set

    /** Number of audio chunks saved this session. */
    @Volatile var chunksSaved: Int = 0
        private set

    /** Human-readable result of the last chunk (e.g. "Saved 3.2s chunk"). */
    @Volatile var lastChunkInfo: String = ""
        private set

    // ─── Binder ───────────────────────────────────────────────────────────────

    inner class LocalBinder : Binder() {
        val service: LifeLoggerService get() = this@LifeLoggerService
    }

    private val binder = LocalBinder()

    // ─── Components ───────────────────────────────────────────────────────────

    private lateinit var uploadQueue: UploadQueue
    private lateinit var audioChunkManager: AudioChunkManager
    private lateinit var vadEngine: VadEngine
    private var wakeLock: PowerManager.WakeLock? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Call startForeground immediately in onCreate so the 5-second ANR window
        // is satisfied even if the service was created via bindService(BIND_AUTO_CREATE)
        // before startForegroundService() was called, or if initialization is slow.
        startForegroundWithNotification()
        acquireWakeLock()

        uploadQueue = UploadQueue(this)

        // AudioChunkManager calls onChunkFinalized when encoding is done.
        // This resumes VadEngine polling so it can detect the next speech segment.
        audioChunkManager = AudioChunkManager(
            context = this,
            uploadQueue = uploadQueue,
            onChunkFinalized = { saved, info ->
                if (saved) chunksSaved++
                lastChunkInfo = info
                recordingState = STATE_LISTENING
                vadEngine.resume()
            }
        )

        vadEngine = VadEngine(object : VadEngine.VadListener {
            override fun onSpeechStart() {
                // VadEngine has already paused its polling at this point.
                // AudioChunkManager can now safely open AudioRecord.
                recordingState = STATE_RECORDING
                audioChunkManager.startChunk()
            }
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundWithNotification()
        uploadQueue.start()
        recordingState = STATE_LISTENING
        vadEngine.start()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        recordingState = STATE_STOPPED
        vadEngine.stop()
        uploadQueue.stop()
        releaseWakeLock()
        super.onDestroy()
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /** Relays battery mode to both VadEngine (poll interval) and AudioChunkManager (silence threshold). */
    fun setBatteryMode(aggressive: Boolean) {
        vadEngine.setBatteryMode(aggressive)
        audioChunkManager.setSilenceThreshold(if (aggressive) 2_000L else 5_000L)
        updateNotificationText()
    }

    /** Triggers an immediate upload drain. Called by StatusFragment "Upload now" button. */
    fun drainNow() {
        scope.launch { uploadQueue.drainIfWifi() }
    }

    /** Resets failed chunks to pending so the next drain will retry them. */
    fun retryFailed() {
        scope.launch { uploadQueue.retryFailed() }
    }

    // ─── Notification ─────────────────────────────────────────────────────────

    private fun startForegroundWithNotification() {
        val notification = buildNotification("Recording active")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotificationText() {
        scope.launch {
            val db = AppDatabase.getInstance(this@LifeLoggerService)
            val pending = db.uploadQueueDao().getCountByStatus("pending")
            val failed = db.uploadQueueDao().getCountByStatus("failed")
            val text = when {
                failed > 0 && pending > 0 -> "Recording — $pending pending, $failed failed"
                failed > 0 -> "Recording — $failed failed (tap to retry)"
                pending > 0 -> "Recording — $pending pending upload"
                else -> "Recording active"
            }
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, buildNotification(text))
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "LifeLogger Recording",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Continuous background audio recording"
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
        }
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LifeLogger")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()
    }

    // ─── Wake lock ────────────────────────────────────────────────────────────

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            acquire(WAKE_LOCK_TIMEOUT_MS)  // 12-hour safety timeout
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        wakeLock = null
    }
}
