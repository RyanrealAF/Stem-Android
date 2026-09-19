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
import androidx.core.content.IntentCompat
import java.io.File

class StemFlowProcessingService : Service() {
    private var worker: Thread? = null
    private lateinit var jobs: JobRepository
    private val audioInspector = AudioInspector()
    private val transcriber = PolyphonicStemTranscriber()
    @Volatile private var stopRequested = false

    override fun onCreate() {
        super.onCreate()
        jobs = JobRepository(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val uri: Uri? = if (intent != null) {
            IntentCompat.getParcelableExtra(intent, EXTRA_INPUT_URI, Uri::class.java)
        } else null

        if (uri == null) return START_NOT_STICKY

        stopRequested = false
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification("Preparing audio…"),
            if (Build.VERSION.SDK_INT >= 35) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING else 0
        )
        worker?.interrupt()
        worker = Thread {
            val state = jobs.create(uri.toString())
            try {
                runPipeline(state, uri)
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

    private fun runPipeline(initial: JobState, uri: Uri) {
        update(initial.copy(phase = JobPhase.VALIDATING, progress = 0f, message = "Validating input…"))
        check(!stopRequested) { "Cancelled" }

        val metadata = audioInspector.inspect(this, uri)
        val jobDir = File(filesDir, "stemflow/jobs/${initial.jobId}").apply { mkdirs() }

        update(
            initial.copy(
                phase = JobPhase.SEPARATING,
                progress = 0.1f,
                message = "Input validated (${metadata.sampleRate} Hz, ${metadata.channelCount} ch)"
            )
        )
        check(!stopRequested) { "Cancelled" }

        // Stems setup
        val stemsDir = File(jobDir, "stems").apply { mkdirs() }
        val midiDir = File(jobDir, "midi").apply { mkdirs() }
        val stemTypes = listOf("vocals", "drums", "bass", "guitar", "piano", "other")

        // Prepare stem files for subsequent neural inference
        val stemFiles = stemTypes.associateWith { name ->
            File(stemsDir, "$name.wav").also { if (!it.exists()) it.createNewFile() }
        }

        val stemPhases = listOf(
            JobPhase.TRANSCRIBING_VOCALS to "vocals",
            JobPhase.TRANSCRIBING_DRUMS to "drums",
            JobPhase.TRANSCRIBING_BASS to "bass",
            JobPhase.TRANSCRIBING_GUITAR to "guitar",
            JobPhase.TRANSCRIBING_PIANO to "piano",
            JobPhase.TRANSCRIBING_OTHER to "other"
        )

        for ((phase, stemName) in stemPhases) {
            check(!stopRequested) { "Cancelled" }
            update(initial.copy(phase = phase, progress = 0.3f, message = "Transcribing $stemName…"))

            val stemFile = stemFiles[stemName] ?: continue
            val midiFile = File(midiDir, "$stemName.mid")

            transcriber.transcribe(stemFile, midiFile) { p, msg ->
                check(!stopRequested) { "Cancelled" }
                update(initial.copy(phase = phase, progress = 0.3f + p * 0.1f, message = "$stemName: $msg"))
            }
        }

        update(initial.copy(phase = JobPhase.EXPORTING, progress = 0.95f, message = "Finalizing job…"))
        check(!stopRequested) { "Cancelled" }

        update(initial.copy(phase = JobPhase.COMPLETE, progress = 1.0f, message = "Processing complete"))
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
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
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
