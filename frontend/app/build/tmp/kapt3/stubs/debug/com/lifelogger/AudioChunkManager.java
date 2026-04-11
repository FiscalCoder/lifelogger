package com.lifelogger;

/**
 * Records audio when speech is active, detects silence to auto-finalise chunks.
 *
 * Lifecycle:
 *  1. VadEngine detects speech → calls [startChunk].
 *  2. [startChunk] opens AudioRecord (VadEngine has already paused its polling).
 *  3. Recording loop: reads PCM, writes to temp file, computes RMS per frame.
 *  4. When RMS < threshold for ≥ [silenceThresholdMs] → [finalizeChunk] is called.
 *  5. [finalizeChunk]: stops AudioRecord, encodes PCM → AAC, enqueues to UploadQueue.
 *  6. Calls [onChunkFinalized] so LifeLoggerService can resume VadEngine polling.
 *
 * AudioRecord is exclusively held during recording (not shared with VadEngine).
 * VadEngine releases the mic before calling [startChunk], so no conflict occurs.
 *
 * All I/O and encoding runs on [Dispatchers.IO].
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000`\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\u000b\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0010\u0006\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\t\n\u0002\b\u0002\n\u0002\u0010\u0017\n\u0000\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0007\u0018\u0000 &2\u00020\u0001:\u0001&BM\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00126\u0010\u0006\u001a2\u0012\u0013\u0012\u00110\b\u00a2\u0006\f\b\t\u0012\b\b\n\u0012\u0004\b\b(\u000b\u0012\u0013\u0012\u00110\f\u00a2\u0006\f\b\t\u0012\b\b\n\u0012\u0004\b\b(\r\u0012\u0004\u0012\u00020\u000e0\u0007\u00a2\u0006\u0002\u0010\u000fJ\u0018\u0010\u0019\u001a\u00020\u00112\u0006\u0010\u001a\u001a\u00020\u001b2\u0006\u0010\u001c\u001a\u00020\u001dH\u0002J\u0018\u0010\u001e\u001a\u00020\u000e2\u0006\u0010\u001f\u001a\u00020 2\u0006\u0010!\u001a\u00020 H\u0002J\b\u0010\"\u001a\u00020\u000eH\u0002J\u000e\u0010#\u001a\u00020\u000e2\u0006\u0010$\u001a\u00020\u0018J\u0006\u0010%\u001a\u00020\u000eR\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0010\u001a\u00020\u0011X\u0082\u000e\u00a2\u0006\u0002\n\u0000RA\u0010\u0006\u001a2\u0012\u0013\u0012\u00110\b\u00a2\u0006\f\b\t\u0012\b\b\n\u0012\u0004\b\b(\u000b\u0012\u0013\u0012\u00110\f\u00a2\u0006\f\b\t\u0012\b\b\n\u0012\u0004\b\b(\r\u0012\u0004\u0012\u00020\u000e0\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0012\u0010\u0013R\u000e\u0010\u0014\u001a\u00020\bX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0015\u001a\u00020\u0016X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0017\u001a\u00020\u0018X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\'"}, d2 = {"Lcom/lifelogger/AudioChunkManager;", "", "context", "Landroid/content/Context;", "uploadQueue", "Lcom/lifelogger/UploadQueue;", "onChunkFinalized", "Lkotlin/Function2;", "", "Lkotlin/ParameterName;", "name", "saved", "", "info", "", "(Landroid/content/Context;Lcom/lifelogger/UploadQueue;Lkotlin/jvm/functions/Function2;)V", "energyThreshold", "", "getOnChunkFinalized", "()Lkotlin/jvm/functions/Function2;", "recording", "scope", "Lkotlinx/coroutines/CoroutineScope;", "silenceThresholdMs", "", "computeRms", "samples", "", "count", "", "encodeToAac", "pcmFile", "Ljava/io/File;", "aacFile", "recordLoop", "setSilenceThreshold", "ms", "startChunk", "Companion", "app_debug"})
public final class AudioChunkManager {
    @org.jetbrains.annotations.NotNull()
    private final android.content.Context context = null;
    @org.jetbrains.annotations.NotNull()
    private final com.lifelogger.UploadQueue uploadQueue = null;
    
    /**
     * Invoked on the IO dispatcher after a chunk has been finalised.
     * @param saved true if the chunk was encoded and enqueued for upload
     * @param info  human-readable status message (e.g. "Saved 3.2s chunk" or error reason)
     */
    @org.jetbrains.annotations.NotNull()
    private final kotlin.jvm.functions.Function2<java.lang.Boolean, java.lang.String, kotlin.Unit> onChunkFinalized = null;
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = android.media.AudioFormat.CHANNEL_IN_MONO;
    private static final int ENCODING = android.media.AudioFormat.ENCODING_PCM_16BIT;
    private static final int AUDIO_BITRATE = 64000;
    private static final double MIN_CHUNK_DURATION_S = 0.5;
    @kotlin.jvm.Volatile()
    private volatile double energyThreshold = 300.0;
    @kotlin.jvm.Volatile()
    private volatile long silenceThresholdMs = 2000L;
    @kotlin.jvm.Volatile()
    private volatile boolean recording = false;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.CoroutineScope scope = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.lifelogger.AudioChunkManager.Companion Companion = null;
    
    public AudioChunkManager(@org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.NotNull()
    com.lifelogger.UploadQueue uploadQueue, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function2<? super java.lang.Boolean, ? super java.lang.String, kotlin.Unit> onChunkFinalized) {
        super();
    }
    
    /**
     * Invoked on the IO dispatcher after a chunk has been finalised.
     * @param saved true if the chunk was encoded and enqueued for upload
     * @param info  human-readable status message (e.g. "Saved 3.2s chunk" or error reason)
     */
    @org.jetbrains.annotations.NotNull()
    public final kotlin.jvm.functions.Function2<java.lang.Boolean, java.lang.String, kotlin.Unit> getOnChunkFinalized() {
        return null;
    }
    
    /**
     * Begin recording. Called by LifeLoggerService when VadEngine detects speech.
     * VadEngine must have paused its polling before this is called.
     */
    public final void startChunk() {
    }
    
    /**
     * Update silence threshold when battery mode changes.
     * aggressive = true  → 2 s silence (default)
     * aggressive = false → 5 s silence (power-saver)
     */
    public final void setSilenceThreshold(long ms) {
    }
    
    private final void recordLoop() {
    }
    
    /**
     * Encodes raw 16-bit PCM (mono, 16 kHz) in [pcmFile] to AAC-LC at 64 kbps.
     * Writes an MPEG-4 container (.aac / .m4a) to [aacFile].
     * Compatible with ffmpeg and the Whisper pipeline.
     */
    private final void encodeToAac(java.io.File pcmFile, java.io.File aacFile) {
    }
    
    private final double computeRms(short[] samples, int count) {
        return 0.0;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u001c\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0010\u0006\n\u0002\b\u0002\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\bX\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\n"}, d2 = {"Lcom/lifelogger/AudioChunkManager$Companion;", "", "()V", "AUDIO_BITRATE", "", "CHANNEL_CONFIG", "ENCODING", "MIN_CHUNK_DURATION_S", "", "SAMPLE_RATE", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}