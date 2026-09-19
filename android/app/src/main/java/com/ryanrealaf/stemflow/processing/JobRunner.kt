package com.ryanrealaf.stemflow.processing

import android.content.ContentResolver
import android.net.Uri
import java.io.File

class JobRunner(
    private val resolver: ContentResolver,
    private val jobs: JobRepository,
    private val separator: StemSeparator,
    private val transcriber: StemTranscriber,
    private val archiveExporter: JobArchiveExporter = JobArchiveExporter(),
    private val cancelled: () -> Boolean = { false },
    private val update: (JobState) -> Unit
) {
    fun run(initial: JobState) {
        val info = AudioInputValidator.inspect(resolver, Uri.parse(initial.inputUri))
        val output = JobOutput(jobs.directory(initial.jobId))
        JsonJobWriter.write(output.jobFile, initial, info)

        setState(initial, JobPhase.SEPARATING, 0f, "Separating six stems")
        separator.separate(
            StreamingPcmChunkSequence(resolver, Uri.parse(initial.inputUri)),
            output.stemsDirectory
        ) { progress, message ->
            checkNotCancelled()
            setState(initial, JobPhase.SEPARATING, progress.coerceIn(0f, 1f), message)
        }

        val stems = Stem.entries.toList()
        stems.forEachIndexed { index, stem ->
            checkNotCancelled()
            val phase = when (stem) {
                Stem.VOCALS -> JobPhase.TRANSCRIBING_VOCALS
                Stem.DRUMS -> JobPhase.TRANSCRIBING_DRUMS
                Stem.BASS -> JobPhase.TRANSCRIBING_BASS
                Stem.GUITAR -> JobPhase.TRANSCRIBING_GUITAR
                Stem.PIANO -> JobPhase.TRANSCRIBING_PIANO
                Stem.OTHER -> JobPhase.TRANSCRIBING_OTHER
            }
            val stemFile = File(output.stemsDirectory, stem.name.lowercase() + ".wav")
            require(stemFile.isFile) { "Separator did not produce " + stem.name.lowercase() + ".wav" }
            setState(initial, phase, index / stems.size.toFloat(), "Transcribing " + stem.name.lowercase())
            transcriber.transcribe(stem, stemFile, output.midi(stem)) { p, message ->
                checkNotCancelled()
                val overall = (index + p.coerceIn(0f, 1f)) / stems.size
                setState(initial, phase, overall, message)
            }
        }

        checkNotCancelled()
        setState(initial, JobPhase.EXPORTING, 0.95f, "Writing archive")
        JsonJobWriter.write(output.analysisFile, initial.copy(
            phase = JobPhase.EXPORTING,
            progress = 0.95f,
            message = "Analysis complete; accuracy not scored"
        ), info)
        archiveExporter.export(jobs.directory(initial.jobId), output.archiveFile)
        setState(initial, JobPhase.COMPLETE, 1f, "StemFlow processing complete")
    }

    private fun setState(base: JobState, phase: JobPhase, progress: Float, message: String) {
        val state = base.copy(phase = phase, progress = progress, message = message, error = null)
        jobs.save(state)
        update(state)
    }

    private fun checkNotCancelled() {
        if (cancelled()) throw ProcessingCancelledException()
    }

    class ProcessingCancelledException : IllegalStateException("Cancelled")
}
