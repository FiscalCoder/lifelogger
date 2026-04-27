package com.lifelogger.ui

import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lifelogger.R
import com.lifelogger.config.AppConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class QueryResult(
    val id: String,
    val speaker: String,
    val text: String,
    val recordedAt: String,
    val sourceFile: String,
    val sourceKind: String,
    val startSeconds: Double?,
    val endSeconds: Double?
)

class QueryFragment : Fragment() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private var etQuery: EditText? = null
    private var btnSearch: Button? = null
    private var tvError: TextView? = null
    private var rvResults: RecyclerView? = null
    private var adapter: QueryResultAdapter? = null
    private var mediaPlayer: MediaPlayer? = null
    private var playingSegmentId: String? = null
    private var stopRunnable: Runnable? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_query, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        etQuery = view.findViewById(R.id.et_query)
        btnSearch = view.findViewById(R.id.btn_search)
        tvError = view.findViewById(R.id.tv_error)
        rvResults = view.findViewById(R.id.rv_results)
        adapter = QueryResultAdapter(::toggleSegmentPlayback)
        rvResults?.layoutManager = LinearLayoutManager(requireContext())
        rvResults?.adapter = adapter
        btnSearch?.setOnClickListener {
            val query = etQuery?.text?.toString()?.trim() ?: return@setOnClickListener
            if (query.isNotEmpty()) performSearch(query)
        }
    }

    override fun onDestroyView() {
        stopSegmentPlayback()
        handler.removeCallbacksAndMessages(null)
        etQuery = null
        btnSearch = null
        tvError = null
        rvResults = null
        adapter = null
        super.onDestroyView()
    }

    private fun performSearch(query: String) {
        tvError?.visibility = View.GONE
        scope.launch {
            val results = runCatching { fetchResults(query) }
            withContext(Dispatchers.Main) {
                results.fold(
                    onSuccess = { list ->
                        adapter?.submitList(list)
                        if (list.isEmpty()) showError("No results found.")
                    },
                    onFailure = { showError(getString(R.string.error_backend_unavailable)) }
                )
            }
        }
    }

    private fun fetchResults(query: String): List<QueryResult> {
        val json = JSONObject().put("query", query).toString()
        val body = json.toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(AppConfig.QUERY_ENDPOINT)
            .header("Authorization", AppConfig.AUTHORIZATION_HEADER)
            .post(body)
            .build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) throw RuntimeException("HTTP ${response.code}")
        val responseBody = response.body?.string() ?: return emptyList()
        return parseResults(responseBody)
    }

    private fun parseResults(json: String): List<QueryResult> {
        val arr: JSONArray = JSONObject(json).optJSONArray("results") ?: return emptyList()
        return List(arr.length()) { index ->
            val obj = arr.getJSONObject(index)
            QueryResult(
                id = obj.optString("id"),
                speaker = obj.optString("speaker", "Unknown"),
                text = obj.optString("text", ""),
                recordedAt = obj.optString("recordedAt", obj.optString("recorded_at", "")),
                sourceFile = obj.optString("sourceFile", obj.optString("source_file", "")),
                sourceKind = obj.optString("sourceKind", obj.optString("source_kind", "unknown_mic")),
                startSeconds = obj.optionalDouble("startSeconds", "start_seconds"),
                endSeconds = obj.optionalDouble("endSeconds", "end_seconds")
            )
        }.filter { it.id.isNotBlank() }
    }

    private fun toggleSegmentPlayback(item: QueryResult) {
        if (playingSegmentId == item.id) {
            stopSegmentPlayback()
            adapter?.setPlayingSegment(null)
            return
        }
        stopSegmentPlayback()
        playingSegmentId = item.id
        adapter?.setPlayingSegment(item.id)
        runCatching {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(
                    requireContext(),
                    Uri.parse(AppConfig.segmentAudioUrl(item.id)),
                    AppConfig.AUTH_HEADERS
                )
                setOnPreparedListener { player -> startPreparedSegment(player, item) }
                setOnCompletionListener {
                    stopSegmentPlayback()
                    adapter?.setPlayingSegment(null)
                }
                setOnErrorListener { _, _, _ ->
                    showError(getString(R.string.error_source_audio_unavailable))
                    stopSegmentPlayback()
                    adapter?.setPlayingSegment(null)
                    true
                }
                prepareAsync()
            }
        }.onFailure {
            showError(it.message ?: getString(R.string.error_source_audio_unavailable))
            stopSegmentPlayback()
            adapter?.setPlayingSegment(null)
        }
    }

    private fun startPreparedSegment(player: MediaPlayer, item: QueryResult) {
        val startMs = ((item.startSeconds ?: 0.0) * 1_000).toLong().coerceAtLeast(0L)
        if (startMs > 0L) {
            player.setOnSeekCompleteListener {
                it.start()
                scheduleStopAtSegmentEnd(item)
            }
            player.seekTo(startMs, MediaPlayer.SEEK_CLOSEST)
            return
        }
        player.start()
        scheduleStopAtSegmentEnd(item)
    }

    private fun scheduleStopAtSegmentEnd(item: QueryResult) {
        val start = item.startSeconds ?: return
        val end = item.endSeconds ?: return
        val durationMs = ((end - start) * 1_000).toLong()
        if (durationMs <= 0L) return
        stopRunnable = Runnable {
            stopSegmentPlayback()
            adapter?.setPlayingSegment(null)
        }
        handler.postDelayed(stopRunnable!!, durationMs + 250L)
    }

    private fun stopSegmentPlayback() {
        stopRunnable?.let { handler.removeCallbacks(it) }
        stopRunnable = null
        runCatching { mediaPlayer?.stop() }
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        playingSegmentId = null
    }

    private fun showError(message: String) {
        tvError?.text = message
        tvError?.visibility = View.VISIBLE
    }
}

