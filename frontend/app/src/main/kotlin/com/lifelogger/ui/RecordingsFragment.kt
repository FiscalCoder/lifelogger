package com.lifelogger.ui

import android.media.MediaPlayer
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lifelogger.R
import com.lifelogger.db.AppDatabase
import com.lifelogger.db.UploadQueueEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Shows all locally stored recordings (pending + failed — not yet uploaded).
 * Each row displays the timestamp, duration, status, and a play/stop button.
 * Only one clip plays at a time; tapping the same row again stops it.
 */
class RecordingsFragment : Fragment() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var recyclerView: RecyclerView? = null
    private var emptyView: TextView? = null
    private var adapter: RecordingAdapter? = null

    private var mediaPlayer: MediaPlayer? = null
    private var playingId: String? = null

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_recordings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = view.findViewById(R.id.rv_recordings)
        emptyView = view.findViewById(R.id.tv_empty_recordings)
        recyclerView?.layoutManager = LinearLayoutManager(requireContext())
        recyclerView?.addItemDecoration(
            DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
        )
    }

    override fun onResume() {
        super.onResume()
        loadRecordings()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopPlayer()
        recyclerView = null
        emptyView = null
        adapter = null
    }

    // ── Data ──────────────────────────────────────────────────────────────────

    private fun loadRecordings() {
        val db = AppDatabase.getInstance(requireContext())
        scope.launch {
            val items = db.uploadQueueDao().getLocal()
            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                if (items.isEmpty()) {
                    recyclerView?.visibility = View.GONE
                    emptyView?.visibility = View.VISIBLE
                } else {
                    recyclerView?.visibility = View.VISIBLE
                    emptyView?.visibility = View.GONE
                    adapter = RecordingAdapter(items, ::togglePlay)
                    recyclerView?.adapter = adapter
                }
            }
        }
    }

    // ── Playback ──────────────────────────────────────────────────────────────

    private fun togglePlay(item: UploadQueueEntity) {
        if (playingId == item.id) {
            stopPlayer()
            adapter?.setPlaying(null)
            return
        }
        stopPlayer()
        val file = File(item.filePath)
        if (!file.exists()) return

        runCatching {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
                setOnCompletionListener {
                    playingId = null
                    adapter?.setPlaying(null)
                }
            }
            playingId = item.id
            adapter?.setPlaying(item.id)
        }
    }

    private fun stopPlayer() {
        runCatching { mediaPlayer?.stop() }
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        playingId = null
    }

    // ── Adapter ───────────────────────────────────────────────────────────────

    private inner class RecordingAdapter(
        private val items: List<UploadQueueEntity>,
        private val onPlayToggle: (UploadQueueEntity) -> Unit
    ) : RecyclerView.Adapter<RecordingAdapter.ViewHolder>() {

        private var currentPlayingId: String? = null

        fun setPlaying(id: String?) {
            val oldPos = items.indexOfFirst { it.id == currentPlayingId }
            val newPos = items.indexOfFirst { it.id == id }
            currentPlayingId = id
            if (oldPos >= 0) notifyItemChanged(oldPos)
            if (newPos >= 0) notifyItemChanged(newPos)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_recording, parent, false)
            return ViewHolder(view)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) =
            holder.bind(items[position])

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val tvTime: TextView = view.findViewById(R.id.tv_rec_time)
            private val tvDuration: TextView = view.findViewById(R.id.tv_rec_duration)
            private val tvStatus: TextView = view.findViewById(R.id.tv_rec_status)
            private val btnPlay: ImageButton = view.findViewById(R.id.btn_play)

            fun bind(item: UploadQueueEntity) {
                tvTime.text = formatTime(item.recordedAt)
                tvDuration.text = formatDuration(item.durationSeconds)

                val fileExists = File(item.filePath).exists()
                tvStatus.text = when {
                    !fileExists -> "file missing"
                    item.status == "failed" -> "failed"
                    else -> "pending"
                }

                val isPlaying = currentPlayingId == item.id
                btnPlay.setImageResource(
                    if (isPlaying) android.R.drawable.ic_media_pause
                    else android.R.drawable.ic_media_play
                )
                btnPlay.isEnabled = fileExists
                btnPlay.alpha = if (fileExists) 1f else 0.35f
                btnPlay.setOnClickListener { onPlayToggle(item) }
            }

            private fun formatTime(iso: String): String = runCatching {
                val instant = Instant.parse(iso)
                DateTimeFormatter.ofPattern("MMM d, HH:mm:ss")
                    .withZone(ZoneId.systemDefault())
                    .format(instant)
            }.getOrElse { iso }

            private fun formatDuration(secs: Float): String {
                val s = secs.toInt()
                return "%d:%02d".format(s / 60, s % 60)
            }
        }
    }
}
