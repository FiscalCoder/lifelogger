package com.lifelogger.config;

/**
 * Central configuration for LifeLogger.
 *
 * SERVER_BASE_URL is read from local.properties at build time via BuildConfig.
 * Set `server.base.url=http://<LOCAL_IP>:8000` in frontend/local.properties.
 * The file is gitignored — each developer sets their own IP.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000*\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\b\n\u0002\b\u0006\n\u0002\u0010\t\n\u0002\b\b\n\u0002\u0010\u0006\n\u0002\b\u0002\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0086T\u00a2\u0006\u0002\n\u0000R\u0011\u0010\u0007\u001a\u00020\u00048F\u00a2\u0006\u0006\u001a\u0004\b\b\u0010\tR\u0011\u0010\n\u001a\u00020\u00048F\u00a2\u0006\u0006\u001a\u0004\b\u000b\u0010\tR\u000e\u0010\f\u001a\u00020\rX\u0086T\u00a2\u0006\u0002\n\u0000R\u0011\u0010\u000e\u001a\u00020\u00048F\u00a2\u0006\u0006\u001a\u0004\b\u000f\u0010\tR\u000e\u0010\u0010\u001a\u00020\u0006X\u0086T\u00a2\u0006\u0002\n\u0000R\u0011\u0010\u0011\u001a\u00020\u00048F\u00a2\u0006\u0006\u001a\u0004\b\u0012\u0010\tR\u000e\u0010\u0013\u001a\u00020\rX\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0014\u001a\u00020\u0006X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0015\u001a\u00020\u0016X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0017\u001a\u00020\rX\u0086T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0018"}, d2 = {"Lcom/lifelogger/config/AppConfig;", "", "()V", "DEVICE_ID", "", "MIN_FREE_STORAGE_MB", "", "QUERY_ENDPOINT", "getQUERY_ENDPOINT", "()Ljava/lang/String;", "SERVER_BASE_URL", "getSERVER_BASE_URL", "SILENCE_THRESHOLD_SECONDS", "", "SPEAKERS_UI_URL", "getSPEAKERS_UI_URL", "UPLOAD_BATCH_SIZE", "UPLOAD_ENDPOINT", "getUPLOAD_ENDPOINT", "UPLOAD_INTERVAL_SECONDS", "UPLOAD_RETRY_MAX", "VAD_ENERGY_THRESHOLD", "", "VAD_POLL_INTERVAL_MS", "app_debug"})
public final class AppConfig {
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String DEVICE_ID = "qin-f21";
    
    /**
     * RMS energy threshold for speech detection.
     *
     * Range: 0–32767 (16-bit PCM). Typical levels:
     *  Silent room:   50–150
     *  Ambient noise: 150–400
     *  Normal speech: 600–5000
     *
     * Default 500: detects normal speech while ignoring typical HVAC/ambient noise.
     */
    public static final double VAD_ENERGY_THRESHOLD = 300.0;
    
    /**
     * Seconds of continuous silence before a recording chunk is closed (aggressive mode).
     * Power-saver mode uses 5s — set via [LifeLoggerService.setBatteryMode].
     */
    public static final long SILENCE_THRESHOLD_SECONDS = 2L;
    
    /**
     * Polling interval in aggressive mode (ms). Power-saver uses 250ms.
     */
    public static final long VAD_POLL_INTERVAL_MS = 100L;
    public static final int UPLOAD_BATCH_SIZE = 5;
    public static final int UPLOAD_RETRY_MAX = 3;
    
    /**
     * How often the periodic upload scheduler runs (seconds).
     */
    public static final long UPLOAD_INTERVAL_SECONDS = 60L;
    
    /**
     * Pause recording if free internal storage drops below this (MB).
     */
    public static final int MIN_FREE_STORAGE_MB = 100;
    @org.jetbrains.annotations.NotNull()
    public static final com.lifelogger.config.AppConfig INSTANCE = null;
    
    private AppConfig() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String getSERVER_BASE_URL() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String getUPLOAD_ENDPOINT() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String getQUERY_ENDPOINT() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String getSPEAKERS_UI_URL() {
        return null;
    }
}