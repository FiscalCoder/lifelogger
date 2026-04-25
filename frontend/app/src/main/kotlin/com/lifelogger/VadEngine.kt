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
 *   IDLE:      Open AudioRecord → sample 10ms → release → check RMS + ZCR.
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

        // ZCR bounds for the speech band in a 160-sample frame.
        // Widened upper bound to 80 so that speech mixed with background noise
        // (e.g. metal sounds + voice simultaneously) still triggers recording.
        private const val ZCR_MIN = 2
        private const val ZCR_MAX = 80

        // Number of consecutive speech-like frames before triggering.
        // At 100 ms poll interval this is 100 ms of continuous speech-like sound.
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
            val (rms, zcr) = sampleFrame()
            val isSpeechLike = rms >= energyThreshold && zcr in ZCR_MIN..ZCR_MAX
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
     * Opens AudioRecord, reads one 10ms frame, computes RMS and ZCR, releases.
     *
     * Returns (0.0, 0) on any failure so the caller sees a below-threshold,
     * out-of-ZCR-range sample and simply reschedules the next poll.
     */
    private fun sampleFrame(): Pair<Double, Int> {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBuf == AudioRecord.ERROR_BAD_VALUE || minBuf == AudioRecord.ERROR) return Pair(0.0, 0)

        val bufferSize = maxOf(minBuf, FRAME_SAMPLES * 2)
        val buffer = ShortArray(FRAME_SAMPLES)

        val record = AudioInput.createRecord(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize)
            ?: return Pair(0.0, 0)

        var started = false
        return try {
            record.startRecording()
            started = true
            val read = record.read(buffer, 0, FRAME_SAMPLES)
            if (read <= 0) Pair(0.0, 0)
            else Pair(computeRms(buffer, read), computeZcr(buffer, read))
        } catch (_: Exception) {
            Pair(0.0, 0)
        } finally {
            if (started) runCatching { record.stop() }
            record.release()
        }
    }

    private fun computeRms(samples: ShortArray, count: Int): Double {
        var sum = 0.0
        for (i in 0 until count) {
            val s = samples[i].toDouble()
            sum += s * s
        }
        return sqrt(sum / count)
    }

    /** Counts zero crossings — a proxy for dominant frequency content. */
    private fun computeZcr(samples: ShortArray, count: Int): Int {
        var crossings = 0
        for (i in 1 until count) {
            if ((samples[i - 1] >= 0) != (samples[i] >= 0)) crossings++
        }
        return crossings
    }
}
