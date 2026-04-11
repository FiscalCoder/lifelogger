package com.lifelogger;

/**
 * Android foreground service that owns the microphone and orchestrates:
 *  - [VadEngine]          — IDLE-phase energy polling
 *  - [AudioChunkManager]  — recording + silence detection
 *  - [UploadQueue]        — WiFi-aware upload drain
 *
 * Lifecycle:
 *  onCreate  → allocate components, acquire wake lock
 *  onStartCommand → startForeground, start VAD, start upload scheduler
 *  onDestroy → stop VAD, stop upload scheduler, release wake lock
 *
 * Battery: PARTIAL_WAKE_LOCK only (CPU on, screen can off). 12-hour auto-release
 * acts as a dead-man switch if the service crashes without calling releaseWakeLock.
 *
 * Binder: StatusFragment binds to get a [LocalBinder] for battery-mode control.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000h\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\t\n\u0002\u0010\u000b\n\u0002\b\u0005\u0018\u0000 22\u00020\u0001:\u000223B\u0005\u00a2\u0006\u0002\u0010\u0002J\b\u0010\u001b\u001a\u00020\u001cH\u0002J\u0010\u0010\u001d\u001a\u00020\u001e2\u0006\u0010\u001f\u001a\u00020\fH\u0002J\b\u0010 \u001a\u00020\u001cH\u0002J\u0006\u0010!\u001a\u00020\u001cJ\u0012\u0010\"\u001a\u00020#2\b\u0010$\u001a\u0004\u0018\u00010%H\u0016J\b\u0010&\u001a\u00020\u001cH\u0016J\b\u0010\'\u001a\u00020\u001cH\u0016J\"\u0010(\u001a\u00020\b2\b\u0010$\u001a\u0004\u0018\u00010%2\u0006\u0010)\u001a\u00020\b2\u0006\u0010*\u001a\u00020\bH\u0016J\b\u0010+\u001a\u00020\u001cH\u0002J\u0006\u0010,\u001a\u00020\u001cJ\u000e\u0010-\u001a\u00020\u001c2\u0006\u0010.\u001a\u00020/J\b\u00100\u001a\u00020\u001cH\u0002J\b\u00101\u001a\u00020\u001cH\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082.\u00a2\u0006\u0002\n\u0000R\u0012\u0010\u0005\u001a\u00060\u0006R\u00020\u0000X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001e\u0010\t\u001a\u00020\b2\u0006\u0010\u0007\u001a\u00020\b@BX\u0086\u000e\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000bR\u001e\u0010\r\u001a\u00020\f2\u0006\u0010\u0007\u001a\u00020\f@BX\u0086\u000e\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\u000fR\u001e\u0010\u0010\u001a\u00020\f2\u0006\u0010\u0007\u001a\u00020\f@BX\u0086\u000e\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u000fR\u000e\u0010\u0012\u001a\u00020\u0013X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0014\u001a\u00020\u0015X\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0016\u001a\u00020\u0017X\u0082.\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u0018\u001a\b\u0018\u00010\u0019R\u00020\u001aX\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u00064"}, d2 = {"Lcom/lifelogger/LifeLoggerService;", "Landroid/app/Service;", "()V", "audioChunkManager", "Lcom/lifelogger/AudioChunkManager;", "binder", "Lcom/lifelogger/LifeLoggerService$LocalBinder;", "<set-?>", "", "chunksSaved", "getChunksSaved", "()I", "", "lastChunkInfo", "getLastChunkInfo", "()Ljava/lang/String;", "recordingState", "getRecordingState", "scope", "Lkotlinx/coroutines/CoroutineScope;", "uploadQueue", "Lcom/lifelogger/UploadQueue;", "vadEngine", "Lcom/lifelogger/VadEngine;", "wakeLock", "Landroid/os/PowerManager$WakeLock;", "Landroid/os/PowerManager;", "acquireWakeLock", "", "buildNotification", "Landroid/app/Notification;", "text", "createNotificationChannel", "drainNow", "onBind", "Landroid/os/IBinder;", "intent", "Landroid/content/Intent;", "onCreate", "onDestroy", "onStartCommand", "flags", "startId", "releaseWakeLock", "retryFailed", "setBatteryMode", "aggressive", "", "startForegroundWithNotification", "updateNotificationText", "Companion", "LocalBinder", "app_debug"})
public final class LifeLoggerService extends android.app.Service {
    private static final int NOTIFICATION_ID = 1001;
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String CHANNEL_ID = "lifelogger_recording";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String WAKE_LOCK_TAG = "LifeLogger::RecordingWakeLock";
    private static final long WAKE_LOCK_TIMEOUT_MS = 43200000L;
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String STATE_STOPPED = "stopped";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String STATE_LISTENING = "listening";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String STATE_RECORDING = "recording";
    
    /**
     * Current recording state, readable by StatusFragment via binder.
     */
    @kotlin.jvm.Volatile()
    @org.jetbrains.annotations.NotNull()
    private volatile java.lang.String recordingState = "stopped";
    
    /**
     * Number of audio chunks saved this session.
     */
    @kotlin.jvm.Volatile()
    private volatile int chunksSaved = 0;
    
    /**
     * Human-readable result of the last chunk (e.g. "Saved 3.2s chunk").
     */
    @kotlin.jvm.Volatile()
    @org.jetbrains.annotations.NotNull()
    private volatile java.lang.String lastChunkInfo = "";
    @org.jetbrains.annotations.NotNull()
    private final com.lifelogger.LifeLoggerService.LocalBinder binder = null;
    private com.lifelogger.UploadQueue uploadQueue;
    private com.lifelogger.AudioChunkManager audioChunkManager;
    private com.lifelogger.VadEngine vadEngine;
    @org.jetbrains.annotations.Nullable()
    private android.os.PowerManager.WakeLock wakeLock;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.CoroutineScope scope = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.lifelogger.LifeLoggerService.Companion Companion = null;
    
    public LifeLoggerService() {
        super();
    }
    
    /**
     * Current recording state, readable by StatusFragment via binder.
     */
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String getRecordingState() {
        return null;
    }
    
    /**
     * Number of audio chunks saved this session.
     */
    public final int getChunksSaved() {
        return 0;
    }
    
    /**
     * Human-readable result of the last chunk (e.g. "Saved 3.2s chunk").
     */
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String getLastChunkInfo() {
        return null;
    }
    
    @java.lang.Override()
    public void onCreate() {
    }
    
    @java.lang.Override()
    public int onStartCommand(@org.jetbrains.annotations.Nullable()
    android.content.Intent intent, int flags, int startId) {
        return 0;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public android.os.IBinder onBind(@org.jetbrains.annotations.Nullable()
    android.content.Intent intent) {
        return null;
    }
    
    @java.lang.Override()
    public void onDestroy() {
    }
    
    /**
     * Relays battery mode to both VadEngine (poll interval) and AudioChunkManager (silence threshold).
     */
    public final void setBatteryMode(boolean aggressive) {
    }
    
    /**
     * Triggers an immediate upload drain. Called by StatusFragment "Upload now" button.
     */
    public final void drainNow() {
    }
    
    /**
     * Resets failed chunks to pending so the next drain will retry them.
     */
    public final void retryFailed() {
    }
    
    private final void startForegroundWithNotification() {
    }
    
    private final void updateNotificationText() {
    }
    
    private final void createNotificationChannel() {
    }
    
    private final android.app.Notification buildNotification(java.lang.String text) {
        return null;
    }
    
    private final void acquireWakeLock() {
    }
    
    private final void releaseWakeLock() {
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\b\n\u0002\b\u0005\n\u0002\u0010\t\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000b\u001a\u00020\fX\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\r"}, d2 = {"Lcom/lifelogger/LifeLoggerService$Companion;", "", "()V", "CHANNEL_ID", "", "NOTIFICATION_ID", "", "STATE_LISTENING", "STATE_RECORDING", "STATE_STOPPED", "WAKE_LOCK_TAG", "WAKE_LOCK_TIMEOUT_MS", "", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u0086\u0004\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002R\u0011\u0010\u0003\u001a\u00020\u00048F\u00a2\u0006\u0006\u001a\u0004\b\u0005\u0010\u0006\u00a8\u0006\u0007"}, d2 = {"Lcom/lifelogger/LifeLoggerService$LocalBinder;", "Landroid/os/Binder;", "(Lcom/lifelogger/LifeLoggerService;)V", "service", "Lcom/lifelogger/LifeLoggerService;", "getService", "()Lcom/lifelogger/LifeLoggerService;", "app_debug"})
    public final class LocalBinder extends android.os.Binder {
        
        public LocalBinder() {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.lifelogger.LifeLoggerService getService() {
            return null;
        }
    }
}