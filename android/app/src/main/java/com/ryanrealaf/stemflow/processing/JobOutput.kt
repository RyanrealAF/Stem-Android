package com.ryanrealaf.stemflow.processing

import java.io.File

class JobOutput(private val jobDirectory: File) {
    val stemsDirectory = File(jobDirectory, "stems").apply { mkdirs() }
    val midiDirectory = File(jobDirectory, "midi").apply { mkdirs() }
    val analysisFile = File(jobDirectory, "analysis.json")
    val jobFile = File(jobDirectory, "job.json")
    val archiveFile = File(jobDirectory, "stemflow-output.zip")

    fun midi(stem: Stem): File = File(midiDirectory, stem.name.lowercase() + ".mid")
}
