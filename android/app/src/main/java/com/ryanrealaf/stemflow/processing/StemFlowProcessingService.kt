package com.ryanrealaf.stemflow.processing

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ryanrealaf.stemflow.R
import java.util.UUID

class StemFlowProcessingService : Service() {
    private var worker: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val uri = intent?.getParcelableExtra<Uri>(EXTRA_INPUT_URI) ?: return START_NOT_STICKY
        createChannel()
        startForeground(NOTIFICATION_ID, notification("Preparing audio…"))
        worker?.interrupt()
        worker = Thread {
            val jobId = UUID.randomUUID().toString()
            try {
                runPipeline(jobId, uri)
            } catch (t: Throwable) {
                updateNotification("Failed: " + (t.message ?: "unknown error"))
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }.also { it.start() }
        return START_NOT_STICKY
    }

    private fun runPipeline(jobId: String, uri: Uri) {
        updateNotification("Validating input…")
        require(contentResolver.openAssetFileDescriptor(uri, "r") != null) {
            "Unable to open selected audio"
        }
        updateNotification("Processing job " + jobId)
    }

    private fun notification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("StemFlow")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification(text))
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "StemFlow processing", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "stemflow_processing"
        private const val NOTIFICATION_ID = 1001
        private const val EXTRA_INPUT_URI = "input_uri"

        fun startIntent(context: Context, uri: Uri): Intent =
            Intent(context, StemFlowProcessingService::class.java).putExtra(EXTRA_INPUT_URI, uri)
    }
}
