package com.ryanrealaf.stemflow.processing

import android.net.Uri
import java.io.File

interface StemSeparator {
    fun separate(input: Uri, outputDirectory: File, progress: (Float, String) -> Unit)
}

interface StemTranscriber {
    fun transcribe(stem: File, outputMidi: File, progress: (Float, String) -> Unit)
}

interface MidiExporter {
    fun export(jobDirectory: File, outputZip: File)
}
