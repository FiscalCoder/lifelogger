package com.lifelogger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Starts [LifeLoggerService] automatically after the device boots.
 *
 * Requires RECEIVE_BOOT_COMPLETED permission declared in the manifest.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val serviceIntent = Intent(context, LifeLoggerService::class.java)
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
