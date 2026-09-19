package com.ryanrealaf.stemflow.processing

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

class StemFlowProcessingService : Service() {
    private var worker: Thread? = null
    private lateinit var jobs: JobRepository
    @Volatile private var stopRequested = false

    override fun onCreate() {
        super.onCreate()
        jobs = JobRepository(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val uri = intent?.getParcelableExtra<Uri>(EXTRA_INPUT_URI) ?: return START_NOT_STICKY
        stopRequested = false
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification("Preparing audio…"),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING else 0
        )
        worker?.interrupt()
        worker = Thread {
            val state = jobs.create(uri.toString())
            try {
                runPipeline(state)
            } catch (t: Throwable) {
                val failedState = state.copy(
                    phase = if (stopRequested) JobPhase.CANCELLED else JobPhase.FAILED,
                    message = if (stopRequested) "Cancelled" else "Processing failed",
                    error = if (stopRequested) null else (t.message ?: t::class.java.simpleName)
                )
                jobs.save(failedState)
                updateNotification(failedState.message)
            } finally {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }.also { it.start() }
        return START_NOT_STICKY
    }

    private fun runPipeline(initial: JobState) {
        update(initial.copy(phase = JobPhase.VALIDATING, progress = 0f, message = "Validating input…"))
        check(!stopRequested) { "Cancelled" }
        require(contentResolver.openAssetFileDescriptor(Uri.parse(initial.inputUri), "r") != null) {
            "Unable to open selected audio"
        }
        val separator = HtDemucs6sSeparator(this)
        val transcriber = UnsupportedBasicPitchEngine()
        val runner = JobRunner(
            resolver = contentResolver,
            jobs = jobs,
            separator = separator,
            transcriber = transcriber,
            cancelled = { stopRequested },
            update = ::update
        )
        runner.run(initial)
    }

    private fun update(state: JobState) {
        jobs.save(state)
        updateNotification(state.message)
    }

    private fun notification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("StemFlow")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "StemFlow processing", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopRequested = true
        worker?.interrupt()
        stopSelf(startId)
    }

    override fun onDestroy() {
        stopRequested = true
        worker?.interrupt()
        super.onDestroy()
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
