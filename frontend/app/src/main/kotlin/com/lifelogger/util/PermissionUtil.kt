package com.lifelogger.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object PermissionUtil {

    fun hasRecordAudioPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Launches the system permission dialog for RECORD_AUDIO.
     * The result is delivered to [Activity.onRequestPermissionsResult].
     */
    fun requestRecordAudioPermission(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQUEST_CODE_RECORD_AUDIO
        )
    }

    fun shouldShowRationale(activity: Activity): Boolean =
        ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.RECORD_AUDIO
        )

    const val REQUEST_CODE_RECORD_AUDIO = 1001
}
