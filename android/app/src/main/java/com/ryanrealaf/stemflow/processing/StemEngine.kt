package com.ryanrealaf.stemflow.processing

import java.io.File

interface StemTranscriber {
    fun transcribe(
        stem: Stem,
        wavFile: File,
        outputMidi: File,
        progress: (Float, String) -> Unit
    )
}

interface MidiExporter {
    fun export(jobDirectory: File, outputZip: File)
}
