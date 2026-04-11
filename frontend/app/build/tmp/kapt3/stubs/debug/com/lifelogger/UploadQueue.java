package com.lifelogger;

/**
 * WiFi-aware upload drain for the SQLite chunk queue.
 *
 * Design:
 *  - Scheduled drain every [AppConfig.UPLOAD_INTERVAL_SECONDS] (first tick is immediate).
 *  - [drainIfWifi]: checks WiFi, drains pending then retries failed. Concurrency-safe.
 *  - Upload one chunk at a time to avoid saturating slow home WiFi.
 *  - On 200: mark uploaded, delete local file.
 *  - On error: increment attempts; mark failed after [AppConfig.UPLOAD_RETRY_MAX].
 *
 * Also callable directly via [LifeLoggerService.drainNow] for the "Upload now" UI action.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000d\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0004\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\u0007\n\u0000\n\u0002\u0010\b\n\u0002\b\b\n\u0002\u0018\u0002\n\u0002\b\u0002\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u000e\u0010\u0014\u001a\u00020\u0015H\u0086@\u00a2\u0006\u0002\u0010\u0016J\u000e\u0010\u0017\u001a\u00020\u0015H\u0082@\u00a2\u0006\u0002\u0010\u0016J\u001e\u0010\u0018\u001a\u00020\u00152\u0006\u0010\u0019\u001a\u00020\u001a2\u0006\u0010\u001b\u001a\u00020\u001a2\u0006\u0010\u001c\u001a\u00020\u001dJ\u000e\u0010\u001e\u001a\u00020\u001fH\u0086@\u00a2\u0006\u0002\u0010\u0016J\u0010\u0010 \u001a\u0004\u0018\u00010\u001aH\u0086@\u00a2\u0006\u0002\u0010\u0016J\u000e\u0010!\u001a\u00020\u001fH\u0086@\u00a2\u0006\u0002\u0010\u0016J\u000e\u0010\"\u001a\u00020\u001fH\u0086@\u00a2\u0006\u0002\u0010\u0016J\u000e\u0010#\u001a\u00020\u0015H\u0086@\u00a2\u0006\u0002\u0010\u0016J\u0006\u0010$\u001a\u00020\u0015J\u0006\u0010%\u001a\u00020\u0015J\u0016\u0010&\u001a\u00020\u00152\u0006\u0010\'\u001a\u00020(H\u0082@\u00a2\u0006\u0002\u0010)R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\nX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000b\u001a\u00020\fX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\r\u001a\b\u0012\u0002\b\u0003\u0018\u00010\u000eX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0016\u0010\u000f\u001a\n \u0011*\u0004\u0018\u00010\u00100\u0010X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0012\u001a\u00020\u0013X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006*"}, d2 = {"Lcom/lifelogger/UploadQueue;", "", "context", "Landroid/content/Context;", "(Landroid/content/Context;)V", "dao", "Lcom/lifelogger/db/UploadQueueDao;", "db", "Lcom/lifelogger/db/AppDatabase;", "draining", "", "httpClient", "Lokhttp3/OkHttpClient;", "scheduledFuture", "Ljava/util/concurrent/ScheduledFuture;", "scheduler", "Ljava/util/concurrent/ScheduledExecutorService;", "kotlin.jvm.PlatformType", "scope", "Lkotlinx/coroutines/CoroutineScope;", "drainIfWifi", "", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "drainPending", "enqueue", "filePath", "", "recordedAt", "durationSeconds", "", "getFailedCount", "", "getLastUploadTime", "getPendingCount", "getUploadedCount", "retryFailed", "start", "stop", "uploadItem", "item", "Lcom/lifelogger/db/UploadQueueEntity;", "(Lcom/lifelogger/db/UploadQueueEntity;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "app_debug"})
public final class UploadQueue {
    @org.jetbrains.annotations.NotNull()
    private final android.content.Context context = null;
    @org.jetbrains.annotations.NotNull()
    private final com.lifelogger.db.AppDatabase db = null;
    @org.jetbrains.annotations.NotNull()
    private final com.lifelogger.db.UploadQueueDao dao = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.CoroutineScope scope = null;
    @org.jetbrains.annotations.NotNull()
    private final okhttp3.OkHttpClient httpClient = null;
    private final java.util.concurrent.ScheduledExecutorService scheduler = null;
    @org.jetbrains.annotations.Nullable()
    private java.util.concurrent.ScheduledFuture<?> scheduledFuture;
    @kotlin.jvm.Volatile()
    private volatile boolean draining = false;
    
    public UploadQueue(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        super();
    }
    
    public final void start() {
    }
    
    public final void stop() {
    }
    
    /**
     * Insert a new row into the upload queue (status = pending).
     */
    public final void enqueue(@org.jetbrains.annotations.NotNull()
    java.lang.String filePath, @org.jetbrains.annotations.NotNull()
    java.lang.String recordedAt, float durationSeconds) {
    }
    
    /**
     * Check WiFi and drain if connected. No-op if already draining or not on WiFi.
     */
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object drainIfWifi(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    /**
     * Reset all failed chunks to pending for next drain attempt.
     */
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object retryFailed(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    private final java.lang.Object drainPending(kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    private final java.lang.Object uploadItem(com.lifelogger.db.UploadQueueEntity item, kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object getPendingCount(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.Integer> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object getFailedCount(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.Integer> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object getUploadedCount(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.Integer> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object getLastUploadTime(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.String> $completion) {
        return null;
    }
}