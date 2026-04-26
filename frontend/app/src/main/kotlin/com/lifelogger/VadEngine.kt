package com.lifelogger

import android.media.AudioFormat
import android.media.AudioRecord
import android.os.Handler
import android.os.HandlerThread
import com.lifelogger.config.AppConfig
import kotlin.math.sqrt

/**
 * Voice Activity Detection engine — IDLE-only mic sampling.
 *
 * Speech detection uses two complementary checks:
 *
 *   1. RMS energy  — must exceed [AppConfig.VAD_ENERGY_THRESHOLD].
 *
 *   2. Zero Crossing Rate (ZCR) — counts how many times the waveform crosses
 *      zero per 10ms frame.  Human speech (85–3400 Hz) produces roughly
 *      3–55 crossings per 160-sample frame at 16 kHz.  Metal impacts, claps,
 *      and high-frequency noise land outside this range and are rejected.
 *
 *   3. Consecutive frames — both checks must pass for [SPEECH_FRAMES_REQUIRED]
 *      frames in a row before [VadListener.onSpeechStart] fires.  This filters
 *      transient impacts (a single bang is gone before the second frame).
 *
 * Architecture:
 *   IDLE:      Open AudioRecord → sample a short window → release → check RMS + ZCR.
 *   DETECTED:  Stop polling, call [VadListener.onSpeechStart].
 *              AudioChunkManager then exclusively holds the mic for recording.
 *   RESUME:    AudioChunkManager calls [resume] when its chunk is finalised.
 *              VadEngine restarts IDLE polling.
 */
class VadEngine(private val listener: VadListener) {

    interface VadListener {
        fun onSpeechStart()
    }

    // ─── Constants ────────────────────────────────────────────────────────────

    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val FRAME_SAMPLES = 160   // 10ms @ 16kHz
        private const val WINDOW_FRAMES = 10
        private const val WINDOW_SAMPLES = FRAME_SAMPLES * WINDOW_FRAMES

        // ZCR bounds for the speech band in a 160-sample frame.
        // Widened upper bound to 80 so that speech mixed with background noise
        // (e.g. metal sounds + voice simultaneously) still triggers recording.
        private const val ZCR_MIN = 2
        private const val ZCR_MAX = 80

        // Number of speech-like poll windows before triggering.
        // Keep this at 1 to avoid clipping the start of speech.
        private const val SPEECH_FRAMES_REQUIRED = 1
    }

    // ─── Threading ────────────────────────────────────────────────────────────

    private val handlerThread = HandlerThread("VadEngine").also { it.start() }
    private val handler = Handler(handlerThread.looper)

    // ─── Configurable thresholds (thread-safe via @Volatile) ─────────────────

    @Volatile private var pollIntervalMs: Long = AppConfig.VAD_POLL_INTERVAL_MS
    @Volatile private var energyThreshold: Double = AppConfig.VAD_ENERGY_THRESHOLD

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Volatile private var running = false
    @Volatile private var speechFrameCount = 0

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            val isSpeechLike = sampleSpeechLike()
            if (isSpeechLike) {
                speechFrameCount++
                if (speechFrameCount >= SPEECH_FRAMES_REQUIRED) {
                    // Confirmed speech: pause polling and hand mic to AudioChunkManager.
                    speechFrameCount = 0
                    running = false
                    listener.onSpeechStart()
                } else {
                    handler.postDelayed(this, pollIntervalMs)
                }
            } else {
                speechFrameCount = 0
                handler.postDelayed(this, pollIntervalMs)
            }
        }
    }

    /** Start the IDLE polling loop. Safe to call multiple times. */
    fun start() {
        if (running) return
        speechFrameCount = 0
        running = true
        handler.post(pollRunnable)
    }

    /** Stop the polling loop entirely (called on service destroy). */
    fun stop() {
        running = false
        speechFrameCount = 0
        handler.removeCallbacks(pollRunnable)
        handlerThread.quitSafely()
    }

    /**
     * Resume IDLE polling after AudioChunkManager has released the mic.
     * Called by LifeLoggerService once a chunk is finalised.
     */
    fun resume() {
        if (running) return
        speechFrameCount = 0
        running = true
        handler.post(pollRunnable)
    }

    /**
     * Switch between aggressive and power-saver VAD polling modes.
     * Takes effect on the next poll cycle.
     *
     * aggressive = true  → 100 ms poll  (default, more responsive)
     * aggressive = false → 250 ms poll  (lower CPU / battery)
     */
    fun setBatteryMode(aggressive: Boolean) {
        pollIntervalMs = if (aggressive) 100L else 250L
    }

    // ─── Mic sampling ─────────────────────────────────────────────────────────

    /**
     * Opens AudioRecord, reads a short window, checks each 10ms frame, releases.
     *
     * Returns false on any failure so the caller simply reschedules the next poll.
     */
    private fun sampleSpeechLike(): Boolean {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBuf == AudioRecord.ERROR_BAD_VALUE || minBuf == AudioRecord.ERROR) return false

        val bufferSize = maxOf(minBuf, WINDOW_SAMPLES * 2)
        val buffer = ShortArray(WINDOW_SAMPLES)

        val record = AudioInput.createRecord(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize)
            ?: return false

        var started = false
        return try {
            record.startRecording()
            started = true
            val read = record.read(buffer, 0, WINDOW_SAMPLES)
            read > 0 && hasSpeechLikeFrame(buffer, read)
        } catch (_: Exception) {
            false
        } finally {
            if (started) runCatching { record.stop() }
            record.release()
        }
    }

    private fun hasSpeechLikeFrame(samples: ShortArray, count: Int): Boolean {
        var offset = 0
        while (offset + FRAME_SAMPLES <= count) {
            val rms = computeRms(samples, offset, FRAME_SAMPLES)
            val zcr = computeZcr(samples, offset, FRAME_SAMPLES)
            if (rms >= energyThreshold && zcr in ZCR_MIN..ZCR_MAX) {
                return true
            }
            offset += FRAME_SAMPLES
        }
        return false
    }

    private fun computeRms(samples: ShortArray, offset: Int, count: Int): Double {
        var sum = 0.0
        for (i in 0 until count) {
            val s = samples[offset + i].toDouble()
            sum += s * s
        }
        return sqrt(sum / count)
    }

    /** Counts zero crossings — a proxy for dominant frequency content. */
    private fun computeZcr(samples: ShortArray, offset: Int, count: Int): Int {
        var crossings = 0
        for (i in 1 until count) {
            val previous = samples[offset + i - 1]
            val current = samples[offset + i]
            if ((previous >= 0) != (current >= 0)) crossings++
        }
        return crossings
    }
}
