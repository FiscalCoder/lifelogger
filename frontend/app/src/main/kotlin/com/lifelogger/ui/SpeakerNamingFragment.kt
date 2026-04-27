package com.lifelogger.ui

import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lifelogger.R
import com.lifelogger.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

data class UnknownSpeaker(
    val id: String,
    val tempName: String,
    val audioSample: String?,
    val recordedAt: String
)

/**
 * Native speaker review screen for naming unknown voices or marking media/public audio.
 */
class SpeakerNamingFragment : Fragment() {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private var recyclerView: RecyclerView? = null
    private var emptyView: TextView? = null
    private var errorView: TextView? = null
    private var adapter: UnknownSpeakerAdapter? = null
    private var mediaPlayer: MediaPlayer? = null
    private var activeAudioId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_speaker_naming, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = view.findViewById(R.id.rv_unknown_speakers)
        emptyView = view.findViewById(R.id.tv_empty_unknown_speakers)
        errorView = view.findViewById(R.id.tv_speaker_error)
        adapter = UnknownSpeakerAdapter(
            onPlayToggle = ::toggleSamplePlayback,
            onNameSpeaker = ::nameUnknownSpeaker,
            onMarkMedia = ::markUnknownAsMedia
        )
        recyclerView?.layoutManager = LinearLayoutManager(requireContext())
        recyclerView?.addItemDecoration(
            DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
        )
        recyclerView?.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        loadUnknownSpeakers()
    }

    override fun onPause() {
        super.onPause()
        stopPlayer()
    }

    override fun onDestroyView() {
        stopPlayer()
        recyclerView = null
        emptyView = null
        errorView = null
        adapter = null
        super.onDestroyView()
    }

    private fun loadUnknownSpeakers() {
        showError(null)
        emptyView?.text = getString(R.string.label_loading_unknown_speakers)
        emptyView?.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching { fetchUnknownSpeakers() }
            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = { items ->
                        adapter?.submitItems(items)
                        recyclerView?.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
                        emptyView?.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                        emptyView?.text = getString(R.string.label_no_unknown_speakers)
                    },
                    onFailure = {
                        recyclerView?.visibility = View.GONE
                        emptyView?.visibility = View.GONE
                        showError(it.message ?: getString(R.string.error_backend_unavailable))
                    }
                )
            }
        }
    }

    private fun fetchUnknownSpeakers(): List<UnknownSpeaker> {
        val request = Request.Builder()
            .url(AppConfig.UNKNOWN_SPEAKERS_ENDPOINT)
            .header("Authorization", AppConfig.AUTHORIZATION_HEADER)
            .get()
            .build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) throw RuntimeException("HTTP ${response.code}")
        val body = response.body?.string() ?: return emptyList()
        val arr = JSONArray(body)
        return List(arr.length()) { index ->
            val obj = arr.getJSONObject(index)
            UnknownSpeaker(
                id = obj.optString("id"),
                tempName = obj.optString("tempName", "unknown"),
                audioSample = obj.nullableString("audioSample"),
                recordedAt = obj.optString("recordedAt", "")
            )
        }.filter { it.id.isNotBlank() }
    }

    private fun nameUnknownSpeaker(
        unknown: UnknownSpeaker,
        name: String,
        onComplete: (String?) -> Unit
    ) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val body = JSONObject()
                    .put("unknownId", unknown.id)
                    .put("name", name)
                    .toString()
                postJson(AppConfig.SPEAKER_NAME_ENDPOINT, body)
            }
            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = {
                        stopPlayerIfActive(unknown.id)
                        adapter?.removeItem(unknown.id)
                        onComplete(null)
                        loadUnknownSpeakers()
                    },
                    onFailure = { onComplete(it.message ?: "Could not save speaker") }
                )
            }
        }
    }

    private fun markUnknownAsMedia(
        unknown: UnknownSpeaker,
        onComplete: (String?) -> Unit
    ) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching { postJson(AppConfig.markMediaEndpoint(unknown.id), "{}") }
            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = {
                        stopPlayerIfActive(unknown.id)
                        adapter?.removeItem(unknown.id)
                        onComplete(null)
                        loadUnknownSpeakers()
                    },
                    onFailure = { onComplete(it.message ?: "Could not mark media") }
                )
            }
        }
    }

    private fun postJson(url: String, json: String) {
        val body = json.toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(url)
            .header("Authorization", AppConfig.AUTHORIZATION_HEADER)
            .post(body)
            .build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            val message = response.body?.string()?.let { parseError(it) } ?: "HTTP ${response.code}"
            throw RuntimeException(message)
        }
    }

    private fun parseError(body: String): String =
        runCatching { JSONObject(body).optString("error") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: body.ifBlank { "Request failed" }

    private fun toggleSamplePlayback(unknown: UnknownSpeaker) {
        if (activeAudioId == unknown.id) {
            stopPlayer()
            adapter?.setActiveAudio(null)
            return
        }
        val sample = unknown.audioSample ?: return
        stopPlayer()
        activeAudioId = unknown.id
        adapter?.setActiveAudio(unknown.id)
        runCatching {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(
                    requireContext(),
                    Uri.parse(AppConfig.audioSampleUrl(sample)),
                    AppConfig.AUTH_HEADERS
                )
                setOnPreparedListener { it.start() }
                setOnCompletionListener {
                    stopPlayer()
                    adapter?.setActiveAudio(null)
                }
                setOnErrorListener { _, _, _ ->
                    showError(getString(R.string.error_audio_sample_unavailable))
                    stopPlayer()
                    adapter?.setActiveAudio(null)
                    true
                }
                prepareAsync()
            }
        }.onFailure {
            showError(it.message ?: getString(R.string.error_audio_sample_unavailable))
            stopPlayer()
            adapter?.setActiveAudio(null)
        }
    }

    private fun stopPlayerIfActive(id: String) {
        if (activeAudioId == id) {
            stopPlayer()
            adapter?.setActiveAudio(null)
        }
    }

    private fun stopPlayer() {
        runCatching { mediaPlayer?.stop() }
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        activeAudioId = null
    }

    private fun showError(message: String?) {
        errorView?.text = message.orEmpty()
        errorView?.visibility = if (message.isNullOrBlank()) View.GONE else View.VISIBLE
    }
}

