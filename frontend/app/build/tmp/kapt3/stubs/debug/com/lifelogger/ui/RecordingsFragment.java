package com.lifelogger.ui;

/**
 * Shows all locally stored recordings (pending + failed — not yet uploaded).
 * Each row displays the timestamp, duration, status, and a play/stop button.
 * Only one clip plays at a time; tapping the same row again stops it.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000X\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0002\b\u0002\u0018\u00002\u00020\u0001:\u0001!B\u0005\u00a2\u0006\u0002\u0010\u0002J\b\u0010\u000f\u001a\u00020\u0010H\u0002J$\u0010\u0011\u001a\u00020\u00122\u0006\u0010\u0013\u001a\u00020\u00142\b\u0010\u0015\u001a\u0004\u0018\u00010\u00162\b\u0010\u0017\u001a\u0004\u0018\u00010\u0018H\u0016J\b\u0010\u0019\u001a\u00020\u0010H\u0016J\b\u0010\u001a\u001a\u00020\u0010H\u0016J\u001a\u0010\u001b\u001a\u00020\u00102\u0006\u0010\u001c\u001a\u00020\u00122\b\u0010\u0017\u001a\u0004\u0018\u00010\u0018H\u0016J\b\u0010\u001d\u001a\u00020\u0010H\u0002J\u0010\u0010\u001e\u001a\u00020\u00102\u0006\u0010\u001f\u001a\u00020 H\u0002R\u0014\u0010\u0003\u001a\b\u0018\u00010\u0004R\u00020\u0000X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0005\u001a\u0004\u0018\u00010\u0006X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0007\u001a\u0004\u0018\u00010\bX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\t\u001a\u0004\u0018\u00010\nX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u000b\u001a\u0004\u0018\u00010\fX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\r\u001a\u00020\u000eX\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\""}, d2 = {"Lcom/lifelogger/ui/RecordingsFragment;", "Landroidx/fragment/app/Fragment;", "()V", "adapter", "Lcom/lifelogger/ui/RecordingsFragment$RecordingAdapter;", "emptyView", "Landroid/widget/TextView;", "mediaPlayer", "Landroid/media/MediaPlayer;", "playingId", "", "recyclerView", "Landroidx/recyclerview/widget/RecyclerView;", "scope", "Lkotlinx/coroutines/CoroutineScope;", "loadRecordings", "", "onCreateView", "Landroid/view/View;", "inflater", "Landroid/view/LayoutInflater;", "container", "Landroid/view/ViewGroup;", "savedInstanceState", "Landroid/os/Bundle;", "onDestroyView", "onResume", "onViewCreated", "view", "stopPlayer", "togglePlay", "item", "Lcom/lifelogger/db/UploadQueueEntity;", "RecordingAdapter", "app_debug"})
public final class RecordingsFragment extends androidx.fragment.app.Fragment {
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.CoroutineScope scope = null;
    @org.jetbrains.annotations.Nullable()
    private androidx.recyclerview.widget.RecyclerView recyclerView;
    @org.jetbrains.annotations.Nullable()
    private android.widget.TextView emptyView;
    @org.jetbrains.annotations.Nullable()
    private com.lifelogger.ui.RecordingsFragment.RecordingAdapter adapter;
    @org.jetbrains.annotations.Nullable()
    private android.media.MediaPlayer mediaPlayer;
    @org.jetbrains.annotations.Nullable()
    private java.lang.String playingId;
    
    public RecordingsFragment() {
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
    public void onResume() {
    }
    
    @java.lang.Override()
    public void onDestroyView() {
    }
    
    private final void loadRecordings() {
    }
    
    private final void togglePlay(com.lifelogger.db.UploadQueueEntity item) {
    }
    
    private final void stopPlayer() {
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000>\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\b\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0005\b\u0082\u0004\u0018\u00002\u0010\u0012\f\u0012\n0\u0002R\u00060\u0000R\u00020\u00030\u0001:\u0001\u0018B\'\u0012\f\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00060\u0005\u0012\u0012\u0010\u0007\u001a\u000e\u0012\u0004\u0012\u00020\u0006\u0012\u0004\u0012\u00020\t0\b\u00a2\u0006\u0002\u0010\nJ\b\u0010\r\u001a\u00020\u000eH\u0016J \u0010\u000f\u001a\u00020\t2\u000e\u0010\u0010\u001a\n0\u0002R\u00060\u0000R\u00020\u00032\u0006\u0010\u0011\u001a\u00020\u000eH\u0016J \u0010\u0012\u001a\n0\u0002R\u00060\u0000R\u00020\u00032\u0006\u0010\u0013\u001a\u00020\u00142\u0006\u0010\u0015\u001a\u00020\u000eH\u0016J\u0010\u0010\u0016\u001a\u00020\t2\b\u0010\u0017\u001a\u0004\u0018\u00010\fR\u0010\u0010\u000b\u001a\u0004\u0018\u00010\fX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00060\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001a\u0010\u0007\u001a\u000e\u0012\u0004\u0012\u00020\u0006\u0012\u0004\u0012\u00020\t0\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0019"}, d2 = {"Lcom/lifelogger/ui/RecordingsFragment$RecordingAdapter;", "Landroidx/recyclerview/widget/RecyclerView$Adapter;", "Lcom/lifelogger/ui/RecordingsFragment$RecordingAdapter$ViewHolder;", "Lcom/lifelogger/ui/RecordingsFragment;", "items", "", "Lcom/lifelogger/db/UploadQueueEntity;", "onPlayToggle", "Lkotlin/Function1;", "", "(Lcom/lifelogger/ui/RecordingsFragment;Ljava/util/List;Lkotlin/jvm/functions/Function1;)V", "currentPlayingId", "", "getItemCount", "", "onBindViewHolder", "holder", "position", "onCreateViewHolder", "parent", "Landroid/view/ViewGroup;", "viewType", "setPlaying", "id", "ViewHolder", "app_debug"})
    final class RecordingAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<com.lifelogger.ui.RecordingsFragment.RecordingAdapter.ViewHolder> {
        @org.jetbrains.annotations.NotNull()
        private final java.util.List<com.lifelogger.db.UploadQueueEntity> items = null;
        @org.jetbrains.annotations.NotNull()
        private final kotlin.jvm.functions.Function1<com.lifelogger.db.UploadQueueEntity, kotlin.Unit> onPlayToggle = null;
        @org.jetbrains.annotations.Nullable()
        private java.lang.String currentPlayingId;
        
        public RecordingAdapter(@org.jetbrains.annotations.NotNull()
        java.util.List<com.lifelogger.db.UploadQueueEntity> items, @org.jetbrains.annotations.NotNull()
        kotlin.jvm.functions.Function1<? super com.lifelogger.db.UploadQueueEntity, kotlin.Unit> onPlayToggle) {
            super();
        }
        
        public final void setPlaying(@org.jetbrains.annotations.Nullable()
        java.lang.String id) {
        }
        
        @java.lang.Override()
        @org.jetbrains.annotations.NotNull()
        public com.lifelogger.ui.RecordingsFragment.RecordingAdapter.ViewHolder onCreateViewHolder(@org.jetbrains.annotations.NotNull()
        android.view.ViewGroup parent, int viewType) {
            return null;
        }
        
        @java.lang.Override()
        public int getItemCount() {
            return 0;
        }
        
        @java.lang.Override()
        public void onBindViewHolder(@org.jetbrains.annotations.NotNull()
        com.lifelogger.ui.RecordingsFragment.RecordingAdapter.ViewHolder holder, int position) {
        }
        
        @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000:\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u0007\n\u0002\b\u0003\b\u0086\u0004\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u000e\u0010\u000b\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\u000eJ\u0010\u0010\u000f\u001a\u00020\u00102\u0006\u0010\u0011\u001a\u00020\u0012H\u0002J\u0010\u0010\u0013\u001a\u00020\u00102\u0006\u0010\u0014\u001a\u00020\u0010H\u0002R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0015"}, d2 = {"Lcom/lifelogger/ui/RecordingsFragment$RecordingAdapter$ViewHolder;", "Landroidx/recyclerview/widget/RecyclerView$ViewHolder;", "view", "Landroid/view/View;", "(Lcom/lifelogger/ui/RecordingsFragment$RecordingAdapter;Landroid/view/View;)V", "btnPlay", "Landroid/widget/ImageButton;", "tvDuration", "Landroid/widget/TextView;", "tvStatus", "tvTime", "bind", "", "item", "Lcom/lifelogger/db/UploadQueueEntity;", "formatDuration", "", "secs", "", "formatTime", "iso", "app_debug"})
        public final class ViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            @org.jetbrains.annotations.NotNull()
            private final android.widget.TextView tvTime = null;
            @org.jetbrains.annotations.NotNull()
            private final android.widget.TextView tvDuration = null;
            @org.jetbrains.annotations.NotNull()
            private final android.widget.TextView tvStatus = null;
            @org.jetbrains.annotations.NotNull()
            private final android.widget.ImageButton btnPlay = null;
            
            public ViewHolder(@org.jetbrains.annotations.NotNull()
            android.view.View view) {
                super(null);
            }
            
            public final void bind(@org.jetbrains.annotations.NotNull()
            com.lifelogger.db.UploadQueueEntity item) {
            }
            
            private final java.lang.String formatTime(java.lang.String iso) {
                return null;
            }
            
            private final java.lang.String formatDuration(float secs) {
                return null;
            }
        }
    }
}