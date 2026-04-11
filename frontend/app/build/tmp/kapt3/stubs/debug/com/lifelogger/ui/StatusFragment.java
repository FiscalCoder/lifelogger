package com.lifelogger.ui;

/**
 * Status dashboard: recording toggle, WiFi status, pending/failed queue counts,
 * last upload timestamp, manual drain button, and power-saver mode switch.
 *
 * Refreshes every 5 seconds via [Handler.postDelayed]. Binds to [LifeLoggerService]
 * to relay battery mode changes and trigger immediate drains.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000p\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\t\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0006\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J$\u0010!\u001a\u00020\t2\u0006\u0010\"\u001a\u00020#2\b\u0010$\u001a\u0004\u0018\u00010%2\b\u0010&\u001a\u0004\u0018\u00010\'H\u0016J\b\u0010(\u001a\u00020)H\u0016J\b\u0010*\u001a\u00020)H\u0016J\b\u0010+\u001a\u00020)H\u0016J\u001a\u0010,\u001a\u00020)2\u0006\u0010-\u001a\u00020\t2\b\u0010&\u001a\u0004\u0018\u00010\'H\u0016J\b\u0010.\u001a\u00020)H\u0002R\u0010\u0010\u0003\u001a\u0004\u0018\u00010\u0004X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0005\u001a\u0004\u0018\u00010\u0004X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0007X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0010\u0010\b\u001a\u0004\u0018\u00010\tX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\u000bX\u0082D\u00a2\u0006\u0002\n\u0000R\u000e\u0010\f\u001a\u00020\rX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000e\u001a\u00020\u000fX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0010\u001a\u0004\u0018\u00010\u0011X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0012\u001a\u00020\u0013X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0014\u001a\u0004\u0018\u00010\u0015X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0016\u001a\u0004\u0018\u00010\u0015X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0017\u001a\u0004\u0018\u00010\u0018X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0019\u001a\u0004\u0018\u00010\u0018X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u001a\u001a\u0004\u0018\u00010\u0018X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u001b\u001a\u0004\u0018\u00010\u0018X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u001c\u001a\u0004\u0018\u00010\u0018X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u001d\u001a\u0004\u0018\u00010\u0018X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u001e\u001a\u0004\u0018\u00010\u0018X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u001f\u001a\u0004\u0018\u00010 X\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006/"}, d2 = {"Lcom/lifelogger/ui/StatusFragment;", "Landroidx/fragment/app/Fragment;", "()V", "btnRetryFailed", "Landroid/widget/Button;", "btnUploadNow", "handler", "Landroid/os/Handler;", "indicatorDot", "Landroid/view/View;", "refreshIntervalMs", "", "refreshRunnable", "Ljava/lang/Runnable;", "scope", "Lkotlinx/coroutines/CoroutineScope;", "service", "Lcom/lifelogger/LifeLoggerService;", "serviceConnection", "Landroid/content/ServiceConnection;", "switchPowerSaver", "Lcom/google/android/material/materialswitch/MaterialSwitch;", "switchRecording", "tvChunkInfo", "Landroid/widget/TextView;", "tvFailedCount", "tvLastUpload", "tvPendingCount", "tvRecordingState", "tvUploadedCount", "tvWifiStatus", "uploadQueue", "Lcom/lifelogger/UploadQueue;", "onCreateView", "inflater", "Landroid/view/LayoutInflater;", "container", "Landroid/view/ViewGroup;", "savedInstanceState", "Landroid/os/Bundle;", "onDestroyView", "", "onStart", "onStop", "onViewCreated", "view", "refresh", "app_debug"})
public final class StatusFragment extends androidx.fragment.app.Fragment {
    @org.jetbrains.annotations.Nullable()
    private com.lifelogger.LifeLoggerService service;
    @org.jetbrains.annotations.Nullable()
    private com.lifelogger.UploadQueue uploadQueue;
    private final long refreshIntervalMs = 5000L;
    @org.jetbrains.annotations.NotNull()
    private final android.os.Handler handler = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.CoroutineScope scope = null;
    @org.jetbrains.annotations.Nullable()
    private android.view.View indicatorDot;
    @org.jetbrains.annotations.Nullable()
    private android.widget.TextView tvRecordingState;
    @org.jetbrains.annotations.Nullable()
    private android.widget.TextView tvChunkInfo;
    @org.jetbrains.annotations.Nullable()
    private android.widget.TextView tvWifiStatus;
    @org.jetbrains.annotations.Nullable()
    private android.widget.TextView tvPendingCount;
    @org.jetbrains.annotations.Nullable()
    private android.widget.TextView tvFailedCount;
    @org.jetbrains.annotations.Nullable()
    private android.widget.TextView tvUploadedCount;
    @org.jetbrains.annotations.Nullable()
    private android.widget.TextView tvLastUpload;
    @org.jetbrains.annotations.Nullable()
    private com.google.android.material.materialswitch.MaterialSwitch switchRecording;
    @org.jetbrains.annotations.Nullable()
    private com.google.android.material.materialswitch.MaterialSwitch switchPowerSaver;
    @org.jetbrains.annotations.Nullable()
    private android.widget.Button btnUploadNow;
    @org.jetbrains.annotations.Nullable()
    private android.widget.Button btnRetryFailed;
    @org.jetbrains.annotations.NotNull()
    private final java.lang.Runnable refreshRunnable = null;
    @org.jetbrains.annotations.NotNull()
    private final android.content.ServiceConnection serviceConnection = null;
    
    public StatusFragment() {
        super();
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public android.view.View onCreateView(@org.jetbrains.annotations.NotNull()
    android.view.LayoutInflater inflater, @org.jetbrains.annotations.Nullable()
    android.view.ViewGroup container, @org.jetbrains.annotations.Nullable()
    android.os.Bundle savedInstanceState) {
        return null;
    }
    
    @java.lang.Override()
    public void onViewCreated(@org.jetbrains.annotations.NotNull()
    android.view.View view, @org.jetbrains.annotations.Nullable()
    android.os.Bundle savedInstanceState) {
    }
    
    @java.lang.Override()
    public void onStart() {
    }
    
    @java.lang.Override()
    public void onStop() {
    }
    
    @java.lang.Override()
    public void onDestroyView() {
    }
    
    private final void refresh() {
    }
}