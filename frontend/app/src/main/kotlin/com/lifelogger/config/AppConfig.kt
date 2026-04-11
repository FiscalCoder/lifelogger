package com.lifelogger.config

import com.lifelogger.BuildConfig

/**
 * Central configuration for LifeLogger.
 *
 * SERVER_BASE_URL is read from local.properties at build time via BuildConfig.
 * Set `server.base.url=http://<LOCAL_IP>:8000` in frontend/local.properties.
 * The file is gitignored — each developer sets their own IP.
 */
object AppConfig {

    // ─── Network ──────────────────────────────────────────────────────────────

    val SERVER_BASE_URL: String get() = BuildConfig.SERVER_BASE_URL
    val UPLOAD_ENDPOINT: String get() = "$SERVER_BASE_URL/upload"
    val QUERY_ENDPOINT: String get() = "$SERVER_BASE_URL/query"
    val SPEAKERS_UI_URL: String get() = "$SERVER_BASE_URL/speakers/ui"

    // ─── Device identity ──────────────────────────────────────────────────────

    const val DEVICE_ID = "qin-f21"

    // ─── VAD thresholds ───────────────────────────────────────────────────────

    /**
     * RMS energy threshold for speech detection.
     *
     * Range: 0–32767 (16-bit PCM). Typical levels:
     *   Silent room:   50–150
     *   Ambient noise: 150–400
     *   Normal speech: 600–5000
     *
     * Default 500: detects normal speech while ignoring typical HVAC/ambient noise.
     */
    const val VAD_ENERGY_THRESHOLD = 300.0

    /**
     * Seconds of continuous silence before a recording chunk is closed (aggressive mode).
     * Power-saver mode uses 5s — set via [LifeLoggerService.setBatteryMode].
     */
    const val SILENCE_THRESHOLD_SECONDS = 2L

    /** Polling interval in aggressive mode (ms). Power-saver uses 250ms. */
    const val VAD_POLL_INTERVAL_MS = 100L

    // ─── Upload queue ─────────────────────────────────────────────────────────

    const val UPLOAD_BATCH_SIZE = 5
    const val UPLOAD_RETRY_MAX = 3

    /** How often the periodic upload scheduler runs (seconds). */
    const val UPLOAD_INTERVAL_SECONDS = 60L

    // ─── Storage guard ────────────────────────────────────────────────────────

    /** Pause recording if free internal storage drops below this (MB). */
    const val MIN_FREE_STORAGE_MB = 100
}
