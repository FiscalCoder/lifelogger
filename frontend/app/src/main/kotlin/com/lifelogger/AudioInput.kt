package com.lifelogger

import android.media.AudioRecord
import com.lifelogger.config.AppConfig

/**
 * Creates AudioRecord instances using the configured source priority order.
 */
object AudioInput {

    fun createRecord(
        sampleRate: Int,
        channelConfig: Int,
        audioFormat: Int,
        bufferSize: Int
    ): AudioRecord? {
        for (source in AppConfig.AUDIO_SOURCE_PRIORITY) {
            val record = runCatching {
                AudioRecord(source, sampleRate, channelConfig, audioFormat, bufferSize)
            }.getOrNull() ?: continue

            if (record.state == AudioRecord.STATE_INITIALIZED) {
                return record
            }
            record.release()
        }
        return null
    }
}
