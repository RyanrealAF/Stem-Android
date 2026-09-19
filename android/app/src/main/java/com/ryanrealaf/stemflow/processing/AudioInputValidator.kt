package com.ryanrealaf.stemflow.processing

import android.content.ContentResolver
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri

data class AudioInfo(
    val mimeType: String,
    val sampleRate: Int,
    val channelCount: Int,
    val durationUs: Long
)

object AudioInputValidator {
    fun inspect(resolver: ContentResolver, uri: Uri): AudioInfo {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(resolver.openFileDescriptor(uri, "r")!!.fileDescriptor)
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (!mime.startsWith("audio/")) continue

                val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE, 0)
                val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT, 0)
                val duration = format.getLong(MediaFormat.KEY_DURATION, 0L)
                require(sampleRate > 0) { "Audio stream has no valid sample rate" }
                require(channels > 0) { "Audio stream has no valid channel count" }
                return AudioInfo(mime, sampleRate, channels, duration)
            }
            error("No audio track found")
        } finally {
            extractor.release()
        }
    }
}
