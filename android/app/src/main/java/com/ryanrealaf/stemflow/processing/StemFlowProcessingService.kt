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
    private val separator by lazy { NeuralStemSeparator(this) }
    private val transcriber by lazy { NeuralPitchTranscriber(this) }
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
        runCatching {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification("Preparing audio…"),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING else 0
            )
        }
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
        update(initial.copy(phase = JobPhase.VALIDATING, progress = 0f, message = "Validating and decoding audio…"))
        check(!stopRequested) { "Cancelled" }
        val metadata = audioInspector.inspect(this, uri)
        val jobDir = File(filesDir, "stemflow/jobs/" + initial.jobId).apply { mkdirs() }
        val stemsDir = File(jobDir, "stems").apply { mkdirs() }
        val midiDir = File(jobDir, "midi").apply { mkdirs() }

        update(initial.copy(phase = JobPhase.SEPARATING, progress = 0.05f, message = "Running HTDemucs on-device (" + metadata.sampleRate + " Hz, " + metadata.channelCount + " ch)…"))
        check(!stopRequested) { "Cancelled" }
        separator.separate(uri, stemsDir) { p, msg ->
            check(!stopRequested) { "Cancelled" }
            update(initial.copy(phase = JobPhase.SEPARATING, progress = p, message = msg))
        }

        val stemNames = listOf("vocals", "drums", "bass", "other")
        for ((index, stemName) in stemNames.withIndex()) {
            check(!stopRequested) { "Cancelled" }
            val phase = when (stemName) {
                "vocals" -> JobPhase.TRANSCRIBING_VOCALS
                "drums" -> JobPhase.TRANSCRIBING_DRUMS
                "bass" -> JobPhase.TRANSCRIBING_BASS
                else -> JobPhase.TRANSCRIBING_OTHER
            }
            val stem = File(stemsDir, stemName + ".wav")
            val midi = File(midiDir, stemName + ".mid")
            update(initial.copy(phase = phase, progress = 0.85f + index * 0.03f, message = "Running Basic Pitch on " + stemName + "…"))
            transcriber.transcribe(stem, midi) { p, msg ->
                check(!stopRequested) { "Cancelled" }
                update(initial.copy(phase = phase, progress = 0.85f + index * 0.03f + p * 0.03f, message = stemName + ": " + msg))
            }
        }

        update(initial.copy(phase = JobPhase.EXPORTING, progress = 0.98f, message = "Validating neural outputs…"))
        for (name in stemNames) require(File(stemsDir, name + ".wav").length() > 44) { "Missing " + name + " stem" }
        for (name in stemNames) require(File(midiDir, name + ".mid").length() > 32) { "Missing " + name + " MIDI" }
        update(initial.copy(phase = JobPhase.COMPLETE, progress = 1.0f, message = "Real stems + MIDI complete"))
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
        runCatching {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
        }
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
