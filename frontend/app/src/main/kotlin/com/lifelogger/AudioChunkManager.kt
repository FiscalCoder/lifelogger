package com.lifelogger

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import com.lifelogger.config.AppConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.math.sqrt

/**
 * Records audio when speech is active, detects silence to auto-finalise chunks.
 *
 * Lifecycle:
 *   1. VadEngine detects speech → calls [startChunk].
 *   2. [startChunk] opens AudioRecord (VadEngine has already paused its polling).
 *   3. Recording loop: reads PCM, writes to temp file, computes RMS per frame.
 *   4. When RMS < threshold for ≥ [silenceThresholdMs] → [finalizeChunk] is called.
 *   5. [finalizeChunk]: stops AudioRecord, encodes PCM → AAC, enqueues to UploadQueue.
 *   6. Calls [onChunkFinalized] so LifeLoggerService can resume VadEngine polling.
 *
 * AudioRecord is exclusively held during recording (not shared with VadEngine).
 * VadEngine releases the mic before calling [startChunk], so no conflict occurs.
 *
 * All I/O and encoding runs on [Dispatchers.IO].
 */
class AudioChunkManager(
    private val context: Context,
    private val uploadQueue: UploadQueue,
    /**
     * Invoked on the IO dispatcher after a chunk has been finalised.
     * @param saved true if the chunk was encoded and enqueued for upload
     * @param info  human-readable status message (e.g. "Saved 3.2s chunk" or error reason)
     */
    val onChunkFinalized: (saved: Boolean, info: String) -> Unit
) {

    // ─── Audio config (matches Whisper expected input — DEC-003) ─────────────

    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val AUDIO_BITRATE = 64_000     // 64 kbps AAC
        private const val MIN_CHUNK_DURATION_S = 0.5 // discard very short false-positive chunks
    }

    // ─── Silence detection thresholds (adjustable via setBatteryMode) ─────────

    @Volatile private var energyThreshold: Double = AppConfig.VAD_ENERGY_THRESHOLD
    @Volatile private var silenceThresholdMs: Long =
        AppConfig.SILENCE_THRESHOLD_SECONDS * 1_000L

    // ─── Recording state ──────────────────────────────────────────────────────

    @Volatile private var recording = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Begin recording. Called by LifeLoggerService when VadEngine detects speech.
     * VadEngine must have paused its polling before this is called.
     */
    fun startChunk() {
        if (recording) return
        recording = true
        scope.launch { recordLoop() }
    }

    /**
     * Update silence threshold when battery mode changes.
     * aggressive = true  → 2 s silence (default)
     * aggressive = false → 5 s silence (power-saver)
     */
    fun setSilenceThreshold(ms: Long) {
        silenceThresholdMs = ms
    }

    // ─── Recording loop ───────────────────────────────────────────────────────

    private fun recordLoop() {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, ENCODING)
        if (minBuf <= 0) {
            recording = false
            onChunkFinalized(false, "Mic unavailable (bad buffer size)")
            return
        }

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            ENCODING,
            minBuf
        )

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            recording = false
            onChunkFinalized(false, "Mic failed to initialise")
            return
        }

        val tmpDir = File(context.filesDir, "audio/tmp").also { it.mkdirs() }
        val tmpFile = File(tmpDir, "chunk_${System.currentTimeMillis()}.pcm")
        val chunkStartMs = System.currentTimeMillis()
        var silenceStartMs = 0L

        try {
            audioRecord.startRecording()
            val buf = ShortArray(minBuf / 2)

            FileOutputStream(tmpFile).use { fos ->
                while (recording) {
                    val read = audioRecord.read(buf, 0, buf.size)
                    if (read <= 0) continue

                    // Write little-endian 16-bit PCM
                    val bytes = ByteArray(read * 2)
                    for (i in 0 until read) {
                        val s = buf[i].toInt()
                        bytes[i * 2] = (s and 0xFF).toByte()
                        bytes[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
                    }
                    fos.write(bytes)

                    // Silence detection
                    val rms = computeRms(buf, read)
                    val now = System.currentTimeMillis()
                    if (rms < energyThreshold) {
                        if (silenceStartMs == 0L) silenceStartMs = now
                        else if (now - silenceStartMs >= silenceThresholdMs) {
                            recording = false   // exit loop → finalize
                        }
                    } else {
                        silenceStartMs = 0L    // speech resumed — reset silence timer
                    }
                }
            }
        } finally {
            runCatching { audioRecord.stop() }
            audioRecord.release()
        }

        // Finalize: encode PCM → AAC, enqueue for upload
        val durationS = (System.currentTimeMillis() - chunkStartMs) / 1_000f
        if (!tmpFile.exists() || tmpFile.length() == 0L || durationS < MIN_CHUNK_DURATION_S) {
            tmpFile.delete()
            onChunkFinalized(false, "Chunk too short (${String.format("%.1f", durationS)}s)")
            return
        }

        val recordedAt = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(chunkStartMs))
        val safeTimestamp = recordedAt.replace(":", "-")
        val pendingDir = File(context.filesDir, "audio/pending").also { it.mkdirs() }
        val aacFile = File(pendingDir, "chunk_${safeTimestamp}.aac")

        val encodeResult = runCatching { encodeToAac(tmpFile, aacFile) }
        tmpFile.delete()

        if (encodeResult.isSuccess && aacFile.exists() && aacFile.length() > 0) {
            uploadQueue.enqueue(aacFile.absolutePath, recordedAt, durationS)
            onChunkFinalized(true, "Saved ${String.format("%.1f", durationS)}s chunk")
        } else {
            val reason = encodeResult.exceptionOrNull()?.message ?: "empty output"
            aacFile.delete()
            onChunkFinalized(false, "Encoding failed: $reason")
        }
    }

    // ─── AAC encoding (MediaCodec + MediaMuxer) ───────────────────────────────

    /**
     * Encodes raw 16-bit PCM (mono, 16 kHz) in [pcmFile] to AAC-LC at 64 kbps.
     * Writes an MPEG-4 container (.aac / .m4a) to [aacFile].
     * Compatible with ffmpeg and the Whisper pipeline.
     */
    private fun encodeToAac(pcmFile: File, aacFile: File) {
        val mime = MediaFormat.MIMETYPE_AUDIO_AAC
        val format = MediaFormat.createAudioFormat(mime, SAMPLE_RATE, 1).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BITRATE)
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
        }

        val codec = MediaCodec.createEncoderByType(mime)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val muxer = MediaMuxer(aacFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var muxerStarted = false

        val info = MediaCodec.BufferInfo()
        var inputDone = false

        pcmFile.inputStream().use { pcmStream ->
            val inputBuf = ByteArray(4_096)

            while (true) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val bytesBuf: ByteBuffer = codec.getInputBuffer(inIdx)!!
                        bytesBuf.clear()
                        val read = pcmStream.read(inputBuf)
                        if (read <= 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            bytesBuf.put(inputBuf, 0, read)
                            codec.queueInputBuffer(inIdx, 0, read, System.nanoTime() / 1_000, 0)
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    outIdx >= 0 -> {
                        val outBuf: ByteBuffer = codec.getOutputBuffer(outIdx)!!
                        if (muxerStarted && info.size > 0 &&
                            info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
                        ) {
                            muxer.writeSampleData(trackIndex, outBuf, info)
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
        }

        codec.stop()
        codec.release()
        if (muxerStarted) {
            muxer.stop()
            muxer.release()
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun computeRms(samples: ShortArray, count: Int): Double {
        var sum = 0.0
        for (i in 0 until count) {
            val s = samples[i].toDouble()
            sum += s * s
        }
        return sqrt(sum / count)
    }
}