private val QUERY_DIFF = object : DiffUtil.ItemCallback<QueryResult>() {
    override fun areItemsTheSame(old: QueryResult, new: QueryResult) = old.id == new.id
    override fun areContentsTheSame(old: QueryResult, new: QueryResult) = old == new
}

class QueryResultAdapter(
    private val onPlayToggle: (QueryResult) -> Unit
) : ListAdapter<QueryResult, QueryResultAdapter.ViewHolder>(QUERY_DIFF) {

    private var playingSegmentId: String? = null

    fun setPlayingSegment(id: String?) {
        val oldIndex = currentList.indexOfFirst { it.id == playingSegmentId }
        val newIndex = currentList.indexOfFirst { it.id == id }
        playingSegmentId = id
        if (oldIndex >= 0) notifyItemChanged(oldIndex)
        if (newIndex >= 0) notifyItemChanged(newIndex)
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSpeaker: TextView = view.findViewById(R.id.tv_result_speaker)
        val tvText: TextView = view.findViewById(R.id.tv_result_text)
        val tvRecordedAt: TextView = view.findViewById(R.id.tv_result_recorded_at)
        val tvSource: TextView = view.findViewById(R.id.tv_result_source)
        val btnPlay: ImageButton = view.findViewById(R.id.btn_result_play)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_query_result, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val isPlaying = playingSegmentId == item.id
        holder.tvSpeaker.text = item.speaker
        holder.tvText.text = item.text
        holder.tvRecordedAt.text = item.recordedAt
        holder.tvSource.text = sourceLabel(item)
        holder.btnPlay.setImageResource(
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        )
        holder.btnPlay.setOnClickListener { onPlayToggle(item) }
    }

    private fun sourceLabel(item: QueryResult): String {
        val start = item.startSeconds?.let { "%.1fs".format(it) } ?: "?"
        val end = item.endSeconds?.let { "%.1fs".format(it) } ?: "?"
        return "${item.sourceKind} | ${item.sourceFile} | $start-$end"
    }
}

private fun JSONObject.optionalDouble(primary: String, fallback: String): Double? {
    val value = when {
        !isNull(primary) -> optDouble(primary, Double.NaN)
        !isNull(fallback) -> optDouble(fallback, Double.NaN)
        else -> Double.NaN
    }
    return value.takeUnless { it.isNaN() }
}