private class UnknownSpeakerAdapter(
    private val onPlayToggle: (UnknownSpeaker) -> Unit,
    private val onNameSpeaker: (UnknownSpeaker, String, (String?) -> Unit) -> Unit,
    private val onMarkMedia: (UnknownSpeaker, (String?) -> Unit) -> Unit
) : RecyclerView.Adapter<UnknownSpeakerAdapter.ViewHolder>() {

    private val items = mutableListOf<UnknownSpeaker>()
    private var activeAudioId: String? = null

    fun submitItems(newItems: List<UnknownSpeaker>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun removeItem(id: String) {
        val index = items.indexOfFirst { it.id == id }
        if (index < 0) return
        items.removeAt(index)
        notifyItemRemoved(index)
    }

    fun setActiveAudio(id: String?) {
        val oldIndex = items.indexOfFirst { it.id == activeAudioId }
        val newIndex = items.indexOfFirst { it.id == id }
        activeAudioId = id
        if (oldIndex >= 0) notifyItemChanged(oldIndex)
        if (newIndex >= 0) notifyItemChanged(newIndex)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_unknown_speaker, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.tv_unknown_temp_name)
        private val recordedAt: TextView = view.findViewById(R.id.tv_unknown_recorded_at)
        private val status: TextView = view.findViewById(R.id.tv_unknown_status)
        private val nameInput: EditText = view.findViewById(R.id.et_unknown_name)
        private val mediaCheck: CheckBox = view.findViewById(R.id.cb_media_public)
        private val playButton: ImageButton = view.findViewById(R.id.btn_unknown_play)
        private val saveButton: Button = view.findViewById(R.id.btn_unknown_save)
        private val skipButton: Button = view.findViewById(R.id.btn_unknown_skip)

        fun bind(item: UnknownSpeaker) {
            title.text = item.tempName
            recordedAt.text = formatDate(item.recordedAt)
            status.text = ""
            nameInput.setText("")
            mediaCheck.setOnCheckedChangeListener(null)
            mediaCheck.isChecked = false
            setBusy(false)
            bindPlayButton(item)
            mediaCheck.setOnCheckedChangeListener { _, checked ->
                nameInput.isEnabled = !checked
                saveButton.text = itemView.context.getString(
                    if (checked) R.string.action_mark_media else R.string.action_save_speaker
                )
            }
            saveButton.setOnClickListener { save(item) }
            skipButton.setOnClickListener { removeItem(item.id) }
        }

        private fun bindPlayButton(item: UnknownSpeaker) {
            val canPlay = item.audioSample != null
            val isPlaying = activeAudioId == item.id
            playButton.isEnabled = canPlay
            playButton.alpha = if (canPlay) 1f else 0.35f
            playButton.setImageResource(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )
            playButton.setOnClickListener { if (canPlay) onPlayToggle(item) }
        }

        private fun save(item: UnknownSpeaker) {
            if (mediaCheck.isChecked) {
                setBusy(true)
                onMarkMedia(item) { error -> handleComplete(error) }
                return
            }
            val name = nameInput.text.toString().trim()
            if (name.isBlank()) {
                status.text = itemView.context.getString(R.string.error_speaker_name_required)
                return
            }
            setBusy(true)
            onNameSpeaker(item, name) { error -> handleComplete(error) }
        }

        private fun handleComplete(error: String?) {
            if (error == null) return
            setBusy(false)
            status.text = error
        }

        private fun setBusy(busy: Boolean) {
            saveButton.isEnabled = !busy
            skipButton.isEnabled = !busy
            mediaCheck.isEnabled = !busy
            nameInput.isEnabled = !busy && !mediaCheck.isChecked
            saveButton.text = itemView.context.getString(
                if (mediaCheck.isChecked) R.string.action_mark_media else R.string.action_save_speaker
            )
        }
    }
}

private fun JSONObject.nullableString(name: String): String? =
    if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

private fun formatDate(iso: String): String = runCatching {
    val instant = Instant.parse(iso)
    DateTimeFormatter.ofPattern("MMM d, HH:mm:ss")
        .withZone(ZoneId.systemDefault())
        .format(instant)
}.getOrElse { iso }
