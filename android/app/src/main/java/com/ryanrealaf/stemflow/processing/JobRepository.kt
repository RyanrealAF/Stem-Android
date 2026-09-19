package com.ryanrealaf.stemflow.processing

import android.content.Context
import java.io.File
import java.util.Properties
import java.util.UUID

class JobRepository(private val root: File) {
    constructor(context: Context) : this(File(context.filesDir, "stemflow/jobs"))

    init { root.mkdirs() }

    fun create(inputUri: String): JobState {
        val state = JobState(
            UUID.randomUUID().toString(),
            inputUri,
            JobPhase.VALIDATING,
            0f,
            "Queued"
        )
        save(state)
        return state
    }

    fun save(state: JobState) {
        val dir = File(root, state.jobId).apply { mkdirs() }
        val p = Properties().apply {
            setProperty("jobId", state.jobId)
            setProperty("inputUri", state.inputUri)
            setProperty("phase", state.phase.name)
            setProperty("progress", state.progress.toString())
            setProperty("message", state.message)
            state.error?.let { setProperty("error", it) }
        }
        File(dir, "job.properties").outputStream().use { p.store(it, "StemFlow job state") }
    }

    fun load(jobId: String): JobState? {
        val file = File(root, "$jobId/job.properties")
        if (!file.isFile) return null
        val p = Properties()
        file.inputStream().use { p.load(it) }
        val phase = runCatching { JobPhase.valueOf(p.getProperty("phase")) }.getOrNull() ?: return null
        return JobState(
            p.getProperty("jobId") ?: return null,
            p.getProperty("inputUri") ?: return null,
            phase,
            p.getProperty("progress")?.toFloatOrNull() ?: 0f,
            p.getProperty("message") ?: "",
            p.getProperty("error")
        )
    }

    fun incomplete(): List<JobState> =
        root.listFiles()?.mapNotNull { load(it.name) }
            ?.filter { it.phase !in TERMINAL_PHASES }
            ?: emptyList()

    companion object {
        private val TERMINAL_PHASES = setOf(
            JobPhase.COMPLETE,
            JobPhase.FAILED,
            JobPhase.CANCELLED
        )
    }
}
