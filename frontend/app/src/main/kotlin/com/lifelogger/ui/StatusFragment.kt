package com.lifelogger.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.graphics.drawable.GradientDrawable
import android.widget.Button
import android.widget.CompoundButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.materialswitch.MaterialSwitch
import com.lifelogger.LifeLoggerService
import com.lifelogger.R
import com.lifelogger.UploadQueue
import com.lifelogger.util.NetworkUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Status dashboard: recording toggle, WiFi status, pending/failed queue counts,
 * last upload timestamp, manual drain button, and power-saver mode switch.
 *
 * Refreshes every second via [Handler.postDelayed]. Binds to [LifeLoggerService]
 * to relay battery mode changes and trigger immediate drains.
 */
class StatusFragment : Fragment() {

    private var service: LifeLoggerService? = null
    private var uploadQueue: UploadQueue? = null
    private val refreshIntervalMs = 1_000L

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // View refs — set in onViewCreated, cleared in onDestroyView
    private var indicatorDot: View? = null
    private var tvRecordingState: TextView? = null
    private var tvChunkInfo: TextView? = null
    private var tvWifiStatus: TextView? = null
    private var tvPendingCount: TextView? = null
    private var tvFailedCount: TextView? = null
    private var tvUploadedCount: TextView? = null
    private var tvLastUpload: TextView? = null
    private var switchRecording: MaterialSwitch? = null
    private var switchPowerSaver: MaterialSwitch? = null
    private var btnUploadNow: Button? = null
    private var btnRetryFailed: Button? = null

    private val refreshRunnable = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, refreshIntervalMs)
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val lb = binder as? LifeLoggerService.LocalBinder ?: return
            service = lb.service
            uploadQueue = UploadQueue(requireContext())
            refresh()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_status, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        indicatorDot = view.findViewById(R.id.indicator_dot)
        tvRecordingState = view.findViewById(R.id.tv_recording_state)
        tvChunkInfo = view.findViewById(R.id.tv_chunk_info)
        tvWifiStatus = view.findViewById(R.id.tv_wifi_status)
        tvPendingCount = view.findViewById(R.id.tv_pending_count)
        tvFailedCount = view.findViewById(R.id.tv_failed_count)
        tvUploadedCount = view.findViewById(R.id.tv_uploaded_count)
        tvLastUpload = view.findViewById(R.id.tv_last_upload)
        switchRecording = view.findViewById(R.id.switch_recording)
        switchPowerSaver = view.findViewById(R.id.switch_power_saver)
        btnUploadNow = view.findViewById(R.id.btn_upload_now)
        btnRetryFailed = view.findViewById(R.id.btn_retry_failed)

        switchRecording?.setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
            val intent = Intent(requireContext(), LifeLoggerService::class.java)
            if (checked) ContextCompat.startForegroundService(requireContext(), intent)
            else requireContext().stopService(intent)
        }

        switchPowerSaver?.setOnCheckedChangeListener { _: CompoundButton, powerSaverOn: Boolean ->
            service?.setBatteryMode(!powerSaverOn)
        }

        btnUploadNow?.setOnClickListener {
            service?.drainNow()
        }

        btnRetryFailed?.setOnClickListener {
            service?.retryFailed()
        }
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(requireContext(), LifeLoggerService::class.java)
        requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        handler.post(refreshRunnable)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(refreshRunnable)
        runCatching { requireContext().unbindService(serviceConnection) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        indicatorDot = null
        tvRecordingState = null
        tvChunkInfo = null
        tvWifiStatus = null
        tvPendingCount = null
        tvFailedCount = null
        tvUploadedCount = null
        tvLastUpload = null
        switchRecording = null
        switchPowerSaver = null
        btnUploadNow = null
        btnRetryFailed = null
    }

    private fun refresh() {
        val ctx = context ?: return

        // Update recording state indicator
        val state = service?.recordingState ?: LifeLoggerService.STATE_STOPPED
        val (label, color) = when (state) {
            LifeLoggerService.STATE_LISTENING -> "Listening for speech..." to 0xFF4CAF50.toInt()  // green
            LifeLoggerService.STATE_RECORDING -> "Recording" to 0xFFF44336.toInt()               // red
            else -> "Stopped" to 0xFF808080.toInt()                                               // grey
        }
        tvRecordingState?.text = label
        (indicatorDot?.background as? GradientDrawable)?.setColor(color)

        val svc = service
        val chunks = svc?.chunksSaved ?: 0
        val lastInfo = svc?.lastChunkInfo ?: ""
        tvChunkInfo?.text = when {
            chunks > 0 && lastInfo.isNotEmpty() -> "Chunks: $chunks | $lastInfo"
            chunks > 0 -> "Chunks saved: $chunks"
            lastInfo.isNotEmpty() -> lastInfo
            else -> ""
        }

        val wifi = NetworkUtil.isWifi(ctx)
        tvWifiStatus?.text = if (wifi) "WiFi: Connected" else "WiFi: Disconnected — uploads paused"
        btnUploadNow?.isEnabled = wifi

        val queue = uploadQueue ?: return
        scope.launch {
            val pending = queue.getPendingCount()
            val failed = queue.getFailedCount()
            val uploaded = queue.getUploadedCount()
            val lastUpload = queue.getLastUploadTime()
            withContext(Dispatchers.Main) {
                tvPendingCount?.text = "Pending: $pending"
                tvFailedCount?.text = "Failed: $failed"
                tvUploadedCount?.text = "Uploaded: $uploaded"
                tvLastUpload?.text = if (lastUpload != null) "Last upload: $lastUpload" else "Last upload: —"
                btnRetryFailed?.visibility = if (failed > 0) View.VISIBLE else View.GONE
            }
        }
    }
}
