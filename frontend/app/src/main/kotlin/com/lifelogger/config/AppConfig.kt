package com.lifelogger.config

import android.media.MediaRecorder
import com.lifelogger.BuildConfig

/**
 * Central configuration for LifeLogger.
 *
 * SERVER_BASE_URL and API_TOKEN are injected into BuildConfig at build time.
 */
object AppConfig {

    // ─── Network ──────────────────────────────────────────────────────────────

    val SERVER_BASE_URL: String get() = BuildConfig.SERVER_BASE_URL
    val AUTHORIZATION_HEADER: String get() = "Bearer ${BuildConfig.API_TOKEN}"
    val AUTH_HEADERS: Map<String, String>
        get() = mapOf("Authorization" to AUTHORIZATION_HEADER)
    val UPLOAD_ENDPOINT: String get() = "$SERVER_BASE_URL/upload"
    val QUERY_ENDPOINT: String get() = "$SERVER_BASE_URL/query"
    val SPEAKERS_UI_URL: String get() = "$SERVER_BASE_URL/speakers/ui"

    // ─── Device identity ──────────────────────────────────────────────────────

    const val DEVICE_ID = "qin-f21"

    // ─── VAD thresholds ───────────────────────────────────────────────────────

    /**
     * RMS energy threshold for speech detection.
     *
     * Range: 0-32767 (16-bit PCM). Typical levels:
     *   Silent room:   50-150
     *   Ambient noise: 150-400
     *   Normal speech: 600-5000
     *
     * Default 500: detects normal speech while ignoring typical HVAC/ambient noise.
     */
    const val VAD_ENERGY_THRESHOLD = 500.0

    /**
     * Audio source priority for F21 Pro testing.
     * VOICE_RECOGNITION is the default target, VOICE_COMMUNICATION is the next
     * DSP-backed option, and MIC keeps recording available if either source
     * fails to initialise on the device.
     */
    val AUDIO_SOURCE_PRIORITY: IntArray = intArrayOf(
        MediaRecorder.AudioSource.VOICE_RECOGNITION,
        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
        MediaRecorder.AudioSource.MIC
    )

    /** Attach Android's platform noise suppressor to full recording sessions when available. */
    const val ENABLE_NOISE_SUPPRESSOR = true

    /**
     * Seconds of continuous silence before a recording chunk is closed (aggressive mode).
     * Power-saver mode uses 5s — set via [LifeLoggerService.setBatteryMode].
     */
    const val SILENCE_THRESHOLD_SECONDS = 2L

    /** Polling interval in aggressive mode (ms). Power-saver uses 250ms. */
    const val VAD_POLL_INTERVAL_MS = 100L

    /** Rollover guard to keep noisy rooms from producing backend-rejected hour-long chunks. */
    const val MAX_CHUNK_DURATION_MS = 25L * 60L * 1_000L

    // ─── Upload queue ─────────────────────────────────────────────────────────

    const val UPLOAD_BATCH_SIZE = 5
    const val UPLOAD_RETRY_MAX = 3

    /** How often the periodic upload scheduler runs (seconds). */
    const val UPLOAD_INTERVAL_SECONDS = 60L

    // ─── Storage guard ────────────────────────────────────────────────────────

    /** Pause recording if free internal storage drops below this (MB). */
    const val MIN_FREE_STORAGE_MB = 100
}
