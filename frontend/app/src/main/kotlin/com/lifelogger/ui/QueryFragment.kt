package com.lifelogger.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
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
    val speaker: String,
    val text: String,
    val recordedAt: String
)

class QueryFragment : Fragment() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private var etQuery: EditText? = null
    private var btnSearch: Button? = null
    private var tvError: TextView? = null
    private var rvResults: RecyclerView? = null
    private var adapter: QueryResultAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_query, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etQuery = view.findViewById(R.id.et_query)
        btnSearch = view.findViewById(R.id.btn_search)
        tvError = view.findViewById(R.id.tv_error)
        rvResults = view.findViewById(R.id.rv_results)

        adapter = QueryResultAdapter()
        rvResults?.layoutManager = LinearLayoutManager(requireContext())
        rvResults?.adapter = adapter

        btnSearch?.setOnClickListener {
            val query = etQuery?.text?.toString()?.trim() ?: return@setOnClickListener
            if (query.isNotEmpty()) performSearch(query)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        etQuery = null
        btnSearch = null
        tvError = null
        rvResults = null
        adapter = null
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
                    onFailure = { showError("Backend not yet available") }
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
        if (!response.isSuccessful) {
            // 404 or any non-2xx → treat as backend unavailable
            throw RuntimeException("HTTP ${response.code}")
        }

        val responseBody = response.body?.string() ?: return emptyList()
        return parseResults(responseBody)
    }

    private fun parseResults(json: String): List<QueryResult> {
        val arr: JSONArray = JSONObject(json).optJSONArray("results") ?: return emptyList()
        return List(arr.length()) { i ->
            val obj = arr.getJSONObject(i)
            QueryResult(
                speaker = obj.optString("speaker", "Unknown"),
                text = obj.optString("text", ""),
                recordedAt = obj.optString("recorded_at", "")
            )
        }
    }

    private fun showError(message: String) {
        tvError?.text = message
        tvError?.visibility = View.VISIBLE
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// RecyclerView Adapter
// ─────────────────────────────────────────────────────────────────────────────

private val DIFF = object : DiffUtil.ItemCallback<QueryResult>() {
    override fun areItemsTheSame(old: QueryResult, new: QueryResult) =
        old.recordedAt == new.recordedAt && old.speaker == new.speaker
    override fun areContentsTheSame(old: QueryResult, new: QueryResult) = old == new
}

class QueryResultAdapter : ListAdapter<QueryResult, QueryResultAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSpeaker: TextView = view.findViewById(R.id.tv_result_speaker)
        val tvText: TextView = view.findViewById(R.id.tv_result_text)
        val tvRecordedAt: TextView = view.findViewById(R.id.tv_result_recorded_at)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_query_result, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.tvSpeaker.text = item.speaker
        holder.tvText.text = item.text
        holder.tvRecordedAt.text = item.recordedAt
    }
}
