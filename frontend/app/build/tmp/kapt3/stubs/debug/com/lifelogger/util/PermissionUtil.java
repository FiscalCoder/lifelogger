package com.lifelogger.util;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000,\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\bJ\u000e\u0010\t\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\fJ\u000e\u0010\r\u001a\u00020\u00062\u0006\u0010\u000b\u001a\u00020\fR\u000e\u0010\u0003\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u000e"}, d2 = {"Lcom/lifelogger/util/PermissionUtil;", "", "()V", "REQUEST_CODE_RECORD_AUDIO", "", "hasRecordAudioPermission", "", "context", "Landroid/content/Context;", "requestRecordAudioPermission", "", "activity", "Landroid/app/Activity;", "shouldShowRationale", "app_debug"})
public final class PermissionUtil {
    public static final int REQUEST_CODE_RECORD_AUDIO = 1001;
    @org.jetbrains.annotations.NotNull()
    public static final com.lifelogger.util.PermissionUtil INSTANCE = null;
    
    private PermissionUtil() {
        super();
    }
    
    public final boolean hasRecordAudioPermission(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        return false;
    }
    
    /**
     * Launches the system permission dialog for RECORD_AUDIO.
     * The result is delivered to [Activity.onRequestPermissionsResult].
     */
    public final void requestRecordAudioPermission(@org.jetbrains.annotations.NotNull()
    android.app.Activity activity) {
    }
    
    public final boolean shouldShowRationale(@org.jetbrains.annotations.NotNull()
    android.app.Activity activity) {
        return false;
    }
}