package com.lifelogger.db;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000,\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u0002\n\u0002\b\n\bg\u0018\u00002\u00020\u0001J\u0016\u0010\u0002\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u0005H\u00a7@\u00a2\u0006\u0002\u0010\u0006J\u0014\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\t0\bH\u00a7@\u00a2\u0006\u0002\u0010\nJ\u0010\u0010\u000b\u001a\u0004\u0018\u00010\u0005H\u00a7@\u00a2\u0006\u0002\u0010\nJ\u0014\u0010\f\u001a\b\u0012\u0004\u0012\u00020\t0\bH\u00a7@\u00a2\u0006\u0002\u0010\nJ\u0014\u0010\r\u001a\b\u0012\u0004\u0012\u00020\t0\bH\u00a7@\u00a2\u0006\u0002\u0010\nJ\u0016\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\tH\u00a7@\u00a2\u0006\u0002\u0010\u0011J\u000e\u0010\u0012\u001a\u00020\u000fH\u00a7@\u00a2\u0006\u0002\u0010\nJ\u001e\u0010\u0013\u001a\u00020\u000f2\u0006\u0010\u0014\u001a\u00020\u00052\u0006\u0010\u0015\u001a\u00020\u0003H\u00a7@\u00a2\u0006\u0002\u0010\u0016J\u001e\u0010\u0017\u001a\u00020\u000f2\u0006\u0010\u0014\u001a\u00020\u00052\u0006\u0010\u0004\u001a\u00020\u0005H\u00a7@\u00a2\u0006\u0002\u0010\u0018\u00a8\u0006\u0019"}, d2 = {"Lcom/lifelogger/db/UploadQueueDao;", "", "getCountByStatus", "", "status", "", "(Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getFailed", "", "Lcom/lifelogger/db/UploadQueueEntity;", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getLastUploadTime", "getLocal", "getPending", "insert", "", "entity", "(Lcom/lifelogger/db/UploadQueueEntity;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "resetFailed", "updateAttempts", "id", "attempts", "(Ljava/lang/String;ILkotlin/coroutines/Continuation;)Ljava/lang/Object;", "updateStatus", "(Ljava/lang/String;Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "app_debug"})
@androidx.room.Dao()
public abstract interface UploadQueueDao {
    
    @androidx.room.Insert(onConflict = 5)
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object insert(@org.jetbrains.annotations.NotNull()
    com.lifelogger.db.UploadQueueEntity entity, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Query(value = "SELECT * FROM upload_queue WHERE status = \'pending\' AND attempts < 3 ORDER BY recordedAt ASC")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getPending(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.util.List<com.lifelogger.db.UploadQueueEntity>> $completion);
    
    @androidx.room.Query(value = "SELECT * FROM upload_queue WHERE status = \'failed\' ORDER BY recordedAt ASC")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getFailed(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.util.List<com.lifelogger.db.UploadQueueEntity>> $completion);
    
    @androidx.room.Query(value = "UPDATE upload_queue SET status = :status WHERE id = :id")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object updateStatus(@org.jetbrains.annotations.NotNull()
    java.lang.String id, @org.jetbrains.annotations.NotNull()
    java.lang.String status, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Query(value = "UPDATE upload_queue SET attempts = :attempts WHERE id = :id")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object updateAttempts(@org.jetbrains.annotations.NotNull()
    java.lang.String id, int attempts, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Query(value = "UPDATE upload_queue SET status = \'pending\', attempts = 0 WHERE status = \'failed\'")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object resetFailed(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Query(value = "SELECT COUNT(*) FROM upload_queue WHERE status = :status")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getCountByStatus(@org.jetbrains.annotations.NotNull()
    java.lang.String status, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.Integer> $completion);
    
    @androidx.room.Query(value = "SELECT recordedAt FROM upload_queue WHERE status = \'uploaded\' ORDER BY recordedAt DESC LIMIT 1")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getLastUploadTime(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.String> $completion);
    
    @androidx.room.Query(value = "SELECT * FROM upload_queue WHERE status != \'uploaded\' ORDER BY recordedAt DESC")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getLocal(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.util.List<com.lifelogger.db.UploadQueueEntity>> $completion);
}